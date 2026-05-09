# 🎓 南通大学课程表 NtuSchedule

> 基于 Jetpack Compose + Material Design 3 的南通大学教务系统课表管理 Android App

[![Release](https://img.shields.io/badge/release-v1.3-blue)](https://github.com/UWPBOOM/NtuSchedule/releases)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.0-purple)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/compose-Material%203-green)](https://developer.android.com/jetpack/compose)

## ✨ 功能特性

- **📅 周视图课表** — HorizontalPager 横向滑动切换 1-20 周，课程卡片展示课程名、教室、教师、节次
- **🔐 智能导入** — WebView 自动登录教务系统，JS 注入填写表单，一键抓取解析 HTML 课表
- **📂 多课表管理** — Room 数据库持久化，支持创建、重命名、切换、删除多个课表配置
- **🎨 主题配色** — 莫兰迪 / 马卡龙 / MD3 三套卡片配色方案，课程预览台实时预览
- **🖼️ 自定义壁纸** — 相册选取图片，`ContentScale.Crop` 保持纵横比铺满全屏
- **🌈 全局配色** — 8 种 Google Material 主色调（默认/蔚蓝/赤红/翠绿/橙黄/青绿/粉红/靛蓝），改变全局图标与标题颜色
- **📱 沉浸式状态栏** — `enableEdgeToEdge()` + 透明状态栏，壁纸穿透系统栏
- **💾 离线优先** — Room + DataStore 本地存储，无需网络即可查看课表
- **✨ 彩蛋** — 设置页版本号连点 3 下复制作者 QQ

## 🔧 技术栈

| 类别 | 技术 |
|------|------|
| UI 框架 | Jetpack Compose + Material Design 3 |
| 导航 | Navigation Compose |
| 数据库 | Room + KSP |
| 偏好存储 | DataStore Preferences |
| 网络请求 | OkHttp 4.12 |
| HTML 解析 | Jsoup 1.17 |
| 图片加载 | Coil Compose |
| 学期时间 | Java Calendar API |

## 🏗️ 项目结构

```
app/src/main/java/com/example/ntuschedule/
├── MainActivity.kt          # 主入口，NavHost 导航
├── ScheduleUI.kt            # 课表网格 UI (HorizontalPager + 课程卡片)
├── LoginScreen.kt           # 教务系统 WebView 智能登录
├── WebViewImportScreen.kt   # 通用 WebView 导入
├── CourseDb.kt              # Room 数据库 (Course + ScheduleProfile + AppDao)
├── CourseRepository.kt      # 数据仓库单例 (StateFlow)
├── CourseParser.kt          # 课表 HTML 解析器
├── PreferencesManager.kt    # DataStore 偏好 (日期/主题/壁纸/配色)
├── SettingsScreen.kt        # 个性化设置 (主题/壁纸/配色/日期)
└── ui/theme/
    ├── Color.kt             # 颜色定义
    ├── Theme.kt             # NtuScheduleTheme (动态取色 + 自定义主色)
    └── Type.kt              # 字体排版
```

## 🚀 构建运行

### 环境要求

- Android Studio Hedgehog+
- JDK 17
- Android SDK 34
- Gradle 8.2+

### 步骤

```bash
git clone https://github.com/UWPBOOM/NtuSchedule.git
cd NtuSchedule
./gradlew assembleDebug
```

或直接用 Android Studio 打开项目 → Run。

### Release 签名

在项目根目录放置 `ntu_key.jks`，`local.properties` 中配置：

```properties
storeFile=ntu_key.jks
storePassword=你的密码
keyAlias=ntu_key
keyPassword=你的密码
```

```bash
./gradlew assembleRelease
```

## 📝 License

```
Copyright © 2026 UWPBOOM. All Rights Reserved.
```

本项目仅供学习交流使用，不得用于商业用途。
