# AMLLJetpackCompose

A native [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/)
implementation of the [Apple Music-like Lyrics](https://github.com/amll-dev/applemusic-like-lyrics)
player — no WebView, no bundled JavaScript, no HTML host page.

The lyric engine is a symbol-level Kotlin translation of AMLL core `0.5.2`, lyric
`1.0.2` and ttml `1.0.1`. Every spring, timeline, layout, word-mask, emphasis and
interlude constant is the upstream value; the mapping is recorded per symbol in
[`docs/AMLL_NATIVE_TRANSLATION.md`](docs/AMLL_NATIVE_TRANSLATION.md).

## Targets

`android` · `jvm` (desktop) · `iosArm64` · `iosSimulatorArm64` · `wasmJs` (browser)

One common Compose tree drives all five. The only platform leaves are word/grapheme
segmentation (ICU `BreakIterator`, JVM `BreakIterator`, CoreFoundation tokenizer,
`Intl.Segmenter`), the reduced-motion system setting, and the browser's DOM wheel
`deltaMode` bridge.

## What it renders

- LRC, NetEase YRC and Apple TTML parsing into one AMLL lyric model
- AMLL's six-step lyric optimizer, line grouping and background-vocal handling
- Per-syllable word animation: mask gradients, emphasis float/scale/glow, ruby text,
  per-word romanization, duet alignment
- Spring-driven scrolling, seek, inertia, interlude dots, blur-by-distance
- Album-artwork background with AMLL's blur / saturation / brightness / scrim stack
- Keyboard navigation, focus rings and semantics for every lyric line
- Reduced-motion behavior sourced from the OS setting

## Use it

```kotlin
// settings.gradle.kts
includeBuild("../AMLLJetpackCompose")
```

```kotlin
// build.gradle.kts
implementation("com.leejlredstar.amll:amll-compose")
```

```kotlin
val document = buildAmllLyricDocument(
    lyricMap = lyricMap,
    wordLines = wordLines,
    translatedLrc = translatedLrc,
    romanLrc = romanLrc,
    showTranslated = true,
    showRoman = true,
)

BoxWithConstraints(Modifier.fillMaxSize()) {
    val parameters = calculateAmllLyricVisualParameters(
        viewportWidthDp = maxWidth.value,
        viewportHeightDp = maxHeight.value,
        reducedMotion = rememberReducedMotionEnabled(),
    )

    AmllBackground(
        artworkUri = artworkUri,
        fallbackArtworkUri = null,
        dynamicCoverUrl = null,
        playDynamicCover = false,
        androidPresentation = false,
        reducedMotion = false,
        onArtworkLoaded = {},
        modifier = Modifier.fillMaxSize(),
        // Optional: your own video leaf for AMLL's `#dynamic-bg`.
        dynamicCoverLayer = null,
    )

    AmllLyricViewport(
        document = document,
        mediaId = mediaId,
        sampledPositionMs = positionMs,
        positionState = positionState,
        isPlaying = isPlaying,
        parameters = parameters,
        androidPresentation = false,
        onSeek = { id, timeMs -> player.seekTo(timeMs) },
        onInteraction = {},
        modifier = Modifier.fillMaxSize(),
    )
}
```

On `wasmJs`, install the DOM bridge above your Compose root so AMLL receives the real
`WheelEvent.deltaMode` and page-visibility transitions before CanvasKit consumes them:

```kotlin
CompositionLocalProvider(
    LocalAmllPlatformEventBridge provides WebAmllPlatformEventBridge,
) { App() }
```

## Scope

This repository owns the renderer and the lyric model. Playback transport, now-playing
metadata, artwork fetching, song details and player chrome belong to the host app —
`AmllLyricViewport` takes a document and a position and calls back with a seek.

## Versions

Kotlin, Compose Multiplatform, AGP and the target set are pinned in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) to match the consuming project.
An included build only substitutes when both sides publish the same Kotlin target
variants, so bump both catalogs together.

## Build

```sh
./gradlew :amll-compose:jvmTest
./gradlew :amll-compose:compileAndroidMain
./gradlew :amll-compose:compileKotlinWasmJs
./gradlew :amll-compose:compileKotlinIosSimulatorArm64
```

## License

`AGPL-3.0-only`, inherited from Apple Music-like Lyrics. See [`LICENSE`](LICENSE) and
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
