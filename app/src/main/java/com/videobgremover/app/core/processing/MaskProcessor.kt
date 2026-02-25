package com.videobgremover.app.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.withSave
import com.videobgremover.app.domain.model.SegmentationMask

/**
 * Configuration for mask post-processing.
 */
data class MaskProcessingConfig(
    val threshold: Float = 0.5f,
    val useTemporalSmoothing: Boolean = true,
    val temporalAlpha: Float = 0.3f,
    val useHysteresisThreshold: Boolean = true,
    val hysteresisDelta: Float = 0.08f,
    val applyMorphology: Boolean = true,
    val morphologyRadius: Int = 2,
    val applyNeighborhoodConsensusFilter: Boolean = true,
    val consensusPasses: Int = 1,
    val applyFeather: Boolean = true,
    val featherRadius: Float = 3f,
    val removeSpeckles: Boolean = true,
    val speckleMinSize: Int = 50,      // Minimum component size to keep
    val speckleMaxHole: Int = 100      // Maximum hole size to fill
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
    private var previousConfidenceMask: FloatArray? = null
    private var previousStableMask: FloatArray? = null
    private val speckleRemover by lazy {
        SpeckleRemover(
            minComponentSize = config.speckleMinSize,
            maxHoleSize = config.speckleMaxHole,
            removeIslands = true,
            fillHoles = true
        )
    }

    /**
     * Process a raw confidence mask from the segmentation model.
     *
     * @param confidenceMask Raw confidence values (0.0 to 1.0)
     * @param width Mask width
     * @param height Mask height
     * @return Processed mask as FloatArray
     */
    fun processMask(confidenceMask: FloatArray, width: Int, height: Int): FloatArray {
        validateMaskDimensions(confidenceMask, width, height)
        var confidence = sanitizeConfidence(confidenceMask)

        // Step 1: Temporal smoothing on confidence mask (more stable than smoothing binary masks)
        if (config.useTemporalSmoothing) {
            confidence = applyTemporalSmoothing(confidence)
        }

        // Step 2: Threshold with hysteresis to reduce near-threshold flicker
        var mask = applyThreshold(confidence, previousStableMask)

        // Step 3: Remove isolated single-pixel noise / tiny holes before heavier ops
        if (config.applyNeighborhoodConsensusFilter) {
            repeat(config.consensusPasses.coerceAtLeast(1)) {
                mask = applyNeighborhoodConsensusFilter(mask, width, height)
            }
        }

        // Step 4: Morphological operations
        if (config.applyMorphology) {
            mask = applyMorphology(mask, width, height)
        }

        // Step 5: Remove speckles and spots
        if (config.removeSpeckles) {
            mask = speckleRemover.process(mask, width, height)
        }

        // Preserve a stable binary mask for hysteresis thresholding on the next frame
        previousStableMask = mask.copyOf()

        // Step 6: Feather edges
        if (config.applyFeather) {
            mask = applyFeather(mask, width, height)
        }

        return mask
    }

    /**
     * Reset temporal state (call when switching videos).
     */
    fun reset() {
        previousConfidenceMask = null
        previousStableMask = null
    }

    /**
     * Apply threshold to convert confidence to binary mask.
     */
    private fun applyThreshold(mask: FloatArray, previousStable: FloatArray?): FloatArray {
        val threshold = config.threshold.coerceIn(0f, 1f)
        if (!config.useHysteresisThreshold ||
            previousStable == null ||
            previousStable.size != mask.size
        ) {
            return FloatArray(mask.size) { i ->
                if (mask[i] >= threshold) 1.0f else 0.0f
            }
        }

        val delta = config.hysteresisDelta.coerceIn(0f, 0.49f)
        val low = (threshold - delta).coerceIn(0f, 1f)
        val high = (threshold + delta).coerceIn(0f, 1f)

        return FloatArray(mask.size) { i ->
            val value = mask[i]
            when {
                value >= high -> 1.0f
                value <= low -> 0.0f
                previousStable[i] >= 0.5f -> 1.0f
                else -> 0.0f
            }
        }
    }

    /**
     * Apply exponential moving average (EMA) for temporal smoothing.
     * Formula: new_mask = alpha * current + (1 - alpha) * previous
     */
    private fun applyTemporalSmoothing(mask: FloatArray): FloatArray {
        val prev = previousConfidenceMask

        val result = if (prev != null && prev.size == mask.size) {
            FloatArray(mask.size) { i ->
                (config.temporalAlpha * mask[i] + (1 - config.temporalAlpha) * prev[i])
                    .coerceIn(0f, 1f)
            }
        } else {
            mask.copyOf()
        }

        previousConfidenceMask = result.copyOf()
        return result
    }

    /**
     * Clamp out-of-range confidence values produced by upstream processing.
     */
    private fun sanitizeConfidence(mask: FloatArray): FloatArray {
        return FloatArray(mask.size) { i -> mask[i].coerceIn(0f, 1f) }
    }

    /**
     * Remove salt-and-pepper noise and tiny pinholes using a local 3x3 consensus rule.
     * This is less destructive than a full majority/median filter and helps with flickering spots.
     */
    private fun applyNeighborhoodConsensusFilter(
        mask: FloatArray,
        width: Int,
        height: Int
    ): FloatArray {
        if (width < 3 || height < 3) return mask

        val result = mask.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                var foregroundNeighbors = 0

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (mask[(y + dy) * width + (x + dx)] >= 0.5f) {
                            foregroundNeighbors++
                        }
                    }
                }

                val isForeground = mask[idx] >= 0.5f
                result[idx] = when {
                    // Isolated white spot (including center): 0-2 foreground pixels in 3x3
                    isForeground && foregroundNeighbors <= 2 -> 0.0f
                    // Tiny black hole in solid subject region: 7-9 foreground pixels in 3x3
                    !isForeground && foregroundNeighbors >= 8 -> 1.0f
                    else -> if (isForeground) 1.0f else 0.0f
                }
            }
        }

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
        validateMaskDimensions(mask, outputWidth, outputHeight)
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
        validateMaskDimensions(mask, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(mask.size) { i ->
            val value = (mask.getOrElse(i) { 0f } * 255).toInt()
            (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    companion object {
        /**
         * Resize the segmentation mask to the target frame size if needed.
         * MediaPipe can return masks at a different resolution than the input bitmap.
         */
        fun normalizeToFrameSize(mask: SegmentationMask, targetWidth: Int, targetHeight: Int): FloatArray {
            if (mask.width == targetWidth && mask.height == targetHeight) {
                return mask.values.copyOf(targetWidth * targetHeight)
            }
            return resizeMaskNearest(mask.values, mask.width, mask.height, targetWidth, targetHeight)
        }

        fun validateMaskDimensions(mask: FloatArray, width: Int, height: Int) {
            require(width > 0 && height > 0) { "Mask dimensions must be > 0" }
            require(mask.size >= width * height) {
                "Mask size (${mask.size}) is smaller than width*height (${width * height})"
            }
        }

        private fun resizeMaskNearest(
            sourceMask: FloatArray,
            sourceWidth: Int,
            sourceHeight: Int,
            targetWidth: Int,
            targetHeight: Int
        ): FloatArray {
            validateMaskDimensions(sourceMask, sourceWidth, sourceHeight)
            require(targetWidth > 0 && targetHeight > 0) { "Target dimensions must be > 0" }

            val result = FloatArray(targetWidth * targetHeight)
            for (y in 0 until targetHeight) {
                val srcY = ((y.toFloat() / targetHeight) * sourceHeight)
                    .toInt()
                    .coerceIn(0, sourceHeight - 1)
                val srcRowOffset = srcY * sourceWidth
                val dstRowOffset = y * targetWidth
                for (x in 0 until targetWidth) {
                    val srcX = ((x.toFloat() / targetWidth) * sourceWidth)
                        .toInt()
                        .coerceIn(0, sourceWidth - 1)
                    result[dstRowOffset + x] = sourceMask[srcRowOffset + srcX]
                }
            }
            return result
        }
    }
}
