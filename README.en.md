# Hazix

[![CI](https://github.com/chinahhy/Hazix/actions/workflows/ci.yml/badge.svg)](https://github.com/chinahhy/Hazix/actions/workflows/ci.yml)
[![Latest release](https://img.shields.io/github/v/release/chinahhy/Hazix)](https://github.com/chinahhy/Hazix/releases/latest)
[![License](https://img.shields.io/badge/license-proprietary-lightgrey)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%20TV%20%7C%20Android-3ddc84)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)

[English](README.en.md) · [简体中文](README.md)

**Hazix** is a native media client for **Android TV / Google TV** and **Android
phones**. The UI, catalogue browsing, detail pages, episode pickers and the
player are all Kotlin + Jetpack Compose with AndroidX Media3; there is no WebView
and the provider's website is never loaded.

> **Not an official app of hdao.tv.** Hazix does not host, transcode, upload, or
> redistribute media. It reads the public interface of a third-party catalogue and
> plays back the addresses that interface returns. Content availability, legality,
> and regional restrictions are the responsibility of the content provider. Use it
> only within the scope you are authorised to.

## Features

**Living room (Android TV / Google TV)**

- Cinematic home screen: the newest three films and three series rotate as hero
  posters, and focus drives the backdrop, detail panel, and a muted auto-preview.
- Seven catalogue entries in the top bar — Movies, Series, Variety, Documentary,
  Anime, Short Drama — plus search and a manual **Check for updates** entry.
- Poster grids with seamless paging that keeps the scroll position and cursor
  where you left them when you come back from a detail page.
- Continue watching, de-duplicated by program ID and normalised title.
- Explicit D-pad focus navigation with visible focus decoration.
- Native HLS playback through ExoPlayer, plus GitHub-release based in-app updates.

**Phone**

- Touch home screen with a hero carousel that advances every six seconds and
  responds to horizontal swipes.
- Search, catalogue browsing, detail pages, episode selection, and player.
- Series entry covering five sub-categories (series, variety, documentary, anime,
  short drama).

**Shared**

- Single data layer for both apps (`tv/hdao/app/data`), so API handling, playback
  URL resolution, and watch progress are implemented once.
- Watch progress per episode, stored locally; "Continue watching" keeps the most
  recent episode per program.
- Encrypted DNS (DNS-over-HTTPS) with a plain-DNS fallback for networks that block
  it.
- Same launcher identity and dark/gold visual language on both form factors.

## Project layout

| Path | Purpose |
| --- | --- |
| `nativeapp/` | Android TV / Google TV app (`tv.hdao.app`) — the main module |
| `mobileapp/` | Android phone app (`tv.hdao.mobile`), reuses the TV data layer |
| `web/` | Browser preview used to iterate on layout and interaction before packaging |
| `dist/` | Locally archived, versioned APKs and their SHA-256 ledger |
| `.github/workflows/` | `ci.yml` (checks + APK artifacts) and `release-apks.yml` (tagged releases) |

## Requirements

- JDK 17
- Android SDK Platform 35 with build-tools 35.0.0
- Node.js 22 or newer — only for the browser preview

## Building

**In the cloud (recommended, and how releases are produced)**

- Push to `main` → `ci.yml` compiles, lints and unit-tests both apps, then builds
  signed release APKs for **both** TV and phone and uploads them as the
  `Hazix-APKs-<sha>` workflow artifact. Nothing has to be installed locally.
- Push a `vX.Y.Z` tag (or run *Release APKs* manually) → `release-apks.yml` builds
  both APKs, verifies their signer, writes `SHA256SUMS.txt`, and publishes them to
  a GitHub Release.

**Locally**

```bash
./gradlew :nativeapp:lintRelease :nativeapp:assembleRelease \
          :mobileapp:lintRelease :mobileapp:assembleRelease
```

Outputs:

```text
nativeapp/build/outputs/apk/release/nativeapp-release.apk
mobileapp/build/outputs/apk/release/mobileapp-release.apk
```

Both modules take their version from the same Gradle properties, so a release
always ships a matching pair:

```bash
./gradlew :nativeapp:assembleRelease :mobileapp:assembleRelease \
  -PVERSION_NAME=3.4.0 -PVERSION_CODE=3004000
```

Release builds are signed with the key in `HAZIX_RELEASE_KEYSTORE` (defaults to
`~/.android/debug.keystore`). This is a convenience key for personal sideloading:
it is the key that existing installations were signed with, so it must stay
stable or upgrades will be rejected. Use a private release keystore before any
real distribution.

## Releases and versioning

`v3.4.0` → `versionCode = 3 * 1000000 + 4 * 1000 + 0 = 3004000`. The TV and phone
APKs share the version number and the signing key, so both can be installed over
an older build. See [CHANGELOG.md](CHANGELOG.md) for the release history.

## Install

Allow installation from unknown sources on the device, then install the APK from
the latest [release](https://github.com/chinahhy/Hazix/releases/latest), or:

```bash
adb install -r Hazix-TV-v3.4.0.apk
adb install -r Hazix-Mobile-v3.4.0.apk
```

## In-app updates (TV)

The TV app reads the latest published GitHub Release on launch. When a newer
version exists it can download the package with the remote and verify, in order,
the download origin, the APK size, `SHA256SUMS.txt`, the package ID, the version
code, and the signing certificate before handing it to the Android system
installer. A manual **Check for updates** entry in the top bar reports "already up
to date" or a network failure instead of staying silent.

Because this is a sideloaded app, Android never allows a silent upgrade: the first
time, allow Hazix to install unknown apps, and confirm each upgrade in the system
installer.

## Remote control

| Key | Browsing | Player |
| --- | --- | --- |
| D-pad | Move focus / scroll | Left/right seek ±10 s, up/down switch episode |
| OK / Enter | Open the focused item | Play/pause and show the control overlay |
| Back | Previous screen | Save progress and leave the player |

## Browser preview

```bash
web/start-preview.command                     # double-click on macOS
cd web && npm start                           # or run it directly
```

Then open <http://127.0.0.1:4173>. The preview serves only `127.0.0.1`, reads the
same catalogue endpoints as the apps, and is used to confirm layout and behaviour
before any packaging. Safari uses native HLS; Chromium browsers fall back to a
project-local HLS.js copy with no external CDN. A browser preview does not prove
Android playback or remote-control compatibility.

## Tests

```bash
./gradlew :nativeapp:testDebugUnitTest :mobileapp:testDebugUnitTest
./gradlew :nativeapp:lintDebug :mobileapp:lintDebug
cd web && pnpm test
```

## Privacy

Hazix has no accounts, no advertising, and no analytics SDK. Watch progress and
settings stay in app-private storage on the device. See [PRIVACY.md](PRIVACY.md)
for the full list of stored data and outbound requests.

## License

Proprietary — all rights reserved. See [LICENSE](LICENSE). Releases published
before 2026-09-19 were distributed under the MIT License; rights already granted
for those versions are unaffected.

## Acknowledgements

The TV interaction and page structure follow Google's
[Android TV Immersive List](https://developer.android.com/design/ui/tv/guides/components/immersive-list)
guidance and the ideas in
[JetStreamCompose](https://github.com/android/tv-samples/tree/main/JetStreamCompose).
Player capabilities come from AndroidX Media3. The engineering trade-offs of a
full-featured TV app were informed by [FongMi/TV](https://github.com/FongMi/TV);
no GPL source from that project was copied, and no crawler, live-TV, or script
engine was introduced here.
