package com.example.ntuschedule

import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Calendar

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
fun ScheduleGrid(onNavigateToLogin: () -> Unit, onNavigateToSettings: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val profiles by CourseRepository.profiles.collectAsState()
    val currentProfileId by CourseRepository.currentProfileId.collectAsState()
    val allCourses by CourseRepository.allCourses.collectAsState(initial = emptyList())

    val startDateMillis by PreferencesManager.getStartDate(context).collectAsState(initial = -1L)
    val themeIndex by PreferencesManager.getThemeIndex(context).collectAsState(initial = 0)
    val wallpaperUri by PreferencesManager.getWallpaperUri(context).collectAsState(initial = "")

    var showProfileSheet by remember { mutableStateOf(false) }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }

    // 【优化】日历和年份只获取一次，避免 Pager 滑动时重复创建 Calendar 导致掉帧
    val isSummerTime = remember { Calendar.getInstance().get(Calendar.MONTH) + 1 in 5..9 }
    val todayYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val todayDayOfYear = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }

    val safeActualWeek = remember(startDateMillis) {
        if (startDateMillis > 0L) {
            val diff = System.currentTimeMillis() - startDateMillis
            ((diff / (1000L * 60 * 60 * 24 * 7)).toInt() + 1).coerceIn(1, 20)
        } else 1
    }

    val pagerState = rememberPagerState(pageCount = { 20 })
    val currentViewWeek = pagerState.currentPage + 1

    // 【修复&优化】加上 currentProfileId 参与重组，并在这里就完成 Profile 过滤！
    val coursesByWeek = remember(allCourses, currentProfileId) {
        val map = mutableMapOf<Int, MutableList<Course>>()
        // 关键修复：只保留当前课表配置下的课程
        allCourses.filter { it.profileId == currentProfileId }.forEach { course ->
            course.weeks.split(",").forEach { weekStr ->
                weekStr.trim().toIntOrNull()?.let { weekNum ->
                    map.getOrPut(weekNum) { mutableListOf() }.add(course)
                }
            }
        }
        map
    }

    LaunchedEffect(startDateMillis) {
        if (startDateMillis > 0L) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showProfileSheet = true }.padding(4.dp)
                    ) {
                        Text(profiles.find { it.id == currentProfileId }?.name ?: "默认课表", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.KeyboardArrowDown, "切换")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "第 $currentViewWeek 周",
                            fontSize = 14.sp,
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
            // 壁纸已提到外层全屏铺满，此处不再放置

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val weekToDisplay = page + 1

                // 查表渲染，O(1) 复杂度
                val displayCourses = coursesByWeek[weekToDisplay] ?: emptyList()

                val weekData = remember(startDateMillis, weekToDisplay) {
                    val baseCal = Calendar.getInstance().apply {
                        if (startDateMillis > 0L) timeInMillis = startDateMillis
                        add(Calendar.DAY_OF_YEAR, (weekToDisplay - 1) * 7)
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
                            // 底部留白，确保最后一行能滚过屏幕圆角区域
                            Spacer(modifier = Modifier.height(80.dp))
                        }

                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val widthPerDay = maxWidth / 7
                            for (i in 1..12) {
                                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), modifier = Modifier.fillMaxWidth().offset(y = (i * 64).dp))
                            }

                            displayCourses.forEach { course ->
                                val startOffset = (course.startPeriod - 1) * 64
                                val height = (course.endPeriod - course.startPeriod + 1) * 64

                                Box(
                                    modifier = Modifier.width(widthPerDay).offset(x = widthPerDay * (course.dayOfWeek - 1), y = startOffset.dp).height(height.dp)
                                ) {
                                    CourseCard(course = course, themeIndex = themeIndex, modifier = Modifier.fillMaxSize()) {
                                        selectedCourse = course
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
        ProfileManagementSheet(profiles = profiles, currentId = currentProfileId, onDismiss = { showProfileSheet = false })
    }

    selectedCourse?.let { course ->
        AlertDialog(
            onDismissRequest = { selectedCourse = null },
            title = { Text(course.name) },
            text = {
                Column {
                    Text("教室: ${course.room}")
                    Text("教师: ${course.teacher}")
                    Text("周次: ${course.weeks}周")
                    Text("节次: 星期${course.dayOfWeek} 第${course.startPeriod}-${course.endPeriod}节")
                }
            },
            confirmButton = { TextButton(onClick = { selectedCourse = null }) { Text("关闭") } }
        )
    }
}
@Composable
fun CourseCard(course: Course, themeIndex: Int, modifier: Modifier, onClick: () -> Unit) {
    val safeThemeIndex = themeIndex.coerceIn(0, Palettes.size - 1)
    val currentPalette = Palettes[safeThemeIndex]
    val bgColor = currentPalette[course.colorIndex % currentPalette.size].copy(alpha = 0.85f)
    val textColor = if (isSystemInDarkTheme() && safeThemeIndex != 2) Color.Black else Color.Black

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
            Spacer(modifier = Modifier.height(2.dp))
            Text("@${course.room}", fontSize = 9.sp, color = textColor.copy(alpha = 0.8f), maxLines = 2)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagementSheet(profiles: List<ScheduleProfile>, currentId: String, onDismiss: () -> Unit) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editProfile by remember { mutableStateOf<ScheduleProfile?>(null) }
    var inputName by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
            Text("课表管理", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            profiles.forEach { profile ->
                val isSelected = profile.id == currentId
                ListItem(
                    headlineContent = { Text(profile.name, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal) },
                    leadingContent = {
                        if(isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        else Spacer(modifier = Modifier.width(24.dp))
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editProfile = profile; inputName = profile.name }) { Icon(Icons.Default.Edit, "重命名") }
                            if (profiles.size > 1) {
                                IconButton(onClick = { CourseRepository.deleteProfile(profile.id) }) {
                                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable { CourseRepository.switchProfile(profile.id); onDismiss() }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { showAddDialog = true; inputName = "" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建课表")
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