package com.videobgremover.app.ui.screens.import_video

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videobgremover.app.core.permission.VideoPermissionHandler
import com.videobgremover.app.core.permission.rememberVideoPermissionLauncher
import com.videobgremover.app.ui.components.MetadataDisplay
import com.videobgremover.app.ui.components.VideoThumbnail
import com.videobgremover.app.ui.contract.VideoPickerContract
import com.videobgremover.app.ui.viewmodel.ImportViewModel

/**
 * Import screen for selecting and previewing videos.
 */
@Composable
fun ImportScreen(
    onVideoImported: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.createFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Permission launcher
    val permissionLauncher = rememberVideoPermissionLauncher { granted ->
        viewModel.onPermissionResult(granted)
    }

    // Video picker launcher
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = VideoPickerContract()
    ) { uri ->
        uri?.let { viewModel.onVideoSelected(it) }
    }

    // Check permission on first launch
    LaunchedEffect(Unit) {
        if (!VideoPermissionHandler.hasPermission(context)) {
            permissionLauncher.launch(VideoPermissionHandler.getRequiredPermission())
        } else {
            viewModel.onPermissionResult(true)
        }
    }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Import Video",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        },
        floatingActionButton = {
            if (uiState.videoMetadata != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        uiState.videoMetadata?.uri?.let { onVideoImported(it) }
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null
                        )
                    },
                    text = { Text("Continue") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Import Button or Video Preview
            when {
                uiState.videoMetadata == null -> {
                    ImportPlaceholder(
                        isLoading = uiState.isLoading,
                        onClick = { videoPickerLauncher.launch(VideoPickerContract.VIDEO_MIME_TYPES) },
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> {
                    // Video thumbnail
                    VideoThumbnail(
                        thumbnail = uiState.thumbnail,
                        isLoading = uiState.isLoading && uiState.thumbnail == null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    )

                    // Metadata card
                    uiState.videoMetadata?.let { metadata ->
                        MetadataDisplay(
                            metadata = metadata,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Change video button
                    TextButton(
                        onClick = { viewModel.clearSelection() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Select different video")
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // Permission denied dialog
    if (!uiState.hasPermission && !uiState.isLoading) {
        PermissionDeniedDialog(
            onDismiss = { /* Keep showing until granted */ },
            onRequestPermission = {
                permissionLauncher.launch(VideoPermissionHandler.getRequiredPermission())
            }
        )
    }
}

@Composable
private fun ImportPlaceholder(
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoFile,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap to import video",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "MP4, MKV, WebM, and other formats supported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionDeniedDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permission Required") },
        text = {
            Text(
                "This app needs access to your videos to process them. " +
                    "Please grant the permission to continue."
            )
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
