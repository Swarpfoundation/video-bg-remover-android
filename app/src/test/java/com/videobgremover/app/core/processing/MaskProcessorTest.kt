package com.videobgremover.app.core.processing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [MaskProcessor].
 */
class MaskProcessorTest {

    private lateinit var processor: MaskProcessor

    @Before
    fun setup() {
        processor = MaskProcessor(MaskProcessingConfig())
    }

    @Test
    fun `threshold converts confidence to binary`() {
        val config = MaskProcessingConfig(threshold = 0.5f)
        val testProcessor = MaskProcessor(config)

        val input = floatArrayOf(0.3f, 0.5f, 0.7f, 0.49f, 0.51f)
        val width = 5
        val height = 1

        val result = testProcessor.processMask(input, width, height)

        // Below threshold -> 0, At/Above threshold -> 1
        assertEquals(0.0f, result[0], 0.01f)  // 0.3 < 0.5
        assertEquals(1.0f, result[1], 0.01f)  // 0.5 >= 0.5
        assertEquals(1.0f, result[2], 0.01f)  // 0.7 >= 0.5
        assertEquals(0.0f, result[3], 0.01f)  // 0.49 < 0.5
        assertEquals(1.0f, result[4], 0.01f)  // 0.51 >= 0.5
    }

    @Test
    fun `temporal smoothing blends with previous frame`() {
        val config = MaskProcessingConfig(
            useTemporalSmoothing = true,
            temporalAlpha = 0.5f
        )
        val testProcessor = MaskProcessor(config)

        val frame1 = floatArrayOf(1.0f, 0.0f, 1.0f)
        val frame2 = floatArrayOf(0.0f, 1.0f, 0.0f)
        val width = 3
        val height = 1

        // First frame - no previous to blend with
        val result1 = testProcessor.processMask(frame1, width, height)
        assertArrayEquals(floatArrayOf(1.0f, 0.0f, 1.0f), result1, 0.01f)

        // Second frame - should blend with first
        val result2 = testProcessor.processMask(frame2, width, height)
        // With alpha=0.5: new = 0.5 * current + 0.5 * previous
        // 0.5 * 0 + 0.5 * 1 = 0.5
        // 0.5 * 1 + 0.5 * 0 = 0.5
        // 0.5 * 0 + 0.5 * 1 = 0.5
        assertEquals(0.5f, result2[0], 0.01f)
        assertEquals(0.5f, result2[1], 0.01f)
        assertEquals(0.5f, result2[2], 0.01f)
    }

    @Test
    fun `reset clears temporal state`() {
        val config = MaskProcessingConfig(
            useTemporalSmoothing = true,
            temporalAlpha = 0.5f
        )
        val testProcessor = MaskProcessor(config)

        val frame1 = floatArrayOf(1.0f, 1.0f, 1.0f)
        val frame2 = floatArrayOf(0.0f, 0.0f, 0.0f)
        val width = 3
        val height = 1

        // Process first frame
        testProcessor.processMask(frame1, width, height)

        // Reset
        testProcessor.reset()

        // Process second frame - should not blend with first
        val result = testProcessor.processMask(frame2, width, height)
        // After threshold, should be all 0s
        assertEquals(0.0f, result[0], 0.01f)
        assertEquals(0.0f, result[1], 0.01f)
        assertEquals(0.0f, result[2], 0.01f)
    }

    @Test
    fun `createMaskVisualization produces grayscale bitmap`() {
        val mask = floatArrayOf(0.0f, 0.5f, 1.0f, 0.25f, 0.75f, 1.0f)
        val width = 3
        val height = 2

        val bitmap = processor.createMaskVisualization(mask, width, height)

        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)

        // Check pixel values
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // 0.0 -> 0 (black)
        assertEquals(0xFF000000.toInt(), pixels[0])
        // 0.5 -> 127 (gray)
        assertTrue(pixels[1] and 0xFF > 120 && pixels[1] and 0xFF < 135)
        // 1.0 -> 255 (white)
        assertEquals(0xFFFFFFFF.toInt(), pixels[2])
    }

    @Test
    fun `mask processing with disabled options returns thresholded mask`() {
        val config = MaskProcessingConfig(
            useTemporalSmoothing = false,
            applyMorphology = false,
            applyFeather = false,
            threshold = 0.5f
        )
        val testProcessor = MaskProcessor(config)

        val input = floatArrayOf(0.3f, 0.6f, 0.4f, 0.8f)
        val width = 2
        val height = 2

        val result = testProcessor.processMask(input, width, height)

        // Should only apply threshold
        assertEquals(0.0f, result[0], 0.01f)  // 0.3 < 0.5
        assertEquals(1.0f, result[1], 0.01f)  // 0.6 >= 0.5
        assertEquals(0.0f, result[2], 0.01f)  // 0.4 < 0.5
        assertEquals(1.0f, result[3], 0.01f)  // 0.8 >= 0.5
    }

    @Test
    fun `temporal smoothing with zero alpha uses only previous`() {
        val config = MaskProcessingConfig(
            useTemporalSmoothing = true,
            temporalAlpha = 0.0f
        )
        val testProcessor = MaskProcessor(config)

        val frame1 = floatArrayOf(1.0f, 0.0f, 1.0f)
        val frame2 = floatArrayOf(0.0f, 1.0f, 0.0f)
        val width = 3
        val height = 1

        testProcessor.processMask(frame1, width, height)
        val result = testProcessor.processMask(frame2, width, height)

        // With alpha=0.0: new = 0.0 * current + 1.0 * previous
        // Should equal previous frame (after threshold)
        assertEquals(1.0f, result[0], 0.01f)
        assertEquals(0.0f, result[1], 0.01f)
        assertEquals(1.0f, result[2], 0.01f)
    }

    @Test
    fun `temporal smoothing with one alpha uses only current`() {
        val config = MaskProcessingConfig(
            useTemporalSmoothing = true,
            temporalAlpha = 1.0f
        )
        val testProcessor = MaskProcessor(config)

        val frame1 = floatArrayOf(1.0f, 0.0f, 1.0f)
        val frame2 = floatArrayOf(0.0f, 1.0f, 0.0f)
        val width = 3
        val height = 1

        testProcessor.processMask(frame1, width, height)
        val result = testProcessor.processMask(frame2, width, height)

        // With alpha=1.0: new = 1.0 * current + 0.0 * previous
        // Should equal current frame (after threshold)
        assertEquals(0.0f, result[0], 0.01f)
        assertEquals(1.0f, result[1], 0.01f)
        assertEquals(0.0f, result[2], 0.01f)
    }

    private fun assertArrayEquals(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals(expected[i], actual[i], delta)
        }
    }
}
