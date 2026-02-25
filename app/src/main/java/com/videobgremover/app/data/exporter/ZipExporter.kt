package com.videobgremover.app.data.exporter

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.videobgremover.app.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Data class representing export progress.
 */
data class ExportProgress(
    val currentFile: Int,
    val totalFiles: Int,
    val currentFileName: String
)

/**
 * Result of a ZIP export operation.
 */
sealed class ZipExportResult {
    data class Success(
        val zipFile: File,
        val fileCount: Int,
        val totalBytes: Long
    ) : ZipExportResult()

    data class Error(val message: String) : ZipExportResult()
    object Cancelled : ZipExportResult()
}

/**
 * Exports PNG frames to a ZIP file.
 */
class ZipExporter(private val context: Context) {

    /**
     * Create a ZIP file from a directory of PNG frames.
     *
     * @param sourceDir Directory containing PNG files
     * @param outputFile Destination ZIP file (or null to create in cache)
     * @param onProgress Callback for progress updates
     * @return ZipExportResult indicating success or failure
     */
    suspend fun exportFrames(
        sourceDir: File,
        outputFile: File? = null,
        onProgress: (suspend (ExportProgress) -> Unit)? = null
    ): ZipExportResult = withContext(Dispatchers.IO) {
        if (!sourceDir.exists() || !sourceDir.isDirectory) {
            return@withContext ZipExportResult.Error("Source directory does not exist")
        }

        val pngFiles: List<File> = sourceDir.listFiles { file ->
            file.extension.lowercase() == "png"
        }?.sortedBy { it.name } ?: emptyList()

        if (pngFiles.isEmpty()) {
            return@withContext ZipExportResult.Error("No PNG files found in source directory")
        }

        val zipFile = outputFile ?: File(
            context.cacheDir,
            "exports/${sourceDir.name}.zip"
        )

        zipFile.parentFile?.mkdirs()

        var totalBytes: Long = 0

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                val buffer = ByteArray(BUFFER_SIZE)

                for ((index, file) in pngFiles.withIndex()) {
                    if (!isActive) {
                        zipFile.delete()
                        return@withContext ZipExportResult.Cancelled
                    }

                    onProgress?.invoke(
                        ExportProgress(
                            currentFile = index + 1,
                            totalFiles = pngFiles.size,
                            currentFileName = file.name
                        )
                    )

                    val entry = ZipEntry(file.name)
                    zos.putNextEntry(entry)

                    FileInputStream(file).use { fis ->
                        BufferedInputStream(fis).use { bis ->
                            var len: Int
                            while (bis.read(buffer).also { len = it } > 0) {
                                zos.write(buffer, 0, len)
                                totalBytes += len
                            }
                        }
                    }

                    zos.closeEntry()
                }
            }

            Logger.d("Created ZIP ${zipFile.name}, files: ${pngFiles.size}, bytes: $totalBytes")

            ZipExportResult.Success(
                zipFile = zipFile,
                fileCount = pngFiles.size,
                totalBytes = totalBytes
            )
        } catch (e: Exception) {
            Logger.e("ZIP export failed", e)
            zipFile.delete()
            ZipExportResult.Error("Export failed: ${e.message}")
        }
    }

    /**
     * Get a content URI for the ZIP file (for sharing).
     */
    fun getContentUri(zipFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
    }

    /**
     * Clean up old export files to free space.
     *
     * @param maxAgeMs Maximum age of files to keep
     * @param maxFiles Maximum number of export files to keep
     */
    suspend fun cleanupOldExports(
        maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
        maxFiles: Int = DEFAULT_MAX_FILES
    ): Int = withContext(Dispatchers.IO) {
        val exportsDir = File(context.cacheDir, "exports")
        if (!exportsDir.exists()) return@withContext 0

        var deletedCount = 0
        val currentTime = System.currentTimeMillis()

        // Delete old files
        exportsDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val age = currentTime - file.lastModified()
                if (age > maxAgeMs) {
                    if (file.delete()) deletedCount++
                }
            }
        }

        // Keep only newest files if still over limit
        val remainingFiles = exportsDir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

        if (remainingFiles.size > maxFiles) {
            remainingFiles.drop(maxFiles).forEach { file ->
                if (file.delete()) deletedCount++
            }
        }

        Logger.d("Cleaned up $deletedCount old export files")
        deletedCount
    }

    companion object {
        private const val BUFFER_SIZE = 8192
        private const val DEFAULT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        private const val DEFAULT_MAX_FILES = 5
    }
}
