package com.videobgremover.app.core.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.videobgremover.app.core.Logger

/**
 * Device capability classification for adaptive performance.
 */
enum class DeviceClass {
    LOW_END,      // Older devices, limited RAM
    MID_RANGE,    // Average modern device
    HIGH_END      // Flagship device
}

/**
 * Processing quality preset.
 */
enum class ProcessingQuality {
    FAST,      // 480p, 15 FPS, frame skipping
    BALANCED,  // 720p, 24 FPS
    BEST       // 1080p, 30 FPS, all frames
}

/**
 * Processing specification.
 */
data class ProcessingSpec(
    val targetResolution: Int,
    val targetFps: Int,
    val processEveryNFrames: Int = 1,
    val useGpu: Boolean = true,
    val description: String
)

/**
 * Controller for adaptive performance based on device capabilities and video properties.
 */
class AdaptivePerformanceController(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Classify the device based on RAM and CPU.
     */
    fun getDeviceClass(): DeviceClass {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalRam = memoryInfo.totalMem / (1024 * 1024 * 1024) // GB

        return when {
            totalRam >= 8 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> DeviceClass.HIGH_END
            totalRam >= 4 -> DeviceClass.MID_RANGE
            else -> DeviceClass.LOW_END
        }
    }

    /**
     * Get optimal processing spec based on device and video duration.
     */
    fun getOptimalSpec(
        videoDurationMs: Long,
        inputResolution: Int,
        userPreference: ProcessingQuality = ProcessingQuality.BALANCED
    ): ProcessingSpec {
        val deviceClass = getDeviceClass()
        val durationSec = videoDurationMs / 1000

        Logger.d("Adaptive spec: device=$deviceClass, duration=${durationSec}s, preference=$userPreference")

        return when (userPreference) {
            ProcessingQuality.FAST -> getFastSpec(deviceClass, durationSec)
            ProcessingQuality.BALANCED -> getBalancedSpec(deviceClass, durationSec)
            ProcessingQuality.BEST -> getBestSpec(deviceClass, durationSec)
        }
    }

    private fun getFastSpec(deviceClass: DeviceClass, durationSec: Long): ProcessingSpec {
        // For long videos or low-end devices, be aggressive
        val processEveryNFrames = when {
            durationSec > 120 -> 6  // Process every 6th frame (5 FPS effective)
            durationSec > 60 -> 4   // Process every 4th frame (7.5 FPS effective)
            else -> 2               // Process every other frame (15 FPS effective)
        }

        val resolution = when (deviceClass) {
            DeviceClass.LOW_END -> 360
            DeviceClass.MID_RANGE -> 480
            DeviceClass.HIGH_END -> 480
        }

        return ProcessingSpec(
            targetResolution = resolution,
            targetFps = 15,
            processEveryNFrames = processEveryNFrames,
            useGpu = deviceClass != DeviceClass.LOW_END,
            description = "Fast - ${resolution}p, ${15 / processEveryNFrames} FPS effective"
        )
    }

    private fun getBalancedSpec(deviceClass: DeviceClass, durationSec: Long): ProcessingSpec {
        val processEveryNFrames = when {
            durationSec > 120 -> 3  // Process every 3rd frame
            durationSec > 60 -> 2   // Process every other frame
            else -> 1               // All frames
        }

        val (resolution, fps) = when (deviceClass) {
            DeviceClass.LOW_END -> 480 to 15
            DeviceClass.MID_RANGE -> 720 to 24
            DeviceClass.HIGH_END -> 720 to 24
        }

        return ProcessingSpec(
            targetResolution = resolution,
            targetFps = fps,
            processEveryNFrames = processEveryNFrames,
            useGpu = true,
            description = "Balanced - ${resolution}p, ${fps / processEveryNFrames} FPS effective"
        )
    }

    private fun getBestSpec(deviceClass: DeviceClass, durationSec: Long): ProcessingSpec {
        // Even "Best" should be reasonable for very long videos
        val processEveryNFrames = if (durationSec > 180) 2 else 1

        val resolution = when (deviceClass) {
            DeviceClass.LOW_END -> 720
            DeviceClass.MID_RANGE -> 1080
            DeviceClass.HIGH_END -> 1080
        }

        return ProcessingSpec(
            targetResolution = resolution,
            targetFps = 30,
            processEveryNFrames = processEveryNFrames,
            useGpu = true,
            description = "Best - ${resolution}p, ${30 / processEveryNFrames} FPS effective"
        )
    }

    /**
     * Estimate processing time based on spec and video duration.
     */
    fun estimateProcessingTimeMs(
        videoDurationMs: Long,
        spec: ProcessingSpec
    ): Long {
        // Rough estimates based on device class and resolution
        val baseTimePerFrameMs = when (spec.targetResolution) {
            in 0..480 -> if (spec.useGpu) 50 else 150
            in 481..720 -> if (spec.useGpu) 100 else 300
            else -> if (spec.useGpu) 200 else 600
        }

        val totalFrames = (videoDurationMs / 1000f * spec.targetFps).toInt()
        val framesToProcess = totalFrames / spec.processEveryNFrames

        return (framesToProcess * baseTimePerFrameMs).toLong()
    }

    /**
     * Get recommended quality options for a specific video.
     */
    fun getQualityOptions(videoDurationMs: Long): List<QualityOption> {
        val deviceClass = getDeviceClass()
        val durationSec = videoDurationMs / 1000

        return ProcessingQuality.values().map { quality ->
            val spec = getOptimalSpec(videoDurationMs, 1080, quality)
            val estimatedTimeMs = estimateProcessingTimeMs(videoDurationMs, spec)
            val estimatedTimeStr = formatDuration(estimatedTimeMs)

            QualityOption(
                quality = quality,
                spec = spec,
                estimatedTimeStr = estimatedTimeStr,
                recommended = when (quality) {
                    ProcessingQuality.FAST -> durationSec > 60 || deviceClass == DeviceClass.LOW_END
                    ProcessingQuality.BALANCED -> durationSec <= 60 && deviceClass != DeviceClass.LOW_END
                    ProcessingQuality.BEST -> durationSec <= 30 && deviceClass == DeviceClass.HIGH_END
                }
            )
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }
}

/**
 * Quality option with metadata for UI.
 */
data class QualityOption(
    val quality: ProcessingQuality,
    val spec: ProcessingSpec,
    val estimatedTimeStr: String,
    val recommended: Boolean
)
