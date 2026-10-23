package tv.hdao.mobile.ui

internal val mobileCategories = listOf(
    "movie" to "电影",
    "tv" to "剧集",
    "variety" to "综艺",
    "documentary" to "纪录片",
    "anime" to "动漫",
    "short-drama" to "短剧",
)

internal data class MobileHomeLayout(
    val firstScreenHeightDp: Float,
    val posterWidthDp: Float,
    val compact: Boolean,
    val headerHeightDp: Float,
    val heroCopyHeightDp: Float,
    val shelfHeightDp: Float,
    val copyTopGapDp: Float,
)

// Available height excludes the measured bottom bar and its system inset.
// Budget the actual top safe inset, two title lines, metadata, 48dp actions,
// and the full shelf separately. Short windows scroll instead of overlapping.
internal fun mobileHomeLayout(availableHeightDp: Float, fontScale: Float, topInsetDp: Float): MobileHomeLayout {
    val scale = fontScale.coerceAtLeast(1f)
    val compact = availableHeightDp / scale < 620f
    val posterWidth = (availableHeightDp * 0.14f).coerceIn(80f, 116f)
    val headerHeight = topInsetDp.coerceAtLeast(0f) + (20f * scale).coerceAtLeast(48f) +
        if (compact) 0f else 48f
    val actionHeight = (20f * scale + 20f).coerceAtLeast(48f)
    val heroCopyHeight =
        2f * (if (compact) 29f else 35f) * scale + // Two title lines.
        6f + 20f * scale * (if (compact) 1 else 2) + // Metadata.
        (if (compact) 0f else 8f + 44f * scale) + // Optional two-line synopsis.
        12f + actionHeight + // Action spacing and uncompressed button.
        (if (compact) 24f else 36f) // Vertical copy padding.
    val shelfHeight = (24f * scale).coerceAtLeast(48f) + // Heading / next button.
        posterWidth * 1.5f + 7f + 20f * scale + 14f // Art, title and rail padding.
    val copyTopGap = 16f
    val minimumHeight = headerHeight + heroCopyHeight + shelfHeight + copyTopGap
    return MobileHomeLayout(
        firstScreenHeightDp = availableHeightDp.coerceAtLeast(minimumHeight),
        posterWidthDp = posterWidth,
        compact = compact,
        headerHeightDp = headerHeight,
        heroCopyHeightDp = heroCopyHeight,
        shelfHeightDp = shelfHeight,
        copyTopGapDp = copyTopGap,
    )
}

internal fun mobilePosterColumns(availableWidthDp: Float): Int =
    if (availableWidthDp < 360f) 2 else (availableWidthDp / 120f).toInt().coerceAtLeast(3)
