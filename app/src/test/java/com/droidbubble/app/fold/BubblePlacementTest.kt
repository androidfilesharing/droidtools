package com.droidbubble.app.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BubblePlacementTest {

    @Test
    fun migrationCopiesLegacyOntoBothRolesOnce() {
        val legacy = BubblePlacement(topOffsetDp = 20, horizontalOffsetDp = -8)
        val (cover, inner) = migratePlacements(
            initialized = false,
            legacy = legacy,
            cover = BubblePlacement(),
            inner = BubblePlacement()
        )
        assertEquals(legacy, cover)
        assertEquals(legacy, inner)
    }

    @Test
    fun initializedProfilesAreNotOverwrittenByLegacy() {
        val (cover, inner) = migratePlacements(
            initialized = true,
            legacy = BubblePlacement(topOffsetDp = 99),
            cover = BubblePlacement(topOffsetDp = 20),
            inner = BubblePlacement(topOffsetDp = 4)
        )
        assertEquals(20, cover.topOffsetDp)
        assertEquals(4, inner.topOffsetDp)
    }

    @Test
    fun offsetsClampToSliderRange() {
        val clamped = BubblePlacement(topOffsetDp = 400, horizontalOffsetDp = -300).clamped()
        assertEquals(120, clamped.topOffsetDp)
        assertEquals(-120, clamped.horizontalOffsetDp)
    }

    @Test
    fun singleSeenRoleWritesBothPlacements() {
        val (cover, inner) = editPlacement(
            cover = BubblePlacement(topOffsetDp = 12),
            inner = BubblePlacement(topOffsetDp = 12),
            rolesSeen = setOf(DisplayRole.COVER),
            editing = DisplayRole.COVER,
            next = BubblePlacement(topOffsetDp = 30, horizontalOffsetDp = 6)
        )
        assertEquals(30, cover.topOffsetDp)
        assertEquals(30, inner.topOffsetDp)
        assertEquals(6, cover.horizontalOffsetDp)
    }

    @Test
    fun bothRolesSeenKeepsTheOtherPlacement() {
        val (cover, inner) = editPlacement(
            cover = BubblePlacement(topOffsetDp = 10),
            inner = BubblePlacement(topOffsetDp = 40),
            rolesSeen = setOf(DisplayRole.COVER, DisplayRole.INNER),
            editing = DisplayRole.COVER,
            next = BubblePlacement(topOffsetDp = 18)
        )
        assertEquals(18, cover.topOffsetDp)
        assertEquals(40, inner.topOffsetDp)
    }

    @Test
    fun singleRoleEditSyncsBoth() {
        val (cover, inner) = editPlacement(
            cover = BubblePlacement(topOffsetDp = 1),
            inner = BubblePlacement(topOffsetDp = 2),
            rolesSeen = setOf(DisplayRole.COVER, DisplayRole.INNER),
            editing = DisplayRole.SINGLE,
            next = BubblePlacement(topOffsetDp = 15)
        )
        assertEquals(15, cover.topOffsetDp)
        assertEquals(15, inner.topOffsetDp)
    }

    @Test
    fun missingCutoutDoesNotMoveTheBubble() {
        assertNull(
            placementFromCutout(
                rects = emptyList(),
                screenWidthPx = 1080,
                pillHeightDp = 36,
                density = 3f,
                current = BubblePlacement()
            )
        )
    }

    @Test
    fun cutoutCenterBecomesTopAndHorizontalOffsets() {
        val aligned = placementFromCutout(
            rects = listOf(CutoutRect(left = 500, top = 30, right = 580, bottom = 90)),
            screenWidthPx = 1080,
            pillHeightDp = 36,
            density = 3f,
            current = BubblePlacement(isTeardropCutout = true)
        )
        val centerXDp = ((500 + 580) / 2f) / 3f
        val screenCenterDp = (1080 / 3f) / 2f
        val expectedHorizontal = kotlin.math.round(centerXDp - screenCenterDp).toInt()
        val centerYDp = ((30 + 90) / 2f) / 3f
        val expectedTop = kotlin.math.round(centerYDp - 18f).toInt()
        assertEquals(expectedHorizontal, aligned!!.horizontalOffsetDp)
        assertEquals(expectedTop, aligned.topOffsetDp)
        assertEquals(true, aligned.isTeardropCutout)
        assertEquals(false, aligned.isLeftCutout)
    }
}
