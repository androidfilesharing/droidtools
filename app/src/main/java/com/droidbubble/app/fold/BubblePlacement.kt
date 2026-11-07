package com.droidbubble.app.fold

import kotlin.math.roundToInt

data class BubblePlacement(
    val topOffsetDp: Int = 12,
    val horizontalOffsetDp: Int = 0,
    val isLeftCutout: Boolean = false,
    val isTeardropCutout: Boolean = false
)

data class CutoutRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

fun BubblePlacement.clamped(): BubblePlacement = copy(
    topOffsetDp = topOffsetDp.coerceIn(0, 120),
    horizontalOffsetDp = horizontalOffsetDp.coerceIn(-120, 120)
)

fun migratePlacements(
    initialized: Boolean,
    legacy: BubblePlacement,
    cover: BubblePlacement,
    inner: BubblePlacement
): Pair<BubblePlacement, BubblePlacement> {
    if (initialized) return cover.clamped() to inner.clamped()
    val seed = legacy.clamped()
    return seed to seed
}

fun editPlacement(
    cover: BubblePlacement,
    inner: BubblePlacement,
    rolesSeen: Set<DisplayRole>,
    editing: DisplayRole,
    next: BubblePlacement
): Pair<BubblePlacement, BubblePlacement> {
    val clamped = next.clamped()
    val split = DisplayRole.COVER in rolesSeen && DisplayRole.INNER in rolesSeen
    if (!split || editing == DisplayRole.SINGLE) {
        return clamped to clamped
    }
    return if (editing == DisplayRole.COVER) {
        clamped to inner
    } else {
        cover to clamped
    }
}

fun placementFromCutout(
    rects: List<CutoutRect>,
    screenWidthPx: Int,
    pillHeightDp: Int,
    density: Float,
    current: BubblePlacement
): BubblePlacement? {
    if (rects.isEmpty() || density <= 0f || screenWidthPx <= 0) return null
    val rect = rects.minBy { it.top }
    val centerXPx = (rect.left + rect.right) / 2f
    val centerYPx = (rect.top + rect.bottom) / 2f
    val horizontal = (centerXPx / density - (screenWidthPx / density) / 2f).roundToInt()
    val top = (centerYPx / density - pillHeightDp / 2f).roundToInt()
    return current.copy(
        topOffsetDp = top,
        horizontalOffsetDp = horizontal,
        isLeftCutout = centerXPx < screenWidthPx * 0.3f
    ).clamped()
}
