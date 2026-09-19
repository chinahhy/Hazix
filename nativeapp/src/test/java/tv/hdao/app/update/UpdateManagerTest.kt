package tv.hdao.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
