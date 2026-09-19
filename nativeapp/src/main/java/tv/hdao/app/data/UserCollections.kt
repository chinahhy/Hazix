package tv.hdao.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * A title the user saved, with just enough detail to draw a card without a
 * network round trip.
 */
data class FavoriteEntry(
    val vodId: Int,
    val title: String,
    val imageUrl: String?,
    val year: String?,
    val score: String?,
    val addedAt: Long,
)

/** Newest first, one entry per vod, capped at [limit]. Pure, unit tested. */
internal fun orderedFavorites(entries: List<FavoriteEntry>, limit: Int): List<FavoriteEntry> =
    entries.sortedByDescending { it.addedAt }.distinctBy { it.vodId }.take(limit)

/** Trims, drops blanks and removes exact duplicates, keeping the given order. Pure, unit tested. */
internal fun cleanSearchTerms(terms: List<String>, limit: Int): List<String> =
    terms.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(limit)

/**
 * Puts [term] at the front, removing any case-insensitive duplicate, and caps the
 * list at [limit]. An empty [term] only cleans the existing list. Pure, unit tested.
 */
internal fun updatedSearchTerms(existing: List<String>, term: String, limit: Int): List<String> {
    val trimmed = term.trim()
    if (trimmed.isEmpty()) return cleanSearchTerms(existing, limit)
    val withoutDuplicate = existing.filterNot { it.trim().equals(trimmed, ignoreCase = true) }
    return cleanSearchTerms(listOf(trimmed) + withoutDuplicate, limit)
}

/**
 * Rebuilds the [Vod] fields a poster card needs, so favourites can reuse the
 * existing rows. Pure, unit tested.
 */
internal fun FavoriteEntry.toVod(): Vod = Vod(
    vodId = vodId,
    title = title,
    coverUrl = imageUrl,
    backdropUrl = imageUrl,
    description = null,
    year = year,
    area = null,
    genres = null,
    remarks = null,
    score = score,
)

/** Locally stored favourites. Nothing leaves the device. */
class Favorites(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun all(limit: Int = 30): List<FavoriteEntry> = orderedFavorites(read(), limit)

    fun isFavorite(vodId: Int): Boolean = read().any { it.vodId == vodId }

    /** Adds or removes [entry]. Returns true when it is a favourite afterwards. */
    fun toggle(entry: FavoriteEntry): Boolean {
        val current = read()
        val exists = current.any { it.vodId == entry.vodId }
        write(if (exists) current.filterNot { it.vodId == entry.vodId } else current + entry)
        return !exists
    }

    private fun read(): List<FavoriteEntry> = runCatching {
        val array = JSONArray(preferences.getString(KEY, null) ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            FavoriteEntry(
                vodId = json.getInt("vodId"),
                title = json.optString("title", "收藏"),
                imageUrl = json.optString("imageUrl").takeIf { it.isNotBlank() && it != "null" },
                year = json.optString("year").takeIf { it.isNotBlank() && it != "null" },
                score = json.optString("score").takeIf { it.isNotBlank() && it != "null" },
                addedAt = json.optLong("addedAt"),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(entries: List<FavoriteEntry>) {
        val array = JSONArray()
        orderedFavorites(entries, MAX_STORED).forEach { entry ->
            array.put(
                JSONObject()
                    .put("vodId", entry.vodId)
                    .put("title", entry.title)
                    .put("imageUrl", entry.imageUrl)
                    .put("year", entry.year)
                    .put("score", entry.score)
                    .put("addedAt", entry.addedAt),
            )
        }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES = "favorites"
        const val KEY = "entries"
        const val MAX_STORED = 100
    }
}

/** Recently searched terms, newest first. Locally stored only. */
class SearchHistory(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun all(limit: Int = 8): List<String> = cleanSearchTerms(read(), limit)

    /** Records [term] and returns the resulting list, newest first. */
    fun record(term: String, limit: Int = 8): List<String> {
        val next = updatedSearchTerms(read(), term, limit)
        write(next)
        return next
    }

    fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private fun read(): List<String> = runCatching {
        val array = JSONArray(preferences.getString(KEY, null) ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    private fun write(terms: List<String>) {
        val array = JSONArray()
        terms.forEach { array.put(it) }
        preferences.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val PREFERENCES = "search_history"
        const val KEY = "terms"
    }
}
