package com.sunnymood.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * 设置存储（DataStore）。
 * M1：记录模式开关（面板模式默认 / 纯点击模式可选，开发文档 §3.5）。
 */
class SettingsStore private constructor(private val context: Context) {

    private object Keys {
        val TAP_ONLY = booleanPreferencesKey("tap_only_mode")
        val PRIVACY_AGREED = booleanPreferencesKey("privacy_agreed")
    }

    /** 纯点击模式（默认关闭 = 面板模式） */
    val tapOnlyMode: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.TAP_ONLY] ?: false }

    suspend fun setTapOnlyMode(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.TAP_ONLY] = value }
    }

    /** 《隐私政策》同意位（首启弹窗必须显式勾选同意，§3.8） */
    val privacyAgreed: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.PRIVACY_AGREED] ?: false }

    suspend fun setPrivacyAgreed(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.PRIVACY_AGREED] = value }
    }

    companion object {
        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
