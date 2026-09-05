/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

// The browser reports CSS pixels; the DOM bridge overrides this with the real deltaMode.
internal actual fun amllComposeWheelDeltaMode(): AmllWheelDeltaMode =
    AmllWheelDeltaMode.PIXEL
