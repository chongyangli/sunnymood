package com.sunnymood.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.sunnymood.app.R
import com.sunnymood.app.SunnyMoodApp
import com.sunnymood.app.ui.record.RecordPanelActivity
import com.sunnymood.app.util.todayLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 小组件视图构建与刷新（事件驱动）：
 * 记录落库后 / 模式切换后 / App 回前台时调用 refreshAll。
 */
object WidgetUpdater {

    private val cellIds = intArrayOf(
        R.id.cell_mood_1, R.id.cell_mood_2, R.id.cell_mood_3, R.id.cell_mood_4,
        R.id.cell_mood_5, R.id.cell_mood_6, R.id.cell_mood_7
    )

    private val hlIds = intArrayOf(
        R.id.hl_mood_1, R.id.hl_mood_2, R.id.hl_mood_3, R.id.hl_mood_4,
        R.id.hl_mood_5, R.id.hl_mood_6, R.id.hl_mood_7
    )

    /** 刷新全部（4x1 + 4x2）小组件 */
    fun refreshAll(context: Context) {
        refresh(context, MoodWidgetProvider4x1::class.java)
        refresh(context, MoodWidgetProvider4x2::class.java)
    }

    fun refresh(context: Context, providerClass: Class<*>) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, providerClass))
                ids.forEach { id ->
                    manager.updateAppWidget(id, buildViews(app, id))
                }
            }
        }
    }

    /** 构建 RemoteViews：状态文本 + 最新心情高亮 + 7 档点击绑定（挂起：查库与设置） */
    suspend fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
        val app = context.applicationContext as SunnyMoodApp
        val manager = AppWidgetManager.getInstance(context)

        val layout = if (manager.getAppWidgetInfo(appWidgetId)?.initialLayout == R.layout.widget_4x1) {
            R.layout.widget_4x1
        } else {
            R.layout.widget_4x2
        }

        val tapOnly = app.settings.tapOnlyMode.first()
        val today = app.repository.getToday()
        val latest = today.firstOrNull()?.moodLevel
        val count = today.size
        val countText = if (count > 0) "已记 $count 条" else "未记录"

        val views = RemoteViews(context.packageName, layout)
        if (layout == R.layout.widget_4x1) {
            views.setTextViewText(R.id.tv_date, "${todayLabel()} · $countText")
        } else {
            views.setTextViewText(R.id.tv_date, todayLabel())
            views.setTextViewText(R.id.tv_count, countText)
        }

        for (level in 1..7) {
            views.setViewVisibility(
                hlIds[level - 1],
                if (latest == level) View.VISIBLE else View.INVISIBLE
            )
            views.setOnClickPendingIntent(
                cellIds[level - 1],
                clickPendingIntent(context, level, tapOnly)
            )
        }
        return views
    }

    /**
     * 点击意图：
     * - 面板模式（默认）：getActivity 直启 RecordPanelActivity（Widget 点击豁免后台启动限制，§3.5）
     * - 纯点击模式：getBroadcast → MoodClickReceiver 直接落库
     * - Android 12+ 必须 FLAG_IMMUTABLE
     */
    private fun clickPendingIntent(context: Context, level: Int, tapOnly: Boolean): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (tapOnly) {
            val intent = Intent(context, MoodClickReceiver::class.java)
                .putExtra(WidgetContract.EXTRA_MOOD_LEVEL, level)
            PendingIntent.getBroadcast(context, WidgetContract.RC_MOOD + level, intent, flags)
        } else {
            val intent = Intent(context, RecordPanelActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(WidgetContract.EXTRA_MOOD_LEVEL, level)
            PendingIntent.getActivity(context, WidgetContract.RC_MOOD + level, intent, flags)
        }
    }
}
