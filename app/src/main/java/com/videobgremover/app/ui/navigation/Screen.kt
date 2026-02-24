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

    data object Export : Screen("export/{sourceDir}") {
        fun createRoute(sourceDir: String) = "export/${UriEncoder.encode(sourceDir)}"
    }
}

/**
 * Utility for encoding/decoding URIs and paths for navigation.
 */
object UriEncoder {
    fun encode(uri: String): String = java.net.URLEncoder.encode(uri, "UTF-8")
    fun decode(encoded: String): String = java.net.URLDecoder.decode(encoded, "UTF-8")
}
