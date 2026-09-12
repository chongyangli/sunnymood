package com.sunnymood.app.report

import com.sunnymood.app.util.MoodSpec
import java.time.Instant
import java.time.ZoneId

/** 建议结果：规则建议 0~3 条 + 关怀卡片判定（§3.6.2 / §3.6.3） */
data class AdviceResult(
    val advices: List<String>,
    val careNeeded: Boolean
)

/**
 * 月度建议引擎（本地规则，无网络；§3.6.2）。
 * 按优先级排序，每月最多展示 3 条；措辞遵循非评判性规范（§3.6.3）。
 */
object AdviceEngine {

    /** 偏负面身体感觉（§4.3 词表前 8 项） */
    private val NEGATIVE = setOf(
        "疲惫", "困倦", "不想动", "没睡好", "头昏脑涨", "肩颈酸痛", "眼睛干涩", "胃部不适"
    )

    /** 触发关怀：连续 ≥5 天日均分 ≤2 且伴随高频负面身体感觉 */
    private const val CARE_DAYS = 5
    private const val CARE_LEVEL_MAX = 2
    private const val CARE_MIN_NEGATIVE_DAYS = 3

    fun generate(report: MonthReport, monthRecords: List<com.sunnymood.app.data.db.MoodRecord>): AdviceResult {
        val advices = mutableListOf<String>()

        // 按日聚合（感觉去重按天计数）
        val zone = ZoneId.systemDefault()
        val byDay: Map<java.time.LocalDate, List<com.sunnymood.app.data.db.MoodRecord>> =
            monthRecords.groupBy {
                Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
            }
        val recordedDays = byDay.size

        // ① 睡眠类消耗：疲惫/困倦/没睡好 合计出现天数 ≥ 40% 记录天数
        if (recordedDays > 0) {
            val sleepDays = byDay.values.count { rs ->
                rs.any { MoodSpec.decodeFeelings(it.bodyFeelings).any { f -> f in setOf("疲惫", "困倦", "没睡好") } }
            }
            if (sleepDays >= recordedDays * 0.4) {
                advices += "本月有 $sleepDays 天出现疲惫或困倦，睡眠可能是主要消耗源，试试固定就寝时间"
            }
        }

        // ② 某身体感觉日的日均分显著低于整体（差 ≥ 1.0）
        report.correlations
            .filter { it.name in NEGATIVE && it.diff <= -1.0 }
            .maxByOrNull { -it.diff }
            ?.let {
                advices += "出现「${it.name}」的日子心情平均低 ${format1(-it.diff)} 分，" +
                    "留意工作强度，主动安排恢复时间"
            }

        // ③ 周一均分比周内均值低 ≥ 1.0
        val weekdayAvg = monthRecords
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().dayOfWeek }
            .mapValues { (_, rs) -> rs.map { it.moodLevel }.average() }
        val monday = weekdayAvg[java.time.DayOfWeek.MONDAY]
        val weekOverall = weekdayAvg.values
        if (monday != null && weekOverall.size > 1) {
            val diff = weekOverall.average() - monday
            if (diff >= 1.0) {
                advices += "周一心情明显偏低，周日晚提前做点轻松的准备，能明显降低周一的启动成本"
            }
        }

        // ④ 正面感觉与高心情共现
        val positiveDays = byDay.values.filter { rs ->
            rs.any { MoodSpec.decodeFeelings(it.bodyFeelings).any { f -> f in setOf("精力充沛", "身体轻盈") } }
        }
        if (positiveDays.size >= 2) {
            val avg = positiveDays.flatten().map { it.moodLevel }.average()
            if (avg >= 5.0) {
                advices += "精力充沛的日子你的心情也更好（平均 ${format1(avg)} 分），保持当前的节奏"
            }
        }

        // ⑤ 覆盖率低，鼓励连续记录
        if (report.score != null && report.coverage < 0.5) {
            val missed = report.totalDays - report.recordedDays
            advices += "本月有 $missed 天没有记录，数据越连续，建议越准——试试把小组件放到顺手的位置"
        }

        // 触发式关怀判定（§3.6.3）
        val careNeeded = checkCare(byDay)

        return AdviceResult(advices.take(3), careNeeded)
    }

    /** 连续 ≥5 天日均分 ≤2，且这些天里负面身体感觉出现 ≥3 天 → 显示关怀卡片 */
    private fun checkCare(byDay: Map<java.time.LocalDate, List<com.sunnymood.app.data.db.MoodRecord>>): Boolean {
        val sortedDays = byDay.keys.sorted()
        if (sortedDays.size < CARE_DAYS) return false

        var run = 0
        var runNegativeDays = 0
        var best = 0
        var bestNegative = 0
        sortedDays.forEach { d ->
            val rs = byDay[d].orEmpty()
            val avg = rs.map { it.moodLevel }.average()
            if (avg <= CARE_LEVEL_MAX) {
                run += 1
                if (rs.any { MoodSpec.decodeFeelings(it.bodyFeelings).any { f -> f in NEGATIVE } }) {
                    runNegativeDays += 1
                }
                if (run > best) {
                    best = run
                    bestNegative = runNegativeDays
                }
            } else {
                run = 0
                runNegativeDays = 0
            }
        }
        return best >= CARE_DAYS && bestNegative >= CARE_MIN_NEGATIVE_DAYS
    }

    private fun format1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)
}
