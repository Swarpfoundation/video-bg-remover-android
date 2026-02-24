package com.videobgremover.app.core.quality

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.videobgremover.app.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Types of quality issues that can affect segmentation.
 */
enum class QualityIssueType {
    LOW_LIGHT,
    HIGH_MOTION_BLUR,
    BUSY_BACKGROUND,
    SUBJECT_TOO_SMALL,
    LOW_CONTRAST,
    GREEN_SCREEN_DETECTED // Could use chroma key instead
}

/**
 * Quality issue with severity and message.
 */
data class QualityIssue(
    val type: QualityIssueType,
    val severity: Severity,
    val message: String,
    val suggestion: String
) {
    enum class Severity {
        INFO, WARNING, CRITICAL
    }
}

/**
 * Analyzes video quality to predict segmentation challenges.
 */
class VideoQualityAnalyzer(private val context: Context) {

    /**
     * Analyze video and return list of detected issues.
     */
    suspend fun analyze(videoUri: Uri): List<QualityIssue> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val issues = mutableListOf<QualityIssue>()

        try {
            retriever.setDataSource(context, videoUri)

            // Sample frames at different timestamps
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val sampleTimes = listOf(
                durationMs / 4,
                durationMs / 2,
                durationMs * 3 / 4
            )

            var totalBrightness = 0.0
            var totalBlur = 0.0
            var totalBackgroundComplexity = 0.0
            var smallSubjectFrames = 0

            sampleTimes.forEach { timeMs ->
                val bitmap = retriever.getFrameAtTime(
                    timeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )

                bitmap?.let {
                    totalBrightness += calculateAverageBrightness(it)
                    totalBlur += estimateMotionBlur(it)
                    totalBackgroundComplexity += estimateBackgroundComplexity(it)

                    if (isSubjectTooSmall(it)) {
                        smallSubjectFrames++
                    }

                    it.recycle()
                }
            }

            val avgBrightness = totalBrightness / sampleTimes.size
            val avgBlur = totalBlur / sampleTimes.size
            val avgComplexity = totalBackgroundComplexity / sampleTimes.size

            // Detect issues
            if (avgBrightness < LOW_LIGHT_THRESHOLD) {
                issues.add(
                    QualityIssue(
                        type = QualityIssueType.LOW_LIGHT,
                        severity = QualityIssue.Severity.WARNING,
                        message = "Low light detected",
                        suggestion = "Results may be less accurate. Try processing in a brighter area."
                    )
                )
            }

            if (avgBlur > HIGH_BLUR_THRESHOLD) {
                issues.add(
                    QualityIssue(
                        type = QualityIssueType.HIGH_MOTION_BLUR,
                        severity = QualityIssue.Severity.INFO,
                        message = "Motion blur detected",
                        suggestion = "Fast movement detected. Edges may be softer."
                    )
                )
            }

            if (avgComplexity > BUSY_BG_THRESHOLD) {
                issues.add(
                    QualityIssue(
                        type = QualityIssueType.BUSY_BACKGROUND,
                        severity = QualityIssue.Severity.WARNING,
                        message = "Complex background",
                        suggestion = "Background has lots of detail. Processing may take longer."
                    )
                )
            }

            if (smallSubjectFrames >= 2) {
                issues.add(
                    QualityIssue(
                        type = QualityIssueType.SUBJECT_TOO_SMALL,
                        severity = QualityIssue.Severity.WARNING,
                        message = "Subject appears small",
                        suggestion = "Move closer to the camera for better results."
                    )
                )
            }

            // Check for green screen
            if (detectGreenScreen(retriever, sampleTimes)) {
                issues.add(
                    QualityIssue(
                        type = QualityIssueType.GREEN_SCREEN_DETECTED,
                        severity = QualityIssue.Severity.INFO,
                        message = "Green screen detected",
                        suggestion = "Try the 'Chroma Key' preset in settings for better results."
                    )
                )
            }

            Logger.d("Quality analysis complete: ${issues.size} issues found")

        } catch (e: Exception) {
            Logger.e("Failed to analyze video quality", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        issues
    }

    /**
     * Calculate average brightness (luminance) of bitmap.
     */
    private fun calculateAverageBrightness(bitmap: Bitmap): Double {
        var totalLuminance = 0.0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        pixels.forEach { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // Perceived luminance formula
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            totalLuminance += luminance
        }

        return totalLuminance / pixels.size
    }

    /**
     * Estimate motion blur using edge variance.
     */
    private fun estimateMotionBlur(bitmap: Bitmap): Double {
        // Simple Laplacian variance for sharpness estimation
        var laplacianSum = 0.0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Sample every 10th pixel for performance
        for (i in 0 until pixels.size step 10) {
            val x = i % bitmap.width
            val y = i / bitmap.width

            if (x > 0 && x < bitmap.width - 1 && y > 0 && y < bitmap.height - 1) {
                val idx = y * bitmap.width + x
                val center = (pixels[idx] shr 16) and 0xFF // Red channel
                val left = (pixels[idx - 1] shr 16) and 0xFF
                val right = (pixels[idx + 1] shr 16) and 0xFF
                val top = (pixels[idx - bitmap.width] shr 16) and 0xFF
                val bottom = (pixels[idx + bitmap.width] shr 16) and 0xFF

                // Laplacian
                val laplacian = 4 * center - left - right - top - bottom
                laplacianSum += laplacian * laplacian
            }
        }

        // Lower variance = more blur
        return laplacianSum / (pixels.size / 10)
    }

    /**
     * Estimate background complexity using edge density.
     */
    private fun estimateBackgroundComplexity(bitmap: Bitmap): Double {
        var edgeCount = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Simple Sobel edge detection on sampled pixels
        for (y in 1 until bitmap.height - 1 step 5) {
            for (x in 1 until bitmap.width - 1 step 5) {
                val idx = y * bitmap.width + x

                val gx = (
                    -((pixels[idx - bitmap.width - 1] shr 16) and 0xFF) -
                        2 * ((pixels[idx - 1] shr 16) and 0xFF) -
                        ((pixels[idx + bitmap.width - 1] shr 16) and 0xFF) +
                        ((pixels[idx - bitmap.width + 1] shr 16) and 0xFF) +
                        2 * ((pixels[idx + 1] shr 16) and 0xFF) +
                        ((pixels[idx + bitmap.width + 1] shr 16) and 0xFF)
                    )

                val gy = (
                    -((pixels[idx - bitmap.width - 1] shr 16) and 0xFF) -
                        2 * ((pixels[idx - bitmap.width] shr 16) and 0xFF) -
                        ((pixels[idx - bitmap.width + 1] shr 16) and 0xFF) +
                        ((pixels[idx + bitmap.width - 1] shr 16) and 0xFF) +
                        2 * ((pixels[idx + bitmap.width] shr 16) and 0xFF) +
                        ((pixels[idx + bitmap.width + 1] shr 16) and 0xFF)
                    )

                if (sqrt((gx * gx + gy * gy).toDouble()) > 100) {
                    edgeCount++
                }
            }
        }

        val sampleSize = (bitmap.width / 5) * (bitmap.height / 5)
        return edgeCount.toDouble() / sampleSize
    }

    /**
     * Check if subject appears to be too small in frame.
     */
    private fun isSubjectTooSmall(bitmap: Bitmap): Boolean {
        // Simple heuristic: check if center region has enough contrast
        val centerX = bitmap.width / 2
        val centerY = bitmap.height / 2
        val regionSize = minOf(bitmap.width, bitmap.height) / 4

        val centerPixels = mutableListOf<Double>()

        for (y in centerY - regionSize / 2 until centerY + regionSize / 2 step 2) {
            for (x in centerX - regionSize / 2 until centerX + regionSize / 2 step 2) {
                if (x in 0 until bitmap.width && y in 0 until bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val gray = ((pixel shr 16) and 0xFF) * 0.299 +
                        ((pixel shr 8) and 0xFF) * 0.587 +
                        (pixel and 0xFF) * 0.114
                    centerPixels.add(gray)
                }
            }
        }

        if (centerPixels.isEmpty()) return true

        val mean = centerPixels.average()
        val variance = centerPixels.map { (it - mean) * (it - mean) }.average()

        // Low variance in center suggests no distinct subject
        return variance < 500
    }

    /**
     * Detect if video appears to be shot on green screen.
     */
    private fun detectGreenScreen(
        retriever: MediaMetadataRetriever,
        sampleTimes: List<Long>
    ): Boolean {
        var greenDominantFrames = 0

        sampleTimes.forEach { timeMs ->
            val bitmap = retriever.getFrameAtTime(timeMs * 1000)
            bitmap?.let {
                val isGreenDominant = checkGreenDominance(it)
                if (isGreenDominant) greenDominantFrames++
                it.recycle()
            }
        }

        return greenDominantFrames >= sampleTimes.size / 2
    }

    private fun checkGreenDominance(bitmap: Bitmap): Boolean {
        var greenCount = 0

        for (i in 0 until 100) {
            val x = (Math.random() * bitmap.width).toInt()
            val y = (Math.random() * bitmap.height).toInt()
            val pixel = bitmap.getPixel(x, y)

            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // Check if green is dominant
            if (g > r + 30 && g > b + 30 && g > 100) {
                greenCount++
            }
        }

        return greenCount > 30 // 30% green pixels
    }

    companion object {
        private const val LOW_LIGHT_THRESHOLD = 50 // Out of 255
        private const val HIGH_BLUR_THRESHOLD = 500 // Laplacian variance threshold
        private const val BUSY_BG_THRESHOLD = 0.15 // Edge density threshold
    }
}
