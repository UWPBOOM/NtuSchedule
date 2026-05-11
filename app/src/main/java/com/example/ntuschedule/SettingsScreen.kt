package com.example.ntuschedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Google Material 全局配色选项
data class AccentColorOption(val name: String, val hex: Long)
val AccentColorOptions = listOf(
    AccentColorOption("默认",    0xFF6750A4),  // M3 Purple
    AccentColorOption("蔚蓝",    0xFF1976D2),  // Blue
    AccentColorOption("赤红",    0xFFC62828),  // Red
    AccentColorOption("翠绿",    0xFF2E7D32),  // Green
    AccentColorOption("橙黄",    0xFFEF6C00),  // Orange
    AccentColorOption("青绿",    0xFF00796B),  // Teal
    AccentColorOption("粉红",    0xFFAD1457),  // Pink
    AccentColorOption("靛蓝",    0xFF283593),  // Indigo
)

// 预览假课程 — 4门，模拟真实大学课表乱排
// 格式: Triple(课程名, 教室, 颜色索引) to Triple(星期几, 起始节, 结束节)
private val PreviewCourses = listOf(
    Triple("高数A(Ⅰ)", "逸夫楼201", 0) to Triple(1, 3, 4),    // 周一3-4节
    Triple("大学物理", "物理楼101", 1) to Triple(3, 1, 3),    // 周三1-3节连堂
    Triple("数据结构", "计算机楼", 3) to Triple(5, 2, 4),     // 周五2-4节
    Triple("形策", "综合楼301", 0) to Triple(2, 1, 1),        // 周二单节
)

// ══════════════════════════════════════
//  设置子页面枚举
// ══════════════════════════════════════
private enum class SettingsSubScreen { MAIN, THEME, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDatePicker by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val startDateMillis by PreferencesManager.getStartDate(context).collectAsState(initial = System.currentTimeMillis())
    val themeIndex by PreferencesManager.getThemeIndex(context).collectAsState(initial = 0)
    val globalColorIndex by PreferencesManager.getGlobalColorIndex(context).collectAsState(initial = 0)
    val wallpaperUri by PreferencesManager.getWallpaperUri(context).collectAsState(initial = "")

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            scope.launch { PreferencesManager.saveWallpaperUri(context, it.toString()) }
            Toast.makeText(context, "壁纸设置成功！", Toast.LENGTH_SHORT).show()
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        scope.launch { PreferencesManager.saveStartDate(context, it) }
                        Toast.makeText(context, "开学日期已保存！", Toast.LENGTH_SHORT).show()
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } }
        ) { DatePicker(state = datePickerState) }
    }

    val dateStr = remember(startDateMillis) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startDateMillis))
    }

    // ───── 带动画的主题设置 / 关于 / 主设置切换 ─────
    val settingsSubScreen = when {
        showThemeSettings -> SettingsSubScreen.THEME
        showAbout -> SettingsSubScreen.ABOUT
        else -> SettingsSubScreen.MAIN
    }

    AnimatedContent(
        targetState = settingsSubScreen,
        transitionSpec = {
            if (targetState != SettingsSubScreen.MAIN) {
                (slideInHorizontally { it } togetherWith slideOutHorizontally { -it })
            } else {
                (slideInHorizontally { -it } togetherWith slideOutHorizontally { it })
            }
        },
        label = "settingsSubScreen"
    ) { screen ->
        when (screen) {
            SettingsSubScreen.THEME -> ThemeSettingsSubScreen(
                themeIndex = themeIndex,
                globalColorIndex = globalColorIndex,
                wallpaperUri = wallpaperUri,
                onBack = { showThemeSettings = false },
                onSelectTheme = { scope.launch { PreferencesManager.saveThemeIndex(context, it) } },
                onSelectGlobalColor = { scope.launch { PreferencesManager.saveGlobalColorIndex(context, it) } },
                onPickWallpaper = { imagePickerLauncher.launch(arrayOf("image/*")) },
                onClearWallpaper = { scope.launch { PreferencesManager.saveWallpaperUri(context, "") } }
            )
            SettingsSubScreen.ABOUT -> AboutSubScreen(
                onBack = { showAbout = false }
            )
            SettingsSubScreen.MAIN -> MainSettingsContent(
                dateStr = dateStr,
                onThemeSettings = { showThemeSettings = true },
                onDatePicker = { showDatePicker = true },
                onAbout = { showAbout = true },
                onBack = onBack
            )
        }
    }
}

// ══════════════════════════════════════
//  主设置列表
// ══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MainSettingsContent(
    dateStr: String,
    onThemeSettings: () -> Unit,
    onDatePicker: () -> Unit,
    onAbout: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个性化设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("主题设置") },
                        supportingContent = { Text("配色方案、壁纸与预览") },
                        leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.KeyboardArrowRight, "进入") },
                        modifier = Modifier.clickable { onThemeSettings() }
                    )

                    Divider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    ListItem(
                        headlineContent = { Text("设置第一周的第一天") },
                        supportingContent = { Text("当前: $dateStr") },
                        leadingContent = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onDatePicker() }
                    )

                    Divider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    ListItem(
                        headlineContent = { Text("关于") },
                        supportingContent = { Text("当前版本 · 检查更新") },
                        leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = { Icon(Icons.Default.KeyboardArrowRight, "进入") },
                        modifier = Modifier.clickable { onAbout() }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Copyright © 2026 UWPBOOM", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text("All Rights Reserved", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

// ══════════════════════════════════════
//  主题设置子页面（二级菜单）
// ══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ThemeSettingsSubScreen(
    themeIndex: Int,
    globalColorIndex: Int,
    wallpaperUri: String,
    onBack: () -> Unit,
    onSelectTheme: (Int) -> Unit,
    onSelectGlobalColor: (Int) -> Unit,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit
) {
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }

    val safeIndex = themeIndex.coerceIn(0, Palettes.size - 1)
    val colors = Palettes[safeIndex]
    val textMain = MaterialTheme.colorScheme.onSurface
    val textSub = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val gridLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题设置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            // ====== 课程 UI 预览台（7天课表风格） ======
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                        if (wallpaperUri.isNotBlank()) {
                            AsyncImage(
                                model = wallpaperUri,
                                contentDescription = "壁纸预览",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                                alpha = 0.55f
                            )
                        }
                        Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                            // ── 星期栏 ──
                            Row(modifier = Modifier.fillMaxWidth().height(22.dp)) {
                                Spacer(modifier = Modifier.width(28.dp))
                                listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        Text(day, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = textMain)
                                    }
                                }
                            }
                            // ── 课程网格 ──
                            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                val w = maxWidth; val h = maxHeight
                                val timeColW = 28.dp; val dayW = (w - timeColW) / 7; val periodH = h / 4

                                for (i in 1..3) {
                                    Box(Modifier.offset(y = periodH * i).fillMaxWidth().height(0.5.dp).background(gridLine))
                                }
                                for (d in 1..6) {
                                    Box(Modifier.offset(x = timeColW + dayW * d).width(0.5.dp).fillMaxHeight().background(gridLine))
                                }
                                Column(modifier = Modifier.width(timeColW).fillMaxHeight()) {
                                    for (i in 1..4) {
                                        val times = getSectionTime(i, true)
                                        Box(Modifier.fillMaxWidth().height(periodH), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("$i", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = textMain)
                                                Text(times.first, fontSize = 6.sp, color = textSub)
                                            }
                                        }
                                    }
                                }
                                Box(modifier = Modifier.offset(x = timeColW).fillMaxHeight().width(w - timeColW)) {
                                    PreviewCourses.forEach { (courseData, dayPeriod) ->
                                        val (name, room, colorIdx) = courseData
                                        val (day, startPeriod, endPeriod) = dayPeriod
                                        Box(
                                            modifier = Modifier
                                                .offset(x = dayW * (day - 1) + 2.dp, y = periodH * (startPeriod - 1) + 2.dp)
                                                .size(dayW - 4.dp, periodH * (endPeriod - startPeriod + 1) - 4.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors[colorIdx % colors.size].copy(alpha = 0.92f))
                                                .padding(2.dp)
                                        ) {
                                            Column {
                                                Text(name, fontSize = 7.sp, fontWeight = FontWeight.Bold,
                                                    maxLines = 3, lineHeight = 9.sp, color = Color.Black)
                                                Spacer(modifier = Modifier.height(1.dp))
                                                Text("@$room", fontSize = 6.sp,
                                                    color = Color.Black.copy(alpha = 0.7f), maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Text("UI 预览", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
            }

            // ====== 主题配色 + 全局配色 + 壁纸 统一 Card ======
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column {
                    // ── 主题配色 ──
                    Text("主题配色",
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary)

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("莫兰迪", "马卡龙", "MD3").forEachIndexed { index, name ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onSelectTheme(index) }) {
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Palettes.getOrElse(index) { Palettes[0] }[0]),
                                    contentAlignment = Alignment.Center) {
                                    if (themeIndex == index) Icon(Icons.Default.Check, null, tint = Color.Black)
                                }
                                Spacer(Modifier.height(4.dp)); Text(name, fontSize = 12.sp)
                            }
                        }
                    }

                    Divider(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // ── 更改全局配色 ──
                    var showGlobalColorPicker by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text("更改全局配色") },
                        supportingContent = { Text("改变重点文字和图标的颜色") },
                        leadingContent = {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(
                                if (globalColorIndex > 0 && globalColorIndex < AccentColorOptions.size) Color(AccentColorOptions[globalColorIndex].hex)
                                else MaterialTheme.colorScheme.primary))
                        },
                        trailingContent = {
                            Icon(if (showGlobalColorPicker) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "展开")
                        },
                        modifier = Modifier.clickable { showGlobalColorPicker = !showGlobalColorPicker }
                    )

                    if (showGlobalColorPicker) {
                        AccentColorOptions.forEachIndexed { index, (name, hex) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectGlobalColor(index) }
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(hex)))
                                Spacer(Modifier.width(14.dp))
                                Text(name, fontSize = 14.sp,
                                    fontWeight = if (index == globalColorIndex) FontWeight.Medium else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f))
                                if (index == globalColorIndex) {
                                    Icon(Icons.Default.Check, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                            if (index < AccentColorOptions.size - 1) {
                                Divider(
                                    modifier = Modifier.padding(start = 58.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    Divider(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    // ── 自定义壁纸 ──
                    ListItem(
                        headlineContent = { Text("设置自定义壁纸") },
                        supportingContent = { Text(if (wallpaperUri.isNotBlank()) "已开启 (长按清除)" else "选择一张图片") },
                        leadingContent = { Icon(Icons.Default.Face, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.combinedClickable(
                            onClick = { if (wallpaperUri.isBlank()) onPickWallpaper() },
                            onLongClick = { if (wallpaperUri.isNotBlank()) onClearWallpaper() }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ══════════════════════════════════════
//  关于子页面
// ══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSubScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var versionTapCount by remember { mutableStateOf(0) }

    androidx.activity.compose.BackHandler(enabled = true) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("当前版本") },
                        supportingContent = { Text("V 1.3.2") },
                        leadingContent = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            versionTapCount++
                            if (versionTapCount >= 3) {
                                versionTapCount = 0
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("author_qq", "2451177632"))
                                Toast.makeText(context, "已复制作者QQ，添加QQ以提供反馈", Toast.LENGTH_LONG).show()
                            }
                        }
                    )

                    Divider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    ListItem(
                        headlineContent = { Text("检查更新") },
                        supportingContent = { Text("前往 GitHub Releases 查看最新版本") },
                        leadingContent = { Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/UWPBOOM/NtuSchedule/releases"))
                            context.startActivity(intent)
                        }
                    )

                    Divider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )

                    ListItem(
                        headlineContent = { Text("开源许可") },
                        supportingContent = { Text("本项目基于 GPL-3.0 协议开源") },
                        leadingContent = { Icon(Icons.Default.Share, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Copyright © 2026 UWPBOOM", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text("All Rights Reserved", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}