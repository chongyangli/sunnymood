package com.sunnymood.app.sync

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * WebDAV 客户端（开发文档 §3.10）：
 * OkHttp 直发标准 WebDAV 方法（PROPFIND / MKCOL / PUT / GET / DELETE），兼容坚果云 / NextCloud 等。
 * 仅 HTTPS（明文由 manifest 的 usesCleartextTraffic=false 全局禁止）；Basic 认证使用应用密码。
 */
class WebDavClient(
    private val serverUrl: String,
    private val account: String,
    private val appPassword: String
) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val auth: String = Credentials.basic(account, appPassword)

    private fun url(vararg segments: String): String =
        serverUrl.trimEnd('/') + "/" + segments.joinToString("/")

    private fun Request.Builder.withAuth(): Request.Builder =
        this.header("Authorization", auth)

    /** 连接测试：PROPFIND Depth 0；2xx / 207 视为成功 */
    fun checkConnection() {
        val request = Request.Builder()
            .url(url())
            .withAuth()
            .header("Depth", "0")
            .method("PROPFIND", null)
            .build()
        client.newCall(request).execute().use { resp ->
            when (resp.code) {
                in 200..299, 207 -> Unit
                401 -> throw SyncException("账号或应用密码错误")
                else -> throw SyncException("服务器响应 ${resp.code}，请检查服务器地址")
            }
        }
    }

    /** 确保远端目录存在（MKCOL；405 = 已存在，忽略） */
    fun ensureFolder(folder: String) {
        val request = Request.Builder()
            .url(url(folder))
            .withAuth()
            .method("MKCOL", null)
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code !in 200..299 && resp.code != 405) {
                throw SyncException("创建云端目录失败（${resp.code}）")
            }
        }
    }

    /** 上传密文快照 */
    fun put(folder: String, fileName: String, bytes: ByteArray) {
        val request = Request.Builder()
            .url(url(folder, fileName))
            .withAuth()
            .put(bytes.toRequestBody())
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code !in 200..299) {
                throw SyncException("上传失败（${resp.code}）")
            }
        }
    }

    /** 下载快照；远端不存在返回 null（首次同步） */
    fun get(folder: String, fileName: String): ByteArray? {
        val request = Request.Builder()
            .url(url(folder, fileName))
            .withAuth()
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            return when {
                resp.code == 404 -> null
                resp.code in 200..299 -> resp.body?.bytes()
                resp.code == 401 -> throw SyncException("账号或应用密码错误")
                else -> throw SyncException("下载失败（${resp.code}）")
            }
        }
    }

    /** 删除云端备份（「清空全部数据」联动选项） */
    fun delete(folder: String, fileName: String) {
        val request = Request.Builder()
            .url(url(folder, fileName))
            .withAuth()
            .delete()
            .build()
        client.newCall(request).execute().use { resp ->
            if (resp.code !in 200..299 && resp.code != 404) {
                throw SyncException("删除云端备份失败（${resp.code}）")
            }
        }
    }
}
