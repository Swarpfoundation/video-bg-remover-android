package com.videobgremover.app.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.videobgremover.app.core.Logger
import java.util.UUID

/**
 * Broadcast receiver for handling cancel actions from notifications.
 */
class ProcessingCancelReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val workIdString = intent.getStringExtra(ProcessingNotificationHelper.EXTRA_WORK_ID)

        if (workIdString != null) {
            try {
                val workId = UUID.fromString(workIdString)
                WorkManager.getInstance(context).cancelWorkById(workId)
                Logger.d("Cancelled work: $workId")
            } catch (e: IllegalArgumentException) {
                Logger.e("Invalid work ID: $workIdString")
            }
        }
    }
}
