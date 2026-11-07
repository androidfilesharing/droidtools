package com.droidbubble.app.fold

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayPostureTest {

    @Test
    fun wideTabletWithoutHingeIsSingleNotInner() {
        val roles = assignRoles(
            deviceReportsHinge = false,
            seenFoldingFeature = false,
            displays = listOf(display(id = 0, sw = 800, width = 2560, height = 1600))
        )
        assertEquals(DisplayRole.SINGLE, roles.getValue(0))
    }

    @Test
    fun phoneWithoutHingeIsSingle() {
        val roles = assignRoles(
            deviceReportsHinge = false,
            seenFoldingFeature = false,
            displays = listOf(display(id = 0, sw = 411))
        )
        assertEquals(DisplayRole.SINGLE, roles.getValue(0))
    }

    @Test
    fun foldableNarrowPanelWithoutFoldingFeatureIsCover() {
        val roles = assignRoles(
            deviceReportsHinge = true,
            seenFoldingFeature = false,
            displays = listOf(display(id = 0, sw = 599, folding = false))
        )
        assertEquals(DisplayRole.COVER, roles.getValue(0))
    }

    @Test
    fun foldableWidePanelOnSameDisplayIdIsInner() {
        val roles = assignRoles(
            deviceReportsHinge = true,
            seenFoldingFeature = false,
            displays = listOf(display(id = 0, sw = 673, folding = false))
        )
        assertEquals(DisplayRole.INNER, roles.getValue(0))
    }

    @Test
    fun foldingFeatureForcesInnerAndMarksDeviceFoldable() {
        val displays = listOf(display(id = 1, sw = 400, folding = true))
        assertTrue(deviceIsFoldable(false, false, displays))
        val roles = assignRoles(false, false, displays)
        assertEquals(DisplayRole.INNER, roles.getValue(1))
    }

    @Test
    fun twoWideOnPanelsUsePhysicalWidthToSplitCoverAndInner() {
        val roles = assignRoles(
            deviceReportsHinge = true,
            seenFoldingFeature = true,
            displays = listOf(
                display(id = 1, sw = 700, width = 1080, height = 2400, on = true),
                display(id = 2, sw = 800, width = 2160, height = 1856, on = true)
            )
        )
        assertEquals(DisplayRole.COVER, roles.getValue(1))
        assertEquals(DisplayRole.INNER, roles.getValue(2))
    }

    @Test
    fun pickSettledDisplayPrefersTheLargerLitPanel() {
        assertNull(pickSettledDisplay(emptyList()))
        val narrow = display(id = 1, sw = 400, width = 1080, height = 2400)
        val wide = display(id = 2, sw = 700, width = 2000, height = 1800)
        assertEquals(wide, pickSettledDisplay(listOf(narrow, wide)))
    }

    @Test
    fun sameDisplayIdUpdatesInPlace() {
        assertEquals(
            OverlayAction.UPDATE_IN_PLACE,
            decideOverlayAction(hasView = true, attachedDisplayId = 0, activeDisplayId = 0)
        )
    }

    @Test
    fun displayIdChangeRebindsOnce() {
        assertEquals(
            OverlayAction.REBIND,
            decideOverlayAction(hasView = true, attachedDisplayId = 0, activeDisplayId = 1)
        )
    }

    @Test
    fun noLitDisplayHidesAndDoesNotAdd() {
        assertEquals(
            OverlayAction.HIDE,
            decideOverlayAction(hasView = true, attachedDisplayId = 0, activeDisplayId = null)
        )
        assertEquals(
            OverlayAction.HIDE,
            decideOverlayAction(hasView = false, attachedDisplayId = null, activeDisplayId = null)
        )
    }

    @Test
    fun missingViewRebindsOntoTheActiveDisplay() {
        assertEquals(
            OverlayAction.REBIND,
            decideOverlayAction(hasView = false, attachedDisplayId = null, activeDisplayId = 1)
        )
    }

    private fun display(
        id: Int,
        sw: Int,
        width: Int = 1080,
        height: Int = 2400,
        on: Boolean = true,
        folding: Boolean = false
    ) = DisplaySnapshot(
        displayId = id,
        stateOn = on,
        smallestWidthDp = sw,
        widthPx = width,
        heightPx = height,
        hasFoldingFeature = folding
    )
}
