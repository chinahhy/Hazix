# 隐私政策

_最后更新：2026-09-19_

Hazix 是第三方影视客户端。应用没有用户账号、没有广告、没有统计 SDK。
本文逐项说明应用在本机保存了什么、以及会向哪些域名发起网络请求。

[English version below](#english)

## 本机保存的数据

| 数据 | 位置 | 用途 |
| --- | --- | --- |
| 观看记录（片名、集数、进度、时间戳） | 应用私有 `SharedPreferences`（`watch_progress`） | 让「最近观看」能接着上次的位置继续播放 |
| 更新包缓存 | 应用私有缓存目录 | 暂存已下载的 APK，直到系统安装器接管 |
| 应用设置 | 应用私有存储 | 主题与播放偏好 |

以上数据全部留在设备本机，**不会上传到开发者，也不存在开发者运营的服务器**。
卸载应用即清除全部本地数据。

## 应用发起的网络请求

| 目标域名 | 用途 | 对方能看到什么 |
| --- | --- | --- |
| `hdao.tv`（`https://hdao.tv/api`） | 片库、搜索、详情与播放地址 | 你的 IP 地址与请求内容，与你访问任何网站相同 |
| `stream.hdao.tv` | 播放流代理 | 你的 IP 地址与媒体请求 |
| `image.tmdb.org` | 海报与背景图 | 你的 IP 地址与图片请求 |
| `dns.alidns.com`、`cloudflare-dns.com`（DNS-over-HTTPS） | 加密域名解析 | 正在解析的域名 |
| `api.github.com` | 检查是否有新版本 | 你的 IP 地址与标准 HTTP User-Agent |

除上述之外，应用不会发起其他对外请求。应用不包含任何统计、崩溃上报、广告或社交 SDK，
也不会读取联系人、位置、相机、麦克风，或应用私有存储之外的文件。

## 权限

- `INTERNET` —— 访问片库与播放媒体所必需。
- `REQUEST_INSTALL_PACKAGES` —— 仅在你接受应用内更新时使用；Android 系统安装器始终会再向你确认一次。

## 儿童

本应用不面向儿童，也不向任何人收集个人信息。

## 媒体与版权

应用不托管、不上传、不转码、不再分发任何影视内容。它只读取第三方站点的公开接口，
并播放该接口返回的地址。内容可用性、合法性及地区限制由内容提供方负责。

## 变更

本政策的任何变更都会提交到本仓库，并更新文件顶部的日期。

## 联系方式

请在 <https://github.com/chinahhy/Hazix/issues> 提交 issue。

---

## English

_Last updated: 2026-09-19_

Hazix is a third-party client for a public media catalogue. It has no user
accounts, no advertising, and no analytics. This document describes exactly what
the app stores and what it sends over the network.

### What the app stores on your device

| Data | Where it lives | Why |
| --- | --- | --- |
| Watch progress (title, episode, position, timestamp) | App-private `SharedPreferences` (`watch_progress`) | So "Continue watching" can resume where you stopped |
| Update download cache | App-private cache directory | Holds the downloaded APK until the system installer runs |
| App settings | App-private storage | Theme and playback preferences |

All of it stays on the device. Nothing is uploaded to the developer, and no
server operated by the developer exists. Uninstalling the app removes all of it.

### What the app sends over the network

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

### Permissions

- `INTERNET` — required to reach the catalogue and to play media.
- `REQUEST_INSTALL_PACKAGES` — used only when you accept an in-app update; the
  Android system installer always asks for your confirmation.

### Children

The app is not directed at children and collects no personal information from
anyone.

### Media and copyright

The app does not host, upload, transcode, or redistribute media. It reads the
public interface of a third-party catalogue and plays back the addresses that
interface returns. Content availability, legality, and regional restrictions are
the responsibility of the content provider.

### Changes

Any change to this policy will be committed to this repository, and the date at
the top of this file will be updated.

### Contact

Open an issue at <https://github.com/chinahhy/Hazix/issues>.
