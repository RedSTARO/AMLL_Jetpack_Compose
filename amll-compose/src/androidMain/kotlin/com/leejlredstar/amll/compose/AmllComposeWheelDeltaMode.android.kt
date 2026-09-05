/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

// Compose on Android reports wheel/rotary scroll in platform ticks, not CSS pixels.
internal actual fun amllComposeWheelDeltaMode(): AmllWheelDeltaMode =
    AmllWheelDeltaMode.LINE
