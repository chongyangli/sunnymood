package com.sunnymood.app.data

import com.sunnymood.app.data.db.MoodRecord
import com.sunnymood.app.data.db.MoodRecordDao
import com.sunnymood.app.diagnostic.Diagnostics
import com.sunnymood.app.sync.SyncScheduler
import com.sunnymood.app.util.MoodSpec
import com.sunnymood.app.util.todayStartMillis
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 记录仓库：唯一写入口。
 * 「记录先落库、追加全部可选」——record() 在面板弹出的瞬间即完成记录（§3.5）。
 * 每次写操作后通知同步调度（30s 去抖；未开启云同步时为 no-op）。
 */
class MoodRepository(private val dao: MoodRecordDao) {

    /** 立即落库，返回记录 id（记录此刻已完成 ✅） */
    suspend fun record(moodLevel: Int, timestamp: Long = System.currentTimeMillis()): String {
        val id = UUID.randomUUID().toString()
        insertDirect(id, moodLevel, timestamp)
        return id
    }

    /** 直插（面板模式：id 在 Activity 侧预生成，UI 与落库解耦） */
    suspend fun insertDirect(id: String, moodLevel: Int, timestamp: Long) {
        dao.insert(
            MoodRecord(
                id = id,
                moodLevel = moodLevel,
                timestamp = timestamp,
                updatedAt = timestamp
            )
        )
        // 诊断日志只记操作类型，不记心情内容（§3.9）
        Diagnostics.log("record")
        SyncScheduler.notifyChanged()
    }

    /** 面板关闭时可选追加身体感觉（未勾选不调用，记录不受影响） */
    suspend fun attachBodyFeelings(recordId: String, feelings: List<String>) {
        val rec = dao.getById(recordId) ?: return
        dao.update(
            rec.copy(
                bodyFeelings = MoodSpec.encodeFeelings(feelings),
                updatedAt = System.currentTimeMillis()
            )
        )
        Diagnostics.log("attach_feelings")
        SyncScheduler.notifyChanged()
    }

    /** 今日时间线（Flow，App 内 UI） */
    fun observeToday(): Flow<List<MoodRecord>> = dao.observeToday(todayStartMillis())

    /** 区间查询（Flow，日历 / 趋势 / 报告；过滤墓碑） */
    fun observeRange(from: Long, to: Long): Flow<List<MoodRecord>> = dao.queryRange(from, to)

    /** 区间一次性查询（近 7 日趋势等） */
    suspend fun getRange(from: Long, to: Long): List<MoodRecord> = dao.getRange(from, to)

    /** 按 id 取单条（编辑面板预填） */
    suspend fun getById(recordId: String): MoodRecord? = dao.getById(recordId)

    /** 编辑：改心情等级与身体感觉（不改时间戳，§3.4；刷新 updatedAt 供同步 LWW，§3.10） */
    suspend fun updateRecord(recordId: String, moodLevel: Int, feelings: List<String>) {
        val rec = dao.getById(recordId) ?: return
        dao.update(
            rec.copy(
                moodLevel = moodLevel,
                bodyFeelings = MoodSpec.encodeFeelings(feelings),
                updatedAt = System.currentTimeMillis()
            )
        )
        Diagnostics.log("edit")
        SyncScheduler.notifyChanged()
    }

    /** 删除：软删除墓碑，供同步传播（§3.4 / §3.10） */
    suspend fun deleteRecord(recordId: String) {
        dao.deleteById(recordId, System.currentTimeMillis())
        Diagnostics.log("delete")
        SyncScheduler.notifyChanged()
    }

    /** 全量（含墓碑，云同步快照 / 合并用） */
    suspend fun getAllIncludingTombstones(): List<MoodRecord> =
        dao.getAllIncludingTombstones()

    /** 同步合并批量写入 */
    suspend fun upsertAll(records: List<MoodRecord>) {
        if (records.isNotEmpty()) dao.upsertAll(records)
    }

    /** 清空全部数据（物理删除，含墓碑） */
    suspend fun clearAll() {
        dao.clearAll()
        Diagnostics.log("clear_all")
    }

    /** 今日数据（一次性，Widget 刷新用） */
    suspend fun getToday(): List<MoodRecord> = dao.getToday(todayStartMillis())
}
