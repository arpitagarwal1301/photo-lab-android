package dev.spikeai.fixtures.shop

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import sergio.sastre.composable.preview.scanner.android.AndroidComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import java.io.File

/**
 * Finds every @Preview in the package (no list to maintain), renders it on the JVM,
 * and runs the commerce rules on its semantics. No device, no emulator, no LLM.
 */
@RunWith(Parameterized::class)
class GuardAllPreviewsTest(private val preview: ComposablePreview<AndroidPreviewInfo>) {

    companion object {
        private val previews by lazy {
            AndroidComposablePreviewScanner()
                .scanPackageTrees("dev.spikeai.fixtures.shop")
                .getPreviews()
        }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun values(): List<ComposablePreview<AndroidPreviewInfo>> = previews
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        renderExtensions = setOf(GuardCaptureExtension(preview.methodName, File("build/guard/screens"))),
    )

    @Test
    fun guard() {
        paparazzi.snapshot(name = preview.methodName) { preview() }
    }
}
