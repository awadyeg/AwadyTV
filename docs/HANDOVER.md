# AwadyTV — Handover

Everything the next engineer (human or AI) needs to continue this project without the previous chat.
Written 2026-11-13 at commit `f5eb95e` on branch `ccr-ed8dfdad-yqtycj`.

> **Read first:** sections 1 (working agreements) and 2 (environment). The rest is reference.
> `README.md` and `docs/DESIGN.md` are **out of date** on the visual design and library versions
> (they still describe "Liquid Glass" and Compose BOM 2024.12.01). This file wins where they disagree.
> `docs/PLAN.md` is the original build plan (milestones M0–M5, v1.1) and is still a good map of intent.

---

## 1. Working agreements with the owner

- **Owner:** GitHub `awadyeg`. Writes in **Egyptian Arabic**; answer in the same language. Code, comments
  and commit messages stay in English.
- **The TV is the owner's living-room TV and they watch it.** Before any `adb` command that changes what is on
  screen (install, launch, key presses, screenshots of a playing app), make sure the owner said the TV is free.
  If they say they are watching ("انا بستخدم التلفزيون" / "don't install now"), stop and only build.
- **The TV app is signed in to the owner's real IPTV provider**, not the mock server. Do not leave test traces in
  their account: no likes / dislikes / favourites, no category reordering left changed, avoid moving watch
  positions (if you seek, seek back). In the taste trainer, test with **Up (skip)** only.
- **Never change TV system settings.** The Google TV screensaver can start while you send keys; if a key lands in
  system UI (it once opened *Screensaver settings*), press Home (`keyevent 3`) and do nothing else there.
  Wake the TV with `keyevent 224` before a test sequence.
- **Network:** the owner allowed only scanning the LAN and connecting to the TV. Do nothing else on their network.
- **Git:** commit and push **only when asked**. The repo has no git identity configured; commits so far use
  `git -c user.name=hassenm32 -c user.email=<owner's email> commit ...` (per-command, config untouched).
  Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
  Don't commit the screenshots in the repo root or `.kotlin/` (see §10).
- The owner likes: short Arabic status updates, numbers before/after for performance, screenshots of the TV.

---

## 2. Environment (owner's Windows PC)

| Thing | Value |
|---|---|
| Repo | `E:\Software\AwadyTV` (clone of `github.com/awadyeg/AwadyTV`, branch `ccr-ed8dfdad-yqtycj`) |
| JDK 17 | `E:\Software\awady-mobile-tools\jdk\jdk-17.0.20.1+1` |
| Android SDK | `E:\Software\awady-mobile-tools\android-sdk` (`local.properties`: `sdk.dir=E:/Software/awady-mobile-tools/android-sdk`, forward slashes) |
| adb | `/e/Software/awady-mobile-tools/android-sdk/platform-tools/adb.exe` |
| Temp dir for Gradle | `E:\Software\gtmp` (also holds test scripts and screenshots) |
| Python | `python` (3.12, with Pillow) — `python3` is NOT on PATH |
| Shells | Git Bash and PowerShell 5.1 |

**Build command (Git Bash).** Gradle fails with "Unable to establish loopback connection" unless TMP and the
unix-domain temp dir point to `E:\Software\gtmp`; in the agent sandbox Gradle and adb must run unsandboxed.

```bash
cd /e/Software/AwadyTV
export JAVA_HOME=/e/Software/awady-mobile-tools/jdk/jdk-17.0.20.1+1 TMP='E:\Software\gtmp' TEMP='E:\Software\gtmp' \
  JAVA_TOOL_OPTIONS='-Djdk.net.unixdomain.tmpdir=E:\Software\gtmp -Djava.io.tmpdir=E:\Software\gtmp'
./gradlew assembleQa            # release-speed APK -> app/build/outputs/apk/qa/app-qa.apk (~11 MB)
./gradlew testDebugUnitTest     # unit tests (all pass at f5eb95e)
./gradlew assembleDebug         # debug APK (~36 MB, Compose debug is several times slower — don't judge speed on it)
```

A full `assembleQa` takes 3–5 min (R8). If a build is killed mid-way, the next one may report BUILD FAILED from
a stale daemon lock; just run it again.

**Git Bash path gotcha:** `adb shell` paths get mangled by MSYS; prefix commands with `MSYS_NO_PATHCONV=1`.
With that set, pass Windows paths to `adb install` as `E:/Software/...` (a `/e/...` path then fails to stat).

---

## 3. The test device

| | |
|---|---|
| TV | Xiaomi Mi TV (model `MiTV-MOEU0`), Google TV, Android 14 (API 34) |
| SoC | MediaTek MT5896 (`merak`), 4× Cortex-A55, **32-bit userspace (armeabi-v7a)**, ~2.4 GB RAM |
| Panel | 3840×2160; apps render at **1920×1080** and SurfaceFlinger upscales (costs GPU time outside the app) |
| adb | Wireless debugging, `192.168.1.74:5555` (PC is `192.168.1.161` on `192.168.1.0/24`) |
| Notable apps | **Button Mapper** (`flar2.homebutton`) runs as an accessibility service — see §6.1. YouTube TV (`com.google.android.youtube.tv`). |

```bash
export MSYS_NO_PATHCONV=1; A="/e/Software/awady-mobile-tools/android-sdk/platform-tools/adb.exe"
$A connect 192.168.1.74:5555
A="$A -s 192.168.1.74:5555"
$A install -r E:/Software/AwadyTV/app/build/outputs/apk/qa/app-qa.apk
$A shell cmd package compile -m speed-profile -f com.awadytv   # AOT, closer to Play-installed speed
$A shell am force-stop com.awadytv; $A shell am start -W -n com.awadytv/.MainActivity   # prints TotalTime
$A exec-out screencap -p > E:/Software/gtmp/shot.png
```

Driving the UI: tapping does nothing on a TV; use key events — `19` up, `20` down, `21` left, `22` right,
`23` OK, `4` back, `3` home, `85` play/pause, `224` wake, `input keyevent --longpress 23` for a long press.
`input` is `cmd input` on this Android (binder call, no JVM per key). Wait ~0.7 s between keys; the app drops
very fast presses. Helper used throughout: `E:\Software\gtmp\tv.sh "<keys>" <shotname>` where keys are codes,
`sN` = sleep N s, `L23` = long-press OK; it saves `E:\Software\gtmp\<shotname>.png`. To review several
screenshots at once, tile them with Pillow and read the single image.

`simpleperf` is blocked by the kernel (`perf_event` permission denied) and `sendevent` to `/dev/input/*` is
denied. What works for profiling is in §8.

---

## 4. What the app is

Android TV player for **Xtream Codes** IPTV providers (sign in with server URL + username + password, like
IPTV Smarters). Live TV with EPG, Movies, Series, Search, Settings, on-device recommendations with an optional
Claude re-ranker. Player only; ships no content.

Stack: Kotlin 2.0.21, Jetpack Compose (**BOM 2025.06.01 = Compose 1.8**), `androidx.tv:tv-material` 1.0.0,
Hilt 2.52, Room 2.6.1, DataStore, Media3 ExoPlayer 1.5.1 (+HLS, OkHttp datasource), Retrofit +
kotlinx.serialization, Coil 3.0.4, WorkManager, Anthropic Java SDK 3.3.0. AGP 8.7.3, compile/target SDK 35,
minSdk 24. Versions live in `gradle/libs.versions.toml`.

### 4.1 Package map (`app/src/main/java/com/awadytv`)

| Package | What's there |
|---|---|
| `navigation/` | `AwadyRoot` (theme + **global key handling**, §6.2), `AppNavHost` (+ `NavControllerNavigator`), `Routes.kt` (routes + `AppNavigator` interface), `Destinations.kt`, `MainShell` (tab bar over tab content), `TabContent`, `StartScreen` (login / sync / home decision + resume-playback) |
| `core/designsystem/` | Tokens (`AwadyColors`, `AwadyDimens`, `AwadyMotion`), `GlassSurface` (now flat), `FocusLift` (card focus), `Cards.kt` (`Artwork`, `ChannelLogo`, `PosterCard`, `LandscapeCard`, `ChannelCard`), `Shelf`, `TopTabBar`, `ListRow` (`GlassListRow`, `RailItem`, `PinDialog`), `Controls`, `Sheets`, `States`, `Backdrop` (no-op now), `Theme` (`PerformanceProfile`) |
| `core/database/` | Room `AppDatabase` (**version 1, `fallbackToDestructiveMigration`**), `Entities.kt`, `Daos.kt` |
| `core/datastore/SettingsRepository` | DataStore prefs: app settings, **category order per profile+section**, **last playback** |
| `core/network`, `core/security` | Xtream URLs, OkHttp, error mapping, `SecretStore` (encrypted prefs: passwords, Claude key), `ParentalGate` |
| `data/xtream` | DTOs, mappers, `XtreamClient` |
| `data/repository` | `ProfileRepository`, `CatalogRepository` (sync), `LiveRepository`, `MoviesRepository`, `SeriesRepository`, `EpgRepository`, `LibraryRepository` (favourites, likes/dislikes/skips, progress, category opens) |
| `data/recommendations` | `RecommendationEngine` (pure, unit-tested), `RecommendationRepository` (runs it, stores rows, For-you, taste cards), `ReRanker` / `ClaudeRecommender` (model `claude-haiku-5-5`, user's own key) |
| `feature/*` | `home`, `live` (list + player), `movies`, `series`, `player` (VOD), `search`, `settings`, `login`, `common` (`CatalogBrowse` for Movies/Series, `CategoryRail`, `DetailComponents`), **`taste`** (taste trainer) |
| `work/BackgroundWork` | Periodic catalog refresh (12 h) and XMLTV EPG refresh |

### 4.2 Build types

- `debug` — slow Compose, for development only.
- `release` — R8 + resource shrinking. **No release signing configured yet.**
- **`qa`** — `initWith(release)`, signed with the debug key, plus `app/src/qa/AndroidManifest.xml`
  (`<profileable android:shell="true"/>`). This is what goes on the TV; it installs over a debug build
  (same key). `proguard-rules.pro` has `-dontwarn com.google.auto.value.**` (pulled in through the
  Anthropic SDK / OpenTelemetry) — release builds failed before that rule existed.

---

## 5. Commit history since the original milestones

| Commit | Summary |
|---|---|
| `3fe077e` … `bf1ea6b` | Original plan, M0–M5 and v1.1 (see `docs/PLAN.md`) |
| `03f064b` | Mock Xtream server (`tools/mock-xtream`, §9) |
| `6488461` | Performance pass: removed Liquid Glass, cut overdraw, Compose 1.8, logo fix, Home Down-key fix, qa build type |
| `6aa4fba` | 15 UX requests: player Back/seek/animations/resume, Live TV OK/Back, tab order, Search keyboard, Home carousel + scrolling, category reordering, For-you category, favourites→recs, Dislike, trailer, long-press fix, **VOD exit crash fix** |
| `c523a42` | Keep screen on during playback; controls auto-hide after a relaunch resume |
| `f5eb95e` | Engine features that fit IPTV catalogs; swipe taste trainer |

---

## 6. Behaviour and design decisions (the non-obvious parts)

### 6.1 Performance — why the UI looks the way it does

Original state on the TV: cold start 3.6 s, Home scrolling p50 200 ms/frame, 100 % janky, GPU pegged.
Causes and fixes:

- **Liquid Glass was the main cost.** `PerformanceProfile` enabled runtime blur on this non-low-RAM device:
  full-screen `BlurEffect` backdrop, blur per glass surface, shadows, noise, tilt, specular sweep. All removed:
  `GlassSurface` is an opaque fill + 1 dp hairline; `FocusLift` is a 120 ms scale + white border (scale in
  the `graphicsLayer` lambda, clip via `graphicsLayer { shape; clip = true }`); `AppBackdrop` draws nothing
  (window background is already the canvas colour); `PerformanceProfile.detect` returns `runtimeBlur = false`.
  Parameters like `parallax`/`specular`/`elevation` are kept for source compatibility and ignored.
- **Overdraw:** placeholder gradients are drawn only until an image loads; hero art covers the right 72 % with
  scrims only where they fade; Coil crossfade off; no shelf enter alpha animation.
- **Accessibility:** with *any* accessibility service on (the owner has Button Mapper), Compose 1.7 rebuilt the
  semantics tree every frame (~24 % of main thread). Upgrading to Compose 1.8 (BOM 2025.06.01) fixed most of it.
  Don't downgrade the BOM.
- Result: cold start ~0.4–0.6 s, Home p50 ~24 ms, p90 ~57 ms, ~16 % janky. The Google TV launcher on the same TV
  measures p50 31 ms / p90 40 ms. Remaining cost is generic Compose layout + GC; the 4K upscale is outside the app.

### 6.2 Global key handling (`navigation/AwadyRoot.kt`)

- **Back** is intercepted in the root `onPreviewKeyEvent` and sent to `OnBackPressedDispatcher` on key-up.
  Reason: Compose otherwise uses Back to move focus *out* of the focused item first ("deselect"), so every
  `BackHandler` needed two presses. All screens rely on `BackHandler` now; side sheets have their own.
- **OK/Enter auto-repeat** (`repeatCount > 0`) is swallowed. Reason: after a long-press opens a new screen, the
  repeats "pressed" the newly focused button (long-press on Continue Watching started playback). Long-presses are
  timed from the first key-down, so nothing needs repeats.

### 6.3 Focus and scrolling

- `TopTabBar`: Down from any tab goes to `contentFocusRequester` (the tab content is a `focusGroup` in
  `MainShell`). Without it Home was unreachable: Home's content starts under the bar, so 2-D focus search never
  considered it "below".
- Tab order (`MainTab` enum): **Search, Home, Series, Movies, Live TV, Settings**; app opens on Home.
  Focusing a tab selects it after a 350 ms dwell; **OK on Search** opens the on-screen keyboard
  (`MainShell.openSearchKeyboard` → `SearchScreen(openKeyboard)`).
- `CategoryRail` (Live/Movies/Series): entering the rail focuses the **selected** category
  (`focusRestorer(selectedFocus)`), not the first one.
- Home uses `CalmVerticalScroll` (custom `BringIntoViewSpec`): no scroll while the focused item is comfortably
  visible, otherwise minimal. Rows keep the platform spec. `Shelf` brings the **whole shelf** into view when focus
  enters it, so moving along a row never moves the page. Recommended cards reserve 2 caption lines
  (`PosterCard(captionLines = 2)`) so row height is constant. Hero buttons: focusing them scrolls to the top;
  Left on Play / Right on More info flips the carousel.

### 6.4 VOD player (`feature/player`)

- Custom video output (`VideoSurface.kt`: `SurfaceView` + `SubtitleView`, no `PlayerView`). Because of that it
  sets **`keepScreenOn`** itself while playing or buffering-with-play-requested; otherwise the TV screensaver /
  sleep closed the app mid-film.
- **ExoPlayer may only be touched on the main thread.** `appScope` is `Dispatchers.Default`; reading the player
  inside it crashed the app every time the player was left (`onStop`). `progressWrite()` reads position/duration
  on main and returns the DB write lambda. Keep this pattern.
- Back: hides next-episode card → hides controls → leaves. Controls: title slides up, transport slides down.
- Left/Right with controls hidden → `quickSeek(±10 s)`: Netflix-style ring indicator on that side, presses
  accumulate (+10, +20…), the actual `seekTo` happens 300 ms after the last press.
- Controls auto-hide after 5 s **only while playing**; the timer restarts when playback actually starts
  (`onIsPlayingChanged`), otherwise they stuck after a resume.
- **Resume after the app was closed:** `SettingsRepository.lastPlayback` = `"movie:<id>"` /
  `"episode:<id>:<seriesId>"` is set when playback starts and cleared in `onCleared` (user left the player).
  `StartViewModel` reopens that player on top of Home. Returning from the TV Home button resumes playing (`onStart`).
- Progress is saved every 10 s while playing, on stop and on exit.

### 6.5 Live TV (`feature/live`)

- In the player: OK opens the channel list sheet; Back hides the channel banner first, then leaves; banner
  auto-hides after 4 s; Up/Down zap; Left also opens the list; red/Bookmark toggles favourite; digits jump.
- HLS → MPEG-TS fallback on error. `ChannelLogo` draws logos on a neutral tile (the old gradient placeholder
  showed as coloured bands around every logo).

### 6.6 Recommendations (`data/recommendations`)

- **Signals** (`likes` table, `LikeSource`): `explicit` (+1, Love / taste-trainer right), `dislike` (−1, thumbs-down
  / taste-trainer left), `implicit` (+0.5, finished ≥ 90 %), `skip` (not a signal; hides a trainer card for 30 days),
  legacy `implicit_negative` (ignored — stopping early is no longer a dislike). **Favourites** (movies/series) count
  as explicit likes. Watched and rated items are excluded from results.
- **Engine features** (all scaled by a soft IDF): genre, director, cast, decade, plot TF-IDF, **title prefix tags**
  (`"AR:"`, `"4K-AR:"`, `"AR-SUBS:"`, `"AR|"` → language/subs/quality; capitals only, so `"Mission: Impossible"`
  isn't a tag), category id, **category-name words**, title words. Reason: Xtream movie lists contain no
  genre/cast/plot until a title's info is fetched, so before this most movies were described by decade and category
  only and results ignored language. Tests in `RecommendationEngineTest`.
- `refresh()` runs at startup (6 h TTL), after catalog sync, and 800 ms after any like/dislike/favourite change.
  After a like it **reuses the cached index** (`rebuildIndex = false`); rebuilding takes tens of seconds on the TV
  for a catalog capped at 15 000 items. The index is in memory only.
- Results are stored in the `recommendations` table: Home shelves, plus **`foryou:movie` / `foryou:series`** rows
  (top 200 each) for the "For you" category at the top of Movies and Series. Home's `shelves()` filters `foryou:`
  out. If For-you rows are missing, `forYou()` triggers a refresh and the UI shows "Preparing your picks".
- Optional Claude re-ranker of the "Recommended for you" shelf (Settings toggle + user API key, encrypted).
- **Taste trainer** (`feature/taste/TasteTrainerScreen.kt`, Settings → AI recommendations → *Train your taste*):
  Right = love, Left = not for me, Up = skip, Back = done. Cards: ~1/3 from the stored For-you ranking, the rest
  round-robin across categories (a shuffle of each category's 40 best-rated titles), never already-answered titles. Movie plot/genre
  fetched on demand (`ensureInfo`) for the card on screen and the next. Ratings are written with `notify = false`;
  one `notifyLikesChanged` when the screen closes. Card picking reads entities straight from Room (not the index).

### 6.7 Other

- **Category reordering:** hold OK on a rail category → Up/Down moves it → OK or Back drops it. Saved per
  profile + section in DataStore (`category_order_<profile>_<kind>`); unknown ids keep provider order after.
- **Trailer:** `openTrailer()` sends `https://www.youtube.com/watch?v=<id>` to `com.google.android.youtube.tv`
  explicitly (the old `vnd.youtube:` link only opened YouTube's start screen).
- **Detail pages:** Play/Resume, Start over, Love, Dislike, Favourite, Trailer (icon button so the row fits).

---

## 7. Mock Xtream server (`tools/mock-xtream`)

`python tools/mock-xtream/server.py --port 8080` (standard library only). Accounts `test/test` (active),
`expired`, `banned`, `disabled` (same password), anything else = wrong credentials. All `player_api.php` actions,
`get_short_epg`, `get_simple_data_table`, `xmltv.php`. Streams 302-redirect to legal open content (archive.org,
blender.org, Apple/Shaka/Mux/Unified Streaming test streams). `--delay N` for slow-server tests. Each kind has an
"Adult (PIN test, safe content)" category. Details in its README. It is **not running** now.
Public access (from anywhere) was planned via `cloudflared tunnel --url http://localhost:8080`, but downloading
cloudflared was not approved; the owner would install it (`winget install --id Cloudflare.cloudflared`).

---

## 8. How performance was measured

```bash
# Home benchmark (E:\Software\gtmp\bench2.sh): cold start + a fixed key sequence, then frame stats.
$A shell am force-stop com.awadytv; sleep 2; $A shell am start -W -n com.awadytv/.MainActivity | grep TotalTime; sleep 6
$A shell dumpsys gfxinfo com.awadytv reset >/dev/null
k(){ for i in $(seq $2); do $A shell input keyevent $1; sleep 0.25; done; }
k 20 6; k 22 5; k 21 5; k 19 3; k 22 4; k 19 3; sleep 2
$A shell dumpsys gfxinfo com.awadytv | grep -E "Total frames|^Janky frames:|percentile|Slow UI"
```

- Per-phase breakdown: `dumpsys gfxinfo com.awadytv framestats`, then compute
  input / animation+composition / layout / draw / sync / GPU from the columns (`E:\Software\gtmp\phases.py`,
  slowest frames: `worst.py`).
- CPU profile (simpleperf is blocked): `am profile start --sampling 1000 com.awadytv /data/local/tmp/x.trace`,
  run the keys, `am profile stop`, pull, parse the ART trace (`arttrace.py`), and de-obfuscate with
  `app/build/outputs/mapping/qa/mapping.txt` (`deob.py`). Works because the qa build is profileable.
- Compare with other apps: `dumpsys gfxinfo com.google.android.apps.tv.launcherx`.
- Keep-screen-on check: `dumpsys window windows | grep -E "^  Window #|fl=" | grep -A1 com.awadytv/` should show
  `KEEP_SCREEN_ON` while playing and not while paused.

(The helper scripts live in `E:\Software\gtmp`, outside the repo; recreate them from these descriptions if missing.)

---

## 9. Manual test checklist (TV)

1. Launch → Home, tab bar order Search·Home·Series·Movies·Live TV·Settings, cold start < 1 s.
2. Down → hero buttons with the whole hero visible; Left/Right flip the carousel; rows don't nudge the page.
3. Long-press a Continue Watching card → detail page opens, nothing plays.
4. Play a movie: Back hides controls; Right×3 shows "+30s"; Left×3 back; Back leaves to Home (no crash).
5. Force-stop while a movie plays → relaunch opens the player near the position; controls hide by themselves.
6. TV Home button while playing → reopen → keeps playing. Screen never sleeps while playing.
7. Live TV: Down lands on the selected category; open a channel; Back hides banner; OK opens list; Back closes it.
8. Search: Left from Home, OK → keyboard opens.
9. Movies/Series: "For you" first; hold OK on a category → move → put it back.
10. Settings → AI recommendations → Train your taste: first card appears; **Up** skips (don't love/dislike on the
    owner's account).

---

## 10. Known issues and suggested next steps

Ordered roughly by value.

1. **Taste trainer first card takes ~10 s** on the real catalog (loads every movie/series entity to sample).
   Use SQL sampling (random rows per category with a poster, excluding answered ids) instead of loading all.
2. **Recommendation pass is heavy**: building the index for ~15 k titles takes 30–45 s of CPU with heavy GC on this
   TV, at every app start (index is memory-only), and the first "For you" waits for it. Options: persist the index
   (or feature vectors) in Room, build incrementally after sync, run it in WorkManager, lower the cap.
3. **Movie metadata gap:** most movies have no genre/plot/cast locally. Fetching `get_vod_info` in the background
   for liked, rated and top-candidate titles (rate-limited; providers dislike bursts) would improve the engine a lot.
4. Frame time p90 ~57 ms on Home; GC churn (~3–5 MB/s while navigating). Further wins need allocation profiling.
5. After leaving the Live player, focus returns to the tab bar instead of the channel row.
6. **Docs are stale:** update `README.md` (design, versions, qa build, new features) and `docs/DESIGN.md`
   (flat surfaces instead of Liquid Glass). This file is the current truth.
7. Launcher icon is a plain play triangle (not adaptive) and the TV banner shows no app name.
8. Room is version 1 with `fallbackToDestructiveMigration()`: any schema change wipes local data (catalog re-syncs,
   but likes/favourites/progress are lost). Add real migrations before changing entities.
9. No release signing config; distribution currently uses the debug-signed `qa` APK.
10. Repo hygiene: add `.kotlin/` to `.gitignore`; the PNG screenshots in the repo root and
    `tools/mock-xtream/tv-home.png` are untracked test artifacts (delete or move them).
11. CI (`.github/workflows/android.yml`) builds debug + unit tests only; consider adding `assembleQa` to catch R8 issues.

## 11. State of the owner's TV/account at handover

- App installed: qa build of `f5eb95e`, signed in to the owner's real provider.
- Test side effects: "The Odyssey" progress moved ~20 s; Movies category order was saved identical to the
  original; two taste-trainer cards skipped (hidden 30 days). No likes, dislikes or favourites were added by testing.
  The owner has used the TV since, so check the current state instead of assuming.
