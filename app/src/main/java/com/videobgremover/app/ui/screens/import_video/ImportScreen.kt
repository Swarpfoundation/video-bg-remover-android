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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
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
import com.videobgremover.app.core.quality.QualityIssue
import com.videobgremover.app.ui.components.MetadataDisplay
import com.videobgremover.app.ui.components.VideoThumbnail
import com.videobgremover.app.ui.contract.VideoPickerContract
import com.videobgremover.app.ui.viewmodel.ImportViewModel
import com.videobgremover.app.ui.viewmodel.QualityScore

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

                    // Quality Analysis
                    if (uiState.isAnalyzingQuality) {
                        QualityAnalysisLoading()
                    } else if (uiState.qualityIssues.isNotEmpty()) {
                        QualityWarningsCard(
                            score = uiState.qualityScore,
                            issues = uiState.qualityIssues
                        )
                    } else if (uiState.videoMetadata != null && !uiState.isLoading) {
                        QualityGoodCard()
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
private fun QualityAnalysisLoading() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
            Text(
                text = "Analyzing video quality...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QualityGoodCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "Great for processing!",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "This video should produce excellent results.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun QualityWarningsCard(
    score: QualityScore,
    issues: List<QualityIssue>
) {
    val (containerColor, icon, title, description) = when (score) {
        QualityScore.EXCELLENT -> Quadruple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Default.CheckCircle,
            "Great for processing!",
            "This video should produce excellent results."
        )
        QualityScore.GOOD -> Quadruple(
            MaterialTheme.colorScheme.secondaryContainer,
            Icons.Default.Info,
            "Good for processing",
            "Minor issues detected, but results should be good."
        )
        QualityScore.FAIR -> Quadruple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Default.Warning,
            "Fair quality",
            "Some issues may affect results. See suggestions below."
        )
        QualityScore.POOR -> Quadruple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Warning,
            "Challenging conditions",
            "Multiple issues detected. Consider retaking the video."
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when (score) {
                        QualityScore.EXCELLENT -> MaterialTheme.colorScheme.primary
                        QualityScore.GOOD -> MaterialTheme.colorScheme.secondary
                        QualityScore.FAIR -> MaterialTheme.colorScheme.tertiary
                        QualityScore.POOR -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Issue chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                issues.forEach { issue ->
                    QualityIssueChip(issue = issue)
                }
            }
        }
    }
}

@Composable
private fun QualityIssueChip(issue: QualityIssue) {
    val (icon, containerColor) = when (issue.severity) {
        QualityIssue.Severity.INFO -> Pair(
            Icons.Default.Info,
            MaterialTheme.colorScheme.surface
        )
        QualityIssue.Severity.WARNING -> Pair(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.tertiaryContainer
        )
        QualityIssue.Severity.CRITICAL -> Pair(
            Icons.Default.Warning,
            MaterialTheme.colorScheme.errorContainer
        )
    }

    InputChip(
        selected = false,
        onClick = { /* Show tooltip or dialog with suggestion */ },
        label = { Text(issue.message) },
        avatar = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

// Helper data class for 4 values
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

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
