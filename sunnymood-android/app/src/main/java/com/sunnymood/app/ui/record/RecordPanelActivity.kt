package com.sunnymood.app.ui.record

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.sunnymood.app.R
import com.sunnymood.app.SunnyMoodApp
import com.sunnymood.app.data.db.MoodRecord
import com.sunnymood.app.ui.theme.SunnyMoodTheme
import com.sunnymood.app.util.Haptics
import com.sunnymood.app.util.MoodSpec
import com.sunnymood.app.util.formatTimeOfDay
import com.sunnymood.app.util.shortDateLabel
import com.sunnymood.app.widget.WidgetContract
import com.sunnymood.app.widget.WidgetUpdater
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * 记录面板（开发文档 §3.5 / §4.3）——记录 / 编辑双形态复用同一面板：
 *
 * 【记录模式】Intent 只带 EXTRA_MOOD_LEVEL：
 * 1. onCreate 立即写库——记录在面板弹出的瞬间已完成 ✅
 * 2. 震动反馈 + 大图标「已记录」确认
 * 3. 身体感觉多选（可选追加，不阻塞）：勾选后点「完成」保存；未勾选直接关闭也不影响记录
 * 4. 点外部 / 下拉关闭
 *
 * 【编辑模式】Intent 带 EXTRA_RECORD_ID（M2，从时间线 / 日历进入）：
 * 预填心情等级与身体感觉，可改等级、改感觉、删除（二次确认）；划走 / 点外部关闭不改动。
 */
class RecordPanelActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as SunnyMoodApp
        val editId = intent?.getStringExtra(WidgetContract.EXTRA_RECORD_ID)

        if (editId == null) {
            // ── 记录模式 ─────────────────────────────────────────────
            val level = intent?.getIntExtra(WidgetContract.EXTRA_MOOD_LEVEL, 4) ?: 4

            // ① 记录先落库（应用级作用域：面板无论如何关闭，写库不取消）
            val recordId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            app.appScope.launch {
                runCatching {
                    app.repository.insertDirect(recordId, level, now)
                    WidgetUpdater.refreshAll(this@RecordPanelActivity)
                }
            }

            // ② 触觉反馈
            Haptics.tick(this)

            // ③ 面板 UI
            setContent {
                SunnyMoodTheme {
                    RecordPanelContent(
                        level = level,
                        onDone = { selected ->
                            app.appScope.launch {
                                runCatching {
                                    app.repository.attachBodyFeelings(recordId, selected)
                                }
                            }
                            finish()
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        } else {
            // ── 编辑模式 ─────────────────────────────────────────────
            lifecycleScope.launch {
                val record = runCatching { app.repository.getById(editId) }.getOrNull()
                if (record == null || record.deleted) {
                    finish()
                    return@launch
                }
                setContent {
                    SunnyMoodTheme {
                        EditPanelContent(
                            record = record,
                            onSave = { level, feelings ->
                                app.appScope.launch {
                                    runCatching {
                                        app.repository.updateRecord(editId, level, feelings)
                                        WidgetUpdater.refreshAll(this@RecordPanelActivity)
                                    }
                                }
                                Toast.makeText(
                                    this@RecordPanelActivity,
                                    R.string.panel_saved,
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            },
                            onDelete = {
                                app.appScope.launch {
                                    runCatching {
                                        app.repository.deleteRecord(editId)
                                        WidgetUpdater.refreshAll(this@RecordPanelActivity)
                                    }
                                }
                                Toast.makeText(
                                    this@RecordPanelActivity,
                                    R.string.panel_deleted,
                                    Toast.LENGTH_SHORT
                                ).show()
                                finish()
                            },
                            onDismiss = { finish() }
                        )
                    }
                }
            }
        }
    }
}

/** 点外部 / 下拉关闭的公共容器（三层摩擦控制之一：随时可走，§3.5） */
@Composable
private fun PanelDismissBox(onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    total += dragAmount
                    if (total > 80.dp.toPx()) onDismiss()
                }
            },
        content = content
    )
}

/** 身体感觉多选词表（记录 / 编辑共用） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeelingsChips(selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MoodSpec.BODY_FEELINGS.forEach { label ->
            FilterChip(
                selected = label in selected,
                onClick = { onToggle(label) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun RecordPanelContent(
    level: Int,
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val spec = MoodSpec.spec(level)
    var selected by remember { mutableStateOf(setOf<String>()) }

    // 心情图标弹跳确认（spring 弹性物理动效，200~300ms，不阻塞主路径，§4.7）
    var played by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (played) 1f else 0.35f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "mood_pop"
    )
    LaunchedEffect(Unit) { played = true }

    PanelDismissBox(onDismiss) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* 消费卡片内点击，避免误关闭 */ },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.panel_recorded),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(10.dp))
                Image(
                    painter = painterResource(spec.iconRes),
                    contentDescription = stringResource(
                        R.string.cd_mood_icon, spec.name, spec.level
                    ),
                    modifier = Modifier
                        .size(76.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = spec.color
                )

                Spacer(Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.panel_body_feelings_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                FeelingsChips(selected) {
                    selected = if (it in selected) selected - it else selected + it
                }

                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { onDone(selected.toList()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(text = stringResource(R.string.panel_done), fontSize = 16.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "不选也可以，划走或点空白处关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * 编辑面板（M2，开发文档 §4.3 编辑形态）：
 * 7 档等级选择器 + 身体感觉预填 + 保存 + 红色删除（二次确认）；关闭不保存。
 */
@Composable
fun EditPanelContent(
    record: MoodRecord,
    onSave: (level: Int, feelings: List<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var level by remember { mutableStateOf(record.moodLevel) }
    var selected by remember {
        mutableStateOf(MoodSpec.decodeFeelings(record.bodyFeelings).toSet())
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val spec = MoodSpec.spec(level)
    val recordDate = Instant.ofEpochMilli(record.timestamp)
        .atZone(ZoneId.systemDefault()).toLocalDate()

    PanelDismissBox(onDismiss) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* 消费卡片内点击，避免误关闭 */ },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.panel_edit_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${shortDateLabel(recordDate)} ${formatTimeOfDay(record.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(18.dp))

                // 7 档心情等级选择器（选中放大高亮）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MoodSpec.levels.forEach { s ->
                        val isSel = s.level == level
                        Image(
                            painter = painterResource(s.iconRes),
                            contentDescription = stringResource(
                                R.string.cd_mood_icon, s.name, s.level
                            ),
                            alpha = if (isSel) 1f else 0.35f,
                            modifier = Modifier
                                .size(if (isSel) 44.dp else 32.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { level = s.level }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = spec.color
                )

                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.panel_edit_feelings_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                FeelingsChips(selected) {
                    selected = if (it in selected) selected - it else selected + it
                }

                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = { onSave(level, selected.toList()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(text = stringResource(R.string.panel_save), fontSize = 16.sp)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(R.string.panel_delete), fontSize = 14.sp)
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.panel_delete_confirm_title)) },
            text = { Text(stringResource(R.string.panel_delete_confirm_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(
                        text = stringResource(R.string.dialog_confirm_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}
