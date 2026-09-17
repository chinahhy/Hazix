# Hazix · Web Preview / Android TV / Mobile

## 当前开发方式：先在网页确认效果

先迭代 `web/` 中的浏览器预览；用户确认满意并明确要求打包后，再生成电视及手机 APK。

双击 `web/start-preview.command` 启动本机服务（优先使用系统 Node，自动兼容本机 Codex 内置运行时），或
在 `web/` 目录执行 `npm start`（Node.js 22+，无需安装依赖），打开
[本机网页预览](http://127.0.0.1:4173)。顶部可切换电视和手机视图；直接访问
[完整页面](http://127.0.0.1:4173/app.html) 可按实际浏览器宽度适配。

网页沿用现有深色／金色视觉和品牌图标，读取同一片库的首页、分类、搜索及详情接口；
支持推荐轮播、选集、方向键焦点和本地观看记录。Safari 优先使用原生 HLS，
Chrome、Edge 等浏览器通过项目本地 HLS.js 回退播放；不依赖外部 CDN。
视频源仍可能返回播放错误；网页视觉确认不代表 Android 播放兼容性已通过。
本轮没有引入额外播放器库。

服务默认仅监听 `127.0.0.1`，不会公开发布；网页迭代不运行 Gradle、不生成 APK。
最终 Android 遥控器、解码及设备兼容性留到确认打包后的真机验证阶段。

## Android 客户端

面向 Android TV / Google TV 与 Android 手机的原生影视客户端。界面、详情、选集与播放器均为
Kotlin + Jetpack Compose 原生实现，不加载 `hdao.tv` 网页。

> 非 hdao.tv 官方应用。应用只按需读取站点公开接口返回的元数据和播放地址，不抓取、托管、
> 转发或破解媒体；内容版权、可用性及地区限制由内容提供方负责。请仅在获得授权的范围内使用。

## 功能

- Netflix 式沉浸首页：以最新 3 部电影和 3 部剧集的海报为单位横向切换，焦点联动背景、详情与静音自动预览；
- TV 左侧独立提供电影、剧集、综艺、纪录片、动漫和短剧入口；
- “继续观看”固定在首页第二排，并按节目 ID 与标准化片名双重去重；
- 海报与背景优先使用 TMDB `original`，详情页不再把低清竖版封面拉伸成全屏背景；
- 所有卡片优先展示站点返回的豆瓣/TMDB 评分；已有精确 TMDB ID 但上游暂缺分数时，网页预览会从 TMDB 公开页面补充并缓存；
- 原生分类网格、电视键盘搜索、详情和选集；
- 遥控器 D-pad 显式焦点导航与高亮；
- Media3/ExoPlayer 原生 HLS 播放；
- OK 播放/暂停，左右快退/快进 10 秒，上下切换集数，返回退出播放器；
- 本地保存每集观看进度；“继续观看”按节目去重并保留最近观看集数；
- 独立手机版支持触控首页、搜索、分类、详情、选集和播放器；
- 手机版 Hero 每 6 秒自动轮播，并支持手势左右切换；
- 手机版“剧集”内含剧集、综艺、纪录片、动漫和短剧五个独立分类；
- 手机与 TV 使用统一的海浪、岛屿与电影光圈品牌图标；
- 家庭客户端不展示网页中指向外部成人站点的“午夜场”入口；
- Android 7.0（API 24）及以上，目标 API 35；
- 应用 ID 保持为 `tv.hdao.app`，可覆盖安装旧 WebView 版。

## 项目结构

```text
nativeapp/   原生 Android TV v3（当前构建模块）
mobileapp/   原生 Android 手机版
app/         旧 WebView v1 源码，仅作回退参考，不参与默认构建
dist/        按版本永久保留的可侧载 APK 与 SHA-256 清单
preview/     Google TV 模拟器真实运行截图
```

## 已保留版本

| 版本 | 安装包 | 说明 |
| --- | --- | --- |
| v1.0.0 | `dist/HDAO-TV-v1.0.0.apk` | WebView 版，仅作兼容回退 |
| v2.0.0 | `dist/HDAO-TV-Native-v2.0.0.apk` | 第一版原生 TV 界面 |
| v3.0.0 | `dist/HDAO-TV-Netflix-v3.0.0.apk` | Netflix 风侧栏、横向卡片与继续观看初版 |
| v3.0.1 | `dist/HDAO-TV-Netflix-v3.0.1.apk` | 加密 DNS 容灾，修复电视解析到 Fake-IP 后连接超时 |
| TV v3.1.0 | `dist/HDAO-TV-Netflix-v3.1.0.apk` | 首页重排、聚焦 Hero、评分标签、继续观看去重 |
| Mobile v1.0.0 | `dist/HDAO-Mobile-v1.0.0.apk` | 原生 Android 手机首版 |
| TV v3.2.0 | `dist/HDAO-TV-Netflix-v3.2.0.apk` | 新品牌图标与 TV 启动横幅 |
| Mobile v1.1.0 | `dist/HDAO-Mobile-v1.1.0.apk` | 新图标、自动/手动 Hero 轮播、剧集子分类 |
| TV v3.3.0 | `dist/HDAO-TV-Netflix-v3.3.0.apk` | 左侧独立分类、最近热映 3+3 轮播、继续观看去重与高清图源 |
| TV v3.3.1 | `dist/HDAO-TV-Netflix-v3.3.1.apk` | 首页仅保留焦点推荐和继续观看，修复轮播错位、操作失效与 HLS 播放错误 |
| TV v3.3.2 | `dist/HDAO-TV-Netflix-v3.3.2.apk` | 海报式首页、继续观看焦点、短剧分类、去品牌水印与高清详情图 |
| TV v3.3.3 | `dist/HDAO-TV-Netflix-v3.3.3.apk` | 沉浸式海报首页，焦点即时联动详情，停留后静音自动预览 |
| TV v3.3.4 | `dist/HDAO-TV-Netflix-v3.3.4.apk` | 增大电视端顶部菜单字号，移除分类页重复导航 |
| TV v3.3.5 | `dist/HDAO-TV-Netflix-v3.3.5.apk` | 顶部七项文字导航统一为大字号，搜索保留为独立图标入口 |
| TV v3.3.6 | `dist/Hazix-TV-v3.3.6.apk` | 软件更名为 Hazix；电视与手机版内容页仅显示图标，不显示软件名称 |
| TV v3.3.7 | `dist/Hazix-TV-v3.3.7.apk` | 首页仅保留最近热播与最近观看；分类页改为无缝加载 |
| TV v3.3.8 | `dist/Hazix-TV-v3.3.8.apk` | 修复切换分类后分页状态未重建，导致剧集、综艺、纪录片、动漫与短剧只有第一页 |

后续发布只新增带版本号的 APK，不覆盖或删除历史包。校验值见
`dist/SHA256SUMS.txt`。

原生电视交互和页面组织参考 Google 官方
[Android TV Immersive List](https://developer.android.com/design/ui/tv/guides/components/immersive-list)、
[JetStreamCompose](https://github.com/android/tv-samples/tree/main/JetStreamCompose) 与
[HomeFlix TV](https://github.com/azad25/homeflix-tv-app) 的 Cinematic Hero / 单剧集继续观看思路；
播放器能力使用 AndroidX Media3。完整型电视应用的工程取舍参考了
[FongMi/TV](https://github.com/FongMi/TV)，但本项目没有复制其 GPL 源码，也没有引入爬虫、直播或脚本引擎。

## 遥控器

| 按键 | 浏览界面 | 播放器 |
| --- | --- | --- |
| 方向键 | 移动焦点/滚动列表 | 左右 ±10 秒；上下切集 |
| OK / Enter | 打开聚焦项 | 播放/暂停并显示控制层 |
| 返回 | 返回上一页 | 保存进度并退出播放 |

## 构建

需要 JDK 17、Android SDK Platform 35：

```bash
./gradlew :nativeapp:lintRelease :nativeapp:assembleRelease \
  :mobileapp:lintRelease :mobileapp:assembleRelease
```

输出：

```text
nativeapp/build/outputs/apk/release/nativeapp-release.apk
mobileapp/build/outputs/apk/release/mobileapp-release.apk
```

当前 release 为个人侧载方便，使用本机 debug key 签名。正式分发前应改用私有 release keystore，
并在后续版本持续使用同一签名。

## GitHub 云端发布

仓库推送到 GitHub 后，电视 APK 由 `.github/workflows/release-tv.yml` 在 GitHub Actions 云端构建，
本机不再运行正式打包。工作流支持两种启动方式：

- 推送 `v3.3.8` 形式的版本标签；
- 在 GitHub 的 Actions 页面手动运行并填写 `3.3.8` 形式的版本号。

工作流会执行 release lint、生成签名 APK、验证签名、计算 SHA-256，并把 APK 与校验文件同时
上传到对应 GitHub Release。版本号由标签或手动输入传给 Gradle，不需要每次改构建脚本。

为了让云端生成的新版能够覆盖安装现有 APK，仓库 Actions Secret 必须配置
`ANDROID_DEBUG_KEYSTORE_BASE64`，内容为当前本机 debug keystore 的 Base64。签名文件本身以及
Base64 内容都不得提交到 Git；工作流只在运行器临时目录内还原，任务结束后由 GitHub 销毁。

## 安装

在电视上允许当前文件管理器安装未知来源应用后，通过 U 盘安装
`dist/Hazix-TV-v3.3.8.apk`；或在标准 Android TV 已开启 ADB 调试后执行：

```bash
adb install -r dist/Hazix-TV-v3.3.8.apk
```

TCL/雷鸟系统可能拦截通用 `adb install`，本项目实机升级改用系统自带的
`com.tcl.packageinstaller.service.renew` 安装服务，可保留应用数据。手机版安装
`dist/HDAO-Mobile-v1.1.0.apk`。

影片、分类和播放地址每次启动时从站点公开接口读取，因此内容会随站点实时更新。当前 API
基址为 `https://hdao.tv/api`。当前接口、图片和播放器网络链路会优先使用应用内加密 DNS，
失败后才回退到电视系统 DNS；如果站点更换域名或接口结构，仍需发布新 APK 适配，但不会影响
已经归档的历史安装包。

本项目不会修改电视或 macOS 系统设置。
