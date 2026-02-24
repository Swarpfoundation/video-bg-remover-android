package com.videobgremover.app.data.repository

import android.graphics.Bitmap
import com.videobgremover.app.domain.repository.SegmentationRepository

/**
 * Implementation of [SegmentationRepository] using MediaPipe.
 */
class SegmentationRepositoryImpl : SegmentationRepository {
    override suspend fun initialize(): Result<Unit> {
        // TODO: Implement in Step 3
        return Result.success(Unit)
    }

    override suspend fun segmentFrame(bitmap: Bitmap): Result<FloatArray> {
        // TODO: Implement in Step 3
        return Result.failure(NotImplementedError("Segmentation not yet implemented"))
    }

    override fun close() {
        // TODO: Implement in Step 3
    }
}
