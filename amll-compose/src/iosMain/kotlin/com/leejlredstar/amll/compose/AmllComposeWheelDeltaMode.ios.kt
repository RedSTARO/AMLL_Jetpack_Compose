/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

// UIKit scroll deltas reach Compose as platform ticks, not CSS pixels.
internal actual fun amllComposeWheelDeltaMode(): AmllWheelDeltaMode =
    AmllWheelDeltaMode.LINE
