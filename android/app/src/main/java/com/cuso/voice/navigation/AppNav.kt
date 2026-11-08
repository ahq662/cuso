package com.cuso.voice.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cuso.voice.CusoApp
import com.cuso.voice.data.SessionStore
import com.cuso.voice.ui.screens.auth.LoginScreen
import com.cuso.voice.ui.screens.auth.RegisterScreen
import com.cuso.voice.ui.screens.detail.ChannelDetailScreen
import com.cuso.voice.ui.screens.main.MainScreen
import com.cuso.voice.ui.screens.voice.VoiceRoomScreen

@Composable
fun AppNav() {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val app = context.applicationContext as CusoApp
    val voiceSession = app.voiceSession
    val navController = rememberNavController()
    val startDestination = if (session.token != null) "main" else "login"

    // 进应用时如果还在语音中（或上次通话被中断），直接回到语音房
    LaunchedEffect(Unit) {
        if (startDestination == "main") {
            val target = voiceSession.channelId.value ?: voiceSession.persistedChannelId()
            if (target != null) {
                navController.navigate("voice/$target") { launchSingleTop = true }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                onLoggedIn = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onGoRegister = { navController.navigate("register") },
            )
        }

        composable("register") {
            RegisterScreen(
                onRegistered = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onGoLogin = { navController.popBackStack() },
            )
        }

        composable("main") {
            MainScreen(
                onOpenChannel = { id -> navController.navigate("detail/$id") },
                onResumeVoice = { id ->
                    navController.navigate("voice/$id") { launchSingleTop = true }
                },
                onLogout = {
                    voiceSession.clearPersisted()
                    session.clear()
                    navController.navigate("login") {
                        popUpTo("main") { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = "detail/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            ChannelDetailScreen(
                channelId = id,
                onBack = { navController.popBackStack() },
                onEnterVoice = { navController.navigate("voice/$id") },
            )
        }

        composable(
            route = "voice/{id}",
            arguments = listOf(navArgument("id") { type = NavType.IntType }),
        ) { entry ->
            val id = entry.arguments?.getInt("id") ?: 0
            VoiceRoomScreen(
                channelId = id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
