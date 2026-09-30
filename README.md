# MusicSM Desktop

Desktop port of [MusicSM](../MusicSM) — a dark, Spotify-like music streaming app with metadata
from YouTube Music (InnerTube) and audio via NewPipeExtractor. Built with **Kotlin + Compose
Multiplatform for Desktop** so the domain and data layers are shared verbatim with the mobile
app; only the UI shell and playback engine are desktop-specific.

## Status: MVP+ (expanded)

Covers the agreed MVP scope plus a fuller UI/feature pass:

- Sidebar navigation (Home / Search / Library) with back-stack.
- Home feed with album/artist card shelves and song rows.
- Your Library: liked songs, local playlists (create + add tracks), recent plays — persisted to
  `~/.musicsm-desktop/library.json` (JSON-file store, since Room isn't practical on plain JVM).
- Album / Artist / Playlist detail screens with track lists and Play-all.
- Mini player bar (artwork, like, expand, queue toggle) plus a full-screen Now Playing view
  (seek, prev/play-pause/next, like, volume, queue toggle) and a slide-in queue panel.
- Cover art via Coil3 (OkHttp network fetcher).
- **Glassmorphic UI**, ported from the mobile app's `ui/components/Glass.kt` / `ui/theme/Palette.kt`:
  a byte-identical dark "Stitch" colour palette, a `GlassPanel` frosted-glass component (real
  backdrop blur via [Haze](https://github.com/chrisbanes/haze), translucent tint, top-left gloss
  sheen, additive rim) applied to the nav rail, mini player, queue panel and Now Playing controls,
  plus a blurred/darkened artwork backdrop on the full-screen player. Desktop drops the mobile
  app's Android-only Liquid Glass lens refraction (RuntimeShader) and low-RAM device gating.

Downloads, lyrics, import/export, settings, EQ and mix mode are still not ported — out of scope
for now; see the mobile app's `.codemap.md` for the full feature set if parity is needed later.

## Modules

| Module | Role |
|--------|------|
| `:innertube` | Copied verbatim from the mobile app — YouTube Music InnerTube client. Plain JVM already. |
| `:motionart` | Copied verbatim — animated cover-art lookup. Plain JVM already. |
| `:domain` | Copied verbatim from the mobile app's `domain/` package — models, repository interfaces, recommend/match/share logic. Pure Kotlin, zero UI/platform deps. |
| `:data` | The mobile app's `data/source/youtube` package (YouTubeMusicSource, NewPipeMusicSource, stream selection). Only change from the original: Android's `Log` swapped for `println`. |
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
