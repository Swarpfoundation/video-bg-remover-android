package com.videobgremover.app.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.withSave

/**
 * Configuration for mask post-processing.
 */
data class MaskProcessingConfig(
    val threshold: Float = 0.5f,
    val useTemporalSmoothing: Boolean = true,
    val temporalAlpha: Float = 0.3f,
    val applyMorphology: Boolean = true,
    val morphologyRadius: Int = 2,
    val applyFeather: Boolean = true,
    val featherRadius: Float = 3f
)

/**
 * Processes segmentation masks with various post-processing techniques:
 * - Thresholding: Convert confidence to binary mask
 * - Temporal smoothing: EMA between consecutive frames
 * - Morphology: Close/open to reduce holes and speckles
 * - Feather: Gaussian-like blur on edges
 */
class MaskProcessor(
    private val config: MaskProcessingConfig = MaskProcessingConfig()
) {
    private var previousMask: FloatArray? = null

    /**
     * Process a raw confidence mask from the segmentation model.
     *
     * @param confidenceMask Raw confidence values (0.0 to 1.0)
     * @param width Mask width
     * @param height Mask height
     * @return Processed mask as FloatArray
     */
    fun processMask(confidenceMask: FloatArray, width: Int, height: Int): FloatArray {
        var mask = confidenceMask.copyOf()

        // Step 1: Threshold
        mask = applyThreshold(mask)

        // Step 2: Temporal smoothing
        if (config.useTemporalSmoothing) {
            mask = applyTemporalSmoothing(mask)
        }

        // Step 3: Morphological operations
        if (config.applyMorphology) {
            mask = applyMorphology(mask, width, height)
        }

        // Step 4: Feather edges
        if (config.applyFeather) {
            mask = applyFeather(mask, width, height)
        }

        return mask
    }

    /**
     * Reset temporal state (call when switching videos).
     */
    fun reset() {
        previousMask = null
    }

    /**
     * Apply threshold to convert confidence to binary mask.
     */
    private fun applyThreshold(mask: FloatArray): FloatArray {
        return FloatArray(mask.size) { i ->
            if (mask[i] >= config.threshold) 1.0f else 0.0f
        }
    }

    /**
     * Apply exponential moving average (EMA) for temporal smoothing.
     * Formula: new_mask = alpha * current + (1 - alpha) * previous
     */
    private fun applyTemporalSmoothing(mask: FloatArray): FloatArray {
        val prev = previousMask

        val result = if (prev != null && prev.size == mask.size) {
            FloatArray(mask.size) { i ->
                config.temporalAlpha * mask[i] + (1 - config.temporalAlpha) * prev[i]
            }
        } else {
            mask
        }

        previousMask = result.copyOf()
        return result
    }

    /**
     * Apply morphological close (dilate then erode) to fill holes.
     */
    private fun applyMorphology(mask: FloatArray, width: Int, height: Int): FloatArray {
        // Dilate (expand mask)
        val dilated = applyDilation(mask, width, height, config.morphologyRadius)
        // Erode (shrink mask)
        return applyErosion(dilated, width, height, config.morphologyRadius)
    }

    /**
     * Dilation: Set pixel to max of neighbors.
     */
    private fun applyDilation(
        mask: FloatArray,
        width: Int,
        height: Int,
        radius: Int
    ): FloatArray {
        return applyMorphologicalOp(mask, width, height, radius, true)
    }

    /**
     * Erosion: Set pixel to min of neighbors.
     */
    private fun applyErosion(
        mask: FloatArray,
        width: Int,
        height: Int,
        radius: Int
    ): FloatArray {
        return applyMorphologicalOp(mask, width, height, radius, false)
    }

    /**
     * Generic morphological operation.
     */
    private fun applyMorphologicalOp(
        mask: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        isDilation: Boolean
    ): FloatArray {
        val result = FloatArray(mask.size)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                var extreme = if (isDilation) 0.0f else 1.0f

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val ny = y + dy
                        val nx = x + dx

                        if (ny in 0 until height && nx in 0 until width) {
                            val nidx = ny * width + nx
                            extreme = if (isDilation) {
                                maxOf(extreme, mask[nidx])
                            } else {
                                minOf(extreme, mask[nidx])
                            }
                        }
                    }
                }

                result[idx] = extreme
            }
        }

        return result
    }

    /**
     * Apply feathering using a simple box blur approximation.
     */
    private fun applyFeather(mask: FloatArray, width: Int, height: Int): FloatArray {
        val radius = config.featherRadius.toInt()
        if (radius <= 0) return mask

        val result = mask.copyOf()

        // Simple box blur
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                var sum = 0.0f
                var count = 0

                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val ny = y + dy
                        val nx = x + dx

                        if (ny in 0 until height && nx in 0 until width) {
                            sum += mask[ny * width + nx]
                            count++
                        }
                    }
                }

                result[idx] = sum / count
            }
        }

        return result
    }

    /**
     * Compose a bitmap with the mask applied as alpha channel.
     */
    fun composeWithAlpha(
        sourceBitmap: Bitmap,
        mask: FloatArray,
        outputWidth: Int,
        outputHeight: Int
    ): Bitmap {
        val output = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Scale source bitmap if needed
        val scaledSource = if (sourceBitmap.width != outputWidth ||
            sourceBitmap.height != outputHeight
        ) {
            Bitmap.createScaledBitmap(sourceBitmap, outputWidth, outputHeight, true)
        } else {
            sourceBitmap
        }

        // Create mask bitmap
        val maskBitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ALPHA_8)
        val maskPixels = IntArray(outputWidth * outputHeight)

        for (i in maskPixels.indices) {
            val alpha = (mask.getOrElse(i) { 0f } * 255).toInt()
            maskPixels[i] = (alpha shl 24)
        }

        maskBitmap.setPixels(
            maskPixels.map { it or 0x00FFFFFF }.toIntArray(),
            0,
            outputWidth,
            0,
            0,
            outputWidth,
            outputHeight
        )

        // Draw source with mask
        val paint = Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(
                android.graphics.PorterDuff.Mode.DST_IN
            )
        }

        canvas.drawBitmap(scaledSource, 0f, 0f, null)
        canvas.drawBitmap(maskBitmap, 0f, 0f, paint)

        if (scaledSource !== sourceBitmap) {
            scaledSource.recycle()
        }
        maskBitmap.recycle()

        return output
    }

    /**
     * Create a mask visualization bitmap (grayscale).
     */
    fun createMaskVisualization(
        mask: FloatArray,
        width: Int,
        height: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(mask.size) { i ->
            val value = (mask.getOrElse(i) { 0f } * 255).toInt()
            (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
