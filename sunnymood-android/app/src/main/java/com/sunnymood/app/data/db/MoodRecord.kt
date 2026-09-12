package com.sunnymood.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 心情记录（开发文档 §3.4）
 *
 * - 主键 UUID（v1.6 起）：Long 自增 id 在多设备同步时必然撞号，UUID 天然不冲突（§3.10）
 * - updatedAt：云同步 LWW 合并依据（创建时 = timestamp）
 * - deleted：软删除墓碑，让「删除」可随同步传播；所有查询一律过滤（§3.10）
 */
@Entity(tableName = "mood_records", indices = [Index("timestamp")])
data class MoodRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val moodLevel: Int,                // 1..7（1=非常差，7=非常好）
    val timestamp: Long,               // 毫秒时间戳
    val bodyFeelings: String? = null,  // 身体感觉，JSON 数组，如 ["疲惫","困倦"]；P0 启用
    val note: String? = null,          // 备注（P1 启用，表结构先行预留，免迁移）
    val tags: String? = null,          // 标签（P1 启用）
    val updatedAt: Long = 0,           // 最后修改时间；云同步合并依据（LWW）
    val deleted: Boolean = false       // 软删除墓碑：同步场景下删除需可传播（§3.10）
)
