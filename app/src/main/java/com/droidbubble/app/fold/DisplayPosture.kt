package com.droidbubble.app.fold

enum class DisplayRole { SINGLE, COVER, INNER }

data class DisplaySnapshot(
    val displayId: Int,
    val stateOn: Boolean,
    val smallestWidthDp: Int,
    val widthPx: Int,
    val heightPx: Int,
    val hasFoldingFeature: Boolean
)

enum class OverlayAction { KEEP, UPDATE_IN_PLACE, REBIND, HIDE }

private const val INNER_SMALLEST_WIDTH_DP = 600

fun deviceIsFoldable(
    deviceReportsHinge: Boolean,
    seenFoldingFeature: Boolean,
    displays: List<DisplaySnapshot>
): Boolean {
    return deviceReportsHinge || seenFoldingFeature || displays.any { it.hasFoldingFeature }
}

fun assignRoles(
    deviceReportsHinge: Boolean,
    seenFoldingFeature: Boolean,
    displays: List<DisplaySnapshot>
): Map<Int, DisplayRole> {
    val foldable = deviceIsFoldable(deviceReportsHinge, seenFoldingFeature, displays)
    if (!foldable) {
        return displays.associate { it.displayId to DisplayRole.SINGLE }
    }
    val initial = displays.associate { display ->
        display.displayId to roleForFoldable(display)
    }
    val on = displays.filter { it.stateOn }
    if (on.size < 2) return initial
    val onRoles = on.map { initial.getValue(it.displayId) }.toSet()
    if (onRoles.size != 1) return initial
    val widest = on.maxWith(compareBy<DisplaySnapshot> { it.area() }.thenBy { it.displayId })
    val adjusted = initial.toMutableMap()
    on.forEach { display ->
        adjusted[display.displayId] =
            if (display.displayId == widest.displayId) DisplayRole.INNER else DisplayRole.COVER
    }
    return adjusted
}

fun pickSettledDisplay(onDisplays: List<DisplaySnapshot>): DisplaySnapshot? {
    if (onDisplays.isEmpty()) return null
    if (onDisplays.size == 1) return onDisplays.first()
    return onDisplays.maxWith(compareBy<DisplaySnapshot> { it.area() }.thenBy { it.displayId })
}

fun decideOverlayAction(
    hasView: Boolean,
    attachedDisplayId: Int?,
    activeDisplayId: Int?
): OverlayAction {
    if (activeDisplayId == null) return OverlayAction.HIDE
    if (!hasView || attachedDisplayId == null) return OverlayAction.REBIND
    if (attachedDisplayId != activeDisplayId) return OverlayAction.REBIND
    return OverlayAction.UPDATE_IN_PLACE
}

private fun roleForFoldable(display: DisplaySnapshot): DisplayRole {
    if (display.hasFoldingFeature || display.smallestWidthDp >= INNER_SMALLEST_WIDTH_DP) {
        return DisplayRole.INNER
    }
    return DisplayRole.COVER
}

private fun DisplaySnapshot.area(): Long = widthPx.toLong() * heightPx.toLong()
