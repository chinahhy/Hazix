# Changelog

Versioned APKs are never overwritten or deleted. Cloud-built packages are
attached to [GitHub Releases](https://github.com/chinahhy/Hazix/releases); local
copies are archived in `dist/` with their SHA-256 values in
`dist/SHA256SUMS.txt`.

## Unreleased

- TV: fixed the progress bar and control overlay never hiding on sources that
  need longer to start playing, such as anime episodes.
- TV: returning from a detail page now restores the category grid, its scroll
  position, and the cursor on the poster that was opened, instead of reloading
  page one and dropping the cursor on the top navigation bar.
- TV: added a manual "Check for updates" entry to the top navigation. The
  automatic check on launch only reacts when a newer release exists, so the
  feature previously had no visible entry point.
- Both apps now share one version number and one signing key, and the release
  workflow builds the TV and phone APKs together.

## 3.3.9

`dist/Hazix-TV-v3.3.9.apk` — in-app updates from GitHub Releases, verifying the
download origin, APK size, SHA-256, package ID, version code, and signing
certificate before handing the package to the system installer.
Installing this version over 3.3.8 still required a U-disk or ADB install.

## 3.3.8

`dist/Hazix-TV-v3.3.8.apk` — fixed paging state not being rebuilt after switching
categories, which limited series, variety, documentary, anime, and short drama to
their first page.

## 3.3.7

`dist/Hazix-TV-v3.3.7.apk` — home screen reduced to the featured carousel and
continue watching; category pages load seamlessly.

## 3.3.6

`dist/Hazix-TV-v3.3.6.apk` — project renamed to Hazix; content screens show the
brand icon only, without the product name.

## 3.3.5

`dist/HDAO-TV-Netflix-v3.3.5.apk` — unified the seven top-bar text entries at a
larger size, keeping search as a separate icon entry.

## 3.3.4

`dist/HDAO-TV-Netflix-v3.3.4.apk` — larger TV top-menu text; removed the
duplicate navigation on category pages.

## 3.3.3

`dist/HDAO-TV-Netflix-v3.3.3.apk` — immersive poster home screen; focus updates
the detail panel immediately, and resting on a poster starts a muted preview.

## 3.3.2

`dist/HDAO-TV-Netflix-v3.3.2.apk` — poster-based home screen, continue-watching
focus handling, short-drama category, removed brand watermark, higher-resolution
detail artwork.

## 3.3.1

`dist/HDAO-TV-Netflix-v3.3.1.apk` — home screen keeps only featured
recommendations and continue watching; fixed carousel misalignment, ignored input,
and HLS playback errors.

## 3.3.0

`dist/HDAO-TV-Netflix-v3.3.0.apk` — dedicated category rail, 3 + 3 featured
carousel, de-duplicated continue watching, higher-resolution artwork.

## 3.2.0

`dist/HDAO-TV-Netflix-v3.2.0.apk` — new brand icon and TV launch banner.

## 3.1.0

`dist/HDAO-TV-Netflix-v3.1.0.apk` — home screen rework, focused hero, rating
badges, de-duplicated continue watching.

## 3.0.1

`dist/HDAO-TV-Netflix-v3.0.1.apk` — encrypted DNS fallback; fixed connection
timeouts when the TV resolved the site to a fake IP.

## 3.0.0

`dist/HDAO-TV-Netflix-v3.0.0.apk` — first Netflix-style TV UI: side rail,
horizontal cards, continue watching.

## 2.0.0

`dist/HDAO-TV-Native-v2.0.0.apk` — first native TV interface.

## Mobile 1.1.0

`dist/HDAO-Mobile-v1.1.0.apk` — new icon, automatic and manual hero carousel,
series sub-categories.

## Mobile 1.0.0

`dist/HDAO-Mobile-v1.0.0.apk` — first native Android phone release.

## 1.0.0

`dist/HDAO-TV-v1.0.0.apk` — WebView version, kept only as a compatibility
fallback.
