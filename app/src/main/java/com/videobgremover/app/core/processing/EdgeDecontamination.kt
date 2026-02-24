package com.videobgremover.app.core.processing

import android.graphics.Bitmap
import android.graphics.Color
import com.videobgremover.app.core.Logger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Edge decontamination (de-spill) processor.
 *
 * Removes color contamination from the background that bleeds onto the subject edges.
 * Common with green screens, blue screens, or bright colored backgrounds.
 *
 * Algorithm:
 * 1. Detect edge pixels (alpha between 0.1 and 0.9)
 * 2. Sample background color from just outside the edge
 * 3. Reduce that color's contribution in the edge pixel while preserving luminance
 */
class EdgeDecontamination {

    data class Config(
        val edgeThresholdLow: Float = 0.1f,   // Alpha below this is background
        val edgeThresholdHigh: Float = 0.9f,  // Alpha above this is foreground
        val sampleRadius: Int = 5,            // Pixels to sample outside edge
        val decontaminationStrength: Float = 0.7f, // How much to remove (0-1)
        val enableForGreen: Boolean = true,
        val enableForBlue: Boolean = true,
        val enableForRed: Boolean = false  // Red screens are rare
    )

    private val config: Config

    constructor(config: Config = Config()) {
        this.config = config
    }

    /**
     * Apply decontamination to a bitmap using the alpha mask.
     *
     * @param rgbaBitmap The bitmap to process (will be modified in place)
     * @param alphaMask Float array of alpha values (0.0 to 1.0)
     * @param width Image width
     * @param height Image height
     * @return Decontaminated bitmap
     */
    fun process(
        rgbaBitmap: Bitmap,
        alphaMask: FloatArray,
        width: Int,
        height: Int
    ): Bitmap {
        if (!config.enableForGreen && !config.enableForBlue && !config.enableForRed) {
            return rgbaBitmap // Decontamination disabled
        }

        val pixels = IntArray(width * height)
        rgbaBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Detect dominant background color
        val bgColor = detectDominantBackgroundColor(pixels, alphaMask, width, height)
        
        if (bgColor == null) {
            Logger.d("No dominant background color detected, skipping decontamination")
            return rgbaBitmap
        }

        Logger.d("Detected background color: R=${bgColor.red}, G=${bgColor.green}, B=${bgColor.blue}")

        // Determine which channels to decontaminate
        val decontaminateRed = config.enableForRed && bgColor.red > max(bgColor.green, bgColor.blue)
        val decontaminateGreen = config.enableForGreen && bgColor.green > max(bgColor.red, bgColor.blue)
        val decontaminateBlue = config.enableForBlue && bgColor.blue > max(bgColor.red, bgColor.green)

        if (!decontaminateRed && !decontaminateGreen && !decontaminateBlue) {
            return rgbaBitmap // No dominant color to decontaminate
        }

        // Process edge pixels
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val alpha = alphaMask[idx]

                // Only process edge pixels
                if (alpha in config.edgeThresholdLow..config.edgeThresholdHigh) {
                    val pixel = pixels[idx]
                    
                    var r = Color.red(pixel)
                    var g = Color.green(pixel)
                    var b = Color.blue(pixel)
                    val a = Color.alpha(pixel)

                    // Store original luminance
                    val originalLuminance = (0.299f * r + 0.587f * g + 0.114f * b)

                    // Apply decontamination
                    if (decontaminateGreen) {
                        g = decontaminateChannel(g, bgColor.green, alpha, originalLuminance)
                    }
                    if (decontaminateBlue) {
                        b = decontaminateChannel(b, bgColor.blue, alpha, originalLuminance)
                    }
                    if (decontaminateRed) {
                        r = decontaminateChannel(r, bgColor.red, alpha, originalLuminance)
                    }

                    // Reconstruct pixel
                    pixels[idx] = Color.argb(a, r, g, b)
                }
            }
        }

        rgbaBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return rgbaBitmap
    }

    /**
     * Decontaminate a single color channel.
     */
    private fun decontaminateChannel(
        pixelValue: Int,
        bgValue: Int,
        alpha: Float,
        targetLuminance: Float
    ): Int {
        // How much of the background color is in this pixel
        val contamination = (pixelValue * bgValue / 255f) / 255f
        
        // Reduce the contaminated color
        val reduction = contamination * config.decontaminationStrength * (1 - alpha)
        var newValue = pixelValue * (1 - reduction)
        
        // Clamp
        newValue = max(0f, min(255f, newValue))
        
        return newValue.toInt()
    }

    /**
     * Detect the dominant background color by sampling pixels with low alpha.
     */
    private fun detectDominantBackgroundColor(
        pixels: IntArray,
        alphaMask: FloatArray,
        width: Int,
        height: Int
    ): BackgroundColor? {
        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var sampleCount = 0

        // Sample pixels that are clearly background (alpha < 0.2)
        for (i in pixels.indices) {
            if (alphaMask[i] < 0.2f) {
                val pixel = pixels[i]
                totalR += Color.red(pixel)
                totalG += Color.green(pixel)
                totalB += Color.blue(pixel)
                sampleCount++
            }
        }

        if (sampleCount < 100) {
            return null // Not enough background samples
        }

        val avgR = (totalR / sampleCount).toInt()
        val avgG = (totalG / sampleCount).toInt()
        val avgB = (totalB / sampleCount).toInt()

        // Only return if there's a clear dominant color
        val maxChannel = maxOf(avgR, avgG, avgB)
        val minChannel = minOf(avgR, avgG, avgB)
        
        // Check if there's a dominant color (difference > 30)
        if (maxChannel - minChannel < 30) {
            return null // Gray/neutral background
        }

        return BackgroundColor(avgR, avgG, avgB)
    }

    /**
     * Alternative: Sample background color from just outside the subject edges.
     * More accurate for complex backgrounds.
     */
    fun processWithEdgeSampling(
        rgbaBitmap: Bitmap,
        alphaMask: FloatArray,
        width: Int,
        height: Int
    ): Bitmap {
        val pixels = IntArray(width * height)
        rgbaBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // For each edge pixel, sample background color nearby
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val alpha = alphaMask[idx]

                if (alpha in config.edgeThresholdLow..config.edgeThresholdHigh) {
                    // Sample background color from outside this edge pixel
                    val bgColor = sampleBackgroundAtEdge(
                        pixels, alphaMask, x, y, width, height
                    ) ?: continue

                    val pixel = pixels[idx]
                    var r = Color.red(pixel)
                    var g = Color.green(pixel)
                    var b = Color.blue(pixel)
                    val a = Color.alpha(pixel)

                    // Apply decontamination based on sampled background
                    val strength = config.decontaminationStrength * (1 - alpha)
                    
                    // Reduce channel that matches background
                    if (bgColor.green > bgColor.red && bgColor.green > bgColor.blue && config.enableForGreen) {
                        g = (g * (1 - strength * (bgColor.green / 255f))).toInt()
                    } else if (bgColor.blue > bgColor.red && bgColor.blue > bgColor.green && config.enableForBlue) {
                        b = (b * (1 - strength * (bgColor.blue / 255f))).toInt()
                    } else if (bgColor.red > bgColor.green && bgColor.red > bgColor.blue && config.enableForRed) {
                        r = (r * (1 - strength * (bgColor.red / 255f))).toInt()
                    }

                    // Ensure we don't lose too much luminance
                    val newLum = 0.299f * r + 0.587f * g + 0.114f * b
                    val oldLum = 0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)
                    
                    if (newLum < oldLum * 0.7f) {
                        // Boost all channels to preserve luminance
                        val boost = (oldLum / max(newLum, 1f)).coerceAtMost(1.5f)
                        r = min(255, (r * boost).toInt())
                        g = min(255, (g * boost).toInt())
                        b = min(255, (b * boost).toInt())
                    }

                    pixels[idx] = Color.argb(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
                }
            }
        }

        rgbaBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return rgbaBitmap
    }

    /**
     * Sample background color from just outside the edge at position (x, y).
     */
    private fun sampleBackgroundAtEdge(
        pixels: IntArray,
        alphaMask: FloatArray,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): BackgroundColor? {
        var totalR = 0
        var totalG = 0
        var totalB = 0
        var sampleCount = 0

        // Sample in a ring outside the edge
        for (dy in -config.sampleRadius..config.sampleRadius) {
            for (dx in -config.sampleRadius..config.sampleRadius) {
                val dist = sqrt((dx * dx + dy * dy).toFloat())
                if (dist in 2f..config.sampleRadius.toFloat()) { // Ring shape
                    val sx = x + dx
                    val sy = y + dy
                    
                    if (sx in 0 until width && sy in 0 until height) {
                        val sIdx = sy * width + sx
                        if (alphaMask[sIdx] < 0.2f) { // Background pixel
                            val pixel = pixels[sIdx]
                            totalR += Color.red(pixel)
                            totalG += Color.green(pixel)
                            totalB += Color.blue(pixel)
                            sampleCount++
                        }
                    }
                }
            }
        }

        if (sampleCount < 3) return null

        return BackgroundColor(
            totalR / sampleCount,
            totalG / sampleCount,
            totalB / sampleCount
        )
    }

    data class BackgroundColor(val red: Int, val green: Int, val blue: Int)
}
