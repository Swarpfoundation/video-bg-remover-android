package com.videobgremover.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.videobgremover.app.core.processing.MaskProcessingConfig
import com.videobgremover.app.core.processing.MaskProcessor
import com.videobgremover.app.data.extractor.VideoMetadataExtractor
import com.videobgremover.app.data.repository.SegmentationRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Preview mode for the segmentation display.
 */
enum class PreviewMode {
    ORIGINAL,      // Show original frame
    MASK,          // Show grayscale mask
    COMPOSITED     // Show RGBA with transparency
}

/**
 * State for the preview screen.
 */
data class PreviewUiState(
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val originalBitmap: Bitmap? = null,
    val maskBitmap: Bitmap? = null,
    val compositedBitmap: Bitmap? = null,
    val previewMode: PreviewMode = PreviewMode.COMPOSITED,
    val error: String? = null,
    val hasPermission: Boolean = false
)

/**
 * ViewModel for previewing segmentation on a single frame.
 */
class PreviewViewModel(
    private val context: Context,
    private val videoUri: String
) : ViewModel() {

    private val metadataExtractor = VideoMetadataExtractor(context)
    private val segmentationRepository = SegmentationRepositoryImpl(context)
    private val maskProcessor = MaskProcessor(MaskProcessingConfig())

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    init {
        loadFrame()
    }

    /**
     * Load a frame from the video and run segmentation.
     */
    private fun loadFrame() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val uri = Uri.parse(videoUri)

            // Extract frame at 0ms (first frame)
            val frameResult = metadataExtractor.extractThumbnail(
                uri = uri,
                timeUs = 0L,
                width = PREVIEW_WIDTH,
                height = PREVIEW_HEIGHT
            )

            frameResult.fold(
                onSuccess = { bitmap ->
                    _uiState.update { it.copy(originalBitmap = bitmap) }
                    runSegmentation(bitmap)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load frame: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Run segmentation on the loaded frame.
     */
    private fun runSegmentation(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            // Initialize segmenter if needed
            if (!segmentationRepository.initialize().isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isProcessing = false,
                        error = "Failed to initialize segmentation model"
                    )
                }
                return@launch
            }

            // Run segmentation
            val result = segmentationRepository.segmentFrame(bitmap)

            result.fold(
                onSuccess = { confidenceMask ->
                    processMask(confidenceMask, bitmap)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isProcessing = false,
                            error = "Segmentation failed: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Process the raw confidence mask and generate preview bitmaps.
     */
    private fun processMask(confidenceMask: FloatArray, sourceBitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Process mask
                val processedMask = maskProcessor.processMask(
                    confidenceMask,
                    sourceBitmap.width,
                    sourceBitmap.height
                )

                // Create mask visualization
                val maskVis = maskProcessor.createMaskVisualization(
                    processedMask,
                    sourceBitmap.width,
                    sourceBitmap.height
                )

                // Create composited image with alpha
                val composited = maskProcessor.composeWithAlpha(
                    sourceBitmap,
                    processedMask,
                    sourceBitmap.width,
                    sourceBitmap.height
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isProcessing = false,
                        maskBitmap = maskVis,
                        compositedBitmap = composited
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isProcessing = false,
                        error = "Mask processing failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Change the preview mode.
     */
    fun setPreviewMode(mode: PreviewMode) {
        _uiState.update { it.copy(previewMode = mode) }
    }

    /**
     * Toggle between preview modes.
     */
    fun cyclePreviewMode() {
        val nextMode = when (_uiState.value.previewMode) {
            PreviewMode.ORIGINAL -> PreviewMode.MASK
            PreviewMode.MASK -> PreviewMode.COMPOSITED
            PreviewMode.COMPOSITED -> PreviewMode.ORIGINAL
        }
        setPreviewMode(nextMode)
    }

    /**
     * Update permission state.
     */
    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasPermission = granted) }
    }

    /**
     * Clear any error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Reload the frame with fresh segmentation.
     */
    fun refresh() {
        // Reset temporal smoothing when refreshing
        maskProcessor.reset()
        loadFrame()
    }

    /**
     * Apply anti-flicker preset to stabilize edges.
     * Increases temporal smoothing and morphology.
     */
    fun applyAntiFlickerPreset() {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }

            // Create new processor with anti-flicker settings
            val antiFlickerConfig = MaskProcessingConfig(
                useTemporalSmoothing = true,
                temporalAlpha = 0.5f, // Stronger smoothing (default 0.3)
                applyMorphology = true,
                morphologyRadius = 3, // Larger radius (default 2)
                applyFeather = true,
                featherRadius = 4f // Slightly more feather (default 3)
            )

            // Replace the processor
            maskProcessor.reset()
            val newProcessor = MaskProcessor(antiFlickerConfig)

            // Get the original bitmap and re-process
            val originalBitmap = _uiState.value.originalBitmap ?: run {
                _uiState.update { it.copy(isProcessing = false) }
                return@launch
            }

            // Re-run segmentation with new settings
            val result = segmentationRepository.segmentFrame(originalBitmap)

            result.fold(
                onSuccess = { confidenceMask ->
                    try {
                        // Process mask with anti-flicker settings
                        val processedMask = newProcessor.processMask(
                            confidenceMask,
                            originalBitmap.width,
                            originalBitmap.height
                        )

                        // Create new visualizations
                        val maskVis = newProcessor.createMaskVisualization(
                            processedMask,
                            originalBitmap.width,
                            originalBitmap.height
                        )

                        val composited = newProcessor.composeWithAlpha(
                            originalBitmap,
                            processedMask,
                            originalBitmap.width,
                            originalBitmap.height
                        )

                        // Clean up old bitmaps
                        _uiState.value.maskBitmap?.recycle()
                        _uiState.value.compositedBitmap?.recycle()

                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                maskBitmap = maskVis,
                                compositedBitmap = composited
                            )
                        }
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                error = "Anti-flicker processing failed: ${e.message}"
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            error = "Failed to apply anti-flicker: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        segmentationRepository.close()
        _uiState.value.originalBitmap?.recycle()
        _uiState.value.maskBitmap?.recycle()
        _uiState.value.compositedBitmap?.recycle()
    }

    companion object {
        private const val PREVIEW_WIDTH = 512
        private const val PREVIEW_HEIGHT = 512

        /**
         * Factory for creating [PreviewViewModel].
         */
        fun createFactory(context: Context, videoUri: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PreviewViewModel(context.applicationContext, videoUri) as T
                }
            }
        }
    }
}
