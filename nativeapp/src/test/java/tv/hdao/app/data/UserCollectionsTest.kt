package tv.hdao.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserCollectionsTest {
    @Test
    fun `favourites are newest first and one entry per vod`() {
        val entries = listOf(
            favorite(vodId = 10, addedAt = 100, title = "旧标题"),
            favorite(vodId = 20, addedAt = 200),
            favorite(vodId = 10, addedAt = 300, title = "新标题"),
        )

        val ordered = orderedFavorites(entries, limit = 10)

        assertEquals(listOf(10, 20), ordered.map { it.vodId })
        assertEquals("新标题", ordered.first().title)
    }

    @Test
    fun `favourites respect the limit`() {
        val entries = (1..10).map { favorite(vodId = it, addedAt = it.toLong()) }

        assertEquals(listOf(10, 9, 8), orderedFavorites(entries, limit = 3).map { it.vodId })
    }

    @Test
    fun `search terms keep the newest first and drop duplicates`() {
        val terms = updatedSearchTerms(listOf("重器", "谍战"), "重器", limit = 8)

        assertEquals(listOf("重器", "谍战"), terms)
    }

    @Test
    fun `search terms ignore case when de-duplicating`() {
        val terms = updatedSearchTerms(listOf("Hazix", "其他"), "hazix", limit = 8)

        assertEquals(listOf("hazix", "其他"), terms)
    }

    @Test
    fun `search terms trim input, drop blanks and cap the list`() {
        assertEquals(
            listOf("三体", "重器"),
            updatedSearchTerms(listOf("三体", "重器", "繁花"), "  三体  ", limit = 2),
        )
        assertEquals(listOf("三体"), updatedSearchTerms(listOf("三体"), "   ", limit = 8))
        assertEquals(
            listOf("a", "b"),
            cleanSearchTerms(listOf(" a ", "", "b", "a", "  "), limit = 8),
        )
    }

    @Test
    fun `favourite maps to the vod fields a card needs`() {
        val vod = favorite(
            vodId = 42,
            addedAt = 1,
            title = "三体",
            imageUrl = "https://example.invalid/a.jpg",
            year = "2023",
            score = "8.7",
        ).toVod()

        assertEquals(42, vod.vodId)
        assertEquals("三体", vod.title)
        assertEquals("https://example.invalid/a.jpg", vod.coverUrl)
        assertEquals("https://example.invalid/a.jpg", vod.backdropUrl)
        assertEquals("2023", vod.year)
        assertEquals("8.7", vod.score)
        assertNull(vod.description)
    }

    private fun favorite(
        vodId: Int,
        addedAt: Long,
        title: String = "影片 $vodId",
        imageUrl: String? = null,
        year: String? = null,
        score: String? = null,
    ) = FavoriteEntry(
        vodId = vodId,
        title = title,
        imageUrl = imageUrl,
        year = year,
        score = score,
        addedAt = addedAt,
    )
}
