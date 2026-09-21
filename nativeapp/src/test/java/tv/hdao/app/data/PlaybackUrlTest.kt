package tv.hdao.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackUrlTest {
    @Test
    fun `wraps source URL exactly like website player`() {
        assertEquals(
            "https://stream.hdao.tv/api/proxy/m3u8?url=" +
                "aHR0cHM6Ly92MTQud3N5enltM3U4LmNvbS8yMDI2MDgvMjgvMkwxeXB6OGtWRDI3L3ZpZGVvL2luZGV4Lm0zdTg",
            playbackUrl("https://v14.wsyzym3u8.com/202608/28/2L1ypz8kVD27/video/index.m3u8"),
        )
    }

    @Test
    fun `does not wrap an existing playback proxy twice`() {
        val url = "https://stream.hdao.tv/api/proxy/m3u8?url=YWJj"
        assertEquals(url, playbackUrl(url))
    }

    @Test
    fun `homepage preview starts in the middle of normal programmes`() {
        assertEquals(1_350_000L, previewStartPositionMs(2_700_000L))
        assertEquals(0L, previewStartPositionMs(10_000L))
        assertEquals(0L, previewStartPositionMs(-1L))
    }
}
