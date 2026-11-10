package com.alsiddiq.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.alsiddiq.app.AppContainer
import com.alsiddiq.app.R
import com.alsiddiq.app.data.demo.DemoSeed
import com.alsiddiq.app.domain.Member
import com.alsiddiq.app.domain.Role
import com.alsiddiq.app.ui.auth.LoginScreen
import com.alsiddiq.app.ui.screens.*
import com.alsiddiq.app.ui.theme.Brand
import kotlinx.coroutines.delay

data class TabItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

fun tabsFor(role: Role): List<TabItem> = when (role) {
    Role.VIEWER -> listOf(
        TabItem("home", "الرئيسية", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
        TabItem("students", "الطلاب", Icons.Outlined.School, Icons.Filled.School),
        TabItem("circles", "الحلقات", Icons.Outlined.Groups, Icons.Filled.Groups),
        // build 15: center reports are for the admin and the teachers only; the general password has no «التقارير» tab.
        TabItem("tools", "الأدوات", Icons.Outlined.GridView, Icons.Filled.GridView),
        TabItem("viewer_settings", "الإعدادات", Icons.Outlined.Settings, Icons.Filled.Settings)
    )
    Role.ADMIN -> listOf(
        TabItem("home", "الرئيسية", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
        TabItem("circles", "الحلقات", Icons.Outlined.Groups, Icons.Filled.Groups),
        TabItem("students", "الطلاب", Icons.Outlined.School, Icons.Filled.School),
        TabItem("reports", "التقارير", Icons.Outlined.Insights, Icons.Filled.Insights),
        TabItem("tools", "الأدوات", Icons.Outlined.GridView, Icons.Filled.GridView),
        TabItem("more", "الإدارة", Icons.Outlined.AdminPanelSettings, Icons.Filled.AdminPanelSettings)
    )
    Role.TEACHER -> listOf(
        TabItem("home", "الرئيسية", Icons.Outlined.Home, Icons.Filled.Home),
        TabItem("circles", "الحلقات", Icons.Outlined.Groups, Icons.Filled.Groups),
        TabItem("record_start", "تسجيل", Icons.Outlined.AddCircleOutline, Icons.Filled.AddCircle),
        TabItem("attendance", "الحضور", Icons.Outlined.FactCheck, Icons.Filled.FactCheck),
        // «المزيد» had the grid icon; it moves to «الأدوات» so the two never look alike.
        TabItem("teacher_more", "المزيد", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
        TabItem("tools", "الأدوات", Icons.Outlined.GridView, Icons.Filled.GridView)
    )
}

@Composable
fun AppRoot(container: AppContainer, autoLogin: String?, startRoute: String?, ciLogin: Pair<String, String>? = null, selfTest: Boolean = false, ciViewerPass: String? = null, ciTry: String? = null, photoTest: Boolean = false) {
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var splash by remember { mutableStateOf(autoLogin == null && ciLogin == null) }
    LaunchedEffect(Unit) { delay(1400); splash = false }

    var ciDone by remember { mutableStateOf(ciLogin == null) }
    LaunchedEffect(autoLogin) {
        // Debug/demo only: CI opens a role directly for screenshots.
        when (autoLogin) {
            "viewer" -> container.auth.signIn(DemoSeed.VIEWER_PASSWORD, null)
            "admin" -> { container.auth.signIn(DemoSeed.VIEWER_PASSWORD, null); container.auth.signIn(DemoSeed.ADMIN_PASSWORD, Role.ADMIN) }
            "teacher" -> { container.auth.signIn(DemoSeed.VIEWER_PASSWORD, null); container.auth.signIn(DemoSeed.TEACHER_PASSWORD, Role.TEACHER) }
            "gate" -> container.auth.signOut()
        }
        if (ciLogin != null) {
            val kind = when (ciLogin.first) { "admin" -> Role.ADMIN; "teacher" -> Role.TEACHER; else -> null }
            if (kind != null && container.auth.currentUser.value?.role != Role.VIEWER) {
                ciViewerPass?.let { container.auth.signIn(it, null) }
            }
            val r = container.auth.signIn(ciLogin.second, kind)
            ciDone = true
            android.util.Log.i("ALSIDDIQ_SELFTEST", "login " + (if (r.isSuccess) "ok role=" + r.getOrNull()?.role else "fail " + r.exceptionOrNull()?.message))
        }
    }

    val appScope = rememberCoroutineScope()
    CompositionLocalProvider(LocalContainer provides container, LocalSnackbar provides snackbar, LocalAppScope provides appScope) {
        AnimatedContent(
            targetState = when {
                splash -> "splash"
                user == null -> "login"
                else -> "main:" + user!!.id
            },
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) },
            label = "root"
        ) { state ->
            com.alsiddiq.app.ui.components.StatusBarIcons(lightIcons = state == "login")
            when {
                state == "splash" -> Splash()
                state == "login" -> Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) }) { pad ->
                    Box(Modifier.padding(pad)) { LoginScreen() }
                }
                else -> user?.let { MainShell(it, snackbar, if (autoLogin != null || ciLogin != null) startRoute else null, selfTest && ciDone, ciTry, photoTest) }
            }
        }
    }
}

@Composable
private fun Splash() {
    // The ORIGINAL logo, never redrawn: it fades and scales in under a soft gold glow, then the welcome line.
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    val glow = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(650))
        glow.animateTo(1f, tween(500))
    }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(230.dp).graphicsLayer { alpha = glow.value * 0.9f }.background(
                    androidx.compose.ui.graphics.Brush.radialGradient(listOf(Brand.Gold.copy(alpha = 0.35f), androidx.compose.ui.graphics.Color.Transparent)), androidx.compose.foundation.shape.CircleShape))
                Image(painterResource(R.drawable.logo), "شعار مركز الصديق",
                    Modifier.size(180.dp).graphicsLayer { alpha = appear.value; scaleX = 0.86f + 0.14f * appear.value; scaleY = 0.86f + 0.14f * appear.value })
            }
            Spacer(Modifier.height(14.dp))
            Text("أهلاً بكم في مركز الصديق", style = MaterialTheme.typography.titleMedium, color = Brand.Navy, modifier = Modifier.graphicsLayer { alpha = glow.value })
            Text("لتعليم القرآن الكريم", style = MaterialTheme.typography.bodySmall, color = Brand.Maroon, modifier = Modifier.graphicsLayer { alpha = glow.value })
        }
    }
}

@Composable
private fun MainShell(user: Member, snackbar: SnackbarHostState, startRoute: String?, selfTest: Boolean = false, ciTry: String? = null, photoTest: Boolean = false) {
    val nav = rememberNavController()
    val tabs = remember(user.role) { tabsFor(user.role) }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val showBar = tabs.any { it.route == current }
    val unread = LocalContainer.current.center.snapshot.collectAsStateWithLifecycle().value
        .notifications.count { !it.read }

    // Debug-only: CI opens a given screen for screenshots. Retries until the navigation graph is ready.
    LaunchedEffect(startRoute) {
        if (!startRoute.isNullOrBlank() && startRoute != "home") {
            for (attempt in 0 until 20) {
                delay(400)
                if (runCatching { nav.navigate(startRoute) }.isSuccess) break
            }
        }
    }
    val container = LocalContainer.current
    // Fresh data from the central database as soon as the main screen opens.
    LaunchedEffect(user.id) { runCatching { container.center.refresh() } }
    // Debug-only end-to-end check used by CI against the real database.
    if (selfTest) LaunchedEffect(user.id) { com.alsiddiq.app.ui.SelfTest.run(container, user, ciTry, photoTest) }
    // Android 13+: ask once for permission to show notifications (absence, new hifz, announcements).
    if (container.cloudConfigured && android.os.Build.VERSION.SDK_INT >= 33) {
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { }
        LaunchedEffect(Unit) { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }

    com.alsiddiq.app.ui.components.StatusBarIcons(lightIcons = current == "home")
    Scaffold(
        containerColor = Brand.Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(showBar, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                NavigationBar(containerColor = androidx.compose.ui.graphics.Color.White, tonalElevation = 0.dp) {
                    tabs.forEach { t ->
                        val sel = current == t.route
                        NavigationBarItem(
                            selected = sel,
                            onClick = {
                                if (!sel) nav.navigate(t.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (t.route == "notifications" && unread > 0) {
                                    BadgedBox(badge = { Badge(containerColor = Brand.Maroon) { Text("$unread") } }) {
                                        Icon(if (sel) t.selectedIcon else t.icon, t.label)
                                    }
                                } else Icon(if (sel) t.selectedIcon else t.icon, t.label)
                            },
                            // 6 items on a 360dp phone: a slightly smaller label on one line, never wrapped or cut.
                            label = { Text(t.label, style = if (tabs.size >= 6) MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp) else MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Brand.Navy,
                                selectedTextColor = Brand.Navy,
                                indicatorColor = Brand.NavySoft,
                                unselectedIconColor = Brand.Muted,
                                unselectedTextColor = Brand.Muted
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AppNavHost(nav, user)
        }
    }
    // «المتفوقون»: a newly published congratulation card, once, on everyone's screen.
    com.alsiddiq.app.ui.screens.HonorPopupHost()
}

@Composable
private fun AppNavHost(nav: NavHostController, user: Member) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        navController = nav,
        startDestination = "home",
        enterTransition = { fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.985f) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + scaleOut(tween(200), targetScale = 0.985f) }
    ) {
        composable("home") {
            when (user.role) {
                Role.ADMIN -> AdminHome(user, nav)
                Role.TEACHER -> TeacherHome(user, nav)
                Role.VIEWER -> ViewerHome(user, nav)
            }
        }
        composable("circles") { CirclesScreen(user, nav) }
        composable("students") { StudentsScreen(user, nav, circleFilter = null) }
        composable("reports") { if (user.role == Role.VIEWER) CenterReportsLocked(nav) else ReportsScreen(user, nav) }
        composable("more") { MoreScreen(user, nav) }
        composable("teacher_more") { TeacherMoreScreen(user, nav) }
        composable("viewer_settings") { ViewerSettingsScreen(user, nav) }
        composable("tools") { com.alsiddiq.app.ui.components.ToolsTabScreen() }
        composable("honors") { com.alsiddiq.app.ui.screens.HonorsScreen(user, nav) }
        // v3.8 (his requests of Nov 8, 9:42–10:06 PM)
        composable("teacher_attendance") { com.alsiddiq.app.ui.screens.TeacherAttendanceScreen(user, nav) }
        composable("budget") { com.alsiddiq.app.ui.screens.BudgetScreen(user, nav) }
        composable("subscriptions") { com.alsiddiq.app.ui.screens.SubscriptionsScreen(user, nav, null) }
        composable("subscriptions/{circleId}") { com.alsiddiq.app.ui.screens.SubscriptionsScreen(user, nav, it.arguments?.getString("circleId")) }
        composable("role_login/{kind}") {
            val kind = if (it.arguments?.getString("kind") == "TEACHER") Role.TEACHER else Role.ADMIN
            com.alsiddiq.app.ui.auth.RoleLoginScreen(kind) { nav.popBackStack() }
        }
        composable("teachers") { TeachersScreen(user, nav) }
        composable("teacher_edit/{id}") { TeacherEditScreen(user, nav, it.arguments?.getString("id") ?: "new") }
        composable("passwords") { PasswordsScreen(user, nav) }
        composable("circle_report/{id}") { CircleReportScreen(user, nav, it.arguments?.getString("id") ?: "") }
        composable("record_start") { RecordStartScreen(user, nav) }
        composable("attendance") { AttendanceScreen(user, nav, null) }
        composable("attendance/{circleId}") { AttendanceScreen(user, nav, it.arguments?.getString("circleId")) }
        composable("history/{sid}") { HistoryScreen(user, nav, it.arguments?.getString("sid"), onBack = back) }
        composable("rewards") { RewardsScreen(user, nav, onBack = back) }
        composable("notifications") { NotificationsScreen(user, nav, onBack = back) }
        composable("circle/{id}") { CircleDetailScreen(user, nav, it.arguments?.getString("id") ?: "") }
        composable("circle_edit/{id}") { CircleEditScreen(user, nav, it.arguments?.getString("id") ?: "new") }
        composable("student/{id}") { StudentDetailScreen(user, nav, it.arguments?.getString("id") ?: "") }
        composable(
            "student_edit/{id}?circle={circle}",
            arguments = listOf(navArgument("circle") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { StudentEditScreen(user, nav, it.arguments?.getString("id") ?: "new", it.arguments?.getString("circle")) }
        composable("record/{sid}") { RecordScreen(user, nav, it.arguments?.getString("sid") ?: "") }
        composable("announcements") { AnnouncementsScreen(user, nav) }
        composable("announce_new") { AnnounceNewScreen(user, nav) }
        composable("settings") { CenterSettingsScreen(user, nav) }
        composable("about") { AboutScreen(nav) }
    }
}
