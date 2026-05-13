package com.example.ntuschedule

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

object PreferencesManager {
    private val START_DATE_KEY = longPreferencesKey("start_date_millis")
    private val THEME_INDEX_KEY = intPreferencesKey("theme_index")
    private val WALLPAPER_URI_KEY = stringPreferencesKey("wallpaper_uri")

    // ⭐ 账号密码存储
    private val USER_ID_KEY = stringPreferencesKey("user_id")
    private val PASSWORD_KEY = stringPreferencesKey("user_password")

    // ⭐ 全局主题色
    private val GLOBAL_COLOR_INDEX_KEY = intPreferencesKey("global_color_index")

    // ⭐ 学年学期（用于智能导入时自动切换课表页的学年/学期）
    private val ACADEMIC_YEAR_KEY = stringPreferencesKey("academic_year")
    private val SEMESTER_KEY = stringPreferencesKey("semester")

    fun getAcademicYear(context: Context): Flow<String> = context.dataStore.data.map { it[ACADEMIC_YEAR_KEY] ?: "" }
    fun getSemester(context: Context): Flow<String> = context.dataStore.data.map { it[SEMESTER_KEY] ?: "" }

    suspend fun saveAcademicYear(context: Context, year: String) { context.dataStore.edit { it[ACADEMIC_YEAR_KEY] = year } }
    suspend fun saveSemester(context: Context, semester: String) { context.dataStore.edit { it[SEMESTER_KEY] = semester } }

    fun getStartDate(context: Context): Flow<Long> = context.dataStore.data.map { it[START_DATE_KEY] ?: System.currentTimeMillis() }
    suspend fun saveStartDate(context: Context, timeMillis: Long) { context.dataStore.edit { it[START_DATE_KEY] = timeMillis } }

    fun getThemeIndex(context: Context): Flow<Int> = context.dataStore.data.map { it[THEME_INDEX_KEY] ?: 0 }
    suspend fun saveThemeIndex(context: Context, index: Int) { context.dataStore.edit { it[THEME_INDEX_KEY] = index } }

    fun getWallpaperUri(context: Context): Flow<String> = context.dataStore.data.map { it[WALLPAPER_URI_KEY] ?: "" }
    suspend fun saveWallpaperUri(context: Context, uri: String) { context.dataStore.edit { it[WALLPAPER_URI_KEY] = uri } }

    fun getUserId(context: Context): Flow<String> = context.dataStore.data.map { it[USER_ID_KEY] ?: "" }
    fun getPassword(context: Context): Flow<String> = context.dataStore.data.map { it[PASSWORD_KEY] ?: "" }

    suspend fun saveCredentials(context: Context, stuId: String, psw: String) {
        context.dataStore.edit {
            it[USER_ID_KEY] = stuId
            it[PASSWORD_KEY] = psw
        }
    }

    fun getGlobalColorIndex(context: Context): Flow<Int> = context.dataStore.data.map { it[GLOBAL_COLOR_INDEX_KEY] ?: 0 }
    suspend fun saveGlobalColorIndex(context: Context, index: Int) { context.dataStore.edit { it[GLOBAL_COLOR_INDEX_KEY] = index } }
}