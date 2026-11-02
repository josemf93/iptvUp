package com.iptvup.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iptvup.app.ui.auth.LoginScreen
import com.iptvup.app.ui.main.MainScreen
import com.iptvup.app.ui.player.PlayerScreen
import com.iptvup.app.ui.profiles.AddProfileScreen
import com.iptvup.app.ui.profiles.ProfilesScreen

@Composable
fun AppRoot(
    appViewModel: AppViewModel = hiltViewModel()
) {
    val user by appViewModel.user.collectAsStateWithLifecycle()

    if (user == null) {
        LoginScreen()
    } else {
        AuthenticatedNavHost()
    }
}

@Composable
private fun AuthenticatedNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "profiles"
    ) {
        composable("profiles") {
            ProfilesScreen(
                onOpenProfile = { profileId ->
                    navController.navigate("main/$profileId")
                },
                onAddProfile = {
                    navController.navigate("add")
                },
                onEditProfile = { profileId ->
                    navController.navigate("edit/$profileId")
                }
            )
        }

        composable("add") {
            AddProfileScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = "edit/{profileId}",
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType }
            )
        ) {
            AddProfileScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = "main/{profileId}",
            arguments = listOf(
                navArgument("profileId") { type = NavType.StringType }
            )
        ) { entry ->
            MainScreen(
                profileId = entry.arguments?.getString("profileId").orEmpty(),
                onPlay = { channelId ->
                    navController.navigate("player/$channelId")
                },
                onSwitchProfile = {
                    navController.navigate("profiles") {
                        popUpTo("profiles") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = "player/{channelId}",
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType }
            )
        ) {
            PlayerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
