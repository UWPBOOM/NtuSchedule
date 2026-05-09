package com.example.ntuschedule

/**
 * 课表专属设置页 - 完全重写版
 * 
 * 功能 2：第一周第一天 — 与全局设置联动，日期选择器 UI 与全局一致
 * 功能 3：调休 — 完整的三步流程（选原日期→确认有课→选目标日期→保存）
 */
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 调休记录（原日期 → 目标日期） */
data class ScheduleAdjustment(
    val fromDate: String,  // yyyy-MM-dd
    val toDate: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fromDate", fromDate)
        put("toDate", toDate)
    }

    companion object {
        fun fromJson(obj: JSONObject): ScheduleAdjustment = ScheduleAdjustment(
            fromDate = obj.getString("fromDate"),
            toDate = obj.getString("toDate")
        )
    }
}

/** 调休流程状态机（顶层，不可放在 Composable 内） */
private enum class AdjStep { IDLE, PICK_FROM, CONFIRM, PICK_TO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    profileId: String,
    profileName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ═══ 全局设置（用于联动） ═══
    val globalStartDateMillis by PreferencesManager.getStartDate(context)
        .collectAsState(initial = System.currentTimeMillis())

    // ═══ 课表专属设置 ═══
    val profileStartDateMillis by ProfilePreferencesManager.getProfileStartDate(context, profileId)
        .collectAsState(initial = -1L)
    val hasCustomStartDate = profileStartDateMillis > 0L
    val effectiveStartDate = if (hasCustomStartDate) profileStartDateMillis else globalStartDateMillis

    val dateStr = remember(effectiveStartDate) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(effectiveStartDate))
    }

    // ═══ 日期选择器（第一周第一天） ═══
    var showFirstDayDatePicker by remember { mutableStateOf(false) }
    val firstDayDatePickerState = rememberDatePickerState(initialSelectedDateMillis = effectiveStartDate)

    // ═══ 当前课表课程列表（用于检测某天是否有课） ═══
    val allCourses by CourseRepository.allCourses.collectAsState(initial = emptyList())
    val profileCourses = remember(allCourses, profileId) {
        allCourses.filter { it.profileId == profileId }
    }

    // ═══ 调休记录 ═══
    val adjustmentsJson by ProfilePreferencesManager.getScheduleAdjustments(context, profileId)
        .collectAsState(initial = "[]")
    val adjustments = remember(adjustmentsJson) {
        try {
            val arr = JSONArray(adjustmentsJson)
            (0 until arr.length()).map { ScheduleAdjustment.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    // ═══ 调休流程状态机 ═══
    var adjStep by remember { mutableStateOf(AdjStep.IDLE) }
    var pendingFromDateMillis by remember { mutableStateOf<Long?>(null) }

    val fromDatePickerState = rememberDatePickerState()
    val toDatePickerState = rememberDatePickerState()

    // 学期有效日期范围
    val semesterStart = remember {
        Calendar.getInstance().apply {
            val m = get(Calendar.MONTH)
            if (m in 1..7) { set(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1) }
            else { set(Calendar.MONTH, 8); set(Calendar.DAY_OF_MONTH, 1) }
        }.timeInMillis
    }
    val semesterEnd = remember {
        Calendar.getInstance().apply {
            val m = get(Calendar.MONTH)
            if (m in 1..7) { set(Calendar.MONTH, 6); set(Calendar.DAY_OF_MONTH, 31) }
            else { set(Calendar.YEAR, get(Calendar.YEAR) + 1); set(Calendar.MONTH, 0); set(Calendar.DAY_OF_MONTH, 31) }
        }.timeInMillis
    }

    /** 检查某日期当天是否有课程（基于当前课表实际数据） */
    fun dateHasCourses(millis: Long): Boolean {
        if (effectiveStartDate <= 0L) return false
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val dayOfWeek = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
        val startCal = Calendar.getInstance().apply { timeInMillis = effectiveStartDate }
        val weekNum = ((millis - startCal.timeInMillis) / (1000L * 60 * 60 * 24 * 7)).toInt() + 1
        if (weekNum !in 1..20) return false
        return profileCourses.any { course ->
            val weeks = course.weeks.split(",").mapNotNull { it.trim().toIntOrNull() }
            weekNum in weeks && course.dayOfWeek == dayOfWeek
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$profileName · 设置") },
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
            // ══════════ 第一周的第一天 ══════════
            Text("第一周的第一天", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (hasCustomStartDate) "该课表已独立设置"
                else "当前跟随全局设置",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 显示当前生效日期
            ListItem(
                headlineContent = { Text("当前生效日期") },
                supportingContent = { Text(dateStr) },
                leadingContent = {
                    Icon(Icons.Default.DateRange, null, tint = MaterialTheme.colorScheme.primary)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 仅当有独立设置时显示"恢复全局"按钮
                if (hasCustomStartDate) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                ProfilePreferencesManager.saveProfileStartDate(context, profileId, -1L)
                                Toast.makeText(context, "已恢复跟随全局设置", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("恢复全局", fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = { showFirstDayDatePicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (hasCustomStartDate) "修改日期" else "独立设置", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Divider()
            Spacer(modifier = Modifier.height(20.dp))

            // ══════════ 调休功能 ══════════
            Text("调休功能", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("将某天所有课移到另一天，即时生效",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // 调休记录列表
            if (adjustments.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("暂无调休记录", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp))
                }
            } else {
                adjustments.forEach { adj ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Refresh, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${adj.fromDate}  →  ${adj.toDate}", fontSize = 14.sp)
                            }
                            IconButton(onClick = {
                                val newList = adjustments.toMutableList().apply { remove(adj) }
                                val newJson = JSONArray().apply { newList.forEach { put(it.toJson()) } }
                                scope.launch {
                                    ProfilePreferencesManager.saveScheduleAdjustments(context, profileId, newJson.toString())
                                    Toast.makeText(context, "已删除该调休记录", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { adjStep = AdjStep.PICK_FROM },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("添加调休")
            }
        }
    }

    // ───── 对话框层（所有 Dialog 写在顶层，确保蒙层覆盖状态栏） ─────

    // 第一周第一天选择器（UI 与全局 DatePicker 完全一致）
    if (showFirstDayDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showFirstDayDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = firstDayDatePickerState.selectedDateMillis
                    if (millis != null) {
                        scope.launch {
                            ProfilePreferencesManager.saveProfileStartDate(context, profileId, millis)
                            Toast.makeText(context, "第一周第一天已更新", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showFirstDayDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showFirstDayDatePicker = false }) { Text("取消") } }
        ) {
            DatePicker(
                state = firstDayDatePickerState,
                dateValidator = { it in semesterStart..semesterEnd }
            )
        }
    }

    // 调休步骤 1：选原日期（UI 与全局 DatePicker 完全一致，无 title）
    if (adjStep == AdjStep.PICK_FROM) {
        DatePickerDialog(
            onDismissRequest = { adjStep = AdjStep.IDLE },
            confirmButton = {
                TextButton(onClick = {
                    val millis = fromDatePickerState.selectedDateMillis
                    if (millis == null) {
                        adjStep = AdjStep.IDLE
                        return@TextButton
                    }
                    if (dateHasCourses(millis)) {
                        pendingFromDateMillis = millis
                        adjStep = AdjStep.CONFIRM
                    } else {
                        Toast.makeText(context, "该日期无课程，无需调休", Toast.LENGTH_LONG).show()
                        adjStep = AdjStep.IDLE
                    }
                }) { Text("下一步") }
            },
            dismissButton = { TextButton(onClick = { adjStep = AdjStep.IDLE }) { Text("取消") } }
        ) {
            DatePicker(
                state = fromDatePickerState,
                dateValidator = { it in semesterStart..semesterEnd }
            )
        }
    }

    // 调休步骤 2：确认原日期有课
    if (adjStep == AdjStep.CONFIRM && pendingFromDateMillis != null) {
        val fromStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(pendingFromDateMillis!!))
        AlertDialog(
            onDismissRequest = { adjStep = AdjStep.IDLE; pendingFromDateMillis = null },
            icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("确认调休") },
            text = {
                Text("检测到 $fromStr 有课程，是否将该日所有课程移动到目标日期？")
            },
            confirmButton = {
                TextButton(onClick = {
                    adjStep = AdjStep.PICK_TO
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = {
                    adjStep = AdjStep.IDLE
                    pendingFromDateMillis = null
                }) { Text("取消") }
            }
        )
    }

    // 调休步骤 3：选目标日期（UI 与全局 DatePicker 完全一致，无 title）
    if (adjStep == AdjStep.PICK_TO && pendingFromDateMillis != null) {
        DatePickerDialog(
            onDismissRequest = {
                adjStep = AdjStep.IDLE
                pendingFromDateMillis = null
            },
            confirmButton = {
                TextButton(onClick = {
                    val toMillis = toDatePickerState.selectedDateMillis
                    val fromMillis = pendingFromDateMillis
                    if (toMillis == null || fromMillis == null) {
                        adjStep = AdjStep.IDLE
                        pendingFromDateMillis = null
                        return@TextButton
                    }
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val fromStr = sdf.format(Date(fromMillis))
                    val toStr = sdf.format(Date(toMillis))

                    if (fromStr == toStr) {
                        Toast.makeText(context, "原日期和目标日期不能相同", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }

                    // 保存调休
                    val newList = adjustments.toMutableList().apply {
                        add(ScheduleAdjustment(fromDate = fromStr, toDate = toStr))
                    }
                    val newJson = JSONArray().apply { newList.forEach { put(it.toJson()) } }
                    scope.launch {
                        ProfilePreferencesManager.saveScheduleAdjustments(context, profileId, newJson.toString())
                        Toast.makeText(context, "调休已添加！返回课表查看效果", Toast.LENGTH_SHORT).show()
                    }
                    adjStep = AdjStep.IDLE
                    pendingFromDateMillis = null
                }) { Text("确认调休") }
            },
            dismissButton = {
                TextButton(onClick = {
                    adjStep = AdjStep.IDLE
                    pendingFromDateMillis = null
                }) { Text("取消") }
            }
        ) {
            DatePicker(
                state = toDatePickerState,
                dateValidator = { it in semesterStart..semesterEnd }
            )
        }
    }
}
