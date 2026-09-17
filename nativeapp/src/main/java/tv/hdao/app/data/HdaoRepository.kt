package tv.hdao.app.data

import android.content.Context
import org.json.JSONObject
import java.util.Locale

class HdaoRepository(private val api: HdaoApi = HdaoApi()) {
    private var featuredCache: FeaturedCatalog? = null
    private val detailCache = mutableMapOf<Int, VodDetail>()

    suspend fun featured(force: Boolean = false): FeaturedCatalog {
        if (!force) featuredCache?.let { return it }
        return api.featured().also { featuredCache = it }
    }

    suspend fun category(category: String, page: Int = 1) = api.category(category, page)
    suspend fun search(query: String) = api.search(query)

    suspend fun detail(vodId: Int, force: Boolean = false): VodDetail {
        if (!force) detailCache[vodId]?.let { return it }
        return api.detail(vodId).also { detailCache[vodId] = it }
    }
}

data class WatchEntry(
    val vodId: Int,
    val episodeId: Long,
    val episodeIndex: Int,
    val title: String,
    val episodeName: String,
    val imageUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

class WatchProgress(context: Context) {
    private val preferences = context.getSharedPreferences("watch_progress", Context.MODE_PRIVATE)

    fun get(vodId: Int, episodeId: Long): Long = preferences.getLong("$vodId:$episodeId", 0L)

    fun save(entry: WatchEntry) {
        val editor = preferences.edit().putLong("${entry.vodId}:${entry.episodeId}", entry.positionMs)
        val entryKey = "entry:${entry.vodId}:${entry.episodeId}"
        if (entry.durationMs > 0L && entry.positionMs >= entry.durationMs - 20_000L) {
            editor.remove("${entry.vodId}:${entry.episodeId}").remove(entryKey).apply()
            return
        }
        val json = JSONObject()
            .put("vodId", entry.vodId)
            .put("episodeId", entry.episodeId)
            .put("episodeIndex", entry.episodeIndex)
            .put("title", entry.title)
            .put("episodeName", entry.episodeName)
            .put("imageUrl", entry.imageUrl)
            .put("positionMs", entry.positionMs)
            .put("durationMs", entry.durationMs)
            .put("updatedAt", entry.updatedAt)
        editor.putString(entryKey, json.toString()).apply()
    }

    fun recent(limit: Int = 8): List<WatchEntry> = dedupeRecent(
        entries = preferences.all
            .filterKeys { it.startsWith("entry:") }
            .values
            .mapNotNull { value ->
                runCatching {
                    val json = JSONObject(value as String)
                    WatchEntry(
                        vodId = json.getInt("vodId"),
                        episodeId = json.getLong("episodeId"),
                        episodeIndex = json.optInt("episodeIndex"),
                        title = json.optString("title", "最近观看"),
                        episodeName = json.optString("episodeName", ""),
                        imageUrl = json.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
                        positionMs = json.optLong("positionMs"),
                        durationMs = json.optLong("durationMs"),
                        updatedAt = json.optLong("updatedAt"),
                    )
                }.getOrNull()
            },
        limit = limit,
    )
}

internal fun dedupeRecent(entries: List<WatchEntry>, limit: Int): List<WatchEntry> {
    val seenVodIds = mutableSetOf<Int>()
    val seenTitles = mutableSetOf<String>()
    return entries
        .sortedByDescending { it.updatedAt }
        .filter { entry ->
            val titleKey = entry.title
                .lowercase(Locale.ROOT)
                .filter { it.isLetterOrDigit() }
                .ifBlank { "vod${entry.vodId}" }
            seenVodIds.add(entry.vodId) && seenTitles.add(titleKey)
        }
        .take(limit)
}
