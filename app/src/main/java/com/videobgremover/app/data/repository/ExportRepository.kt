package com.videobgremover.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import com.videobgremover.app.core.Logger
import com.videobgremover.app.data.exporter.MaskVideoExporter
import com.videobgremover.app.data.exporter.ZipExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Export format options.
 */
enum class ExportFormat {
    PNG_ZIP,      // ZIP of PNG frames with alpha
    MASK_MP4      // Grayscale mask video (placeholder for Step 6)
}

/**
 * Export destination options.
 */
enum class ExportDestination {
    CACHE,        // App cache (for sharing)
    SAF,          // Storage Access Framework (user selected folder)
    MEDIA_STORE   // MediaStore (Movies/VideoBgRemover)
}

/**
 * State of an export operation.
 */
data class ExportState(
    val isExporting: Boolean = false,
    val progress: Int = 0,
    val currentFile: String = "",
    val totalFiles: Int = 0,
    val outputUri: Uri? = null,
    val error: String? = null
)

/**
 * Repository for handling export operations.
 */
class ExportRepository(private val context: Context) {

    private val zipExporter = ZipExporter(context)
    private val maskVideoExporter = MaskVideoExporter(context)

    /**
     * Check if there's enough storage space available.
     */
    suspend fun hasEnoughSpace(requiredBytes: Long): Boolean = withContext(Dispatchers.IO) {
        val stat = StatFs(context.cacheDir.path)
        val availableBytes = stat.availableBytes
        availableBytes > requiredBytes
    }

    /**
     * Estimate the size of the export.
     */
    suspend fun estimateExportSize(sourceDir: File): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        sourceDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
            }
        }
        // ZIP compression typically reduces PNGs by 10-20%
        (totalSize * 0.9).toLong()
    }

    /**
     * Export frames as ZIP with progress tracking.
     */
    fun exportAsZip(
        sourceDir: File,
        destinationUri: Uri? = null
    ): Flow<ExportState> = flow {
        emit(ExportState(isExporting = true))

        // Clean up old exports first
        zipExporter.cleanupOldExports()

        val result = zipExporter.exportFrames(
            sourceDir = sourceDir,
            outputFile = destinationUri?.let { uri ->
                // If SAF URI provided, copy to temp first then to destination
                null
            }
        ) { progress ->
            // Map progress to state
            val percent = if (progress.totalFiles > 0) {
                (progress.currentFile * 100) / progress.totalFiles
            } else 0

            emit(
                ExportState(
                    isExporting = true,
                    progress = percent,
                    currentFile = progress.currentFileName,
                    totalFiles = progress.totalFiles
                )
            )
        }

        when (result) {
            is ZipExporter.ZipExportResult.Success -> {
                val contentUri = zipExporter.getContentUri(result.zipFile)
                emit(
                    ExportState(
                        isExporting = false,
                        progress = 100,
                        outputUri = contentUri
                    )
                )
            }

            is ZipExporter.ZipExportResult.Error -> {
                emit(
                    ExportState(
                        isExporting = false,
                        error = result.message
                    )
                )
            }

            ZipExporter.ZipExportResult.Cancelled -> {
                emit(
                    ExportState(
                        isExporting = false,
                        error = "Export cancelled"
                    )
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Copy ZIP to user-selected directory via SAF.
     */
    suspend fun copyToSafDestination(
        sourceFile: File,
        destinationUri: Uri
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Logger.d("Copied ${sourceFile.name} to SAF destination")
            Result.success(destinationUri)
        } catch (e: Exception) {
            Logger.e("Failed to copy to SAF destination", e)
            Result.failure(e)
        }
    }

    /**
     * Save to MediaStore (Movies/VideoBgRemover).
     */
    suspend fun saveToMediaStore(
        sourceFile: File,
        fileName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(
                    android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    "Movies/VideoBgRemover"
                )
                put(android.provider.MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
            }

            val uri = context.contentResolver.insert(
                android.provider.MediaStore.Files.getContentUri("external"),
                contentValues
            ) ?: return@withContext Result.failure(
                IllegalStateException("Failed to create MediaStore entry")
            )

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Logger.d("Saved ${sourceFile.name} to MediaStore")
            Result.success(uri)
        } catch (e: Exception) {
            Logger.e("Failed to save to MediaStore", e)
            Result.failure(e)
        }
    }

    /**
     * Create a share intent for the exported file.
     */
    fun createShareIntent(uri: Uri, mimeType: String = "application/zip"): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Create a chooser intent for sharing.
     */
    fun createShareChooserIntent(
        uri: Uri,
        mimeType: String = "application/zip",
        title: String = "Share export"
    ): Intent {
        val shareIntent = createShareIntent(uri, mimeType)
        return Intent.createChooser(shareIntent, title)
    }

    /**
     * Get metadata from the processed output directory.
     */
    suspend fun getProcessingMetadata(sourceDir: File): Map<String, Any>? =
        withContext(Dispatchers.IO) {
            val metadataFile = File(sourceDir, "metadata.json")
            if (!metadataFile.exists()) return@withContext null

            try {
                val json = metadataFile.readText()
                parseMetadataJson(json)
            } catch (e: Exception) {
                Logger.e("Failed to parse metadata", e)
                null
            }
        }

    /**
     * Simple JSON parser for metadata (production would use Gson/Moshi).
     */
    private fun parseMetadataJson(json: String): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val regex = """"(\w+)":\s*([^,}]+)""".toRegex()

        regex.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].trim()
                .removeSurrounding("\"")

            result[key] = when {
                value.matches(Regex("-?\\d+")) -> value.toLong()
                value.matches(Regex("-?\\d+\.\\d+")) -> value.toDouble()
                value == "true" -> true
                value == "false" -> false
                else -> value
            }
        }

        return result
    }

    /**
     * Delete a processed output directory and its contents.
     */
    suspend fun cleanupOutputDir(sourceDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            sourceDir.deleteRecursively()
        } catch (e: Exception) {
            Logger.e("Failed to cleanup output directory", e)
            false
        }
    }

    /**
     * Export as mask MP4 video from processed frames.
     */
    suspend fun exportAsMaskMp4(
        sourceDir: File,
        destinationUri: Uri? = null
): ExportState = withContext(Dispatchers.IO) {
        // Clean up old exports
        zipExporter.cleanupOldExports()

        val outputFile = File(
            context.cacheDir,
            "exports/${MaskVideoExporter.generateDefaultFilename()}"
        )
        outputFile.parentFile?.mkdirs()

        val result = maskVideoExporter.createMaskVideoFromProcessedDir(
            processedDir = sourceDir,
            outputFile = outputFile
        )

        when (result) {
            is MaskVideoExporter.MaskVideoExportResult.Success -> {
                // Copy to destination if provided
                if (destinationUri != null) {
                    copyToSafDestination(outputFile, destinationUri)
                }

                val contentUri = zipExporter.getContentUri(outputFile)
                ExportState(
                    isExporting = false,
                    progress = 100,
                    outputUri = contentUri
                )
            }

            is MaskVideoExporter.MaskVideoExportResult.Error -> {
                ExportState(
                    isExporting = false,
                    error = result.message
                )
            }

            MaskVideoExporter.MaskVideoExportResult.Cancelled -> {
                ExportState(
                    isExporting = false,
                    error = "Export cancelled"
                )
            }
        }
    }
}
