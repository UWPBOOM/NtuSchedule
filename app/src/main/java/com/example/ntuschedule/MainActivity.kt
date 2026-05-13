package com.example.ntuschedule

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ntuschedule.ui.theme.NtuScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ⭐ 核心：系统级纯净沉浸状态栏（没有任何遮罩）
        enableEdgeToEdge()
        CourseRepository.init(applicationContext)

        // 读取全局配色偏好
        val globalColorIndexFlow = PreferencesManager.getGlobalColorIndex(this)

        setContent {
            val globalColorIndex by globalColorIndexFlow.collectAsState(initial = 0)
            val customPrimary = AccentColorOptions
                .getOrNull(globalColorIndex)
                ?.hex
                ?.let { Color(it) }

            NtuScheduleTheme(customPrimary = customPrimary) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    var showOverwriteDialog by remember { mutableStateOf(false) }
                    var tempImportedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }

                    NavHost(
                        navController = navController,
                        startDestination = "schedule",
                        // 主界面不写动画，这样退出软件时会完美调用系统默认动画（绝不淡出）
                    ) {
                        composable("schedule") {
                            ScheduleGrid(
                                // 这里把原来的导入去掉了，把智能登录改成了唯一的入口
                                onNavigateToLogin = { navController.navigate("login") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToProfileSettings = { profileId, profileName ->
                                    navController.navigate("profile_settings/$profileId/$profileName")
                                }
                            )
                        }

                        // ⭐ iOS 风格滑动动画 + FastOutSlowIn 缓动，丝滑不僵硬
                        composable(
                            "login",
                            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
                            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
                            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) },
                            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
                        ) {
                            LoginScreen(
                                onBack = { navController.popBackStack() },
                                onLoginSuccess = { html ->
                                    try {
                                        val realCourses = CourseParser.parseHtml(html)
                                        if (realCourses.isEmpty()) {
                                            Toast.makeText(this@MainActivity, "未抓取到课程", Toast.LENGTH_LONG).show()
                                        } else {
                                            tempImportedCourses = realCourses
                                            showOverwriteDialog = true
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(this@MainActivity, "解析失败", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                        }

                        composable(
                            "settings",
                            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
                            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
                            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) },
                            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
                        ) {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }

                        // 【Feature 2】课表专属设置页路由
                        composable(
                            "profile_settings/{profileId}/{profileName}",
                            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
                            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
                            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) },
                            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
                        ) { backStackEntry ->
                            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
                            val profileName = backStackEntry.arguments?.getString("profileName") ?: "课表"
                            ProfileSettingsScreen(
                                profileId = profileId,
                                profileName = profileName,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    if (showOverwriteDialog) {
                        AlertDialog(
                            onDismissRequest = { showOverwriteDialog = false },
                            title = { Text("导入成功") },
                            text = { Text("共识别到 ${tempImportedCourses.size} 节课。你要覆盖当前课表，还是追加进去？") },
                            confirmButton = {
                                TextButton(onClick = {
                                    CourseRepository.importCoursesToCurrentProfile(tempImportedCourses, true)
                                    showOverwriteDialog = false
                                    navController.popBackStack()
                                }) { Text("覆盖旧数据") }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    CourseRepository.importCoursesToCurrentProfile(tempImportedCourses, false)
                                    showOverwriteDialog = false
                                    navController.popBackStack()
                                }) { Text("追加新数据") }
                            }
                        )
                    }
                }
            }
        }
    }
}