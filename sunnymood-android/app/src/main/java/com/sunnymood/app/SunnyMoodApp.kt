package com.sunnymood.app

import android.app.Application
import com.sunnymood.app.data.MoodRepository
import com.sunnymood.app.data.SettingsStore
import com.sunnymood.app.data.db.AppDatabase
import com.sunnymood.app.diagnostic.Diagnostics
import com.sunnymood.app.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 心晴 SunnyMood Application
 *
 * appScope：应用级协程作用域，用于「记录先落库、面板可关闭」等不随 UI 销毁而取消的操作
 */
class SunnyMoodApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: MoodRepository by lazy { MoodRepository(database.moodRecordDao()) }
    val settings: SettingsStore by lazy { SettingsStore.get(this) }

    override fun onCreate() {
        super.onCreate()
        Diagnostics.init(this)
        Diagnostics.log("app_start")
        // 云同步：仅当用户已绑定网盘并设置口令时才联网（默认关闭 → 零网络请求，§3.10）
        SyncScheduler.init(this)
        SyncScheduler.syncOnStart()
    }
}
