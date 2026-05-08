package com.example.ntuschedule // ⭐包名确认无误

import org.jsoup.Jsoup

object CourseParser {
    fun parseHtml(html: String): List<Course> {
        val courseList = mutableListOf<Course>()

        // 1. 强力清洗 HTML
        var cleanHtml = html
        if (cleanHtml.startsWith("\"") && cleanHtml.endsWith("\"")) {
            cleanHtml = cleanHtml.substring(1, cleanHtml.length - 1)
        }
        cleanHtml = cleanHtml.replace("\\\"", "\"")
            .replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\n", "")
            .replace("\\t", "")
            .replace("\\\\", "\\")

        val document = Jsoup.parse(cleanHtml)
        var colorCounter = 0

        // ==========================================
        // 第一部分：抓取课表网格（Grid）里的正常课程
        // ==========================================

        // ⭐ 终极绝招：直接遍历带有 id 属性的 td 单元格 (例如 id="2-1" 代表周二第1节)
        // 你们系统的 rowspan 已经处理好了连堂课，我们只需要认准 td 里面的课程块
        val tds = document.select("td[id]")

        for (td in tds) {
            val tdId = td.attr("id")
            val idMatch = Regex("^(\\d+)-(\\d+)$").find(tdId) ?: continue

            var dayOfWeek = idMatch.groupValues[1].toInt()
            if (dayOfWeek == 0) dayOfWeek = 7 // 修复周日可能为0的情况

            // 获取这个格子里所有的课程块（你们系统会把单双周的不同课塞进同一个格子里）
            val courseContainers = td.select("div.timetable_con")

            for (container in courseContainers) {
                try {
                    // 1. 提取课程名 (兼容 <span> 和 <u> 标签)
                    var name = container.select(".title").text()
                    if (name.isEmpty()) continue
                    // 清理垃圾字符：【调】、符号等
                    name = name.replace("【调】", "").replace("■", "").replace("◆", "").replace("▲", "").trim()

                    // 2. 提取教师
                    val teacherNode = container.select("[title*=教师]").first()
                    val teacher = teacherNode?.parent()?.text()?.trim() ?: "未知教师"

                    // 3. 提取教室
                    val roomNode = container.select("[title*=地点]").first()
                    val room = roomNode?.parent()?.text()?.trim()
                        ?.replace("啬园校区", "")?.trim() ?: "未排地点"

                    // 4. 提取时间与周次 (极其关键！)
                    val timeNode = container.select("[title*=节/周]").first()
                    val timeText = timeNode?.parent()?.text()?.replace(" ", "") ?: ""

                    // 匹配 "(1-2节)1-16周" 或 "(3-5节)1-7周(单),8-10周"
                    val timeRegex = Regex("\\((\\d+)-(\\d+)节?\\)(.*)")
                    val timeMatch = timeRegex.find(timeText)

                    var startPeriod = idMatch.groupValues[2].toInt() // 默认使用 td 的节次
                    var endPeriod = startPeriod + 1
                    var weeksRaw = "1"

                    if (timeMatch != null) {
                        startPeriod = timeMatch.groupValues[1].toInt()
                        endPeriod = timeMatch.groupValues[2].toInt()
                        weeksRaw = timeMatch.groupValues[3]
                    }

                    courseList.add(
                        Course(
                            id = 0,
                            profileId = "",
                            name = name,
                            teacher = teacher,
                            room = room,
                            dayOfWeek = dayOfWeek,
                            startPeriod = startPeriod,
                            endPeriod = endPeriod,
                            weeks = parseWeeks(weeksRaw),
                            colorIndex = colorCounter++
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // ==========================================
        // 第二部分：抓取底部的“其它课程”（实习、课程设计等）
        // ==========================================
        try {
            // 找到包含“其它课程：”的 span
            val otherCourseTitleSpan = document.select("span:contains(其它课程：)").first()
            if (otherCourseTitleSpan != null) {
                // 内容在紧跟它后面的 span 里
                val contentSpan = otherCourseTitleSpan.nextElementSibling()
                if (contentSpan != null) {
                    val otherText = contentSpan.text() // 例：电工电子实习◆张旭东(共1周)/17周/无;精密机械...
                    val courseItems = otherText.split(";")

                    for (item in courseItems) {
                        if (item.isBlank()) continue

                        // 按斜杠分割：[0]名称与教师 [1]周次 [2]地点
                        val parts = item.split("/")
                        if (parts.isNotEmpty()) {
                            val nameAndTeacher = parts[0]
                            val weeksRaw = if (parts.size > 1) parts[1] else "1"
                            val room = if (parts.size > 2) parts[2] else "未排地点"

                            // 用符号切割名称和老师 (电工电子实习◆张旭东(共1周))
                            var name = nameAndTeacher
                            var teacher = "未知教师"
                            val symbolRegex = Regex("([■◆▲]+)")
                            val nameParts = nameAndTeacher.split(symbolRegex)
                            if (nameParts.size >= 2) {
                                name = "[实践] " + nameParts[0].trim() // 加上前缀以示区别
                                teacher = nameParts[1].replace(Regex("\\(.*?\\)"), "").trim() // 删掉"(共x周)"
                            } else {
                                name = "[实践] " + name
                            }

                            courseList.add(
                                Course(
                                    id = 0,
                                    profileId = "",
                                    name = name,
                                    teacher = teacher,
                                    room = room,
                                    dayOfWeek = 7, // ⭐ 强制安排在周日
                                    startPeriod = 10, // ⭐ 安排在晚上第10-12节，避免挤占白天课表
                                    endPeriod = 12,
                                    weeks = parseWeeks(weeksRaw),
                                    colorIndex = colorCounter++
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 安全兜底去重
        return courseList.distinctBy { it.name + it.dayOfWeek + it.startPeriod + it.weeks }
    }

    private fun parseWeeks(weeksRaw: String): String {
        if (weeksRaw.isBlank()) return "1"
        val result = mutableSetOf<Int>()
        // 清理文字，保留数字、逗号、中划线和单双标记
        val cleanStr = weeksRaw.replace("周", "").replace("节", "")
        val parts = cleanStr.split(",")

        for (part in parts) {
            val isOdd = part.contains("单")
            val isEven = part.contains("双")
            val rangeStr = part.replace("单", "").replace("双", "").replace("(", "").replace(")", "").trim()

            if (rangeStr.contains("-")) {
                val bounds = rangeStr.split("-")
                if (bounds.size == 2) {
                    val s = bounds[0].toIntOrNull() ?: continue
                    val e = bounds[1].toIntOrNull() ?: continue
                    for (week in s..e) {
                        if (isOdd && week % 2 == 0) continue
                        if (isEven && week % 2 != 0) continue
                        result.add(week)
                    }
                }
            } else {
                rangeStr.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result.sorted().joinToString(",")
    }
}