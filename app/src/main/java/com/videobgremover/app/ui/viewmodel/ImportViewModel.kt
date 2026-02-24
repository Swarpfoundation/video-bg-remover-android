package com.videobgremover.app.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.videobgremover.app.data.extractor.VideoMetadataExtractor
import com.videobgremover.app.domain.model.VideoMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the import screen.
 */
data class ImportUiState(
    val isLoading: Boolean = false,
    val videoMetadata: VideoMetadata? = null,
    val thumbnail: Bitmap? = null,
    val error: String? = null,
    val hasPermission: Boolean = false
)

/**
 * ViewModel for handling video import operations.
 */
class ImportViewModel(
    private val metadataExtractor: VideoMetadataExtractor
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    /**
     * Called when a video is selected from the picker.
     */
    fun onVideoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Validate the video first
            val isValid = metadataExtractor.isValidVideo(uri)
            if (!isValid) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Selected file is not a valid video"
                    )
                }
                return@launch
            }

            // Extract metadata
            val metadataResult = metadataExtractor.extract(uri)

            metadataResult.fold(
                onSuccess = { metadata ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            videoMetadata = metadata
                        )
                    }

                    // Extract thumbnail separately
                    extractThumbnail(uri)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to extract video metadata"
                        )
                    }
                }
            )
        }
    }

    /**
     * Extract thumbnail for the selected video.
     */
    private fun extractThumbnail(uri: Uri) {
        viewModelScope.launch {
            val thumbnailResult = metadataExtractor.extractThumbnail(
                uri = uri,
                width = THUMBNAIL_WIDTH,
                height = THUMBNAIL_HEIGHT
            )

            thumbnailResult.fold(
                onSuccess = { bitmap ->
                    _uiState.update { it.copy(thumbnail = bitmap) }
                },
                onFailure = {
                    // Thumbnail is optional, don't show error
                }
            )
        }
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
     * Clear the current selection.
     */
    fun clearSelection() {
        _uiState.update {
            ImportUiState(hasPermission = it.hasPermission)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up bitmap
        _uiState.value.thumbnail?.recycle()
    }

    companion object {
        private const val THUMBNAIL_WIDTH = 512
        private const val THUMBNAIL_HEIGHT = 512

        /**
         * Factory for creating [ImportViewModel].
         */
        fun createFactory(context: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val extractor = VideoMetadataExtractor(context.applicationContext)
                    return ImportViewModel(extractor) as T
                }
            }
        }
    }
}
