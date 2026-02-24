package com.videobgremover.app.data.worker

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.videobgremover.app.core.Logger
import com.videobgremover.app.core.notification.ProcessingNotificationHelper
import com.videobgremover.app.core.processing.MaskProcessingConfig
import com.videobgremover.app.core.processing.MaskProcessor
import com.videobgremover.app.data.extractor.VideoMetadataExtractor
import com.videobgremover.app.data.repository.SegmentationRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Worker for processing video frames in the background.
 *
 * Extracts frames from a video, runs segmentation on each frame,
 * and exports PNG files with alpha transparency to a cache directory.
 */
class VideoProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationHelper = ProcessingNotificationHelper(context)
    private val metadataExtractor = VideoMetadataExtractor(context)
    private val segmentationRepository = SegmentationRepositoryImpl(context)
    private val maskProcessor = MaskProcessor(MaskProcessingConfig())

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val videoUri = inputData.getString(KEY_VIDEO_URI)
            ?: return@withContext Result.failure(
                errorData("No video URI provided")
            )

        val targetFps = inputData.getInt(KEY_TARGET_FPS, DEFAULT_TARGET_FPS)
        val maxFrames = inputData.getInt(KEY_MAX_FRAMES, DEFAULT_MAX_FRAMES)

        Logger.d("Starting video processing for URI: $videoUri")

        try {
            // Initialize segmentation
            setProgress(progressData(0, 0, "Initializing..."))

            val initResult = segmentationRepository.initialize()
            if (initResult.isFailure) {
                return@withContext Result.failure(
                    errorData("Failed to initialize segmentation model")
                )
            }

            // Extract metadata
            val metadataResult = metadataExtractor.extract(Uri.parse(videoUri))
            if (metadataResult.isFailure) {
                return@withContext Result.failure(
                    errorData("Failed to extract video metadata")
                )
            }

            val metadata = metadataResult.getOrThrow()
            val durationMs = metadata.durationMs

            // Calculate frame interval
            val frameIntervalMs = 1000 / targetFps
            val totalFrames = minOf(
                ((durationMs / frameIntervalMs) + 1).toInt(),
                maxFrames
            )

            Logger.d("Processing $totalFrames frames at $targetFps FPS")

            // Create output directory
            val outputDir = File(applicationContext.cacheDir, "processed/${System.currentTimeMillis()}")
            outputDir.mkdirs()

            // Process frames
            var processedFrames = 0
            var currentTimeMs = 0L
            val startTime = System.currentTimeMillis()

            while (currentTimeMs <= durationMs && processedFrames < maxFrames && isActive) {
                // Extract frame
                val frameResult = metadataExtractor.extractThumbnail(
                    uri = Uri.parse(videoUri),
                    timeUs = currentTimeMs * 1000,
                    width = OUTPUT_WIDTH,
                    height = OUTPUT_HEIGHT
                )

                if (frameResult.isSuccess) {
                    val frameBitmap = frameResult.getOrThrow()

                    // Run segmentation
                    val segmentationResult = segmentationRepository.segmentFrame(frameBitmap)

                    if (segmentationResult.isSuccess) {
                        val confidenceMask = segmentationResult.getOrThrow()

                        // Process mask
                        val processedMask = maskProcessor.processMask(
                            confidenceMask,
                            frameBitmap.width,
                            frameBitmap.height
                        )

                        // Compose with alpha
                        val outputBitmap = maskProcessor.composeWithAlpha(
                            frameBitmap,
                            processedMask,
                            frameBitmap.width,
                            frameBitmap.height
                        )

                        // Save PNG
                        saveFrame(outputBitmap, outputDir, processedFrames)

                        outputBitmap.recycle()
                    }

                    frameBitmap.recycle()
                }

                processedFrames++
                currentTimeMs += frameIntervalMs

                // Update progress
                val progress = (processedFrames * 100) / totalFrames
                val elapsedMs = System.currentTimeMillis() - startTime
                val estimatedTotalMs = if (processedFrames > 0) {
                    (elapsedMs * totalFrames) / processedFrames
                } else {
                    0
                }
                val remainingMs = estimatedTotalMs - elapsedMs

                setProgress(
                    progressData(
                        progress = progress,
                        framesProcessed = processedFrames,
                        totalFrames = totalFrames,
                        remainingMs = remainingMs,
                        status = "Processing frame $processedFrames/$totalFrames"
                    )
                )

                // Update notification periodically
                if (processedFrames % NOTIFICATION_UPDATE_INTERVAL == 0) {
                    setForeground(
                        createForegroundInfo(
                            progress,
                            processedFrames,
                            totalFrames
                        )
                    )
                }
            }

            // Create metadata file
            createMetadataFile(outputDir, metadata, processedFrames, targetFps)

            // Clean up
            segmentationRepository.close()

            Logger.d("Processing complete. Output: ${outputDir.absolutePath}")

            // Return success with output directory
            Result.success(
                successData(
                    outputDir = outputDir.absolutePath,
                    framesProcessed = processedFrames,
                    totalFrames = totalFrames
                )
            )
        } catch (e: Exception) {
            Logger.e("Processing failed", e)
            segmentationRepository.close()
            Result.failure(errorData("Processing failed: ${e.message}"))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(0, 0, 0)
    }

    private fun createForegroundInfo(
        progress: Int,
        currentFrame: Int,
        totalFrames: Int
    ): ForegroundInfo {
        return notificationHelper.createForegroundInfo(
            progress = progress,
            currentFrame = currentFrame,
            totalFrames = totalFrames,
            workId = id.toString()
        )
    }

    /**
     * Save a processed frame as PNG with alpha.
     */
    private fun saveFrame(bitmap: Bitmap, outputDir: File, frameIndex: Int) {
        val fileName = String.format(FRAME_NAME_FORMAT, frameIndex)
        val file = File(outputDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    /**
     * Create metadata JSON file for the processed video.
     */
    private fun createMetadataFile(
        outputDir: File,
        metadata: com.videobgremover.app.domain.model.VideoMetadata,
        framesProcessed: Int,
        targetFps: Int
    ) {
        val metadataFile = File(outputDir, "metadata.json")
        val json = """
            {
                "sourceUri": "${metadata.uri}",
                "sourceName": "${metadata.name}",
                "sourceDurationMs": ${metadata.durationMs},
                "sourceResolution": "${metadata.resolution}",
                "sourceFrameRate": ${metadata.frameRate},
                "framesProcessed": $framesProcessed,
                "targetFps": $targetFps,
                "outputWidth": $OUTPUT_WIDTH,
                "outputHeight": $OUTPUT_HEIGHT,
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        metadataFile.writeText(json)
    }

    private fun progressData(
        progress: Int,
        framesProcessed: Int,
        totalFrames: Int = 0,
        remainingMs: Long = 0,
        status: String = ""
    ): Data {
        return Data.Builder()
            .putInt(PROGRESS_KEY, progress)
            .putInt(FRAMES_PROCESSED_KEY, framesProcessed)
            .putInt(TOTAL_FRAMES_KEY, totalFrames)
            .putLong(REMAINING_MS_KEY, remainingMs)
            .putString(STATUS_KEY, status)
            .build()
    }

    private fun successData(
        outputDir: String,
        framesProcessed: Int,
        totalFrames: Int
    ): Data {
        return Data.Builder()
            .putString(OUTPUT_DIR_KEY, outputDir)
            .putInt(FRAMES_PROCESSED_KEY, framesProcessed)
            .putInt(TOTAL_FRAMES_KEY, totalFrames)
            .build()
    }

    private fun errorData(message: String): Data {
        return Data.Builder()
            .putString(ERROR_KEY, message)
            .build()
    }

    companion object {
        const val KEY_VIDEO_URI = "video_uri"
        const val KEY_TARGET_FPS = "target_fps"
        const val KEY_MAX_FRAMES = "max_frames"

        const val PROGRESS_KEY = "progress"
        const val FRAMES_PROCESSED_KEY = "frames_processed"
        const val TOTAL_FRAMES_KEY = "total_frames"
        const val REMAINING_MS_KEY = "remaining_ms"
        const val STATUS_KEY = "status"
        const val OUTPUT_DIR_KEY = "output_dir"
        const val ERROR_KEY = "error"

        private const val DEFAULT_TARGET_FPS = 15
        private const val DEFAULT_MAX_FRAMES = 900 // 60 seconds at 15 FPS
        private const val OUTPUT_WIDTH = 512
        private const val OUTPUT_HEIGHT = 512
        private const val NOTIFICATION_UPDATE_INTERVAL = 10
        private const val FRAME_NAME_FORMAT = "frame_%05d.png"

        /**
         * Create input data for the worker.
         */
        fun createInputData(
            videoUri: String,
            targetFps: Int = DEFAULT_TARGET_FPS,
            maxFrames: Int = DEFAULT_MAX_FRAMES
        ): Data {
            return Data.Builder()
                .putString(KEY_VIDEO_URI, videoUri)
                .putInt(KEY_TARGET_FPS, targetFps)
                .putInt(KEY_MAX_FRAMES, maxFrames)
                .build()
        }
    }
}
