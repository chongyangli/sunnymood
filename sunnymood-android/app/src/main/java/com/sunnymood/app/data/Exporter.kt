package com.sunnymood.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sunnymood.app.data.db.MoodRecord
import com.sunnymood.app.diagnostic.Diagnostics
import com.sunnymood.app.util.MoodSpec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * 数据导出（开发文档 §2.2 P0 / §3.8 可携权）：
 * CSV（Excel 打开中文无乱码：UTF-8 BOM）/ JSON，走系统分享（FileProvider，零存储权限）。
 */
object Exporter {

    private val isoFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

    private fun iso(ts: Long): String = isoFormatter.format(Instant.ofEpochMilli(ts))

    /** CSV（含 UTF-8 BOM，Excel 中文不乱码；导出有效记录，不含墓碑） */
    fun buildCsv(records: List<MoodRecord>): String {
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM
        sb.append("id,记录时间,心情等级,心情,身体感觉,备注,最后修改,已删除\r\n")
        records.forEach { r ->
            val feelings = MoodSpec.decodeFeelings(r.bodyFeelings).joinToString("、")
            sb.append(csvCell(r.id)).append(',')
                .append(csvCell(iso(r.timestamp))).append(',')
                .append(r.moodLevel).append(',')
                .append(csvCell(MoodSpec.name(r.moodLevel))).append(',')
                .append(csvCell(feelings)).append(',')
                .append(csvCell(r.note ?: "")).append(',')
                .append(csvCell(iso(r.updatedAt))).append(',')
                .append(if (r.deleted) "是" else "否")
                .append("\r\n")
        }
        return sb.toString()
    }

    /** JSON（schema v1，字段向后兼容预留） */
    fun buildJson(records: List<MoodRecord>): String {
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
            o.put("bodyFeelings", if (r.bodyFeelings.isNullOrBlank()) JSONObject.NULL else r.bodyFeelings)
            o.put("note", if (r.note.isNullOrBlank()) JSONObject.NULL else r.note)
            o.put("tags", if (r.tags.isNullOrBlank()) JSONObject.NULL else r.tags)
            o.put("updatedAt", r.updatedAt)
            o.put("deleted", r.deleted)
            arr.put(o)
        }
        root.put("records", arr)
        return root.toString(2)
    }

    private fun csvCell(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n')) {
            "\"${v.replace("\"", "\"\"")}\""
        } else {
            v
        }

    /** 写入缓存并调起系统分享；返回是否成功 */
    fun share(context: Context, format: ExportFormat, records: List<MoodRecord>): Boolean {
        val content = when (format) {
            ExportFormat.CSV -> buildCsv(records)
            ExportFormat.JSON -> buildJson(records)
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "sunnymood_export_${stamp}.${format.ext}"

        return runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, fileName)
            file.writeText(content, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = format.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, "导出${format.label}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Diagnostics.log("export_data_${format.ext}")
            true
        }.getOrDefault(false)
    }
}

enum class ExportFormat(val ext: String, val mime: String, val label: String) {
    CSV("csv", "text/csv", "CSV"),
    JSON("json", "application/json", "JSON")
}
