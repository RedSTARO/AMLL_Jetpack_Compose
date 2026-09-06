/*
 * Copyright (c) 2026 AMLL contributors and RedefineNCM KMP contributors.
 *
 * Native Compose translation/adaptation of Apple Music-like Lyrics and the former
 * RedefineNCM AMLL host.
 *
 * Modified for RedefineNCM KMP on 2026-07-27.
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.Image
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Platform leaf that produces `#dynamic-bg`'s video frames.
 *
 * AMLL's own layers stay in common Compose: the library applies the http(s) gate, the overscan,
 * blur, scale, brightness and scrim, and hands the leaf only the URL that passed, the play
 * request, the reduced-motion preference, and the modifier carrying that treatment. A null
 * layer renders the static artwork alone.
 */
typealias AmllDynamicCoverLayer = @Composable (
    url: String,
    play: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier,
) -> Unit

private const val BackgroundCrossfadeMillis = 400
private val BackgroundCssEase = CubicBezierEasing(0.25f, 0.10f, 0.25f, 1.00f)

/**
 * The cached artwork is rendered at this fraction of the overscan box.
 *
 * `blur(24px)` and `blur(48px)` discard every detail finer than a few dozen pixels, so a
 * quarter-resolution source upscaled bilinearly is indistinguishable from the full-size filter
 * while the one-off Gaussian costs a sixteenth as much and the bitmap holds a sixteenth of the
 * memory.
 */
private const val BackgroundCacheDownsample = 4

/** Multi-pass fallback for targets without `RenderEffect`; each pass halves the bitmap. */
private const val BackgroundCacheMaxDownsamplePasses = 6

internal data class AmllBackgroundVisualSpec(
    val blurRadiusDp: Float,
    val overscanFraction: Double = 0.10,
    val transformScale: Float,
    val saturation: Float,
    val brightness: Float,
    val scrimStartAlpha: Float = 0.62f,
    val scrimEndAlpha: Float = 0.56f,
)

internal fun amllBackgroundVisualSpec(
    androidPresentation: Boolean,
): AmllBackgroundVisualSpec =
    if (androidPresentation) {
        AmllBackgroundVisualSpec(
            blurRadiusDp = 24f,
            transformScale = 1.06f,
            saturation = 1.15f,
            brightness = 0.46f,
        )
    } else {
        AmllBackgroundVisualSpec(
            blurRadiusDp = 48f,
            transformScale = 1.10f,
            saturation = 1.30f,
            brightness = 0.55f,
        )
    }

/**
 * Native equivalent of the shared AMLL page's `#bg`, `#dynamic-bg`, and `#bg-scrim`
 * layers. The static artwork and every visual effect stay in common Compose; only video
 * frame production is delegated to the platform leaf composable.
 *
 * The browser's compositor caches a filtered layer whose content never changes, so CSS
 * `filter: blur(48px)` on a still cover is paid once. Compose applies a `RenderEffect` on every
 * frame the layer is drawn, which made a full-screen Gaussian the largest constant cost of the
 * player on weaker GPUs. The static artwork is therefore blurred once into a bitmap when it
 * loads and drawn as a plain image afterwards; only the video leaf, whose frames change, keeps
 * the live filter.
 */
@Composable
fun AmllBackground(
    artworkUri: String?,
    fallbackArtworkUri: String?,
    dynamicCoverUrl: String?,
    playDynamicCover: Boolean,
    androidPresentation: Boolean,
    reducedMotion: Boolean,
    onArtworkLoaded: (Image) -> Unit,
    modifier: Modifier = Modifier,
    dynamicCoverLayer: AmllDynamicCoverLayer? = null,
) {
    // player.html selects these values by `data-platform="android"`, not by viewport width.
    // Keeping that distinction matters for Android tablets and narrow desktop windows.
    val visualSpec = amllBackgroundVisualSpec(androidPresentation)
    val blurRadius = visualSpec.blurRadiusDp.dp
    // applyViewportSize() makes both background elements 120% of the viewport and centers them
    // at -10%. That is layout overscan, not a transform: CSS blur runs in the enlarged element's
    // own coordinate space and only the later platform transform scales the filtered result.
    val overscanFraction = visualSpec.overscanFraction
    val transformScale = visualSpec.transformScale
    val saturation = visualSpec.saturation
    val brightness = visualSpec.brightness
    var displayedArtworkUri by remember(artworkUri, fallbackArtworkUri) {
        mutableStateOf(artworkUri)
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF0A0A0A)),
    ) {
        AnimatedContent(
            targetState = displayedArtworkUri,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { artworkCrossfade(reducedMotion) },
            label = "AmllArtworkCrossfade",
        ) { currentArtwork ->
            AmllCachedBlurredArtwork(
                model = currentArtwork,
                saturation = saturation,
                blurRadius = blurRadius,
                overscanFraction = overscanFraction,
                transformScale = transformScale,
                onLoaded = { image ->
                    if (currentArtwork == displayedArtworkUri) {
                        onArtworkLoaded(image)
                    }
                },
                onError = {
                    nextAmllArtworkUriAfterFailure(
                        failedUri = currentArtwork,
                        primaryUri = artworkUri,
                        fallbackUri = fallbackArtworkUri,
                    )?.let { fallback ->
                        displayedArtworkUri = fallback
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        dynamicCoverUrl
            ?.takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            ?.let { url ->
                dynamicCoverLayer?.invoke(
                    url,
                    playDynamicCover,
                    reducedMotion,
                    Modifier
                        .amllBackgroundOverscan(
                            overscanFraction = overscanFraction,
                            transformScale = transformScale,
                        )
                        .blur(
                            radius = blurRadius,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        ),
                )
            }

        // A black source-over layer is equivalent to CSS brightness() for both Compose images
        // and the Android TextureView leaf: outputRgb = inputRgb * brightness.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 1f - brightness)),
        )

        // AMLL uses a horizontal 0.62 -> 0.56 black scrim. Keeping this separate from
        // image saturation also makes the fallback deterministic when artwork is absent.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = visualSpec.scrimStartAlpha),
                            Color.Black.copy(alpha = visualSpec.scrimEndAlpha),
                        ),
                    ),
                ),
        )
    }
}

fun nextAmllArtworkUriAfterFailure(
    failedUri: String?,
    primaryUri: String?,
    fallbackUri: String?,
): String? = fallbackUri
    ?.takeIf(String::isNotBlank)
    ?.takeIf { failedUri == primaryUri && it != failedUri }

/**
 * `#bg` for one artwork URI: loads it, then blurs and saturates it once into a bitmap sized to
 * the overscan box, and draws that bitmap on every following frame.
 *
 * If the snapshot cannot be produced on this target the live `AsyncImage` + `Modifier.blur`
 * path is used instead, so a failure costs performance rather than the background.
 */
@Composable
private fun AmllCachedBlurredArtwork(
    model: String?,
    saturation: Float,
    blurRadius: Dp,
    overscanFraction: Double,
    transformScale: Float,
    onLoaded: (Image) -> Unit,
    onError: () -> Unit,
    modifier: Modifier,
) {
    val painter = rememberAsyncImagePainter(model = model)
    val painterState by painter.state.collectAsState()
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    val latestOnError by rememberUpdatedState(onError)
    LaunchedEffect(painterState) {
        when (val state = painterState) {
            is AsyncImagePainter.State.Success -> latestOnLoaded(state.result.image)
            is AsyncImagePainter.State.Error -> latestOnError()
            else -> Unit
        }
    }
    val sourcePainter = (painterState as? AsyncImagePainter.State.Success)?.painter
    val colorFilter = remember(saturation) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    }
    val graphicsContext = LocalGraphicsContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var snapshotUnavailable by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        val overscanWidth = scaledAmllBackgroundDimension(constraints.maxWidth, overscanFraction)
        val overscanHeight = scaledAmllBackgroundDimension(constraints.maxHeight, overscanFraction)
        val blurRadiusPx = with(density) { blurRadius.toPx() }
        var cached by remember(sourcePainter) { mutableStateOf<ImageBitmap?>(null) }

        LaunchedEffect(sourcePainter, overscanWidth, overscanHeight, blurRadiusPx, colorFilter) {
            val source = sourcePainter ?: return@LaunchedEffect
            if (snapshotUnavailable || overscanWidth <= 0 || overscanHeight <= 0) {
                return@LaunchedEffect
            }
            val rendered = runCatching {
                renderAmllBlurredArtwork(
                    graphicsContext = graphicsContext,
                    density = density,
                    layoutDirection = layoutDirection,
                    painter = source,
                    targetSize = IntSize(overscanWidth, overscanHeight),
                    blurRadiusPx = blurRadiusPx,
                    colorFilter = colorFilter,
                )
            }.getOrNull()
            if (rendered != null) {
                cached = rendered
            } else {
                snapshotUnavailable = true
            }
        }

        val overscanModifier = Modifier.amllBackgroundOverscan(
            overscanFraction = overscanFraction,
            transformScale = transformScale,
        )
        when {
            snapshotUnavailable -> AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = overscanModifier.blur(
                    radius = blurRadius,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                ),
            )
            cached != null -> Image(
                bitmap = checkNotNull(cached),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low,
                modifier = overscanModifier,
            )
        }
    }
}

/**
 * Renders [painter] cropped into [targetSize] with the saturation filter and a Gaussian of
 * [blurRadiusPx], at [BackgroundCacheDownsample] resolution.
 *
 * Without `RenderEffect` (Android below 12) the Gaussian is replaced by repeated bilinear
 * halving: each pass averages a 2×2 texel box, so `k` passes approximate a box filter `2^k`
 * source pixels wide, sized to the requested radius.
 */
private suspend fun renderAmllBlurredArtwork(
    graphicsContext: GraphicsContext,
    density: Density,
    layoutDirection: LayoutDirection,
    painter: Painter,
    targetSize: IntSize,
    blurRadiusPx: Float,
    colorFilter: ColorFilter,
): ImageBitmap {
    val gaussian = amllSupportsRenderEffectBlur()
    val downsample = if (gaussian) BackgroundCacheDownsample else 2
    val firstSize = IntSize(
        width = max(1, targetSize.width / downsample),
        height = max(1, targetSize.height / downsample),
    )
    val scaledRadius = blurRadiusPx / downsample
    var bitmap = renderAmllLayer(
        graphicsContext = graphicsContext,
        density = density,
        layoutDirection = layoutDirection,
        size = firstSize,
        // The layer is exactly the overscan box, so clamping only replicates its outermost
        // pixels; there is nothing outside for Decal to fade towards.
        renderEffect = if (gaussian) BlurEffect(scaledRadius, scaledRadius, TileMode.Clamp) else null,
    ) {
        drawAmllCroppedPainter(painter, colorFilter)
    }
    if (!gaussian) {
        var passes = amllDownsamplePassesFor(scaledRadius)
        while (passes > 0 && bitmap.width > 2 && bitmap.height > 2) {
            val previous = bitmap
            val halfSize = IntSize(previous.width / 2, previous.height / 2)
            bitmap = renderAmllLayer(
                graphicsContext = graphicsContext,
                density = density,
                layoutDirection = layoutDirection,
                size = halfSize,
                renderEffect = null,
            ) {
                drawImage(
                    image = previous,
                    dstSize = halfSize,
                    filterQuality = FilterQuality.Low,
                )
            }
            passes -= 1
        }
    }
    return bitmap
}

/** Halving passes whose combined box footprint matches a Gaussian of [radiusPx]. */
internal fun amllDownsamplePassesFor(radiusPx: Float): Int {
    if (radiusPx <= 1f) return 0
    return ceil(log2(radiusPx * 2f)).toInt().coerceIn(1, BackgroundCacheMaxDownsamplePasses)
}

private suspend fun renderAmllLayer(
    graphicsContext: GraphicsContext,
    density: Density,
    layoutDirection: LayoutDirection,
    size: IntSize,
    renderEffect: RenderEffect?,
    block: DrawScope.() -> Unit,
): ImageBitmap {
    val layer = graphicsContext.createGraphicsLayer()
    try {
        layer.record(density, layoutDirection, size, block)
        layer.renderEffect = renderEffect
        return layer.toImageBitmap()
    } finally {
        graphicsContext.releaseGraphicsLayer(layer)
    }
}

/** `object-fit: cover` — what `AsyncImage(contentScale = Crop)` did through its layout. */
private fun DrawScope.drawAmllCroppedPainter(
    painter: Painter,
    colorFilter: ColorFilter,
) {
    val intrinsic = painter.intrinsicSize
    if (intrinsic.isUnspecified || intrinsic.width <= 0f || intrinsic.height <= 0f) {
        with(painter) { draw(size = size, colorFilter = colorFilter) }
        return
    }
    val scale = ContentScale.Crop.computeScaleFactor(intrinsic, size)
    val scaledWidth = intrinsic.width * scale.scaleX
    val scaledHeight = intrinsic.height * scale.scaleY
    val dx = (size.width - scaledWidth) / 2f
    val dy = (size.height - scaledHeight) / 2f
    clipRect {
        translate(left = dx, top = dy) {
            with(painter) {
                draw(size = Size(scaledWidth, scaledHeight), colorFilter = colorFilter)
            }
        }
    }
}

/**
 * The overscan layout and platform transform from `applyViewportSize()`; the caller adds
 * `Modifier.blur` only for content whose frames change.
 */
private fun Modifier.amllBackgroundOverscan(
    overscanFraction: Double,
    transformScale: Float,
): Modifier = fillMaxSize()
    .layout { measurable, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val overscanWidth = scaledAmllBackgroundDimension(width, overscanFraction)
        val overscanHeight = scaledAmllBackgroundDimension(height, overscanFraction)
        val placeable = measurable.measure(
            Constraints.fixed(
                width = overscanWidth,
                height = overscanHeight,
            ),
        )
        layout(width, height) {
            placeable.place(
                x = (width - overscanWidth) / 2,
                y = (height - overscanHeight) / 2,
            )
        }
    }
    .graphicsLayer {
        scaleX = transformScale
        scaleY = transformScale
    }

internal fun scaledAmllBackgroundDimension(
    viewportDimensionPx: Int,
    overscanFraction: Double,
): Int {
    if (viewportDimensionPx <= 0) return 0
    val maximumDimensionPx = Constraints.Infinity - 1
    val maximumOverscanPx =
        ((maximumDimensionPx - viewportDimensionPx).coerceAtLeast(0)) / 2
    val overscanPx = ceil(viewportDimensionPx * overscanFraction)
        .toInt()
        .coerceIn(0, maximumOverscanPx)
    return viewportDimensionPx + overscanPx * 2
}

private fun artworkCrossfade(reducedMotion: Boolean): ContentTransform =
    if (reducedMotion) {
        fadeIn(snap()) togetherWith fadeOut(snap())
    } else {
        fadeIn(tween(BackgroundCrossfadeMillis, easing = BackgroundCssEase)) togetherWith
            fadeOut(tween(BackgroundCrossfadeMillis, easing = BackgroundCssEase))
    }
