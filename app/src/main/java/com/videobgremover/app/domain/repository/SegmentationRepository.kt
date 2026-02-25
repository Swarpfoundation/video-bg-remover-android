package com.videobgremover.app.domain.repository

import android.graphics.Bitmap
import com.videobgremover.app.domain.model.SegmentationMask

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
     * Returns mask values plus the model output dimensions.
     */
    suspend fun segmentFrame(bitmap: Bitmap): Result<SegmentationMask>

    /**
     * Release resources.
     */
    fun close()
}
