package com.videobgremover.app.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.videobgremover.app.data.worker.VideoProcessingWorker
import com.videobgremover.app.data.worker.VideoProcessingWorker.Companion.createInputData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.work.workDataOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Repository for managing video processing operations.
 */
class ProcessingRepository(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Start processing a video.
     *
     * @param videoUri The URI of the video to process
     * @param targetFps Target frames per second for extraction
     * @param maxFrames Maximum number of frames to process
     * @return The work ID as a string
     */
    fun startProcessing(
        videoUri: String,
        targetFps: Int = DEFAULT_TARGET_FPS,
        maxFrames: Int = DEFAULT_MAX_FRAMES
    ): String {
        val inputData = createInputData(
            videoUri = videoUri,
            targetFps = targetFps,
            maxFrames = maxFrames
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<VideoProcessingWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag(PROCESSING_WORK_TAG)
            .addTag(createVideoTag(videoUri))
            .build()

        // Use unique work to prevent duplicate processing of same video
        workManager.enqueueUniqueWork(
            createUniqueWorkName(videoUri),
            ExistingWorkPolicy.KEEP,
            workRequest
        )

        return workRequest.id.toString()
    }

    /**
     * Cancel processing for a specific video.
     */
    fun cancelProcessing(videoUri: String) {
        workManager.cancelUniqueWork(createUniqueWorkName(videoUri))
    }

    /**
     * Cancel all processing.
     */
    fun cancelAllProcessing() {
        workManager.cancelAllWorkByTag(PROCESSING_WORK_TAG)
    }

    /**
     * Observe work info for a specific work ID.
     */
    fun getWorkInfo(workId: String): LiveData<WorkInfo?> {
        return workManager.getWorkInfoByIdLiveData(java.util.UUID.fromString(workId))
    }

    /**
     * Observe all processing work.
     */
    fun getAllProcessingWork(): Flow<List<WorkInfo>> {
        return workManager.getWorkInfosByTagFlow(PROCESSING_WORK_TAG)
            .map { it.filterNotNull() }
    }

    /**
     * Check if there's any ongoing processing for a video.
     */
    suspend fun isProcessing(videoUri: String): Boolean {
        val workInfos = workManager.getWorkInfosForUniqueWork(
            createUniqueWorkName(videoUri)
        ).await()

        return workInfos.any { workInfo ->
            workInfo.state == WorkInfo.State.RUNNING ||
                workInfo.state == WorkInfo.State.ENQUEUED
        }
    }

    /**
     * Get the output directory from completed work.
     */
    fun getOutputDirectory(workInfo: WorkInfo): String? {
        return workInfo.outputData.getString(VideoProcessingWorker.OUTPUT_DIR_KEY)
    }

    /**
     * Get the number of frames processed from work info.
     */
    fun getFramesProcessed(workInfo: WorkInfo): Int {
        return workInfo.progress.getInt(VideoProcessingWorker.FRAMES_PROCESSED_KEY, 0)
    }

    /**
     * Get the total number of frames from work info.
     */
    fun getTotalFrames(workInfo: WorkInfo): Int {
        return workInfo.progress.getInt(VideoProcessingWorker.TOTAL_FRAMES_KEY, 0)
    }

    /**
     * Get progress percentage from work info.
     */
    fun getProgress(workInfo: WorkInfo): Int {
        return workInfo.progress.getInt(VideoProcessingWorker.PROGRESS_KEY, 0)
    }

    /**
     * Get status message from work info.
     */
    fun getStatus(workInfo: WorkInfo): String? {
        return workInfo.progress.getString(VideoProcessingWorker.STATUS_KEY)
    }

    private fun createUniqueWorkName(videoUri: String): String {
        return "process_${videoUri.hashCode()}"
    }

    private fun createVideoTag(videoUri: String): String {
        return "video_${videoUri.hashCode()}"
    }

    companion object {
        private const val PROCESSING_WORK_TAG = "video_processing"
        private const val DEFAULT_TARGET_FPS = 15
        private const val DEFAULT_MAX_FRAMES = 900
    }
}

/**
 * Extension to convert ListenableFuture to suspend function.
 */
private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        Futures.addCallback(
            this,
            object : FutureCallback<T> {
                @Suppress("UNCHECKED_CAST")
                override fun onSuccess(result: T?) {
                    continuation.resume(result as T)
                }

                override fun onFailure(t: Throwable) {
                    continuation.resumeWithException(t)
                }
            },
            MoreExecutors.directExecutor()
        )

        continuation.invokeOnCancellation { cancel(true) }
    }
}
