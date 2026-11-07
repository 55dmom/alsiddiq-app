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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
    Role.ADMIN -> listOf(
        TabItem("home", "الرئيسية", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
        TabItem("circles", "الحلقات", Icons.Outlined.Groups, Icons.Filled.Groups),
        TabItem("students", "الطلاب", Icons.Outlined.School, Icons.Filled.School),
        TabItem("reports", "التقارير", Icons.Outlined.Insights, Icons.Filled.Insights),
        TabItem("more", "المزيد", Icons.Outlined.GridView, Icons.Filled.GridView)
    )
    Role.TEACHER -> listOf(
        TabItem("home", "الرئيسية", Icons.Outlined.Home, Icons.Filled.Home),
        TabItem("circles", "حلقاتي", Icons.Outlined.Groups, Icons.Filled.Groups),
        TabItem("record_start", "تسجيل", Icons.Outlined.AddCircleOutline, Icons.Filled.AddCircle),
        TabItem("attendance", "الحضور", Icons.Outlined.FactCheck, Icons.Filled.FactCheck),
        TabItem("profile", "حسابي", Icons.Outlined.Person, Icons.Filled.Person)
    )
    Role.STUDENT -> listOf(
        TabItem("home", "الرئيسية", Icons.Outlined.Home, Icons.Filled.Home),
        TabItem("history", "سجلّي", Icons.Outlined.MenuBook, Icons.Filled.MenuBook),
        TabItem("rewards", "المكافآت", Icons.Outlined.EmojiEvents, Icons.Filled.EmojiEvents),
        TabItem("notifications", "الإشعارات", Icons.Outlined.Notifications, Icons.Filled.Notifications),
        TabItem("profile", "حسابي", Icons.Outlined.Person, Icons.Filled.Person)
    )
    Role.PARENT -> listOf(
        TabItem("home", "أبنائي", Icons.Outlined.FamilyRestroom, Icons.Filled.FamilyRestroom),
        TabItem("reports", "التقارير", Icons.Outlined.Insights, Icons.Filled.Insights),
        TabItem("attendance", "الحضور", Icons.Outlined.EventAvailable, Icons.Filled.EventAvailable),
        TabItem("notifications", "الإشعارات", Icons.Outlined.Notifications, Icons.Filled.Notifications),
        TabItem("profile", "حسابي", Icons.Outlined.Person, Icons.Filled.Person)
    )
}

@Composable
fun AppRoot(container: AppContainer, autoLogin: String?, startRoute: String?) {
    val user by container.auth.currentUser.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var splash by remember { mutableStateOf(autoLogin == null) }
    LaunchedEffect(Unit) { delay(900); splash = false }

    LaunchedEffect(autoLogin) {
        val phone = when (autoLogin) {
            "admin" -> DemoSeed.ADMIN_PHONE
            "teacher" -> DemoSeed.TEACHER_PHONE
            "student" -> DemoSeed.STUDENT_PHONE
            "parent" -> DemoSeed.PARENT_PHONE
            else -> null
        }
        if (phone != null && container.auth.currentUser.value?.phone != phone) {
            container.auth.signIn(phone, DemoSeed.DEMO_PASSWORD)
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
                else -> user?.let { MainShell(it, snackbar, if (autoLogin != null) startRoute else null) }
            }
        }
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.logo), "شعار مركز الصديق", Modifier.size(180.dp))
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(color = Brand.Navy, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun MainShell(user: Member, snackbar: SnackbarHostState, startRoute: String?) {
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
                            label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
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
                Role.STUDENT -> StudentHome(user, nav)
                Role.PARENT -> ParentHome(user, nav)
            }
        }
        composable("circles") { CirclesScreen(user, nav) }
        composable("students") { StudentsScreen(user, nav, circleFilter = null) }
        composable("reports") { ReportsScreen(user, nav) }
        composable("more") { MoreScreen(user, nav) }
        composable("record_start") { RecordStartScreen(user, nav) }
        composable("attendance") { AttendanceScreen(user, nav, null) }
        composable("attendance/{circleId}") { AttendanceScreen(user, nav, it.arguments?.getString("circleId")) }
        composable("profile") { ProfileScreen(user, nav, onBack = if (user.role == Role.ADMIN) back else null) }
        composable("history") { HistoryScreen(user, nav, user.studentId, onBack = null) }
        composable("history/{sid}") { HistoryScreen(user, nav, it.arguments?.getString("sid"), onBack = back) }
        composable("rewards") { RewardsScreen(user, nav, onBack = if (user.role == Role.STUDENT) null else back) }
        composable("notifications") { NotificationsScreen(user, nav, onBack = if (user.role == Role.STUDENT || user.role == Role.PARENT) null else back) }
        composable("circle/{id}") { CircleDetailScreen(user, nav, it.arguments?.getString("id") ?: "") }
        composable("circle_edit/{id}") { CircleEditScreen(user, nav, it.arguments?.getString("id") ?: "new") }
        composable("student/{id}") { StudentDetailScreen(user, nav, it.arguments?.getString("id") ?: "") }
        composable(
            "student_edit/{id}?circle={circle}",
            arguments = listOf(navArgument("circle") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { StudentEditScreen(user, nav, it.arguments?.getString("id") ?: "new", it.arguments?.getString("circle")) }
        composable("record/{sid}") { RecordScreen(user, nav, it.arguments?.getString("sid") ?: "") }
        composable("members/{role}") { MembersScreen(user, nav, Role.valueOf(it.arguments?.getString("role") ?: "TEACHER")) }
        composable("member_edit/{role}/{id}") {
            MemberEditScreen(user, nav, Role.valueOf(it.arguments?.getString("role") ?: "TEACHER"), it.arguments?.getString("id") ?: "new")
        }
        composable("announcements") { AnnouncementsScreen(user, nav) }
        composable("announce_new") { AnnounceNewScreen(user, nav) }
        composable("settings") { CenterSettingsScreen(user, nav) }
        composable("about") { AboutScreen(nav) }
    }
}
