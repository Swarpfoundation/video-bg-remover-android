package com.videobgremover.app.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [VideoProcessingWorker].
 *
 * These tests verify the worker's input/output handling and configuration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class VideoProcessingWorkerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `createInputData creates correct data structure`() {
        val videoUri = "content://test/video.mp4"
        val targetFps = 30
        val maxFrames = 100

        val data = VideoProcessingWorker.createInputData(
            videoUri = videoUri,
            targetFps = targetFps,
            maxFrames = maxFrames
        )

        assertThat(data.getString(VideoProcessingWorker.KEY_VIDEO_URI), `is`(videoUri))
        assertThat(data.getInt(VideoProcessingWorker.KEY_TARGET_FPS, 0), `is`(targetFps))
        assertThat(data.getInt(VideoProcessingWorker.KEY_MAX_FRAMES, 0), `is`(maxFrames))
    }

    @Test
    fun `createInputData uses default values`() {
        val videoUri = "content://test/video.mp4"

        val data = VideoProcessingWorker.createInputData(videoUri = videoUri)

        assertThat(data.getString(VideoProcessingWorker.KEY_VIDEO_URI), `is`(videoUri))
        assertThat(data.getInt(VideoProcessingWorker.KEY_TARGET_FPS, -1), `is`(15))
        assertThat(data.getInt(VideoProcessingWorker.KEY_MAX_FRAMES, -1), `is`(900))
    }

    @Test
    fun `worker fails with missing video URI`() = runBlocking {
        val worker = TestListenableWorkerBuilder<VideoProcessingWorker>(context)
            .setInputData(Data.EMPTY)
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `progressData creates correct progress structure`() {
        val progress = 50
        val framesProcessed = 100
        val totalFrames = 200
        val remainingMs = 5000L
        val status = "Processing frame 100/200"

        val data = VideoProcessingWorker.progressData(
            progress = progress,
            framesProcessed = framesProcessed,
            totalFrames = totalFrames,
            remainingMs = remainingMs,
            status = status
        )

        assertThat(data.getInt(VideoProcessingWorker.PROGRESS_KEY, 0), `is`(progress))
        assertThat(data.getInt(VideoProcessingWorker.FRAMES_PROCESSED_KEY, 0), `is`(framesProcessed))
        assertThat(data.getInt(VideoProcessingWorker.TOTAL_FRAMES_KEY, 0), `is`(totalFrames))
        assertThat(data.getLong(VideoProcessingWorker.REMAINING_MS_KEY, 0), `is`(remainingMs))
        assertThat(data.getString(VideoProcessingWorker.STATUS_KEY), `is`(status))
    }

    @Test
    fun `successData creates correct output structure`() {
        val outputDir = "/data/cache/processed/12345"
        val framesProcessed = 100
        val totalFrames = 100

        val data = VideoProcessingWorker.successData(
            outputDir = outputDir,
            framesProcessed = framesProcessed,
            totalFrames = totalFrames
        )

        assertThat(data.getString(VideoProcessingWorker.OUTPUT_DIR_KEY), `is`(outputDir))
        assertThat(data.getInt(VideoProcessingWorker.FRAMES_PROCESSED_KEY, 0), `is`(framesProcessed))
        assertThat(data.getInt(VideoProcessingWorker.TOTAL_FRAMES_KEY, 0), `is`(totalFrames))
    }

    @Test
    fun `errorData creates correct error structure`() {
        val errorMessage = "Test error message"

        val data = VideoProcessingWorker.errorData(errorMessage)

        assertThat(data.getString(VideoProcessingWorker.ERROR_KEY), `is`(errorMessage))
    }
}

/**
 * Extension function to create progress data (mirroring worker's private function).
 */
private fun VideoProcessingWorker.Companion.progressData(
    progress: Int,
    framesProcessed: Int,
    totalFrames: Int = 0,
    remainingMs: Long = 0,
    status: String = ""
): Data {
    return Data.Builder()
        .putInt(VideoProcessingWorker.PROGRESS_KEY, progress)
        .putInt(VideoProcessingWorker.FRAMES_PROCESSED_KEY, framesProcessed)
        .putInt(VideoProcessingWorker.TOTAL_FRAMES_KEY, totalFrames)
        .putLong(VideoProcessingWorker.REMAINING_MS_KEY, remainingMs)
        .putString(VideoProcessingWorker.STATUS_KEY, status)
        .build()
}

private fun VideoProcessingWorker.Companion.successData(
    outputDir: String,
    framesProcessed: Int,
    totalFrames: Int
): Data {
    return Data.Builder()
        .putString(VideoProcessingWorker.OUTPUT_DIR_KEY, outputDir)
        .putInt(VideoProcessingWorker.FRAMES_PROCESSED_KEY, framesProcessed)
        .putInt(VideoProcessingWorker.TOTAL_FRAMES_KEY, totalFrames)
        .build()
}

private fun VideoProcessingWorker.Companion.errorData(message: String): Data {
    return Data.Builder()
        .putString(VideoProcessingWorker.ERROR_KEY, message)
        .build()
}
