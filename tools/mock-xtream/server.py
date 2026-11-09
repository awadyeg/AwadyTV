#!/usr/bin/env python3
"""
Mock Xtream Codes server for testing AwadyTV without a real provider.

Standard library only. Serves:
  /player_api.php            auth + every action the app uses
  /xmltv.php                 generated XMLTV guide
  /live|movie|series/<u>/<p>/<id>.<ext>  302 redirect to a legal public test stream

All media are open/test content (Blender open movies on archive.org/blender.org, test-videos.co.uk
clips, Apple/Mux/Shaka/Akamai/Unified Streaming HLS test streams, ExoPlayer test media). Nothing is hosted here: players are redirected
to the original CDNs, so the tunnel only carries small JSON responses.

Accounts (username / password):
  test / test           active
  expired / expired     status "Expired"
  banned / banned       status "Banned"
  disabled / disabled   status "Disabled"
  anything else         auth = 0 (wrong credentials)

Usage:  python server.py [--port 8080] [--delay 0]
"""
from __future__ import annotations

import argparse
import base64
import json
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, quote, urlparse
from xml.sax.saxutils import escape

# --------------------------------------------------------------------------------------------
# Catalog
# --------------------------------------------------------------------------------------------

def logo(text: str, bg: str) -> str:
    return f"https://placehold.co/400x400/{bg}/ffffff/png?text={quote(text)}"


def poster(text: str, bg: str = "1f2937") -> str:
    return f"https://placehold.co/500x750/{bg}/ffffff/png?text={quote(text)}"


def backdrop(_text: str, bg: str = "111827") -> str:
    # No text: the app draws its own title over the backdrop.
    return f"https://placehold.co/1280x720/{bg}/{bg}/png?text=%20"


# Media files (all legal open/test content). key -> (url, container_extension)
MEDIA = {
    "bbb": ("https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4", "mp4"),
    "ed": ("https://archive.org/download/ElephantsDream/ed_hd.mp4", "mp4"),
    "sintel": ("https://archive.org/download/Sintel/sintel-2048-surround.mp4", "mp4"),
    "tos": ("https://download.blender.org/demo/movies/ToS/tears_of_steel_720p.mov", "mov"),
    "bbb10": ("https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4", "mp4"),
    "jelly10": ("https://test-videos.co.uk/vids/jellyfish/mp4/h264/720/Jellyfish_720_10s_1MB.mp4", "mp4"),
    "sintel10": ("https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_1MB.mp4", "mp4"),
    "bbb10mkv": ("https://test-videos.co.uk/vids/bigbuckbunny/mkv/720/Big_Buck_Bunny_720_10s_1MB.mkv", "mkv"),
    "screens": ("https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv", "mkv"),
    "bbblow": ("https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4", "mp4"),
}


LIVE_CATEGORIES = [
    {"category_id": "1", "category_name": "Test Channels", "parent_id": 0},
    {"category_id": "2", "category_name": "Open Movies 24/7", "parent_id": 0},
    {"category_id": "3", "category_name": "Real Live Feeds", "parent_id": 0},
    {"category_id": "9", "category_name": "Adult (PIN test, safe content)", "parent_id": 0},
]

# (stream_id, name, category, hls_url, logo bg)
LIVE = [
    (101, "Apple BipBop", "1", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8", "1f6feb"),
    (102, "Apple BipBop fMP4", "1", "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8", "0969da"),
    (103, "Angel One (multi-audio)", "1", "https://storage.googleapis.com/shaka-demo-assets/angel-one-hls/hls.m3u8", "8250df"),
    (104, "Mux Discontinuity Test", "1", "https://test-streams.mux.dev/dai-discontinuity-deltatre/manifest.m3u8", "953800"),
    (201, "Big Buck Bunny TV", "2", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "e16f24"),
    (202, "Tears of Steel TV (subtitles)", "2", "https://test-streams.mux.dev/tos_ismc/main.m3u8", "bf3989"),
    (203, "Tears of Steel TV", "2", "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8", "1a7f37"),
    (301, "Akamai Live Test", "3", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8", "cf222e"),
    (302, "Unified Live Clock", "3", "https://demo.unified-streaming.com/k8s/live/stable/live.isml/.m3u8", "6e7781"),
    (901, "Locked Channel", "9", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8", "57606a"),
]

VOD_CATEGORIES = [
    {"category_id": "10", "category_name": "Blender Open Movies", "parent_id": 0},
    {"category_id": "11", "category_name": "Shorts", "parent_id": 0},
    {"category_id": "19", "category_name": "Adult (PIN test, safe content)", "parent_id": 0},
]

# (stream_id, name, category, media key, genre, year, minutes, rating, plot, director, cast, trailer)
MOVIES = [
    (1001, "Big Buck Bunny", "10", "bbb", "Animation, Comedy", 2008, 10, 7.6,
     "A giant rabbit takes gentle revenge on three bullying rodents.", "Sacha Goedegebure",
     "Big Buck Bunny, Frank, Rinky, Gimera", "YE7VzlLtp-4"),
    (1002, "Elephants Dream", "10", "ed", "Animation, Sci-Fi", 2006, 11, 6.5,
     "Two men explore a strange, ever-changing machine world.", "Bassam Kurdali",
     "Tygo Gernandt, Cas Jansen", "TLkA0RELQ1g"),
    (1003, "Sintel", "10", "sintel", "Animation, Fantasy", 2010, 15, 7.5,
     "A young woman searches for the baby dragon she once rescued.", "Colin Levy",
     "Halina Reijn, Thom Hoffman", "eRsGyueVLvQ"),
    (1004, "Tears of Steel", "10", "tos", "Sci-Fi, Action", 2012, 12, 6.3,
     "Scientists in Amsterdam try to rescue the world from destructive robots.", "Ian Hubert",
     "Derek de Lint, Sergio Hasselbaink, Rogier Schippers", "R6MlUcmOul8"),
    (1101, "Bunny 10s", "11", "bbb10", "Short, Animation", 2008, 1, 5.5,
     "Ten-second MP4 clip.", "Blender Foundation", "", ""),
    (1102, "Jellyfish 10s", "11", "jelly10", "Short, Nature", 2016, 1, 5.6,
     "Ten-second MP4 clip.", "test-videos.co.uk", "", ""),
    (1103, "Sintel 10s", "11", "sintel10", "Short, Fantasy", 2010, 1, 5.8,
     "Ten-second MP4 clip.", "Blender Foundation", "", ""),
    (1104, "Bunny 10s (MKV)", "11", "bbb10mkv", "Short, Animation", 2008, 1, 5.4,
     "Ten-second MKV clip, for testing the Matroska path.", "Blender Foundation", "", ""),
    (1105, "Android Screens (MKV)", "11", "screens", "Short, Tech", 2015, 1, 5.2,
     "ExoPlayer's MKV test file.", "Google", "", ""),
    (1106, "Big Buck Bunny (low-res)", "11", "bbblow", "Animation, Comedy", 2008, 10, 7.0,
     "320x180 version, for testing slow connections.", "Sacha Goedegebure", "", ""),
    (1901, "Locked Movie", "19", "jelly10", "Short", 2016, 1, 5.0,
     "Safe content in an adult-named category, for testing the parental PIN.", "test", "", ""),
]

SERIES_CATEGORIES = [
    {"category_id": "20", "category_name": "Open Movie Collections", "parent_id": 0},
    {"category_id": "21", "category_name": "Test Clips", "parent_id": 0},
]

# series_id -> metadata and seasons: {season: [(episode_id, title, media key, minutes)]}
SERIES = {
    5001: {
        "name": "Blender Open Movies", "category": "20", "color": "7c3aed", "genre": "Animation", "year": 2006,
        "rating": 7.2, "plot": "The Blender Foundation's open movies, one per episode.", "director": "Various",
        "cast": "Blender Foundation", "trailer": "eRsGyueVLvQ",
        "seasons": {
            1: [(50011, "Elephants Dream", "ed", 11), (50012, "Big Buck Bunny", "bbb", 10)],
            2: [(50021, "Sintel", "sintel", 15), (50022, "Tears of Steel", "tos", 12)],
        },
    },
    5002: {
        "name": "Ten Second Tests", "category": "21", "color": "0e7490", "genre": "Short", "year": 2016,
        "rating": 5.6, "plot": "Ten-second episodes, handy for testing Up next and autoplay.", "director": "test",
        "cast": "", "trailer": "",
        "seasons": {
            1: [(50031, "Bunny", "bbb10", 1), (50032, "Jellyfish", "jelly10", 1), (50033, "Sintel", "sintel10", 1),
                (50034, "Bunny (MKV)", "bbb10mkv", 1)],
            2: [(50041, "Jellyfish again", "jelly10", 1), (50042, "Bunny again", "bbb10", 1)],
        },
    },
    5003: {
        "name": "ExoPlayer Samples", "category": "21", "color": "b45309", "genre": "Tech", "year": 2015,
        "rating": 6.1, "plot": "Sample files from the ExoPlayer test media buckets.", "director": "Google",
        "cast": "", "trailer": "",
        "seasons": {
            1: [(50051, "Android Screens (MKV)", "screens", 1), (50052, "Big Buck Bunny 320p", "bbblow", 10)],
        },
    },
}

ACCOUNTS = {
    "test": ("test", "Active"),
    "expired": ("expired", "Expired"),
    "banned": ("banned", "Banned"),
    "disabled": ("disabled", "Disabled"),
}

ADDED = int(datetime(2026, 1, 1, tzinfo=timezone.utc).timestamp())

# --------------------------------------------------------------------------------------------
# Builders
# --------------------------------------------------------------------------------------------


def epg_id(stream_id: int) -> str:
    return f"ch{stream_id}.awady.test"


def live_streams(category: str | None):
    out = []
    for num, (sid, name, cat, _url, bg) in enumerate(LIVE, start=1):
        if category and cat != category:
            continue
        out.append({
            "num": num, "name": name, "stream_type": "live", "stream_id": sid,
            "stream_icon": logo(name, bg), "epg_channel_id": epg_id(sid), "added": str(ADDED),
            "category_id": cat, "custom_sid": "", "tv_archive": 0, "direct_source": "",
            "tv_archive_duration": 0,
        })
    return out


def vod_streams(category: str | None):
    out = []
    for num, m in enumerate(MOVIES, start=1):
        sid, name, cat, key, *_rest = m
        rating = m[7]
        if category and cat != category:
            continue
        out.append({
            "num": num, "name": name, "stream_type": "movie", "stream_id": sid,
            "stream_icon": poster(name), "rating": str(rating), "rating_5based": rating / 2,
            "added": str(ADDED + num * 3600), "category_id": cat, "container_extension": MEDIA[key][1],
            "custom_sid": "", "direct_source": "",
        })
    return out


def vod_info(vod_id: int):
    for m in MOVIES:
        sid, name, cat, key, genre, year, minutes, rating, plot, director, cast, trailer = m
        if sid != vod_id:
            continue
        return {
            "info": {
                "name": name, "movie_image": poster(name), "cover_big": poster(name),
                "backdrop_path": [backdrop(name)], "plot": plot, "description": plot, "cast": cast,
                "actors": cast, "director": director, "genre": genre, "releasedate": f"{year}-01-01",
                "rating": str(rating), "duration": f"{minutes // 60:02d}:{minutes % 60:02d}:00",
                "duration_secs": minutes * 60, "youtube_trailer": trailer, "tmdb_id": "",
            },
            "movie_data": {"stream_id": sid, "name": name, "added": str(ADDED), "category_id": cat,
                           "container_extension": MEDIA[key][1]},
        }
    return {"info": [], "movie_data": []}  # what real panels send for an unknown id


def series_list(category: str | None):
    out = []
    for num, (sid, s) in enumerate(SERIES.items(), start=1):
        if category and s["category"] != category:
            continue
        out.append({
            "num": num, "name": s["name"], "series_id": sid, "cover": poster(s["name"], s["color"]), "plot": s["plot"],
            "cast": s["cast"], "director": s["director"], "genre": s["genre"],
            "releaseDate": f"{s['year']}-01-01", "last_modified": str(ADDED + num * 3600),
            "rating": str(s["rating"]), "rating_5based": s["rating"] / 2, "backdrop_path": [backdrop(s["name"], s["color"])],
            "youtube_trailer": s["trailer"], "episode_run_time": "10", "category_id": s["category"],
        })
    return out


def series_info(series_id: int):
    s = SERIES.get(series_id)
    if not s:
        return {"seasons": [], "info": [], "episodes": []}
    seasons, episodes = [], {}
    for season, eps in s["seasons"].items():
        seasons.append({"id": series_id * 10 + season, "name": f"Season {season}", "season_number": season,
                        "episode_count": len(eps), "air_date": f"{s['year']}-01-01", "overview": "",
                        "cover": poster(s["name"], s["color"]), "cover_big": poster(s["name"], s["color"])})
        episodes[str(season)] = [{
            "id": str(eid), "episode_num": n, "title": f"{s['name']} - S{season:02d}E{n:02d} - {title}",
            "container_extension": MEDIA[key][1],
            "info": {"movie_image": f"https://placehold.co/640x360/{s['color']}/ffffff/png?text={quote(f'S{season}E{n} {title}')}",
                     "plot": f"{title}.", "duration_secs": mins * 60,
                     "duration": f"00:{mins:02d}:00", "rating": s["rating"], "releasedate": f"{s['year']}-01-01",
                     "name": title},
            "added": str(ADDED), "season": season, "direct_source": "",
        } for n, (eid, title, key, mins) in enumerate(eps, start=1)]
    info = series_list(None)[list(SERIES).index(series_id)]
    return {"seasons": seasons, "info": info, "episodes": episodes}


SHOWS = ["Morning News", "Tech Talk", "Open Movie Hour", "Nature Walk", "Sports Roundup", "Late Show",
         "Documentary", "Kids Corner", "Music Mix", "Weather Update", "Cooking Today", "Night Film"]


def programmes(stream_id: int, hours_back: int = 2, hours_forward: int = 24):
    """Hourly programmes around now; deterministic per channel."""
    now = int(time.time())
    base = now - now % 3600
    out = []
    for h in range(-hours_back, hours_forward):
        start = base + h * 3600
        idx = (start // 3600 + stream_id) % len(SHOWS)
        title = SHOWS[idx]
        out.append((start, start + 3600, title, f"{title} on channel {stream_id}. Generated mock guide data."))
    return out


def fmt(ts: int) -> str:
    return datetime.fromtimestamp(ts, tz=timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def b64(text: str) -> str:
    return base64.b64encode(text.encode()).decode()


def short_epg(stream_id: int, limit: int | None, full: bool):
    now = int(time.time())
    rows = programmes(stream_id, hours_back=2 if full else 0)
    if not full:
        rows = [r for r in rows if r[1] > now]
        rows = rows[: limit or 4]
    listings = []
    for i, (start, end, title, desc) in enumerate(rows):
        row = {
            "id": str(stream_id * 1000 + i), "epg_id": str(stream_id), "title": b64(title), "lang": "en",
            "start": fmt(start), "end": fmt(end), "description": b64(desc), "channel_id": epg_id(stream_id),
            "start_timestamp": str(start), "stop_timestamp": str(end),
        }
        if full:
            row["now_playing"] = 1 if start <= now < end else 0
            row["has_archive"] = 0
        listings.append(row)
    return {"epg_listings": listings}


def xmltv() -> str:
    parts = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv generator-info-name="awady-mock-xtream">']
    for sid, name, _cat, _url, bg in LIVE:
        parts.append(f'  <channel id="{epg_id(sid)}"><display-name>{escape(name)}</display-name>'
                     f'<icon src="{escape(logo(name, bg))}"/></channel>')
    for sid, *_ in LIVE:
        for start, end, title, desc in programmes(sid):
            s = datetime.fromtimestamp(start, tz=timezone.utc).strftime("%Y%m%d%H%M%S +0000")
            e = datetime.fromtimestamp(end, tz=timezone.utc).strftime("%Y%m%d%H%M%S +0000")
            parts.append(f'  <programme start="{s}" stop="{e}" channel="{epg_id(sid)}">'
                         f'<title lang="en">{escape(title)}</title><desc lang="en">{escape(desc)}</desc></programme>')
    parts.append("</tv>")
    return "\n".join(parts)


def stream_target(kind: str, item_id: int) -> str | None:
    if kind == "live":
        return next((url for sid, _n, _c, url, _b in LIVE if sid == item_id), None)
    if kind == "movie":
        return next((MEDIA[m[3]][0] for m in MOVIES if m[0] == item_id), None)
    if kind == "series":
        for s in SERIES.values():
            for eps in s["seasons"].values():
                for eid, _t, key, _m in eps:
                    if eid == item_id:
                        return MEDIA[key][0]
    return None


# --------------------------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------------------------


class Handler(BaseHTTPRequestHandler):
    server_version = "AwadyMockXtream/1.0"
    delay = 0.0

    def log_message(self, fmt_, *args):  # redact credentials from the access log
        path = urlparse(self.path)
        action = parse_qs(path.query).get("action", ["auth" if path.path.endswith("player_api.php") else ""])[0]
        print(f"{self.address_string()} {self.command} {path.path.split('/')[1] if '/' in path.path else ''} "
              f"{action} -> {args[1] if len(args) > 1 else ''}", flush=True)

    def send_json(self, payload, status: int = 200):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_text(self, text: str, ctype: str, status: int = 200):
        body = text.encode()
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def account(self, user: str, pwd: str):
        acc = ACCOUNTS.get(user)
        return acc[1] if acc and acc[0] == pwd else None

    def do_GET(self):
        if self.delay:
            time.sleep(self.delay)
        url = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(url.query).items()}
        path = url.path.rstrip("/")

        if path in ("", "/"):
            return self.send_text("AwadyTV mock Xtream server. Log in with test / test.\n", "text/plain")

        if path.endswith("/player_api.php"):
            return self.player_api(q)

        if path.endswith("/xmltv.php"):
            if self.account(q.get("username", ""), q.get("password", "")) != "Active":
                return self.send_text("Unauthorized", "text/plain", 401)
            return self.send_text(xmltv(), "application/xml; charset=utf-8")

        # /live/<u>/<p>/<id>.<ext>, /movie/..., /series/..., and the bare /<u>/<p>/<id> live form
        parts = [p for p in path.split("/") if p]
        if len(parts) == 3:
            parts = ["live", *parts]
        if len(parts) == 4 and parts[0] in ("live", "movie", "series"):
            kind, user, pwd, file = parts
            if self.account(user, pwd) != "Active":
                return self.send_text("Forbidden", "text/plain", 403)
            try:
                item_id = int(file.split(".")[0])
            except ValueError:
                return self.send_text("Not found", "text/plain", 404)
            target = stream_target(kind, item_id)
            if not target:
                return self.send_text("Not found", "text/plain", 404)
            self.send_response(302)
            self.send_header("Location", target)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return

        self.send_text("Not found", "text/plain", 404)

    def player_api(self, q: dict):
        user, pwd = q.get("username", ""), q.get("password", "")
        status = self.account(user, pwd)
        action = q.get("action")
        if status is None:
            return self.send_json({"user_info": {"auth": 0}})
        if action is None:
            return self.send_json(self.auth_payload(user, pwd, status))
        if status != "Active":
            return self.send_json({"user_info": {"auth": 0}})

        cat = q.get("category_id")
        if action == "get_live_categories":
            return self.send_json(LIVE_CATEGORIES)
        if action == "get_live_streams":
            return self.send_json(live_streams(cat))
        if action == "get_vod_categories":
            return self.send_json(VOD_CATEGORIES)
        if action == "get_vod_streams":
            return self.send_json(vod_streams(cat))
        if action == "get_vod_info":
            return self.send_json(vod_info(int(q.get("vod_id", "0") or 0)))
        if action == "get_series_categories":
            return self.send_json(SERIES_CATEGORIES)
        if action == "get_series":
            return self.send_json(series_list(cat))
        if action == "get_series_info":
            return self.send_json(series_info(int(q.get("series_id", "0") or 0)))
        if action == "get_short_epg":
            return self.send_json(short_epg(int(q.get("stream_id", "0") or 0), int(q.get("limit", "4") or 4), False))
        if action == "get_simple_data_table":
            return self.send_json(short_epg(int(q.get("stream_id", "0") or 0), None, True))
        return self.send_json([])

    def auth_payload(self, user: str, pwd: str, status: str):
        now = int(time.time())
        exp = now - 86400 * 3 if status == "Expired" else now + 86400 * 365
        host = (self.headers.get("X-Forwarded-Host") or self.headers.get("Host") or "localhost").split(":")
        proto = self.headers.get("X-Forwarded-Proto") or "http"
        port = host[1] if len(host) > 1 else ("443" if proto == "https" else "80")
        return {
            "user_info": {
                "username": user, "password": pwd, "message": "AwadyTV mock server", "auth": 1, "status": status,
                "exp_date": str(exp), "is_trial": "1", "active_cons": "0", "created_at": str(ADDED),
                "max_connections": "2", "allowed_output_formats": ["m3u8", "ts"],
            },
            "server_info": {
                "url": host[0], "port": port if proto == "http" else "80", "https_port": port if proto == "https" else "443",
                "server_protocol": proto, "rtmp_port": "0", "timezone": "UTC", "timestamp_now": now, "time_now": fmt(now),
            },
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--delay", type=float, default=0.0, help="seconds to wait before every response (slow-server test)")
    args = parser.parse_args()
    Handler.delay = args.delay
    httpd = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Mock Xtream server on http://{args.host}:{args.port}  (login: test / test)", flush=True)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
