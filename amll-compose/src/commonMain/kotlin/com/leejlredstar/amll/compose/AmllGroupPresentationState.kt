/*
 * Native Compose translation support for @applemusic-like-lyrics/core 0.5.2
 * packages/core/src/lyric-player/dom/lyric-group.ts (renderStyles).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * One lyric group's slice of the engine frame, split by how often each value changes.
 *
 * `LyricLineGroup.renderStyles()` writes transform, opacity and filter straight onto DOM nodes
 * every animation frame; nothing in the browser re-runs layout or script for that. The Compose
 * equivalent is to keep every per-frame spring value out of composition: [frame] and its
 * per-frame projections are only read inside `graphicsLayer`, `drawBehind` and measure blocks,
 * so a spring step invalidates one layer's properties instead of recomposing the group tree.
 *
 * The remaining projections change only when the engine lays out — a line becoming active, its
 * blur/opacity target moving — and are safe to read in composition. `derivedStateOf` with
 * structural equality guarantees a group is not recomposed by a frame whose only difference is
 * `topPx`.
 */
@Stable
internal class AmllGroupPresentationState(
    frameState: State<AmllEngineFrame>,
    val index: Int,
    fallback: AmllGroupFrame,
    hoveredIndex: State<Int?>,
) {
    /** The whole frame. Per-frame: read it only from draw-phase or measure lambdas. */
    val frame: State<AmllGroupFrame> = derivedStateOf {
        frameState.value.groups.getOrNull(index) ?: fallback
    }

    // Layout-time values. Composition may read these.
    val isActive: State<Boolean> = derivedStateOf { frame.value.isActive }
    val opacity: State<Double> = derivedStateOf { frame.value.opacity }
    val blurPx: State<Double> = derivedStateOf { frame.value.blurPx }
    val renderMode: State<AmllLineRenderMode> = derivedStateOf { frame.value.renderMode }
    val animateMaskAlpha: State<Boolean> = derivedStateOf { frame.value.animateMaskAlpha }
    val hovered: State<Boolean> = derivedStateOf { hoveredIndex.value == index }

    // Spring-driven values. Read only from layer blocks, draw blocks, or the mask-alpha loop.
    val mainScale: State<Double> = derivedStateOf { frame.value.mainScalePercent / 100.0 }
    val backgroundScale: State<Double> =
        derivedStateOf { frame.value.backgroundScalePercent / 100.0 }
    val backgroundSlideYPercent: State<Double> =
        derivedStateOf { frame.value.backgroundSlideYPercent }
    val backgroundWrapperScale: State<Double> =
        derivedStateOf { frame.value.backgroundWrapperScale }
}

/**
 * Where the mouse last was over the lyric viewport, and which group that motion landed on.
 *
 * `.lyricLineWrapper:hover` is meant as click feedback: point at a line, it lights up. Compose
 * re-evaluates hover after every layout change, so a stationary cursor also lights whichever
 * line the spring scroll carries under it — on a full-screen player that is the active line
 * nearly all the time, and it reads as a stray box rather than an affordance. Hover here is
 * therefore latched only on real pointer motion and released as soon as the group it landed
 * on moves out from under the still cursor.
 */
internal class AmllHoverTracker {
    var pointerPosition: Offset? = null
        private set
    private val hoveredIndexState = mutableStateOf<Int?>(null)

    /** The group the pointer was last moved onto, or null. Snapshot state. */
    val hoveredIndex: State<Int?> get() = hoveredIndexState

    /**
     * Latches [index] for a pointer that arrived at [position].
     *
     * Compose re-sends the last pointer position as a synthetic Move whenever layout changes
     * beneath a still cursor, and those carry the same [PointerEventType][androidx.compose.ui.input.pointer.PointerEventType]
     * as real motion. The one thing that distinguishes them is the position, so a Move that
     * did not move is ignored: it must not re-latch a group that [reconcile] just released.
     */
    fun onPointerMoved(position: Offset, index: Int?) {
        if (position == pointerPosition) return
        pointerPosition = position
        hoveredIndexState.value = index
    }

    fun onPointerExited() {
        pointerPosition = null
        hoveredIndexState.value = null
    }

    /** Called once per engine frame with the groups' new positions. */
    fun reconcile(
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        groupTopsPx: List<Double>,
        groupHeightsPx: List<Int>,
    ) {
        val index = hoveredIndexState.value ?: return
        val position = pointerPosition ?: run {
            hoveredIndexState.value = null
            return
        }
        val stillUnderPointer = pickAmllTouchClickGroupIndex(
            position = position,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            groupTopsPx = groupTopsPx,
            groupHeightsPx = groupHeightsPx,
        ) == index
        if (!stillUnderPointer) hoveredIndexState.value = null
    }
}

/**
 * Blur effects keyed by quarter-pixel radius.
 *
 * A group's `filter: blur()` transitions over a few hundred milliseconds, and every visible
 * group transitions at once when the active line moves. Building a fresh [BlurEffect] each
 * frame allocated a native image filter per group per frame; quantising to 0.25 px also lets
 * the graphics layer skip re-rendering when consecutive frames round to the same radius.
 */
internal class AmllBlurEffectCache {
    private val effects = HashMap<Int, RenderEffect>()

    fun effectFor(radiusPx: Float): RenderEffect? {
        val quarterPixels = (radiusPx * 4f).roundToInt()
        if (quarterPixels <= 0) return null
        return effects.getOrPut(quarterPixels) {
            val radius = quarterPixels / 4f
            // BlurredEdgeTreatment.Unbounded: no clip, transparent outside the content.
            BlurEffect(radius, radius, TileMode.Decal)
        }
    }
}

internal fun amllFallbackGroupFrame(
    index: Int,
    shouldBackgroundBeFirst: Boolean,
    springEnabled: Boolean,
): AmllGroupFrame = AmllGroupFrame(
    index = index,
    topPx = 0.0,
    backgroundSlideYPercent = if (shouldBackgroundBeFirst) 80.0 else -80.0,
    backgroundWrapperScale = 0.8,
    mainScalePercent = 100.0,
    backgroundScalePercent = 100.0,
    isActive = false,
    opacity = 1.0,
    blurPx = 0.0,
    renderMode = AmllLineRenderMode.SOLID,
    animateMaskAlpha = springEnabled,
)
