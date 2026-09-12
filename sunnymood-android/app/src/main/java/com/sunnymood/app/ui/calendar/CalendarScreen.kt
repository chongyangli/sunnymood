package com.sunnymood.app.ui.calendar

import android.content.Intent
import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import com.sunnymood.app.ui.record.RecordPanelActivity
import com.sunnymood.app.util.MoodSpec
import com.sunnymood.app.util.formatTimeOfDay
import com.sunnymood.app.util.monthEndMillis
import com.sunnymood.app.util.monthStartMillis
import com.sunnymood.app.util.monthTitle
import com.sunnymood.app.util.shortDateLabel
import com.sunnymood.app.widget.WidgetContract
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 日历 ViewModel（M2）：
 * 月份切换 → observeRange Flow 驱动月历着色；点某天展开当日明细，明细条目进入编辑面板。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val sunny = app as SunnyMoodApp

    /** 当前查看的月份 */
    val month = MutableStateFlow(YearMonth.now())

    /** 展开当日明细的日期（再点一次收起） */
    val expandedDay = MutableStateFlow<LocalDate?>(null)

    val monthRecords: StateFlow<List<MoodRecord>> = month
        .flatMapLatest { ym ->
            sunny.repository.observeRange(
                monthStartMillis(ym.year, ym.monthValue),
                monthEndMillis(ym.year, ym.monthValue)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun prevMonth() {
        month.value = month.value.minusMonths(1)
        expandedDay.value = null
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
        expandedDay.value = null
    }

    fun toggleDay(date: LocalDate) {
        expandedDay.value = if (expandedDay.value == date) null else date
    }
}

/**
 * 日历视图（M2，开发文档 §4.4）：
 * 月历网格按当日均分 7 档色阶着色；点某天展开当日记录明细，明细可点进编辑面板。
 */
@Composable
fun CalendarScreen(vm: CalendarViewModel = viewModel()) {
    val context = LocalContext.current
    val month by vm.month.collectAsState()
    val records by vm.monthRecords.collectAsState()
    val expanded by vm.expandedDay.collectAsState()

    val byDay = records.groupBy {
        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val expandedRecords = expanded?.let { byDay[it] }.orEmpty().sortedBy { it.timestamp }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 月份切换器（‹ 2026年9月 ›）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.prevMonth() }) {
                Text(
                    text = "‹",
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = monthTitle(month.year, month.monthValue),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.nextMonth() }) {
                Text(
                    text = "›",
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // 星期表头（周一起始）
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { w ->
                Text(
                    text = w,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // 月历网格（周一起始，前导空位补 null）
        val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
        val days: List<LocalDate?> =
            List<LocalDate?>(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    if (cell != null) {
                        DayCell(
                            date = cell,
                            avg = byDay[cell]?.map { it.moodLevel }?.average(),
                            count = byDay[cell]?.size ?: 0,
                            isToday = cell == LocalDate.now(),
                            selected = expanded == cell,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.toggleDay(cell) }
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                // 末行不满 7 格补空
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }

        // 空月份提示
        if (records.isEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.calendar_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            )
        }

        // 当日明细（点某天展开）
        if (expanded != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = shortDateLabel(expanded!!),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            if (expandedRecords.isEmpty()) {
                Text(
                    text = stringResource(R.string.calendar_day_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                expandedRecords.forEach { record ->
                    DayRecordItem(record) {
                        context.startActivity(
                            Intent(context, RecordPanelActivity::class.java)
                                .putExtra(WidgetContract.EXTRA_RECORD_ID, record.id)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 日历格子：按当日均分取最近档位着色（7 档色阶，色弱可结合数字辨认，§4.4） */
@Composable
private fun DayCell(
    date: LocalDate,
    avg: Double?,
    count: Int,
    isToday: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (avg != null) {
        MoodSpec.color(Math.round(avg).toInt().coerceIn(1, 7)).copy(alpha = 0.30f)
    } else {
        Color.Transparent
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .background(bg, RoundedCornerShape(12.dp))
            .then(
                when {
                    selected -> Modifier.border(
                        1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)
                    )
                    isToday -> Modifier.border(
                        1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)
                    )
                    else -> Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (avg != null) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (count > 1) {
                Text(
                    text = "$count",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 当日明细条目（与首页时间线同风格，可点进编辑） */
@Composable
private fun DayRecordItem(record: MoodRecord, onClick: () -> Unit) {
    val spec = MoodSpec.spec(record.moodLevel)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatTimeOfDay(record.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(14.dp))
        Image(
            painter = painterResource(spec.iconRes),
            contentDescription = stringResource(R.string.cd_mood_icon, spec.name, spec.level),
            modifier = Modifier.size(30.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = spec.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = spec.color
            )
            val feelings = MoodSpec.decodeFeelings(record.bodyFeelings)
            if (feelings.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = feelings.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
