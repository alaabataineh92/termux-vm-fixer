package io.github.alaabataineh92.termuxvmfixer

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.alaabataineh92.termuxvmfixer.feature.home.HomeScreen
import io.github.alaabataineh92.termuxvmfixer.feature.patcher.PatcherScreen
import io.github.alaabataineh92.termuxvmfixer.feature.settings.LogViewerScreen
import io.github.alaabataineh92.termuxvmfixer.feature.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val LOG = "log"
    const val PATCHER = "patcher/{remote}"

    fun patcher(remote: Boolean): String = "patcher/$remote"
}

@Composable
fun TermuxVmFixerNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenLog = { navController.navigate(Routes.LOG) },
                onOpenPatcher = { remote ->
                    navController.navigate(Routes.patcher(remote))
                },
            )
        }

        composable(
            route = Routes.PATCHER,
            arguments = listOf(navArgument("remote") { type = NavType.BoolType }),
        ) { entry ->
            PatcherScreen(
                remote = entry.arguments?.getBoolean("remote") == true,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLog = { navController.navigate(Routes.LOG) },
            )
        }

        composable(Routes.LOG) {
            val context = LocalContext.current

            LogViewerScreen(
                onBack = { navController.popBackStack() },
                onShare = {
                    shareLog(context)
                },
            )
        }
    }
}

private fun shareLog(context: android.content.Context) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            android.content.Intent.EXTRA_SUBJECT,
            context.getString(R.string.vmfix_logshare_subject),
        )
        putExtra(
            android.content.Intent.EXTRA_TEXT,
            LogShare.text(context),
        )
    }

    runCatching {
        context.startActivity(
            android.content.Intent.createChooser(
                intent,
                context.getString(R.string.vmfix_logshare_chooser),
            )
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
