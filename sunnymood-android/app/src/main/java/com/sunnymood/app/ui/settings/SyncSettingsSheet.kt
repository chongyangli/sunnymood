package com.sunnymood.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunnymood.app.R
import com.sunnymood.app.SunnyMoodApp
import com.sunnymood.app.diagnostic.Diagnostics
import com.sunnymood.app.sync.SyncEngine
import com.sunnymood.app.sync.SyncException
import com.sunnymood.app.sync.SyncSettingsStore
import com.sunnymood.app.sync.SyncStatus
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 云同步设置（M5，开发文档 §3.10）：
 * 绑定网盘（坚果云地址预置 + 应用密码 + 连接测试）→ 设置同步口令（二次确认）→
 * 自动/手动同步切换 + 立即同步 + 上次同步状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as SunnyMoodApp
    val scope = rememberCoroutineScope()
    val store = remember { SyncSettingsStore.get(context) }

    // ── 绑定表单 ──
    var server by remember { mutableStateOf(store.server) }
    var account by remember { mutableStateOf(store.account) }
    var appPassword by remember { mutableStateOf(store.appPassword) }
    var testResult by remember { mutableStateOf<String?>(null) }

    // ── 口令表单 ──
    var pass1 by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }

    // ── 状态 ──
    var status by remember { mutableStateOf(store.status()) }
    var autoSync by remember { mutableStateOf(store.autoSync) }
    var syncing by remember { mutableStateOf(false) }

    val configured = remember(server, account, appPassword) {
        server.isNotBlank() && account.isNotBlank() && appPassword.isNotBlank()
    }
    val hasPass = store.hasPassphrase()

    fun saveBinding() {
        store.server = server.trim()
        store.account = account.trim()
        store.appPassword = appPassword
        Diagnostics.log("sync_bind")
    }

    fun startSync() {
        if (syncing) return
        syncing = true
        scope.launch {
            val r = SyncEngine.syncNow(context)
            syncing = false
            status = store.status()
            Toast.makeText(
                context,
                if (r.isSuccess) R.string.sync_done_ok else R.string.sync_done_fail,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.sync_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sync_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(16.dp))

            // 绑定网盘
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(stringResource(R.string.sync_server)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text(stringResource(R.string.sync_account)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = appPassword,
                onValueChange = { appPassword = it },
                label = { Text(stringResource(R.string.sync_app_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sync_app_password_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        saveBinding()
                        testResult = null
                        scope.launch {
                            testResult = try {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.sunnymood.app.sync.WebDavClient(
                                        server.trim(), account.trim(), appPassword
                                    ).checkConnection()
                                }
                                context.getString(R.string.sync_test_ok)
                            } catch (e: Exception) {
                                context.getString(
                                    R.string.sync_test_fail,
                                    (e as? SyncException)?.message ?: e.message
                                        ?: context.getString(R.string.sync_err_network)
                                )
                            }
                        }
                    },
                    enabled = configured,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.sync_test)) }

                Button(
                    onClick = {
                        saveBinding()
                        startSync()
                    },
                    enabled = configured && hasPass,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.sync_now)) }
            }
            testResult?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it == context.getString(R.string.sync_test_ok)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(Modifier.height(18.dp))

            // 同步口令
            Text(
                text = if (hasPass) {
                    stringResource(R.string.sync_passphrase_set)
                } else {
                    stringResource(R.string.sync_passphrase_title)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (!hasPass) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.sync_passphrase_warn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass1,
                    onValueChange = { pass1 = it },
                    label = { Text(stringResource(R.string.sync_passphrase)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass2,
                    onValueChange = { pass2 = it },
                    label = { Text(stringResource(R.string.sync_passphrase_confirm)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        if (pass1.length < 6) {
                            Toast.makeText(
                                context, R.string.sync_passphrase_short, Toast.LENGTH_SHORT
                            ).show()
                        } else if (pass1 != pass2) {
                            Toast.makeText(
                                context, R.string.sync_passphrase_mismatch, Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            store.passphrase = pass1
                            Diagnostics.log("sync_passphrase_set")
                            Toast.makeText(
                                context, R.string.sync_passphrase_saved, Toast.LENGTH_SHORT
                            ).show()
                            // 刷新面板（hasPass 变化触发重组合的最简方式）
                            status = store.status()
                            autoSync = store.autoSync
                        }
                    },
                    enabled = pass1.isNotBlank() && pass2.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.sync_passphrase_save)) }
            }

            // 已开启同步：自动开关 + 状态
            if (configured && hasPass) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.sync_auto),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.sync_auto_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSync,
                        onCheckedChange = {
                            autoSync = it
                            store.autoSync = it
                            Diagnostics.log("sync_auto_$it")
                        }
                    )
                }
                Spacer(Modifier.height(10.dp))
                SyncStatusLine(status)
            }

            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text(stringResource(R.string.sync_close)) }
        }
    }
}

/** 上次同步状态行 */
@Composable
private fun SyncStatusLine(status: SyncStatus) {
    val timeText = if (status.lastSyncAt == 0L) {
        stringResource(R.string.sync_never)
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(status.lastSyncAt))
    }
    Text(
        text = if (status.lastSyncAt == 0L) {
            stringResource(R.string.sync_status_never, timeText)
        } else if (status.ok) {
            stringResource(R.string.sync_status_ok, timeText)
        } else {
            stringResource(R.string.sync_status_fail, timeText, status.error ?: "")
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 18.sp
    )
}
