package com.sunnymood.app.ui.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
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
import com.sunnymood.app.data.ExportFormat
import com.sunnymood.app.data.Exporter
import com.sunnymood.app.data.db.MoodRecord
import com.sunnymood.app.diagnostic.Diagnostics
import com.sunnymood.app.sync.SyncEngine
import com.sunnymood.app.sync.SyncSettingsStore
import com.sunnymood.app.ui.policy.PrivacyPolicyDialog
import com.sunnymood.app.ui.record.RecordPanelActivity
import com.sunnymood.app.ui.settings.SyncSettingsSheet
import com.sunnymood.app.util.MoodSpec
import com.sunnymood.app.util.dayEndMillis
import com.sunnymood.app.util.dayStartMillis
import com.sunnymood.app.util.formatTimeOfDay
import com.sunnymood.app.util.fullDateLabel
import com.sunnymood.app.util.weekdayShort
import com.sunnymood.app.widget.MoodWidgetProvider4x1
import com.sunnymood.app.widget.MoodWidgetProvider4x2
import com.sunnymood.app.widget.WidgetContract
import com.sunnymood.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
/**
 * 首页 ViewModel：今日时间线 + 近 7 日趋势 + 记录模式开关。
 * observeToday 由 Room Flow 驱动，记录落库后列表自动更新。
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val sunny = app as SunnyMoodApp

    val today: StateFlow<List<MoodRecord>> = sunny.repository.observeToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 近 7 日记录（迷你趋势用；过滤墓碑） */
    val trend: StateFlow<List<MoodRecord>> = sunny.repository.observeRange(
        from = dayStartMillis(LocalDate.now().minusDays(6)),
        to = dayEndMillis(LocalDate.now())
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tapOnly: StateFlow<Boolean> = sunny.settings.tapOnlyMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 切换记录模式后立刻重绑小组件点击意图（面板 / 广播直录） */
    fun setTapOnly(value: Boolean) {
        viewModelScope.launch {
            runCatching {
                sunny.settings.setTapOnlyMode(value)
                WidgetUpdater.refreshAll(getApplication())
                Diagnostics.log("settings_tap_only")
            }
        }
    }
}

/**
 * 首页（M2 范围，开发文档 §4.4）：
 * - 7 档心情直点（与小组件同一链路：弹出记录面板）
 * - 近 7 日迷你趋势（日均分着色）
 * - 今日时间线（时间 + 心情 + 身体感觉），点条目进编辑面板
 * - 设置底部弹层：纯点击模式开关 / 诊断日志导出
 */
@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val today by vm.today.collectAsState()
    val trend by vm.trend.collectAsState()
    val tapOnly by vm.tapOnly.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showSync by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }

    fun openRecordPanel(level: Int) {
        context.startActivity(
            Intent(context, RecordPanelActivity::class.java)
                .putExtra(WidgetContract.EXTRA_MOOD_LEVEL, level)
        )
    }

    fun openEditPanel(recordId: String) {
        context.startActivity(
            Intent(context, RecordPanelActivity::class.java)
                .putExtra(WidgetContract.EXTRA_RECORD_ID, recordId)
        )
    }

    // 未添加小组件时显示引导（§4.6，添加后自动消失）
    val widgetBound = remember {
        runCatching {
            val mgr = AppWidgetManager.getInstance(context)
            mgr.getAppWidgetIds(ComponentName(context, MoodWidgetProvider4x1::class.java))
                .isNotEmpty() ||
                mgr.getAppWidgetIds(ComponentName(context, MoodWidgetProvider4x2::class.java))
                    .isNotEmpty()
        }.getOrDefault(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showSettings = true }) {
                Image(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.cd_settings),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = fullDateLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // 7 档心情直点（唯一事实源 MoodSpec）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MoodSpec.levels.forEach { spec ->
                IconButton(onClick = { openRecordPanel(spec.level) }) {
                    Image(
                        painter = painterResource(spec.iconRes),
                        contentDescription = stringResource(
                            R.string.cd_record_mood, spec.name
                        ),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 小组件引导卡（未添加时显示，§4.6）
        if (!widgetBound) {
            WidgetGuideCard()
            Spacer(Modifier.height(20.dp))
        }

        // 近 7 日迷你趋势（M2）
        WeekTrend(trend)

        Spacer(Modifier.height(24.dp))

        // 今日记录标题 + 计数
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_timeline_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            if (today.isNotEmpty()) {
                Text(
                    text = "今日已记 ${today.size} 条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 时间线 / 空状态
        Box(modifier = Modifier.weight(1f)) {
            if (today.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(MoodSpec.spec(4).iconRes),
                        contentDescription = null,
                        alpha = 0.45f,
                        modifier = Modifier.size(46.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.home_today_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(today, key = { it.id }) { record ->
                        TimelineItem(record, onClick = { openEditPanel(record.id) })
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            tapOnly = tapOnly,
            onTapOnlyChange = { vm.setTapOnly(it) },
            onOpenSync = {
                showSettings = false
                showSync = true
            },
            onOpenPrivacy = {
                showSettings = false
                showPrivacy = true
            },
            onDismiss = { showSettings = false }
        )
    }

    if (showSync) {
        SyncSettingsSheet(onDismiss = { showSync = false })
    }

    if (showPrivacy) {
        PrivacyPolicyDialog(onClose = { showPrivacy = false })
    }
}

/**
 * 近 7 日迷你趋势（M2，开发文档 §4.4 首页）：
 * 每天一根柱，高度 = 当日均分比例，颜色 = 最近档位心情色；无记录日为灰色矮柱。
 */
@Composable
private fun WeekTrend(records: List<MoodRecord>) {
    val zone = ZoneId.systemDefault()
    Column {
        Text(
            text = stringResource(R.string.home_trend_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (6 downTo 0).forEach { offset ->
                val day = LocalDate.now().minusDays(offset.toLong())
                val dayAvg = records
                    .filter {
                        Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() == day
                    }
                    .map { it.moodLevel }
                    .let { if (it.isEmpty()) null else it.average() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val height = if (dayAvg == null) {
                            6.dp
                        } else {
                            (10.0 + 56.0 * (dayAvg / 7.0)).dp
                        }
                        val color = if (dayAvg == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                        } else {
                            MoodSpec.color(
                                Math.round(dayAvg).toInt().coerceIn(1, 7)
                            ).copy(alpha = 0.85f)
                        }
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .height(height)
                                .background(color, RoundedCornerShape(6.dp))
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = weekdayShort(day),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 时间线条目：时间 + 心情图标 + 心情名（+ 身体感觉），点击进编辑面板 */
@Composable
private fun TimelineItem(record: MoodRecord, onClick: () -> Unit) {
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

/**
 * 设置底部弹层（开发文档 §3.5 / §3.9 / §3.10 / §3.8）：
 * 记录模式 / 数据导出 CSV·JSON / 云同步入口 / 隐私政策入口 / 诊断日志导出 / 清空全部数据（二次确认）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    tapOnly: Boolean,
    onTapOnlyChange: (Boolean) -> Unit,
    onOpenSync: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SunnyMoodApp
    val scope = rememberCoroutineScope()
    val syncReady = remember {
        SyncSettingsStore.get(context).run { isConfigured() && hasPassphrase() }
    }

    var showClearConfirm by remember { mutableStateOf(false) }
    var alsoCloud by remember { mutableStateOf(false) }

    fun export(format: ExportFormat) {
        scope.launch {
            val records = app.repository.getAllIncludingTombstones().filter { !it.deleted }
            val ok = runCatching { Exporter.share(context, format, records) }.getOrDefault(false)
            Toast.makeText(
                context,
                if (ok) R.string.export_data_ok else R.string.export_data_fail,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.home_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(20.dp))

            // 纯点击模式开关
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_tap_only),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_tap_only_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = tapOnly, onCheckedChange = onTapOnlyChange)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // 数据导出：CSV / JSON（§3.8 可携权）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_export_data),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_export_data_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { export(ExportFormat.CSV) }) {
                    Text("CSV", color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { export(ExportFormat.JSON) }) {
                    Text("JSON", color = MaterialTheme.colorScheme.primary)
                }
            }

            SettingRow(
                title = stringResource(R.string.settings_sync),
                desc = stringResource(R.string.settings_sync_desc),
                onClick = onOpenSync
            )

            SettingRow(
                title = stringResource(R.string.settings_privacy),
                desc = stringResource(R.string.settings_privacy_desc),
                onClick = onOpenPrivacy
            )

            // 导出诊断日志（zip 走系统分享，零存储权限）
            SettingRow(
                title = stringResource(R.string.settings_export_diag),
                desc = stringResource(R.string.settings_export_diag_desc),
                onClick = {
                    val ok = Diagnostics.shareZip(context)
                    Toast.makeText(
                        context,
                        if (ok) R.string.diag_exported else R.string.diag_export_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            // 清空全部数据（§3.8 删除权，二次确认）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showClearConfirm = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_clear_all),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.settings_clear_all_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.settings_clear_all)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_clear_all_confirm))
                    if (syncReady) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = alsoCloud,
                                onCheckedChange = { alsoCloud = it }
                            )
                            Text(
                                text = stringResource(R.string.settings_clear_also_cloud),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    scope.launch {
                        runCatching {
                            app.repository.clearAll()
                            if (alsoCloud && syncReady) {
                                SyncEngine.deleteRemote(context)
                            }
                            WidgetUpdater.refreshAll(app)
                        }
                        alsoCloud = false
                    }
                }) {
                    Text(
                        text = stringResource(R.string.dialog_confirm_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

/** 通用设置行：标题 + 描述 + 点击 */
@Composable
private fun SettingRow(title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "›",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 小组件引导卡（§2.2 P0 / §4.6）：分步图文要点 */
@Composable
private fun WidgetGuideCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.guide_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        GuideStep(1, stringResource(R.string.guide_step_1))
        GuideStep(2, stringResource(R.string.guide_step_2))
        GuideStep(3, stringResource(R.string.guide_step_3))
    }
}

@Composable
private fun GuideStep(no: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = "$no. ",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
