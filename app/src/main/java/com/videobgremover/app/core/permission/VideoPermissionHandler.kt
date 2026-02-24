package com.videobgremover.app.core.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

/**
 * Handles video permission requests based on Android version.
 */
object VideoPermissionHandler {

    /**
     * Gets the required permission based on Android version.
     * - Android 13+ (API 33+): READ_MEDIA_VIDEO
     * - Android 12 and below: READ_EXTERNAL_STORAGE
     */
    fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    /**
     * Checks if the required permission is granted.
     */
    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            getRequiredPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Composable that handles requesting video permission.
 */
@Composable
fun rememberVideoPermissionLauncher(
    onResult: (Boolean) -> Unit
): ManagedActivityResultLauncher<String, Boolean> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onResult(isGranted)
    }
}

/**
 * Composable that automatically requests permission on first launch if needed.
 */
@Composable
fun RequestVideoPermission(
    onPermissionResult: (Boolean) -> Unit
) {
    val launcher = rememberVideoPermissionLauncher(onResult = onPermissionResult)

    LaunchedEffect(Unit) {
        launcher.launch(VideoPermissionHandler.getRequiredPermission())
    }
}
