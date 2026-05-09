package com.example.ntuschedule

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 扩展 PreferencesManager，增加每个课表独立的设置项
// key 格式: {profileId}_first_day_of_week, {profileId}_schedule_adjustments

object ProfilePreferencesManager {
    // 每个课表的第一周第一天 dayOfWeek（1=周一 ~ 7=周日，-1=使用全局默认）
    private fun firstDayKey(profileId: String) = intPreferencesKey("${profileId}_first_day_of_week")

    // 每个课表的独立第一周第一天日期毫秒（-1=使用全局默认）
    private fun profileStartDateKey(profileId: String) = longPreferencesKey("${profileId}_start_date_millis")

    // 调休记录 JSON: [{"fromDate":"2025-02-17","toDate":"2025-03-03"}, ...]
    private fun adjustmentsKey(profileId: String) = stringPreferencesKey("${profileId}_schedule_adjustments")

    fun getFirstDayOfWeek(context: Context, profileId: String): Flow<Int> =
        context.dataStore.data.map { it[firstDayKey(profileId)] ?: -1 } // 默认-1=使用全局

    suspend fun saveFirstDayOfWeek(context: Context, profileId: String, day: Int) {
        context.dataStore.edit { it[firstDayKey(profileId)] = day }
    }

    fun getProfileStartDate(context: Context, profileId: String): Flow<Long> =
        context.dataStore.data.map { it[profileStartDateKey(profileId)] ?: -1L }

    suspend fun saveProfileStartDate(context: Context, profileId: String, millis: Long) {
        context.dataStore.edit { it[profileStartDateKey(profileId)] = millis }
    }

    fun getScheduleAdjustments(context: Context, profileId: String): Flow<String> =
        context.dataStore.data.map { it[adjustmentsKey(profileId)] ?: "[]" }

    suspend fun saveScheduleAdjustments(context: Context, profileId: String, json: String) {
        context.dataStore.edit { it[adjustmentsKey(profileId)] = json }
    }
}
