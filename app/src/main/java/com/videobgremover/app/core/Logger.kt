package com.videobgremover.app.core

import android.util.Log
import com.videobgremover.app.BuildConfig

/**
 * Simple logger wrapper for the application.
 */
object Logger {
    private const val DEFAULT_TAG = "VideoBgRemover"

    fun d(message: String, tag: String = DEFAULT_TAG) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun d(tag: String, message: String, throwable: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message, throwable)
        }
    }

    fun i(message: String, tag: String = DEFAULT_TAG) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(message: String, tag: String = DEFAULT_TAG) {
        Log.w(tag, message)
    }

    fun w(message: String, throwable: Throwable, tag: String = DEFAULT_TAG) {
        Log.w(tag, message, throwable)
    }

    fun e(message: String, tag: String = DEFAULT_TAG) {
        Log.e(tag, message)
    }

    fun e(message: String, throwable: Throwable, tag: String = DEFAULT_TAG) {
        Log.e(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
}
