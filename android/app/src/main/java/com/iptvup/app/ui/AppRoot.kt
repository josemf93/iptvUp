package com.iptvup.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.iptvup.app.core.ui.components.ExitAppDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iptvup.app.core.ui.theme.Background
import com.iptvup.app.core.ui.theme.Primary
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
    val authReady by appViewModel.authReady.collectAsStateWithLifecycle()
    val startState by appViewModel.startState.collectAsStateWithLifecycle()

    when {
        !authReady -> LoadingScreen()
        user == null -> LoginScreen()
        startState is AppStartState.Loading -> LoadingScreen()
        else -> {
            val profileId = (startState as AppStartState.Ready).profileId
            AuthenticatedNavHost(startProfileId = profileId)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Primary)
    }
}

@Composable
private fun AuthenticatedNavHost(startProfileId: String?) {
    val navController = rememberNavController()
    val startDestination = if (startProfileId != null) {
        "main/$startProfileId"
    } else {
        "profiles"
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    var showExit by remember { mutableStateOf(false) }

    BackHandler(enabled = backStackEntry?.destination?.route == "profiles") {
        showExit = true
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
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
                        popUpTo(navController.graph.findStartDestination().id) {
                            inclusive = true
                        }
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

    if (showExit) {
        ExitAppDialog(onDismiss = { showExit = false })
    }
}
