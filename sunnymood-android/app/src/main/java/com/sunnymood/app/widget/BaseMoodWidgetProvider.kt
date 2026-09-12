package com.sunnymood.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Widget / 面板 / 广播之间的 Intent 契约 */
object WidgetContract {
    const val EXTRA_MOOD_LEVEL = "com.sunnymood.app.extra.MOOD_LEVEL"
    const val EXTRA_RECORD_ID = "com.sunnymood.app.extra.RECORD_ID" // 编辑模式（M2）
    const val RC_MOOD = 1000 // PendingIntent requestCode 基数（+level 区分 7 档）
}

/**
 * 小组件基类（RemoteViews + AppWidgetProvider，开发文档 §3.2 / §3.5）。
 *
 * - 事件驱动刷新（updatePeriodMillis=0）：记录后 / 启动器回调时主动 updateAppWidget
 * - 点击绑定两种模式：面板模式（默认，PendingIntent.getActivity 直启）/
 *   纯点击模式（getBroadcast 直接落库）——Android 12+ 一律 FLAG_IMMUTABLE
 */
abstract class BaseMoodWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val work = goAsync() // 数据库查询是挂起操作，需要持住广播
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    runCatching {
                        appWidgetManager.updateAppWidget(
                            id, WidgetUpdater.buildViews(context, id)
                        )
                    }
                }
            } finally {
                work.finish()
            }
        }
    }
}

/** 4x1 紧凑版（两行布局） */
class MoodWidgetProvider4x1 : BaseMoodWidgetProvider()

/** 4x2 大图标版（主推，7 图标触控更从容） */
class MoodWidgetProvider4x2 : BaseMoodWidgetProvider()
