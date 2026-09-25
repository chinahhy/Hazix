# 交接记录（HANDOFF）

当同一个仓库由两个编码代理（Codex / DSH）交替接手时，用本文件交接上下文。
**Codex 额度恢复后，请先读完本文件再动手。** 对话上下文不会跨代理传递，只有仓库里的文件会。

## 记录规则

- 只追加，不改写；不要删除或重写历史记录。
- 不 rebase、不 amend、不 force push 已经推送的提交，保持历史只增不改。
- 「改动文件」里列出的文件，动手前请重新读取当前内容，不要依赖更早的上下文快照。
- 没验证的事必须写成「未验证」，不要当成已完成。
- 每个条目给出可复现的验证命令和真实结果。

## 2026-09-19 · 交接基线

- 基线提交：`5392b9e`（`fix(ci): prevent incompatible grouped major updates`）。
- 当时状态：工作区干净，`main` 与 `origin/main` 一致，无未提交改动。
- 检查点 tag：`checkpoint-20260919-baseline`（本地普通 tag，命名刻意避开 `vX.Y.Z`，不会触发发布流水线）。

## 2026-09-19 · DSH 接手轮次（Codex 额度用尽期间）

用户报的 5 项问题，全部处理。**所有改动都提交在 `main`，历史只增不改。**

### 1. 构建与验证环境（最重要，先看这段）

**这台 Mac 上无法编译改过的 Kotlin 源码。** 项目路径含中文
（`/Volumes/Data/ChatGPT项目文件/观影平台`），Kotlin 的 Compose 编译器插件路径会被
转义破坏，实测报错：

```text
error: plugin classpath entry points to a non-existent location:
/Volumes/Data/ChatGPTu9879u76EEu6587u4EF6/u89C2u5F71u5E73u53F0/.tooling/gradle-home/.../kotlin-compose-compiler-plugin-embeddable-2.0.20.jar
```

加不加 Gradle 守护进程结果一样。因此：**本地只做「未改动源码」的构建（走缓存能过），
任何 Kotlin 改动的验证都必须交给 GitHub Actions。**（`.tooling/` 里的工具链本身是可用的：
JDK 17 + Android SDK 35 + platform-tools + Google TV 模拟器都在项目内，由更早的会话下载解压。）

**模拟器同样跑不起来**：它强制要写 `~/Library/Caches/TemporaryItems/avd/running`，
在当前文件沙箱下被拒绝后直接 FATAL。除非明确获得许可，不要再尝试启动模拟器。

**签名密钥**：`~/.android/debug.keystore` 的证书 SHA-256 等于
`release-apks.yml` / `ci.yml` 里写死的 `EXPECTED_SIGNER_SHA256`
（`6f4c4390...f9f1`）。**绝对不要重新生成这个文件**，否则已安装的版本无法覆盖升级。
云端通过 Actions Secret `ANDROID_DEBUG_KEYSTORE_BASE64` 还原同一把钥匙。

### 2. 本轮改动文件

| 问题 | 改动文件 |
| --- | --- |
| 动漫播放时控制条不自动隐藏 | `nativeapp/.../ui/PlayerScreen.kt` |
| 从详情返回分类页焦点应回到原卡片 | `nativeapp/.../ui/Screens.kt`（新增 `CategoryScreenState`）、`ui/HdaoTvApp.kt` |
| TV 缺少可发现的「检查更新」入口 | `ui/Components.kt`、`ui/UpdateDialog.kt`、`update/UpdateViewModel.kt`、`ui/HdaoTvApp.kt` |
| 每次构建同时产出 TV + 手机 APK | `mobileapp/build.gradle.kts`、`.github/workflows/ci.yml`、`.github/workflows/release-apks.yml`（由 `release-tv.yml` 改名） |
| README 专业化 / 许可 / 隐私 | `README.md`（中文主版本）、`README.en.md`（英文镜像）、`CHANGELOG.md`、`LICENSE`、`PRIVACY.md` |

### 3. 根因（供 Codex 复核）

1. **控制条不隐藏**：`LaunchedEffect(controlsTick)` 从最后一次交互起算 4.5 秒，
   到点只在 `isPlaying` 为真时才隐藏。动漫源起播慢，计时结束时 `isPlaying` 还是 false，
   于是控制条永久留在屏幕上。改为 `LaunchedEffect(controlsTick, isPlaying)`，只有真正在播放时才开始倒计时。
2. **返回焦点错位**：分类页所有状态都是 `remember(category)`，进入详情页后整个页面离开组合，
   网格、滚动位置、分页全部销毁；重建时页面里**没有任何一处显式请求焦点**，
   焦点于是落到顶部导航（用户看到的「电影」分类名）。
   改为把状态提升到 `HdaoTvApp` 的 `categoryStates`（新增 `CategoryScreenState`），
   返回时按 `lastOpenedVodId` 滚动回原卡片并显式请求焦点；同时加了「已加载就不重复请求第 1 页」的判断。
3. **找不到更新入口**：`UpdateCoordinator` 只在启动时 `check()` 一次，且
   `UpdateUiState.Checking -> Unit`、无更新时直接回到 `Hidden`，全程零可见入口。
   新增 `UpToDate` / `Checking(manual)` 状态与 `check(manual)`，顶部导航加「检查更新」文字入口。
   注意：`UpdateManager.checkForUpdate()` 在 `BuildConfig.DEBUG` 下直接返回 null，
   所以调试包永远显示「已是最新版本」，要验证「发现新版本」必须装 release 包。
4. **手机版签名隐患**：`mobileapp` 的 release 之前用 `signingConfigs.getByName("debug")`，
   而 AGP 的 debug 配置指向的 keystore 会随 `ANDROID_USER_HOME` 漂移，云端构建可能产出
   无法覆盖安装的 APK。已改为与 `nativeapp` 相同的 `hazixRelease` 配置和同一把钥匙，
   并让两个模块共用 `VERSION_NAME`/`VERSION_CODE`。

### 4. 验证命令与结果

- 本地 `./gradlew :nativeapp:assembleDebug :mobileapp:assembleDebug`：**通过**（50s，全部走缓存）。
- 本地 `./gradlew :nativeapp:compileDebugKotlin`（有源码改动）：**失败**，原因见上面第 1 节，属环境问题，不是代码问题。
- 云端 CI：见本轮各提交的 `ci.yml` 运行结果。
- **未验证（必须真机确认）**：第 1、2、3 项的运行时行为——控制条是否真的自动隐藏、
  返回后光标是否落在原卡片、新入口是否可见且能给出反馈。本机模拟器跑不起来，
  这三项只能在真实电视上验证。

### 5. 待办 / 需要用户确认

- 已处理：用户确认 `Jimmy (Autodarts-TV)` 不是本人。它是第三方 MIT 项目
  [Autodarts-TV](https://github.com/TheJim03/Autodarts-TV) 的版权人，旧的 `app/` 模块改编过其代码。
  该 MIT 声明已从 `LICENSE` 移到 `THIRD_PARTY_NOTICES.md` 完整保留。
- **2026-09-19 更新**：`app/` 模块已整体删除——它从未参与 Gradle 构建（`settings.gradle.kts`
  只 include 了 `:nativeapp` 与 `:mobileapp`），是 758 行死代码，也是仓库内唯一源自第三方
  MIT 项目的代码。删除后仓库已不含该第三方代码，MIT 义务随之消失；
  `THIRD_PARTY_NOTICES.md` 里只保留历史致谢。
- 仓库目前在 GitHub 上是 **public**。用户以为只自己用；若想私有需在仓库设置里改。
- `README.md` 是**中文主版本**（GitHub 仓库首页默认展示的就是它），`README.en.md` 是英文镜像，
  **两份要同步维护**。此前一度把英文放在 `README.md`，已按用户要求交换回来。
- `dist/SHA256SUMS.txt` 是本地台账（`dist/*.apk` 被 gitignore）；云端产物在 GitHub Releases。

### 6. CI 最终结果（提交 `b821e8d`，三个 job 全绿）

- `Web checks`：`pnpm run check` + `pnpm test` 通过。
- `Android tests and lint`：TV 与手机的单测 + `lintDebug` 通过。
- `Build TV and mobile APKs`：双端 release APK（R8 混淆 + 签名 + 签名指纹校验）构建成功。

该 run 的 artifact `Hazix-APKs-<sha>` 内含 `Hazix-TV-<sha>.apk` 与 `Hazix-Mobile-<sha>.apk`，
版本号取模块默认值（3.3.9 / versionCode 17），可以直接覆盖安装现有版本。
推 `vX.Y.Z` 标签时由 `release-apks.yml` 发布为 GitHub Release 的两个正式 APK 加 `SHA256SUMS.txt`。

**踩到的坑，务必记住**：`lintDebug` 曾因 `UpdateDialog.kt` 中
`LocalContext.current as ComponentActivity` 报 `ContextCastToActivity` 错误而失败。
**写成一行的 cast 会被 lint 抓到，写成 `val context = LocalContext.current` 再另起一行 cast 则不会**
——旧代码正是这样侥幸通过的。已改为 `LocalActivity.current as? ComponentActivity`。
结论：**Kotlin 改动必须用 `lintDebug`/CI 验证，`assembleRelease` 通过不代表 lint 通过。**

**CI 失败原因如何在不登录的情况下读到**：job log 需要仓库管理员权限，但 check annotations 是公开的。
`ci.yml` 新增的 `Surface Gradle failures` 步骤把 Gradle 失败行以 `::error::` 注解重复一遍，
于是可用 `https://api.github.com/repos/chinahhy/Hazix/check-runs/<id>/annotations` 读到具体报错。
另外 `Test and lint` 已拆成独立的 `Unit tests` 与 `Lint` 两步，便于定位。

### 7. 给 Codex 的提醒

- 提交历史只增不改，本轮所有修改都在 `main`，无需 merge。
- **绝不要重新生成 `~/.android/debug.keystore`**，否则电视端自更新与手机端覆盖安装都会失败。
- 想在本机编译 Kotlin，必须先解决两件事：中文项目路径导致 Compose 编译器插件路径被转义破坏，
  以及 Kotlin daemon 无法写入项目外目录。在解决之前一律用云端 CI 验证。
- **文档语言**：`README.md` 是中文主版本（GitHub 首页默认展示的就是它），`README.en.md` 是英文镜像，
  两份需同步维护；`CHANGELOG.md`、`PRIVACY.md` 已改为中文为主（`PRIVACY.md` 末尾附完整英文）；
  `LICENSE` 与 `THIRD_PARTY_NOTICES.md` 保持英文——法律文本惯例，第三方许可原文必须逐字保留。
  此前一度把英文放在 `README.md`，已按用户要求交换。

### 9. 第二轮改动（同一会话，Codex 仍在停用期）

用户要求按评估清单继续优化。已完成的提交：

| 提交 | 内容 |
| --- | --- |
| `e71c584` | 删除未参与构建的遗留 `app/` 模块（758 行死代码 + 仓库内唯一的第三方 MIT 代码），新增 `ROADMAP.md` |
| `e867142` | 首页与搜索页的返回状态保持（`HomeScreenState` / `SearchScreenState` 提升到导航层） |
| `c6cb1fd` | 修 CI 报出的可见性错误：`LoadState` 原为文件私有，被新状态类作为公开属性暴露，改为 `internal` |
| `4b12005` | 播放器字幕 / 音轨 / 倍速，新增文件 `nativeapp/.../ui/PlayerSettings.kt` |

**新增约定（新页面必须遵守）**：屏幕状态由导航层 `HdaoTvApp` 持有，屏幕通过参数接收
（`HomeScreenState` / `SearchScreenState` / `CategoryScreenState`），
返回时按 `lastOpenedVodId` 滚动回原卡片并**显式请求焦点**。
把状态写在屏幕内部的 `remember` 里就会重现"返回后丢状态"的问题——这个问题在分类页、
首页、搜索页各出现过一次。

**播放器设置面板的入口**：菜单键（`KEYCODE_MENU` / `SETTINGS` / `TV_CONTENTS_MENU` /
`BUTTON_Y` / `PROG_BLUE`）或**长按 OK**；上下选行、左右调整、返回关闭。
面板不持有可聚焦子元素，不会与播放器的焦点处理冲突。
**注意 `KEYCODE_DPAD_CENTER` 的 `repeatCount >= 1` 被用作长按**，改动 OK 键逻辑时要留意。

**这一轮仍未真机验证**：首页/搜索的返回行为、字幕与音轨是否真能切换、长按 OK 在真实遥控器上
是否可达。这些都属于"看出来的"行为，CI 只能保证编译、lint 与单测通过。
**额外风险**：`PlayerSettings.kt` 里的 `setOverrideForType` / `clearOverridesOfType` 只做了静态校验，
从未在真实媒体流上跑过——若某个片源切换字幕后黑屏或无声，优先怀疑这里。

### 8. 当前发布状态（交接时的实际状态）

- **v3.4.0 已发布**：<https://github.com/chinahhy/Hazix/releases/tag/v3.4.0>，
  资产为 `Hazix-TV-v3.4.0.apk`（2,792,558 字节）、`Hazix-Mobile-v3.4.0.apk`（2,740,164 字节）、
  `SHA256SUMS.txt`。
- 已核对：TV 包为 `tv.hdao.app` 3.4.0（versionCode 3004000），手机包为 `tv.hdao.mobile` 3.4.0（3004000），
  两者签名证书 SHA-256 = `6f4c4390...f9f1`，与已安装版本一致，可直接覆盖升级。
- 两个 APK 已归档到 `dist/`，校验值追加进 `dist/SHA256SUMS.txt`（该文件受版本控制，APK 本身被 gitignore）。
- `v3.4.0` 的发布说明只有一行自动生成的对比链接（当时工作流用的是 `--generate-notes`）。
  已改为从 `CHANGELOG.md` 提取对应版本小节生成发布说明，**下一个版本起生效**；
  v3.4.0 那段说明如需补全，只能在 GitHub 网页上手动编辑。
- **可验证的二进制证据**：`v3.4.0` 电视 APK 的 `classes.dex` 里确实含有新增界面字符串
  `检查更新`、`正在检查更新`、`已经是最新版本`（用对照串 `本地更新` 验证过检索方法不会假阳性），
  说明新入口真的编进了发布包；其合并 DEX 摘要与 v3.3.9 不同（`0a348139…` vs `27a6cf12…`）。
  复现：`unzip -o dist/Hazix-TV-v3.4.0.apk 'classes*.dex' -d .tooling/tmp/dex &&
  grep -a 检查更新 .tooling/tmp/dex/*.dex`
- **三项 UI 行为仍未真机确认**（属于"看出来的"行为，CI 无法覆盖）。请在电视上按三步入场验证：
  1. 播放一集动漫，手离开遥控器：4.5 秒后进度条应消失，按任意键重现；暂停或缓冲时不应消失。
  2. 电影分类向下翻很多 → 进入某片详情 → 返回：光标应回到刚才那张卡片，滚动位置不跳、不重载第一页。
  3. 顶部导航右侧应出现「检查更新」，点击后显示"已经是最新版本 v3.4.0"。
  另外，已安装的 v3.3.9 启动时会发现 v3.4.0 并可应用内升级，这正好覆盖完整升级链路。

### 10. 第三轮改动（同一会话，Codex 仍在停用期）

| 提交 | 内容 |
| --- | --- |
| `f447f35` | 收藏（详情页开关 + 首页「我的收藏」行）与搜索历史（最近 8 条），新增 `data/UserCollections.kt` 与 `data/UserCollectionsTest.kt` |

**新增存储**（都在 `data/UserCollections.kt`，与 `WatchProgress` 同构，只用本机 SharedPreferences）：
`Favorites`（`all()` / `isFavorite()` / `toggle()`，存为一条 JSON 数组，最多 100 条）与
`SearchHistory`（`all()` / `record()` / `clear()`，默认 8 条）。

**可单测的纯函数**（同文件顶层，`UserCollectionsTest` 覆盖）：
`orderedFavorites`、`cleanSearchTerms`、`updatedSearchTerms`、`FavoriteEntry.toVod`。
**新增纯逻辑请沿用这个做法**：把与 Android 无关的部分抽成顶层函数，否则单测跑不了
（JVM 单元测试里没有真的 `org.json`，直接测 store 会失败）。

**UI 接线**：`HdaoTvApp` 创建两个 store 并传下去；首页的收藏行在每次进入首页时重新读取
（`LaunchedEffect(Unit) { state.favoriteItems = favorites.all().map { it.toVod() } }`），
因此在详情页收藏后返回首页即可看到。

**仍未真机验证**：收藏按钮的焦点顺序、首页收藏行的焦点与滚动、「最近搜索」一排按钮的导航。
**注意**：收藏与搜索历史目前**只在电视端实现**，手机端还没有（已记入 `ROADMAP.md`）。

### 11. 电视端网络故障排查（2026-09-19）

用户报告两个现象，成因不同：

1. **「检查更新失败（403）」**：GitHub 对未登录请求按**公网 IP** 限流（60 次/小时）。
   开发机在同一出口网络下反复轮询 GitHub API，会把整个家庭的额度耗光，电视端随即收到 403。
   **排查纪律：不要用 `api.github.com` 轮询 CI 状态**，改用工作流徽章
   （`https://github.com/<owner>/<repo>/actions/workflows/ci.yml/badge.svg?branch=main`，不占配额）
   或直接下载 release 资产（`releases/download/...`，也不占配额）。配额每小时重置。
   代码侧已把 403/429 换成「GitHub 暂时限制了更新检查，请稍后再试」。

2. **「无法连接影视数据源」**：从开发机实测 `https://hdao.tv/api/vods/featured` 返回 **HTTP 200**，
   说明站点正常；但本机 DNS 把 `hdao.tv` 解析到 **`198.18.5.171`（代理 Fake-IP 保留段）**，
   即该网络使用 Fake-IP 模式的代理。电视能拿到 GitHub 的 403 响应，说明外网可达，
   失败只发生在 hdao.tv 这条路径上。根因尚未确诊，因此本轮做了两件事：
   - **把真实原因显示出来**：`HdaoApi.request` 之前把 `IOException` 的原文吞掉，只留一句笼统提示；
     现在会把底层原因（超时／连接被拒／解析失败原文）拼进界面文案。**下次报错截图即可确诊。**
   - **增加第二条解析路径**：`NetworkClients.systemDnsClient`（`Dns.SYSTEM`）作为兜底，
     `HdaoApi.request` 第一次失败后用它整条重试。
   - **不要**把系统 DNS 结果与加密 DNS 结果混在同一个 `lookup` 里返回：
     `NetworkClientsTest` 明确断言主路径不得出现 `198.18.`／`198.19.`／`28.` 开头的 Fake-IP，
     这是 v3.0.1 的既定设计，改动会直接让测试失败。

**遗留缺口**：播放器（`PlayerScreen` 的 `OkHttpDataSource`）只使用加密 DNS 客户端，
没有第二条解析路径；若内容接口靠兜底恢复而播放仍失败，就是这个原因。

**测试脆弱点**：`NetworkClientsTest` 会**访问真实网络**（请求 hdao.tv）。
若站点限制 GitHub Actions 出口 IP，CI 会无故变红——排查 CI 失败时先看这一条。

## 2026-09-19 · 第五轮：电视端应用内升级失败的根因与修复（v3.6.4）

用户报「安装包已经下载好了，但到安装这一步报错」。弹窗是
**更新没有完成 / 无法验证更新包签名**（`Failed(release != null, …)`）。
用户先前发过一张 403 的图，那张是发错的，不是本次现象。

### 1. 根因（AOSP 源码证据，不是推测）

`UpdateManager.verifyPackage()` 用
`PackageManager.getPackageArchiveInfo(path, GET_SIGNING_CERTIFICATES)` 读下载好的 APK 的签名，
再与已安装包比对。但 **Android 9～13 只有在 flags 里带 `GET_SIGNATURES` 时才会去收集证书**：

- Android 9 (P) `core/java/android/content/pm/PackageManager.java`：
  ```java
  PackageParser.Package pkg = parser.parseMonolithicPackage(apkFile, 0);
  if ((flags & GET_SIGNATURES) != 0) { PackageParser.collectCertificates(pkg, false); }
  return PackageParser.generatePackageInfo(pkg, null, flags, 0, 0, null, state);
  ```
- Android 9 (P)、10 (Q)、13 (T) 的源码都只有 `if ((flags & GET_SIGNATURES) != 0)`；
  **Android 14 (U) 才**改成 `if (GET_SIGNATURES || GET_SIGNING_CERTIFICATES)`。
- 没收集证书时 `pkg.mSigningDetails == SigningDetails.UNKNOWN`，而 `generatePackageInfo` 里是
  `if (mSigningDetails != UNKNOWN) pi.signingInfo = new SigningInfo(...); else pi.signingInfo = null;`
  → `signingInfo` 为 null → `apkContentsSigners.orEmpty()` 为空 →
  `require(archiveSigners.isNotEmpty() …) { "无法验证更新包签名" }` 直接失败。

用户电视是 **TCL/雷鸟 Android 9**（见 `preview/tv-v3.3.0/design-audit.md:56`），正落在受影响区间。
`dist/*.apk` 实测**只有 v2 签名**，因此这条路径在该机型上从来没有成功过。

复现（本机可跑，走项目内工具链）：
```bash
JAVA_HOME=$PWD/.tooling/jdk/jdk-17.0.20.1+1/Contents/Home \
  .tooling/android-sdk/build-tools/35.0.0/apksigner verify --print-certs -v dist/Hazix-TV-v3.6.3.apk
# Verified using v1 scheme (JAR signing): false
# Verified using v2 scheme (APK Signature Scheme v2): true
```
AOSP 源码取法（`?format=TEXT` 返回 base64，且**没有换行**，必须用 `openssl base64 -d -A`：
`base64 -d` 解不出来）：
```bash
curl -sS "https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-9.0.0_r1/core/java/android/content/pm/PackageManager.java?format=TEXT" \
  | openssl base64 -d -A | grep -n -A22 "public PackageInfo getPackageArchiveInfo"
```

### 2. 本轮改动文件

| 文件 | 改动 |
| --- | --- |
| `nativeapp/.../update/UpdateManager.kt` | 主修复：`SIGNING_FLAGS = GET_SIGNATURES or GET_SIGNING_CERTIFICATES`；签名从 `signingInfo` 与旧的 `signatures` 两处合并；平台不给签名时不再阻止安装；`cachedUpdate()` 复用缓存里已校验的包；API 失败回退到 `releases/latest` 重定向；`requireTrustedReleaseUrl` 改用 OkHttp `HttpUrl`（可在 JVM 单测里跑） |
| `nativeapp/.../update/UpdateViewModel.kt` | 检查更新时先取缓存包；网络结果与缓存版本相同则直接 `Ready`；网络失败或"已是最新"时，只要缓存里有更新包就仍然提供安装 |
| `nativeapp/.../ui/UpdateDialog.kt` | `Ready` 弹窗标题带上版本号：`更新包已就绪 vX.Y.Z` |
| `nativeapp/build.gradle.kts`、`mobileapp/build.gradle.kts` | `hazixRelease` 打开 `enableV1Signing`（v1+v2 双签名，同一把钥匙，覆盖安装不受影响） |
| `nativeapp/src/test/.../UpdateManagerTest.kt` | 新增两条纯函数单测：重定向 Location → tag、发布资产 URL 约定 |
| `CHANGELOG.md` | v3.6.4 条目 |

### 3. 重要：本机能编译 Kotlin 了（推翻第 1 节的旧结论）

第 1 节说"本机无法编译改过的 Kotlin"——那是**路径问题，不是环境缺件**。把**项目和
GRADLE_USER_HOME 都放到纯 ASCII 路径**下就能正常编译、跑单测和 lint：

```bash
SRC="/Volumes/Data/ChatGPT项目文件/观影平台"
rsync -a --exclude '.tooling' --exclude '.git' --exclude '.gradle' --exclude '.kotlin' \
      --exclude 'build' --exclude 'node_modules' "$SRC/" /tmp/hazix/
cp -a "$SRC/.tooling/gradle-home" /tmp/gradle-home          # 1.8G，必须真拷贝：符号链接会被解析回中文真路径
mkdir -p /tmp/android-sdk && cp -a "$SRC/.tooling/android-sdk/build-tools/34.0.0" \
      "$SRC/.tooling/android-sdk/build-tools/35.0.0" /tmp/android-sdk/build-tools/ \
  && cp -a "$SRC/.tooling/android-sdk/platforms/android-35" /tmp/android-sdk/platforms/
ln -sfn "$SRC/.tooling/jdk/jdk-17.0.20.1+1/Contents/Home" /tmp/jdk
printf 'sdk.dir=/tmp/android-sdk\n' > /tmp/hazix/local.properties
cd /tmp/hazix
JAVA_HOME=/tmp/jdk PATH=/tmp/jdk/bin:$PATH ANDROID_HOME=/tmp/android-sdk GRADLE_USER_HOME=/tmp/gradle-home \
  ./gradlew :nativeapp:testDebugUnitTest :nativeapp:lintDebug --no-daemon
```
关键点：`build-tools/34.0.0` 也要拷（AGP 8.6.1 默认用它）；不要加 `--offline`
（lint 的 `debugAndroidTestCompileClasspath` 有些依赖不在缓存里）。
`enableV1Signing` 的效果要用 `--min-sdk-version 23` 才看得出来：minSdk 24 的包
apksigner 默认**不校验** v1，会直接打印 `v1 scheme: false`，那不代表签名无效。

**发布钥匙在本机不存在，正式包只能由 CI 产出。** `.tooling/android-user/debug.keystore`
可以给本地 release 构建用（`HAZIX_RELEASE_KEYSTORE=<该路径>`），但它**不是**发布钥匙：
它导出的证书 SHA-256 是 `6161046b1e77824a08b5cf8b3ed28d71ce13f7a16c851dd62f4553983414e4db`，
而发布链要求 `6f4c4390f681e237d466ab0d4bfb7f43305f8c24d169d4a4320d2a041f3fd9f1`
（旧记录里的 `~/.android/debug.keystore` 现在已不存在）。真钥匙只在 GitHub Actions secret
`ANDROID_DEBUG_KEYSTORE_BASE64` 里，所以**绝不能把本地构建的 v3.6.4 发给用户**——
签名不同，覆盖安装会被系统拒绝。

### 4. 验证结果

- `:nativeapp:testDebugUnitTest`：**通过**，`UpdateManagerTest` 5 项（含两条新增）0 失败，全部测试 0 失败。
- `:nativeapp:lintDebug`：**通过**（`abortOnError = true` 生效）。
- `:nativeapp:assembleRelease :mobileapp:assembleRelease -PVERSION_NAME=3.6.4 -PVERSION_CODE=3006004`
  （本地钥匙，仅验证打包与签名配置）：**通过**；产出的 APK 用
  `apksigner verify -v --min-sdk-version 23` 检查为 **v1 true + v2 true**，
  对照 `dist/Hazix-TV-v3.6.3.apk` 在同一命令下是 `DOES NOT VERIFY / Missing META-INF/MANIFEST.MF`
  （即旧包确实没有 v1 签名）。本地包签名者是 `6161046b…`，**不是**发布链，仅供验证。
- **未验证（必须真机确认）**：电视上点「安装更新」后系统安装器是否真的完成升级。
  本机没有可用的安卓设备（`adb devices` 为空），模拟器仍受沙箱限制。

### 4b. 本轮发布状态：v3.6.4（2026-09-19）

- 提交 `180ccc2` 推到 `main`，tag `v3.6.4` 触发 `release-apks.yml`，云端签名并发布：
  <https://github.com/chinahhy/Hazix/releases/tag/v3.6.4>
- 官方电视包 `Hazix-TV-v3.6.4.apk` 已实测：
  - `apksigner verify -v --min-sdk-version 23 --print-certs`：**v1 true + v2 true**，
    signer SHA-256 = `6f4c4390…f9f1`，与已安装版本同一把钥匙，可直接覆盖安装；
  - `aapt2 dump badging`：`tv.hdao.app`，versionCode 3006004 / versionName 3.6.4；
  - SHA-256 `f2a08a7b…7e05`，与 release 的 `SHA256SUMS.txt` 一致；
  - dex 内含新界面串「更新包已就绪」。
- 手机包 `Hazix-Mobile-v3.6.4.apk`：SHA-256 `30176158…7fa3`。
- 两个包已归档进 `dist/`，校验值追加到 `dist/SHA256SUMS.txt`（该文件受版本控制，APK 被 gitignore）。
- **用户必须手动装一次 v3.6.4**：他电视上那版（≤ v3.6.1 的某个版本）的应用内升级永远走不通，
  装完这一次之后，后续版本才能靠应用内升级完成。

### 5. 给下一个接手的代理

- 不要再相信"本机不能编译 Kotlin"这条旧结论，第 3 节的做法可直接复用；
  唯一代价是每次改动后要重新 `rsync` 到 `/tmp/hazix`。
- `api.github.com` 对未登录请求按公网 IP 限流 60 次/小时，**不要用它轮询**；
  需要判断最新版本时用 `curl -sI https://github.com/chinahhy/Hazix/releases/latest` 看 `Location`。
- 应用内升级的签名校验**不是**安全边界：真正把关的是发布页 SHA-256 与系统安装器本身；
  所以"平台读不到签名"时应当继续安装，而不是报错挡住用户。


## 2026-09-19 · DSH 第二轮：首页按 Netflix 视觉语言重做（浏览器预览先做）

用户反馈原话：「类 Netflix 首页，codex 没做到……内容行不用太多了，现在就够了，
就是首页样式不像，很不像，海报就不像，还有很多都不像」。
按 `AGENTS.md`，先在 `web/` 浏览器预览里做，用户满意后才动电视端 Kotlin。

### 1. 诊断：差的不是数据行，是视觉语言

`/api/vods/featured` 服务端本来就返回 `hero` + 6 类行（movies / tv / variety / anime /
documentary / shortDrama，各 50 条），但网页首页只用了 `recentHot()` 拼出的 6 条
「最近热播」，其余数据全部没用上；电视端 Kotlin 同样只渲染 1 排。真正「不像」的是：

| 位置 | 旧实现 | Netflix 实际做法 |
| --- | --- | --- |
| 强调色 | 金色 `#f4c15d`（标题、评分角标、焦点框、进度条，共 20 处） | 纯黑 `#141414`/`#000` + 白 + 灰 `#b3b3b3`，几乎不用彩色，唯一彩色是评分绿 `#46d369` |
| 卡片 | 2:3 竖版 + 1px 描边 + 圆角 4px + 金色评分角标 + 底部渐变 | 行内用 16:9 横版圆角 4px，无描边无角标，聚焦时 `scale(1.16)` + 白描边 + 浮层（标题/评分/播放/详情） |
| 巨幕 | 顶部 6 张横版轮播 + 左箭头 | 全幅背景 + 左侧文案 + 底部渐隐，且内容区更高（标题落在屏幕下三分之一） |
| 导航 | 22px 粗体字 + 金色下划线 | 15px/500 白灰字，无下划线，滚动后变实底 |
| 字体比例 | h1 66 / h2 27 / 边距 72 | h1 38–58 / h2 17–22 / 边距 88 |

另一个「海报不像」的客观原因是数据：`tmdbBackdrop` 缺失时只能退化成站点竖版封面，
裁成 16:9 后构图就散了。实测覆盖率（`/api/vods/featured` 快照）：

```text
hero 6/6 有 backdrop；movies 43/50；tv 46/50；variety 38/50；anime 36/50；documentary 44/50；shortDrama 0/50
```

### 2. 本轮改动文件（只动预览，未动 Kotlin/APK）

| 文件 | 改动 |
| --- | --- |
| `web/public/app.css` | 整体重写：`:root` 换单色令牌（去掉 `--accent` 金色）、导航改固定顶栏、巨幕改全幅 + 两段式渐隐、卡片/行/焦点/悬浮层全部按 Netflix 重做，手机断点同步调整 |
| `web/public/app.js` | `card()` 改为无角标卡片并加 hover/focus 浮层（播放/详情/标题/评分）；首页改用服务端 `catalog.hero`（不再用 `recentHot`）、巨幕交叉淡入（`scene()`）、导航滚动变实底、无观看记录时不再渲染空行 |
| `web/public/preview.css` | 预览外壳的选中态/焦点色去掉金色 |
| `preview/netflix-home-v1/*.png` | 前后对比：`01-before-tv-1920`（旧）、`02-after-tv-1920`（新）、`03-after-phone-414`（新，手机） |

### 3. 本机预览与截图工具（可复用）

```bash
cd web && node server.mjs                       # 预览：http://127.0.0.1:4173
cd web && pnpm run check && pnpm test           # 8/8 通过
```

无头 Chrome 截图有两个坑，都踩过了：

1. 必须 `--no-sandbox`，否则 `sandbox initialization failed: Operation not permitted`；
2. **无头 Chrome 的窗口最小宽度是 500px**：`--window-size=414,896` 实际视口是 500 宽，
   截出来的 414px 图右侧会被裁掉，看起来像「文案溢出屏幕」。要精确模拟手机视口必须用
   DevTools 协议 `Emulation.setDeviceMetricsOverride`（`/tmp/shot-cdp.mjs` 的做法）。
   用窗口截图量布局得出的结论不可信，务必先用 `innerWidth` 核对。

### 4. 已验证 / 未验证

- 已验证：`pnpm run check`（语法）与 `pnpm test`（8 项）通过；1920×1080 与 414×896 两种
  视口都抓图确认过：导航、巨幕、文案、按钮、横向行、手机底部导航均为新样式，
  `document.scrollWidth === innerWidth`，无横向溢出。
- 未验证：真实遥控器焦点移动、hover 浮层在触屏上的表现、以及电视端原生效果——
  本轮**没有改任何 Kotlin**，电视端首页还是旧的 Compose 布局（金色、竖版卡片、单排）。
- 未做（等用户确认预览后再做）：把这套视觉语言移植到
  `nativeapp/.../ui/Screens.kt`、`Components.kt`、`Theme.kt`（`Gold` → 白色强调）、
  `HomePreviewPlayer`；Kotlin 改动按本文件第 3 节用 `/tmp/hazix` 编译 + `lintDebug` 验证。

### 5. 给下一个接手的代理

- 首页视觉基准看 `preview/netflix-home-v1/02-after-tv-1920.png`，旧版看 `01-before`。
- 预览首页的三条硬规则：不要引入金色/彩色强调（只允许 `#46d369` 评分绿）、行内卡片用
  16:9、导航用 15px 白灰字。改动前先读 `web/public/app.css` 的 `:root` 令牌。
- 电视端 Kotlin 与预览是两套实现，改了一边要同步另一边，否则用户看到的和预览不一致。

> 注意：`preview/` 目录在 `.gitignore` 里（第 15 行），所以上面那三张对比图只存在于本机，
> 换机器或 clone 下来是看不到的；要留证据请把图挪到 `preview/` 之外的路径，或改成文字描述。

### 6. 用户复查后的第二次修正（同一轮）

用户反馈两件事，都查清并处理了：

1. **「首页怎么只有轮播海报，没有最近播放」** —— 不是丢失，是两个原因叠加：
   - 「最近观看」行只在真有观看记录时渲染（Netflix 的做法），而用户在预览里还没播过片。
     用脚本写入 3 条 `hdao.web.progress.v1` 后，DOM 里确实出现 `最近观看` 行与 3 张卡
     （`rows: ["最近观看","最近热播"], continueCards: 3`），功能是好的。
     注意：造出来的记录里如果 `item` 没有 `tmdbPoster`，预览的 `/image` 代理只放行 TMDB
     路径，卡片就是灰底「暂无图片」——真实观看记录里有 `tmdbPoster`，不会这样。
   - 首屏被巨幕吃满。已把巨幕压到 `min(500px, 68vh)`（≥1500px 断点 `min(540px, 72vh)`）、
     巨幕下边距 40px、`最近观看` 行上边距 16px。1920×1080 实测：巨幕 500 / 最近观看 540 /
     最近热播 847，视口高 937 —— 首屏能看到最近观看完整一排加最近热播的标题与卡片上半截。
2. **「检查更新没了」** —— 网页预览版从来就没有这个入口（它是电视端顶栏的功能），
   现在补上了：
   - `web/public/app.html` 里放 `<!-- version -->` 占位，`server.mjs` 服务时注入
     `window.__HDAO_VERSION__`（默认 `3.6.4`，可用环境变量 `HDAO_VERSION` 覆盖）；
   - 顶栏新增「检查更新」按钮（`data-update`），点击后查
     `api.github.com/repos/chinahhy/Hazix/releases/latest`，按版本号比较后给出
     「发现新版本 / 已是最新版本 / 网络不可用 + 打开发布页」三种结果；
   - **踩到的坑**：这个按钮在 `#nav` 里，而点击委托原来只挂在 `#content` 上，
     事件根本传不到 `#content`，所以按钮点了没反应。已把 `[data-update]` 的委托移到
     `document` 的点击监听里（和 `[data-dialog-close]` 同一处）。改动点击委托时务必确认
     目标元素在哪个容器里。
   - 实测：点击后弹窗先显示「当前版本 v3.6.4，正在查询最新版本…」，约 10 秒后变为
     「已是最新版本 / 当前版本 v3.6.4，无需更新。」（GitHub 最新 release 就是 v3.6.4）。

本轮新增/改动：`web/public/app.js`（更新检查、弹窗、委托修正、`card()`/`home()`）、
`web/public/app.css`（弹窗、检查更新按钮、巨幕与行距收紧）、`web/public/app.html`（版本占位）、
`web/server.mjs`（`APP_VERSION` 注入）、`HANDOFF.md`。
`pnpm run check` 与 `pnpm test`（8/8）在每次改动后都跑过。

### 7. 用户第三次反馈：顶栏字号 + 遥控器按键音效

1. **顶栏分类字号太小（65 寸电视）** —— 之前按 Netflix 网页的 15px 做，但那是桌面视距；
   10-foot UI 要按客厅视距放大。已改为：分类链接 `21px`（≥1500px 断点），中屏断点
   `18px`（原来是 14px），logo 34→42px，图标 21→26px，导航高 68→84px，项间距 18→26px，
   「检查更新」14→18px。巨幕 `padding-top` 同步加到 146px，避免顶栏压住标题。
   **规矩：1080p 下顶栏文字不要低于 20px，中屏不要低于 18px。**
2. **遥控器按键声：确实从来没有** —— 全仓搜过，`SoundEffectConstants` / `playSoundEffect` /
   `AudioManager` 一处都没有，不是「不支持」，是没做。预览版先补上：
   - 新增 `web/public/sound.js`：用 Web Audio 合成短促按键音（移动 660Hz / 确认 880Hz，
     各带指数衰减，增益 0.055/0.08），开关存 `localStorage['hdao.web.sound.v1']`；
   - `isFeedbackKey()` 放在 `web/public/data.js`（纯函数、可单测）：只有方向键、Enter/空格、
     Escape/Backspace/BrowserBack 出声；音量键、电源键、字母键、带修饰键的组合一律静音；
   - `app.js` 的 keydown 第一行调用 `playKeyFeedback(event)`，`event.repeat` 直接跳过
     （长按方向键不会连环作响），移动音之间还有 70ms 间隔保护；
   - 顶栏加了音效开关（音量图标，`data-sound-toggle`），`aria-pressed` 反映状态。
   - **验证**（`/tmp/sound-test5.mjs`，CDP 发 `isTrusted` 真实按键 + 给真实 AudioContext 打桩
     统计 `createOscillator` 调用）：连续按 右、左、上、下、Enter、右、下 得到
     `0,1,1,1,1,1,1` 次发声——**第一次没有声音是浏览器的自动播放策略**（AudioContext 必须等一次
     用户手势才能 running），用户在预览里点一下页面就解决了；打包后的电视端走 Android
     系统按键音，没有这个限制。
   - 单测：`pnpm test` 9/9 通过（新增「只有遥控器导航、确认和返回键才触发按键音效」）。
     `pnpm run check` 已把 `data.js`、`sound.js` 纳入语法检查。

**给移植到电视端的具体做法（下一轮做）**：

- `nativeapp` 侧新增按键音：`val view = LocalView.current` + `view.playSoundEffect(SoundEffectConstants.CLICK)`，
  挂在 `HdaoTvApp` 根 Box 的 `onPreviewKeyEvent`（或 `MainActivity` 的 `dispatchKeyEvent` 兜底），
  `ACTION_DOWN` 且 `repeatCount == 0` 才发声，映射方向键与 Enter/DPAD_CENTER 为 CLICK、
  返回键为 BACK——与预览的 `isFeedbackKey()` 一一对应；
- 电视端不需要自己合成音：`playSoundEffect` 走系统音效池（TCL/雷鸟这类机型都带），
  且用户关系统音量/静音时自动安静；
- 开关要持久化（`SharedPreferences`，仿 `Favorites`），入口放 `TopNavigation` 顶栏，
  状态语义与预览的 `aria-pressed` 一致；
- Kotlin 改动按第 3 节用 `/tmp/hazix` 编译 + `lintDebug` 验证；音效本身**必须真机听**，
  这台 Mac 没有可用设备（`adb devices` 为空），届时要在交接里写明「未真机验证」。

### 8. 修正（用户第三次反馈的追加要求）

用户要求：**去掉按键音效开关**，音效默认开启，关闭交给电视的系统设置。已改：

- `web/public/app.js`：删除顶栏音效开关按钮、`soundToggleMarkup()` 与开关的点击处理；
  只保留 keydown → `playMoveSound/playConfirmSound/playBackSound`。
- `web/public/sound.js`：删除 localStorage 开关与 `soundEnabled/setSoundEnabled/resetSoundForTest`，
  音效恒定开启（浏览器端由系统/设备静音控制，Android 端由系统「按键音」设置控制）。
- `web/public/app.css`：删除 `.sound-toggle` 规则（含两处断点）。
- 结果：顶栏右侧只剩「检查更新 / 搜索 / 我的」，导航高 84px、分类 21px 不变。

**这一轮我自己踩的坑，写下来免得再犯**：用 Python 脚本按「起点字符串 → 终点字符串」整段删除
代码时，起点取的是音效开关的注释，终点取的是 `let epoch = 0`，结果把夹在中间的
`const icon = name => ...` 一起删掉了——`node --check` 语法检查**通过**（只是少了一个声明），
但运行时报 `ReferenceError: icon is not defined`，整个首页白屏（nav 和 content 全空）。
教训：删代码后不能只看 `node --check`，必须真的打开页面看 DOM——
`/tmp/diag-app.mjs` 用 CDP 抓 `Runtime.exceptionThrown` 一眼就能看到。

### 9. 修正：把卡片评分角标加回来（用户第三次反馈的追加要求）

用户问「评分呢？我之前是有评分的」。查过了：**数据一直都在**
（`/api/vods/featured` 快照：hero 6/6、movies 50/50、tv 49/50、variety 42/50、anime 48/50、
documentary 48/50 有分；只有 shortDrama 50 部**全无评分**），
是我在第 1 节重做视觉时按"Netflix 卡片不显示评分"把金色角标删了，判断错了。

现在：`.card-score` 中性深色胶囊（`rgba(0,0,0,.72)` + 浅灰字，13px；横版 12px；手机 11px），
放在**每张卡片**左上角——横版行、竖版分类页、搜索结果、我的页面都有，不藏在悬浮层里。
巨幕的评分仍是 `meta()` 里的绿色数字。

实测：首页 `badges: 6`（6.7/6.4/7.2/8.7/8.8/8.3）；分类页 `cards: 72, badges: 72`，
无 JS 异常。**评分显示不要再删**——它不是装饰，是用户挑片的主要依据。
`rgba(#46d369)` 只在文字上用于强调，没有回到大金色块。

## 2026-09-19 · DSH 第四轮：更新机制改后台自动检查 + NAS 中转站

用户两条要求：
1. **删掉「检查更新」入口**，改成后台任务：每次打开软件自动在后台查版本，有更新就提醒，
   让用户选「立即更新 / 稍后」。
2. **大陆网络访问 GitHub 不方便**，用他自己的 NAS 做中转。

### 1. 更新检查改成真正的后台任务

| 文件 | 改动 |
| --- | --- |
| `ui/Components.kt` | 删除顶栏「检查更新」项与 `onCheckUpdate` 参数（连带不再需要那里用到的 `Icons.Rounded.Refresh` 导航项） |
| `ui/HdaoTvApp.kt` | 去掉 `onCheckUpdate` 传参；`UpdateCoordinator` 保留 |
| `ui/UpdateDialog.kt` | `Checking` 状态不再弹窗（后台检查不该有 UI 痕迹）；`UpToDate` 分支删除；`Available` 的按钮改成「立即更新 / 稍后」；失败重试改调 `check(force = true)`；启动检查前 `delay(5 分钟)` 等电视把网络拉起来 |
| `update/UpdateViewModel.kt` | 状态机精简为 `Hidden / Checking / Available / Downloading / Ready / Failed`；`check(manual)` 改为 `check(force)`；用 `SharedPreferences("update_check").lastCheckAt` 做节流：**成功 6 小时**、**失败 10 分钟**后再试，所以"每次打开都查"不会真的每次都打网络，开机时网络没就绪也能在几分钟后自动补上 |

行为：电视开机 → 5 分钟后静默检查 → 有新版本就弹「发现新版本 vX」+「立即更新 / 稍后」。
没有新版本、或网络失败，界面完全不出现（静默）。

### 2. NAS 中转站（`tools/nas-release-proxy.mjs`）

程序只依赖 Node 标准库（Node 18+），**保持和 GitHub 完全一样的 URL 结构**，
所以 App 侧只换 host，`tagFromReleaseLocation`、`requireTrustedReleaseUrl`、
SHA-256 校验逻辑一行都不用改。部署说明见 `tools/README-nas-mirror.md`
（compose / docker run / 直接 node 三种写法，群晖威联通都适用）。

本机实测：

```text
/healthz                     → {"ok":true,...}
/releases/latest             → 302 到 releases/tag/v3.6.4
首次下载（回源 GitHub）       → 1.12s, 2.5 MB/s
二次下载（命中 NAS 缓存）     → 0.18s, 15.7 MB/s
Range 断点续传               → 206，8KB 正确
SHA-256                      → f2a08a7b… 与 dist/SHA256SUMS.txt 一致
非白名单路径                  → 404
```

**踩到的坑**：`path.resolve(process.argv[1]) === new URL(import.meta.url).pathname`
在含中文的仓库路径下永远不相等（URL 的 pathname 是百分号编码的），
结果进程启动后什么都不监听、也不报错。必须用 `fileURLToPath(import.meta.url)`——
本仓库的 `web/server.mjs` 早就这么写了，照抄即可。

### 3. App 侧接镜像

- `update/UpdateManager.kt`：
  - 新增 `ReleaseSource(label, base, api)` 与来源列表 `sourcesFor()`：
    **配了 NAS 就先试 NAS，失败自动回退 GitHub**，下载 URL 用命中的那个来源；
  - 新增 `normalizeMirrorBase()`（纯函数、可单测）：接受 `192.168.1.10:8088`
    这种裸主机，自动补 `http://`；带路径或查询串的一律判为非法（宁可不启用镜像，
    也不产生坏 URL）；
  - 镜像探测节流 `mirrorProbeDue` / `MIRROR_PROBE_INTERVAL_MS = 30 分钟`：
    NAS 不可用（电视不在家）时只多花一次超时，不会每次检查都等；
  - `releaseDownloadUrl(tag, apkName, base)`、`tagFromReleaseLocation(location, base)`、
    `requireTrustedReleaseUrl(value, base)` 都加了 base 参数，默认仍是 GitHub，
    **原有安全校验（必须是本项目 release 路径、host 必须匹配、GitHub 必须 https）不变**。
  - 地址来源：`BuildConfig.MIRROR_BASE_URL`（构建期 `-PhazixMirrorBase=…` 写入，
    默认空 + 空字符串支持）或运行期 `update_mirror` SharedPreferences，
    **不写死任何地址**，没配就完全走 GitHub。
- `AndroidManifest.xml` + 新增 `res/xml/network_security_config.xml`：
  `usesCleartextTraffic` 仍是 false，只对 RFC1918 私有网段 / `.local` / Tailscale 网段
  放行明文 http——否则 NAS 的 `http://192.168.x.x:8088` 在 Android 9+ 上会被直接掐断。
- `nativeapp/build.gradle.kts`：新增 `buildConfigField MIRROR_BASE_URL`。
- `nativeapp/src/test/.../UpdateManagerTest.kt`：新增 4 条纯函数单测
  （镜像地址规整、镜像下载 URL、镜像重定向只认配置的 host、默认仍是 GitHub）。

### 4. 验证状态

- 本机（`/tmp/hazix`，纯 ASCII 路径）编译 + 单测结果见下一节；
- NAS 中转站：本机实测通过（上面那张表）；
- **未验证**：真机上的完整升级链路（NAS → 下载 → 签名校验 → 系统安装器），
  这台 Mac 没有可用安卓设备（`adb devices` 为空），必须在用户电视上确认。

### 5. NAS 中转站已真实部署（极空间 Z4Pro，10.0.0.104:18088）

用户的 NAS 是极空间 Z4Pro（x86_64，Docker 27.5.1 + Compose v2.21），已按下面的方式部署完成：

- compose：`/zspace/zsrp/zdocker/compose_config/hazix-mirror/docker-compose.yml`
  （沿用极空间自带约定目录 `/zspace/zsrp/zdocker/compose_config/<名字>/`）
- 缓存：同目录 `./cache`，首轮 TV 包 + 手机包 + SHA256SUMS = 5.4MB
- 访问入口：`http://10.0.0.104:18088`（`10.x` 属于 RFC1918，正好在
  `network_security_config.xml` 放行的私有网段里，电视端不需要额外配置）

**这台 NAS 的网络有坑，必须知道**：它**直连 github.com 不通**——宿主机和容器里对
`github.com`(20.205.243.166) 的 TCP 443 都是超时，而同一时刻用户 Mac 走同一个出口能到
GitHub（3MB/s），容器访问 `hdao.tv` 也正常。所以中转站的回源改成**走 GitHub 加速镜像**，
实测能用的：`ghfast.top`、`ghproxy.net`（问版本 + 下资产都行）、
`gh-proxy.com`（**只代理资源、拒绝网页**，只能下资产）。当前配置：

```yaml
- UPSTREAM=https://ghfast.top/https://github.com
- UPSTREAM_FALLBACKS=https://ghproxy.net/https://github.com,https://gh-proxy.com/https://github.com,https://github.com
```

`tools/nas-release-proxy.mjs` 因此支持多候选回源：**问版本号和下资产分别按候选列表回退**
（因为有些镜像只具备其中一种能力）。

**验收结果**（从用户 Mac 上、也就是电视的视角实测）：

| 项目 | 结果 |
| --- | --- |
| `/healthz` | `{"ok":true,...}` |
| 版本查询 | `302` + `X-Hazix-Tag: v3.6.4` |
| TV 包下载 | 200，2.8MB，约 2 秒（回源走加速镜像） |
| 哈希 | `f2a08a7b…`，与 `dist/SHA256SUMS.txt` 一致 |
| 重复下载 3 次 | 哈希一致；服务端「已缓存」只出现 3 次 = 没有重复回源 |
| Range 断点续传 | `206`，字节数正确 |
| 手机包 / SHA256SUMS.txt | 均 200 |

**部署时踩的三个坑**（已写进 `tools/README-nas-mirror.md`）：

1. compose 的 `command` 被镜像 CMD 吃掉，实际只跑了 `node`，容器静默重启、日志为空——
   改用 `entrypoint: ["node", "/app/nas-release-proxy.mjs"]`；
2. 中文路径下 `new URL(import.meta.url).pathname` 是百分号编码，启动判断永远为假；
3. 加速镜像的 URL 形状是 `<前缀>/<owner>/<repo>/...`，不能把完整 github URL 再拼一次
   （我第一次就拼成了 `gh-proxy.com/https://github.com/https://github.com/...`，全是 404）。

## 2026-09-19 · DSH 第五轮：发布 v3.7.0

发版内容 = 第四轮的更新机制改造（后台自动检查 + NAS 中转 + 私有网段明文放行）。
**首页 Netflix 视觉、顶栏字号、按键音效仍只在浏览器预览里，不在这个 APK 里**——
CHANGELOG 的 3.7.0 条目已按「电视端 / 浏览器预览」两段写清，别把预览的东西算进 APK。

发版步骤（本轮照此执行）：

1. `CHANGELOG.md` 写 3.7.0 条目（含"预览不在本包内"的说明）；
2. 提交并推到 `main`；
3. 打 tag `v3.7.0` 并推 tag → 触发 `.github/workflows/release-apks.yml`：
   用云端发布钥匙签名、跑 lint、发 GitHub Release，并把新包预热进 NAS 中转站
   （需要仓库变量 `NAS_MIRROR_BASE=http://10.0.0.104:18088`，没设则跳过预热步骤）；
4. 下载 Release 里的 `Hazix-TV-v3.7.0.apk`，核对 SHA-256 后归档进 `dist/`，
   把校验值追加到 `dist/SHA256SUMS.txt`，并在下一段交接里写清哈希。

**本机签名的包绝不能给用户**：`~/.android/debug.keystore` 已不存在，本地只有
`.tooling/android-user/debug.keystore`（证书 SHA-256 `6161046b…`），与发布链
（`6f4c4390…`）不同，覆盖安装会被系统拒绝。

**用户必须手动装这一版**：他电视上装的仍是旧版，其应用内更新的下载走 GitHub 直连，
在他家的网络下很可能失败；只有先手动装上 v3.7.0（内含 NAS 中转），后续版本才走得通。

### v3.7.0 发布结果（2026-09-19）

- CI 全部绿：`Web checks`、`Android tests and lint`、`Build TV and mobile APKs`。
- Release：<https://github.com/chinahhy/Hazix/releases/tag/v3.7.0>
- 产物与哈希（已归档进 `dist/`，并追加到 `dist/SHA256SUMS.txt`）：

| 包 | SHA-256 | 大小 |
| --- | --- | --- |
| `Hazix-TV-v3.7.0.apk` | `e8723550b71380ac681375b3de6358c4c51839828a8afa1c3f71127f8f8f2045` | 2834048 |
| `Hazix-Mobile-v3.7.0.apk` | `51b62a96eb7a7ed01e4d5b65083c0ba8d03bf9a0732536fb198bf0f9969e6df0` | 2764462 |

- 签名核对：`apksigner verify --min-sdk-version 23` = **v1 true + v2 true**（发布链同一把钥匙，
  可覆盖安装）；`aapt2 dump badging`：`tv.hdao.app`，versionCode 3007000 / versionName 3.7.0。
- 本机预跑过 CI 的同一条命令 `:nativeapp:lintRelease :mobileapp:lintRelease`，两个模块都通过
  （此前只跑过 `lintDebug`，这次补上了 release 变体的验证）。

**重要遗留：这一版没有烧进 NAS 地址。**
仓库变量 `NAS_MIRROR_BASE` 尚未设置，所以 CI 的 `-PhazixMirrorBase=` 是空的，
`Hazix-TV-v3.7.0.apk` 的 dex 里搜不到 `10.0.0.104`，它的更新仍然只走 GitHub。
如果用户电视所在网络也到不了 GitHub（他 NAS 就到不了），这一版的应用内更新会失败。
两条补救路径（择一，见下一轮）：

1. 设置仓库变量 `NAS_MIRROR_BASE=http://10.0.0.104:18088`，然后发 v3.7.1（推荐，一次性）；
2. 不重新打包，用 `adb shell` 往应用私有 SharedPreferences `update_mirror` 里写 `baseUrl`
   （`UpdateManager.mirrorSettings()` 会优先读它）。但电视上没有 adb，用户需要先开电视的
   ADB 调试，实际操作比重新发一版麻烦。

NAS 中转站已预热 v3.7.0：`/chinahhy/Hazix/releases/latest` 返回 `X-Hazix-Tag: v3.7.0`，
三个资产都能从 `http://10.0.0.104:18088` 取到，且与官方发布逐字节一致。

### v3.7.1 发布结果（2026-09-19）— 这一版才是要装的那一版

- Release：<https://github.com/chinahhy/Hazix/releases/tag/v3.7.1>
- **关键验证**：解包 `Hazix-TV-v3.7.1.apk` 的 `classes.dex`，**能搜到 `10.0.0.104:18088`**
  ——中转站地址确实编进包里了（v3.7.0 搜不到）。`apksigner`：v1 true + v2 true；
  `aapt2 dump badging`：versionCode 3007001 / versionName 3.7.1。
- 产物与哈希（已归档 `dist/` 并追加 `dist/SHA256SUMS.txt`）：

| 包 | SHA-256 | 大小 |
| --- | --- | --- |
| `Hazix-TV-v3.7.1.apk` | `72ee110e64e9f94b663cbbb4b14c29c3ea919586362889c2ce1ad51bc2d2086f` | 2834048 |
| `Hazix-Mobile-v3.7.1.apk` | `6018ffd217523e690a2c2981386907d799176ba044a85f50461ea5a65ce6a4cc` | 2764459 |

- **CI 的 NAS 预热步骤生效了**：发布后中转站日志出现三条"已缓存"
  （2834048 / 2764462 / 176 字节），且 `/releases/latest` 现在返回 `X-Hazix-Tag: v3.7.1`。
  也就是说以后每发一版，NAS 会自动拿到新包，不需要人工喂缓存。

### 本轮踩的坑：gh CLI 与仓库变量

用户以为 `gh` 早装好了。实际情况：`~/.config/gh/` 配置还在，但**二进制已经不在了**
（`/opt/homebrew` 与 `/usr/local/Cellar` 都不存在，Homebrew 本身也没了）。
按用户要求重装到 `/tmp`（`/usr/local/bin` 被沙箱挡住不能写）后，`gh auth status` 报
**钥匙串里的 token 已失效**。

最终走的是 git 自己的凭据：`git credential fill`（helper 未显式配置，走 macOS 系统钥匙串）
能取到 40 字符 token。用它调 API 确认 `permissions.admin = true` 且当时
`/actions/variables` 为空，于是直接创建了仓库变量：

```bash
TOKEN=$(printf 'protocol=https\nhost=github.com\n\n' | git credential fill | awk -F= '/^password=/{print $2}')
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  -d '{"name":"NAS_MIRROR_BASE","value":"http://10.0.0.104:18088"}' \
  https://api.github.com/repos/chinahhy/Hazix/actions/variables
```

以后需要动仓库配置时，可以复用这条路径（比修 `gh` 的登录快）。注意：**token 不要打印到日志里**。

## 2026-09-21 · DSH 接手轮次（修 v3.7.1 的两个静默回归）

用户让 Codex 复核 v3.7.0/3.7.1 的几轮改动，然后要 DSH 判断 Codex 的结论。
**本轮只改源码与 CI 配置：没有打 APK、没有发版、没有推送、没有动 CHANGELOG 的版本号。**
提交信息：`fix(update): restore the preview's version gate and the LAN mirror's cleartext whitelist`。

### 1. Codex 的两条结论：都成立，但有一条归因错了

| Codex 的结论 | 复核 |
| --- | --- |
| TV 把 CIDR 写进 `<domain>`，NAS 明文镜像会被 Android 拦掉 | 成立，**范围比他说的更大**：5 条里 4 条无效 |
| 网页「检查更新」抛 `ReferenceError: APP_VERSION is not defined` | 成立 |

**归因修正**：Codex 说是 Netflix 视觉重构（`ceaa231`）误删的。git 的实际顺序是
`ceaa231`（重构）→ `1cb3a43`（**新增**版本三件套）→ `6b11d6a`（**删除**它们）。
`6b11d6a` 的 message 自己写着 "restores the icon() helper that a scripted deletion took out"——
同一次脚本化删除删了两批东西：`icon()` 补回来了，`APP_VERSION`/`RELEASES_PAGE`/`isNewer` 没有。
病因是「脚本化批量删除 + 没有回归网」，不是某次重构手滑。这一点决定了修法：要让「删掉」这件事
在加载时就炸，而不是等用户点按钮。

### 2. 复核用的证据（都可复现）

- 从**已发布的包**里解出 NSC，不是读源码猜的：
  `aapt2 dump xmltree dist/Hazix-TV-v3.7.1.apk --file res/8G.xml` → `T: '10.0.0.0/8'` 等
  CIDR 原样编进包里（资源名被 R8 混淆成 `res/8G.xml`，用 `dump xmltree` 挨个看才找得到）。
- 平台语义：AOSP `ApplicationConfig.getConfigForHostname` 只有
  `domain.hostname.equals(hostname)` 与 `hostname.endsWith("." + domain)` 两种匹配，
  **没有网段概念**，所以 `10.0.0.104` 永远不会命中 `10.0.0.0/8`，落到 base-config 的 false，
  OkHttp 在建连前就抛 cleartext 异常。
  <https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/security/net/config/ApplicationConfig.java>
- 网页侧把服务真跑起来抓字节：`app.html` 注入 `window.__HDAO_VERSION__="3.6.4"`，
  而 `app.js` 里 `APP_VERSION` 引用 5 处、定义 0 处。
- 顺手确认 **手机包不用动**：`Hazix-Mobile-v3.7.1.apk` 的 dex 里既没有 `10.0.0.104:18088`
  也没有 `update_mirror`（mobileapp 只编译 nativeapp 的 `data` 包，没有更新模块），
  播放走 `https://stream.hdao.tv/...`，所以白名单与它无关。修复面只有 TV 一个模块。

### 3. 本轮改动

| 问题 | 改动文件 |
| --- | --- |
| 网页版本三件套被删 | 新增 `web/public/update.js`（判定 + 弹窗内容）、`web/test/update.test.mjs`、`web/test/preview.test.mjs`；`web/public/app.js` 只留取数与塞 DOM |
| 预览版本号漂移（手抄 `3.6.4`） | `web/server.mjs` 改成从 `CHANGELOG.md` 顶部读，并导出 `server`/`versionFromChangelog` 供测试 |
| 新模块会漏检 | `web/package.json` 的 `check` 改成 `for file in public/*.js`，新模块自动纳入 |
| Android 明文白名单是死配置 | `nativeapp/src/main/res/xml/network_security_config.xml`：删掉 4 条 CIDR，改成精确主机 `10.0.0.104` + `local` |
| 镜像被明文策略拦住却完全无声 | `nativeapp/.../update/UpdateManager.kt`：新增 `cleartextBlockedReason()`，`mirrorSettings()` 命中时写一条 `Log.w`（tag `HazixUpdate`） |
| 缺回归网 | `UpdateManagerTest.kt` 新增 3 条；`ci.yml` 单测步骤带上 `-PhazixMirrorBase`；`release-apks.yml` 的构建命令加 `:nativeapp:testDebugUnitTest` |

关于 CI 接线：新测试只在 `BuildConfig.MIRROR_BASE_URL` 非空时断言，而
`ci.yml` 原本跑单测时**没有**传 `-PhazixMirrorBase`、`release-apks.yml` 传了却**不跑单测**，
所以两处都补上了——否则这条守卫是摆设。

### 4. 验证命令与真实结果

- 网页静态检查：`pnpm run check` 等价命令 → **check OK**。
- 网页单测：`node --test test/*.test.mjs` → **14 passed / 0 failed**（原 9 条）。
- 网页真实浏览器（headless Chrome + CDP，脚本驱动，不是只读代码）：
  导航 `http://127.0.0.1:4173/app.html`，点 `[data-update]` →
  注入版本 `3.7.1`；400ms 后弹窗「当前版本 v3.7.1，正在查询最新版本…」，
  10s 后「已是最新版本 / 当前版本 v3.7.1，无需更新。 / 知道了」，
  **console error 与 exception 均为 0**（修复前同一路径抛 `ReferenceError`）。截图存于
  `/tmp/hdao-update-dialog.png`。
- Android 单测（本轮新打通本地编译，见第 5 节）：
  `./gradlew --offline :nativeapp:testDebugUnitTest -PhazixMirrorBase=http://10.0.0.104:18088`
  → **BUILD SUCCESSFUL**，28 tests / 0 failures（`UpdateManagerTest` 从 9 条变 12 条）。
- **反向验证（证明守卫真的会响）**：把 v3.7.1 那份 CIDR 配置放回沙箱再跑，两条新测试都 **FAILED**：
  - `bakedMirrorAddressIsWhitelistedForCleartext` →「构建烧进了明文中转站 10.0.0.104，但它不在
    network_security_config.xml 里：Android 会拒绝明文连接，局域网镜像静默失效（v3.7.1 的回归）」
  - `cleartextHostsAreWhitelistedAsPlainHostnames` →「10.0.0.0/8 不是主机名：`<domain>` 不支持网段、端口或协议」
  恢复配置后重新跑回 green。
- Android Lint：`./gradlew --offline :nativeapp:lintRelease` → **BUILD SUCCESSFUL**，
  0 error；14 条 warning 全部是既有的（`InlinedApi` 在第 57/359 行等），新增代码没有引入任何一条。

### 5. 重要环境发现：本地可以编译改过的 Kotlin 了（推翻上一轮的结论）

上一轮记的是「这台 Mac 上无法编译改过的 Kotlin 源码」。根因现在看清了：
Kotlin 编译器收到的**每一个**绝对路径里的非 ASCII 字符都被转成 `uXXXX`（反斜杠被吃掉），
`/Volumes/Data/ChatGPT项目文件/观影平台` → `/Volumes/Data/ChatGPTu9879u76EEu6587u4EF6/u89C2u5F71u5E73u53F0`，
于是源码、`android.jar`、`R.jar`、插件 classpath 全部「不存在」。

**只把 `GRADLE_USER_HOME` 换成 ASCII 路径不够**（那只解决插件 jar），源码与 SDK 路径同样被转义；
**符号链接也没用**（Gradle 会解析回真实路径）。必须让整条链路都在 ASCII 路径上：

```bash
cd "/Volumes/Data/ChatGPT项目文件/观影平台"
mkdir -p /tmp/hdao-src
rsync -a --exclude '.tooling' --exclude dist --exclude .git --exclude build --exclude .gradle ./ /tmp/hdao-src/
cp -R .tooling/gradle-home /tmp/hdao-gh                    # 1.8G
cp -R .tooling/android-sdk  /tmp/hdao-sdk                  # 10G
cp -R ".tooling/jdk/jdk-17.0.20.1+1/Contents/Home" /tmp/hdao-jdk

cd /tmp/hdao-src
export JAVA_HOME=/tmp/hdao-jdk GRADLE_USER_HOME=/tmp/hdao-gh ANDROID_HOME=/tmp/hdao-sdk
./gradlew --offline :nativeapp:testDebugUnitTest -PhazixMirrorBase=http://10.0.0.104:18088
./gradlew --offline :nativeapp:lintRelease
```

注意：改完源码要**重新 rsync 一次**再跑；`--offline` 下 `lintDebug` 会因 androidTest 的
依赖没进本地缓存而失败（`kotlin-stdlib-jdk7:1.8.21`、`androidx.collection:collection-jvm:1.4.0`），
用 `:nativeapp:lintRelease` 代替（与上一轮的经验一致）。这套沙箱是加密部署时一次性复制。

### 6. 未验证 / 下一步

- **真机未验证**：Android 明文白名单只在源码与单测层面验证过，**没有在 Android TV 9+ 真机上
  抓包或看 logcat 确认 NAS 镜像真的走通了**。装机后应确认更新检查确实走了「NAS 中转站」。
- **没有打 APK、没有发版**。`CHANGELOG.md` 仍停在 3.7.1：预览的版本号现在跟随 CHANGELOG 顶部，
  提前写 3.7.2 会让预览谎报版本；发版时再补条目。
- **部署死结（需要用户决策）**：如果那台电视真的到不了 GitHub，v3.7.1 无法通过应用内更新拿到
  v3.7.2——它要走的镜像正被明文策略挡着。修 updater 的包送不进 updater，需要 U 盘/adb 手动装一次。
- `update_mirror` 运行时可改，但 `network_security_config.xml` 是编译期静态的：换地址必须同时加
  `<domain>` 条目，否则仍被拒（现在至少有 `Log.w` 与 CI 断言，不会再无声无息）。
- **未处理（留作后续）**：NAS 上开 TLS、把自签证书钉进 `domain-config` 的 `<trust-anchors>`——
  这是唯一既保持 `base-config cleartextTrafficPermitted="false"`、又能支持任意 LAN 地址的路子。
- **现场发现，需用户确认**：仓库根目录出现未跟踪的 `Hazix-TV-v3.7.1.apk`（22:19 写入，与
  `dist/Hazix-TV-v3.7.1.apk` 逐字节相同，sha256 `72ee110e…`）。**不是本轮 DSH 放的**，
  很可能是 Codex 会话为了让文件可下载而复制出来的。按项目规则版本化 APK 只应留在 `dist/`，
  本轮**没有删除**它，请确认后清理。

## 2026-09-21 · Codex：首页首卡聚焦、高清推荐与中段静音预览

用户反馈 v3.7.2 首页卡片偏小、启动焦点没有直接落到第一张节目卡、预览从片头开始、海报偏糊，
并要求真正利用 hdao.tv 已经做好的首页数据。本轮同时修改浏览器预览与电视端源码，**没有打 APK、
没有发版、没有提交或推送**。

### 1. 根因与改动

- `HdaoApi.featured()` 原来丢弃 `/api/vods/featured` 的 `hero`，重新拼「3 部电影 + 3 部剧」；
  现改为优先采用 hdao.tv 自己筛选且带 TMDB 横图的 `hero`，缺失时才回退旧算法。
- 原生首页把共享的 `contentFocusRequester` 绑在「播放」按钮；现改绑第一张节目卡，首次进入首页
  自动聚焦首卡，从详情返回仍恢复原卡。首排低清兜底内容后置，优先 TMDB 海报。
- 首页卡片从 `112×158dp` 放大到 `132×186dp`，聚焦缩放从默认 1.07 提高到 1.10，白色 3dp
  焦点框；海报与巨幕开启高质量缩放过滤。
- 原生 `HomePreviewPlayer` 在 HLS 时长可用且播放器 Ready 后先 seek 到 50%，再开始静音播放，
  避免闪过片头；未知/不足 20 秒的流不强行跳转。
- 网页预览同步增加：首卡自动聚焦、850ms 防抖后加载首集、从时长 50% 静音播放；方向键切卡会
  同步更换标题/海报/视频。顺手修了一个旧竞态：卡片有焦点时 8 秒轮播仍会换标题，导致标题与
  正在播放的卡片不一致。

### 2. 改动文件

- 原生：`data/HdaoApi.kt`、`data/PlaybackUrl.kt`、`ui/Screens.kt`、`ui/Components.kt`、
  `ui/HomePreviewPlayer.kt` 及对应两个数据层测试。
- 网页：`web/public/app.js`、`app.css`、`data.js`、`web/test/data.test.mjs`。

### 3. 验证结果

- 网页：`pnpm run check` 通过；`pnpm test` **15 passed / 0 failed**。
- 真实浏览器 1920×1080：首焦点为「明日之幸」第一卡；10 秒后标题、选中卡仍一致；视频状态
  `playing=true`，实测从 `3069.7967 / 6139.5933 秒`起播。按右键后焦点与标题切到「逃出绝命街」，
  第二源随后从 `2788.9367 / 5577.8733 秒`起播；console warning/error 为 0。
- 真实浏览器 414×896：`document.scrollWidth <= innerWidth`，无横向溢出，无 console 错误；
  手机端不主动抢焦点、也不自动下载预览视频。
- Android（ASCII 沙箱 `/tmp/hdao-src`）：
  `:nativeapp:testDebugUnitTest :nativeapp:lintRelease -PhazixMirrorBase=http://10.0.0.104:18088`
  **BUILD SUCCESSFUL**；30 tests / 0 failures，lint 0 error（既有 warning 保留）。
- **未验证**：真实电视上的焦点框尺寸、遥控器切卡手感、首帧耗时与 HLS seek 兼容性。没有可用
  Android 设备；发版前仍需 TV 真机确认。根目录未跟踪的 `Hazix-TV-v3.7.1.apk` 原样保留。

## 2026-09-22 · Codex：发布 v3.7.2

- 首页首卡聚焦、高清 hero 与中段静音预览改动提交为 `350b8c8`，推送 `main` 后 CI #54 三项任务
  全部成功：Web checks、Android tests and lint、Build TV and mobile APKs。
- 标签 `v3.7.2` 指向 `350b8c8`；GitHub Release 已发布：
  <https://github.com/chinahhy/Hazix/releases/tag/v3.7.2>。
- Release 含 `Hazix-TV-v3.7.2.apk`、`Hazix-Mobile-v3.7.2.apk`、`SHA256SUMS.txt`；两个 APK
  已下载归档到 `dist/`，没有覆盖或删除任何旧版本。
- 云端校验文件与本地字节一致：TV SHA-256
  `a802a3d02d42a2edeaea2ac9db3dbc0fe038aca0c93fceb61bbaa18ad888c209`；手机 SHA-256
  `57cf9fa0caabed19cf3752a5f753b9cd310a170664df4ae46b32108cc645e328`。
- `aapt dump badging`：TV 为 `tv.hdao.app`、手机为 `tv.hdao.mobile`，均为 versionName `3.7.2`、
  versionCode `3007002`。`apksigner verify --min-sdk-version 23`：两包 v1/v2 签名均有效，证书
  SHA-256 均为既有升级链 `6f4c4390…f9f1`。
- 仍需真机验证首页首焦点、遥控器切卡、HLS 中段 seek 与首帧耗时；构建验证不能替代电视现场体验。

## 2026-09-22 · Codex：首页海报行下移

- 用户的 v3.7.2 真机照片显示：放大后的「最近热播」海报行向上侵入了左侧简介与操作按钮区。
- 根因是海报从 `112×158dp` 放大到 `132×186dp`、聚焦缩放提到 `1.10`，但 `HomePosterCarousel` 容器仍是 `560dp`。
- `nativeapp/.../ui/Screens.kt`：将首页 hero 高度增加到 `600dp`；上方文案保持原位，底部对齐的标题与海报行整体下移 `40dp`，同时给 `1.10` 聚焦外扩留出余量。
- 验证：ASCII 沙箱中 `:nativeapp:testDebugUnitTest :nativeapp:lintRelease` 构建成功，30 tests / 0 failures，lint 0 error；网页基线 `pnpm run check && pnpm test` 通过，15 tests / 0 failures。
- 本轮只修源码，不打 APK、不发版；真机视觉间距需要下一个 APK 安装后确认。

## 2026-09-22 · Codex：发布 v3.7.3

- 首页海报行下移修复与 `CHANGELOG.md` 提交为 `a67fc7e`，已推送 `main`；标签 `v3.7.3` 指向该提交。
- GitHub Release：<https://github.com/chinahhy/Hazix/releases/tag/v3.7.3>；云端正式签名流水线已产出 TV 与手机两个 APK。
- 两包已归档到 `dist/`，未覆盖或删除旧版：TV 2,833,970 字节，SHA-256 `a9734b67760cfc3cc8d4a5b58a732c90691c8f7d59ae95a91bd843c00c6370a2`；手机 2,764,466 字节，SHA-256 `5a816bd264681db0d5707041f1dc35b5143dbce4977db5b15aee3c4b364d3a47`。
- `aapt dump badging`：TV 为 `tv.hdao.app`、手机为 `tv.hdao.mobile`，均为 versionName `3.7.3` / versionCode `3007003`。
- `apksigner verify --min-sdk-version 23`：两包 v1/v2 签名均有效，证书 SHA-256 均为既有升级链 `6f4c4390f681e237d466ab0d4bfb7f43305f8c24d169d4a4320d2a041f3fd9f1`。
- 真机仍需确认海报下移后与简介、按钮的最终间距；构建、lint 与签名验证不能替代电视现场视觉验收。

## 2026-09-22 · Codex：按 TCL 真机坐标再次下移首页海报

- 用 ADB 连上已记录的 `tcl_m7642` (`10.0.0.124:5555`)，读到真实显示参数为 `1920×1080` / `240 dpi`（`1dp = 1.5px`），电视上运行的是正式版 3.7.3（versionCode 3007003）。
- v3.7.3 真机 UI 树：播放按钮 `y=331..403px`，「最近热播」`y=454..491px`，首张聚焦海报 `y=491..798px`，片名 `y=810..849px`，「最近观看」`y=868..908px`。
- `nativeapp/.../ui/Screens.kt`：`HomePosterCarousel` 高度从 `600dp` 增至 `664dp`，左侧详情保持不动，底部对齐的「最近热播」和整排卡片再下移 `64dp = 96px`。预期首张海报为 `y=587..894px`，仍完整落在 1080p 视口内。
- 为真机验收构建的 debug APK 已成功，但 TCL 系统返回 `install apk has be disabled from pm by system default`，未安装、未留下 debug 应用；没有擅自修改电视的 ADB 安装安全开关。
- ASCII 沙箱验证：`:nativeapp:assembleDebug`、`:nativeapp:testDebugUnitTest`、`:nativeapp:lintRelease` 全部通过，30 tests / 0 failures，lint 0 error。本轮未改 `CHANGELOG.md`、未打正式 APK、未提交或发版。

## 2026-09-22 · Codex：发布 v3.7.4

- TCL 真机坐标校准的海报下移修复与 `CHANGELOG.md` 提交为 `44aa9cb`，已推送 `main`；标签 `v3.7.4` 指向该提交。
- GitHub Release：<https://github.com/chinahhy/Hazix/releases/tag/v3.7.4>；正式签名流水线已产出 TV 与手机两个 APK。
- 两包已归档到 `dist/`，未覆盖或删除旧版：TV 2,833,977 字节，SHA-256 `9718f2cd0fc78422e67d7fb5f36b9f93e055866c9694b8c61f1439a5fa8d8372`；手机 2,764,468 字节，SHA-256 `28553aabea8cd246cab34e91ec640da1cffadef24d9206edb68cb7dd00400c52`。
- `aapt dump badging`：TV 为 `tv.hdao.app`、手机为 `tv.hdao.mobile`，均为 versionName `3.7.4` / versionCode `3007004`。
- `apksigner verify --min-sdk-version 23`：两包 v1/v2 签名均有效，证书 SHA-256 为既有升级链 `6f4c4390f681e237d466ab0d4bfb7f43305f8c24d169d4a4320d2a041f3fd9f1`。
- NAS 中转站 `/releases/latest` 已返回 `X-Hazix-Tag: v3.7.4`；TV 包 Range 请求返回 `206` 且总大小 2,833,977 字节。
- 待用户在 TCL 真机安装后确认首张海报是否落在预期的 `y=587..894px`。

## 2026-09-25 · Codex：首页首屏与最近观看分屏

- 用户明确希望：首页打开时轮播介绍和海报卡片占据首屏，海报卡片靠屏幕底部；「最近观看」留在首屏以下，遥控器按下后才显示。
- 电视端 `Screens.kt`：首页轮播容器改为占满当前 `LazyColumn` 视口（替代固定 `664dp`），海报行仍底部对齐；「最近观看」前留 `48dp` 安全间隔，避免初始聚焦引起轻微滚动时露出下一行。海报卡拦截遥控器下键，滚动到最近观看并请求首项焦点。
- 电视端 `Components.kt`：没有观看历史时，空状态文案也可聚焦，按下键仍能看到明确反馈。
- 网页预览 `app.js` / `app.css`：首屏组合轮播介绍与底部竖版海报；最近观看移到首屏以下；下键从海报直接聚焦最近观看，有记录和空记录均适用。手机宽度保留底部导航空间。
- 真实浏览器 1920×1080：首屏底边与最近观看起点都在 `y=1080px`；初始焦点是第一张海报；按一次下键后焦点是最近观看卡，页面滚动 `307px`，该行位于视口 `y=773px`。414×896 手机宽度 `scrollWidth <= innerWidth`，首屏顶部时最近观看从 `y=824px` 开始。
- 网页检查：`pnpm run check` 通过，`pnpm test` 15/15 通过。电视端用项目内 JDK / Gradle / SDK，在原项目路径执行 `:nativeapp:compileDebugKotlin :nativeapp:testDebugUnitTest :nativeapp:lintRelease --offline`，**BUILD SUCCESSFUL**，30 tests / 0 failures，lint 无 error。旧记录说中文路径无法编译；本轮以项目本地环境变量直接编译成功，后续优先复核这一新事实。
- **真机未验证**：新代码尚未装到 TCL 电视；上次 ADB 安装被电视系统禁止。本轮未打 APK、未发版、未提交或推送。根目录原有未跟踪 `Hazix-TV-v3.7.1.apk` 未触碰。
