# MusicSM Desktop

Desktop port of [MusicSM](../MusicSM) — a dark, Spotify-like music streaming app with metadata
from YouTube Music (InnerTube) and audio via NewPipeExtractor. Built with **Kotlin + Compose
Multiplatform for Desktop** so the domain and data layers are shared verbatim with the mobile
app; only the UI shell and playback engine are desktop-specific.

## Status

Feature parity with the mobile app, minus Android-only pieces (see bottom of list):

- **Navigation**: the mobile app's landscape `SideDock` — one Liquid Glass pane on the left with a
  sliding selection puck (Search / Listen / Library / Downloads / Local / Stats / Settings), with
  back-stack; window sized to the usable screen.
- **Home**: personalized feed like mobile (recently played, quick picks, daily rotation, forgotten
  favorites, ranked with `ShelfRanker`/`TasteProfile`) plus YouTube Music shelves (song rows and
  album/artist/playlist cards); more shelves load as you scroll. A **List / Cards** switch in the
  header (also Settings → Appearance → "Cards on Home") shows songs as mobile's horizontal card
  shelves instead of rows; right-click a card for the song menu.
- **Search**: dedicated screen with All / Songs / Videos / Albums / Artists filters, top result,
  recent-search history (`search_history.json`). Videos follow the "Videos in search" setting.
  Searches as you type, as on mobile: 350 ms after typing pauses (Enter searches at once), with
  the previous results kept on screen and a small spinner in the field until the new ones arrive;
  a lookup overtaken by further typing is dropped. Queries join the history when submitted with
  Enter or when one of their results is opened, not while being typed. The filter chip is kept
  while refining a query and resets when the box is cleared.
  Songs, albums and artists opened from results are kept as a **Recent searches** card shelf
  (`search_recent_items.json`, newest first, × on hover to remove). **Browse all** uses mobile's
  genre tiles (gradient, sheen and a large genre icon cropped into the corner), after desktop's
  New releases and Charts.
- **Song menu** (⋮ or right-click on any song): play next, add to queue, start radio, add to /
  new playlist, go to artist/album, download, share, like.
- **Library**: liked songs, local playlists, recent plays (`library.json`); **playlist import**
  from Spotify / Apple Music / YouTube / YouTube Music links; **playlist sharing** via a
  backend-free `musicsm://` link + QR code (ZXing), and opening shared links or QR images.
- **Downloads**: offline copies in the downloads folder, played in preference to streaming;
  Downloads screen with play all / shuffle / delete; "Download all" on album/playlist pages.
- **Local music**: scans chosen folders (tags + artwork via jaudiotagger), search, sort, play.
- **Player**: the mobile `MiniPlayer` Liquid Glass pill floating over the content, and a full-screen
  Now Playing laid out like mobile's (blurred artwork backdrop, breathing cover, Apple-style
  scrubber, controls card around the hue-tinted frosted `PlayPauseButton`, glassy volume track,
  lyrics / output / queue row). Shuffle, repeat off/all/one, speed, sleep timer with fade-out,
  equalizer; the output picker and sleep timer open as mobile's Liquid Glass sheet. Autoplay radio
  continues with related tracks when the queue ends; the queue (paused) and volume are restored on
  launch. As on mobile, the next track's stream URL is looked up ahead of time, so skipping starts
  almost at once; the old track stops the moment you switch, and the play button shows a spinner
  while a new track loads.
- **Motion**: mobile's transitions — Now Playing slides up from the mini player on a spring (the
  pill fades under it), pushed pages slide in from the right and back out the other way, dock tabs
  cross-fade, and the queue panel slides in. Going back keeps a page's scroll position and filters.
  The seek bar and word-synced lyrics are interpolated per frame between libVLC's position ticks
  (`SmoothPosition.kt`), and playback position lives in its own flow, so the rest of the app doesn't
  recompose several times a second while music plays; only the lyric line being sung reads it.
  The first open of Now Playing in a session used to stutter for a few hundred ms while the GPU
  compiled its blur, glass and lyric-line shaders mid-slide. So once there is a song, at the first
  moment the mouse and keyboard have been still for 2 s, Now Playing (with its lyrics panel) is
  drawn once underneath the app, which is a hair short of opaque for that ~1.5 s. The one-off stall
  lands while nothing moves, and the first real open is as smooth as later ones.
- **Synced lyrics**: the mobile lyrics stack (Apple Music, BiniLyrics, LyricsPlus, SimpMusic,
  LRCLIB, KuGou, Unison, YouTube Music) with word-by-word highlighting, click-to-seek and per-song
  sync offset, shown beside the artwork in Now Playing. The panel opens on its own when a song has
  lyrics (Settings → Lyrics → "Open lyrics automatically", on by default); closing it keeps it
  closed for that song, and a panel that opened itself closes again for a song without lyrics.
- **Equalizer**: libVLC presets, preamp and 10 bands, persisted.
- **Stats**: play-event log (`stats.json`) with totals, top songs/artists, activity and
  listening-clock charts per range.
- **Settings**: playback, search, lyrics source order/toggles, audio, folders, desktop, data.
- **Desktop extras**: keyboard shortcuts (Space play/pause, Ctrl+←/→ prev/next, Shift+←/→ seek,
  Ctrl+↑/↓ volume, Ctrl+L like, Ctrl+S shuffle, Ctrl+R repeat, Esc back), global media keys
  (JNativeHook), system tray with optional minimize-to-tray.
- **Glass design** — the mobile app's `Glass.kt` ported as-is, with the same libraries and
  versions: the byte-identical dark "Stitch" palette; `GlassPanel` frosted panels via
  [Haze](https://github.com/chrisbanes/haze) 1.6.10; and **real Liquid Glass** (vibrancy, blur,
  lens refraction at the rounded edge, specular highlight) via Kyant's
  [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 2.0.1, which runs on Compose Desktop.
  Like mobile, the content is recorded once as both the Haze source and the Liquid Glass layer,
  the chrome floats outside it so it can refract it, and lists fade into the background above the
  mini player. Shelf cards are plain artwork, as on mobile — glass is only for floating chrome.
- **Album-art colours** — mobile's artwork theming, with AndroidX Palette's algorithm ported to
  plain Kotlin (`ui/theme/ArtworkPalette.kt`: same median-cut quantizer, targets and scoring, pick
  = vibrant → dominant → dark vibrant → muted). As on mobile, Now Playing is tinted by the cover
  (background wash, volume bar, labels), lyrics sit on the cover's tint, album/artist/playlist
  pages wash into a deepened cover colour, and the Search header glows with the top result's cover.
  Every other tab carries the same header gradient in the accent colour; it is drawn once behind
  the pages (`HeaderWash`), so it holds still while tabs cross-fade and eases between colours.
  **Theme from artwork** (Settings → Appearance, on by default here, opt-in on mobile) recolours the
  whole app's accent to the current song's cover; covers without a real hue keep the stock coral,
  and very dark or pale ones are lifted so buttons and links stay readable. Tinted page backgrounds
  run under the glass side dock to the window edge.
- **AMOLED black** (Settings → Appearance) — mobile's pure-black palette for OLED screens.
- **Window frame** — on Windows the native title bar and border are painted in the app's
  background colour (DWM caption/border colours, dark caption buttons), following AMOLED.
- **App icon** — the phone app's launcher icon (`ic_launcher_img`), cropped and rounded the way
  Android shows it: every size in `resources/icons` for the title bar, taskbar, Alt+Tab and tray
  (`AppIcon.kt`), and `desktopApp/icons/icon.ico` / `.icns` / `.png` for the installers.

Not ported (Android-only or heavy): crossfade / DJ mix, animated motion artwork, Wear OS / widget
/ Quick Settings tile, in-app updater.

All app data lives in `~/.musicsm-desktop/` (`settings.json`, `library.json`, `stats.json`,
`downloads.json`, `queue.json`, …).

## Modules

| Module | Role |
|--------|------|
| `:innertube` | Copied verbatim from the mobile app — YouTube Music InnerTube client. Plain JVM already. |
| `:motionart` | Copied verbatim — animated cover-art lookup. Plain JVM already. |
| `:domain` | Copied verbatim from the mobile app's `domain/` package — models, repository interfaces, recommend/match/share logic. Pure Kotlin, zero UI/platform deps. |
| `:data` | The mobile app's `data/source/youtube` package (YouTubeMusicSource, NewPipeMusicSource, stream selection), plus the lyrics providers/repository and playlist-import clients (Spotify, Apple Music, YouTube). Android APIs swapped for JVM ones (`Log` → `println`, `android.util.Xml` → kXML2, `android.util.Base64` → `java.util.Base64`, `AppPreferences` → `LyricsPreferences`). Unit tests ported alongside. |
| `:desktopApp` | Compose Desktop UI, manual DI (`AppGraph`), and playback via [vlcj](https://github.com/caprica/vlcj) (libVLC bindings) — the desktop equivalent of the mobile app's Media3/ExoPlayer bridge. |

## Requirements

- JDK 25 — Gradle's daemon toolchain (`gradle/gradle-daemon-jvm.properties`); Android Studio's
  bundled JBR is one, and is enough to build and run.
- [VLC](https://www.videolan.org/vlc/) installed on the host machine (desktopApp uses libVLC
  through vlcj for audio playback — Media3 is Android-only, so there's no direct equivalent).

## Running

```powershell
.\gradlew.bat :desktopApp:run
```

## Packaging

Installers are built by `jpackage`, which Android Studio's JBR doesn't include, so point packaging
at a full JDK 25 or newer:

```powershell
.\gradlew.bat :desktopApp:packageMsi "-PpackagingJdk=C:\Program Files\Java\jdk-27"
```

The installer lands in `desktopApp\build\compose\binaries\main\msi\` and adds a Start-menu entry.
`createDistributable` builds the unpacked app (`...\main\app\`) without an installer.

## Notes carried over from the mobile app

- Not distributed on app stores: same YouTube ToS / GPLv3 (NewPipeExtractor) considerations apply.
- Stream URLs expire (~6h) and are IP-bound — cached in memory for ~5h (`MusicRepository`), and
  re-resolved once if libVLC reports a playback error.
- `YouTubeMusicSource` dispatches by id shape (`MPREb_…` album / `UC…` channel → InnerTube; URLs /
  bare names → NewPipe). Metadata falls back to NewPipe silently; stream resolution has no fallback.
