package tv.hdao.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tv.hdao.app.BuildConfig
import tv.hdao.app.data.API_USER_AGENT
import tv.hdao.app.data.NetworkClients
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/chinahhy/Hazix/releases/latest"
private const val RELEASE_DOWNLOAD_PREFIX = "/chinahhy/Hazix/releases/download/"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val MAX_APK_BYTES = 100L * 1024L * 1024L
private const val MAX_CHECKSUM_BYTES = 256L * 1024L

data class UpdateRelease(
    val version: String,
    val tag: String,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val checksumUrl: String,
    val apkSize: Long,
)

class UpdateManager(private val context: Context) {
    private val client = NetworkClients.httpClient

    suspend fun checkForUpdate(): UpdateRelease? = withContext(Dispatchers.IO) {
        if (BuildConfig.DEBUG) return@withContext null
        val request = okhttp3.Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", API_USER_AGENT)
            .build()
        val root = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error(
                    when (response.code) {
                        // GitHub limits unauthenticated callers per public IP, so a
                        // household that has been polling the API reaches this.
                        403, 429 -> "GitHub 暂时限制了更新检查，请稍后再试"
                        else -> "检查更新失败（${response.code}）"
                    },
                )
            }
            JSONObject(response.body?.string().orEmpty())
        }
        val tag = root.optString("tag_name")
        val version = tag.removePrefix("v")
        if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return@withContext null

        val expectedApkName = "Hazix-TV-v$version.apk"
        val assets = root.optJSONArray("assets") ?: error("更新包信息不完整")
        var apkUrl: String? = null
        var checksumUrl: String? = null
        var apkSize = -1L
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            when (asset.optString("name")) {
                expectedApkName -> {
                    apkUrl = asset.optString("browser_download_url")
                    apkSize = asset.optLong("size", -1L)
                }
                "SHA256SUMS.txt" -> checksumUrl = asset.optString("browser_download_url")
            }
        }
        require(apkSize in 1..MAX_APK_BYTES) { "更新包大小异常" }
        val resolvedApkUrl = requireTrustedReleaseUrl(apkUrl)
        val resolvedChecksumUrl = requireTrustedReleaseUrl(checksumUrl)
        UpdateRelease(
            version = version,
            tag = tag,
            notes = root.optString("body").trim().take(2_000),
            apkName = expectedApkName,
            apkUrl = resolvedApkUrl,
            checksumUrl = resolvedChecksumUrl,
            apkSize = apkSize,
        )
    }

    suspend fun downloadAndVerify(
        release: UpdateRelease,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val checksumText = readText(release.checksumUrl)
        val expectedDigest = checksumFor(checksumText, release.apkName)
            ?: error("发布页缺少该安装包的 SHA-256")
        val updateDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val destination = File(updateDir, release.apkName)
        val partial = File(updateDir, "${release.apkName}.part")
        partial.delete()
        destination.delete()
        try {
            val actualDigest = download(release.apkUrl, partial, release.apkSize, onProgress)
            require(actualDigest.equals(expectedDigest, ignoreCase = true)) {
                "更新包校验失败，文件可能已损坏"
            }
            require(partial.length() == release.apkSize) { "更新包大小校验失败" }
            require(partial.renameTo(destination)) { "无法保存更新包" }
            verifyPackage(destination)
            destination
        } catch (error: Exception) {
            partial.delete()
            destination.delete()
            throw error
        }
    }

    fun needsInstallPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update-provider",
            apk,
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun readText(url: String): String {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", API_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("下载校验文件失败（${response.code}）")
            val body = response.body ?: error("校验文件内容为空")
            val contentLength = body.contentLength()
            if (contentLength > 0L) {
                require(contentLength <= MAX_CHECKSUM_BYTES) { "校验文件过大" }
            }
            body.byteStream().buffered().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= MAX_CHECKSUM_BYTES) { "校验文件过大" }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
        }
    }

    private fun download(
        url: String,
        destination: File,
        expectedSize: Long,
        onProgress: (Int) -> Unit,
    ): String {
        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", API_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("下载安装包失败（${response.code}）")
            val body = response.body ?: error("安装包内容为空")
            val contentLength = body.contentLength()
            if (contentLength > 0L) require(contentLength == expectedSize) { "更新包大小异常" }
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            var lastProgress = -1
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_APK_BYTES) { "更新包超过大小限制" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val progress = ((copied * 100L) / expectedSize).toInt().coerceIn(0, 100)
                        if (progress != lastProgress) {
                            lastProgress = progress
                            onProgress(progress)
                        }
                    }
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun verifyPackage(apk: File) {
        val manager = context.packageManager
        val archive = manager.archivePackageInfo(apk.absolutePath)
            ?: error("Android 无法识别更新包")
        require(archive.packageName == context.packageName) { "更新包的应用 ID 不匹配" }
        val installed = manager.installedPackageInfo(context.packageName)
        require(archive.longVersion() > installed.longVersion()) { "更新包版本没有提高" }
        val archiveSigners = archive.signerDigests()
        val installedSigners = installed.signerDigests()
        require(archiveSigners.isNotEmpty() && installedSigners.isNotEmpty()) { "无法验证更新包签名" }
        require(archiveSigners == installedSigners) { "更新包签名不匹配" }
    }
}

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    val next = numericVersion(candidate) ?: return false
    val installed = numericVersion(current) ?: return false
    val size = maxOf(next.size, installed.size)
    for (index in 0 until size) {
        val comparison = (next.getOrElse(index) { 0 }).compareTo(installed.getOrElse(index) { 0 })
        if (comparison != 0) return comparison > 0
    }
    return false
}

internal fun checksumFor(contents: String, fileName: String): String? = contents
    .lineSequence()
    .map { it.trim() }
    .mapNotNull { line ->
        val parts = line.split(Regex("\\s+"), limit = 2)
        if (parts.size != 2) null else parts[0] to parts[1].removePrefix("*")
    }
    .firstOrNull { (digest, name) ->
        name == fileName && digest.length == 64 && digest.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }
    ?.first

private fun numericVersion(value: String): List<Int>? {
    val normalized = value.trim().removePrefix("v").substringBefore('-')
    if (!normalized.matches(Regex("\\d+(\\.\\d+)*"))) return null
    return normalized.split('.').map { it.toIntOrNull() ?: return null }
}

private fun requireTrustedReleaseUrl(value: String?): String {
    val url = value?.let(Uri::parse) ?: error("更新下载地址缺失")
    require(url.scheme == "https" && url.host == "github.com") { "更新下载地址不可信" }
    require(url.path.orEmpty().startsWith(RELEASE_DOWNLOAD_PREFIX)) { "更新下载地址不属于本项目" }
    return value
}

@Suppress("DEPRECATION")
private fun PackageManager.archivePackageInfo(path: String): PackageInfo? {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageArchiveInfo(path, flags)
    }
}

@Suppress("DEPRECATION")
private fun PackageManager.installedPackageInfo(packageName: String): PackageInfo {
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        PackageManager.GET_SIGNATURES
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
        getPackageInfo(packageName, flags)
    }
}

@Suppress("DEPRECATION")
private fun PackageInfo.signerDigests(): Set<String> {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.apkContentsSigners.orEmpty()
    } else {
        signatures.orEmpty()
    }
    return signatures.map { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }.toSet()
}

@Suppress("DEPRECATION")
private fun PackageInfo.longVersion(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
