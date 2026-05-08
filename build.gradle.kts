// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
    // 👇 下面这行就是补上的 KSP 插件和匹配的版本号
    id("com.google.devtools.ksp") version "1.9.0-1.0.13" apply false
}