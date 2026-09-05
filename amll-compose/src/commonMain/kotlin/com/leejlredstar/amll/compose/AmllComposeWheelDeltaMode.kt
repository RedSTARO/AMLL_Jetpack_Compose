/*
 * Native Compose translation support for @applemusic-like-lyrics/core 0.5.2
 * packages/core/src/lyric-player/base/scroll.ts.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package com.leejlredstar.amll.compose

/**
 * The `WheelEvent.deltaMode` Compose's own scroll delta corresponds to on this target.
 *
 * AMLL branches on the DOM value: PIXEL deltas are added to the scroll offset directly and
 * lay out synchronously, LINE/PAGE deltas are multiplied by 50 and lay out asynchronously.
 * Compose documents `scrollDelta` as platform-specific wheel ticks on Android, iOS and the
 * JVM, so those map to the non-pixel branch. The browser reports true CSS pixels, and
 * [LocalAmllPlatformEventBridge] supplies the real DOM value ahead of CanvasKit anyway —
 * this stays its fallback for an event emitted before viewport bounds exist.
 */
internal expect fun amllComposeWheelDeltaMode(): AmllWheelDeltaMode
