package com.videobgremover.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import com.videobgremover.app.core.Logger
import com.videobgremover.app.domain.model.SegmentationMask
import com.videobgremover.app.domain.repository.SegmentationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

/**
 * Implementation of [SegmentationRepository] using MediaPipe Image Segmenter.
 *
 * Uses the selfie segmentation model to generate person masks.
 * Supports both CPU and GPU acceleration.
 */
class SegmentationRepositoryImpl(
    context: Context,
    useGpu: Boolean = true
) : SegmentationRepository {

    private val applicationContext = context.applicationContext
    private var imageSegmenter: ImageSegmenter? = null
    private var isInitialized = false
    private val gpuSupported = useGpu && isGpuSupported()

    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)

            // Use GPU if available for 2-5x speed improvement
            if (gpuSupported) {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
                Logger.d("Using GPU acceleration for segmentation")
            } else {
                Logger.d("Using CPU for segmentation")
            }

            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(true)
                .build()

            imageSegmenter = ImageSegmenter.createFromOptions(applicationContext, options)
            isInitialized = true

            Logger.d("MediaPipe ImageSegmenter initialized successfully (GPU: $gpuSupported)")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("Failed to initialize MediaPipe ImageSegmenter", e)
            // Fallback to CPU if GPU fails
            if (gpuSupported) {
                Logger.d("Falling back to CPU initialization")
                return@withContext initializeCpuFallback()
            }
            Result.failure(e)
        }
    }

    private suspend fun initializeCpuFallback(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_PATH)
                .setDelegate(Delegate.CPU)
                .build()

            val options = ImageSegmenter.ImageSegmenterOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(true)
                .build()

            imageSegmenter = ImageSegmenter.createFromOptions(applicationContext, options)
            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun segmentFrame(bitmap: Bitmap): Result<SegmentationMask> =
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
                val masks = result.confidenceMasks().orElse(emptyList())
                if (masks.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("No segmentation masks returned")
                    )
                }

                // Get the first mask (person)
                val mask = masks.first()
                val maskWidth = mask.getWidth()
                val maskHeight = mask.getHeight()
                val expectedSize = maskWidth * maskHeight

                // Convert to FloatArray
                val byteBuffer = ByteBufferExtractor.extract(mask)
                    .duplicate()
                    .order(ByteOrder.nativeOrder())
                byteBuffer.rewind()
                val floatBuffer = byteBuffer.asFloatBuffer()
                if (floatBuffer.remaining() < expectedSize) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            "Confidence mask buffer too small: ${floatBuffer.remaining()} < $expectedSize"
                        )
                    )
                }
                val floatArray = FloatArray(expectedSize)
                floatBuffer.get(floatArray)

                Result.success(
                    SegmentationMask(
                        values = floatArray,
                        width = maskWidth,
                        height = maskHeight
                    )
                )
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

    /**
     * Check if GPU acceleration is supported on this device.
     */
    private fun isGpuSupported(): Boolean {
        return try {
            // Check for OpenGL ES 3.1 or higher
            val activityManager = applicationContext.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
            val configurationInfo = activityManager.deviceConfigurationInfo
            val supportsEs31 = configurationInfo.reqGlEsVersion >= 0x00030001

            Logger.d("GPU support check: OpenGL ES 3.1=$supportsEs31")
            supportsEs31
        } catch (e: Exception) {
            Logger.w("Failed to check GPU support, defaulting to false", e)
            false
        }
    }

    companion object {
        private const val MODEL_PATH = "selfie_segmenter.tflite"
    }
}
