package com.videobgremover.app.ui.screens.processing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videobgremover.app.core.performance.ProcessingQuality
import com.videobgremover.app.core.performance.QualityOption
import com.videobgremover.app.ui.viewmodel.ProcessingState
import com.videobgremover.app.ui.viewmodel.ProcessingViewModel

/**
 * Processing screen with progress tracking and controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingScreen(
    videoUri: String,
    onBack: () -> Unit,
    onComplete: (outputDir: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProcessingViewModel = viewModel(
        factory = ProcessingViewModel.createFactory(LocalContext.current, videoUri)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate to export on success
    LaunchedEffect(uiState.state, uiState.outputDir) {
        if (uiState.state == ProcessingState.SUCCEEDED && uiState.outputDir != null) {
            // Optional: auto-navigate after success
            // onComplete(uiState.outputDir!!)
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
            TopAppBar(
                title = { Text("Processing Video") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = uiState.state,
                    label = "processing_state"
                ) { state ->
                    when (state) {
                        ProcessingState.IDLE,
                        ProcessingState.SELECTING_QUALITY -> {
                            QualitySelectorContent(
                                options = uiState.qualityOptions,
                                selectedQuality = uiState.selectedQuality,
                                onQualitySelected = { viewModel.selectQuality(it) },
                                onStartProcessing = { viewModel.startProcessingWithQuality() }
                            )
                        }

                        ProcessingState.ENQUEUED,
                        ProcessingState.BLOCKED -> {
                            QueuedContent()
                        }

                        ProcessingState.RUNNING -> {
                            ProcessingContent(
                                progress = uiState.progress,
                                framesProcessed = uiState.framesProcessed,
                                totalFrames = uiState.totalFrames,
                                remainingSeconds = uiState.remainingSeconds,
                                status = uiState.status
                            )
                        }

                        ProcessingState.SUCCEEDED -> {
                            SuccessContent(
                                framesProcessed = uiState.framesProcessed,
                                outputDir = uiState.outputDir,
                                onContinue = { uiState.outputDir?.let { onComplete(it) } }
                            )
                        }

                        ProcessingState.FAILED -> {
                            ErrorContent(
                                error = uiState.error ?: "Unknown error",
                                onRetry = { viewModel.retry() }
                            )
                        }

                        ProcessingState.CANCELLED -> {
                            CancelledContent(
                                onRetry = { viewModel.retry() }
                            )
                        }
                    }
                }
            }

            // Bottom actions
            BottomActions(
                state = uiState.state,
                onCancel = { viewModel.cancelProcessing() },
                onContinue = { uiState.outputDir?.let { onComplete(it) } },
                onRetry = { viewModel.retry() }
            )
        }
    }
}

@Composable
private fun QualitySelectorContent(
    options: List<QualityOption>,
    selectedQuality: ProcessingQuality,
    onQualitySelected: (ProcessingQuality) -> Unit,
    onStartProcessing: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Select Quality",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Higher quality takes longer but produces better results.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Quality options
        options.forEach { option ->
            QualityOptionCard(
                option = option,
                isSelected = option.quality == selectedQuality,
                onClick = { onQualitySelected(option.quality) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartProcessing,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Start Processing")
        }
    }
}

@Composable
private fun QualityOptionCard(
    option: QualityOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (icon, title, color) = when (option.quality) {
        ProcessingQuality.FAST -> Triple(
            Icons.Default.Speed,
            "Fast",
            MaterialTheme.colorScheme.primary
        )
        ProcessingQuality.BALANCED -> Triple(
            Icons.Default.Timer,
            "Balanced",
            MaterialTheme.colorScheme.secondary
        )
        ProcessingQuality.BEST -> Triple(
            Icons.Default.Star,
            "Best Quality",
            MaterialTheme.colorScheme.tertiary
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                color.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, color)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (option.recommended) {
                        androidx.compose.material3.AssistChip(
                            onClick = {},
                            label = { Text("Recommended") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Recommend,
                                    null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                Text(
                    text = option.spec.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Text(
                    text = "Est. time: ${option.estimatedTimeStr}",
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun QueuedContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = "Waiting to start...",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun ProcessingContent(
    progress: Int,
    framesProcessed: Int,
    totalFrames: Int,
    remainingSeconds: Int,
    status: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress / 100f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Progress circle
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 8.dp
                )
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth()
            )

            // Frame count
            Text(
                text = "$framesProcessed / $totalFrames frames",
                style = MaterialTheme.typography.bodyLarge
            )

            // ETA
            if (remainingSeconds > 0) {
                Text(
                    text = "About ${formatDuration(remainingSeconds)} remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Status message
            if (status.isNotEmpty()) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SuccessContent(
    framesProcessed: Int,
    outputDir: String?,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Processing Complete!",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Processed $framesProcessed frames",
            style = MaterialTheme.typography.bodyLarge
        )

        outputDir?.let {
            Text(
                text = "Output saved to cache",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onContinue) {
            Text("Continue to Export")
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Text(
            text = "Processing Failed",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Retry")
        }
    }
}

@Composable
private fun CancelledContent(
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Cancel,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
        )

        Text(
            text = "Processing Cancelled",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "The operation was cancelled by the user",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Start Over")
        }
    }
}

@Composable
private fun BottomActions(
    state: ProcessingState,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        when (state) {
            ProcessingState.IDLE,
            ProcessingState.ENQUEUED,
            ProcessingState.BLOCKED,
            ProcessingState.RUNNING -> {
                OutlinedButton(
                    onClick = onCancel,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Cancel")
                }
            }

            ProcessingState.SUCCEEDED -> {
                TextButton(onClick = { /* Stay on screen */ }) {
                    Text("Stay Here")
                }
                Button(onClick = onContinue) {
                    Text("Export")
                }
            }

            ProcessingState.FAILED,
            ProcessingState.CANCELLED -> {
                OutlinedButton(onClick = onRetry) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Retry")
                }
            }
        }
    }
}

/**
 * Format seconds to MM:SS.
 */
private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) {
        "${mins}m ${secs}s"
    } else {
        "${secs}s"
    }
}
