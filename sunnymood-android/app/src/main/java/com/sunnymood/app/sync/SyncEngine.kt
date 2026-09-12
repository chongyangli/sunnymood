package com.sunnymood.app.sync

import android.content.Context
import com.sunnymood.app.data.db.MoodRecord
import com.sunnymood.app.diagnostic.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 同步引擎（开发文档 §3.10）：
 * 上传：Room 全量 → JSON 快照 → AES-256-GCM 加密 → PUT 用户网盘 /sunnymood/backup.json.enc
 * 下载：GET → 解密 → 与本地合并（UUID 对齐 · LWW · 墓碑）→ 写回 Room → UI 自动刷新
 *
 * 合并规则：按 UUID 对齐；双方都有取 updatedAt 新者（LWW）；仅一方有则采纳；
 * 删除经 deleted 墓碑传播（本地删除后墓碑 updatedAt 更新，会覆盖远端旧记录）。
 */
object SyncEngine {

    const val REMOTE_DIR = "sunnymood"
    const val REMOTE_FILE = "backup.json.enc"

    /** 全量同步：下载合并（若有远端）→ 上传合并结果。返回 Result 供 UI 展示原因 */
    suspend fun syncNow(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as com.sunnymood.app.SunnyMoodApp
        val cfg = SyncSettingsStore.get(app)

        val result = runCatching {
            if (!cfg.isConfigured()) throw SyncException("尚未绑定网盘")
            if (!cfg.hasPassphrase()) throw SyncException("尚未设置同步口令")

            val client = WebDavClient(cfg.server, cfg.account, cfg.appPassword)
            val local = app.repository.getAllIncludingTombstones()

            // 下载远端（首次同步时可能不存在）
            val remoteBytes = client.get(REMOTE_DIR, REMOTE_FILE)
            val merged: List<MoodRecord> = if (remoteBytes != null) {
                val plain = try {
                    SyncCrypto.decrypt(remoteBytes, cfg.passphrase)
                } catch (e: Exception) {
                    throw SyncException("解密失败：同步口令可能不正确")
                }
                val remote = parseSnapshot(String(plain, Charsets.UTF_8))
                mergeAndApply(app, local, remote)
            } else {
                local
            }

            // 上传合并后的完整快照（双方收敛为同一状态）
            client.ensureFolder(REMOTE_DIR)
            val payload = SyncCrypto.encrypt(snapshotOf(merged).toByteArray(Charsets.UTF_8), cfg.passphrase)
            client.put(REMOTE_DIR, REMOTE_FILE, payload)

            Diagnostics.log("sync_ok")
        }

        result.fold(
            onSuccess = {
                cfg.setStatus(SyncStatus(System.currentTimeMillis(), ok = true, error = null))
            },
            onFailure = { e ->
                Diagnostics.log("sync_fail")
                cfg.setStatus(
                    SyncStatus(
                        System.currentTimeMillis(),
                        ok = false,
                        error = (e as? SyncException)?.message ?: "网络异常，请稍后重试"
                    )
                )
            }
        )
        result
    }

    /** 删除云端备份（清空全部数据联动） */
    suspend fun deleteRemote(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as com.sunnymood.app.SunnyMoodApp
        val cfg = SyncSettingsStore.get(app)
        runCatching {
            if (!cfg.isConfigured()) throw SyncException("尚未绑定网盘")
            val client = WebDavClient(cfg.server, cfg.account, cfg.appPassword)
            client.delete(REMOTE_DIR, REMOTE_FILE)
            Diagnostics.log("sync_delete_remote")
        }
    }

    /**
     * 合并并写回本地：远端记录按 LWW 采纳进本地库，返回合并后的全集。
     * 远端墓碑同样参与合并（本地已删除的记录若远端 updatedAt 更旧则保持墓碑）。
     */
    private suspend fun mergeAndApply(
        app: com.sunnymood.app.SunnyMoodApp,
        local: List<MoodRecord>,
        remote: List<MoodRecord>
    ): List<MoodRecord> {
        val localById = local.associateBy { it.id }
        val toApply = mutableListOf<MoodRecord>()
        remote.forEach { r ->
            val l = localById[r.id]
            if (l == null || r.updatedAt > l.updatedAt) {
                toApply.add(r)
            }
        }
        if (toApply.isNotEmpty()) {
            app.repository.upsertAll(toApply)
        }
        return (localById + toApply.associateBy { it.id }).values.sortedBy { it.timestamp }
    }

    /** 快照序列化（schema v1，字段演进向后兼容） */
    private fun snapshotOf(records: List<MoodRecord>): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("app", "sunnymood")
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        records.forEach { r ->
            val o = JSONObject()
            o.put("id", r.id)
            o.put("moodLevel", r.moodLevel)
            o.put("timestamp", r.timestamp)
            o.put("bodyFeelings", r.bodyFeelings ?: JSONObject.NULL)
            o.put("note", r.note ?: JSONObject.NULL)
            o.put("tags", r.tags ?: JSONObject.NULL)
            o.put("updatedAt", r.updatedAt)
            o.put("deleted", r.deleted)
            arr.put(o)
        }
        root.put("records", arr)
        return root.toString()
    }

    /** 快照解析（容错：单条损坏跳过，不拖垮整体恢复） */
    private fun parseSnapshot(json: String): List<MoodRecord> {
        val arr = JSONObject(json).optJSONArray("records") ?: return emptyList()
        val out = mutableListOf<MoodRecord>()
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                out.add(
                    MoodRecord(
                        id = o.getString("id"),
                        moodLevel = o.getInt("moodLevel"),
                        timestamp = o.getLong("timestamp"),
                        bodyFeelings = o.optString("bodyFeelings").takeIf { it.isNotBlank() },
                        note = o.optString("note").takeIf { it.isNotBlank() },
                        tags = o.optString("tags").takeIf { it.isNotBlank() },
                        updatedAt = o.optLong("updatedAt", 0),
                        deleted = o.optBoolean("deleted", false)
                    )
                )
            }
        }
        return out
    }
}
