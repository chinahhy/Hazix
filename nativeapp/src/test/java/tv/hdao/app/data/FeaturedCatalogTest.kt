package tv.hdao.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FeaturedCatalogTest {
    @Test
    fun `recent hot contains latest three movies and three tv shows`() {
        val movies = (1..5).map { vod(it, "电影 $it", "电影") }
        val tv = (11..15).map { vod(it, "剧集 $it", "剧集") }

        val result = recentHot(movies, tv)

        assertEquals(listOf(1, 2, 3, 11, 12, 13), result.map { it.vodId })
    }

    @Test
    fun `homepage uses curated hdao hero with backdrops before synthetic fallback`() {
        val curated = listOf(
            vod(101, "无横图", "电影"),
            vod(102, "高清推荐", "剧集").copy(backdropUrl = "https://image.tmdb.org/t/p/original/hero.jpg"),
        )
        val movies = listOf(vod(1, "电影 1", "电影"))
        val tv = listOf(vod(2, "剧集 1", "剧集"))

        assertEquals(listOf(102), homeHero(curated, movies, tv).map { it.vodId })
        assertEquals(listOf(1, 2), homeHero(emptyList(), movies, tv).map { it.vodId })
    }

    @Test
    fun `tmdb images take priority without stretching poster into backdrop`() {
        assertEquals(
            "https://image.tmdb.org/t/p/original/poster.jpg",
            preferredPoster("/poster.jpg", "https://site.example/cover.jpg"),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/original/backdrop.jpg",
            preferredBackdrop("/backdrop.jpg"),
        )
        assertEquals(null, preferredBackdrop(null))
    }

    @Test
    fun `short drama uses server slug instead of falling back to all content`() {
        assertEquals("short-drama", normalizeCategory("shortDrama"))
        assertEquals("short-drama", normalizeCategory("short-drama"))
        assertEquals("tv", normalizeCategory("tv"))
    }

    @Test
    fun `short drama rejects regular tv items when server response is mixed`() {
        val regularTv = vod(1, "普通剧集", "剧集").copy(parentTypeId = 2)
        val shortDrama = vod(2, "短剧节目", "短剧").copy(parentTypeId = 54)

        val result = filterCategoryVods("short-drama", listOf(regularTv, shortDrama))

        assertEquals(listOf(2), result.map { it.vodId })
    }

    @Test
    fun `invalid scores above ten are hidden`() {
        assertEquals("8.7", "8.7".meaningfulScore())
        assertEquals(null, "87.3".meaningfulScore())
        assertEquals(null, "0.0".meaningfulScore())
    }

    private fun vod(id: Int, title: String, mediaType: String) = Vod(
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
        mediaType = mediaType,
    )
}
