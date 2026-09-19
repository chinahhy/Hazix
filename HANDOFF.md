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
| README 专业化 / 许可 / 隐私 | `README.md`、`README.zh-CN.md`、`CHANGELOG.md`、`LICENSE`、`PRIVACY.md` |

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
  该 MIT 声明已从 `LICENSE` 移到 `THIRD_PARTY_NOTICES.md` 完整保留（MIT 要求必须保留），
  `LICENSE` 只承载本项目自己的专有条款。
  **注意：将来若删除不再使用的 `app/` 模块，这项 MIT 义务才会随之消失。**
- 仓库目前在 GitHub 上是 **public**。用户以为只自己用；若想私有需在仓库设置里改。
- `README.md` 现为英文主版本 + `README.zh-CN.md` 中文镜像，**两份要同步维护**。
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
- `README.md` 与 `README.zh-CN.md` 需同步维护。

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
