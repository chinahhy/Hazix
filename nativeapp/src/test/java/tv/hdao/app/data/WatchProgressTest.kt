package tv.hdao.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WatchProgressTest {
    @Test
    fun `continue watching keeps only latest episode for each title`() {
        val entries = listOf(
            entry(vodId = 10, episodeId = 101, episodeIndex = 0, updatedAt = 100),
            entry(vodId = 20, episodeId = 201, episodeIndex = 0, updatedAt = 200),
            entry(vodId = 10, episodeId = 103, episodeIndex = 2, updatedAt = 300),
        )

        val recent = dedupeRecent(entries, limit = 8)

        assertEquals(listOf(10, 20), recent.map { it.vodId })
        assertEquals(2, recent.first().episodeIndex)
    }

    @Test
    fun `continue watching also removes duplicate titles with different ids`() {
        val entries = listOf(
            entry(vodId = 10, episodeId = 101, episodeIndex = 0, updatedAt = 100, title = "早春晴朗"),
            entry(vodId = 99, episodeId = 991, episodeIndex = 3, updatedAt = 300, title = "早春 晴朗"),
            entry(vodId = 20, episodeId = 201, episodeIndex = 0, updatedAt = 200, title = "重器"),
        )

        val recent = dedupeRecent(entries, limit = 8)

        assertEquals(listOf(99, 20), recent.map { it.vodId })
    }

    private fun entry(
        vodId: Int,
        episodeId: Long,
        episodeIndex: Int,
        updatedAt: Long,
        title: String = "影片 $vodId",
    ) = WatchEntry(
        vodId = vodId,
        episodeId = episodeId,
        episodeIndex = episodeIndex,
        title = title,
        episodeName = "第 ${episodeIndex + 1} 集",
        imageUrl = null,
        positionMs = 1_000,
        durationMs = 10_000,
        updatedAt = updatedAt,
    )
}
