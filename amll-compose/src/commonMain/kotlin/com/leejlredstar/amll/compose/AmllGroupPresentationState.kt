/*
 * Native Compose translation support for @applemusic-like-lyrics/core 0.5.2
 * packages/core/src/lyric-player/dom/lyric-group.ts (renderStyles).
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

import androidx.compose.runtime.MutableDoubleState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import kotlin.math.roundToInt

/**
 * One lyric group's presentation, held as individual snapshot states.
 *
 * `LyricLineGroup.renderStyles()` writes `transform`, `opacity` and `filter` straight onto DOM
 * nodes every animation frame, and the browser touches only the properties that changed. This
 * is the same contract: the viewport's frame loop calls [update] once per engine frame, and a
 * field is written only when its value differs from the last one, so a spring step invalidates
 * the layer blocks that read [topPx] or a scale and nothing else — no composition is checked,
 * no data class is compared, no derived state is re-evaluated.
 *
 * Deriving these from one whole-frame state was measured to cost more than the recomposition
 * it replaced: every group's derived states had to be re-validated against the new frame each
 * step (`rereadTrackedInstances`, `areEqual` on the frame record) before Compose could decide
 * nothing had changed.
 *
 * Per-frame values ([topPx], the scales, the background slide) are read only from
 * `graphicsLayer` / draw / measure blocks. Layout-time values ([isActive], [opacity],
 * [blurPx], [renderMode], [animateMaskAlpha]) change only when the engine lays out and may be
 * read in composition.
 */
@Stable
internal class AmllGroupPresentationState(
    val index: Int,
    initial: AmllGroupFrame,
) {
    // Spring-driven. Layer / draw / measure reads only.
    val topPx: MutableDoubleState = mutableDoubleStateOf(initial.topPx)
    val mainScale: MutableDoubleState = mutableDoubleStateOf(initial.mainScalePercent / 100.0)
    val backgroundScale: MutableDoubleState =
        mutableDoubleStateOf(initial.backgroundScalePercent / 100.0)
    val backgroundSlideYPercent: MutableDoubleState =
        mutableDoubleStateOf(initial.backgroundSlideYPercent)
    val backgroundWrapperScale: MutableDoubleState =
        mutableDoubleStateOf(initial.backgroundWrapperScale)

    /** Inside the overscan band: drawn at all. The DOM detaches the node outside it. */
    val placed: MutableState<Boolean> = mutableStateOf(true)

    // Layout-time. Composition may read these.
    val isActive: MutableState<Boolean> = mutableStateOf(initial.isActive)
    val opacity: MutableDoubleState = mutableDoubleStateOf(initial.opacity)
    val blurPx: MutableDoubleState = mutableDoubleStateOf(initial.blurPx)
    val renderMode: MutableState<AmllLineRenderMode> = mutableStateOf(initial.renderMode)
    val animateMaskAlpha: MutableState<Boolean> = mutableStateOf(initial.animateMaskAlpha)

    /** Writes only the fields whose value changed. Called from the frame loop. */
    fun update(frame: AmllGroupFrame, placed: Boolean) {
        topPx.setIfChanged(frame.topPx)
        mainScale.setIfChanged(frame.mainScalePercent / 100.0)
        backgroundScale.setIfChanged(frame.backgroundScalePercent / 100.0)
        backgroundSlideYPercent.setIfChanged(frame.backgroundSlideYPercent)
        backgroundWrapperScale.setIfChanged(frame.backgroundWrapperScale)
        if (this.placed.value != placed) this.placed.value = placed
        if (isActive.value != frame.isActive) isActive.value = frame.isActive
        opacity.setIfChanged(frame.opacity)
        blurPx.setIfChanged(frame.blurPx)
        if (renderMode.value != frame.renderMode) renderMode.value = frame.renderMode
        if (animateMaskAlpha.value != frame.animateMaskAlpha) {
            animateMaskAlpha.value = frame.animateMaskAlpha
        }
    }

    private fun MutableDoubleState.setIfChanged(value: Double) {
        if (doubleValue != value) doubleValue = value
    }
}

/**
 * Where the mouse last was over the lyric viewport, and which group that motion landed on.
 *
 * Kept for click-to-seek feedback and pointer bookkeeping. Compose re-evaluates hover after
 * every layout change, so a stationary cursor would otherwise "hover" whichever line the spring
 * scroll carries under it; hover here is latched only on real pointer motion and released as
 * soon as the group it landed on moves out from under the still cursor.
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
     * beneath a still cursor, and those carry the same event type as real motion. The one thing
     * that distinguishes them is the position, so a Move that did not move is ignored: it must
     * not re-latch a group that [reconcile] just released.
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
