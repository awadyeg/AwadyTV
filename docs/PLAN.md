# AwadyTV — Build Plan

An Android TV player for **Xtream Codes** IPTV providers (the same provider protocol IPTV Smarters Pro uses), styled after **tvOS / Apple TV with the Liquid Glass design language**, with an **AI recommendations** shelf on Home driven by the shows and movies the user has liked.

This document is the specification an implementing agent works from. Sections are ordered the way they should be built. Anything marked **MVP** must ship in the first build; **v1.1** items follow once the MVP compiles and runs.

---

## 1. Product scope

### 1.1 What the app does
1. **Login** — first launch asks for *Server URL*, *Username*, *Password* (Xtream Codes "API login", identical to IPTV Smarters Pro's "Login with Xtream Codes API"). Multiple profiles can be saved and switched.
2. **Home** — tvOS-style top tab bar (Home · Live TV · Movies · Series · Search · Settings), a hero banner, then shelves: *Continue Watching*, *Recommended for you (AI)*, *Because you liked …*, *Recently added movies*, *Recently added series*, *Favourite channels*.
3. **Live TV** — categories on the left (glass side rail), channel grid/list on the right with now/next EPG; full-screen player with a glass channel overlay (number, logo, now/next progress bar), channel up/down with the D-pad, mini channel-list overlay on Select/OK.
4. **Movies** — categories → poster grid → detail page (backdrop, poster, plot, cast, director, rating, year, duration, "Play", "Resume", "Like", "Add to favourites", "Trailer" if YouTube id present) → player.
5. **Series** — categories → poster grid → detail with seasons picker and episode rail → player with "Next episode" auto-play and resume.
6. **Search** — one glass search field (D-pad keyboard + voice via `RecognizerIntent`) across live, movies, series.
7. **Settings** — profiles, playback (decoder, buffer, subtitle style, preferred stream format `m3u8`/`ts`), EPG refresh, parental PIN for adult categories, "AI recommendations" toggle plus optional Claude API key, clear cache, about.

### 1.2 Non-goals for MVP
- No downloads / offline playback.
- No M3U-only playlists (Xtream Codes API only). The data layer must be abstracted so an M3U source can be added later.
- No Chromecast.

---

## 2. Technology

| Area | Choice |
|---|---|
| Language | Kotlin 2.0.x |
| UI | Jetpack Compose + **Compose for TV** (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`) |
| Player | **Media3 ExoPlayer** (`exoplayer`, `exoplayer-hls`, `ui`, `datasource-okhttp`) |
| DI | Hilt |
| Networking | Retrofit + OkHttp + `kotlinx.serialization` (Xtream responses are inconsistent: ids come as numbers *or* strings, `null`/`""` interchangeably — use lenient JSON and custom serializers) |
| Local data | Room (profiles, favourites, likes, watch progress, EPG cache, catalog cache) + DataStore (settings) + `EncryptedSharedPreferences` for the Xtream password and the Claude API key |
| Images | Coil 3 (with OkHttp, memory+disk cache, crossfade) |
| Navigation | `androidx.navigation:navigation-compose` with typed routes |
| Build | AGP 8.7+, Gradle 8.14 (system gradle at `/opt/gradle/bin/gradle`, generate wrapper with `gradle wrapper`), `compileSdk 35`, `targetSdk 35`, **`minSdk 24`** |
| Tests | JUnit5/4 + MockK for domain & recommendation engine, Compose UI tests for focus behaviour where cheap |

Android TV manifest requirements: `LEANBACK_LAUNCHER` intent filter, `android.software.leanback` required, `android.hardware.touchscreen` **not** required, TV banner `320x180`, `INTERNET` permission, `RECORD_AUDIO` only if voice search is used.

---

## 3. Xtream Codes API reference (what the data layer talks to)

Base: `{server}/player_api.php?username={u}&password={p}`. Normalise the server URL: add `http://` if missing, strip trailing `/` and any `/player_api.php` or `/get.php` suffix the user pasted; respect an explicit port.

| Action | Purpose |
|---|---|
| *(none)* | Auth. Returns `user_info` (`auth` 1/0, `status` Active/Expired/Banned, `exp_date`, `max_connections`, `allowed_output_formats`) and `server_info` (`url`, `port`, `https_port`, `server_protocol`, `timezone`, `time_now`). |
| `get_live_categories` | `[{category_id, category_name, parent_id}]` |
| `get_live_streams` (`&category_id=`) | `[{num, name, stream_type, stream_id, stream_icon, epg_channel_id, added, category_id, custom_sid, tv_archive, direct_source, tv_archive_duration}]` |
| `get_vod_categories` / `get_vod_streams` | movies; items carry `stream_id`, `name`, `stream_icon`, `rating`, `rating_5based`, `added`, `category_id`, `container_extension`, `custom_sid`, `direct_source` |
| `get_vod_info&vod_id=` | `info` {`movie_image`, `backdrop_path[]`, `plot`, `cast`, `director`, `genre`, `releasedate`, `rating`, `duration`, `youtube_trailer`, `tmdb_id`} + `movie_data` |
| `get_series_categories` / `get_series` | items carry `series_id`, `name`, `cover`, `plot`, `cast`, `director`, `genre`, `releaseDate`, `last_modified`, `rating`, `backdrop_path[]`, `youtube_trailer`, `episode_run_time`, `category_id` |
| `get_series_info&series_id=` | `seasons[]`, `info`, `episodes` as a **map of season number → list of** `{id, episode_num, title, container_extension, info{movie_image, plot, duration, rating, releasedate}, season}` |
| `get_short_epg&stream_id=&limit=` | now/next; `title` and `description` are **base64** encoded |
| `get_simple_data_table&stream_id=` | full EPG for one channel (base64 as above) |
| `{server}/xmltv.php?username&password` | full XMLTV guide — parse with a streaming XML parser in a background worker |

Stream URLs:
- Live: `{server}/live/{u}/{p}/{stream_id}.m3u8` (fallback `.ts` when `allowed_output_formats` lacks `m3u8`)
- Movie: `{server}/movie/{u}/{p}/{stream_id}.{container_extension}`
- Episode: `{server}/series/{u}/{p}/{episode_id}.{container_extension}`
- Catch-up: `{server}/streaming/timeshift.php?username&password&stream={id}&start={YYYY-MM-DD:HH-MM}&duration={min}` (v1.1)

---

## 4. Architecture

Single Gradle module `:app` for MVP (fast to iterate), organised by feature packages with clean layering so it can be split later:

```
com.awadytv
├── AwadyTvApp.kt                 (Hilt application)
├── MainActivity.kt               (single activity, Compose host)
├── core/
│   ├── designsystem/             Liquid Glass theme, colours, type, GlassSurface, FocusScale, shelves, cards, TopTabBar
│   ├── network/                  OkHttp/Retrofit setup, lenient JSON, XtreamUrlBuilder
│   ├── database/                 Room db, DAOs, entities
│   ├── datastore/                Settings
│   └── util/                     Result wrapper, base64 helpers, time
├── data/
│   ├── xtream/                   XtreamApi (Retrofit), DTOs, mappers
│   ├── repository/               ProfileRepository, LiveRepository, MoviesRepository, SeriesRepository, EpgRepository, LibraryRepository (favourites/likes/progress)
│   └── recommendations/          RecommendationEngine (local) + ClaudeRecommender (optional)
├── domain/                       Models (Channel, Movie, Series, Episode, EpgEntry, Profile, Recommendation) + use cases
├── feature/
│   ├── login/
│   ├── home/
│   ├── live/   (browse + player overlay)
│   ├── movies/ (browse + detail)
│   ├── series/ (browse + detail + episodes)
│   ├── search/
│   ├── settings/
│   └── player/ (PlayerScreen, PlayerViewModel, controls overlay, track selection)
└── navigation/                   AppNavHost, routes
```

Patterns: MVVM with `ViewModel` + `StateFlow<UiState>`; repositories expose `Flow`s backed by Room (offline-first — the catalog is cached and refreshed in the background with a stale-while-revalidate policy; Xtream providers are slow and rate-limit, so **never** hit the API on every screen open). `WorkManager` periodic job refreshes EPG every 6h.

---

## 5. Design: tvOS + Liquid Glass

Target the look of tvOS 26: a dark, cinematic canvas with content imagery doing the work, and chrome rendered as **glass** — translucent, blurred, refractive layers with a soft specular edge — that sits *over* the content and never competes with it.

### 5.1 Tokens (`core/designsystem`)
- **Background**: near-black `#0B0B0F` → radial/linear gradient derived from the focused item's artwork (Palette API extracts the dominant colour, animated with `animateColorAsState`, 600ms).
- **Glass surface** (`GlassSurface` composable): rounded corners 20–28dp; fill `white @ 10–14%` over a blur of the backdrop; 1dp **inner stroke** gradient `white 45% → white 5%` (top-left to bottom-right) for the specular edge; outer soft shadow `black 35%`; optional subtle **noise** overlay at 3% so large panels do not band. Variants: `Regular` (tab bar, side rails, overlays), `Clear` (hero text scrims), `Tinted` (accent-tinted buttons).
  - On API 31+ and when `ActivityManager.isLowRamDevice()` is false: real backdrop blur via `Modifier.graphicsLayer { renderEffect = BlurEffect(…) }` on a dedicated backdrop layer **behind** the glass (blur the backdrop, not the glass itself).
  - Fallback (API < 31 or low-RAM TV boxes — most are): no runtime blur; use a pre-blurred backdrop image (Coil `BlurTransformation` at load time, downsampled to 1/8) with the same tint/stroke so the glass still reads as glass. Design must look correct in both modes.
- **Focus**: Apple's "lift" — focused card scales to **1.08** with spring `(dampingRatio 0.7, stiffness 400)`, gains a 2dp white glass border and a larger drop shadow; unfocused siblings dim to 85% alpha. Add a **parallax tilt** on poster cards (±4° based on which D-pad direction last moved focus, 200ms, then settle). Subtle **specular sweep** across the focused card on first focus.
- **Typography**: `SF Pro`-like → ship **Inter** (variable). Display 48/semibold for hero titles, Title 28, Body 18, Caption 14; generous letter-spacing on uppercase shelf headers (`0.08em`). Minimum legible text on TV is 16sp.
- **Motion**: shelves fade+slide in (`y 24dp → 0`, 350ms) on screen entry; screen transitions are crossfade+scale `0.98 → 1`. Nothing bounces more than once.
- **Safe area**: 48dp horizontal, 27dp vertical overscan padding on all screens.

### 5.2 Key screens
- **Login**: full-bleed blurred artwork (bundled gradient mesh), centred glass card 560dp wide: app mark, three fields (server, username, password with show/hide), "Sign in" tinted glass button, inline error text, spinner state. Remember last profile; "Switch profile" chip when profiles exist.
- **Top tab bar**: tvOS pill tab bar — a single floating glass capsule, selected tab is a brighter glass pill that slides between items (`animateDpAsState`). It collapses to a thin line when focus moves down into content and re-expands on D-pad Up from the first shelf.
- **Home**: hero (16:9 backdrop, title, meta line, Play / More Info glass buttons) auto-rotates among 5 recommended items every 8s when idle; shelves below as in §1.1.
- **Live TV browse**: left glass rail of categories (width 300dp), right panel channel list rows: logo tile (glass background), number, name, *now playing* title with thin progress bar, next title dimmed.
- **Live player**: video full-screen; on D-pad Select show a bottom glass overlay (channel logo/name/number, now/next with progress, time) that auto-hides after 5s; Up/Down zaps channel inside the current category; Left opens the channel list as a side glass sheet; Back exits.
- **Movie detail**: left poster (2:3) in a glass frame, right text column, action row of glass buttons; related rail below ("More like this" from the local engine).
- **Series detail**: as movie detail + season chips (glass pills) + episode rail with thumbnail, number, title, duration, progress bar.
- **VOD player**: Media3 `PlayerView`-free custom Compose controls: scrubber with chapter-free thumbnails disabled (no sprites from Xtream), ±10s skip, audio/subtitle track picker as glass sheet, "Next episode" card at 95% progress.

---

## 6. AI recommendations

### 6.1 Signals (stored in Room `library` tables)
- **Likes**: explicit ❤ on movie/series detail (`liked_at`).
- **Implicit**: completed watch (≥ 90%) counts as a weak like; abandoned < 10% counts as a weak dislike.
- Each item carries the metadata Xtream provides: `genre` (comma list), `cast`, `director`, `releaseDate`, `rating`, `plot`.

### 6.2 Local engine — **MVP**, always on, no network, no key
`RecommendationEngine` in `data/recommendations`:
1. Build a **taste profile** from liked items: weighted term vectors for genre (weight 3), director (2), cast top-5 (1.5), decade (1), plot keywords (TF-IDF over the catalog, stop-words removed, weight 1). Recency decay: weight × `0.5^(days since like / 60)`.
2. Score every un-watched catalog item by cosine similarity to the profile, add `+0.1 × normalised rating`, add a small novelty bonus for items added in the last 14 days, and penalise items in categories the user never opens.
3. Output shelves: *Recommended for you* (top 20 mixed), *Because you liked «X»* (per the 3 most recent likes, nearest neighbours of that single item), *Top rated in {favourite genre}*.
4. Runs on `Dispatchers.Default`, caches results in Room with a 6h TTL, recomputes immediately after a new like.
5. Cold start (no likes): fall back to top-rated + recently added.
Unit tests: deterministic fixtures, assert ordering and that liked/watched items are excluded.

### 6.3 Claude-powered layer — **v1.1**, optional, user-supplied key
- Settings → *AI recommendations* → "Use Claude for smarter picks" + API key field (stored encrypted, never logged).
- `ClaudeRecommender` sends the user's liked titles (title, year, genre only — **no credentials, no plot text, no server URL**) plus the candidate list the local engine ranked top-60, and asks for a re-ranked top-20 with a one-line reason each, as JSON. Use the Anthropic Messages API with a small fast model; **load the `claude-api` skill before writing this code** to confirm the current model id, request shape and pricing. One request per 6h or per new like; results cached; failure silently falls back to the local engine. Show the reason as a caption under each card ("Because you liked *Dark* and like slow-burn mysteries").
- Hard rules: the key is optional; the app must be fully functional without it; no telemetry.

---

## 7. Data model (Room)

```
profiles(id, name, server_url, username, password_ref, created_at, last_used_at)
live_categories(profile_id, id, name, parent_id)
channels(profile_id, stream_id, num, name, icon, epg_channel_id, category_id, tv_archive, added)
vod_categories / movies(profile_id, stream_id, name, icon, rating, added, category_id, container_ext, genre?, year?)
series_categories / series(profile_id, series_id, name, cover, plot, cast, director, genre, release_date, rating, backdrop, last_modified, category_id)
episodes(profile_id, series_id, season, episode_id, num, title, container_ext, image, plot, duration, release_date)
epg(profile_id, epg_channel_id, start, end, title, description)
favourites(profile_id, kind, item_id, added_at)
likes(profile_id, kind, item_id, liked_at, source[explicit|implicit])
progress(profile_id, kind, item_id, position_ms, duration_ms, updated_at)
recommendations(profile_id, shelf, item_kind, item_id, score, reason, computed_at)
```
Indices on `(profile_id, category_id)` and `(profile_id, epg_channel_id, start)`.

---

## 8. Build order (milestones)

**M0 — Skeleton (compiles, installs, launches on a TV emulator)**
Gradle project, version catalog, Hilt, Compose TV, theme tokens, `GlassSurface`, `TopTabBar`, placeholder screens, `local.properties` with `sdk.dir=/opt/android-sdk` (git-ignored), `./gradlew assembleDebug` green.

**M1 — Login + catalog sync**
`XtreamApi`, URL normalisation, auth, profile persistence with encrypted password, full catalog sync into Room with progress UI, error states (expired account, banned, wrong URL, offline).

**M2 — Live TV**
Categories rail, channel list, short EPG, ExoPlayer HLS/TS playback, zap overlay, channel list sheet, favourites.

**M3 — Movies & Series**
Grids, detail pages, VOD playback with resume, series seasons/episodes, next-episode, Continue Watching.

**M4 — Home + local AI recommendations**
Hero, shelves, `RecommendationEngine` + tests, Because-you-liked rows, like button everywhere.

**M5 — Search, Settings, polish**
Search across kinds, voice search, settings screen, parental PIN, EPG worker, adaptive background colour, parallax/specular polish, performance pass on a low-end profile (disable runtime blur, cap shelf item count, Coil downsampling).

**v1.1** — Claude re-ranker, catch-up TV, XMLTV full guide grid, M3U source.

---

## 9. Quality bar / acceptance

- `./gradlew assembleDebug lint testDebugUnitTest` passes.
- Every focusable element has a visible focus state; no focus traps; Back always goes somewhere sensible; first focus on each screen is deterministic.
- Start-up to Home with a warm cache < 2s on a 2GB TV box; shelves scroll at 60fps (no blur on fallback path, images downsampled to the card size).
- Secrets: Xtream password and Claude key live only in `EncryptedSharedPreferences`; stream URLs that embed credentials are never logged.
- Works on 1080p and 4K, 16:9; overscan-safe.
- README documents how to build, how to run on an emulator (`Television (1080p)` AVD, API 34), and the design system.

---

## 10. Repository layout

```
AwadyTV/
├── docs/PLAN.md               (this file)
├── docs/DESIGN.md             (tokens + component gallery, written during M0)
├── README.md
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradlew
├── app/
│   ├── build.gradle.kts
│   └── src/main/{AndroidManifest.xml, java/com/awadytv/…, res/…}
└── .github/workflows/android.yml   (assembleDebug + unit tests on push)
```
