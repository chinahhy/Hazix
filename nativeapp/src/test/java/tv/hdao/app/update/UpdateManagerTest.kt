package tv.hdao.app.update

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.hdao.app.BuildConfig
import java.io.File

class UpdateManagerTest {
    @Test
    fun newerSemanticVersionIsDetected() {
        assertTrue(isNewerVersion("3.3.9", "3.3.8"))
        assertTrue(isNewerVersion("4.0.0", "3.99.99"))
        assertFalse(isNewerVersion("3.3.8", "3.3.8"))
        assertFalse(isNewerVersion("3.3.7", "3.3.8"))
        assertFalse(isNewerVersion("not-a-version", "3.3.8"))
        assertFalse(isNewerVersion("3.999999999999999999999.10", "3.3.8"))
    }

    @Test
    fun debugSuffixDoesNotChangeVersionComparison() {
        assertTrue(isNewerVersion("3.3.9", "3.3.8-debug"))
        assertFalse(isNewerVersion("3.3.8", "3.3.8-debug"))
    }

    @Test
    fun checksumMustMatchExactFileNameAndSha256Shape() {
        val expected = "a".repeat(64)
        val contents = "${"b".repeat(64)}  another.apk\n$expected *Hazix-TV-v3.3.9.apk"

        assertEquals(expected, checksumFor(contents, "Hazix-TV-v3.3.9.apk"))
        assertNull(checksumFor(contents, "Hazix-TV-v3.3.8.apk"))
        assertNull(checksumFor("bad  Hazix-TV-v3.3.9.apk", "Hazix-TV-v3.3.9.apk"))
    }

    @Test
    fun releaseTagIsOnlyReadFromAGithubReleaseTagUrl() {
        assertEquals(
            "v3.6.4",
            tagFromReleaseLocation("https://github.com/chinahhy/Hazix/releases/tag/v3.6.4"),
        )
        assertNull(tagFromReleaseLocation("https://github.com/chinahhy/Hazix/releases"))
        // A proxy or captive portal must never be able to name a release.
        assertNull(tagFromReleaseLocation("https://example.com/releases/tag/v3.6.4"))
        assertNull(tagFromReleaseLocation("https://github.com/chinahhy/Hazix/releases/tag/nightly"))
        assertNull(tagFromReleaseLocation(null))
    }

    @Test
    fun releaseDescriptorFollowsTheProjectsAssetNaming() {
        val release = releaseForVersion("3.6.4")

        assertEquals("3.6.4", release.version)
        assertEquals("v3.6.4", release.tag)
        assertEquals("Hazix-TV-v3.6.4.apk", release.apkName)
        assertEquals(
            "https://github.com/chinahhy/Hazix/releases/download/v3.6.4/Hazix-TV-v3.6.4.apk",
            release.apkUrl,
        )
        assertEquals(
            "https://github.com/chinahhy/Hazix/releases/download/v3.6.4/SHA256SUMS.txt",
            release.checksumUrl,
        )
    }

    @Test
    fun mirrorBaseIsNormalisedAndRejectsAnythingButAPlainHost() {
        // A bare host, with or without a port, becomes an http base.
        assertEquals("http://192.168.1.10:8088", normalizeMirrorBase("192.168.1.10:8088"))
        assertEquals("http://nas.local:8088", normalizeMirrorBase("nas.local:8088"))
        assertEquals("http://192.168.1.10:8088", normalizeMirrorBase("http://192.168.1.10:8088/"))
        assertEquals("https://nas.example.com", normalizeMirrorBase("https://nas.example.com"))
        // Anything with a path or query is a mistake, not a base URL.
        assertNull(normalizeMirrorBase("http://192.168.1.10:8088/chinahhy/Hazix"))
        assertNull(normalizeMirrorBase("not a url"))
        assertNull(normalizeMirrorBase(""))
    }

    @Test
    fun mirrorReleaseKeepsTheMirrorHostAndUsesTheSameAssetPaths() {
        val release = releaseForVersion("3.6.4", base = "http://192.168.1.10:8088")

        assertEquals(
            "http://192.168.1.10:8088/chinahhy/Hazix/releases/download/v3.6.4/Hazix-TV-v3.6.4.apk",
            release.apkUrl,
        )
        assertEquals(
            "http://192.168.1.10:8088/chinahhy/Hazix/releases/download/v3.6.4/SHA256SUMS.txt",
            release.checksumUrl,
        )
    }

    @Test
    fun mirrorRedirectIsAcceptedOnlyForTheConfiguredHost() {
        val mirror = "http://192.168.1.10:8088"
        // The NAS mirror answers the same path GitHub does, and may redirect the
        // client back to github.com for the tag.
        assertEquals(
            "v3.6.4",
            tagFromReleaseLocation("http://192.168.1.10:8088/chinahhy/Hazix/releases/tag/v3.6.4", mirror),
        )
        assertEquals(
            "v3.6.4",
            tagFromReleaseLocation("https://github.com/chinahhy/Hazix/releases/tag/v3.6.4", mirror),
        )
        // A redirect to any other host is still refused, even with a mirror set.
        assertNull(
            tagFromReleaseLocation("http://evil.example.com/chinahhy/Hazix/releases/tag/v9.9.9", mirror),
        )
    }

    @Test
    fun releaseUrlFromAnotherHostIsRefused() {
        // requireTrustedReleaseUrl is private; releaseForVersion is the public
        // path that runs it, so an attacker-controlled base must be rejected by
        // the tag/host checks above. This pins the GitHub default.
        val release = releaseForVersion("3.6.4")
        assertTrue(release.apkUrl.startsWith("https://github.com/"))
        assertEquals("v3.6.4", tagFromReleaseLocation("https://github.com/x/releases/tag/v3.6.4"))
    }

    @Test
    fun cleartextMirrorNamesTheHostThePlatformRefuses() {
        // A blocked mirror has to name its host instead of looking like "the NAS
        // is switched off", which is exactly how v3.7.1 hid its own breakage.
        val blocked = requireNotNull(
            cleartextBlockedReason("http://10.0.0.104:18088") { host -> host != "10.0.0.104" },
        )
        assertTrue(blocked, blocked.contains("10.0.0.104"))
        assertTrue(blocked, blocked.contains("明文"))
        assertNull(cleartextBlockedReason("http://10.0.0.104:18088") { host -> host == "10.0.0.104" })
        // https never goes through the cleartext policy, and neither does an
        // address that is missing or malformed.
        assertNull(cleartextBlockedReason("https://nas.example.com") { false })
        assertNull(cleartextBlockedReason(null) { false })
        assertNull(cleartextBlockedReason("not a url") { false })
    }

    @Test
    fun cleartextHostsAreWhitelistedAsPlainHostnames() {
        val rules = cleartextDomainRules()
        assertTrue("没有解析到任何 <domain>，这条测试本身失效了", rules.isNotEmpty())
        for (rule in rules) {
            // v3.7.1 wrote RFC1918 ranges and the Tailscale range here. Android
            // matches <domain> as a literal hostname, so "10.0.0.0/8" can never
            // match "10.0.0.104": the LAN mirror was refused as cleartext and
            // nothing reported it.
            assertFalse(
                "${rule.hostname} 不是主机名：<domain> 不支持网段、端口或协议",
                rule.hostname.contains('/') || rule.hostname.contains(':') ||
                    rule.hostname.any { it.isWhitespace() },
            )
            assertTrue(
                "${rule.hostname} 不是合法主机名",
                rule.hostname.matches(Regex("[A-Za-z0-9][A-Za-z0-9.-]*")),
            )
        }
    }

    @Test
    fun bakedMirrorAddressIsWhitelistedForCleartext() {
        val base = BuildConfig.MIRROR_BASE_URL.trim()
        // Only a build that actually baked an address has something to check.
        // CI and the release job both pass -PhazixMirrorBase, so this runs for
        // real on every push and on every tag that ships an APK.
        if (base.isEmpty()) return
        val url = requireNotNull(base.toHttpUrlOrNull()) { "MIRROR_BASE_URL 不是合法地址：$base" }
        if (url.isHttps) return
        val (baseCleartext, rules) = cleartextPolicy()
        val permitted = baseCleartext || rules.any { rule ->
            url.host == rule.hostname ||
                (rule.includeSubdomains && url.host.endsWith(".${rule.hostname}"))
        }
        assertTrue(
            "构建烧进了明文中转站 ${url.host}，但它不在 network_security_config.xml 里：" +
                "Android 会拒绝明文连接，局域网镜像静默失效（v3.7.1 的回归）",
            permitted,
        )
    }

    /** A `<domain>` entry the way Android reads it: literal hostname, optional subdomains. */
    private data class DomainRule(val hostname: String, val includeSubdomains: Boolean)

    private fun cleartextDomainRules(): List<DomainRule> = cleartextPolicy().second

    /**
     * Parses whether the base policy allows cleartext, plus the rules of the
     * domain-config block that allows it.
     */
    private fun parseNetworkSecurityConfig(text: String): Pair<Boolean, List<DomainRule>> {
        val base = requireNotNull(
            Regex("""<base-config[^>]*cleartextTrafficPermitted="?(true|false)""").find(text),
        ) { "network_security_config.xml 缺少 base-config" }.groupValues[1]
        val block = requireNotNull(
            Regex(
                """<domain-config[^>]*cleartextTrafficPermitted="true"[^>]*>(.*?)</domain-config>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(text),
        ) { "network_security_config.xml 里没有放行明文的 domain-config" }
        val rules = Regex("""<domain\b([^>]*)>([^<]*)</domain>""")
            .findAll(block.groupValues[1])
            .map { match ->
                DomainRule(
                    hostname = match.groupValues[2].trim(),
                    includeSubdomains = match.groupValues[1].contains("includeSubdomains=\"true\""),
                )
            }
            .toList()
        return (base == "true") to rules
    }

    /**
     * Reads the repository's network_security_config.xml. A unit test may run
     * with the module directory or the repository root as its working directory,
     * so search upwards instead of assuming one of them.
     */
    private fun cleartextPolicy(): Pair<Boolean, List<DomainRule>> {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            for (relative in listOf(
                "src/main/res/xml/network_security_config.xml",
                "nativeapp/src/main/res/xml/network_security_config.xml",
            )) {
                val file = File(dir, relative)
                if (file.isFile) return parseNetworkSecurityConfig(file.readText())
            }
            dir = dir.parentFile
        }
        throw AssertionError("找不到 nativeapp/src/main/res/xml/network_security_config.xml")
    }
}
