package com.sunnymood.app.ui.report

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunnymood.app.R
import com.sunnymood.app.SunnyMoodApp
import com.sunnymood.app.data.db.MoodRecord
import com.sunnymood.app.report.AdviceEngine
import com.sunnymood.app.report.MonthReport
import com.sunnymood.app.report.MoodReportGenerator
import com.sunnymood.app.util.MoodSpec
import com.sunnymood.app.util.monthEndMillis
import com.sunnymood.app.util.monthStartMillis
import com.sunnymood.app.util.monthTitle
import com.sunnymood.app.util.shortDateLabel
import com.sunnymood.app.util.weekdayShort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 报告 ViewModel（M3）：
 * 月份切换 → 本月记录 Flow + 上月均分一次性查询 → MoodReportGenerator 统计 → AdviceEngine 建议。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(app: Application) : AndroidViewModel(app) {

    private val sunny = app as SunnyMoodApp

    val month = MutableStateFlow(YearMonth.now())

    val records: StateFlow<List<MoodRecord>> = month
        .flatMapLatest { ym ->
            sunny.repository.observeRange(
                monthStartMillis(ym.year, ym.monthValue),
                monthEndMillis(ym.year, ym.monthValue)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val prevScore = MutableStateFlow<Double?>(null)

    val report: StateFlow<MonthReport?> = combine(records, month, prevScore) { rs, ym, prev ->
        MoodReportGenerator.generate(rs, ym.year, ym.monthValue, prev)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            month.collect { ym ->
                val prev = ym.minusMonths(1)
                val rs = runCatching {
                    sunny.repository.getRange(
                        monthStartMillis(prev.year, prev.monthValue),
                        monthEndMillis(prev.year, prev.monthValue)
                    )
                }.getOrDefault(emptyList())
                prevScore.value = if (rs.isEmpty()) null else rs.map { it.moodLevel }.average()
            }
        }
    }

    fun prevMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
}

/** 月度报告页（M3，开发文档 §4.5 信息架构） */
@Composable
fun ReportScreen(vm: ReportViewModel = viewModel()) {
    val month by vm.month.collectAsState()
    val records by vm.records.collectAsState()
    val report by vm.report.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 月份切换器
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.prevMonth() }) {
                Text("‹", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                text = monthTitle(month.year, month.monthValue),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.nextMonth() }) {
                Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(12.dp))

        val r = report
        if (r == null || records.isEmpty()) {
            // 空月份：轻量提示，不渲染空图表（§4.6）
            Text(
                text = stringResource(R.string.report_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp)
            )
        } else {
            val advice = remember(r, records) { AdviceEngine.generate(r, records) }

            // 关怀卡片（§3.6.3，触发式）
            if (advice.careNeeded) {
                CareCard()
                Spacer(Modifier.height(14.dp))
            }

            // ① 月评分卡
            ScoreCard(r)
            Spacer(Modifier.height(14.dp))

            // ② 分布环形图
            SectionCard(title = stringResource(R.string.report_distribution)) {
                DistributionChart(r)
            }
            Spacer(Modifier.height(14.dp))

            // ③ 趋势图（柱状 + 7 日均线）
            SectionCard(title = stringResource(R.string.report_trend)) {
                TrendChart(r)
            }
            Spacer(Modifier.height(14.dp))

            // ④ 身体感觉卡（Top3 + 关联）
            SectionCard(title = stringResource(R.string.report_body)) {
                BodySection(r)
            }
            Spacer(Modifier.height(14.dp))

            // ⑤ 洞察卡
            SectionCard(title = stringResource(R.string.report_insights)) {
                InsightsSection(r)
            }
            Spacer(Modifier.height(14.dp))

            // ⑥ 建议卡
            if (advice.advices.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.report_advices)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        advice.advices.forEachIndexed { i, a ->
                            Row {
                                Text(
                                    text = "${i + 1}. ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = a,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // ⑦ 页脚：文字总结 + 免责说明
            SummaryFooter(r)
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 关怀卡片：低落期提醒 + 心理援助热线 12356（§3.6.3） */
@Composable
private fun CareCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFE85D8A).copy(alpha = 0.12f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.report_care_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFC2446C)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.report_care_body),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** 月评分卡：大号评分 + 环比 + 覆盖天数（§4.5） */
@Composable
private fun ScoreCard(r: MonthReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = r.score?.let { format1(it) } ?: "--",
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            color = r.score?.let { MoodSpec.color(it.roundToInt().coerceIn(1, 7)) }
                ?: MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val change = r.changeFromPrev
            if (change != null && change != 0.0) {
                val up = change > 0
                Text(
                    text = (if (up) "↑" else "↓") + format1(kotlin.math.abs(change)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (up) Color(0xFF34C77B) else Color(0xFFE85D8A)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(
                    R.string.report_score_recorded, r.recordedDays, r.totalDays
                ),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 通用卡片容器 */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** 分布环形图（Canvas drawArc 分段 + 图例） */
@Composable
private fun DistributionChart(r: MonthReport) {
    val total = r.distribution.values.sum().coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2
                var startAngle = -90f
                MoodSpec.levels.forEach { spec ->
                    val count = r.distribution[spec.level] ?: 0
                    if (count > 0) {
                        val sweep = count.toFloat() / total * 360f
                        drawArc(
                            color = spec.color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                            topLeft = Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - stroke, size.height - stroke
                            )
                        )
                        startAngle += sweep
                    }
                }
            }
            Text(
                text = "${r.recordedDays}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MoodSpec.levels.forEach { spec ->
                val count = r.distribution[spec.level] ?: 0
                if (count > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(spec.color, RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                R.string.report_dist_item,
                                spec.name, count, (count * 100 / total)
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/** 日趋势：柱状（日均分着色）+ 7 日移动平均线（Canvas） */
@Composable
private fun TrendChart(r: MonthReport) {
    val lineColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        val n = r.trend.size.coerceAtLeast(1)
        val dayW = size.width / n
        val barW = dayW * 0.55f
        val bottomPad = 4.dp.toPx()

        // 柱状
        r.trend.forEachIndexed { i, p ->
            if (p.avg != null) {
                val h = (p.avg / 7.0).toFloat() * (size.height - bottomPad)
                drawRoundRect(
                    color = MoodSpec.color(p.avg!!.roundToInt().coerceIn(1, 7)).copy(alpha = 0.85f),
                    topLeft = Offset(i * dayW + (dayW - barW) / 2, size.height - bottomPad - h),
                    size = androidx.compose.ui.geometry.Size(barW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                )
            } else {
                drawRoundRect(
                    color = emptyColor,
                    topLeft = Offset(i * dayW + (dayW - barW) / 2, size.height - bottomPad - 3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(barW, 3.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                )
            }
        }

        // 7 日移动平均线
        val pts = r.trend.mapIndexedNotNull { i, p ->
            r.movingAvg[p.date]?.let {
                Offset(
                    x = i * dayW + dayW / 2,
                    y = size.height - bottomPad - (it / 7.0).toFloat() * (size.height - bottomPad)
                )
            }
        }
        if (pts.size >= 2) {
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color = lineColor, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

/** 身体感觉 Top3 + 心情 × 身体关联 */
@Composable
private fun BodySection(r: MonthReport) {
    if (r.bodyTop.isEmpty()) {
        Text(
            text = stringResource(R.string.report_body_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        r.bodyTop.forEachIndexed { i, stat ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${i + 1}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stat.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.report_body_days, stat.days),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        r.correlations.forEach { c ->
            val resId = if (c.diff < 0) R.string.report_corr_down else R.string.report_corr_up
            Text(
                text = stringResource(resId, c.name, format1(kotlin.math.abs(c.diff))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

/** 洞察：最快乐的一天 / 心情最好的星期 / 最长连续记录 */
@Composable
private fun InsightsSection(r: MonthReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        r.happiestDay?.let { d ->
            Text(
                text = stringResource(
                    R.string.report_happiest,
                    shortDateLabel(d), format1(r.happiestScore ?: 0.0)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        r.bestWeekday?.let { w ->
            Text(
                text = stringResource(
                    R.string.report_best_weekday,
                    weekdayShort(w), format1(r.bestWeekdayAvg ?: 0.0)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (r.longestStreak > 0) {
            Text(
                text = stringResource(R.string.report_streak, r.longestStreak),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (r.happiestDay == null && r.bestWeekday == null && r.longestStreak == 0) {
            Text(
                text = stringResource(R.string.report_insights_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 页脚：文字总结 + 免责说明（§3.6.3） */
@Composable
private fun SummaryFooter(r: MonthReport) {
    val score = r.score
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (score != null) {
            val change = r.changeFromPrev
            val summary = when {
                change == null -> stringResource(R.string.report_summary_noprev, format1(score))
                change > 0.05 -> stringResource(R.string.report_summary_up, format1(score), format1(change))
                change < -0.05 -> stringResource(R.string.report_summary_down, format1(score), format1(-change))
                else -> stringResource(R.string.report_summary_same, format1(score))
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = stringResource(R.string.report_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

private fun format1(v: Double): String =
    String.format(Locale.US, "%.1f", v)
