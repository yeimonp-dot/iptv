package com.example.fibraconet

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.fibraconet.data.model.Channel
import com.example.fibraconet.ui.MainViewModel
import com.example.fibraconet.ui.UiState
import com.example.fibraconet.ui.screens.DashboardScreen
import com.example.fibraconet.ui.screens.LoginScreen
import com.example.fibraconet.ui.screens.PlayerScreen
import com.example.fibraconet.ui.screens.SplashScreen
import com.example.fibraconet.ui.theme.FibraconetTheme

class MainActivity : ComponentActivity() {

    // Acceso al ViewModel desde la Activity para Auto-PiP
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FibraconetTheme {
                AppNavigation(mainViewModel)
            }
        }
    }

    // Auto-PiP: cuando el usuario presiona Home/Recents con un canal activo
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            mainViewModel.currentChannel.value != null) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel) {
    val navController  = rememberNavController()
    val isLoggedIn     by viewModel.isLoggedIn.collectAsState()
    var fullscreenChannel by remember { mutableStateOf<Channel?>(null) }
    var fullscreenChannelList by remember { mutableStateOf<List<Channel>>(emptyList()) }

    NavHost(
        navController    = navController,
        startDestination = "splash",
        enterTransition  = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 } },
        exitTransition   = { fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it / 4 } },
        popEnterTransition  = { fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 4 } },
        popExitTransition   = { fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it / 4 } }
    ) {
        // ── Splash ────────────────────────────────────────────────────────────
        composable(
            "splash",
            enterTransition = { fadeIn(tween(0)) },
            exitTransition  = { fadeOut(tween(0)) }
        ) {
            SplashScreen(
                onFinished = {
                    val dest = if (isLoggedIn) "dashboard" else "login"
                    navController.navigate(dest) {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        // ── Login ─────────────────────────────────────────────────────────────
        composable("login") {
            // If session was restored while on login, jump to dashboard
            LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            }
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // ── Dashboard ─────────────────────────────────────────────────────────
        composable("dashboard") {
            DashboardScreen(
                viewModel = viewModel,
                onOpenFullscreen = { channel, channelList ->
                    fullscreenChannel = channel
                    fullscreenChannelList = channelList
                    navController.navigate("player")
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                    }
                }
            )
        }

        // ── Player fullscreen ─────────────────────────────────────────────────
        composable("player") {
            val channel = fullscreenChannel
            if (channel != null) {
                val currentIdx = fullscreenChannelList.indexOfFirst { it.id == channel.id }
                PlayerScreen(
                    channel = channel,
                    onBack  = { navController.popBackStack() },
                    onPreviousChannel = if (currentIdx > 0) {
                        { fullscreenChannel = fullscreenChannelList[currentIdx - 1] }
                    } else null,
                    onNextChannel = if (currentIdx < fullscreenChannelList.size - 1) {
                        { fullscreenChannel = fullscreenChannelList[currentIdx + 1] }
                    } else null
                )
            } else {
                navController.popBackStack()
            }
        }
    }
}
