package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.AppContainer
import com.example.R
import com.example.ui.chat.ApprovalDialog
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.memory.MemoryScreen
import com.example.ui.memory.MemoryViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.skills.SkillScreen
import com.example.ui.skills.SkillViewModel
import com.example.ui.tasks.ScheduledTaskScreen
import com.example.ui.tasks.ScheduledTaskViewModel

object ArunaRoutes {
    const val CHAT = "chat"
    const val MEMORY = "memory"
    const val SKILLS = "skills"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
}

private data class NavDestinationItem(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val NAV_ITEMS = listOf(
    NavDestinationItem(ArunaRoutes.CHAT, R.string.nav_chat, Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chat"),
    NavDestinationItem(ArunaRoutes.MEMORY, R.string.nav_memory, Icons.Filled.Psychology, Icons.Outlined.Psychology, "nav_memory"),
    NavDestinationItem(ArunaRoutes.SKILLS, R.string.nav_skills, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_skills"),
    NavDestinationItem(ArunaRoutes.TASKS, R.string.nav_tasks, Icons.Filled.Schedule, Icons.Outlined.Schedule, "nav_tasks"),
    NavDestinationItem(ArunaRoutes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings"),
)

@Composable
fun ArunaNavShell(
    container: AppContainer,
    widthSizeClass: WindowWidthSizeClass,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ArunaRoutes.CHAT
    val isWideScreen = widthSizeClass != WindowWidthSizeClass.Compact

    val pendingApprovals by container.taskAndApprovalRepository
        .observePendingApprovals()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingCount = pendingApprovals.size

    val foregroundApproval by container.foregroundApproval.collectAsStateWithLifecycle()

    // Request POST_NOTIFICATIONS runtime permission on Android 13+
    val context = LocalContext.current
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val navGraphContent: @Composable (Modifier) -> Unit = { mod ->
        NavHost(
            navController = navController,
            startDestination = ArunaRoutes.CHAT,
            modifier = mod,
        ) {
            composable(ArunaRoutes.CHAT) {
                val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(container))
                ChatScreen(
                    viewModel = vm,
                    isWideScreen = isWideScreen,
                    onNavigateToSettings = { navigateTo(ArunaRoutes.SETTINGS) },
                )
            }
            composable(ArunaRoutes.MEMORY) {
                val vm: MemoryViewModel = viewModel(factory = MemoryViewModel.factory(container))
                MemoryScreen(viewModel = vm, isWideScreen = isWideScreen)
            }
            composable(ArunaRoutes.SKILLS) {
                val vm: SkillViewModel = viewModel(factory = SkillViewModel.factory(container))
                SkillScreen(viewModel = vm, isWideScreen = isWideScreen)
            }
            composable(ArunaRoutes.TASKS) {
                val vm: ScheduledTaskViewModel = viewModel(factory = ScheduledTaskViewModel.factory(container))
                ScheduledTaskScreen(viewModel = vm)
            }
            composable(ArunaRoutes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                SettingsScreen(viewModel = vm)
            }
        }
    }

    if (isWideScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                windowInsets = WindowInsets.safeDrawing,
            ) {
                NAV_ITEMS.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationRailItem(
                        selected = selected,
                        onClick = { navigateTo(item.route) },
                        icon = {
                            NavIconWithBadge(
                                icon = if (selected) item.selectedIcon else item.unselectedIcon,
                                label = stringResource(item.labelRes),
                                badgeCount = if (item.route == ArunaRoutes.TASKS) pendingCount else 0,
                            )
                        },
                        label = { Text(stringResource(item.labelRes)) },
                        modifier = Modifier.testTag(item.testTag),
                    )
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                navGraphContent(Modifier.fillMaxSize())
            }
        }
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(windowInsets = WindowInsets.navigationBars) {
                    NAV_ITEMS.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(item.route) },
                            icon = {
                                NavIconWithBadge(
                                    icon = if (selected) item.selectedIcon else item.unselectedIcon,
                                    label = stringResource(item.labelRes),
                                    badgeCount = if (item.route == ArunaRoutes.TASKS) pendingCount else 0,
                                )
                            },
                            label = { Text(stringResource(item.labelRes)) },
                            modifier = Modifier.testTag(item.testTag),
                        )
                    }
                }
            },
        ) { innerPadding ->
            navGraphContent(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            )
        }
    }

    foregroundApproval?.let { prompt ->
        ApprovalDialog(
            request = prompt.request,
            onApprove = { container.resolveForegroundApproval(true) },
            onReject = { container.resolveForegroundApproval(false) },
        )
    }
}

@Composable
private fun NavIconWithBadge(
    icon: ImageVector,
    label: String,
    badgeCount: Int,
) {
    if (badgeCount > 0) {
        BadgedBox(badge = { Badge { Text(badgeCount.toString()) } }) {
            Icon(imageVector = icon, contentDescription = label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = label)
    }
}
