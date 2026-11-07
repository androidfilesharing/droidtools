package com.droidbubble.app.fold

import com.droidbubble.app.model.IslandSettings

fun IslandSettings.withActivePlacement(role: DisplayRole): IslandSettings {
    val placement = when (role) {
        DisplayRole.INNER -> innerPlacement
        DisplayRole.COVER, DisplayRole.SINGLE -> coverPlacement
    }
    return copy(
        topOffsetDp = placement.topOffsetDp,
        horizontalOffsetDp = placement.horizontalOffsetDp,
        isLeftCutout = placement.isLeftCutout,
        isTeardropCutout = placement.isTeardropCutout,
        lastActiveRole = role
    )
}

fun IslandSettings.withPlacementEdit(role: DisplayRole, next: BubblePlacement): IslandSettings {
    val (cover, inner) = editPlacement(
        cover = coverPlacement,
        inner = innerPlacement,
        rolesSeen = placementRolesSeen,
        editing = role,
        next = next
    )
    val mirror = when (role) {
        DisplayRole.INNER -> inner
        else -> cover
    }
    return copy(
        coverPlacement = cover,
        innerPlacement = inner,
        placementProfilesInitialized = true,
        topOffsetDp = mirror.topOffsetDp,
        horizontalOffsetDp = mirror.horizontalOffsetDp,
        isLeftCutout = mirror.isLeftCutout,
        isTeardropCutout = mirror.isTeardropCutout,
        lastActiveRole = if (role == DisplayRole.SINGLE) lastActiveRole else role
    )
}

fun IslandSettings.observeRole(role: DisplayRole): IslandSettings {
    if (role == DisplayRole.SINGLE || role in placementRolesSeen) return this
    return copy(placementRolesSeen = placementRolesSeen + role)
}
