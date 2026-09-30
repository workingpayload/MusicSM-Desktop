# MusicSM Desktop

Desktop port of [MusicSM](../MusicSM) — a dark, Spotify-like music streaming app with metadata
from YouTube Music (InnerTube) and audio via NewPipeExtractor. Built with **Kotlin + Compose
Multiplatform for Desktop** so the domain and data layers are shared verbatim with the mobile
app; only the UI shell and playback engine are desktop-specific.

## Status

Feature parity with the mobile app, minus Android-only pieces (see bottom of list):

- **Navigation**: glass sidebar (Home / Search / Library / Downloads / Local / Stats / Settings)
  with back-stack; window sized to the usable screen.
- **Home**: personalized feed like mobile (recently played, quick picks, daily rotation, forgotten
  favorites, ranked with `ShelfRanker`/`TasteProfile`) plus YouTube Music shelves (song rows and
  album/artist/playlist cards); more shelves load as you scroll.
- **Search**: dedicated screen with All / Songs / Videos / Albums / Artists filters, top result,
  recent-search history (`search_history.json`). Videos follow the "Videos in search" setting.
- **Song menu** (⋮ or right-click on any song): play next, add to queue, start radio, add to /
  new playlist, go to artist/album, download, share, like.
- **Library**: liked songs, local playlists, recent plays (`library.json`); **playlist import**
  from Spotify / Apple Music / YouTube / YouTube Music links; **playlist sharing** via a
  backend-free `musicsm://` link + QR code (ZXing), and opening shared links or QR images.
- **Downloads**: offline copies in the downloads folder, played in preference to streaming;
  Downloads screen with play all / shuffle / delete; "Download all" on album/playlist pages.
- **Local music**: scans chosen folders (tags + artwork via jaudiotagger), search, sort, play.
- **Player**: floating glass mini player and full-screen Now Playing (seek, volume, like,
  shuffle, repeat off/all/one, speed, sleep timer with fade-out, audio output picker, equalizer,
  queue). Seek/volume use the mobile `AppleSeekBar`. Autoplay radio continues with related tracks
  when the queue ends; the queue (paused) and volume are restored on launch.
- **Synced lyrics**: the mobile lyrics stack (Apple Music, BiniLyrics, LyricsPlus, SimpMusic,
  LRCLIB, KuGou, Unison, YouTube Music) with word-by-word highlighting, click-to-seek and per-song
  sync offset, shown beside the artwork in Now Playing.
- **Equalizer**: libVLC presets, preamp and 10 bands, persisted.
- **Stats**: play-event log (`stats.json`) with totals, top songs/artists, activity and
  listening-clock charts per range.
- **Settings**: playback, search, lyrics source order/toggles, audio, folders, desktop, data.
- **Desktop extras**: keyboard shortcuts (Space play/pause, Ctrl+←/→ prev/next, Shift+←/→ seek,
  Ctrl+↑/↓ volume, Ctrl+L like, Ctrl+S shuffle, Ctrl+R repeat, Esc back), global media keys
  (JNativeHook), system tray with optional minimize-to-tray.
- **Glassmorphic UI** ported from the mobile app's `Glass.kt` / `Palette.kt`: byte-identical dark
  "Stitch" palette and a `GlassPanel` with real backdrop blur via
  [Haze](https://github.com/chrisbanes/haze).

Not ported (Android-only or heavy): crossfade / DJ mix, animated motion artwork, Liquid Glass lens
refraction, Wear OS / widget / Quick Settings tile, in-app updater.

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

- JDK 17
- [VLC](https://www.videolan.org/vlc/) installed on the host machine (desktopApp uses libVLC
  through vlcj for audio playback — Media3 is Android-only, so there's no direct equivalent).

## Running

```powershell
.\gradlew.bat :desktopApp:run
```

## Packaging

```powershell
.\gradlew.bat :desktopApp:packageDistributionForCurrentOS
```

## Notes carried over from the mobile app

- Not distributed on app stores: same YouTube ToS / GPLv3 (NewPipeExtractor) considerations apply.
- Stream URLs expire (~6h) and are IP-bound — resolved per track at play time, never cached long.
- `YouTubeMusicSource` dispatches by id shape (`MPREb_…` album / `UC…` channel → InnerTube; URLs /
  bare names → NewPipe). Metadata falls back to NewPipe silently; stream resolution has no fallback.
