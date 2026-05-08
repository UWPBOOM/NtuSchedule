package com.example.ntuschedule

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
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

// 预览假课程 — 7天随机排布 (day, start, end, name, room, colorIdx)
private val PreviewCourses = listOf(
    Triple("高等数学A(Ⅰ)", "逸夫教学楼", 0) to (1 to 1),   // 周一1-2节
    Triple("大学英语(3)", "综合楼301", 1) to (2 to 1),     // 周二1-2节
    Triple("数据结构", "计算机楼201", 4) to (3 to 3),      // 周三3-4节
    Triple("线性代数", "逸夫教学楼", 2) to (1 to 3),       // 周一3-4节
    Triple("体育(3)", "体育馆", 5) to (4 to 1),            // 周四1-2节
    Triple("思想道德", "综合楼201", 3) to (2 to 3),        // 周二3-4节
    Triple("大学物理B", "物理楼101", 0) to (5 to 1),       // 周五1-2节
    Triple("C++程序", "计算机楼303", 1) to (3 to 1),       // 周三1-2节
    Triple("大学英语", "综合楼301", 4) to (5 to 3),        // 周五3-4节
    Triple("高等数学", "逸夫教学楼", 2) to (4 to 3),       // 周四3-4节
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDatePicker by remember { mutableStateOf(false) }
    var showThemeSettings by remember { mutableStateOf(false) }
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

    // ───── 版本号彩蛋 ─────
    var versionTapCount by remember { mutableStateOf(0) }

    // ───── 带动画的主题设置 / 主设置切换 ─────
    AnimatedContent(
        targetState = showThemeSettings,
        transitionSpec = {
            if (targetState) {
                // 进入主题设置：新页从右滑入，旧页向左滑出
                (slideInHorizontally { it } togetherWith slideOutHorizontally { -it })
            } else {
                // 返回主设置：旧页向右滑出，新页从左滑入
                (slideInHorizontally { -it } togetherWith slideOutHorizontally { it })
            }
        },
        label = "themeSettings"
    ) { inTheme ->
        if (inTheme) {
            ThemeSettingsSubScreen(
                themeIndex = themeIndex,
                globalColorIndex = globalColorIndex,
                wallpaperUri = wallpaperUri,
                onBack = { showThemeSettings = false },
                onSelectTheme = { scope.launch { PreferencesManager.saveThemeIndex(context, it) } },
                onSelectGlobalColor = { scope.launch { PreferencesManager.saveGlobalColorIndex(context, it) } },
                onPickWallpaper = { imagePickerLauncher.launch(arrayOf("image/*")) },
                onClearWallpaper = { scope.launch { PreferencesManager.saveWallpaperUri(context, "") } }
            )
        } else {
            MainSettingsContent(
                dateStr = dateStr,
                onThemeSettings = { showThemeSettings = true },
                onDatePicker = { showDatePicker = true },
                versionTapCount = versionTapCount,
                onVersionTap = {
                    versionTapCount++
                    if (versionTapCount >= 3) {
                        versionTapCount = 0
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("author_qq", "2451177632"))
                        Toast.makeText(context, "已复制作者QQ，添加QQ以提供反馈", Toast.LENGTH_LONG).show()
                    }
                },
                onBack = onBack
            )
        }
    }
}

// ══════════════════════════════════════
//  主设置列表
// ══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainSettingsContent(
    dateStr: String,
    onThemeSettings: () -> Unit,
    onDatePicker: () -> Unit,
    versionTapCount: Int,
    onVersionTap: () -> Unit,
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            ListItem(
                headlineContent = { Text("主题设置", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                supportingContent = { Text("配色方案、壁纸与预览") },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(Icons.Default.KeyboardArrowRight, "进入") },
                modifier = Modifier.clickable { onThemeSettings() }
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            ListItem(
                headlineContent = { Text("设置第一周的第一天") },
                supportingContent = { Text("当前: $dateStr") },
                leadingContent = { Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onDatePicker() }
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            ListItem(
                headlineContent = { Text("版本号（提供反馈请点按多次）") },
                supportingContent = { Text("v1.2") },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.clickable { onVersionTap() }
            )

            Spacer(modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Copyright © 2026 LiBingze", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("All Rights Reserved", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ══════════════════════════════════════
//  主题设置子页面（二级菜单）
// ══════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
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
    // ★ 系统返回键回到主设置
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }

    val safeIndex = themeIndex.coerceIn(0, Palettes.size - 1)
    val colors = Palettes[safeIndex]

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
        ) {

            // ====== 课程 UI 预览台（7天课表风格） ======
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
                    // 壁纸背景 + 蒙层
                    if (wallpaperUri.isNotBlank()) {
                        AsyncImage(
                            model = wallpaperUri,
                            contentDescription = "壁纸预览",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            alpha = 0.6f
                        )
                    }
                    // 拟真课表
                    Column(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        // ── 顶部星期栏 ──
                        Row(modifier = Modifier.fillMaxWidth().height(22.dp)) {
                            Spacer(modifier = Modifier.width(28.dp)) // 时间列占位
                            listOf("一", "二", "三", "四", "五", "六", "日").forEach { day ->
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(day, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.7f))
                                }
                            }
                        }
                        // ── 课程网格 ──
                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            val w = maxWidth
                            val h = maxHeight
                            val timeColW = 28.dp
                            val dayW = (w - timeColW) / 7
                            val periodH = h / 4

                            // 横向分隔线
                            for (i in 1..3) {
                                Box(
                                    modifier = Modifier
                                        .offset(y = periodH * i)
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(Color.White.copy(alpha = 0.12f))
                                )
                            }
                            // 纵向分隔线（星期之间）
                            for (d in 1..6) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = timeColW + dayW * d, y = 0.dp)
                                        .width(0.5.dp).fillMaxHeight()
                                        .background(Color.White.copy(alpha = 0.12f))
                                )
                            }

                            // 时间标签 1-4 + 时间
                            Column(modifier = Modifier.width(timeColW).fillMaxHeight()) {
                                for (i in 1..4) {
                                    val times = getSectionTime(i, true) // 用夏令时随便
                                    Box(
                                        modifier = Modifier.fillMaxWidth().height(periodH),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("$i", fontSize = 8.sp, fontWeight = FontWeight.Bold,
                                                color = Color.White.copy(alpha = 0.7f))
                                            Text(times.first, fontSize = 6.sp,
                                                color = Color.White.copy(alpha = 0.45f))
                                        }
                                    }
                                }
                            }

                            // 课程块（放在时间列右侧）
                            Box(modifier = Modifier
                                .offset(x = timeColW)
                                .fillMaxHeight()
                                .width(w - timeColW)
                            ) {
                                PreviewCourses.forEach { (courseData, dayPeriod) ->
                                    val (name, room, colorIdx) = courseData
                                    val (day, startPeriod) = dayPeriod
                                    val endPeriod = startPeriod + 1  // 每门课占2节

                                    Box(
                                        modifier = Modifier
                                            .offset(
                                                x = dayW * (day - 1) + 2.dp,
                                                y = periodH * (startPeriod - 1) + 2.dp
                                            )
                                            .size(
                                                width = dayW - 4.dp,
                                                height = periodH * (endPeriod - startPeriod + 1) - 4.dp
                                            )
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(colors[colorIdx % colors.size].copy(alpha = 0.92f))
                                            .padding(2.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = name,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                lineHeight = 9.sp,
                                                color = Color.Black
                                            )
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = "@$room",
                                                fontSize = 6.sp,
                                                color = Color.Black.copy(alpha = 0.7f),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ====== 主题配色 ======
            Text(
                "主题配色",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val themeNames = listOf("莫兰迪", "马卡龙", "MD3")
                themeNames.forEachIndexed { index, name ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onSelectTheme(index) }
                    ) {
                        val palette = Palettes.getOrElse(index) { Palettes[0] }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(palette[0]),
                            contentAlignment = Alignment.Center
                        ) {
                            if (themeIndex == index)
                                Icon(Icons.Default.Check, null, tint = Color.Black)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(name, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            // ====== 更改全局配色 ======
            var showGlobalColorPicker by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("更改全局配色") },
                supportingContent = { Text("改变重点文字和图标的颜色") },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (globalColorIndex in 1 until AccentColorOptions.size)
                                    Color(AccentColorOptions[globalColorIndex].hex)
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                },
                trailingContent = {
                    Icon(
                        if (showGlobalColorPicker) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        "展开"
                    )
                },
                modifier = Modifier.clickable { showGlobalColorPicker = !showGlobalColorPicker }
            )

            if (showGlobalColorPicker) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column {
                        AccentColorOptions.forEachIndexed { index, (name, hex) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectGlobalColor(index) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 颜色圆
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                )
                                Spacer(Modifier.width(14.dp))
                                // 色彩名
                                Text(
                                    text = name,
                                    fontSize = 15.sp,
                                    fontWeight = if (index == globalColorIndex) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                // 使用按钮
                                OutlinedButton(
                                    onClick = { onSelectGlobalColor(index) },
                                    border = BorderStroke(
                                        1.dp,
                                        if (index == globalColorIndex) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = if (index == globalColorIndex) MaterialTheme.colorScheme.primary
                                        else Color.Gray
                                    )
                                ) {
                                    Text(
                                        text = if (index == globalColorIndex) "使用中" else "使用",
                                        fontSize = 13.sp,
                                        fontWeight = if (index == globalColorIndex) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            // ====== 自定义壁纸 ======
            ListItem(
                headlineContent = { Text("设置自定义壁纸") },
                supportingContent = {
                    Text(if (wallpaperUri.isNotBlank()) "已开启 (点击清除)" else "框选裁剪一张图片")
                },
                leadingContent = {
                    Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier.clickable {
                    if (wallpaperUri.isNotBlank()) onClearWallpaper() else onPickWallpaper()
                }
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}