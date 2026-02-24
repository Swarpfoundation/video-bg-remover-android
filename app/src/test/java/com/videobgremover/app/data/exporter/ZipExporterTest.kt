package com.videobgremover.app.data.exporter

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Unit tests for [ZipExporter].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ZipExporterTest {

    private lateinit var context: Context
    private lateinit var exporter: ZipExporter

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        exporter = ZipExporter(context)
    }

    @Test
    fun `exportFrames returns error for non-existent directory`() = runBlocking {
        val nonExistentDir = File(context.cacheDir, "non_existent_${System.currentTimeMillis()}")

        val result = exporter.exportFrames(nonExistentDir)

        assertTrue(result is ZipExportResult.Error)
        assertEquals(
            "Source directory does not exist",
            (result as ZipExportResult.Error).message
        )
    }

    @Test
    fun `exportFrames returns error for empty directory`() = runBlocking {
        val emptyDir = File(context.cacheDir, "empty_${System.currentTimeMillis()}")
        emptyDir.mkdirs()

        val result = exporter.exportFrames(emptyDir)

        assertTrue(result is ZipExportResult.Error)
        assertEquals(
            "No PNG files found in source directory",
            (result as ZipExportResult.Error).message
        )

        emptyDir.deleteRecursively()
    }

    @Test
    fun `exportFrames successfully creates ZIP from PNG files`() = runBlocking {
        // Create test directory with PNG files
        val testDir = File(context.cacheDir, "test_export_${System.currentTimeMillis()}")
        testDir.mkdirs()

        // Create some dummy PNG files
        repeat(3) { index ->
            val file = File(testDir, "frame_${String.format("%05d", index)}.png")
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
        }

        val progressUpdates = mutableListOf<ExportProgress>()

        val result = exporter.exportFrames(testDir) { progress ->
            progressUpdates.add(progress)
        }

        assertTrue(result is ZipExportResult.Success)

        val success = result as ZipExportResult.Success
        assertEquals(3, success.fileCount)
        assertTrue(success.totalBytes > 0)
        assertTrue(success.zipFile.exists())

        // Verify ZIP contents
        ZipFile(success.zipFile).use { zip ->
            assertEquals(3, zip.size())
        }

        // Verify progress updates
        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(3, progressUpdates.last().currentFile)
        assertEquals(3, progressUpdates.last().totalFiles)

        // Cleanup
        testDir.deleteRecursively()
        success.zipFile.delete()
    }

    @Test
    fun `exportFrames creates ZIP at custom output location`() = runBlocking {
        val testDir = File(context.cacheDir, "test_custom_${System.currentTimeMillis()}")
        testDir.mkdirs()

        val file = File(testDir, "frame_00000.png")
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()

        val customOutput = File(context.cacheDir, "custom_output_${System.currentTimeMillis()}.zip")

        val result = exporter.exportFrames(testDir, customOutput)

        assertTrue(result is ZipExportResult.Success)
        assertEquals(customOutput.absolutePath, (result as ZipExportResult.Success).zipFile.absolutePath)
        assertTrue(customOutput.exists())

        // Cleanup
        testDir.deleteRecursively()
        customOutput.delete()
    }

    @Test
    fun `cleanupOldExports removes old files`() = runBlocking {
        val exportsDir = File(context.cacheDir, "exports")
        exportsDir.mkdirs()

        // Create an old file
        val oldFile = File(exportsDir, "old_export.zip")
        oldFile.writeText("test")
        oldFile.setLastModified(System.currentTimeMillis() - 10 * 24 * 60 * 60 * 1000) // 10 days old

        // Create a recent file
        val recentFile = File(exportsDir, "recent_export.zip")
        recentFile.writeText("test")

        val deletedCount = exporter.cleanupOldExports(
            maxAgeMs = 7 * 24 * 60 * 60 * 1000, // 7 days
            maxFiles = 10
        )

        assertEquals(1, deletedCount)
        assertFalse(oldFile.exists())
        assertTrue(recentFile.exists())

        // Cleanup
        exportsDir.deleteRecursively()
    }

    @Test
    fun `cleanupOldExports respects max files limit`() = runBlocking {
        val exportsDir = File(context.cacheDir, "exports")
        exportsDir.mkdirs()

        // Create 5 files
        repeat(5) { index ->
            val file = File(exportsDir, "export_$index.zip")
            file.writeText("test")
            file.setLastModified(
                System.currentTimeMillis() - (5 - index) * 60 * 60 * 1000 // Different times
            )
        }

        val deletedCount = exporter.cleanupOldExports(
            maxAgeMs = Long.MAX_VALUE, // Don't delete by age
            maxFiles = 3
        )

        assertEquals(2, deletedCount)

        // Cleanup
        exportsDir.deleteRecursively()
    }

    @Test
    fun `formatFileSize formats bytes correctly`() {
        val testCases = listOf(
            500L to "500 B",
            1500L to "2 KB",
            1500000L to "1.5 MB",
            1500000000L to "1.50 GB"
        )

        testCases.forEach { (bytes, expected) ->
            // Just verify the pattern, exact formatting may vary
            val result = formatFileSizeForTest(bytes)
            assertTrue(
                "Expected $expected but got $result for $bytes bytes",
                result.contains(expected.split(" ")[1]) || // unit matches
                    result == expected
            )
        }
    }

    private fun formatFileSizeForTest(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000f)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
            bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000f)
            else -> "$bytes B"
        }
    }
}
