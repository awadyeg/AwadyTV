# AwadyTV

An Android TV player for **Xtream Codes** IPTV providers, with a tvOS-style **Liquid Glass**
interface and **on-device AI recommendations** on Home.

Sign in the same way as IPTV Smarters Pro's "Login with Xtream Codes API" (server URL,
username, password), then browse Live TV with now/next EPG, Movies, Series, Search and Settings.

> AwadyTV is a player only. It ships no content or playlists; use it with a provider you are
> entitled to use.

## Features

- **Login and profiles**: Xtream Codes API sign-in, URL normalisation (adds `http://`, strips a
  pasted `/player_api.php` or `/get.php`, keeps the port), clear errors for wrong credentials,
  expired/banned/disabled accounts, offline and unreachable servers. Several profiles can be
  saved and switched. Passwords are kept in `EncryptedSharedPreferences` only.
- **Offline-first catalog**: categories, channels, movies and series are synced into Room once
  (with a progress screen) and refreshed in the background every 12h (stale-while-revalidate).
  Screens never call the provider on open.
- **Live TV**: glass category rail, channel list with logos, now/next and progress; full-screen
  player with Up/Down zapping inside the category, number-key entry, glass now/next overlay,
  channel-list side sheet, favourites, automatic HLS to MPEG-TS fallback. EPG from
  `get_short_epg` on demand plus a WorkManager XMLTV refresh every 6h.
- **Movies and Series**: poster grids, detail pages (backdrop, poster, plot, cast, director,
  rating, year, duration, Play / Resume / Start over, Like, Favourite, Trailer, "More like this"),
  season chips and episode rail, resume, "Up next" card with autoplay at 95%, Continue watching.
- **Player**: Media3 ExoPlayer (HLS, MPEG-TS, MKV, MP4) through OkHttp with custom Compose
  controls, audio/subtitle track picker, hardware/software decoder and buffer settings.
- **Search**: across channels, movies and series, with the on-screen keyboard or voice
  (system speech recogniser via `RecognizerIntent`).
- **Settings**: profiles, playback, TV guide, parental PIN for adult categories, AI
  recommendations (with an optional Claude re-ranker using your own API key), low-end mode,
  cache clearing, about.

## Build

Requirements: JDK 17+ (21 tested), Android SDK with `platforms;android-35` and
`build-tools;35.0.0`.

```bash
echo "sdk.dir=$ANDROID_HOME" > local.properties   # git-ignored
./gradlew assembleDebug            # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # unit tests
./gradlew lintDebug                # Android lint
```

Versions are pinned in `gradle/libs.versions.toml` (AGP 8.7.3, Kotlin 2.0.21, Compose BOM
2024.12.01, tv-material 1.0.0, Media3 1.5.1, Hilt 2.52, Room 2.6.1, Coil 3.0.4). Maven Central is
read through Google's mirror (`maven-central.storage-download.googleapis.com`) first because
`repo.maven.apache.org` rate-limits shared CI proxies.

CI (`.github/workflows/android.yml`) runs `assembleDebug` and `testDebugUnitTest` on every push.

## Run on an emulator

1. In Android Studio open **Device Manager → Create device → TV → Television (1080p)**.
2. Pick a **API 34** system image ("Android TV" / Google TV, x86_64) and finish.
3. Start it, then install and launch:

```bash
./gradlew installDebug
adb shell am start -n com.awadytv/.MainActivity
```

The app appears in the TV launcher row with its banner. Use the arrow keys, Enter (OK) and
Esc (Back) on the emulator. Hold Enter for a long-press (like a poster, favourite a channel).

## Design system

The look follows tvOS with Liquid Glass: a near-black cinematic canvas tinted by the focused
artwork, and translucent glass chrome (white 8–14 % fill, 1dp specular inner stroke, 3 % noise,
soft shadow) floating over it. Focused cards "lift" (scale 1.08, spring 0.7/400, white glass
border, bigger shadow), siblings dim to 85 %, posters tilt ±4° toward the D-pad direction and a
specular sweep crosses them. The top tab bar is a floating glass capsule with a sliding pill that
collapses to a line while you are in the content.

Glass has two rendering paths: **runtime blur** (`RenderEffect` on the backdrop layer behind the
glass) on API 31+ devices that are not low-RAM, and a **fallback** for typical TV boxes where the
backdrop is downsampled to 1/8 and blurred once at load time. Low-end mode also disables tilt and
caps shelves at 24 items. Typeface: Inter (bundled, OFL).

Full token tables and the component list: [`docs/DESIGN.md`](docs/DESIGN.md).

## How recommendations work

Everything runs on the TV by default; no network, no key, no telemetry.

1. **Signals** (Room `likes` table): an explicit ❤ on a movie/series (hold OK on a poster, or the
   heart on a detail page) counts 1.0; finishing ≥ 90 % counts as a weak like (0.5); abandoning
   < 10 % counts as a weak dislike (−0.5).
2. **Features** per title from Xtream metadata: genre (weight 3), director (2), top-5 cast (1.5),
   decade (1), plot keywords by TF-IDF over the catalog with stop-words removed (1). Movies that
   lack metadata use genre words found in their category name and their category.
3. **Taste profile** = sum of liked items' vectors × signal weight × `0.5^(days since like / 60)`.
4. **Scoring**: cosine similarity to the profile + `0.1 × rating/10` + 0.05 if added in the last
   14 days − 0.05 for categories you never open. Liked, watched and (while the parental lock is
   on) adult titles are excluded.
5. **Shelves**: *Recommended for you* (top 20, each with a "Because you liked X" caption),
   *Because you liked «X»* for the 3 most recent likes (nearest neighbours of that title), and
   *Top rated in {favourite genre}*. With no likes yet it falls back to top-rated + recently added.
6. Results are cached in Room for 6 hours and recomputed right after every like. Detail pages use
   the same index for "More like this".

The engine is `data/recommendations/RecommendationEngine.kt`, covered by deterministic unit tests
(`RecommendationEngineTest`).

**Optional Claude re-ranker.** In Settings → AI recommendations you can add your own Anthropic API
key (stored encrypted) and enable "Use Claude for smarter picks". The app then sends only the
titles, years and genres of your liked items plus the local engine's top 60 candidates (no
credentials, no plots, no server URL) and asks for a re-ranked top 20 with a one-line reason
each. One request per 6 hours or per new like; any failure silently keeps the local order. The
app is fully functional without it.

## Project layout

```
app/src/main/java/com/awadytv/
  core/designsystem   Liquid Glass tokens and components
  core/network        OkHttp/Retrofit, lenient JSON serializers, XtreamUrls
  core/database       Room entities and DAOs
  core/datastore      Settings (DataStore)
  core/security       EncryptedSharedPreferences, parental PIN
  data/xtream         Xtream API, DTOs, mappers
  data/repository     Profile, Catalog, Live, Movies, Series, EPG, Library
  data/recommendations RecommendationEngine, repository, re-rankers
  domain              Models
  feature/*           login, home, live, movies, series, search, settings, player
  navigation          Nav host, routes, tab shell
  work                Catalog and EPG workers
```
