package com.videobgremover.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import com.videobgremover.app.core.Logger
import com.videobgremover.app.domain.repository.SegmentationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of [SegmentationRepository] using MediaPipe Image Segmenter.
 *
 * Uses the selfie segmentation model to generate person masks.
 */
class SegmentationRepositoryImpl(
    private val context: Context
) : SegmentationRepository {

    private var imageSegmenter: ImageSegmenter? = null
    private var isInitialized = false

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .build()

            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(true)
                .build()

            imageSegmenter = ImageSegmenter.createFromOptions(context, options)
            isInitialized = true

            Logger.d("MediaPipe ImageSegmenter initialized successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("Failed to initialize MediaPipe ImageSegmenter", e)
            Result.failure(e)
        }
    }

    override suspend fun segmentFrame(bitmap: Bitmap): Result<FloatArray> =
        withContext(Dispatchers.Default) {
            if (!isInitialized) {
                initialize().onFailure {
                    return@withContext Result.failure(it)
                }
            }

            val segmenter = imageSegmenter
                ?: return@withContext Result.failure(
                    IllegalStateException("ImageSegmenter not initialized")
                )

            try {
                // Convert bitmap to MediaPipe Image
                val mpImage = BitmapImageBuilder(bitmap).build()

                // Run segmentation
                val result: ImageSegmenterResult = segmenter.segment(mpImage)

                // Extract confidence mask (first mask is person/background)
                val masks = result.confidenceMasks()
                if (masks.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("No segmentation masks returned")
                    )
                }

                // Get the first mask (person)
                val mask = masks.first()
                val maskWidth = mask.width
                val maskHeight = mask.height

                // Convert to FloatArray
                val floatArray = FloatArray(maskWidth * maskHeight)
                mask.get(floatArray)

                Result.success(floatArray)
            } catch (e: Exception) {
                Logger.e("Segmentation failed", e)
                Result.failure(e)
            }
        }

    override fun close() {
        try {
            imageSegmenter?.close()
            imageSegmenter = null
            isInitialized = false
            Logger.d("MediaPipe ImageSegmenter closed")
        } catch (e: Exception) {
            Logger.e("Error closing ImageSegmenter", e)
        }
    }

    companion object {
        private const val MODEL_PATH = "selfie_segmenter.tflite"
    }
}
