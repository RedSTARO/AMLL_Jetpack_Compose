/*
 * Frame-clock regression tests for the native AMLL renderer.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.leejlredstar.amll.compose

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmllFrameDeltaTest {

    @Test
    fun animationDeltaPreservesHighRefreshFrameDurations() {
        assertClose(16.666667, amllFrameDeltaMillis(0L, 16_666_667L))
        assertClose(6.944444, amllFrameDeltaMillis(0L, 6_944_444L))
        assertClose(4.166667, amllFrameDeltaMillis(0L, 4_166_667L))
        assertEquals(0.0, amllFrameDeltaMillis(10L, 10L))
        assertEquals(0.0, amllFrameDeltaMillis(10L, 9L))
    }

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.000001,
    ) {
        assertTrue(abs(expected - actual) <= tolerance, "expected=$expected actual=$actual")
    }
}
