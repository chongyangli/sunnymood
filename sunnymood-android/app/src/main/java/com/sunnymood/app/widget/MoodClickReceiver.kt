package com.sunnymood.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sunnymood.app.SunnyMoodApp
import com.sunnymood.app.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 纯点击模式（设置开关，默认关闭，开发文档 §3.5）：
 * 点击 Widget 图标 → 广播直接落库 → 震动 + 刷新小组件，全程不弹任何界面。
 */
class MoodClickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val level = intent.getIntExtra(WidgetContract.EXTRA_MOOD_LEVEL, 4)
        val app = context.applicationContext as SunnyMoodApp

        val work = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                runCatching {
                    app.repository.record(level)
                    Haptics.tick(context)
                    WidgetUpdater.refreshAll(context)
                }
            } finally {
                work.finish()
            }
        }
    }
}
