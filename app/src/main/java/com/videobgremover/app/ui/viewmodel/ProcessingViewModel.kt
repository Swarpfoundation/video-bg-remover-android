package com.videobgremover.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
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
    val workId: String? = null
)

/**
 * ViewModel for managing video processing UI.
 */
class ProcessingViewModel(
    private val context: Context,
    private val videoUri: String,
    private val targetFps: Int = DEFAULT_TARGET_FPS,
    private val maxFrames: Int = DEFAULT_MAX_FRAMES
) : ViewModel() {

    private val repository = ProcessingRepository(context)

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var workInfoLiveData: LiveData<WorkInfo?>? = null

    /**
     * Start processing the video.
     */
    fun startProcessing() {
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
        // Remove previous observer if exists
        workInfoLiveData?.let { /* cleanup handled by MediatorLiveData pattern */ }

        val liveData = repository.getWorkInfo(workId)

        liveData.observeForever { workInfo ->
            workInfo?.let { updateStateFromWorkInfo(it) }
        }

        workInfoLiveData = liveData
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
        _uiState.update { ProcessingUiState() }
        startProcessing()
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        // Note: We don't cancel the work when ViewModel is cleared
        // The work should continue in background
        workInfoLiveData?.removeObserver { }
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
                    return ProcessingViewModel(
                        context.applicationContext,
                        videoUri,
                        targetFps,
                        maxFrames
                    ) as T
                }
            }
        }
    }
}
