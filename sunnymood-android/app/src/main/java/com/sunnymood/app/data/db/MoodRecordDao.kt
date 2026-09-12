package com.sunnymood.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodRecordDao {

    @Insert
    suspend fun insert(record: MoodRecord)

    /** 同步合并批量写入（UUID 主键冲突时覆盖） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<MoodRecord>)

    @Update
    suspend fun update(record: MoodRecord)

    @Query("SELECT * FROM mood_records WHERE id = :id")
    suspend fun getById(id: String): MoodRecord?

    /** 软删除：置墓碑供同步传播（开发文档 §3.4 / §3.10） */
    @Query("UPDATE mood_records SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun deleteById(id: String, now: Long)

    /** 全量（含墓碑，云同步快照 / 合并用） */
    @Query("SELECT * FROM mood_records ORDER BY timestamp")
    suspend fun getAllIncludingTombstones(): List<MoodRecord>

    /** 清空全部数据（物理删除，含墓碑；§3.8 删除权） */
    @Query("DELETE FROM mood_records")
    suspend fun clearAll()

    /** 报告 / 日历（过滤墓碑） */
    @Query(
        "SELECT * FROM mood_records WHERE deleted = 0 AND timestamp BETWEEN :from AND :to " +
            "ORDER BY timestamp"
    )
    fun queryRange(from: Long, to: Long): Flow<List<MoodRecord>>

    /** 今日时间线 / Widget 状态（一次性查询，Widget 刷新用） */
    @Query(
        "SELECT * FROM mood_records WHERE deleted = 0 AND timestamp >= :todayStart " +
            "ORDER BY timestamp DESC"
    )
    suspend fun getToday(todayStart: Long): List<MoodRecord>

    /** 今日时间线（Flow 驱动 UI 自动刷新） */
    @Query(
        "SELECT * FROM mood_records WHERE deleted = 0 AND timestamp >= :todayStart " +
            "ORDER BY timestamp DESC"
    )
    fun observeToday(todayStart: Long): Flow<List<MoodRecord>>

    /** 范围一次性查询（近 7 日趋势等，过滤墓碑） */
    @Query(
        "SELECT * FROM mood_records WHERE deleted = 0 AND timestamp BETWEEN :from AND :to " +
            "ORDER BY timestamp"
    )
    suspend fun getRange(from: Long, to: Long): List<MoodRecord>
}
