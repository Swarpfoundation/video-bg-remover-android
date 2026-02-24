package com.videobgremover.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videobgremover.app.data.preferences.Settings
import com.videobgremover.app.ui.viewmodel.SettingsViewModel

/**
 * Settings screen for app configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.createFactory(LocalContext.current)
    )
) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.resetToDefaults() }) {
                        Text("Reset")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Processing Settings
            SettingsSection(title = "Processing") {
                // Target FPS
                SliderSetting(
                    title = "Target Frame Rate",
                    value = settings.targetFps.toFloat(),
                    onValueChange = { viewModel.setTargetFps(it.toInt()) },
                    valueRange = 10f..30f,
                    steps = 3,
                    valueText = "${settings.targetFps} FPS"
                )

                // Max duration
                SliderSetting(
                    title = "Max Video Duration",
                    value = settings.maxVideoDuration.toFloat(),
                    onValueChange = { viewModel.setMaxVideoDuration(it.toInt()) },
                    valueRange = 15f..120f,
                    steps = 6,
                    valueText = "${settings.maxVideoDuration}s"
                )

                // Processing resolution
                ResolutionSelector(
                    selectedResolution = settings.processingResolution,
                    onResolutionSelected = { viewModel.setProcessingResolution(it) }
                )
            }

            // Segmentation Settings
            SettingsSection(title = "Segmentation") {
                // Confidence threshold
                SliderSetting(
                    title = "Confidence Threshold",
                    value = settings.confidenceThreshold,
                    onValueChange = { viewModel.setConfidenceThreshold(it) },
                    valueRange = 0.3f..0.7f,
                    valueText = "%.0f%%".format(settings.confidenceThreshold * 100)
                )

                // Temporal smoothing
                SwitchSetting(
                    title = "Temporal Smoothing",
                    description = "Smooth mask transitions between frames",
                    checked = settings.temporalSmoothing,
                    onCheckedChange = { viewModel.setTemporalSmoothing(it) }
                )

                if (settings.temporalSmoothing) {
                    SliderSetting(
                        title = "Smoothing Strength",
                        value = settings.temporalAlpha,
                        onValueChange = { viewModel.setTemporalAlpha(it) },
                        valueRange = 0.1f..0.5f,
                        valueText = "%.0f%%".format(settings.temporalAlpha * 100)
                    )
                }

                // Morphology
                SwitchSetting(
                    title = "Fill Holes",
                    description = "Remove small gaps in the mask",
                    checked = settings.applyMorphology,
                    onCheckedChange = { viewModel.setApplyMorphology(it) }
                )

                // Feather
                SwitchSetting(
                    title = "Feather Edges",
                    description = "Soften the edges of the mask",
                    checked = settings.applyFeather,
                    onCheckedChange = { viewModel.setApplyFeather(it) }
                )

                if (settings.applyFeather) {
                    SliderSetting(
                        title = "Feather Radius",
                        value = settings.featherRadius,
                        onValueChange = { viewModel.setFeatherRadius(it) },
                        valueRange = 1f..8f,
                        steps = 6,
                        valueText = "%.0fpx".format(settings.featherRadius)
                    )
                }
            }

            // Export Settings
            SettingsSection(title = "Export") {
                ExportFormatSelector(
                    selectedFormat = settings.defaultExportFormat,
                    onFormatSelected = { viewModel.setDefaultExportFormat(it) }
                )

                SwitchSetting(
                    title = "Auto Cleanup",
                    description = "Automatically delete old exports",
                    checked = settings.autoCleanup,
                    onCheckedChange = { viewModel.setAutoCleanup(it) }
                )
            }

            // About
            SettingsSection(title = "About") {
                AboutCard()
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueText: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ResolutionSelector(
    selectedResolution: Int,
    onResolutionSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Processing Resolution", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${selectedResolution}p",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select Resolution") },
            text = {
                Column {
                    ResolutionOption(
                        resolution = 360,
                        selected = selectedResolution == 360,
                        onSelect = {
                            onResolutionSelected(360)
                            showDialog = false
                        }
                    )
                    ResolutionOption(
                        resolution = 512,
                        selected = selectedResolution == 512,
                        onSelect = {
                            onResolutionSelected(512)
                            showDialog = false
                        }
                    )
                    ResolutionOption(
                        resolution = 720,
                        selected = selectedResolution == 720,
                        onSelect = {
                            onResolutionSelected(720)
                            showDialog = false
                        }
                    )
                    ResolutionOption(
                        resolution = 1080,
                        selected = selectedResolution == 1080,
                        onSelect = {
                            onResolutionSelected(1080)
                            showDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ResolutionOption(
    resolution: Int,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(
            text = "${resolution}p",
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ExportFormatSelector(
    selectedFormat: Int,
    onFormatSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val formatNames = mapOf(0 to "PNG Sequence (ZIP)", 1 to "Mask Video (MP4)")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Default Export Format", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = formatNames[selectedFormat] ?: "PNG Sequence",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select Default Format") },
            text = {
                Column {
                    FormatOption(
                        name = "PNG Sequence (ZIP)",
                        description = "Best quality, larger files",
                        selected = selectedFormat == 0,
                        onSelect = {
                            onFormatSelected(0)
                            showDialog = false
                        }
                    )
                    FormatOption(
                        name = "Mask Video (MP4)",
                        description = "Universal compatibility",
                        selected = selectedFormat == 1,
                        onSelect = {
                            onFormatSelected(1)
                            showDialog = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FormatOption(
    name: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(text = name)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun AboutCard() {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Video Background Remover",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = "Remove backgrounds from videos using on-device AI. " +
                    "Powered by MediaPipe segmentation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
