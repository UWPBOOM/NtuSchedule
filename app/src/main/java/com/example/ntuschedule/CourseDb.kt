package com.example.ntuschedule // ⭐记得改包名

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// 1. 课表档案表
@Entity(tableName = "profiles")
data class ScheduleProfile(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String
)

// 2. 课程表（加回了 Room 的实体注解！）
@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val profileId: String = "", // 绑定课表ID
    val name: String,
    val teacher: String,
    val room: String,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: String,
    val colorIndex: Int
)

// 3. 数据库操作对象 (DAO)
@Dao
interface AppDao {
    @Query("SELECT * FROM profiles")
    fun getProfiles(): Flow<List<ScheduleProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ScheduleProfile)

    @Query("UPDATE profiles SET name = :newName WHERE id = :id")
    suspend fun updateProfileName(id: String, newName: String)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("SELECT * FROM courses")
    fun getCourses(): Flow<List<Course>>

    @Insert
    suspend fun insertCourses(courses: List<Course>)

    @Query("DELETE FROM courses WHERE profileId = :profileId")
    suspend fun deleteCoursesByProfileId(profileId: String)

    // 覆盖导入时：先删旧课，再插新课
    @Transaction
    suspend fun overwriteCourses(profileId: String, courses: List<Course>) {
        deleteCoursesByProfileId(profileId)
        insertCourses(courses)
    }
}

// 4. Room 数据库本体
@Database(entities = [Course::class, ScheduleProfile::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schedule_database"
                )
                    // 自动清理旧版本不兼容的表结构，防止崩溃
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}