import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "com.leejlredstar.amll"
version = "0.1.0"

kotlin {
    applyDefaultHierarchyTemplate()

    // Must stay identical to the consuming project's target set: an included build only
    // substitutes when both sides publish the same Kotlin target variants, and the
    // expect/actual leaves below have one actual per target listed here.
    iosArm64()
    iosSimulatorArm64()

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "com.leejlredstar.amll.compose"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)
            // Album artwork behind the lyric viewport.
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.coroutines.core)
            // TTML lyric parsing.
            implementation(libs.xmlutil.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            // SystemParametersInfo(SPI_GETCLIENTAREAANIMATION) for the Windows reduced-motion read.
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }
        wasmJsMain.dependencies {
            // Intl.Segmenter results cross the Wasm boundary as JSON.
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
