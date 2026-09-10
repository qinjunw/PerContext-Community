package com.percontext.app.feature.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.percontext.app.R
import com.percontext.app.feature.record.RecordRoute
import com.percontext.app.feature.record.RecordViewModel
import com.percontext.app.feature.review.ReviewRoute
import com.percontext.app.feature.review.ReviewViewModel
import com.percontext.app.feature.settings.SettingsRoute
import com.percontext.app.feature.settings.SettingsViewModel
import com.percontext.app.ui.theme.CanvasColor
import com.percontext.app.ui.theme.LocalColor
import com.percontext.app.ui.theme.MutedColor

@Composable
fun PerContextApp(
    recordViewModel: RecordViewModel,
    reviewViewModel: ReviewViewModel,
    settingsViewModel: SettingsViewModel,
    onStartRecording: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.RECORD) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    fun closeSettings() {
        performSettingsClose(
            onScreenClosed = settingsViewModel::onScreenClosed,
            closeScreen = { settingsOpen = false },
        )
    }

    BackHandler(enabled = settingsOpen) { closeSettings() }

    Scaffold(
        containerColor = CanvasColor,
        bottomBar = {
            if (!settingsOpen) {
                AppBottomBar(
                    selectedTab = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }
        },
    ) { contentPadding ->
        Box(modifier = Modifier.padding(contentPadding)) {
            when {
                settingsOpen -> SettingsRoute(
                    viewModel = settingsViewModel,
                    onBack = { closeSettings() },
                )
                selectedTab == AppTab.RECORD -> RecordRoute(
                    viewModel = recordViewModel,
                    onStartRecording = onStartRecording,
                    onOpenSettings = { settingsOpen = true },
                )
                else -> ReviewRoute(
                    viewModel = reviewViewModel,
                    onOpenSettings = { settingsOpen = true },
                )
            }
        }
    }
}

internal fun performSettingsClose(
    onScreenClosed: () -> Unit,
    closeScreen: () -> Unit,
) {
    onScreenClosed()
    closeScreen()
}

@Composable
private fun AppBottomBar(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = CanvasColor,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = selectedTab == AppTab.RECORD,
            onClick = { onSelect(AppTab.RECORD) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_mic),
                    contentDescription = null,
                )
            },
            label = { Text("记录") },
            colors = navigationItemColors(),
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.REVIEW,
            onClick = { onSelect(AppTab.REVIEW) },
            icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            label = { Text("回顾") },
            colors = navigationItemColors(),
        )
    }
}

@Composable
private fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = LocalColor,
    selectedTextColor = LocalColor,
    indicatorColor = LocalColor.copy(alpha = 0.12f),
    unselectedIconColor = MutedColor,
    unselectedTextColor = MutedColor,
)

private enum class AppTab {
    RECORD,
    REVIEW,
}
