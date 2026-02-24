package com.videobgremover.app.domain.repository

import android.graphics.Bitmap

/**
 * Repository interface for image segmentation operations.
 */
interface SegmentationRepository {
    /**
     * Initialize the segmentation model.
     */
    suspend fun initialize(): Result<Unit>

    /**
     * Segment a single frame and return the confidence mask.
     * @return FloatArray of confidence values (0.0 to 1.0) or null on error
     */
    suspend fun segmentFrame(bitmap: Bitmap): Result<FloatArray>

    /**
     * Release resources.
     */
    fun close()
}
