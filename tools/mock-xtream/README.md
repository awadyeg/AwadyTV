# Mock Xtream server

A small Xtream Codes compatible server for testing AwadyTV without a real provider.
Python 3.9+ standard library only, no install step.

```bash
python tools/mock-xtream/server.py --port 8080
```

Then sign in on the TV with server `http://<your-PC-IP>:8080`, user `test`, password `test`.

## Accounts

| Username / password | Result in the app |
|---|---|
| `test` / `test` | Active account |
| `expired` / `expired` | "Expired" error |
| `banned` / `banned` | "Banned" error |
| `disabled` / `disabled` | "Disabled" error |
| anything else | Wrong credentials |

`--delay 12` makes every response slow, for testing the "Server is slow" path. Stopping the
server tests "Server unreachable", and any non-Xtream URL (for example `http://example.com`)
tests "Unexpected response".

## Content

All media are legal open/test content. The server only answers the JSON API and redirects
stream URLs (`/live|movie|series/<user>/<pass>/<id>.<ext>`) with a 302 to the original CDN,
so video never passes through your PC or the tunnel.

- **Live TV (9 channels, 4 categories):** Apple BipBop (TS and fMP4), Shaka Angel One
  (multiple audio tracks), Mux Big Buck Bunny, Mux Tears of Steel (subtitles), Unified Streaming
  Tears of Steel, Akamai live test, Unified Streaming live clock, and a discontinuity test.
- **Movies (11):** Big Buck Bunny, Elephants Dream, Sintel (archive.org), Tears of Steel
  (`.mov`, blender.org), 10-second MP4 and MKV clips, ExoPlayer's MKV test file, a 320x180
  low-res file.
- **Series (3):** Blender Open Movies (2 seasons), Ten Second Tests (short episodes for Up
  next and autoplay), ExoPlayer Samples.
- **EPG:** hourly generated programmes for every channel through `get_short_epg`,
  `get_simple_data_table` and `xmltv.php`.
- **Parental PIN:** each kind has an "Adult (PIN test, safe content)" category holding harmless
  test media, so the PIN lock can be tested.
- **Artwork:** generated placeholders from placehold.co.

## Reaching it from anywhere

On the same network, use the PC's LAN IP. To reach it from any network, put a tunnel in front
of it, for example a Cloudflare quick tunnel:

```bash
cloudflared tunnel --url http://localhost:8080
```

It prints an `https://<random>.trycloudflare.com` address to use as the server URL. The address
changes every time the tunnel starts. Anyone who has the address can use the `test` account, and
it only serves public test media.
