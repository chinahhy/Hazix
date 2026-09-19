# Hazix

[![CI](https://github.com/chinahhy/Hazix/actions/workflows/ci.yml/badge.svg)](https://github.com/chinahhy/Hazix/actions/workflows/ci.yml)
[![最新版本](https://img.shields.io/github/v/release/chinahhy/Hazix)](https://github.com/chinahhy/Hazix/releases/latest)
[![许可](https://img.shields.io/badge/license-proprietary-lightgrey)](LICENSE)

[English](README.en.md) · 简体中文

**Hazix** 是面向 **Android TV / Google TV** 与 **Android 手机** 的原生影视客户端。
界面、片库浏览、详情、选集与播放器全部为 Kotlin + Jetpack Compose + AndroidX Media3
原生实现，不使用 WebView，也不加载内容提供方的网页。

> **非 hdao.tv 官方应用。** Hazix 不托管、不转码、不上传、不再分发任何影视内容。
> 它只读取第三方站点公开接口返回的元数据与播放地址，内容可用性、版权与地区限制由
> 内容提供方负责。请仅在获得授权的范围内使用。

## 功能

**电视端（Android TV / Google TV）**

- 沉浸式首页：最新 3 部电影与 3 部剧集作为海报轮播，焦点联动背景、详情面板与静音自动预览；
- 顶部七项分类导航（电影、剧集、综艺、纪录片、动漫、短剧）加搜索，以及独立的「检查更新」入口；
- 海报网格无缝加载；从详情页返回时保留原滚动位置，并把光标放回刚才打开的那张卡片；
- 「最近观看」按节目 ID 与标准化片名双重去重；
- 遥控器 D-pad 显式焦点导航与高亮；
- ExoPlayer 原生 HLS 播放，以及基于 GitHub Release 的应用内更新。

**手机端**

- 触控首页，Hero 每 6 秒自动轮播并支持左右手势切换；
- 搜索、分类、详情、选集与播放器；
- 「剧集」内含剧集、综艺、纪录片、动漫、短剧五个子分类。

**共用**

- TV 与手机共用同一套数据层（`tv/hdao/app/data`），接口处理、播放地址解析与观看记录只实现一次；
- 本地保存每集观看进度，「最近观看」保留每个节目最近观看的集数；
- 优先使用加密 DNS（DNS-over-HTTPS），被网络阻断时回退到系统 DNS；
- 两端使用同一套暗色／金色视觉与品牌图标。

## 项目结构

| 路径 | 说明 |
| --- | --- |
| `nativeapp/` | Android TV / Google TV 应用（`tv.hdao.app`），主模块 |
| `mobileapp/` | Android 手机应用（`tv.hdao.mobile`），复用 TV 数据层 |
| `web/` | 浏览器预览，用于打包前确认布局与交互 |
| `dist/` | 本地按版本归档的 APK 与 SHA-256 清单 |
| `.github/workflows/` | `ci.yml`（检查 + APK 产物）与 `release-apks.yml`（标签发布） |

## 环境要求

- JDK 17
- Android SDK Platform 35 与 build-tools 35.0.0
- Node.js 22 或更高（仅浏览器预览需要）

## 构建

**云端构建（推荐，正式版本即由此产出）**

- push 到 `main` → `ci.yml` 编译、lint、跑单测，并**同时**构建 TV 与手机两个已签名
  release APK，作为 `Hazix-APKs-<sha>` 工作流产物上传。本机无需安装任何东西。
- push `vX.Y.Z` 标签（或在 Actions 页面手动运行 *Release APKs*）→ `release-apks.yml`
  构建两个 APK、校验签名、生成 `SHA256SUMS.txt`，并发布到 GitHub Release。

**本地构建**

```bash
./gradlew :nativeapp:lintRelease :nativeapp:assembleRelease \
          :mobileapp:lintRelease :mobileapp:assembleRelease
```

产物：

```text
nativeapp/build/outputs/apk/release/nativeapp-release.apk
mobileapp/build/outputs/apk/release/mobileapp-release.apk
```

两个模块读取同一组 Gradle 属性，因此每次发布都是版本号一致的一对：

```bash
./gradlew :nativeapp:assembleRelease :mobileapp:assembleRelease \
  -PVERSION_NAME=3.4.0 -PVERSION_CODE=3004000
```

release 构建使用 `HAZIX_RELEASE_KEYSTORE` 指定的签名（默认 `~/.android/debug.keystore`）。
这只是个人侧载用的便利密钥：现有安装就是用这把钥匙签的，因此它必须保持不变，否则升级会被系统拒绝。
正式对外分发前应改用私有 release keystore。

## 版本与发布

`v3.4.0` → `versionCode = 3 * 1000000 + 4 * 1000 + 0 = 3004000`。TV 与手机 APK 共用版本号与
签名密钥，因此两者都能覆盖安装旧版本。历史版本见 [CHANGELOG.md](CHANGELOG.md)。

## 安装

在设备上允许安装未知来源应用后，安装最新 [Release](https://github.com/chinahhy/Hazix/releases/latest)
中的 APK，或执行：

```bash
adb install -r Hazix-TV-v3.4.0.apk
adb install -r Hazix-Mobile-v3.4.0.apk
```

## 应用内更新（电视端）

电视端启动时读取本仓库最新的正式 GitHub Release。发现更高版本后可用遥控器直接下载，
下载完成后会依次核对下载来源、APK 大小、`SHA256SUMS.txt`、应用 ID、版本号与签名证书，
全部通过才交给 Android 系统安装器。顶部导航新增的「检查更新」入口会明确反馈
「已是最新版本」或网络失败，而不是毫无提示。

由于这是侧载应用，Android 不允许静默升级：第一次需要在系统设置中允许 Hazix
「安装未知来源应用」，之后每次升级仍需在系统安装器中确认一次。

## 遥控器

| 按键 | 浏览界面 | 播放器 |
| --- | --- | --- |
| 方向键 | 移动焦点／滚动列表 | 左右 ±10 秒；上下切换集数 |
| OK / Enter | 打开聚焦项 | 播放／暂停并显示控制层 |
| 返回 | 返回上一页 | 保存进度并退出播放 |

## 浏览器预览

```bash
web/start-preview.command                     # macOS 下双击运行
cd web && npm start                           # 或直接执行
```

打开 <http://127.0.0.1:4173>。预览服务只监听 `127.0.0.1`，读取与应用相同的片库接口，
用于在打包前确认布局与行为。Safari 使用原生 HLS，Chromium 系浏览器回退到项目内置的
HLS.js，不依赖外部 CDN。网页预览不代表 Android 播放与遥控器兼容性已通过。

## 测试

```bash
./gradlew :nativeapp:testDebugUnitTest :mobileapp:testDebugUnitTest
./gradlew :nativeapp:lintDebug :mobileapp:lintDebug
cd web && pnpm test
```

## 隐私

Hazix 无账号、无广告、无统计 SDK。观看记录与设置只保存在本机应用私有存储中。
完整的数据存储与外发请求清单见 [PRIVACY.md](PRIVACY.md)。

## 许可

专有许可，保留所有权利，详见 [LICENSE](LICENSE)。2026-09-19 之前发布的版本以 MIT
许可分发，已授予那些版本的权利不受本次变更影响。

## 致谢

电视端交互与页面组织参考 Google
[Android TV Immersive List](https://developer.android.com/design/ui/tv/guides/components/immersive-list)
与 [JetStreamCompose](https://github.com/android/tv-samples/tree/main/JetStreamCompose)
的思路；播放能力来自 AndroidX Media3。完整型电视应用的工程取舍参考了
[FongMi/TV](https://github.com/FongMi/TV)，但本项目没有复制其 GPL 源码，
也没有引入爬虫、直播或脚本引擎。
