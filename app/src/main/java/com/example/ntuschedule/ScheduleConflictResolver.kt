package com.example.ntuschedule

/**
 * 课表冲突检测与解决工具
 *
 * 规则：
 * 1. 优先显示时长最长的课程
 * 2. 时长相同时，优先显示开始时间最早的课程
 * 3. 时长和开始时间都相同时，随机显示其中一个（保留列表第一个）
 */

data class ConflictGroup(
    val mainCourse: Course,          // 主课程（按优先级选出）
    val allCourses: List<Course>     // 该格子的所有冲突课程（含主课程）
)

object ScheduleConflictResolver {

    /**
     * 对同一格子（同一天、同一时间段）的课程列表进行冲突检测
     * 返回：该格子应显示的课程列表（每个格子只显示一个主课程，附上冲突信息）
     */
    fun resolve(courses: List<Course>): List<ConflictGroup> {
        if (courses.isEmpty()) return emptyList()

        // 按 (dayOfWeek, startPeriod, endPeriod) 完全相同的格子分组
        val grouped = courses.groupBy { Triple(it.dayOfWeek, it.startPeriod, it.endPeriod) }

        return grouped.map { (_, group) ->
            // 选主课程：时长最长 > 开始时间最早 > 保留第一个（稳定排序）
            val main = group.maxWithOrNull(
                compareByDescending<Course> { it.endPeriod - it.startPeriod + 1 } // 时长
                    .thenBy { it.startPeriod }                                    // 开始时间最早
            ) ?: group.first() // 理论不会走到这

            ConflictGroup(mainCourse = main, allCourses = group)
        }
    }

    /** 判断某个格子的课程是否有冲突（同一格子有多门课） */
    fun hasConflict(group: ConflictGroup): Boolean = group.allCourses.size > 1
}
