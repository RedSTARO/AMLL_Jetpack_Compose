/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AmllGroupPresentationStateTest {

    @Test
    fun hoverLatchesOnPointerMotionAndReleasesWhenTheGroupMovesAway() {
        val tracker = AmllHoverTracker()
        val heights = listOf(100, 100, 100)

        tracker.onPointerMoved(position = Offset(50f, 150f), index = 1)
        assertEquals(1, tracker.hoveredIndex.value)

        // The spring scroll lifts every group by 30 px; the pointer is still inside group 1.
        tracker.reconcile(
            viewportWidthPx = 400,
            viewportHeightPx = 600,
            groupTopsPx = listOf(-30.0, 70.0, 170.0),
            groupHeightsPx = heights,
        )
        assertEquals(1, tracker.hoveredIndex.value)

        // Another lift carries group 1 out from under the still cursor: nothing lights up,
        // not even the group that now sits there, until the pointer itself moves.
        tracker.reconcile(
            viewportWidthPx = 400,
            viewportHeightPx = 600,
            groupTopsPx = listOf(-130.0, -30.0, 70.0),
            groupHeightsPx = heights,
        )
        assertNull(tracker.hoveredIndex.value)

        // Only genuine motion — a new position — lights the group now under the cursor.
        tracker.onPointerMoved(position = Offset(50f, 151f), index = 2)
        assertEquals(2, tracker.hoveredIndex.value)
        tracker.onPointerExited()
        assertNull(tracker.hoveredIndex.value)
    }

    @Test
    fun aSyntheticMoveAtTheSamePositionDoesNotRelatch() {
        val tracker = AmllHoverTracker()
        val position = Offset(50f, 150f)
        tracker.onPointerMoved(position = position, index = 1)

        // The list lifts group 1 away and group 2 slides under the still cursor.
        tracker.reconcile(
            viewportWidthPx = 400,
            viewportHeightPx = 600,
            groupTopsPx = listOf(-130.0, -30.0, 70.0),
            groupHeightsPx = listOf(100, 100, 100),
        )
        assertNull(tracker.hoveredIndex.value)

        // Compose's synthetic Move after that relayout reports the unchanged position.
        tracker.onPointerMoved(position = position, index = 2)
        assertNull(tracker.hoveredIndex.value)

        // Real motion, even by one pixel, latches again.
        tracker.onPointerMoved(position = Offset(51f, 150f), index = 2)
        assertEquals(2, tracker.hoveredIndex.value)
    }

    @Test
    fun reconcileWithoutAPointerPositionReleasesTheLatch() {
        val tracker = AmllHoverTracker()
        tracker.onPointerMoved(position = Offset(10f, 10f), index = 0)
        tracker.onPointerExited()
        tracker.reconcile(
            viewportWidthPx = 400,
            viewportHeightPx = 600,
            groupTopsPx = listOf(0.0),
            groupHeightsPx = listOf(100),
        )
        assertNull(tracker.hoveredIndex.value)
    }

    @Test
    fun blurEffectsAreSharedPerQuarterPixel() {
        val cache = AmllBlurEffectCache()
        assertNull(cache.effectFor(0f))
        assertNull(cache.effectFor(0.1f))
        val a = assertNotNull(cache.effectFor(2.0f))
        val b = assertNotNull(cache.effectFor(2.1f))
        val c = assertNotNull(cache.effectFor(2.3f))
        assertSame(a, b)
        assertFalse(a === c)
    }

    @Test
    fun groupsOutsideTheOverscanBandAreNotPlaced() {
        val viewport = 800.0
        val overscan = 300.0
        assertTrue(isAmllGroupWithinOverscan(-350.0, 100.0, viewport, overscan))
        assertFalse(isAmllGroupWithinOverscan(-401.0, 100.0, viewport, overscan))
        assertTrue(isAmllGroupWithinOverscan(1_100.0, 100.0, viewport, overscan))
        assertFalse(isAmllGroupWithinOverscan(1_100.1, 100.0, viewport, overscan))
        assertTrue(isAmllGroupWithinOverscan(0.0, 5_000.0, viewport, overscan))
    }

    @Test
    fun downsamplePassesFollowTheRequestedRadius() {
        assertEquals(0, amllDownsamplePassesFor(0f))
        assertEquals(0, amllDownsamplePassesFor(1f))
        assertEquals(2, amllDownsamplePassesFor(2f))
        assertEquals(4, amllDownsamplePassesFor(6f))
        assertEquals(6, amllDownsamplePassesFor(36f))
        assertEquals(6, amllDownsamplePassesFor(500f))
    }
}
