/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

// AWT MouseWheelEvent units reach Compose as platform ticks, not CSS pixels.
internal actual fun amllComposeWheelDeltaMode(): AmllWheelDeltaMode =
    AmllWheelDeltaMode.LINE
