package com.videobgremover.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.videobgremover.app.ui.screens.import_video.ImportScreen
import com.videobgremover.app.ui.screens.preview.PreviewScreen

/**
 * Navigation graph for the app.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Import.route,
        modifier = modifier
    ) {
        composable(route = Screen.Import.route) {
            ImportScreen(
                onVideoImported = { videoUri ->
                    navController.navigate(Screen.Preview.createRoute(videoUri))
                }
            )
        }

        composable(
            route = Screen.Preview.route,
            arguments = listOf(
                navArgument("videoUri") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            val videoUri = UriEncoder.decode(encodedUri)

            PreviewScreen(
                videoUri = videoUri,
                onBack = {
                    navController.popBackStack()
                },
                onContinue = {
                    navController.navigate(Screen.Processing.createRoute(videoUri))
                }
            )
        }

        composable(
            route = Screen.Processing.route,
            arguments = listOf(
                navArgument("videoUri") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("videoUri") ?: ""
            val videoUri = UriEncoder.decode(encodedUri)

            // Placeholder for processing screen (Step 4)
            ImportScreen(
                onVideoImported = {}
            )
        }
    }
}
