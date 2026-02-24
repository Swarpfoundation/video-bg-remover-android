package com.videobgremover.app.ui.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Contract for picking video files using Storage Access Framework (SAF).
 */
class VideoPickerContract : ActivityResultContracts.OpenDocument() {

    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            putExtra(Intent.EXTRA_MIME_TYPES, VIDEO_MIME_TYPES)
        }
    }

    companion object {
        val VIDEO_MIME_TYPES = arrayOf(
            "video/mp4",
            "video/x-matroska",
            "video/webm",
            "video/3gpp",
            "video/3gpp2",
            "video/avi",
            "video/mpeg",
            "video/quicktime"
        )
    }
}
