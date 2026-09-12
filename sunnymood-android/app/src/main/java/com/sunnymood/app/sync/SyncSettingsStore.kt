package com.sunnymood.app.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** 同步状态（设置页展示） */
data class SyncStatus(
    val lastSyncAt: Long = 0,
    val ok: Boolean = false,
    val error: String? = null
)

/**
 * 云同步配置与口令存储（开发文档 §3.10）：
 * EncryptedSharedPreferences 加密保存服务器地址 / 账号 / 应用密码 / 同步口令；
 * 卸载即随之清除，重装恢复时需重新输入（口令丢失则云端备份无法解密，开启时已明示）。
 */
class SyncSettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "sunnymood_sync_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        // 极端情况（系统密钥库异常）：退回普通 prefs，只影响口令的静态加密，不影响端到端加密
        context.getSharedPreferences("sunnymood_sync_fallback", Context.MODE_PRIVATE)
    }

    private object Keys {
        const val SERVER = "server"
        const val ACCOUNT = "account"
        const val APP_PASSWORD = "app_password"
        const val PASSPHRASE = "passphrase"
        const val AUTO_SYNC = "auto_sync"
        const val LAST_SYNC_AT = "last_sync_at"
        const val LAST_SYNC_OK = "last_sync_ok"
        const val LAST_SYNC_ERROR = "last_sync_error"
    }

    var server: String
        get() = prefs.getString(Keys.SERVER, DEFAULT_SERVER) ?: DEFAULT_SERVER
        set(v) = prefs.edit().putString(Keys.SERVER, v).apply()

    var account: String
        get() = prefs.getString(Keys.ACCOUNT, "") ?: ""
        set(v) = prefs.edit().putString(Keys.ACCOUNT, v).apply()

    var appPassword: String
        get() = prefs.getString(Keys.APP_PASSWORD, "") ?: ""
        set(v) = prefs.edit().putString(Keys.APP_PASSWORD, v).apply()

    /** 同步口令（仅本机保存，加密静态存储；不上传） */
    var passphrase: String
        get() = prefs.getString(Keys.PASSPHRASE, "") ?: ""
        set(v) = prefs.edit().putString(Keys.PASSPHRASE, v).apply()

    /** 自动同步（默认开：记录后 30s 去抖 + 启动时；关闭则纯手动） */
    var autoSync: Boolean
        get() = prefs.getBoolean(Keys.AUTO_SYNC, true)
        set(v) = prefs.edit().putBoolean(Keys.AUTO_SYNC, v).apply()

    /** 已绑定网盘（服务器 + 账号 + 应用密码齐备） */
    fun isConfigured(): Boolean =
        server.isNotBlank() && account.isNotBlank() && appPassword.isNotBlank()

    /** 已设置同步口令（真正开始同步还需要它） */
    fun hasPassphrase(): Boolean = passphrase.isNotBlank()

    fun status(): SyncStatus = SyncStatus(
        lastSyncAt = prefs.getLong(Keys.LAST_SYNC_AT, 0),
        ok = prefs.getBoolean(Keys.LAST_SYNC_OK, false),
        error = prefs.getString(Keys.LAST_SYNC_ERROR, null)
    )

    fun setStatus(status: SyncStatus) {
        prefs.edit()
            .putLong(Keys.LAST_SYNC_AT, status.lastSyncAt)
            .putBoolean(Keys.LAST_SYNC_OK, status.ok)
            .putString(Keys.LAST_SYNC_ERROR, status.error)
            .apply()
    }

    companion object {
        const val DEFAULT_SERVER = "https://dav.jianguoyun.com/dav/"

        @Volatile
        private var instance: SyncSettingsStore? = null

        fun get(context: Context): SyncSettingsStore =
            instance ?: synchronized(this) {
                instance ?: SyncSettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
