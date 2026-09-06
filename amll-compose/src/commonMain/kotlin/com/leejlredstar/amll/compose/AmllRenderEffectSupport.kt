/*
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

/**
 * Whether `RenderEffect`-based blur is available on this target.
 *
 * Skia backends always have it. Android gained `RenderEffect` in API 31; below that,
 * `Modifier.blur` and a `GraphicsLayer.renderEffect` are silently ignored, so the artwork
 * background falls back to a multi-pass downsample that at least softens the cover.
 */
internal expect fun amllSupportsRenderEffectBlur(): Boolean
