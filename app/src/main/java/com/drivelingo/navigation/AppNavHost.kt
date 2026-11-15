package com.drivelingo.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.drivelingo.app.LocalAppContainer
import com.drivelingo.feature.course.CourseScreen
import com.drivelingo.feature.home.HomeScreen
import com.drivelingo.feature.lesson.LessonScreen
import com.drivelingo.feature.onboarding.OnboardingScreen
import com.drivelingo.feature.report.ReportScreen
import com.drivelingo.feature.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val LESSON = "lesson"
    const val REPORT = "report"
    const val COURSE = "course"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    val container = LocalAppContainer.current
    val start = if (container.progress.settings.value.onboarded) Routes.HOME else Routes.ONBOARDING

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = { navController.navigateClearing(Routes.HOME) })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onStartLesson = { navController.navigate(Routes.LESSON) { launchSingleTop = true } },
                onOpenCourse = { navController.navigate(Routes.COURSE) },
                onOpenReport = { navController.navigate(Routes.REPORT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LESSON) {
            LessonScreen(
                onFinished = {
                    navController.navigate(Routes.REPORT) { popUpTo(Routes.HOME) }
                },
                onExit = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(Routes.REPORT) {
            ReportScreen(onBack = { navController.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.COURSE) {
            CourseScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onRetakeLevelTest = { navController.navigateClearing(Routes.ONBOARDING) },
            )
        }
    }
}

private fun NavHostController.navigateClearing(route: String) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
