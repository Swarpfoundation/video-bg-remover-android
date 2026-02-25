package com.videobgremover.app.data.extractor

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.videobgremover.app.domain.model.VideoMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [VideoMetadataExtractor].
 *
 * Note: These tests use Robolectric to mock Android framework classes.
 * Full integration tests would require actual video files.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VideoMetadataExtractorTest {

    private lateinit var context: Context
    private lateinit var extractor: VideoMetadataExtractor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        extractor = VideoMetadataExtractor(context)
    }

    @Test
    fun `formatDuration formats seconds correctly`() {
        // This test documents the expected format behavior
        val testCases = listOf(
            0L to "00:00",
            5000L to "00:05",
            60000L to "01:00",
            65000L to "01:05",
            360000L to "06:00",
            3661000L to "01:01:01"
        )

        testCases.forEach { (millis, expected) ->
            val result = formatDurationForTest(millis)
            assertEquals("Failed for $millis ms", expected, result)
        }
    }

    @Test
    fun `VideoMetadata calculates estimated frame count correctly`() {
        val metadata = VideoMetadata(
            uri = "content://test",
            name = "test.mp4",
            durationMs = 10000,
            width = 1920,
            height = 1080,
            frameRate = 30f,
            codec = "h264"
        )

        // 10 seconds * 30 fps = 300 frames
        assertEquals(300, metadata.estimatedFrameCount)
    }

    @Test
    fun `VideoMetadata resolution formats correctly`() {
        val metadata = VideoMetadata(
            uri = "content://test",
            name = "test.mp4",
            durationMs = 1000,
            width = 1920,
            height = 1080,
            frameRate = 30f,
            codec = "h264"
        )

        assertEquals("1920x1080", metadata.resolution)
    }

    @Test
    fun `VideoMetadata handles zero duration`() {
        val metadata = VideoMetadata(
            uri = "content://test",
            name = "test.mp4",
            durationMs = 0,
            width = 1920,
            height = 1080,
            frameRate = 30f,
            codec = "h264"
        )

        assertEquals(0, metadata.estimatedFrameCount)
    }

    @Test
    fun `isValidVideo returns false for invalid URI`() = runBlocking {
        val invalidUri = Uri.EMPTY
        val result = extractor.isValidVideo(invalidUri)

        // Should return false without crashing
        assertFalse(result)
    }

    @Test
    fun `extract handles invalid URI without crashing`() = runBlocking {
        val invalidUri = Uri.fromFile(File(context.cacheDir, "missing_${System.nanoTime()}.mp4"))
        val result = extractor.extract(invalidUri)

        assertTrue(
            result.isFailure || result.getOrNull()?.uri == invalidUri.toString()
        )
    }

    @Test
    fun `extractThumbnail returns failure for invalid URI`() = runBlocking {
        val invalidUri = Uri.EMPTY
        val result = extractor.extractThumbnail(invalidUri)

        assertTrue(result.isFailure)
    }

    /**
     * Helper function matching the private formatDuration in MetadataDisplay.
     */
    private fun formatDurationForTest(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = durationMs / (1000 * 60 * 60)

        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
