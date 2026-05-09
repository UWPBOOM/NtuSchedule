package com.example.ntuschedule // ⭐记得改包名

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object CourseRepository {
    private var dao: AppDao? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initialized get() = dao != null
    private fun requireDao(): AppDao = dao ?: error("CourseRepository 未初始化，请先调用 init(context)")

    private val _profiles = MutableStateFlow<List<ScheduleProfile>>(emptyList())
    val profiles: StateFlow<List<ScheduleProfile>> = _profiles

    private val _currentProfileId = MutableStateFlow("")
    val currentProfileId: StateFlow<String> = _currentProfileId

    private val _allCourses = MutableStateFlow<List<Course>>(emptyList())
    val allCourses: StateFlow<List<Course>> = _allCourses

    // App 启动时调用，初始化数据库并拉取数据
    fun init(context: Context) {
        if (initialized) return // 防止重复初始化
        dao = AppDatabase.getDatabase(context).appDao()
        val d = requireDao()

        // 监听并拉取所有课表配置
        coroutineScope.launch {
            d.getProfiles().collect { list ->
                if (list.isEmpty()) {
                    // 如果刚下载 App 一个课表都没有，自动建一个
                    val defaultProfile = ScheduleProfile(name = "我的课表")
                    d.insertProfile(defaultProfile)
                    // 立即更新状态，不等下一轮 collect
                    _profiles.value = listOf(defaultProfile)
                    _currentProfileId.value = defaultProfile.id
                } else {
                    _profiles.value = list
                    // 如果当前没选课表或当前课表已被删除，默认选第一个
                    if (_currentProfileId.value.isEmpty() || list.none { it.id == _currentProfileId.value }) {
                        _currentProfileId.value = list.first().id
                    }
                }
            }
        }

        // 监听并拉取所有具体课程数据
        coroutineScope.launch {
            d.getCourses().collect { list ->
                _allCourses.value = list
            }
        }
    }

    // --- 以下操作直接写入数据库，数据永不丢失 ---
    fun addProfile(name: String) {
        coroutineScope.launch {
            val d = requireDao()
            val newProfile = ScheduleProfile(name = name)
            d.insertProfile(newProfile)
            _currentProfileId.value = newProfile.id
        }
    }

    fun renameProfile(id: String, newName: String) {
        coroutineScope.launch {
            requireDao().updateProfileName(id, newName)
        }
    }

    fun deleteProfile(id: String) {
        if (_profiles.value.size <= 1) return
        coroutineScope.launch {
            val d = requireDao()
            d.deleteProfile(id)
            d.deleteCoursesByProfileId(id) // 课表删了，里面的课也要删干净
        }
    }

    fun switchProfile(id: String) {
        _currentProfileId.value = id
    }

    // 导入新课表写入数据库
    fun importCoursesToCurrentProfile(newCourses: List<Course>, isOverwrite: Boolean) {
        val curId = _currentProfileId.value
        if (curId.isEmpty() || !initialized) return
        // 把新课程绑上当前课表的ID，并且把课程ID设为0让Room自动生成主键
        val coursesToInsert = newCourses.map { it.copy(profileId = curId, id = 0) }

        coroutineScope.launch {
            val d = requireDao()
            if (isOverwrite) {
                d.overwriteCourses(curId, coursesToInsert)
            } else {
                d.insertCourses(coursesToInsert)
            }
        }
    }
}