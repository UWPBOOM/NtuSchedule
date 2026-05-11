package com.example.ntuschedule

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

val Palettes = listOf(
    // 0: 莫兰迪（默认）
    listOf(Color(0xFFD0E8FF), Color(0xFFD5F0D9), Color(0xFFFFE8CC), Color(0xFFFFD6E5), Color(0xFFE8D5F0), Color(0xFFCFFFF5), Color(0xFFFFF1C5)),
    // 1: 马卡龙
    listOf(Color(0xFFE3F2FD), Color(0xFFE8F5E9), Color(0xFFFFF3E0), Color(0xFFFCE4EC), Color(0xFFF3E5F5), Color(0xFFE0F7FA), Color(0xFFFFF8E1)),
    // 2: Material Design 3
    listOf(Color(0xFFEADDFF), Color(0xFFD3E3FD), Color(0xFFC4EED0), Color(0xFFFFF3C0), Color(0xFFFFD8E4), Color(0xFFC5F0F5), Color(0xFFE8DEF8))
)

// 将 Map 提取到外层，只在类加载时初始化一次
private val MorningTimes = mapOf(1 to ("07:50" to "08:30"), 2 to ("08:40" to "09:20"), 3 to ("09:35" to "10:15"), 4 to ("10:30" to "11:10"), 5 to ("11:20" to "12:00"))
private val SummerAftTimes = mapOf(6 to ("14:00" to "14:40"), 7 to ("14:50" to "15:30"), 8 to ("15:50" to "16:30"), 9 to ("16:40" to "17:20"), 10 to ("19:00" to "19:40"), 11 to ("19:50" to "20:30"), 12 to ("20:40" to "21:20"))
private val WinterAftTimes = mapOf(6 to ("13:30" to "14:10"), 7 to ("14:20" to "15:00"), 8 to ("15:20" to "16:00"), 9 to ("16:10" to "16:50"), 10 to ("18:30" to "19:10"), 11 to ("19:20" to "20:00"), 12 to ("20:10" to "20:50"))

fun getSectionTime(section: Int, isSummer: Boolean): Pair<String, String> {
    if (section in 1..5) return MorningTimes[section] ?: ("" to "")
    return if (isSummer) SummerAftTimes[section] ?: ("" to "") else WinterAftTimes[section] ?: ("" to "")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ScheduleGrid(
    onNavigateToLogin: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfileSettings: (String, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val profiles by CourseRepository.profiles.collectAsState()
    val currentProfileId by CourseRepository.currentProfileId.collectAsState()
    val allCourses by CourseRepository.allCourses.collectAsState(initial = emptyList())

    val startDateMillis by PreferencesManager.getStartDate(context).collectAsState(initial = -1L)
    val themeIndex by PreferencesManager.getThemeIndex(context).collectAsState(initial = 0)
    val wallpaperUri by PreferencesManager.getWallpaperUri(context).collectAsState(initial = "")

    // 当前课表的独立第一周第一天（毫秒），-1L=使用全局设置
    val profileStartDateMillis by ProfilePreferencesManager.getProfileStartDate(context, currentProfileId)
        .collectAsState(initial = -1L)
    // 实际生效的开学日期：课表独立 > 全局 > 当前时间（保证永远有效）
    val effectiveStartDate = remember(profileStartDateMillis, startDateMillis) {
        val raw = if (profileStartDateMillis > 0L) profileStartDateMillis else startDateMillis
        if (raw > 0L) raw else System.currentTimeMillis()
    }

    // 调休记录 JSON（State 对象，derivedStateOf 直接读这个才能追踪变化）
    val adjustmentsJson by ProfilePreferencesManager.getScheduleAdjustments(context, currentProfileId)
        .collectAsState(initial = "[]")

    var showProfileSheet by remember { mutableStateOf(false) }
    var selectedConflictGroup by remember { mutableStateOf<ConflictGroup?>(null) }

    // 【优化】日历和年份只获取一次，避免 Pager 滑动时重复创建 Calendar 导致掉帧
    val isSummerTime = remember { Calendar.getInstance().get(Calendar.MONTH) + 1 in 5..9 }
    val todayYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val todayDayOfYear = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }

    val safeActualWeek = remember(effectiveStartDate) {
        if (effectiveStartDate > 0L) {
            val diff = System.currentTimeMillis() - effectiveStartDate
            ((diff / (1000L * 60 * 60 * 24 * 7)).toInt() + 1).coerceIn(1, 20)
        } else 1
    }

    val pagerState = rememberPagerState(pageCount = { 20 })
    val currentViewWeek = pagerState.currentPage + 1

    // 【修复】derivedStateOf 直接读 adjustmentsJson（State），确保变化必定触发重算
    val coursesByWeek = remember(allCourses, currentProfileId, adjustmentsJson, effectiveStartDate) {
        android.util.Log.d("ScheduleUI", "=== coursesByWeek 重新计算 ===")
        android.util.Log.d("ScheduleUI", "adjustmentsJson = $adjustmentsJson")
        android.util.Log.d("ScheduleUI", "effectiveStartDate = $effectiveStartDate")
        android.util.Log.d("ScheduleUI", "currentProfileId = $currentProfileId")
        android.util.Log.d("ScheduleUI", "allCourses count = ${allCourses.size}")

        val map = mutableMapOf<Int, MutableList<Course>>()
        allCourses.filter { it.profileId == currentProfileId }.forEach { course ->
            course.weeks.split(",").forEach { weekStr ->
                weekStr.trim().toIntOrNull()?.let { weekNum ->
                    map.getOrPut(weekNum) { mutableListOf() }.add(course)
                }
            }
        }

        android.util.Log.d("ScheduleUI", "week map keys = ${map.keys.sorted()}")

        // 在这里直接解析 adjustmentsJson，derivedStateOf 会自动追踪它
        val parsedAdjustments = try {
            val arr = org.json.JSONArray(adjustmentsJson)
            (0 until arr.length()).map { ScheduleAdjustment.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }

        android.util.Log.d("ScheduleUI", "parsedAdjustments size = ${parsedAdjustments.size}")
        parsedAdjustments.forEach { adj ->
            android.util.Log.d("ScheduleUI", "  adj: ${adj.fromDate} -> ${adj.toDate}")
        }

        // 应用调休
        if (parsedAdjustments.isNotEmpty() && effectiveStartDate > 0L) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            for (adj in parsedAdjustments) {
                try {
                    val fromDate = sdf.parse(adj.fromDate)!!
                    val toDate = sdf.parse(adj.toDate)!!
                    val startCal = Calendar.getInstance().apply { timeInMillis = effectiveStartDate }
                    // 计算两个日期对应的教学周
                    val fromWeek = ((fromDate.time - startCal.timeInMillis) / (1000L * 60 * 60 * 24 * 7)).toInt() + 1
                    val toWeek = ((toDate.time - startCal.timeInMillis) / (1000L * 60 * 60 * 24 * 7)).toInt() + 1

                    val fromCal = Calendar.getInstance().apply { time = fromDate }
                    val toCal = Calendar.getInstance().apply { time = toDate }
                    // 转为 1=周一 ~ 7=周日
                    val fromDayOfWeek = ((fromCal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                    val toDayOfWeek = ((toCal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

                    android.util.Log.d("ScheduleUI", "调休: ${adj.fromDate}(w$fromWeek d$fromDayOfWeek) -> ${adj.toDate}(w$toWeek d$toDayOfWeek)")
                    if (fromWeek !in 1..20 || toWeek !in 1..20) {
                        android.util.Log.d("ScheduleUI", "  SKIP: week out of range")
                        continue
                    }
                    // 同周同天才跳过；同周不同天需要迁移！
                    if (fromWeek == toWeek && fromDayOfWeek == toDayOfWeek) {
                        android.util.Log.d("ScheduleUI", "  SKIP: same day")
                        continue
                    }

                    // 移动课程：fromWeek 周 dayOfWeek=fromDayOfWeek 的课程 → toWeek 周 dayOfWeek=toDayOfWeek
                    val fromCourses = map[fromWeek]?.filter { it.dayOfWeek == fromDayOfWeek }?.toList() ?: emptyList()
                    android.util.Log.d("ScheduleUI", "  fromWeek=$fromWeek day=$fromDayOfWeek, courses found: ${fromCourses.size}")
                    if (fromCourses.isNotEmpty()) {
                        map[fromWeek] = map[fromWeek]?.filter { it.dayOfWeek != fromDayOfWeek }?.toMutableList() ?: mutableListOf()
                        val targetList = map.getOrPut(toWeek) { mutableListOf() }
                        fromCourses.forEach { course ->
                            targetList.add(course.copy(dayOfWeek = toDayOfWeek))
                            android.util.Log.d("ScheduleUI", "  MOVED: ${course.name} to week=$toWeek day=$toDayOfWeek")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ScheduleUI", "调休解析失败", e)
                }
            }
        }

        map
    }

    LaunchedEffect(effectiveStartDate) {
        if (effectiveStartDate > 0L) {
            pagerState.scrollToPage(safeActualWeek - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 壁纸铺满全屏（含状态栏区域），保持纵横比裁剪
        if (wallpaperUri.isNotEmpty()) {
            AsyncImage(
                model = Uri.parse(wallpaperUri),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.6f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                title = {
                    Column(
                        modifier = Modifier.clickable { showProfileSheet = true }.padding(vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profiles.find { it.id == currentProfileId }?.name ?: "默认课表", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.KeyboardArrowDown, "切换", modifier = Modifier.size(20.dp))
                        }
                        Text(
                            text = "第 $currentViewWeek 周",
                            fontSize = 12.sp,
                            color = if (currentViewWeek == safeActualWeek) MaterialTheme.colorScheme.primary else Color.Red
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(safeActualWeek - 1) } }) {
                        Icon(Icons.Default.Refresh, "回到本周")
                    }
                    IconButton(onClick = onNavigateToLogin) { Icon(Icons.Default.Add, "导入") }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, "设置") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val weekToDisplay = page + 1

                val rawCourses = coursesByWeek[weekToDisplay] ?: emptyList()

                // 【Feature 3】冲突检测：用 ScheduleConflictResolver 处理同一格子的课程
                val conflictGroups = remember(rawCourses) {
                    ScheduleConflictResolver.resolve(rawCourses)
                }

                // 计算周数据时使用 effectiveStartDate
                val weekData = remember(effectiveStartDate, weekToDisplay) {
                    val baseCal = Calendar.getInstance().apply {
                        if (effectiveStartDate > 0L) timeInMillis = effectiveStartDate
                        add(Calendar.DAY_OF_YEAR, (weekToDisplay - 1) * 7)    // 跳到目标周
                    }
                    val month = baseCal.get(Calendar.MONTH) + 1

                    val days = List(7) { index ->
                        val c = baseCal.clone() as Calendar
                        c.add(Calendar.DAY_OF_YEAR, index)
                        val isToday = c.get(Calendar.DAY_OF_YEAR) == todayDayOfYear && c.get(Calendar.YEAR) == todayYear
                        Triple(c.get(Calendar.DAY_OF_MONTH), isToday, listOf("一", "二", "三", "四", "五", "六", "日")[index])
                    }
                    Pair(month, days)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.width(42.dp).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${weekData.first}\n月",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            )
                        }

                        weekData.second.forEach { (dateNum, isToday, dayName) ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(dayName, fontSize = 12.sp, color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontWeight = if(isToday) FontWeight.Bold else FontWeight.Normal)
                                    Text(dateNum.toString(), fontSize = 11.sp, color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    val scrollState = rememberScrollState()
                    Row(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                        Column(modifier = Modifier.width(42.dp)) {
                            for (i in 1..12) {
                                val times = getSectionTime(i, isSummerTime)
                                Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(i.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(times.first, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        Text(times.second, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                }
                            }
                            // 底部留白，确保最后一行能滚过屏幕圆角/导航栏区域
                            Spacer(modifier = Modifier.height(120.dp))
                        }

                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val widthPerDay = maxWidth / 7
                            for (i in 1..12) {
                                Divider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                    modifier = Modifier.fillMaxWidth().offset(y = (i * 64).dp)
                                )
                            }

                            // 【Feature 3】使用 conflictGroups 渲染，每格只显示一个主课程
                            conflictGroups.forEach { group ->
                                val course = group.mainCourse
                                val hasConflict = ScheduleConflictResolver.hasConflict(group)
                                val startOffset = (course.startPeriod - 1) * 64
                                val height = (course.endPeriod - course.startPeriod + 1) * 64

                                Box(
                                    modifier = Modifier.width(widthPerDay).offset(x = widthPerDay * (course.dayOfWeek - 1), y = startOffset.dp).height(height.dp)
                                ) {
                                    CourseCard(
                                        course = course,
                                        themeIndex = themeIndex,
                                        hasConflict = hasConflict,
                                        gridWidth = widthPerDay,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        // 点击时传 ConflictGroup 而非单个 Course，以便显示冲突信息
                                        selectedConflictGroup = group
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (showProfileSheet) {
        ProfileManagementSheet(
            profiles = profiles,
            currentId = currentProfileId,
            onDismiss = { showProfileSheet = false },
            onNavigateToSettings = { profileId, profileName ->
                showProfileSheet = false
                onNavigateToProfileSettings(profileId, profileName)
            }
        )
    }

    // 【Feature 3】课程详情对话框 — MD3 风格，显示冲突信息
    selectedConflictGroup?.let { group ->
        val dayLabel = mapOf(
            1 to "星期一", 2 to "星期二", 3 to "星期三", 4 to "星期四",
            5 to "星期五", 6 to "星期六", 7 to "星期日"
        )
        val hasConflict = ScheduleConflictResolver.hasConflict(group)
        val c = group.mainCourse

        Dialog(
            onDismissRequest = { selectedConflictGroup = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ═══ 标题（居中） ═══
                    Text(
                        c.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )

                    // ═══ 课程信息（紧凑单行） ═══
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ─── 教室 ───
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Home,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "教室：${c.room}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // ─── 教师 ───
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "教师：${c.teacher}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // ─── 上课时间 ───
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "${dayLabel[c.dayOfWeek] ?: "周${c.dayOfWeek}"}  第 ${c.startPeriod}–${c.endPeriod} 节",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // ═══ 冲突课程列表 ═══
                    if (hasConflict) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "课程冲突（${group.allCourses.size} 门）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            group.allCourses.forEach { conflict ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        conflict.name,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        "教室：${conflict.room}  |  教师：${conflict.teacher}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${dayLabel[conflict.dayOfWeek] ?: "周${conflict.dayOfWeek}"}  第 ${conflict.startPeriod}–${conflict.endPeriod} 节",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // ═══ 关闭按钮 ═══
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { selectedConflictGroup = null }) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CourseCard(
    course: Course,
    themeIndex: Int,
    hasConflict: Boolean = false,
    gridWidth: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val safeThemeIndex = themeIndex.coerceIn(0, Palettes.size - 1)
    val currentPalette = Palettes[safeThemeIndex]
    val bgColor = currentPalette[course.colorIndex % currentPalette.size].copy(alpha = 0.85f)
    val textColor = Color.Black

    Box(
        modifier = modifier
            .padding(1.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Column {
            Text(course.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 4, lineHeight = 12.sp, color = textColor)
            Text("@${course.room}", fontSize = 9.sp, color = textColor.copy(alpha = 0.8f), maxLines = 2, lineHeight = 11.sp)
        }

        // 【Feature 3】冲突指示器：右上角小直角等腰三角形
        if (hasConflict) {
            val triSize = gridWidth / 8  // 边长 = 格子宽度的 1/8
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(triSize)
            ) {
                // 直角等腰三角形，直角在右上角，两直角边与格子边缘平行
                val path = Path().apply {
                    moveTo(size.width, 0f)              // 右上角（直角顶点）
                    lineTo(size.width, size.height)      // 右边向下
                    lineTo(0f, size.height)              // 底边向左
                    close()
                }
                // 填充红色
                drawPath(path = path, color = Color(0xFFE53935))
                // 描边确保可见
                drawPath(
                    path = path,
                    color = Color(0xFFE53935).copy(alpha = 0.9f),
                    style = Stroke(width = 1.5f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagementSheet(
    profiles: List<ScheduleProfile>,
    currentId: String,
    onDismiss: () -> Unit,
    onNavigateToSettings: (String, String) -> Unit = { _, _ -> }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf<ScheduleProfile?>(null) }
    var inputName by remember { mutableStateOf("") }

    // 动画状态：进入时从底部滑入，退出时滑出 + 淡出
    var sheetVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { sheetVisible = true }

    fun dismiss() {
        sheetVisible = false
        coroutineScope.launch {
            kotlinx.coroutines.delay(280)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = sheetVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = true) { dismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false) { },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column {
                    // 标题：左侧对齐，与圆角有合理间距
                    Text(
                        "课表管理",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp)
                    )
                    profiles.forEach { profile ->
                        val isSelected = profile.id == currentId
                        ListItem(
                            headlineContent = { Text(profile.name, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingContent = {
                                if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                else Spacer(modifier = Modifier.width(24.dp))
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onNavigateToSettings(profile.id, profile.name) }) {
                                        Icon(Icons.Default.Settings, "课表设置", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { editProfile = profile; inputName = profile.name }) {
                                        Icon(Icons.Default.Edit, "重命名")
                                    }
                                    if (profiles.size > 1) {
                                        IconButton(onClick = { CourseRepository.deleteProfile(profile.id) }) {
                                            Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable { CourseRepository.switchProfile(profile.id); dismiss() }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showAddDialog = true; inputName = "" },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新建课表")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showAddDialog || editProfile != null) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false; editProfile = null },
            title = { Text(if (showAddDialog) "新建课表" else "重命名") },
            text = { OutlinedTextField(value = inputName, onValueChange = { inputName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (inputName.isNotBlank()) {
                        if (showAddDialog) CourseRepository.addProfile(inputName)
                        else editProfile?.let { CourseRepository.renameProfile(it.id, inputName) }
                    }
                    showAddDialog = false; editProfile = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false; editProfile = null }) { Text("取消") }
            }
        )
    }
}
