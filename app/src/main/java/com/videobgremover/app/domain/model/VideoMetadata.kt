package com.videobgremover.app.domain.model

/**
 * Metadata for a video file.
 */
data class VideoMetadata(
    val uri: String,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val codec: String?
) {
    val estimatedFrameCount: Int
        get() = ((durationMs / 1000f) * frameRate).toInt()

    val resolution: String
        get() = "${width}x${height}"
}
