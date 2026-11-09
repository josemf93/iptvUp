package com.iptvup.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptvup.app.core.ui.components.BrandLogo
import com.iptvup.app.core.ui.components.ExitAppDialog
import com.iptvup.app.core.ui.theme.Background
import com.iptvup.app.core.ui.theme.Primary
import com.iptvup.app.core.ui.theme.SurfaceDark
import com.iptvup.app.core.ui.util.isTelevision
import com.iptvup.app.ui.epg.EpgScreen
import com.iptvup.app.ui.favorites.FavoritesScreen
import com.iptvup.app.ui.home.HomeScreen
import com.iptvup.app.ui.live.LiveScreen
import com.iptvup.app.ui.settings.SettingsScreen
import com.iptvup.app.ui.update.UpdateDialog

private enum class MainTab(
    val label: String,
    val icon: ImageVector
) {
    HOME("Inicio", Icons.Rounded.Home),
    LIVE("TV", Icons.Rounded.LiveTv),
    EPG("Guía", Icons.Rounded.CalendarMonth),
    FAVORITES("Favoritos", Icons.Rounded.Star),
    SETTINGS("Ajustes", Icons.Rounded.Settings)
}

@Composable
fun MainScreen(
    profileId: String,
    onPlay: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val television = isTelevision()
    var tabName by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    val tab = MainTab.valueOf(tabName)
    var showExit by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkUpdates(manual = false)
    }

    BackHandler {
        if (tab == MainTab.HOME) {
            showExit = true
        } else {
            tabName = MainTab.HOME.name
        }
    }

    val content: @Composable () -> Unit = {
        when (tab) {
            MainTab.HOME -> HomeScreen(
                profile = profile,
                onPlay = onPlay
            )

            MainTab.LIVE -> LiveScreen(onPlay = onPlay)
            MainTab.EPG -> EpgScreen(onPlay = onPlay)
            MainTab.FAVORITES -> FavoritesScreen(
                onPlay = onPlay,
                onGoLive = { tabName = MainTab.LIVE.name }
            )

            MainTab.SETTINGS -> SettingsScreen(onSwitchProfile = onSwitchProfile)
        }
    }

    if (television) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = SurfaceDark,
                header = {
                    Box(modifier = Modifier.padding(vertical = 12.dp)) {
                        BrandLogo(showName = false)
                    }
                }
            ) {
                MainTab.entries.forEach { item ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()
                    val focusShape = RoundedCornerShape(16.dp)
                    NavigationRailItem(
                        selected = tab == item,
                        onClick = { tabName = item.name },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        alwaysShowLabel = true,
                        interactionSource = interactionSource,
                        modifier = if (focused) {
                            Modifier
                                .clip(focusShape)
                                .background(Primary.copy(alpha = 0.2f))
                                .border(3.dp, Primary, focusShape)
                        } else {
                            Modifier
                        }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                content()
            }
        }
    } else {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(containerColor = SurfaceDark) {
                    MainTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tabName = item.name },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                content()
            }
        }
    }

    UpdateDialog(
        state = updateState,
        onInstall = viewModel::installUpdate,
        onSkip = viewModel::skipUpdate,
        onDismiss = viewModel::dismissUpdate
    )

    if (showExit) {
        ExitAppDialog(onDismiss = { showExit = false })
    }
}
