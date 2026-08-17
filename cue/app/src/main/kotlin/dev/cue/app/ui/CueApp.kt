package dev.cue.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.cue.app.ui.drafts.DraftsScreen
import dev.cue.app.ui.inbox.InboxScreen
import dev.cue.app.ui.onboarding.OnboardingScreen
import dev.cue.app.ui.settings.SettingsScreen

object Routes {
    const val INBOX = "inbox"
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val DRAFTS = "drafts/{conversationId}"

    fun drafts(conversationId: String) = "drafts/$conversationId"
}

@Composable
fun CueApp(startConversationId: String?) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startConversationId?.let { Routes.drafts(it) } ?: Routes.INBOX,
    ) {
        composable(Routes.INBOX) {
            InboxScreen(
                onOpenConversation = { navController.navigate(Routes.drafts(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenOnboarding = { navController.navigate(Routes.ONBOARDING) },
            )
        }
        composable(
            Routes.DRAFTS,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType }),
        ) { entry ->
            DraftsScreen(
                conversationId = entry.arguments?.getString("conversationId").orEmpty(),
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(Routes.INBOX)
                },
            )
        }
    }
}
