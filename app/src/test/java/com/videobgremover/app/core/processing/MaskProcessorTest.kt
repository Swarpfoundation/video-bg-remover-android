package com.videobgremover.app.core.processing

import com.videobgremover.app.domain.model.SegmentationMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskProcessorTest {

    @Test
    fun hysteresisThreshold_reducesNearThresholdFlicker() {
        val processor = MaskProcessor(
            MaskProcessingConfig(
                threshold = 0.5f,
                useTemporalSmoothing = false,
                useHysteresisThreshold = true,
                hysteresisDelta = 0.1f,
                applyNeighborhoodConsensusFilter = false,
                applyMorphology = false,
                removeSpeckles = false,
                applyFeather = false
            )
        )

        val frame1 = processor.processMask(floatArrayOf(0.95f), 1, 1)
        val frame2 = processor.processMask(floatArrayOf(0.55f), 1, 1) // in hysteresis band
        val frame3 = processor.processMask(floatArrayOf(0.45f), 1, 1) // in hysteresis band
        val frame4 = processor.processMask(floatArrayOf(0.10f), 1, 1) // clearly background

        assertEquals(1f, frame1[0], 0f)
        assertEquals(1f, frame2[0], 0f)
        assertEquals(1f, frame3[0], 0f)
        assertEquals(0f, frame4[0], 0f)
    }

    @Test
    fun neighborhoodConsensus_removesIsolatedSpot_andFillsTinyHole() {
        val processor = MaskProcessor(
            MaskProcessingConfig(
                threshold = 0.5f,
                useTemporalSmoothing = false,
                useHysteresisThreshold = false,
                applyNeighborhoodConsensusFilter = true,
                consensusPasses = 1,
                applyMorphology = false,
                removeSpeckles = false,
                applyFeather = false
            )
        )

        val isolatedSpot = FloatArray(9) { 0f }.apply { this[4] = 1f }
        val cleanedSpot = processor.processMask(isolatedSpot, 3, 3)
        assertTrue(cleanedSpot.all { it == 0f })

        processor.reset()

        val tinyHole = FloatArray(9) { 1f }.apply { this[4] = 0f }
        val filledHole = processor.processMask(tinyHole, 3, 3)
        assertTrue(filledHole.all { it == 1f })
    }

    @Test
    fun normalizeToFrameSize_resizesMaskAndPreservesOutputSize() {
        val mask = SegmentationMask(
            values = floatArrayOf(
                0f, 1f,
                1f, 0f
            ),
            width = 2,
            height = 2
        )

        val normalized = MaskProcessor.normalizeToFrameSize(mask, 4, 4)

        assertEquals(16, normalized.size)
        assertEquals(0f, normalized[0], 0f)
        assertEquals(1f, normalized[3], 0f)
        assertEquals(1f, normalized[12], 0f)
        assertEquals(0f, normalized[15], 0f)
    }
}
