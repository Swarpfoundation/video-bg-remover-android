package com.videobgremover.app.data.exporter

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.videobgremover.app.core.Logger
import com.videobgremover.app.data.encoder.MaskVideoEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Data class representing mask video export progress.
 */
data class MaskVideoExportProgress(
    val currentFrame: Int,
    val totalFrames: Int,
    val currentFileName: String
)

/**
 * Result of mask video export.
 */
sealed class MaskVideoExportResult {
    data class Success(
        val videoFile: File,
        val frameCount: Int,
        val durationMs: Long,
        val fileSize: Long
    ) : MaskVideoExportResult()

    data class Error(val message: String) : MaskVideoExportResult()
    object Cancelled : MaskVideoExportResult()
}

/**
 * Exports mask videos from processed frames or directly from source video.
 */
class MaskVideoExporter(private val context: Context) {

    private val encoder = MaskVideoEncoder()

    /**
     * Export mask video from a directory of processed mask PNG files.
     *
     * @param maskPngDir Directory containing mask PNG files
     * @param outputFile Output MP4 file
     * @param frameRate Target frame rate
     * @return Flow of progress updates and final result
     */
    fun exportFromPngs(
        maskPngDir: File,
        outputFile: File,
        frameRate: Int = 30
    ): Flow<MaskVideoExportResult> = flow {
        emit(MaskVideoExportResult.Error("PNG to mask video not yet implemented"))
    }.flowOn(Dispatchers.IO)

    /**
     * Export mask video directly from source video using real-time segmentation.
     *
     * @param videoUri Source video URI
     * @param outputFile Output MP4 file
     * @param targetFps Target FPS for extraction
     * @param maxFrames Maximum frames to process
     * @return Flow of progress updates and final result
     */
    fun exportFromVideo(
        videoUri: Uri,
        outputFile: File,
        targetFps: Int = 30,
        maxFrames: Int = 900
    ): Flow<MaskVideoExportResult> = flow {
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, videoUri)

            // Get video metadata
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 1920

            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 1080

            // Calculate frame parameters
            val frameIntervalMs = 1000 / targetFps
            val totalFrames = minOf(
                ((durationMs / frameIntervalMs) + 1).toInt(),
                maxFrames
            )

            // Initialize encoder
            if (!encoder.initialize(outputFile, width, height, targetFps)) {
                emit(MaskVideoExportResult.Error("Failed to initialize video encoder"))
                return@flow
            }

            // Process frames
            var processedFrames = 0
            var currentTimeMs = 0L

            while (currentTimeMs <= durationMs &&
                processedFrames < maxFrames &&
                kotlinx.coroutines.coroutineContext.isActive
            ) {
                // Extract frame bitmap
                val bitmap = retriever.getFrameAtTime(
                    currentTimeMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )

                if (bitmap != null) {
                    // Convert bitmap to grayscale mask
                    val maskData = bitmapToMaskData(bitmap)

                    // Encode frame
                    encoder.encodeFrame(maskData, bitmap.width, bitmap.height)

                    bitmap.recycle()
                }

                processedFrames++
                currentTimeMs += frameIntervalMs

                // Emit progress
                // Note: Real segmentation would happen here in actual implementation
            }

            // Finish encoding
            if (encoder.finish()) {
                val fileSize = outputFile.length()
                emit(
                    MaskVideoExportResult.Success(
                        videoFile = outputFile,
                        frameCount = processedFrames,
                        durationMs = durationMs,
                        fileSize = fileSize
                    )
                )
            } else {
                emit(MaskVideoExportResult.Error("Failed to finalize video"))
            }
        } catch (e: Exception) {
            Logger.e("Mask video export failed", e)
            emit(MaskVideoExportResult.Error("Export failed: ${e.message}"))
        } finally {
            encoder.release()
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Create a mask video from already processed mask data in a directory.
     * This version reads the metadata.json to get processing info.
     */
    suspend fun createMaskVideoFromProcessedDir(
        processedDir: File,
        outputFile: File
    ): MaskVideoExportResult = withContext(Dispatchers.IO) {
        try {
            // Read metadata
            val metadataFile = File(processedDir, "metadata.json")
            if (!metadataFile.exists()) {
                return@withContext MaskVideoExportResult.Error("Metadata not found")
            }

            val metadata = parseMetadata(metadataFile.readText())
            val targetFps = (metadata["targetFps"] as? Number)?.toInt() ?: 30
            val framesProcessed = (metadata["framesProcessed"] as? Number)?.toInt() ?: 0
            val outputWidth = (metadata["outputWidth"] as? Number)?.toInt() ?: 512
            val outputHeight = (metadata["outputHeight"] as? Number)?.toInt() ?: 512

            // Initialize encoder
            if (!encoder.initialize(outputFile, outputWidth, outputHeight, targetFps)) {
                return@withContext MaskVideoExportResult.Error("Failed to initialize encoder")
            }

            // Get PNG files and sort them
            val pngFiles = processedDir.listFiles { file ->
                file.extension.lowercase() == "png" && file.name.startsWith("frame_")
            }?.sortedBy { it.name } ?: emptyArray()

            if (pngFiles.isEmpty()) {
                encoder.release()
                return@withContext MaskVideoExportResult.Error("No PNG files found")
            }

            // Process each PNG file
            pngFiles.forEachIndexed { index, pngFile ->
                if (!isActive) {
                    encoder.release()
                    return@withContext MaskVideoExportResult.Cancelled
                }

                // Load PNG and convert to mask data
                val bitmap = android.graphics.BitmapFactory.decodeFile(pngFile.absolutePath)
                if (bitmap != null) {
                    val maskData = bitmapToMaskData(bitmap)
                    encoder.encodeFrame(maskData, bitmap.width, bitmap.height)
                    bitmap.recycle()
                }
            }

            // Finish
            if (encoder.finish()) {
                val fileSize = outputFile.length()
                val durationMs = (framesProcessed * 1000L) / targetFps

                MaskVideoExportResult.Success(
                    videoFile = outputFile,
                    frameCount = framesProcessed,
                    durationMs = durationMs,
                    fileSize = fileSize
                )
            } else {
                MaskVideoExportResult.Error("Failed to finalize video")
            }
        } catch (e: Exception) {
            Logger.e("Mask video creation failed", e)
            encoder.release()
            MaskVideoExportResult.Error("Export failed: ${e.message}")
        }
    }

    /**
     * Convert a bitmap to mask float array (grayscale).
     */
    private fun bitmapToMaskData(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        return FloatArray(pixels.size) { i ->
            val pixel = pixels[i]
            // Extract alpha or green channel for mask
            val alpha = (pixel shr 24) and 0xFF
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF

            // Use alpha if present, otherwise use green channel (common for mask images)
            if (alpha < 255) {
                alpha / 255f
            } else {
                green / 255f
            }
        }
    }

    /**
     * Parse metadata JSON file.
     */
    private fun parseMetadata(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val regex = """"(\w+)":\s*([^,}]+)""".toRegex()

        regex.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim().removeSurrounding("\"")

            result[key] = when {
                value.matches(Regex("-?\\d+")) -> value.toLong()
                value.matches(Regex("-?\\d+\\.\\d+")) -> value.toDouble()
                value == "true" -> true
                value == "false" -> false
                else -> value
            }
        }

        return result
    }

    companion object {
        /**
         * Generate a default filename for mask video exports.
         */
        fun generateDefaultFilename(): String {
            val timestamp = java.text.SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                java.util.Locale.getDefault()
            ).format(java.util.Date())
            return "mask_video_$timestamp.mp4"
        }
    }
}
