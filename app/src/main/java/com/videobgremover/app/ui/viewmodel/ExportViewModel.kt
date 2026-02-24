package com.videobgremover.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.videobgremover.app.data.repository.ExportDestination
import com.videobgremover.app.data.repository.ExportFormat
import com.videobgremover.app.data.repository.ExportRepository
import com.videobgremover.app.data.repository.ExportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * UI state for the export screen.
 */
data class ExportUiState(
    val exportFormat: ExportFormat = ExportFormat.PNG_ZIP,
    val exportDestination: ExportDestination = ExportDestination.CACHE,
    val isCheckingSpace: Boolean = false,
    val hasEnoughSpace: Boolean = true,
    val estimatedSize: Long = 0,
    val actualSize: Long = 0,
    val exportState: ExportState = ExportState(),
    val outputUri: Uri? = null,
    val canShare: Boolean = false,
    val error: String? = null,
    val showDestinationPicker: Boolean = false
)

/**
 * ViewModel for managing export operations.
 */
class ExportViewModel(
    private val context: Context,
    private val sourceDir: String
) : ViewModel() {

    private val repository = ExportRepository(context)
    private val sourceDirFile = File(sourceDir)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        checkStorageSpace()
    }

    /**
     * Check available storage space.
     */
    private fun checkStorageSpace() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingSpace = true) }

            val estimatedSize = repository.estimateExportSize(sourceDirFile)
            val hasSpace = repository.hasEnoughSpace(estimatedSize)

            _uiState.update {
                it.copy(
                    isCheckingSpace = false,
                    estimatedSize = estimatedSize,
                    hasEnoughSpace = hasSpace,
                    error = if (!hasSpace) "Insufficient storage space" else null
                )
            }
        }
    }

    /**
     * Set the export format.
     */
    fun setExportFormat(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    /**
     * Set the export destination.
     */
    fun setExportDestination(destination: ExportDestination) {
        _uiState.update {
            it.copy(
                exportDestination = destination,
                showDestinationPicker = destination == ExportDestination.SAF
            )
        }

        if (destination == ExportDestination.SAF) {
            // Will trigger picker in UI
        } else if (destination == ExportDestination.CACHE) {
            startExport()
        }
    }

    /**
     * Handle SAF destination selection.
     */
    fun onSafDestinationSelected(uri: Uri?) {
        _uiState.update { it.copy(showDestinationPicker = false) }

        if (uri != null) {
            startExport(safUri = uri)
        }
    }

    /**
     * Start the export process.
     */
    fun startExport(safUri: Uri? = null) {
        if (_uiState.value.exportState.isExporting) return

        val format = _uiState.value.exportFormat

        when (format) {
            ExportFormat.PNG_ZIP -> exportAsZip(safUri)
            ExportFormat.MASK_MP4 -> exportAsMaskMp4(safUri)
        }
    }

    /**
     * Export as mask MP4 video.
     */
    private fun exportAsMaskMp4(safUri: Uri? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = it.exportState.copy(isExporting = true)) }

            val state = repository.exportAsMaskMp4(sourceDirFile, safUri)

            _uiState.update {
                it.copy(
                    exportState = state,
                    outputUri = state.outputUri,
                    canShare = state.outputUri != null && !state.isExporting
                )
            }

            // Copy to SAF if needed
            if (state.outputUri != null &&
                _uiState.value.exportDestination == ExportDestination.SAF &&
                safUri != null
            ) {
                val file = File(state.outputUri.path ?: return@launch)
                repository.copyToSafDestination(file, safUri)
            }
        }
    }

    /**
     * Export as ZIP file.
     */
    private fun exportAsZip(safUri: Uri? = null) {
        repository.exportAsZip(sourceDirFile, safUri)
            .onEach { state ->
                _uiState.update {
                    it.copy(
                        exportState = state,
                        outputUri = state.outputUri,
                        canShare = state.outputUri != null && !state.isExporting
                    )
                }

                // If we have a URI and SAF destination, copy to SAF
                if (state.outputUri != null &&
                    !state.isExporting &&
                    _uiState.value.exportDestination == ExportDestination.SAF &&
                    safUri != null
                ) {
                    copyToSaf(state.outputUri, safUri)
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Copy exported file to SAF destination.
     */
    private fun copyToSaf(sourceUri: Uri, destinationUri: Uri) {
        viewModelScope.launch {
            // Get the actual file from content URI
            val file = File(sourceUri.path ?: return@launch)

            repository.copyToSafDestination(file, destinationUri)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            outputUri = it,
                            canShare = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = "Failed to save: ${error.message}")
                    }
                }
        }
    }

    /**
     * Save to MediaStore.
     */
    fun saveToMediaStore(fileName: String) {
        viewModelScope.launch {
            val currentUri = _uiState.value.outputUri ?: return@launch
            val file = File(currentUri.path ?: return@launch)

            _uiState.update { it.copy(exportState = it.exportState.copy(isExporting = true)) }

            repository.saveToMediaStore(file, fileName)
                .onSuccess { uri ->
                    _uiState.update {
                        it.copy(
                            exportState = it.exportState.copy(isExporting = false),
                            outputUri = uri
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            exportState = it.exportState.copy(isExporting = false),
                            error = "Failed to save: ${error.message}"
                        )
                    }
                }
        }
    }

    /**
     * Create a share intent for the exported file.
     */
    fun createShareIntent(): Intent? {
        val uri = _uiState.value.outputUri ?: return null
        return repository.createShareChooserIntent(uri)
    }

    /**
     * Get metadata about the processed video.
     */
    fun getMetadata(): Map<String, Any>? {
        return repository.getProcessingMetadata(sourceDirFile)
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clean up temporary files.
     */
    fun cleanup() {
        viewModelScope.launch {
            repository.cleanupOutputDir(sourceDirFile)
        }
    }

    companion object {
        /**
         * Factory for creating [ExportViewModel].
         */
        fun createFactory(context: Context, sourceDir: String): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ExportViewModel(context.applicationContext, sourceDir) as T
                }
            }
        }
    }
}
