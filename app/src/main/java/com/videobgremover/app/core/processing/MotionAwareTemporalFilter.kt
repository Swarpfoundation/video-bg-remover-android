package com.videobgremover.app.core.processing

import android.graphics.Bitmap
import com.videobgremover.app.core.Logger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Motion-aware temporal filter that compensates for camera/subject movement.
 *
 * Unlike simple EMA smoothing which just blends masks temporally,
 * this filter estimates motion between frames and warps the previous mask
 * to align with the current frame before blending.
 */
class MotionAwareTemporalFilter {

    // Previous frame data
    private var previousFrameGray: ByteArray? = null
    private var previousMask: FloatArray? = null
    private var previousWidth: Int = 0
    private var previousHeight: Int = 0

    // Motion vectors (simplified block matching)
    private val blockSize = 16
    private val searchRadius = 8

    /**
     * Configuration for temporal filtering.
     */
    data class Config(
        val temporalAlpha: Float = 0.3f,
        val useMotionCompensation: Boolean = true,
        val motionThreshold: Float = 0.1f, // Min motion to trigger compensation
        val maxMotionPixels: Float = 50f   // Max expected motion per frame
    )

    private val config: Config

    constructor(config: Config = Config()) {
        this.config = config
    }

    /**
     * Process mask with motion-aware temporal smoothing.
     *
     * @param currentMask Raw mask from segmentation model
     * @param currentFrame Current frame bitmap (for motion estimation)
     * @param width Frame width
     * @param height Frame height
     * @return Processed mask with temporal smoothing
     */
    fun process(
        currentMask: FloatArray,
        currentFrame: Bitmap,
        width: Int,
        height: Int
    ): FloatArray {
        // Convert current frame to grayscale
        val currentGray = bitmapToGrayscale(currentFrame, width, height)

        val prevGray = previousFrameGray
        val prevMask = previousMask

        val result = if (config.useMotionCompensation &&
            prevGray != null &&
            prevMask != null &&
            prevGray.size == currentGray.size &&
            prevMask.size == currentMask.size &&
            previousWidth == width &&
            previousHeight == height
        ) {
            // Estimate motion between frames
            val motionVectors = estimateMotion(prevGray, currentGray, width, height)
            val avgMotion = calculateAverageMotion(motionVectors)

            if (avgMotion > config.motionThreshold) {
                Logger.d("Motion detected: $avgMotion, applying motion compensation")

                // Warp previous mask based on motion
                val warpedMask = warpMask(prevMask, motionVectors, width, height)

                // Blend warped mask with current mask
                blendMasks(currentMask, warpedMask, config.temporalAlpha)
            } else {
                // Little motion, use standard EMA
                blendMasks(currentMask, prevMask, config.temporalAlpha)
            }
        } else {
            // First frame or no previous data
            currentMask.copyOf()
        }

        // Store for next frame
        previousFrameGray = currentGray
        previousMask = result.copyOf()
        previousWidth = width
        previousHeight = height

        return result
    }

    /**
     * Simple block matching motion estimation.
     * Returns array of motion vectors (dx, dy) for each block.
     */
    private fun estimateMotion(
        prevGray: ByteArray,
        currGray: ByteArray,
        width: Int,
        height: Int
    ): Array<MotionVector> {
        val blocksX = width / blockSize
        val blocksY = height / blockSize
        if (blocksX <= 0 || blocksY <= 0) {
            return emptyArray()
        }
        val vectors = Array(blocksX * blocksY) { MotionVector(0f, 0f) }

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                val blockIdx = by * blocksX + bx
                val centerX = bx * blockSize + blockSize / 2
                val centerY = by * blockSize + blockSize / 2

                // Find best match in search window
                var bestDx = 0
                var bestDy = 0
                var bestSad = Int.MAX_VALUE

                for (dy in -searchRadius..searchRadius) {
                    for (dx in -searchRadius..searchRadius) {
                        val sad = calculateSAD(
                            prevGray, currGray,
                            centerX, centerY,
                            dx, dy,
                            width, height
                        )
                        if (sad < bestSad) {
                            bestSad = sad
                            bestDx = dx
                            bestDy = dy
                        }
                    }
                }

                vectors[blockIdx] = MotionVector(
                    bestDx.toFloat().coerceIn(-config.maxMotionPixels, config.maxMotionPixels),
                    bestDy.toFloat().coerceIn(-config.maxMotionPixels, config.maxMotionPixels)
                )
            }
        }

        return smoothMotionVectors(vectors, blocksX, blocksY)
    }

    /**
     * Calculate Sum of Absolute Differences for a block.
     */
    private fun calculateSAD(
        prev: ByteArray,
        curr: ByteArray,
        centerX: Int,
        centerY: Int,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int
    ): Int {
        var sad = 0
        val halfBlock = blockSize / 2

        for (y in -halfBlock until halfBlock) {
            for (x in -halfBlock until halfBlock) {
                val px1 = centerX + x
                val py1 = centerY + y
                val px2 = px1 + offsetX
                val py2 = py1 + offsetY

                if (px1 in 0 until width && py1 in 0 until height &&
                    px2 in 0 until width && py2 in 0 until height
                ) {
                    val idx1 = py1 * width + px1
                    val idx2 = py2 * width + px2
                    val prevValue = prev[idx1].toInt() and 0xFF
                    val currValue = curr[idx2].toInt() and 0xFF
                    sad += abs(prevValue - currValue)
                }
            }
        }
        return sad
    }

    /**
     * Smooth block motion vectors to reduce jitter from noisy matches.
     */
    private fun smoothMotionVectors(
        vectors: Array<MotionVector>,
        blocksX: Int,
        blocksY: Int
    ): Array<MotionVector> {
        if (vectors.isEmpty()) return vectors

        val smoothed = Array(vectors.size) { MotionVector(0f, 0f) }
        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                var sumDx = 0f
                var sumDy = 0f
                var count = 0

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val nx = bx + dx
                        val ny = by + dy
                        if (nx !in 0 until blocksX || ny !in 0 until blocksY) continue
                        val mv = vectors[ny * blocksX + nx]
                        sumDx += mv.dx
                        sumDy += mv.dy
                        count++
                    }
                }

                val idx = by * blocksX + bx
                smoothed[idx] = if (count > 0) {
                    MotionVector(sumDx / count, sumDy / count)
                } else {
                    vectors[idx]
                }
            }
        }
        return smoothed
    }

    /**
     * Warp mask based on motion vectors.
     */
    private fun warpMask(
        mask: FloatArray,
        motionVectors: Array<MotionVector>,
        width: Int,
        height: Int
    ): FloatArray {
        val warped = FloatArray(mask.size)
        val blocksX = width / blockSize

        // Simple bilinear interpolation from nearby blocks
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x

                // Find which block this pixel belongs to
                val bx = min(x / blockSize, blocksX - 1)
                val by = min(y / blockSize, (height / blockSize) - 1)
                val blockIdx = by * blocksX + bx

                val mv = motionVectors.getOrNull(blockIdx) ?: MotionVector(0f, 0f)

                // Calculate source position (inverse motion)
                val srcX = x - mv.dx
                val srcY = y - mv.dy

                // Bilinear sample from source
                warped[idx] = sampleBilinear(mask, srcX, srcY, width, height)
            }
        }

        return warped
    }

    /**
     * Bilinear sampling of mask value.
     */
    private fun sampleBilinear(
        mask: FloatArray,
        x: Float,
        y: Float,
        width: Int,
        height: Int
    ): Float {
        val x0 = x.toInt().coerceIn(0, width - 1)
        val y0 = y.toInt().coerceIn(0, height - 1)
        val x1 = min(x0 + 1, width - 1)
        val y1 = min(y0 + 1, height - 1)

        val fx = x - x0
        val fy = y - y0

        val idx00 = y0 * width + x0
        val idx01 = y0 * width + x1
        val idx10 = y1 * width + x0
        val idx11 = y1 * width + x1

        val v00 = mask[idx00]
        val v01 = mask[idx01]
        val v10 = mask[idx10]
        val v11 = mask[idx11]

        // Bilinear interpolation
        val v0 = v00 * (1 - fx) + v01 * fx
        val v1 = v10 * (1 - fx) + v11 * fx
        return v0 * (1 - fy) + v1 * fy
    }

    /**
     * Blend two masks using temporal alpha.
     */
    private fun blendMasks(
        current: FloatArray,
        previous: FloatArray,
        alpha: Float
    ): FloatArray {
        return FloatArray(current.size) { i ->
            alpha * current[i] + (1 - alpha) * previous[i]
        }
    }

    /**
     * Calculate average motion magnitude.
     */
    private fun calculateAverageMotion(vectors: Array<MotionVector>): Float {
        if (vectors.isEmpty()) return 0f
        val totalMotion = vectors.sumOf { (it.dx * it.dx + it.dy * it.dy).toDouble() }
        return kotlin.math.sqrt(totalMotion / vectors.size).toFloat()
    }

    /**
     * Convert bitmap to grayscale byte array.
     */
    private fun bitmapToGrayscale(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val gray = ByteArray(width * height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Standard grayscale conversion
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt().toByte()
        }

        return gray
    }

    /**
     * Reset temporal state.
     */
    fun reset() {
        previousFrameGray = null
        previousMask = null
        previousWidth = 0
        previousHeight = 0
    }

    /**
     * Motion vector data class.
     */
    data class MotionVector(val dx: Float, val dy: Float)

    companion object {
        private const val TAG = "MotionAwareFilter"
    }
}
