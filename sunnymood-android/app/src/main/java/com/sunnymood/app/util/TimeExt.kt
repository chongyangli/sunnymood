package com.sunnymood.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WEEK_CN = mapOf(
    java.time.DayOfWeek.MONDAY to "一",
    java.time.DayOfWeek.TUESDAY to "二",
    java.time.DayOfWeek.WEDNESDAY to "三",
    java.time.DayOfWeek.THURSDAY to "四",
    java.time.DayOfWeek.FRIDAY to "五",
    java.time.DayOfWeek.SATURDAY to "六",
    java.time.DayOfWeek.SUNDAY to "日",
)

/** 今日 00:00（本地时区）——所有按日统计的日界，开发文档 §3.4 */
fun todayStartMillis(): Long =
    LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** "今天 · 周四"（小组件状态行） */
fun todayLabel(): String = "今天 · 周${WEEK_CN[LocalDate.now().dayOfWeek]}"

/** "周X"（近 7 日趋势 / 日历） */
fun weekdayShort(date: LocalDate): String = "周${WEEK_CN[date.dayOfWeek]}"

/** "周X"（报告洞察按星期聚合） */
fun weekdayShort(dayOfWeek: java.time.DayOfWeek): String = "周${WEEK_CN[dayOfWeek]}"

/** 某日 00:00（本地时区，按日统计的日界） */
fun dayStartMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 时间戳所在日的 00:00 */
fun dayStartMillis(ts: Long): Long =
    dayStartMillis(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate())

/** 某日最后一毫秒 */
fun dayEndMillis(date: LocalDate): Long = dayStartMillis(date.plusDays(1)) - 1

/** 月份起始（本地时区） */
fun monthStartMillis(year: Int, month: Int): Long =
    dayStartMillis(LocalDate.of(year, month, 1))

/** 月份结束（本月最后一毫秒，BOTH 兼容 BETWEEN 查询） */
fun monthEndMillis(year: Int, month: Int): Long =
    dayStartMillis(LocalDate.of(year, month, 1).plusMonths(1)) - 1

/** "2026年9月"（日历月份标题） */
fun monthTitle(year: Int, month: Int): String = "${year}年${month}月"

/** "9月12日 · 周五"（日历当日明细标题） */
fun shortDateLabel(date: LocalDate): String =
    "${date.monthValue}月${date.dayOfMonth}日 · ${weekdayShort(date)}"

/** "2026年9月10日 · 周四"（首页标题行） */
fun fullDateLabel(): String {
    val d = LocalDate.now()
    return "${d.year}年${d.monthValue}月${d.dayOfMonth}日 · 周${WEEK_CN[d.dayOfWeek]}"
}

/** 时间戳 -> "HH:mm"（今日时间线） */
fun formatTimeOfDay(ts: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(ts))
