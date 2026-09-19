package tv.hdao.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import tv.hdao.app.BuildConfig
import tv.hdao.app.data.API_USER_AGENT
import tv.hdao.app.data.NetworkClients
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

private const val LATEST_RELEASE_URL =
    "https://api.github.com/repos/chinahhy/Hazix/releases/latest"

/**
 * Same release, resolved without the REST API: github.com answers with a redirect
 * to the newest tag, and that path is not subject to the per-IP API quota.
 */
private const val LATEST_RELEASE_PAGE_URL =
    "https://github.com/chinahhy/Hazix/releases/latest"
private const val RELEASE_TAG_MARKER = "/releases/tag/"
private const val RELEASE_DOWNLOAD_PREFIX = "/chinahhy/Hazix/releases/download/"
private const val APK_NAME_PREFIX = "Hazix-TV-v"
private const val APK_NAME_SUFFIX = ".apk"
private const val CHECKSUM_FILE_NAME = "SHA256SUMS.txt"
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val MAX_APK_BYTES = 100L * 1024L * 1024L
private const val MAX_CHECKSUM_BYTES = 256L * 1024L

/**
 * Flags for [PackageManager.getPackageArchiveInfo].
 *
 * Android 9 through 13 only collect the certificates of an archive when the
 * legacy GET_SIGNATURES flag is set — the platform code is literally
 * `if ((flags & GET_SIGNATURES) != 0) collectCertificates(...)` — so a caller
 * that asks for GET_SIGNING_CERTIFICATES alone gets a null
 * `PackageInfo.signingInfo` for a downloaded, not yet installed APK. Android 14
 * finally accepts either flag, and everything below API 28 knows only
 * GET_SIGNATURES, so ask for both and read whichever field the platform filled.
 */
@Suppress("DEPRECATION")
private const val SIGNING_FLAGS =
    PackageManager.GET_SIGNATURES or PackageManager.GET_SIGNING_CERTIFICATES

/**
 * One place a release can be fetched from. [api] is null for a NAS mirror,
 * which serves the GitHub paths but not the REST API.
 */
internal data class ReleaseSource(val label: String, val base: String, val api: String?)

/**
 * The NAS release mirror, when the household has one. It serves exactly the
 * GitHub release paths (see tools/nas-release-proxy.mjs), so only the host
 * changes and every existing URL check keeps working.
 */
data class MirrorSettings(
    val baseUrl: String?,
    val timeoutSeconds: Long,
)

private const val MIRROR_PROBE_INTERVAL_MS = 30L * 60L * 1000L
private const val MIRROR_TIMEOUT_SECONDS = 4L
private const val GITHUB_BASE = "https://github.com"

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

    /**
     * While true the LAN mirror is tried first. Set to false after a check that
     * had to fall through to GitHub (the NAS is off, or the TV is on another
     * network), and re-armed after [MIRROR_PROBE_INTERVAL_MS].
     */
    private var mirrorProbeDue = true
    private var mirrorProbedAt = 0L

    /**
     * The NAS mirror address, when the household has one.
     *
     * Set at build time with `-PhazixMirrorBase=http://192.168.1.10:8088` (baked
     * into [BuildConfig.MIRROR_BASE_URL]) and overridable at runtime by writing
     * the same value into the `update_mirror` preferences, so a changed LAN
     * address does not need a new APK. Nothing is hard-coded: with no address
     * configured the updater talks to GitHub exactly as before.
     */
    fun mirrorSettings(): MirrorSettings? {
        val stored = context.getSharedPreferences("update_mirror", Context.MODE_PRIVATE)
            .getString("baseUrl", null)
        val base = stored?.trim()?.takeIf { it.isNotEmpty() }
            ?: BuildConfig.MIRROR_BASE_URL.trim().takeIf { it.isNotEmpty() }
            ?: return null
        return MirrorSettings(
            baseUrl = normalizeMirrorBase(base) ?: return null,
            timeoutSeconds = MIRROR_TIMEOUT_SECONDS,
        )
    }

    /** The same client, but exposing the redirect that carries the newest tag. */
    private val redirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * The LAN mirror is tried before GitHub: it is one hop away, usually much
     * faster, and works on a TV whose route to GitHub is blocked. A mirror that
     * does not answer within [MirrorSettings.timeoutSeconds] is skipped until
     * [mirrorProbeDue], so an unreachable NAS (the TV is away from home) costs a
     * few seconds once, not on every check.
     */
    suspend fun checkForUpdate(mirror: MirrorSettings? = null): UpdateRelease? =
        withContext(Dispatchers.IO) {
            if (BuildConfig.DEBUG) return@withContext null
            val sources = sourcesFor(mirror)
            var lastError: Throwable? = null
            for (source in sources) {
                try {
                    val release = checkAt(source)
                    mirrorProbeDue = source.base != GITHUB_BASE
                    mirrorProbedAt = System.currentTimeMillis()
                    return@withContext release
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            throw lastError ?: error("检查更新失败")
        }

    private fun checkAt(source: ReleaseSource): UpdateRelease? {
        // A mirror answers the release paths only, so its newest tag comes from
        // the redirect; GitHub also offers the REST API, which carries the
        // release notes and the exact asset size.
        val fromRedirect = runCatching { latestFromRedirect(source) }
        return source.api?.let { api ->
            runCatching { latestFromApi(api, source) }
                .getOrElse { fromRedirect.getOrThrow() }
        } ?: fromRedirect.getOrThrow()
    }

    private fun sourcesFor(mirror: MirrorSettings?): List<ReleaseSource> {
        val github = ReleaseSource("GitHub", GITHUB_BASE, LATEST_RELEASE_URL)
        val base = mirror?.baseUrl?.takeIf { it.isNotBlank() } ?: return listOf(github)
        if (!mirrorProbeDue && System.currentTimeMillis() - mirrorProbedAt < MIRROR_PROBE_INTERVAL_MS) {
            return listOf(github)
        }
        return listOf(
            ReleaseSource("NAS 中转站", base, null),
            github,
        )
    }

    private fun latestFromApi(api: String, source: ReleaseSource): UpdateRelease? {
        val request = okhttp3.Request.Builder()
            .url(api)
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
        if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return null

        val expectedApkName = apkNameFor(version)
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
                CHECKSUM_FILE_NAME -> checksumUrl = asset.optString("browser_download_url")
            }
        }
        require(apkSize in 1..MAX_APK_BYTES) { "更新包大小异常" }
        val resolvedApkUrl = requireTrustedReleaseUrl(apkUrl, source.base)
        val resolvedChecksumUrl = requireTrustedReleaseUrl(checksumUrl, source.base)
        return UpdateRelease(
            version = version,
            tag = tag,
            notes = root.optString("body").trim().take(2_000),
            apkName = expectedApkName,
            apkUrl = resolvedApkUrl,
            checksumUrl = resolvedChecksumUrl,
            apkSize = apkSize,
        )
    }

    /**
     * Reads the newest tag out of the `/releases/latest` redirect. The release
     * notes are unavailable on this path, which is acceptable: the caller shows
     * its generic "a new version is ready" text when the notes are blank.
     */
    private fun latestFromRedirect(source: ReleaseSource): UpdateRelease? {
        val request = okhttp3.Request.Builder()
            .url("${source.base}/chinahhy/Hazix/releases/latest")
            .header("User-Agent", API_USER_AGENT)
            .build()
        val location = redirectClient.newCall(request).execute().use { response ->
            if (!response.isRedirect) error("检查更新失败（${response.code}）")
            response.header("Location")
        }
        val tag = tagFromReleaseLocation(location, source.base)
            ?: error("发布页没有给出最新版本号")
        val version = tag.removePrefix("v")
        if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return null
        return releaseForVersion(
            version = version,
            tag = tag,
            apkSize = remoteApkSize(version, tag, source),
            base = source.base,
        )
    }

    /** Best effort: an unknown size only costs the progress bar and a size check. */
    private fun remoteApkSize(version: String, tag: String, source: ReleaseSource): Long = runCatching {
        val request = okhttp3.Request.Builder()
            .url(releaseDownloadUrl(tag, apkNameFor(version), source.base))
            .head()
            .header("User-Agent", API_USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.contentLength() ?: -1L else -1L
        }
    }.getOrDefault(-1L)

    suspend fun downloadAndVerify(
        release: UpdateRelease,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val checksumText = readText(release.checksumUrl)
        val expectedDigest = checksumFor(checksumText, release.apkName)
            ?: error("发布页缺少该安装包的 SHA-256")
        val updateDir = updatesDirectory().apply { mkdirs() }
        val destination = File(updateDir, release.apkName)
        val partial = File(updateDir, "${release.apkName}.part")
        partial.delete()
        destination.delete()
        try {
            val actualDigest = download(release.apkUrl, partial, release.apkSize, onProgress)
            require(actualDigest.equals(expectedDigest, ignoreCase = true)) {
                "更新包校验失败，文件可能已损坏"
            }
            if (release.apkSize > 0L) {
                require(partial.length() == release.apkSize) { "更新包大小校验失败" }
            }
            require(partial.renameTo(destination)) { "无法保存更新包" }
            verifyPackage(destination)
            destination
        } catch (error: Exception) {
            partial.delete()
            destination.delete()
            throw error
        }
    }

    /**
     * An update that an earlier session already downloaded and verified.
     *
     * [downloadAndVerify] renames a file into place only after its SHA-256 matched
     * the published `SHA256SUMS.txt`, so a matching file under this name is a
     * verified release APK. The app used to keep that knowledge only in memory:
     * after a restart, a "later" tap or an interrupted install the package stayed
     * in the cache with no way to install it, and the next check downloaded it
     * again.
     */
    suspend fun cachedUpdate(): Pair<UpdateRelease, File>? = withContext(Dispatchers.IO) {
        val files = updatesDirectory().listFiles().orEmpty()
        val candidates = files
            .filter {
                it.isFile &&
                    it.name.startsWith(APK_NAME_PREFIX) &&
                    it.name.endsWith(APK_NAME_SUFFIX)
            }
            .mapNotNull { file ->
                val version = file.name
                    .removePrefix(APK_NAME_PREFIX)
                    .removeSuffix(APK_NAME_SUFFIX)
                if (isNewerVersion(version, BuildConfig.VERSION_NAME)) version to file else null
            }
        // Everything else is older than what is installed, half-downloaded or was
        // never a release package: drop it so the cache cannot grow forever.
        val kept = candidates.mapTo(mutableSetOf()) { it.second }
        files.filter { it !in kept }.forEach { it.delete() }

        val newest = candidates.maxWithOrNull(
            Comparator { left, right ->
                when {
                    isNewerVersion(left.first, right.first) -> 1
                    isNewerVersion(right.first, left.first) -> -1
                    else -> 0
                }
            },
        ) ?: return@withContext null

        val usable = runCatching { verifyPackage(newest.second) }.isSuccess
        if (!usable) {
            newest.second.delete()
            return@withContext null
        }
        releaseForVersion(
            version = newest.first,
            apkSize = newest.second.length(),
        ) to newest.second
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

    private fun updatesDirectory(): File = File(context.cacheDir, "updates")

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
            if (contentLength > 0L && expectedSize > 0L) {
                require(contentLength == expectedSize) { "更新包大小异常" }
            }
            // The size is only known up front on the API path; the redirect path
            // usually learns it from the response instead.
            val totalBytes = if (expectedSize > 0L) expectedSize else contentLength
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
                        val progress = if (totalBytes > 0L) {
                            ((copied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
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
        // A platform that declines to hand out an archive's signer certificates
        // must not make updates impossible: the package already matched the
        // published SHA-256, and the system installer refuses an APK signed with a
        // different key anyway. Compare when the platform answers — Android 9 to
        // 13 need SIGNING_FLAGS for that — and stay quiet when it does not.
        if (archiveSigners.isNotEmpty() && installedSigners.isNotEmpty()) {
            require(archiveSigners == installedSigners) { "更新包签名不匹配" }
        }
    }
}

internal fun apkNameFor(version: String): String = "$APK_NAME_PREFIX$version$APK_NAME_SUFFIX"

/**
 * Accepts `192.168.1.10:8088`, `nas.local:8088` or a full URL, and returns the
 * scheme-qualified base. Returns null for anything that is not a plain host
 * (no path, no query), so a mistyped address degrades to "no mirror" instead of
 * producing broken download URLs.
 */
internal fun normalizeMirrorBase(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
    val url = candidate.toHttpUrlOrNull() ?: return null
    if (url.encodedPath != "/") return null
    return candidate
}

internal fun releaseDownloadUrl(
    tag: String,
    apkName: String,
    base: String = GITHUB_BASE,
): String = "${base.trimEnd('/')}/chinahhy/Hazix/releases/download/$tag/$apkName"

/**
 * `https://github.com/chinahhy/Hazix/releases/tag/v3.6.4` → `v3.6.4`.
 *
 * Anything that is not a release-tag URL on github.com yields null, so a captive
 * portal or a proxy error page can never be mistaken for a release.
 */
internal fun tagFromReleaseLocation(location: String?, base: String = GITHUB_BASE): String? {
    val url = location?.toHttpUrlOrNull() ?: return null
    val expected = base.toHttpUrlOrNull() ?: return null
    // github.com always answers over https. A NAS mirror may answer over plain
    // http on the LAN, so cleartext is allowed only for the configured mirror
    // host - never for github.com and never for anything else.
    val trustedScheme = if (url.host == "github.com") "https" else expected.scheme
    if (url.host != expected.host && url.host != "github.com") return null
    if (url.scheme != trustedScheme) return null
    return url.encodedPath
        .substringAfterLast(RELEASE_TAG_MARKER, "")
        .takeIf { tag -> tag.matches(Regex("v\\d+(\\.\\d+)*")) }
}

/**
 * Describes a release from the project's asset naming convention alone. Used when
 * the REST API is unavailable (per-IP rate limit) and when a package that was
 * downloaded in an earlier session is picked up from the cache.
 */
internal fun releaseForVersion(
    version: String,
    tag: String = "v$version",
    notes: String = "",
    apkSize: Long = -1L,
    base: String = GITHUB_BASE,
): UpdateRelease {
    val apkName = apkNameFor(version)
    return UpdateRelease(
        version = version,
        tag = tag,
        notes = notes,
        apkName = apkName,
        apkUrl = requireTrustedReleaseUrl(releaseDownloadUrl(tag, apkName, base), base),
        checksumUrl = requireTrustedReleaseUrl(
            releaseDownloadUrl(tag, CHECKSUM_FILE_NAME, base),
            base,
        ),
        apkSize = apkSize,
    )
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

private fun requireTrustedReleaseUrl(value: String?, base: String = GITHUB_BASE): String {
    val url = value?.toHttpUrlOrNull() ?: error("更新下载地址缺失")
    val expected = base.toHttpUrlOrNull() ?: error("更新源地址不可信")
    require(url.host == expected.host) { "更新下载地址不可信" }
    if (url.host == "github.com") {
        require(url.scheme == "https") { "更新下载地址不可信" }
    } else {
        // A LAN mirror may use http, but only when it is the configured source.
        require(url.scheme == expected.scheme) { "更新下载地址不可信" }
    }
    require(url.encodedPath.startsWith(RELEASE_DOWNLOAD_PREFIX)) { "更新下载地址不属于本项目" }
    return value
}

@Suppress("DEPRECATION")
private fun PackageManager.archivePackageInfo(path: String): PackageInfo? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(SIGNING_FLAGS.toLong()))
    } else {
        getPackageArchiveInfo(path, SIGNING_FLAGS)
    }

@Suppress("DEPRECATION")
private fun PackageManager.installedPackageInfo(packageName: String): PackageInfo =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(SIGNING_FLAGS.toLong()))
    } else {
        getPackageInfo(packageName, SIGNING_FLAGS)
    }

@Suppress("DEPRECATION")
private fun PackageInfo.signerDigests(): Set<String> {
    // Which of the two APIs carries the answer depends on the Android version
    // (see SIGNING_FLAGS) and on how many signers the APK declares, so read both.
    val certificates = mutableListOf<Signature>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.let { info ->
            certificates += info.apkContentsSigners.orEmpty()
            if (!info.hasMultipleSigners()) {
                certificates += info.signingCertificateHistory.orEmpty()
            }
        }
    }
    certificates += signatures.orEmpty()
    return certificates
        .map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        .toSet()
}

@Suppress("DEPRECATION")
private fun PackageInfo.longVersion(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
