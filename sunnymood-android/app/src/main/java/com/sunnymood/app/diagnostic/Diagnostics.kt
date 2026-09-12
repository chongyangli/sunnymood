package com.sunnymood.app.diagnostic

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 本地诊断日志（开发文档 §3.9，零网络下的排障方案）
 *
 * - 崩溃捕获：UncaughtExceptionHandler 写入本地后交还系统默认处理
 * - 记录范围：崩溃堆栈 + 关键操作（记录 / 编辑 / 删除 / 导出）的时间戳与操作类型；
 *   不记录心情数值与身体感觉内容，兼顾排障与隐私
 * - 存储：filesDir/logs/，按天滚动，保留 7 天
 * - 导出：zip 打包走系统分享（FileProvider，零存储权限）
 */
object Diagnostics {

    private const val TAG = "SunnyMood"
    private const val DIR_NAME = "logs"
    private const val KEEP_DAYS = 7L
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    fun init(app: Context) {
        appContext = app.applicationContext

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                writeLine("CRASH thread=${thread.name} ${throwable.stackTraceToString()}")
            }
            previous?.uncaughtException(thread, throwable)
        }
        cleanOldLogs(app.applicationContext)
    }

    /** 记录一条操作日志（op 只传操作类型，不传内容） */
    fun log(op: String) {
        Log.d(TAG, op)
        val ctx = appContext ?: return
        runCatching { writeLine("OP $op") }
    }

    private fun writeLine(line: String) {
        val ctx = appContext ?: return
        synchronized(lock) {
            val dir = File(ctx.filesDir, DIR_NAME).apply { mkdirs() }
            val fileName = "diag_${fileNameStamp()}.log"
            File(dir, fileName).appendText("${timeStamp()} $line\n")
        }
    }

    private fun timeStamp(): String =
        SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun fileNameStamp(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun cleanOldLogs(context: Context) {
        runCatching {
            val dir = File(context.filesDir, DIR_NAME)
            val cutoff = System.currentTimeMillis() - KEEP_DAYS * 24 * 3_600_000
            dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        }
    }

    /** 打包全部日志为 zip（输出到 cache/diagnostics/） */
    fun exportZip(context: Context): File? = runCatching {
        val dir = File(context.filesDir, DIR_NAME)
        val files = dir.listFiles { f -> f.isFile && f.extension == "log" }
            ?.sortedBy { it.name }
            ?: emptyList()

        val outDir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        outDir.listFiles()?.forEach { it.delete() }

        val zip = File(outDir, "sunnymood_diag_${zipStamp()}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { zos ->
            files.forEach { f ->
                zos.putNextEntry(ZipEntry("logs/${f.name}"))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        zip
    }.getOrNull()

    private fun zipStamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** 设置页一键导出：zip 走系统分享（SAF 免权限） */
    fun shareZip(context: Context): Boolean {
        val zip = exportZip(context) ?: return false
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", zip
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "导出诊断日志").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            Diagnostics.log("export_diag")
            true
        }.getOrDefault(false)
    }
}
