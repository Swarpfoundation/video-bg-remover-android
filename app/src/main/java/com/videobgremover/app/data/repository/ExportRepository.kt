package com.videobgremover.app.data.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.StatFs
import android.provider.MediaStore
import com.videobgremover.app.core.Logger
import com.videobgremover.app.data.exporter.MaskVideoExportResult
import com.videobgremover.app.data.exporter.MaskVideoExporter
import com.videobgremover.app.data.exporter.ZipExportResult
import com.videobgremover.app.data.exporter.ZipExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

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
    val cacheFilePath: String? = null,
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
            is ZipExportResult.Success -> {
                val finalOutputUri = if (destinationUri != null) {
                    val copyResult = copyToSafDestination(result.zipFile, destinationUri)
                    copyResult.getOrElse { error ->
                        emit(
                            ExportState(
                                isExporting = false,
                                cacheFilePath = result.zipFile.absolutePath,
                                error = "Failed to save to destination: ${error.message}"
                            )
                        )
                        return@flow
                    }
                } else {
                    zipExporter.getContentUri(result.zipFile)
                }

                emit(
                    ExportState(
                        isExporting = false,
                        progress = 100,
                        cacheFilePath = result.zipFile.absolutePath,
                        outputUri = finalOutputUri
                    )
                )
            }

            is ZipExportResult.Error -> {
                emit(
                    ExportState(
                        isExporting = false,
                        error = result.message
                    )
                )
            }

            ZipExportResult.Cancelled -> {
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
        val resolver = context.contentResolver
        try {
            val outputStream = resolver.openOutputStream(destinationUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Failed to open destination output stream")
                )

            outputStream.use { stream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(stream)
                    stream.flush()
                }
            }
            Logger.d("Copied ${sourceFile.name} to SAF destination")
            Result.success(destinationUri)
        } catch (e: Exception) {
            Logger.e("Failed to copy to SAF destination", e)
            // Best-effort rollback if the destination document was partially written.
            runCatching { resolver.delete(destinationUri, null, null) }
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
        val resolver = context.contentResolver
        var insertedUri: Uri? = null
        try {
            val isMp4 = fileName.lowercase().endsWith(".mp4")
            val mimeType = if (isMp4) "video/mp4" else "application/zip"
            val collection = when {
                isMp4 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                isMp4 ->
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else ->
                    MediaStore.Files.getContentUri("external")
            }

            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/VideoBgRemover")
                put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(
                collection,
                contentValues
            ) ?: return@withContext Result.failure(
                IllegalStateException("Failed to create MediaStore entry")
            )
            insertedUri = uri

            val outputStream = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Failed to open MediaStore output stream")

            outputStream.use { stream ->
                FileInputStream(sourceFile).use { inputStream ->
                    inputStream.copyTo(stream)
                    stream.flush()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    android.content.ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    },
                    null,
                    null
                )
            }

            Logger.d("Saved ${sourceFile.name} to MediaStore")
            Result.success(uri)
        } catch (e: Exception) {
            Logger.e("Failed to save to MediaStore", e)
            insertedUri?.let { uri ->
                runCatching { resolver.delete(uri, null, null) }
            }
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
                value.matches(Regex("-?\\d+\\.\\d+")) -> value.toDouble()
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
            is MaskVideoExportResult.Success -> {
                // Copy to destination if provided
                val finalOutputUri = if (destinationUri != null) {
                    val copyResult = copyToSafDestination(outputFile, destinationUri)
                    copyResult.getOrElse { error ->
                        return@withContext ExportState(
                            isExporting = false,
                            cacheFilePath = outputFile.absolutePath,
                            error = "Failed to save to destination: ${error.message}"
                        )
                    }
                } else {
                    zipExporter.getContentUri(outputFile)
                }

                ExportState(
                    isExporting = false,
                    progress = 100,
                    cacheFilePath = outputFile.absolutePath,
                    outputUri = finalOutputUri
                )
            }

            is MaskVideoExportResult.Error -> {
                ExportState(
                    isExporting = false,
                    error = result.message
                )
            }

            MaskVideoExportResult.Cancelled -> {
                ExportState(
                    isExporting = false,
                    error = "Export cancelled"
                )
            }
        }
    }
}
