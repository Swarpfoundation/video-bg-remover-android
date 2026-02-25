package com.videobgremover.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.videobgremover.app.core.performance.AdaptivePerformanceController
import com.videobgremover.app.core.performance.ProcessingQuality
import com.videobgremover.app.core.performance.ProcessingSpec
import com.videobgremover.app.core.performance.QualityOption
import com.videobgremover.app.data.extractor.VideoMetadataExtractor
import com.videobgremover.app.data.repository.ProcessingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State of the processing operation.
 */
enum class ProcessingState {
    IDLE,
    SELECTING_QUALITY, // New: User selecting quality preset
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    BLOCKED
}

/**
 * UI state for the processing screen.
 */
data class ProcessingUiState(
    val state: ProcessingState = ProcessingState.IDLE,
    val progress: Int = 0,
    val framesProcessed: Int = 0,
    val totalFrames: Int = 0,
    val remainingSeconds: Int = 0,
    val status: String = "",
    val outputDir: String? = null,
    val error: String? = null,
    val workId: String? = null,
    // New: Quality selection
    val qualityOptions: List<QualityOption> = emptyList(),
    val selectedQuality: ProcessingQuality = ProcessingQuality.BALANCED,
    val videoDurationMs: Long = 0
)

/**
 * ViewModel for managing video processing UI.
 */
class ProcessingViewModel(
    context: Context,
    private val videoUri: String,
    private val performanceController: AdaptivePerformanceController,
    private val targetFps: Int = DEFAULT_TARGET_FPS,
    private val maxFrames: Int = DEFAULT_MAX_FRAMES
) : ViewModel() {

    private val repository = ProcessingRepository(context.applicationContext)
    private val metadataExtractor = VideoMetadataExtractor(context.applicationContext)

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var workInfoLiveData: androidx.lifecycle.LiveData<WorkInfo?>? = null
    private var workInfoObserver: Observer<WorkInfo?>? = null

    init {
        loadQualityOptions()
    }

    /**
     * Load video metadata and calculate quality options.
     */
    private fun loadQualityOptions() {
        viewModelScope.launch {
            try {
                val uri = Uri.parse(videoUri)
                val metadataResult = metadataExtractor.extract(uri)

                metadataResult.fold(
                    onSuccess = { metadata ->
                        val durationMs = metadata.durationMs
                        val options = performanceController.getQualityOptions(durationMs)

                        _uiState.update {
                            it.copy(
                                state = ProcessingState.SELECTING_QUALITY,
                                qualityOptions = options,
                                videoDurationMs = durationMs
                            )
                        }
                    },
                    onFailure = {
                        // Fallback to default options
                        val options = performanceController.getQualityOptions(30000)
                        _uiState.update {
                            it.copy(
                                state = ProcessingState.SELECTING_QUALITY,
                                qualityOptions = options
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                // Start with default options
                val options = performanceController.getQualityOptions(30000)
                _uiState.update {
                    it.copy(
                        state = ProcessingState.SELECTING_QUALITY,
                        qualityOptions = options
                    )
                }
            }
        }
    }

    /**
     * Select processing quality.
     */
    fun selectQuality(quality: ProcessingQuality) {
        _uiState.update { it.copy(selectedQuality = quality) }
    }

    /**
     * Start processing with selected quality.
     */
    fun startProcessingWithQuality(quality: ProcessingQuality? = null) {
        val selectedQuality = quality ?: _uiState.value.selectedQuality
        val spec = _uiState.value.qualityOptions
            .find { it.quality == selectedQuality }?.spec
            ?: return

        // Calculate actual maxFrames based on spec
        val durationSec = _uiState.value.videoDurationMs / 1000
        val effectiveMaxFrames = ((durationSec * spec.targetFps) / spec.processEveryNFrames).toInt()

        startProcessing(
            targetFps = spec.targetFps,
            maxFrames = effectiveMaxFrames.coerceAtMost(maxFrames),
            processEveryNFrames = spec.processEveryNFrames
        )
    }

    /**
     * Start processing the video.
     */
    private fun startProcessing(
        targetFps: Int = this.targetFps,
        maxFrames: Int = this.maxFrames,
        processEveryNFrames: Int = 1
    ) {
        if (_uiState.value.state == ProcessingState.RUNNING ||
            _uiState.value.state == ProcessingState.ENQUEUED
        ) {
            return // Already processing
        }

        val workId = repository.startProcessing(
            videoUri = videoUri,
            targetFps = targetFps,
            maxFrames = maxFrames
        )

        _uiState.update { it.copy(workId = workId) }

        // Observe work progress
        observeWork(workId)
    }

    /**
     * Observe work progress via LiveData.
     */
    private fun observeWork(workId: String) {
        clearWorkObserver()
        val liveData = repository.getWorkInfo(workId)
        val observer = Observer<WorkInfo?> { workInfo ->
            workInfo?.let { updateStateFromWorkInfo(it) }
        }

        liveData.observeForever(observer)
        workInfoLiveData = liveData
        workInfoObserver = observer
    }

    private fun clearWorkObserver() {
        val liveData = workInfoLiveData
        val observer = workInfoObserver
        if (liveData != null && observer != null) {
            liveData.removeObserver(observer)
        }
        workInfoLiveData = null
        workInfoObserver = null
    }

    /**
     * Update UI state from WorkInfo.
     */
    private fun updateStateFromWorkInfo(workInfo: WorkInfo) {
        val state = when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> ProcessingState.ENQUEUED
            WorkInfo.State.RUNNING -> ProcessingState.RUNNING
            WorkInfo.State.SUCCEEDED -> ProcessingState.SUCCEEDED
            WorkInfo.State.FAILED -> ProcessingState.FAILED
            WorkInfo.State.CANCELLED -> ProcessingState.CANCELLED
            WorkInfo.State.BLOCKED -> ProcessingState.BLOCKED
        }

        val progress = repository.getProgress(workInfo)
        val framesProcessed = repository.getFramesProcessed(workInfo)
        val totalFrames = repository.getTotalFrames(workInfo)
        val status = repository.getStatus(workInfo) ?: ""

        // Calculate estimated remaining time
        val remainingMs = workInfo.progress.getLong(
            com.videobgremover.app.data.worker.VideoProcessingWorker.REMAINING_MS_KEY,
            0
        )
        val remainingSeconds = (remainingMs / 1000).toInt()

        val outputDir = if (state == ProcessingState.SUCCEEDED) {
            repository.getOutputDirectory(workInfo)
        } else {
            null
        }

        val error = if (state == ProcessingState.FAILED) {
            workInfo.outputData.getString(
                com.videobgremover.app.data.worker.VideoProcessingWorker.ERROR_KEY
            )
        } else {
            null
        }

        _uiState.update {
            it.copy(
                state = state,
                progress = progress,
                framesProcessed = framesProcessed,
                totalFrames = totalFrames,
                remainingSeconds = remainingSeconds,
                status = status,
                outputDir = outputDir ?: it.outputDir,
                error = error
            )
        }

        if (state == ProcessingState.SUCCEEDED ||
            state == ProcessingState.FAILED ||
            state == ProcessingState.CANCELLED
        ) {
            clearWorkObserver()
        }
    }

    /**
     * Cancel the current processing.
     */
    fun cancelProcessing() {
        repository.cancelProcessing(videoUri)
    }

    /**
     * Retry processing after failure.
     */
    fun retry() {
        _uiState.update { it.copy(state = ProcessingState.SELECTING_QUALITY) }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        clearWorkObserver()
    }

    companion object {
        private const val DEFAULT_TARGET_FPS = 15
        private const val DEFAULT_MAX_FRAMES = 900

        /**
         * Factory for creating [ProcessingViewModel].
         */
        fun createFactory(
            context: Context,
            videoUri: String,
            targetFps: Int = DEFAULT_TARGET_FPS,
            maxFrames: Int = DEFAULT_MAX_FRAMES
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    val performanceController = AdaptivePerformanceController(context.applicationContext)
                    return ProcessingViewModel(
                        context.applicationContext,
                        videoUri,
                        performanceController,
                        targetFps,
                        maxFrames
                    ) as T
                }
            }
        }
    }
}
