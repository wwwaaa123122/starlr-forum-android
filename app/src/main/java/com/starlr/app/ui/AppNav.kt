package com.starlr.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.starlr.app.AppContainer
import com.starlr.app.ui.screens.bookmarks.BookmarksScreen
import com.starlr.app.ui.screens.compose.ComposeScreen
import com.starlr.app.ui.screens.detail.PostDetailScreen
import com.starlr.app.ui.screens.explore.ExploreScreen
import com.starlr.app.ui.screens.home.HomeScreen
import com.starlr.app.ui.screens.login.LoginScreen
import com.starlr.app.ui.screens.notifications.NotificationsScreen
import com.starlr.app.ui.screens.profile.ProfileScreen
import com.starlr.app.ui.screens.settings.SettingsScreen
import com.starlr.app.ui.screens.admin.AdminReportsScreen
import com.starlr.app.ui.screens.SessionViewModel

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val NOTIFICATIONS = "notifications"
    const val ME = "me"
    const val BOOKMARKS = "bookmarks"
    const val ADMIN_REPORTS = "admin/reports"
    const val PROFILE = "u/{username}"
    const val DETAIL = "post/{postId}"
    const val COMPOSE = "compose?replyTo={replyTo}"
    const val LOGIN = "login"
    const val SETTINGS = "settings"

    fun profile(username: String) = "u/$username"
    fun detail(postId: String) = "post/$postId"
    fun compose(replyTo: String? = null) = if (replyTo == null) "compose" else "compose?replyTo=$replyTo"
}

private data class Tab(
    val route: String,
    val label: String,
    val on: ImageVector,
    val off: ImageVector,
)

private val TABS = listOf(
    Tab(Routes.HOME, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    Tab(Routes.EXPLORE, "探索", Icons.Filled.Search, Icons.Outlined.Search),
    Tab(Routes.NOTIFICATIONS, "通知", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    Tab(Routes.ME, "我", Icons.Filled.Person, Icons.Outlined.Person),
)

/**
 * Starlr 应用壳：底部四标签 + 发帖 FAB（X 移动端形态）。
 * 详情 / 发帖 / 登录 / 设置 为全屏路由（无底栏）。
 */
@Composable
fun StarlrApp(container: AppContainer) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val sessionVm: SessionViewModel = viewModel(factory = SessionViewModel.factory(container))
    val session by sessionVm.session.collectAsState()

    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBars = currentRoute in TABS.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBars) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    nav.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { inclusive = tab.route == Routes.HOME }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = {
                                if (tab.route == Routes.NOTIFICATIONS && session.unread > 0) {
                                    BadgedBox(badge = { Badge { Text(if (session.unread > 99) "99+" else "${session.unread}") } }) {
                                        Icon(if (selected) tab.on else tab.off, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(if (selected) tab.on else tab.off, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showBars && session.loggedIn) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Routes.compose()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Filled.Add, contentDescription = "发帖") },
                    text = { Text("发帖") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(
                navController = nav,
                startDestination = Routes.HOME,
                modifier = Modifier,
            ) {
                composable(Routes.HOME) {
                    HomeScreen(
                        container = container,
                        onOpenPost = { nav.navigate(Routes.detail(it)) },
                        onOpenUser = { nav.navigate(Routes.profile(it)) },
                        onTag = { nav.navigate("${Routes.EXPLORE}?tag=$it") },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                    )
                }
                composable(
                    route = "${Routes.EXPLORE}?tag={tag}",
                    arguments = listOf(navArgument("tag") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    ExploreScreen(
                        container = container,
                        initialTag = entry.arguments?.getString("tag").orEmpty(),
                        onOpenPost = { nav.navigate(Routes.detail(it)) },
                        onOpenUser = { nav.navigate(Routes.profile(it)) },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                    )
                }
                composable(Routes.NOTIFICATIONS) {
                    NotificationsScreen(
                        container = container,
                        loggedIn = session.loggedIn,
                        onOpenPost = { nav.navigate(Routes.detail(it)) },
                        onOpenReports = { nav.navigate(Routes.ADMIN_REPORTS) },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                        onUnreadChanged = { sessionVm.refreshUnread() },
                    )
                }
                composable(Routes.ME) {
                    ProfileScreen(
                        container = container,
                        username = null,
                        loggedIn = session.loggedIn,
                        onOpenPost = { nav.navigate(Routes.detail(it)) },
                        onOpenUser = { nav.navigate(Routes.profile(it)) },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                        onSettings = { nav.navigate(Routes.SETTINGS) },
                        onBookmarks = { nav.navigate(Routes.BOOKMARKS) },
                        onBack = null,
                    )
                }
                composable(Routes.BOOKMARKS) {
                    BookmarksScreen(
                        container = container,
                        onBack = { nav.popBackStack() },
                        onOpenPost = { nav.navigate(Routes.detail(it)) },
                        onOpenUser = { nav.navigate(Routes.profile(it)) },
                    )
                }
                composable(
                    route = Routes.PROFILE,
                    arguments = listOf(navArgument("username") { type = NavType.StringType }),
                ) { entry ->
                    ProfileScreen(
                        container = container,
                        username = entry.arguments?.getString("username"),
                        loggedIn = session.loggedIn,
                        onOpenPost = { nav.navigate(Routes.detail(it)) },
                        onOpenUser = { nav.navigate(Routes.profile(it)) },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                        onSettings = { nav.navigate(Routes.SETTINGS) },
                        onBookmarks = { nav.navigate(Routes.BOOKMARKS) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    route = Routes.DETAIL,
                    arguments = listOf(navArgument("postId") { type = NavType.StringType }),
                ) { entry ->
                    PostDetailScreen(
                        container = container,
                        postId = entry.arguments?.getString("postId").orEmpty(),
                        loggedIn = session.loggedIn,
                        onBack = { nav.popBackStack() },
                        onOpenUser = { nav.navigate(Routes.profile(it)) },
                        onLogin = { nav.navigate(Routes.LOGIN) },
                        onReply = { postId -> nav.navigate(Routes.compose(postId)) },
                    )
                }
                composable(
                    route = Routes.COMPOSE,
                    arguments = listOf(navArgument("replyTo") { type = NavType.StringType; defaultValue = "" }),
                ) { entry ->
                    ComposeScreen(
                        container = container,
                        replyTo = entry.arguments?.getString("replyTo").orEmpty().ifBlank { null },
                        onBack = { nav.popBackStack() },
                        onDone = { postId ->
                            nav.popBackStack()
                            postId?.let { nav.navigate(Routes.detail(it)) }
                        },
                    )
                }
                composable(Routes.LOGIN) {
                    LoginScreen(
                        container = container,
                        onDone = { nav.popBackStack() },
                    )
                }
                composable(Routes.ADMIN_REPORTS) {
                    val canModerate = session.user?.role == "admin" || session.user?.role == "moderator"
                    if (canModerate) {
                        AdminReportsScreen(
                            container = container,
                            onBack = { nav.popBackStack() },
                            onOpenPost = { nav.navigate(Routes.detail(it)) },
                        )
                    } else {
                        // 非管理角色不可进入（后端 JWT 鉴权兜底）
                        nav.popBackStack()
                    }
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        container = container,
                        onBack = { nav.popBackStack() },
                        onLoggedOut = {
                            nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                        },
                    )
                }
            }
        }
    }
}
