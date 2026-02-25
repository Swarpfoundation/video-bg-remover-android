package com.videobgremover.app.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.videobgremover.app.R
import com.videobgremover.app.ui.MainActivity

/**
 * Helper for creating and managing processing notifications.
 */
class ProcessingNotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Create foreground info for the worker.
     */
    fun createForegroundInfo(
        progress: Int,
        currentFrame: Int,
        totalFrames: Int,
        workId: String
    ): ForegroundInfo {
        val notification = createNotification(
            progress = progress,
            currentFrame = currentFrame,
            totalFrames = totalFrames,
            workId = workId
        )

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    /**
     * Create the processing notification.
     */
    private fun createNotification(
        progress: Int,
        currentFrame: Int,
        totalFrames: Int,
        workId: String
    ): Notification {
        val title = context.getString(R.string.processing_notification_title)
        val content = if (totalFrames > 0) {
            "Frame $currentFrame of $totalFrames"
        } else {
            "Processing..."
        }

        // Intent to open the app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WORK_ID, workId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Cancel action
        val cancelIntent = Intent(context, ProcessingCancelReceiver::class.java).apply {
            putExtra(EXTRA_WORK_ID, workId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            workId.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, totalFrames == 0)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.processing_notification_cancel),
                cancelPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Create the notification channel (required for Android O+).
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.processing_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.processing_channel_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "video_processing_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_WORK_ID = "work_id"
    }
}
