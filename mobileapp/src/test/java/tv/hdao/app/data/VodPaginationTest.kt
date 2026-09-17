package tv.hdao.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class VodPaginationTest {
    @Test
    fun mergeVodPages_preservesOrderAndRemovesDuplicates() {
        val firstPage = listOf(vod(1), vod(2))
        val secondPage = listOf(vod(2), vod(3))

        assertEquals(listOf(1, 2, 3), mergeVodPages(firstPage, secondPage).map { it.vodId })
    }

    @Test
    fun mergeVodPages_keepsFirstCopyWhenPagesOverlap() {
        val firstCopy = vod(7, "第一页")
        val laterCopy = vod(7, "第二页")

        assertEquals(firstCopy, mergeVodPages(listOf(firstCopy), listOf(laterCopy)).single())
    }

    private fun vod(id: Int, title: String = "影片 $id") = Vod(
        vodId = id,
        title = title,
        coverUrl = null,
        backdropUrl = null,
        description = null,
        year = null,
        area = null,
        genres = null,
        remarks = null,
        score = null,
    )
}
