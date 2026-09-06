/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

import android.os.Build

internal actual fun amllSupportsRenderEffectBlur(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
