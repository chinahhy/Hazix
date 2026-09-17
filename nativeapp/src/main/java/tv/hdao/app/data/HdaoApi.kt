package tv.hdao.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class HdaoApi(
    private val baseUrl: String = "https://hdao.tv/api",
    private val client: OkHttpClient = NetworkClients.httpClient,
) {
    suspend fun featured(): FeaturedCatalog = withContext(Dispatchers.IO) {
        val root = request("/vods/featured")
        val definitions = listOf(
            CatalogDefinition("热映电影", "movie", "movies", "电影"),
            CatalogDefinition("热播剧集", "tv", "tv", "剧集"),
            CatalogDefinition("人气动漫", "anime", "anime", "动漫"),
            CatalogDefinition("热门综艺", "variety", "variety", "综艺"),
            CatalogDefinition("精选纪录片", "documentary", "documentary", "纪录片"),
            CatalogDefinition("上头短剧", SHORT_DRAMA_CATEGORY, "shortDrama", "短剧"),
        )
        val rows = definitions.map { definition ->
            CatalogRow(
                definition.title,
                definition.category,
                root.optJSONArray(definition.key).toVods(definition.mediaType),
            )
        }.filter { it.items.isNotEmpty() }
        val movies = rows.firstOrNull { it.category == "movie" }?.items.orEmpty()
        val tv = rows.firstOrNull { it.category == "tv" }?.items.orEmpty()
        val hero = recentHot(movies, tv).ifEmpty {
            root.optJSONArray("hero").toVods().ifEmpty { rows.firstOrNull()?.items.orEmpty() }
        }
        FeaturedCatalog(hero, rows)
    }

    suspend fun category(category: String, page: Int = 1): PagedVods = withContext(Dispatchers.IO) {
        val normalizedCategory = normalizeCategory(category)
        val root = request("/vods?category=${encode(normalizedCategory)}&page=$page&limit=40")
        PagedVods(
            items = filterCategoryVods(normalizedCategory, root.optJSONArray("items").toVods()),
            page = root.optInt("page", page),
            totalPages = root.optInt("totalPages", 1),
        )
    }

    suspend fun search(query: String): List<Vod> = withContext(Dispatchers.IO) {
        val root = request("/search?q=${encode(query)}")
        (root.optJSONArray("items") ?: root.optJSONArray("results")).toVods()
    }

    suspend fun detail(vodId: Int): VodDetail = withContext(Dispatchers.IO) {
        val root = request("/vods/$vodId")
        val item = root.optJSONObject("item")?.toVod()
            ?: error("详情数据缺少影片信息")
        VodDetail(
            item = item,
            episodes = root.optJSONArray("episodes").toEpisodes(),
            related = root.optJSONArray("related").toVods(),
        )
    }

    private fun request(path: String): JSONObject {
        val request = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")
            .header("User-Agent", API_USER_AGENT)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("服务器返回 ${response.code}")
                JSONObject(body)
            }
        } catch (error: IOException) {
            throw IOException("无法连接影视数据源，应用已尝试加密 DNS，请检查网络后重试", error)
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

private data class CatalogDefinition(
    val title: String,
    val category: String,
    val key: String,
    val mediaType: String,
)

internal fun recentHot(movies: List<Vod>, tv: List<Vod>, perType: Int = 3): List<Vod> =
    (movies.take(perType) + tv.take(perType)).distinctBy { it.vodId }

private fun JSONArray?.toVods(mediaType: String? = null): List<Vod> = buildList {
    val array = this@toVods ?: return@buildList
    for (index in 0 until array.length()) {
        array.optJSONObject(index)?.let { add(it.toVod(mediaType)) }
    }
}

private fun JSONObject.toVod(mediaType: String? = null): Vod {
    fun text(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
    val poster = preferredPoster(text("tmdbPoster"), text("coverUrl"))
    val backdrop = preferredBackdrop(text("tmdbBackdrop"))
    return Vod(
        vodId = optInt("vodId", optInt("id")),
        title = text("title") ?: text("vodName") ?: "未命名",
        coverUrl = poster,
        backdropUrl = backdrop,
        description = text("description") ?: text("content"),
        year = text("year"),
        area = text("area"),
        genres = text("genres") ?: text("typeName"),
        remarks = text("remarks"),
        score = text("doubanScore").meaningfulScore() ?: text("tmdbScore").meaningfulScore(),
        mediaType = mediaType,
        typeId = optInt("typeId").takeIf { it > 0 },
        parentTypeId = optInt("parentTypeId").takeIf { it > 0 },
    )
}

internal fun preferredPoster(tmdbPoster: String?, coverUrl: String?): String? =
    tmdbPoster?.tmdbImage("original") ?: coverUrl

internal fun preferredBackdrop(tmdbBackdrop: String?): String? =
    tmdbBackdrop?.tmdbImage("original")

internal fun String?.meaningfulScore(): String? = this
    ?.takeIf { score -> score.toDoubleOrNull()?.let { it > 0.0 && it <= 10.0 } == true }

private fun String.tmdbImage(size: String): String =
    if (startsWith("http")) this else "https://image.tmdb.org/t/p/$size/${trimStart('/')}"

internal const val SHORT_DRAMA_CATEGORY = "short-drama"
private const val SHORT_DRAMA_PARENT_TYPE_ID = 54

internal fun normalizeCategory(category: String): String = when (category) {
    "shortDrama" -> SHORT_DRAMA_CATEGORY
    else -> category
}

internal fun filterCategoryVods(category: String, items: List<Vod>): List<Vod> =
    if (normalizeCategory(category) == SHORT_DRAMA_CATEGORY) {
        items.filter { it.parentTypeId == SHORT_DRAMA_PARENT_TYPE_ID }
    } else {
        items
    }

private fun JSONArray?.toEpisodes(): List<Episode> = buildList {
    val array = this@toEpisodes ?: return@buildList
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val url = item.optString("originalUrl").takeIf { it.startsWith("http") } ?: continue
        add(
            Episode(
                id = item.optLong("id"),
                name = item.optString("name", "第${index + 1}集"),
                originalUrl = url,
                sortOrder = item.optInt("sortOrder", index),
            )
        )
    }
}.sortedBy { it.sortOrder }
