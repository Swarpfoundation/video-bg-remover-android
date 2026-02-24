package com.videobgremover.app.ui.contract

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Contract for picking a destination folder using Storage Access Framework (SAF).
 */
class FolderPickerContract : ActivityResultContracts.OpenDocumentTree() {

    override fun createIntent(context: Context, input: Uri?): Intent {
        return super.createIntent(context, input).apply {
            // Add extra to allow the user to create a new folder
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        }
    }
}

/**
 * Contract for creating a new document via SAF.
 */
class CreateDocumentContract : ActivityResultContracts.CreateDocument("application/zip") {

    override fun createIntent(context: Context, input: String): Intent {
        return super.createIntent(context, input).apply {
            // Set a default filename with timestamp
            val timestamp = java.text.SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                java.util.Locale.getDefault()
            ).format(java.util.Date())
            putExtra(Intent.EXTRA_TITLE, "video_bg_removed_$timestamp.zip")
        }
    }
}
