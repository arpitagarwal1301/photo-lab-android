package dev.spikeai.spike.paparazzi

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.accessibility.AccessibilityRenderExtension
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Plain render + animated frames (APNG) — no device needed. */
class RenderTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5)

    @Test
    fun checkout() = paparazzi.snapshot { CheckoutScreen() }

    /** 1 s at 10 fps = 10 frames with known timing, for the stability detector. */
    @Test
    fun loadingGif() = paparazzi.gif(name = "loading", start = 0, end = 1000, fps = 10) { LoadingSpinner() }
}

/** TalkBack-order legend: ground truth for what a screen reader (and our pruner) should see. */
class AccessibilityLegendTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        renderExtensions = setOf(AccessibilityRenderExtension()),
    )

    @Test
    fun checkout() = paparazzi.snapshot { CheckoutScreen() }
}

/** The actual question of Spike D: can we get the semantics tree out as data? */
class SemanticsDumpTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        renderExtensions = setOf(SemanticsDumpExtension(File("build/spike-d/checkout"))),
    )

    @Test
    fun checkout() = paparazzi.snapshot { CheckoutScreen() }
}
