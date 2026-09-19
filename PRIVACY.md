# Privacy Policy

_Last updated: 2026-09-19_

Hazix is a third-party client for a public media catalogue. It has no user
accounts, no advertising, and no analytics. This document describes exactly what
the app stores and what it sends over the network.

## What the app stores on your device

| Data | Where it lives | Why |
| --- | --- | --- |
| Watch progress (title, episode, position, timestamp) | App-private `SharedPreferences` (`watch_progress`) | So "Continue watching" can resume where you stopped |
| Update download cache | App-private cache directory | Holds the downloaded APK until the system installer runs |
| App settings | App-private storage | Theme and playback preferences |

All of it stays on the device. Nothing is uploaded to the developer, and no
server operated by the developer exists. Uninstalling the app removes all of it.

## What the app sends over the network

| Destination | Purpose | What it can see |
| --- | --- | --- |
| `hdao.tv` (`https://hdao.tv/api`) | Catalogue, search, detail and playback addresses | Your IP address and the request itself, as with any website you visit |
| `stream.hdao.tv` | Playback stream proxy | Your IP address and the media request |
| `image.tmdb.org` | Poster and backdrop images | Your IP address and the image request |
| `dns.alidns.com`, `cloudflare-dns.com` (DNS-over-HTTPS) | Encrypted name resolution | The domain names being resolved |
| `api.github.com` | Checking for a newer release | Your IP address and a standard HTTP user agent |

The app makes no other outbound requests. It does not include any analytics,
crash-reporting, advertising, or social SDK, and it does not read contacts,
location, camera, microphone, or files outside its own private storage.

## Permissions

- `INTERNET` — required to reach the catalogue and to play media.
- `REQUEST_INSTALL_PACKAGES` — used only when you accept an in-app update; the
  Android system installer always asks for your confirmation.

## Children

The app is not directed at children and collects no personal information from
anyone.

## Media and copyright

The app does not host, upload, transcode, or redistribute media. It reads the
public interface of a third-party catalogue and plays back the addresses that
interface returns. Content availability, legality, and regional restrictions are
the responsibility of the content provider.

## Changes

Any change to this policy will be committed to this repository, and the date at
the top of this file will be updated.

## Contact

Open an issue at <https://github.com/chinahhy/Hazix/issues>.

---

## 中文摘要

Hazix 无账号、无广告、无统计 SDK。观看记录与设置只保存在本机应用私有存储中，
不向任何开发者服务器上传数据；卸载应用即清除全部本地数据。应用只访问
`hdao.tv`（片库、搜索、详情、播放地址）、`stream.hdao.tv`（播放代理）、
`image.tmdb.org`（海报图片）、加密 DNS 服务，以及 `api.github.com`（检查新版本）。
应用不采集联系人、位置、相机、麦克风或应用私有存储之外的文件。应用不托管、
不转存任何影视内容，只读取第三方站点公开接口返回的播放地址。
