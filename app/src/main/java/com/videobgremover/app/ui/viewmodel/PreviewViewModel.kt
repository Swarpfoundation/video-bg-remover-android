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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    context: Context,
    private val videoUri: String
) : ViewModel() {

    private val metadataExtractor = VideoMetadataExtractor(context.applicationContext)
    private val segmentationRepository = SegmentationRepositoryImpl(context.applicationContext)
    private val maskProcessor = MaskProcessor(MaskProcessingConfig())

    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()
    private var pipelineJob: Job? = null
    private var reprocessJob: Job? = null
    private var frameToken: Long = 0

    init {
        loadFrame()
    }

    /**
     * Load a frame from the video and run segmentation.
     */
    private fun loadFrame() {
        val previousPipelineJob = pipelineJob
        val previousReprocessJob = reprocessJob
        val token = ++frameToken

        pipelineJob = viewModelScope.launch {
            previousReprocessJob?.cancelAndJoin()
            previousPipelineJob?.cancelAndJoin()
            _uiState.update { it.copy(isLoading = true, error = null) }

            val uri = Uri.parse(videoUri)

            // Extract frame at 0ms (first frame)
            val frameResult = metadataExtractor.extractThumbnail(
                uri = uri,
                timeUs = 0L,
                width = PREVIEW_WIDTH,
                height = PREVIEW_HEIGHT
            )

            if (frameResult.isSuccess) {
                val bitmap = frameResult.getOrThrow()
                if (!isCurrentFrame(token) || !isScopeActive()) {
                    recycleBitmap(bitmap)
                    return@launch
                }

                replaceOriginalBitmap(bitmap)
                replaceProcessedBitmaps(maskBitmap = null, compositedBitmap = null)
                runSegmentation(bitmap, token)
            } else {
                val error = frameResult.exceptionOrNull()
                if (!isCurrentFrame(token) || !isScopeActive()) return@launch
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load frame: ${error?.message}"
                    )
                }
            }
        }
    }

    /**
     * Run segmentation on the loaded frame.
     */
    private suspend fun runSegmentation(bitmap: Bitmap, token: Long) {
        if (!isCurrentFrame(token) || !isScopeActive()) return

        _uiState.update { it.copy(isProcessing = true) }

        // Initialize segmenter if needed
        if (!segmentationRepository.initialize().isSuccess) {
            if (!isCurrentFrame(token) || !isScopeActive()) return
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isProcessing = false,
                    error = "Failed to initialize segmentation model"
                )
            }
            return
        }

        // Run segmentation
        val result = segmentationRepository.segmentFrame(bitmap)
        if (!isCurrentFrame(token) || !isScopeActive()) return

        if (result.isSuccess) {
            val segmentationMask = result.getOrThrow()
            val confidenceMask = MaskProcessor.normalizeToFrameSize(
                segmentationMask,
                bitmap.width,
                bitmap.height
            )
            processMask(confidenceMask, bitmap, token)
        } else {
            val error = result.exceptionOrNull()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isProcessing = false,
                    error = "Segmentation failed: ${error?.message}"
                )
            }
        }
    }

    /**
     * Process the raw confidence mask and generate preview bitmaps.
     */
    private suspend fun processMask(confidenceMask: FloatArray, sourceBitmap: Bitmap, token: Long) {
        try {
            val (maskVis, composited) = withContext(Dispatchers.Default) {
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

                maskVis to composited
            }

            if (!isCurrentFrame(token) || !isScopeActive()) {
                recycleBitmap(maskVis)
                recycleBitmap(composited)
                return
            }

            replaceProcessedBitmaps(maskBitmap = maskVis, compositedBitmap = composited)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isProcessing = false
                )
            }
        } catch (e: Exception) {
            if (!isCurrentFrame(token) || !isScopeActive()) return
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isProcessing = false,
                    error = "Mask processing failed: ${e.message}"
                )
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
     * Apply spot removal preset.
     * Aggressively removes isolated spots and fills holes.
     */
    fun applySpotRemovalPreset() {
        // Create new processor with aggressive spot removal
        val spotRemovalConfig = MaskProcessingConfig(
            threshold = 0.55f,
            useTemporalSmoothing = true,
            temporalAlpha = 0.3f,
            useHysteresisThreshold = true,
            hysteresisDelta = 0.12f,
            applyMorphology = true,
            morphologyRadius = 3,
            applyNeighborhoodConsensusFilter = true,
            consensusPasses = 2,
            applyFeather = true,
            featherRadius = 2f,
            removeSpeckles = true,
            speckleMinSize = 100,  // Remove smaller components
            speckleMaxHole = 200   // Fill larger holes
        )

        startReprocess(spotRemovalConfig)
    }

    /**
     * Apply anti-flicker preset to stabilize edges.
     * Increases temporal smoothing and morphology.
     */
    fun applyAntiFlickerPreset() {
        // Create new processor with anti-flicker settings
        val antiFlickerConfig = MaskProcessingConfig(
            threshold = 0.5f,
            useTemporalSmoothing = true,
            temporalAlpha = 0.45f, // Stronger smoothing (default 0.3)
            useHysteresisThreshold = true,
            hysteresisDelta = 0.12f,
            applyMorphology = true,
            morphologyRadius = 3, // Larger radius (default 2)
            applyNeighborhoodConsensusFilter = true,
            consensusPasses = 2,
            applyFeather = true,
            featherRadius = 4f, // Slightly more feather (default 3)
            removeSpeckles = true,
            speckleMinSize = 30,
            speckleMaxHole = 50
        )

        startReprocess(antiFlickerConfig)
    }

    private fun startReprocess(config: MaskProcessingConfig) {
        val previousReprocessJob = reprocessJob
        val token = frameToken

        reprocessJob = viewModelScope.launch {
            previousReprocessJob?.cancelAndJoin()
            if (!isCurrentFrame(token) || !isScopeActive()) return@launch

            _uiState.update { it.copy(isProcessing = true) }
            reprocessWithConfig(config, token)
        }
    }

    /**
     * Re-process the frame with a specific config.
     */
    private suspend fun reprocessWithConfig(config: MaskProcessingConfig, token: Long) {
        // Replace the processor
        maskProcessor.reset()
        val newProcessor = MaskProcessor(config)

        // Get the original bitmap and re-process
        val originalBitmap = _uiState.value.originalBitmap ?: run {
            _uiState.update { it.copy(isProcessing = false) }
            return
        }

        // Re-run segmentation with new settings
        val result = segmentationRepository.segmentFrame(originalBitmap)
        if (!isCurrentFrame(token) || !isScopeActive()) return

        if (result.isSuccess) {
            val segmentationMask = result.getOrThrow()
            try {
                val (maskVis, composited) = withContext(Dispatchers.Default) {
                    val confidenceMask = MaskProcessor.normalizeToFrameSize(
                        segmentationMask,
                        originalBitmap.width,
                        originalBitmap.height
                    )

                    // Process mask with new settings
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

                    maskVis to composited
                }

                if (!isCurrentFrame(token) || !isScopeActive()) {
                    recycleBitmap(maskVis)
                    recycleBitmap(composited)
                    return
                }

                replaceProcessedBitmaps(maskBitmap = maskVis, compositedBitmap = composited)

                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                if (!isCurrentFrame(token) || !isScopeActive()) return
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Processing failed: ${e.message}"
                    )
                }
            }
        } else {
            val error = result.exceptionOrNull()
            if (!isCurrentFrame(token) || !isScopeActive()) return
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    error = "Failed to process: ${error?.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pipelineJob?.cancel()
        reprocessJob?.cancel()
        segmentationRepository.close()
        recycleBitmap(_uiState.value.originalBitmap)
        recycleBitmap(_uiState.value.maskBitmap)
        recycleBitmap(_uiState.value.compositedBitmap)
    }

    private fun isCurrentFrame(token: Long): Boolean = token == frameToken

    private fun isScopeActive(): Boolean = viewModelScope.coroutineContext.isActive

    private fun replaceOriginalBitmap(originalBitmap: Bitmap?) {
        var oldBitmap: Bitmap? = null
        _uiState.update { current ->
            oldBitmap = current.originalBitmap
            current.copy(originalBitmap = originalBitmap)
        }
        if (oldBitmap !== originalBitmap) {
            recycleBitmap(oldBitmap)
        }
    }

    private fun replaceProcessedBitmaps(maskBitmap: Bitmap?, compositedBitmap: Bitmap?) {
        var oldMask: Bitmap? = null
        var oldComposited: Bitmap? = null
        _uiState.update { current ->
            oldMask = current.maskBitmap
            oldComposited = current.compositedBitmap
            current.copy(
                maskBitmap = maskBitmap,
                compositedBitmap = compositedBitmap
            )
        }
        if (oldMask !== maskBitmap) {
            recycleBitmap(oldMask)
        }
        if (oldComposited !== compositedBitmap) {
            recycleBitmap(oldComposited)
        }
    }

    private fun recycleBitmap(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            runCatching { bitmap.recycle() }
        }
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
