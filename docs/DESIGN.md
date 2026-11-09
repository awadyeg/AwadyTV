# AwadyTV design system — tvOS + Liquid Glass

All tokens and components live in `app/src/main/java/com/awadytv/core/designsystem`.
The look follows plan §5: a dark cinematic canvas where artwork does the work, and chrome
rendered as translucent **glass** that floats over content.

## Tokens

### Colour (`AwadyColors`)
| Token | Value | Use |
|---|---|---|
| `Canvas` | `#0B0B0F` | App background, base of every gradient |
| `CanvasRaised` | `#16161D` | Opaque fallback surfaces |
| `TextPrimary` / `Secondary` / `Tertiary` | white 100 / 70 / 45 % | Text hierarchy |
| `TextOnFocus` | `#0B0B0F` | Labels on focused (white) controls |
| `Accent` | `#0A84FF` | Tinted glass buttons, links |
| `Like` | `#FF375F` | Like heart |
| `GlassRegular` | white 14 % | Tab bar, side rails, overlays |
| `GlassClear` | white 8 % | Hero text scrims, resting chips |
| `GlassFocused` | white 90 % | Focused buttons / tab pill (tvOS) |
| `StrokeHighlight` → `StrokeFade` | white 45 % → 5 % | 1dp specular inner stroke, top-left → bottom-right |
| `Shadow` | black 35 % | Soft outer shadow of glass |

The background behind everything is `AppBackdrop`: `Canvas` + the focused item's artwork
(blurred) + a radial tint of its Palette-extracted colour, animated with `animateColorAsState`
over **600 ms**, and a vertical scrim so the lower half stays near-black.

### Shape & spacing (`AwadyDimens`)
- Corner radii: 12 (fields), 16 (cards), 20 (default glass), 24, 28 (sheets/dialogs); pills are 50 %.
- Overscan-safe area: **48dp horizontal, 27dp vertical** on every screen (`SafeArea`).
- Poster 132×198dp (2:3), landscape card 248×140dp (16:9), channel tile 176×99dp, side rail 300dp.
- Tab bar 52dp tall, collapsing to a 6dp line.

### Typography (`AwadyType`)
| Style | Size / weight | Use |
|---|---|---|
| `Display` | 48 / SemiBold | Hero titles |
| `Title` | 28 / SemiBold | Screen and detail titles |
| `Headline` | 22 / SemiBold | Panel titles |
| `Body` | 18 / Regular | Copy, plot |
| `Label` | 16 / Medium | Buttons, tabs, list rows |
| `Caption` | 14 / Regular | Meta lines only |
| `ShelfHeader` | 16 / SemiBold, uppercase, `0.08em` tracking | Shelf headers |

Font: **Inter**, bundled as four static Latin cuts (400/500/600/700) in `res/font`. The TTFs were
converted losslessly from the WOFF files of the `@fontsource/inter` 5.2.8 distribution (the only
reachable source; rsms.me and GitHub were blocked), so the variable font is not used. Glyphs
outside Latin fall back to the system font automatically. Licence: SIL OFL 1.1 (`docs/INTER-LICENSE.txt`).

### Motion (`AwadyMotion`)
- Focus lift: scale **1.08**, spring damping **0.7**, stiffness **400** (buttons 1.06).
- Unfocused siblings in a focused shelf dim to **85 %**.
- Parallax tilt **±4°** toward the D-pad direction that moved focus, 200 ms, then settles in 260 ms.
- Specular sweep: a diagonal white 22 % band crosses the card in 750 ms when it gains focus.
- Shelves fade + slide in from **24dp** over **350 ms** (once per shelf).
- Screen/tab transitions: crossfade + scale **0.98 → 1** (300 ms).
- Nothing bounces more than once (all springs are near-critically damped).

## Glass rendering paths

`GlassSurface` = backdrop slice + fill + 3 % noise + specular stroke + shadow. The backdrop slice is
the region of the shared `GlassBackdrop` image that lies behind the surface (located with
`positionInRoot`), so the glass shows the artwork *behind* it rather than blurring itself.

| | Runtime blur path | Fallback path |
|---|---|---|
| When | API 31+ **and** `!ActivityManager.isLowRamDevice()` | API < 31 or low-RAM (most TV boxes) |
| Backdrop image | 320×180 decode | 1/8 downsample + 3-pass box blur once at load (`DownsampleBlurTransformation`, cached by Coil) |
| Blur | `graphicsLayer { renderEffect = BlurEffect(…) }` on the backdrop layer behind the glass | none at runtime |
| Extras | parallax tilt, specular sweep | tilt disabled on low-end; shelves capped at 24 items |

`PerformanceProfile.detect()` decides the path; Settings → Playback & performance can force the
low-end path.

## Components
| Component | File | Notes |
|---|---|---|
| `AwadyTheme` | `Theme.kt` | tv-material3 dark scheme + locals (`LocalPerformance`, `LocalGlassBackdrop`, `LocalFocusDirection`) |
| `AppBackdrop`, `BackdropController` | `Backdrop.kt` | Adaptive artwork background; screens call `LocalBackdrop.current.show(url)` on focus |
| `GlassSurface` (`Regular`/`Clear`/`Tinted`) | `GlassSurface.kt` | The glass material |
| `FocusLift` | `FocusLift.kt` | Lift + border + shadow + dim + tilt + specular for any card |
| `GlassButton`, `GlassIconButton`, `GlassChip`, `GlassTextField` | `Controls.kt` | Focused controls invert to white glass with dark labels |
| `PosterCard`, `LandscapeCard`, `ChannelCard`, `Artwork`, `ProgressLine` | `Cards.kt` | Artwork has a gradient + initials placeholder |
| `Shelf` | `Shelf.kt` | Uppercase header, `LazyRow` with `focusRestorer`, enter animation |
| `TopTabBar` | `TopTabBar.kt` | Floating capsule, sliding pill, dwell-to-select, collapses to a line |
| `GlassSideSheet`, `GlassDialog`, `SafeArea` | `Sheets.kt` | Channel list / track picker sheets, PIN dialog |
| `LoadingView`, `EmptyView`, `ErrorView`, `GlassSpinner` | `States.kt` | Every list/grid uses these; `ErrorView` grabs focus on its retry button |

## Focus rules
- Every focusable element has a visible focus state (lift + white border, or white glass inversion).
- First focus on each screen is requested explicitly once content is ready.
- Rows use `focusRestorer()` so returning to a shelf lands on the last focused card.
- Text fields hand D-pad Up/Down back to focus search, so they never trap focus.
- Back: sheets/overlays close first, then non-Home tabs return to Home, then the app exits.
