package com.videobgremover.app.data.extractor

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.videobgremover.app.domain.model.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts metadata from video files using [MediaMetadataRetriever].
 */
class VideoMetadataExtractor(private val context: Context) {

    /**
     * Extract metadata from a video URI.
     *
     * @param uri The video content URI
     * @return Result containing [VideoMetadata] or an exception
     */
    suspend fun extract(uri: Uri): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L

            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0

            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0

            val frameRate = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE
            )?.toFloatOrNull()
                ?: run {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        retriever.extractMetadata(VIDEO_FRAME_COUNT_METADATA_KEY_COMPAT)
                            ?.toLongOrNull()
                            ?.let { frameCount ->
                                if (durationMs > 0) frameCount * 1000f / durationMs else 30f
                            }
                    } else {
                        null
                    }
                }
                ?: 30f

            val codec = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_MIMETYPE
            )

            val name = uri.lastPathSegment?.substringAfterLast("/")
                ?: "unknown_video"

            val metadata = VideoMetadata(
                uri = uri.toString(),
                name = name,
                durationMs = durationMs,
                width = width,
                height = height,
                frameRate = frameRate,
                codec = codec
            )

            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
        }
    }

    /**
     * Extract a thumbnail from the video at the specified time.
     *
     * @param uri The video content URI
     * @param timeUs Time in microseconds (default: 0 = first frame)
     * @param width Desired width (null for original)
     * @param height Desired height (null for original)
     * @return Result containing [Bitmap] or an exception
     */
    suspend fun extractThumbnail(
        uri: Uri,
        timeUs: Long = 0L,
        width: Int? = null,
        height: Int? = null
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val bitmap = if (width != null && height != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        width,
                        height
                    )
                } else {
                    val original = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (original != null && (original.width != width || original.height != height)) {
                        val scaled = Bitmap.createScaledBitmap(original, width, height, true)
                        if (scaled !== original) {
                            original.recycle()
                        }
                        scaled
                    } else {
                        original
                    }
                }
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } ?: return@withContext Result.failure(
                IllegalStateException("Failed to extract thumbnail")
            )

            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
        }
    }

    /**
     * Check if a URI points to a valid video file.
     */
    suspend fun isValidVideo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val hasVideo = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
            ) == "yes"
            val duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            hasVideo && duration > 0
        } catch (e: Exception) {
            false
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
        }
    }

    companion object {
        // Same value as MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT (API 28+)
        private const val VIDEO_FRAME_COUNT_METADATA_KEY_COMPAT = 32
    }
}
