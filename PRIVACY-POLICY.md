# Privacy Policy for MusicSM Desktop

**Effective date: October 1, 2026**

MusicSM Desktop is published by Raj Pratap Singh ("we", "us", or "our"). This
policy explains how the application handles information when you install and
use it, including when obtained through the Microsoft Store.

For privacy questions or requests, contact **rs91963@gmail.com**.

## 1. Overview

MusicSM Desktop does not require an account. We do not operate a MusicSM account
service or a server that receives your library, search history, or listening
statistics. The application does not include advertising trackers or a
publisher-operated analytics or crash-reporting service. We do not sell your
personal information or use it for targeted advertising.

This does not mean that the application never sends information over the
internet. Online music search, streaming, still and animated artwork, lyrics, playlist imports,
and update checks require requests to third-party services, as described below.

## 2. Information stored on your device

The application stores information locally to provide its features:

| Information | Purpose |
|-------------|---------|
| Settings, including playback preferences, volume, equalizer settings, audio output selection, folder paths, and lyrics preferences | Remember your configuration |
| Liked songs, playlists, and recently played songs | Maintain your music library |
| Search queries you submit or act on, and recently opened search results | Provide search history and recent-search shortcuts |
| Played songs, play timestamps, and listening duration | Show listening statistics and personalize the home feed locally |
| Playback queue and related playback state | Restore your queue between sessions |
| Downloaded audio, download records, and file paths | Support offline playback and download management |
| Local music file paths, tags, duration, and extracted artwork | Index and play music stored on your device |

On Windows, the primary data folder is
`%USERPROFILE%\.musicsm-desktop`. Downloads are stored there by default, or in
the download folder you configure. Cached resources may also be kept locally by
the application's supporting libraries.

Local music scanning reads supported audio files in your configured folders,
including the Windows Music folder by default. The application does not upload
your local audio files to us. Online features used with a local song, such as
lyrics lookup, may send song metadata or identifiers to an external provider.

## 3. Information sent to third-party services

Depending on the features you use, the application contacts:

- **YouTube and YouTube Music:** for searches, home and related-music results,
  song, album, artist and playlist information, audio stream resolution,
  playback, and artwork. Requests may include search text, content identifiers,
  client information, language/region settings, and service-issued session
  identifiers.
- **Spotify and Apple Music public playlist services:** when you import a
  playlist link. The relevant service receives the playlist identifier or URL
  information needed to retrieve its public contents. Imported tracks may then
  be matched through YouTube music searches. The application does not ask for
  your Spotify, Apple Music, or Google account password.
- **Lyrics providers:** for lyrics lookup and synchronization. Providers may
  receive song titles, artist and album names, durations, and track or video
  identifiers. The current providers include BiniLyrics, LyricsPlus,
  SimpMusic, LRCLIB, KuGou, Unison, YouTube Music, and an Apple Music lyrics
  proxy at `lyrics-api.boidu.dev`. Some providers use multiple endpoints or
  proxy services. Provider availability and requests depend on your lyrics
  settings.
- **Artwork and media hosts:** to retrieve cover images and audio from the
  addresses returned by music services.
- **Animated artwork services:** when animated album art is enabled and Now
  Playing is open, the application looks up silent cover videos through Apple
  Music, TIDAL, and the Vivi community manifest at
  `vivimusicanvas.mkmdevilmi.workers.dev`, according to your selected source.
  Catalog searches may include song title, artist, album, and a region derived
  from your computer's locale. Matching videos and HLS playlists or segments
  are retrieved from the hosts specified by those services. Animated artwork
  is enabled by default and may use any internet connection, including a
  metered connection. You can disable it or select a specific source in
  Settings. The app does not upload your local audio for this lookup.
- **GitHub:** installed builds check for new releases at startup and
  periodically while running. Requests include the application's version in
  its User-Agent header. This is an update check, not an upload of your
  listening history.

These services necessarily receive your public IP address and may receive
standard request information, such as request time, request headers, and the
content being requested. They may infer an approximate location from your IP
address. They process information under their own privacy policies and may
retain request logs. We do not control their retention, security practices, or
processing locations. Their servers may be located outside your country.

Opening an external link, including the download website, launches your browser.
The destination website's privacy policy applies to that visit.

## 4. Playlist sharing and device access

Playlist sharing creates a self-contained `musicsm://` link or QR image on your
device, without uploading the playlist to a MusicSM sharing server. The shared
payload includes the playlist name and track identifiers, titles, artists, and
durations. It is encoded and compressed, **not encrypted**. Anyone who obtains
the link or QR image can read its contents. If you send it through another
application or service, that service's privacy practices also apply.

The application accesses configured music and download folders and files you
select, including QR images you choose to import. Copy and share actions can
write information to the system clipboard.

When global media keys are enabled, a system-wide keyboard hook receives key
events so the application can respond to play/pause, next, previous, and stop
keys. MusicSM Desktop does not record or transmit typed text or a keystroke
history. You can disable global media keys in Settings. The application does
not use your microphone or camera for these features.

## 5. Your choices, retention, and deletion

Local information remains on your device until you remove it or replace it.
You can clear search history and listening statistics using the application's
controls, remove liked songs or playlists, delete downloads, change indexed
music folders, configure or disable individual lyrics sources, and disable
animated artwork or choose its provider.

To remove the application's primary local data, fully exit MusicSM Desktop,
including its system tray process, and delete
`%USERPROFILE%\.musicsm-desktop`. This also deletes downloads kept in the
default download folder. Downloads in a custom folder and exported QR images
must be removed separately if you no longer want them. Do not delete your
original music folders unless you intend to remove those files.

Uninstalling the application may leave user-created files and the data folder
behind. Removing local data does not delete logs held by third-party services
or copies of links and images you have shared.

If you contact us for support, we receive the information you choose to include,
such as your email address and message. We use it to respond to your request and
retain it only as needed for support and any applicable legal obligations.
Please do not send passwords or unnecessary sensitive information. You can
contact us to request access to or deletion of support correspondence, subject
to applicable legal requirements. We cannot access local app data that you have
not provided to us or delete information held independently by other services.

## 6. Security

The application generally uses HTTPS for its configured online service
requests. Primary local settings and library records are stored as ordinary
files and are not encrypted by MusicSM Desktop. Their protection depends on
your operating system, user-account permissions, and device security.
No storage or transmission method can be guaranteed completely secure.

## 7. Children

MusicSM Desktop is not specifically directed at children. We do not knowingly
collect children's personal information through a publisher-operated account
or analytics service. If you believe a child has provided personal information
to us through support correspondence, contact us to request its deletion.
Third-party music services have their own age requirements and privacy policies.

## 8. Microsoft Store

Microsoft may process information about Store downloads, installations,
transactions, and diagnostics independently of MusicSM Desktop. Microsoft's
handling of that information is described in the
[Microsoft Privacy Statement](https://privacy.microsoft.com/privacystatement).
This policy describes MusicSM Desktop, not Microsoft's services.

## 9. Changes and contact

We may update this policy when the application's features or data practices
change. The effective date at the top identifies the current version.

**Publisher:** Raj Pratap Singh  
**Privacy contact:** rs91963@gmail.com
