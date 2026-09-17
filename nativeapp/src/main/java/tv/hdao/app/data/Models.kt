package tv.hdao.app.data

data class Vod(
    val vodId: Int,
    val title: String,
    val coverUrl: String?,
    val backdropUrl: String?,
    val description: String?,
    val year: String?,
    val area: String?,
    val genres: String?,
    val remarks: String?,
    val score: String?,
    val mediaType: String? = null,
    val typeId: Int? = null,
    val parentTypeId: Int? = null,
)

data class Episode(
    val id: Long,
    val name: String,
    val originalUrl: String,
    val sortOrder: Int,
)

data class VodDetail(
    val item: Vod,
    val episodes: List<Episode>,
    val related: List<Vod>,
)

data class FeaturedCatalog(
    val hero: List<Vod>,
    val rows: List<CatalogRow>,
)

data class CatalogRow(val title: String, val category: String, val items: List<Vod>)

data class PagedVods(
    val items: List<Vod>,
    val page: Int,
    val totalPages: Int,
)

internal fun mergeVodPages(existing: List<Vod>, incoming: List<Vod>): List<Vod> =
    (existing + incoming).distinctBy { it.vodId }
