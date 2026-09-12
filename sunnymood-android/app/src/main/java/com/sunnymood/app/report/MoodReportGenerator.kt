package com.sunnymood.app.report

import com.sunnymood.app.data.db.MoodRecord
import com.sunnymood.app.util.MoodSpec
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToInt

/** 单日趋势点（avg 为 null 表示当天无记录） */
data class DayPoint(val date: LocalDate, val avg: Double?, val count: Int)

/** 身体感觉统计 */
data class BodyStat(val name: String, val days: Int)

/** 心情 × 身体关联：出现该感觉的日均分与未出现日均分之差（负数 = 状态更差） */
data class BodyCorrelation(val name: String, val diff: Double)

/** 月度报告（开发文档 §3.6.1 统计指标） */
data class MonthReport(
    val year: Int,
    val month: Int,
    val score: Double?,                 // 月评分 1.0~7.0
    val prevScore: Double?,             // 上月评分（环比基准）
    val recordedDays: Int,
    val totalDays: Int,
    val distribution: Map<Int, Int>,    // 心情分布：等级 -> 条数
    val trend: List<DayPoint>,          // 日趋势（日均分）
    val movingAvg: Map<LocalDate, Double>, // 7 日移动平均线
    val happiestDay: LocalDate?,        // 最快乐的一天（日均分最高的日子）
    val happiestScore: Double?,
    val bestWeekday: DayOfWeek?,        // 心情最好的星期
    val bestWeekdayAvg: Double?,
    val longestStreak: Int,             // 本月最长连续记录天数
    val bodyTop: List<BodyStat>,        // 身体感觉 Top 3（按出现天数）
    val correlations: List<BodyCorrelation>
) {
    val coverage: Double get() = if (totalDays == 0) 0.0 else recordedDays.toDouble() / totalDays
    val changeFromPrev: Double?
        get() = if (score == null || prevScore == null) null else score - prevScore
}

/**
 * 月度报告统计（纯 Kotlin，便于单元测试；§3.6.1）。
 * 输入为本月全部记录（已过滤墓碑）与上月评分。
 */
object MoodReportGenerator {

    fun generate(
        records: List<MoodRecord>,
        year: Int,
        month: Int,
        prevScore: Double? = null
    ): MonthReport {
        val zone = ZoneId.systemDefault()
        val byDay: Map<LocalDate, List<MoodRecord>> = records.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }

        val ym = YearMonth.of(year, month)
        val days = (1..ym.lengthOfMonth()).map { ym.atDay(it) }
        val trend = days.map { d ->
            val rs = byDay[d].orEmpty()
            DayPoint(d, if (rs.isEmpty()) null else rs.map { it.moodLevel }.average(), rs.size)
        }

        val score = if (records.isEmpty()) null else records.map { it.moodLevel }.average()

        // 7 日移动平均：窗口内按有数据的日均分平均（至少 1 个数据点）
        val movingAvg = buildMap {
            trend.forEach { point ->
                val window = trend.filter {
                    !it.date.isAfter(point.date) &&
                        it.date.isAfter(point.date.minusDays(7))
                }.mapNotNull { it.avg }
                if (window.isNotEmpty()) {
                    put(point.date, window.average())
                }
            }
        }

        // 最快乐的一天
        val (happiestDay, happiestScore) = trend
            .filter { it.avg != null }
            .maxByOrNull { it.avg!! }
            ?.let { it.date to it.avg } ?: (null to null)

        // 心情最好的星期
        val weekdayAvg: Map<DayOfWeek, Double> = records
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().dayOfWeek }
            .mapValues { (_, rs) -> rs.map { it.moodLevel }.average() }
        val best = weekdayAvg.maxByOrNull { it.value }

        // 最长连续记录天数
        val recorded = trend.filter { it.count > 0 }.map { it.date }.toSet()
        var streak = 0
        var maxStreak = 0
        days.forEach { d ->
            if (d in recorded) {
                streak += 1
                maxStreak = maxOf(maxStreak, streak)
            } else {
                streak = 0
            }
        }

        // 身体感觉：按「出现天数」排行（一条记录可能含多个感觉）
        val feelingDays: Map<String, Int> = buildMap {
            byDay.forEach { (_, rs) ->
                rs.flatMap { MoodSpec.decodeFeelings(it.bodyFeelings) }.toSet().forEach { f ->
                    merge(f, 1, Int::plus)
                }
            }
        }
        val bodyTop = feelingDays.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(3)
            .map { BodyStat(it.key, it.value) }

        // 心情 × 身体关联：出现日均分 vs 未出现日均分之差（保留 1 位小数精度差异可忽略）
        val overallDayAvg = trend.mapNotNull { it.avg }
        val overall = if (overallDayAvg.isEmpty()) null else overallDayAvg.average()
        val correlations = bodyTop.mapNotNull { stat ->
            val with = trend.filter { p -> byDay[p.date].orEmpty().any { r ->
                MoodSpec.decodeFeelings(r.bodyFeelings).contains(stat.name)
            } }.mapNotNull { it.avg }
            val without = trend.filterNot { p -> byDay[p.date].orEmpty().any { r ->
                MoodSpec.decodeFeelings(r.bodyFeelings).contains(stat.name)
            } }.mapNotNull { it.avg }
            if (with.isEmpty() || without.isEmpty() || overall == null) {
                null
            } else {
                val diff = ((with.average() - without.average()) * 10).roundToInt() / 10.0
                BodyCorrelation(stat.name, diff)
            }
        }

        return MonthReport(
            year = year,
            month = month,
            score = score,
            prevScore = prevScore,
            recordedDays = byDay.size,
            totalDays = ym.lengthOfMonth(),
            distribution = records.groupingBy { it.moodLevel }.eachCount(),
            trend = trend,
            movingAvg = movingAvg,
            happiestDay = happiestDay,
            happiestScore = happiestScore,
            bestWeekday = best?.key,
            bestWeekdayAvg = best?.value,
            longestStreak = maxStreak,
            bodyTop = bodyTop,
            correlations = correlations
        )
    }
}
