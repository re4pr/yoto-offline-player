# Yoto Offline Player (Android Tablet)

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

A kid-safe, touch-first Android tablet client (`com.homelab.yototablet`) for listening to playlists from a self-hosted LAN Yoto Hub API, with automatic network refresh and full local offline caching so every static playlist works reliably away from home.

---

## Features

- **Listening-Only Kid UI**: Clean playlist grid and audio player with zero ads, browser links, or playlist editing/deletion controls.
- **Full Offline Downloads**: Automatically mirrors catalog metadata, cover artwork, and static audio tracks (`.ogg`, `.m4a`, `.mp3`, etc.) into app-private storage (`files/yoto_offline/`). Live radio streams (`type == "stream"`) remain marked `LIVE` so they never fill storage.
- **Automatic Sync & Refresh**: Checks the configured LAN Hub API on app launch/resume, when Wi-Fi reconnects, and periodically via `WorkManager`, plus a manual **Refresh** button.
- **Background Playback**: Built on Media3 `MediaSessionService` + `ExoPlayer` with audio focus handling, headphone-unplug pause, local wake lock, and lock-screen/notification controls.
- **Magical Worlds Themes**: Includes 5 animated particle themes (*Princess Castle*, *Mermaid Lagoon*, *Unicorn Dreamland*, *Frozen Ice Palace*, *Fairy Garden*) with daily rotation and a **World** switcher.
- **Parent Settings**: Long-press the header title for `1.5s` to inspect hub connectivity, offline storage usage, change the Hub API URL, or force a full sync.

---

## Expected LAN Hub API Endpoints

The app expects a local HTTP API exposing:

- `GET /api/yoto/cards` (`?refresh=true` optional) — Returns `{ ready, needs_auth, cached, message, count, cards: [...] }` where each card includes `card_id`, `title`, `author`, `category`, `cover_url`, `track_count`, `duration_seconds`, `source`, `updated_at`.
- `GET /api/yoto/cards/{card_id}` (`?refresh=true` optional) — Returns `{ ready, playable, card_id, title, author, cover_url, message, count, tracks: [...] }` where each track includes `id`, `index`, `title`, `chapter_title`, `duration_seconds`, `format`, `type`, `url`.
- Static cover and audio URLs resolved relative to the configured Hub API URL (or absolute `https://` URLs for live radio streams).

---

## Configuration & Security

- **Zero Embedded Credentials**: No Yoto OAuth tokens, API keys, or passwords are stored in the app; authentication stays strictly on your LAN Hub server.
- **LAN-Scoped Cleartext**: Generated `network_security_config.xml` blocks cleartext HTTP globally and permits cleartext only for your configured LAN host and `127.0.0.1`.

1. Copy `local.properties.example` to `local.properties` (gitignored):
   ```bash
   cp local.properties.example local.properties
   ```
2. Set `sdk.dir`, `yoto.hubUrl`, and optionally `yoto.appName` in `local.properties`:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   yoto.hubUrl=http://192.168.1.100:9595
   yoto.appName=Yoto Offline Player
   ```

---

## Build & Test

```bash
./gradlew --no-daemon testDebugUnitTest assembleDebug
```

Install the generated APK (`app/build/outputs/apk/debug/app-debug.apk`) via `adb install -r`.

---

## License

This project is open-source software released under the [MIT License](LICENSE). Anyone is free to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the software.
