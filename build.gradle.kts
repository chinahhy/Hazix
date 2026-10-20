plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
}

// Local and CI builds must produce an APK that can be installed over the latest
// release. The release workflow still passes -PVERSION_NAME/-VERSION_CODE from
// the tag; these values are only the fallback. Reading the fallback from
// CHANGELOG.md keeps it from drifting (it used to be hard-coded to 3.3.9/17,
// which made every main-branch CI artifact a downgrade package).
val changelogVersion = file("CHANGELOG.md")
    .takeIf { it.isFile }
    ?.useLines { lines -> lines.firstOrNull { it.startsWith("## ") } }
    ?.let { Regex("""^##\s+(\d+)\.(\d+)\.(\d+)\b""").find(it) }
    ?: error("CHANGELOG.md 顶部缺少 ## X.Y.Z 版本号；发布构建请显式传 -PVERSION_NAME/-VERSION_CODE")

extra["defaultVersionName"] = changelogVersion.groupValues.slice(1..3).joinToString(".")
extra["defaultVersionCode"] = changelogVersion.groupValues[1].toInt() * 1_000_000 +
    changelogVersion.groupValues[2].toInt() * 1_000 +
    changelogVersion.groupValues[3].toInt()
