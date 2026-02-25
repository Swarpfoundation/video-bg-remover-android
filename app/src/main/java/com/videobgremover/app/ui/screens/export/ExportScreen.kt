package com.videobgremover.app.ui.screens.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videobgremover.app.data.repository.ExportDestination
import com.videobgremover.app.data.repository.ExportFormat
import com.videobgremover.app.ui.contract.CreateDocumentContract
import com.videobgremover.app.ui.contract.FolderPickerContract
import com.videobgremover.app.ui.viewmodel.ExportUiState
import com.videobgremover.app.ui.viewmodel.ExportViewModel

/**
 * Export screen for saving processed video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    sourceDir: String,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportViewModel = viewModel(
        factory = ExportViewModel.createFactory(LocalContext.current, sourceDir)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // SAF file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = CreateDocumentContract()
    ) { uri ->
        viewModel.onSafDestinationSelected(uri)
    }

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = FolderPickerContract()
    ) { uri ->
        uri?.let {
            // For folder, we still need a filename, use default
            viewModel.onSafDestinationSelected(it)
        }
    }

    // Show error in snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // Handle destination picker
    LaunchedEffect(uiState.showDestinationPicker) {
        if (uiState.showDestinationPicker) {
            filePickerLauncher.launch("video_bg_removed.zip")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Export Video") },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Storage warning if needed
            if (!uiState.hasEnoughSpace && !uiState.isCheckingSpace) {
                StorageWarningCard(estimatedSize = uiState.estimatedSize)
            }

            // Export format selection
            ExportFormatCard(
                selectedFormat = uiState.exportFormat,
                onFormatSelected = { viewModel.setExportFormat(it) }
            )

            // Destination selection
            DestinationCard(
                selectedDestination = uiState.exportDestination,
                onDestinationSelected = { viewModel.setExportDestination(it) }
            )

            // Progress or result
            if (uiState.exportState.isExporting) {
                ExportProgressCard(
                    progress = uiState.exportState.progress,
                    currentFile = uiState.exportState.currentFile,
                    totalFiles = uiState.exportState.totalFiles
                )
            } else if (uiState.canShare && uiState.outputUri != null) {
                ExportSuccessCard(
                    onShare = {
                        viewModel.createShareIntent()?.let { intent ->
                            context.startActivity(intent)
                        }
                    },
                    onDone = onDone
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            ExportActions(
                isExporting = uiState.exportState.isExporting,
                canShare = uiState.canShare,
                canExport = uiState.hasEnoughSpace && !uiState.isCheckingSpace,
                onExport = { viewModel.startExport() },
                onShare = {
                    viewModel.createShareIntent()?.let { intent ->
                        context.startActivity(intent)
                    }
                },
                onDone = onDone
            )
        }
    }
}

@Composable
private fun StorageWarningCard(estimatedSize: Long) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Column {
                Text(
                    text = "Low Storage Space",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Need ${formatFileSize(estimatedSize)} for export",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ExportFormatCard(
    selectedFormat: ExportFormat,
    onFormatSelected: (ExportFormat) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Export Format",
                style = MaterialTheme.typography.titleMedium
            )

            ExportFormatOption(
                title = "PNG Sequence (ZIP)",
                description = "ZIP file containing PNG frames with alpha transparency. " +
                    "Best for editing software.",
                selected = selectedFormat == ExportFormat.PNG_ZIP,
                onClick = { onFormatSelected(ExportFormat.PNG_ZIP) }
            )

            ExportFormatOption(
                title = "Mask Video (MP4)",
                description = "Grayscale H.264 video for use as luma matte. " +
                    "White = opaque, Black = transparent.",
                selected = selectedFormat == ExportFormat.MASK_MP4,
                onClick = { onFormatSelected(ExportFormat.MASK_MP4) }
            )
        }
    }
}

@Composable
private fun ExportFormatOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled
        )
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                }
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (!enabled) {
                Text(
                    text = "Coming soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DestinationCard(
    selectedDestination: ExportDestination,
    onDestinationSelected: (ExportDestination) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Save Location",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedDestination == ExportDestination.CACHE,
                    onClick = { onDestinationSelected(ExportDestination.CACHE) },
                    label = { Text("App Cache") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                FilterChip(
                    selected = selectedDestination == ExportDestination.SAF,
                    onClick = { onDestinationSelected(ExportDestination.SAF) },
                    label = { Text("Choose Folder") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ExportProgressCard(
    progress: Int,
    currentFile: String,
    totalFiles: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Creating ZIP...",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            )

            if (currentFile.isNotEmpty()) {
                Text(
                    text = "Processing: $currentFile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ExportSuccessCard(
    onShare: () -> Unit,
    onDone: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )

            Text(
                text = "Export Complete!",
                style = MaterialTheme.typography.headlineSmall
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Share")
                }

                OutlinedButton(onClick = onDone) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun ExportActions(
    isExporting: Boolean,
    canShare: Boolean,
    canExport: Boolean,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        when {
            isExporting -> {
                // Show nothing during export
            }

            canShare -> {
                OutlinedButton(onClick = onDone) {
                    Text("Done")
                }
            }

            else -> {
                Button(
                    onClick = onExport,
                    enabled = canExport
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Export")
                }
            }
        }
    }
}

/**
 * Format file size to human readable string.
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000f)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000f)
        else -> "$bytes B"
    }
}
