package tv.hdao.app.ui

/** A dp/sp budget, not a conversion of the browser's physical pixel sizes. */
internal data class HomeLayout(
    val firstScreenHeightDp: Float,
    val posterWidthDp: Float,
    val posterHeightDp: Float,
    val titleSizeSp: Float,
    val titleLineHeightSp: Float,
    val descriptionSizeSp: Float,
    val descriptionLineHeightSp: Float,
    val descriptionMaxLines: Int,
    val topInsetDp: Float,
    val copyGapDp: Float,
    val titleMetadataGapDp: Float,
    val metadataDescriptionGapDp: Float,
    val actionsGapDp: Float,
    val minimumCopyHeightDp: Float,
    val shelfOverheadDp: Float,
)

internal fun homeLayout(availableHeightDp: Float, fontScale: Float = 1f): HomeLayout {
    val height = availableHeightDp.takeIf { it.isFinite() && it > 0f } ?: 720f
    val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
    val compact = height < 620f
    val titleSize = if (compact) 32f else 38f
    val titleLine = titleSize + 4f
    val descriptionSize = if (compact) 16f else 17f
    val descriptionLine = if (compact) 22f else 24f
    // The overlay navigation is 60dp tall; copy must never start underneath it.
    val topInset = if (compact) 72f else 82f
    // Heading + one-line card title, padding and 1.10x focus-scale clearance.
    val shelfOverhead = 48f * scale + 47f
    val buttonHeight = maxOf(44f, 22f * scale + 16f)
    var copyGap = 24f
    var titleMetadataGap = 6f
    var metadataDescriptionGap = 8f
    var actionsGap = 14f
    fun copyHeight(descriptionLines: Int): Float =
        (titleLine * 2f + 22f + descriptionLine * descriptionLines) * scale +
            titleMetadataGap + (if (descriptionLines > 0) metadataDescriptionGap else 0f) +
            actionsGap + buttonHeight + 8f
    fun requiredHeight(descriptionLines: Int, posterHeight: Float): Float =
        topInset + copyHeight(descriptionLines) + copyGap + shelfOverhead + posterHeight

    var descriptionLines = 2
    // Protect the standard 132 x 186dp cards first by spending less on whitespace
    // and synopsis. TCL's recorded 1920 x 1080 / 240dpi viewport is about 720dp.
    if (requiredHeight(descriptionLines, 186f) > height) {
        copyGap = 16f
        titleMetadataGap = 4f
        metadataDescriptionGap = 6f
        actionsGap = 10f
        if (requiredHeight(descriptionLines, 186f) > height) descriptionLines = 1
    }
    // At extreme heights/font scales, omit synopsis before making cards tiny.
    if (requiredHeight(descriptionLines, 132f) > height) descriptionLines = 0
    val minimumCopyHeight = copyHeight(descriptionLines)
    val posterHeight = (height - topInset - minimumCopyHeight - copyGap - shelfOverhead)
        .coerceIn(132f, 186f)
    return HomeLayout(
        // If even title + metadata + actions cannot fit, allow scrolling instead
        // of silently clipping actions or distorting posters below the minimum.
        firstScreenHeightDp = maxOf(height, requiredHeight(descriptionLines, posterHeight)),
        posterWidthDp = posterHeight * 132f / 186f,
        posterHeightDp = posterHeight,
        titleSizeSp = titleSize,
        titleLineHeightSp = titleLine,
        descriptionSizeSp = descriptionSize,
        descriptionLineHeightSp = descriptionLine,
        descriptionMaxLines = descriptionLines,
        topInsetDp = topInset,
        copyGapDp = copyGap,
        titleMetadataGapDp = titleMetadataGap,
        metadataDescriptionGapDp = metadataDescriptionGap,
        actionsGapDp = actionsGap,
        minimumCopyHeightDp = minimumCopyHeight,
        shelfOverheadDp = shelfOverhead,
    )
}
