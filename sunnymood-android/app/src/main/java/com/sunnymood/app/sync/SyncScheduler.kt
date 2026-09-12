package com.sunnymood.app.sync

import android.content.Context
import com.sunnymood.app.SunnyMoodApp
import com.sunnymood.app.diagnostic.Diagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 同步触发调度（开发文档 §3.10）：
 * - 自动模式：记录 / 编辑 / 删除后 30 秒去抖同步 + App 启动时同步
 * - 手动模式：仅设置页「立即同步」
 * 无后台常驻服务：一切在进程存续期间的应用级协程内完成。
 */
object SyncScheduler {

    private const val DEBOUNCE_MS = 30_000L

    @Volatile
    private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var debounceJob: Job? = null
    private var startJob: Job? = null

    fun init(app: SunnyMoodApp) {
        appContext = app.applicationContext
    }

    /** 数据发生变化（记录 / 编辑 / 删除）后由仓库调用；30s 去抖 */
    fun notifyChanged() {
        val ctx = appContext ?: return
        val cfg = SyncSettingsStore.get(ctx)
        if (!cfg.autoSync || !cfg.isConfigured() || !cfg.hasPassphrase()) return

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            runCatching { SyncEngine.syncNow(ctx) }
        }
    }

    /** App 启动时触发一次（恢复 / 多设备收敛的核心场景，§10 M5） */
    fun syncOnStart() {
        val ctx = appContext ?: return
        val cfg = SyncSettingsStore.get(ctx)
        if (!cfg.autoSync || !cfg.isConfigured() || !cfg.hasPassphrase()) return

        startJob?.cancel()
        startJob = scope.launch {
            delay(2_000) // 等待首屏渲染，避免抢启动
            runCatching { SyncEngine.syncNow(ctx) }
        }
    }
}
