# 🎓 南通大学课程表 NtuSchedule

> 基于 Jetpack Compose + Material Design 3 的南通大学教务系统课表管理 Android App

[![Release](https://img.shields.io/badge/release-v1.3-blue)](https://github.com/UWPBOOM/NtuSchedule/releases)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.0-purple)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/compose-Material%203-green)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/min%20sdk-26-orange)](https://developer.android.com)
[![APK Size](https://img.shields.io/badge/APK%20size-<5MB-success)](https://github.com/UWPBOOM/NtuSchedule/releases)

## ✨ 功能特性

- **📅 周视图课表** — HorizontalPager 横向滑动切换 1-20 周，课程卡片展示课程名、教室、教师、节次
- **🔐 智能导入** — WebView 自动登录教务系统，JS 注入填写表单，一键抓取解析 HTML 课表
- **📂 多课表管理** — Room 数据库持久化，支持创建、重命名、切换、删除多个课表配置；每个课表可独立设置开学日期
- **🔀 调休迁移** — 支持将指定日期的所有课程迁移到另一天，三步确认流程（选原日期→检测有课→选目标日期），多条记录管理，即时生效
- **⚠️ 冲突检测** — 同一时间段多门课程时自动按优先级显示主课程（时长最长 > 开始最早），右上角红色三角提醒，详情页完整列出所有冲突课程
- **🎨 主题配色** — 莫兰迪 / 马卡龙 / MD3 三套卡片配色方案，课程预览台实时预览
- **🖼️ 自定义壁纸** — 相册选取图片，`ContentScale.Crop` 保持纵横比铺满全屏，设置页实时预览
- **🌈 全局配色** — 8 种 Google Material 主色调，一键切换全局图标、标题、强调文字颜色
- **📱 沉浸式状态栏** — `enableEdgeToEdge()` + 透明状态栏，壁纸穿透系统栏，全屏蒙层覆盖状态栏
- **💾 离线优先** — Room + DataStore 本地存储，无需网络即可查看课表，调休记录持久化
- **✨ 彩蛋** — 设置页版本号连点 3 下复制作者 QQ

## 🔧 技术栈

| 类别 | 技术 |
|------|------|
| UI 框架 | Jetpack Compose + Material Design 3 |
| 导航 | Navigation Compose + 自定义 iOS 风格过场动画 |
| 数据库 | Room + KSP 编译时注解 |
| 偏好存储 | DataStore Preferences（全局设置 + 课表独立设置） |
| 网络请求 | OkHttp 4.12 |
| HTML 解析 | Jsoup 1.17 |
| 图片加载 | Coil Compose |
| 日历计算 | Java Calendar API + SimpleDateFormat |

## 🏗️ 项目结构

```
app/src/main/java/com/example/ntuschedule/
├── MainActivity.kt                # 主入口，NavHost 导航 + 全局配色初始化
├── ScheduleUI.kt                  # 课表网格 UI (HorizontalPager + 课程卡片 + 冲突检测)
│   ├── ScheduleGrid()             # 主课表页面
│   ├── CourseCard()               # 课程卡片（含冲突三角指示器）
│   ├── ProfileManagementSheet()   # 课表管理弹窗（全屏沉浸式蒙层 + 滑入动画）
│   ├── Palettes / getSectionTime()# 配色方案 & 时间表
├── LoginScreen.kt                 # 教务系统 WebView 智能登录（JS 注入 + 手动接管）
├── WebViewImportScreen.kt         # 通用 WebView 导入（底部控制栏 + JS 抓取）
├── CourseDb.kt                    # Room 数据库 (Course + ScheduleProfile + AppDao)
├── CourseRepository.kt            # 数据仓库单例 (StateFlow 响应式)
├── CourseParser.kt                # 课表 HTML 解析器（Jsoup + Regex）
├── ScheduleConflictResolver.kt    # 课程冲突检测与优先级排序
├── PreferencesManager.kt          # DataStore 全局偏好 (日期/主题/壁纸/配色/账号)
├── ProfilePreferencesManager.kt   # DataStore 课表独立偏好 (开学日期/调休记录)
├── ProfileSettingsScreen.kt       # 课表专属设置页（开学日期 + 调休管理）
├── SettingsScreen.kt              # 全局个性化设置 (主题/壁纸/配色/日期)
│   ├── AccentColorOptions         # 8 种 Google Material 主色调
│   ├── MainSettingsContent        # 主设置列表
│   ├── ThemeSettingsSubScreen     # 主题设置二级菜单（预览台 + 配色 + 壁纸）
└── ui/theme/
    ├── Color.kt                   # 颜色定义
    ├── Theme.kt                   # NtuScheduleTheme（动态取色 + 自定义主色）
    └── Type.kt                    # 字体排版
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

## 📝 设计原则

- **极简** — 零装饰，纯内容导向，< 5 MB APK 体积
- **纯原生** — 无第三方 SDK，纯 Kotlin + Jetpack Compose + MD3
- **仅三个必要权限** — Internet（WebView 导入）、Read Media Images（壁纸）、Storage（缓存）
- **不上传数据** — 所有数据本地存储，无网络上报
- **MD3 一致性** — 全局设置页、课表设置页、弹窗组件视觉风格统一

## 📝 License

```
Copyright © 2026 UWPBOOM. All Rights Reserved.
```

本项目仅供学习交流使用，不得用于商业用途。
