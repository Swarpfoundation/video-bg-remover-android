package com.videobgremover.app.ui.navigation

/**
 * Sealed class representing navigation destinations.
 */
sealed class Screen(val route: String) {
    data object Import : Screen("import")
    data object Preview : Screen("preview/{videoUri}") {
        fun createRoute(videoUri: String) = "preview/${UriEncoder.encode(videoUri)}"
    }

    data object Processing : Screen("processing/{videoUri}") {
        fun createRoute(videoUri: String) = "processing/${UriEncoder.encode(videoUri)}"
    }

    data object Export : Screen("export") {
        const val PARAM_VIDEO_URI = "videoUri"
        const val PARAM_OUTPUT_DIR = "outputDir"
    }
}

/**
 * Utility for encoding/decoding URIs for navigation.
 */
object UriEncoder {
    fun encode(uri: String): String = java.net.URLEncoder.encode(uri, "UTF-8")
    fun decode(encoded: String): String = java.net.URLDecoder.decode(encoded, "UTF-8")
}
