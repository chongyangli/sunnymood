package com.sunnymood.app.ui.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sunnymood.app.R
import com.sunnymood.app.SunnyMoodApp
import com.sunnymood.app.data.SettingsStore
import kotlinx.coroutines.launch

/**
 * 《隐私政策》内容（开发文档 §3.8 要点）：
 * 收集范围 / 不收集清单 / 权限用途 / 数据安全 / 云同步专章 / 用户权利 / 未成年人 / 联系方式。
 */
@Composable
fun PrivacyPolicyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "心晴《隐私政策》",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "更新日期：2026-09-12 · 本政策适用于「心晴 SunnyMood」App",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        PolicyParagraph("1. 我们收集什么")
        PolicyBody("心晴仅存储你在本机上主动记录的心情与身体感觉数据。除此之外，我们不收集任何信息：不采集设备标识、位置、通讯录、相册、应用列表，也不接入任何统计、广告、推送类第三方 SDK。")
        PolicyParagraph("2. 数据存在哪里")
        PolicyBody("默认情况下，全部数据只保存在你的手机本地（App 卸载即随之删除）。App 自身没有任何开发者服务器，不存在向开发者上传数据的行为。")
        PolicyParagraph("3. 网络权限的用途")
        PolicyBody("App 申请了网络权限，但仅用于你主动开启的「云同步」功能。未开启云同步时，App 不发出任何网络请求（可开启飞行模式验证）。")
        PolicyParagraph("4. 云同步与端到端加密")
        PolicyBody("开启云同步后，你的数据会先在手机上加密（同步口令派生密钥，AES-256-GCM），再上传到你自己绑定并授权的网盘（如坚果云）。网盘服务商与 App 开发者都无法解密内容。请牢记同步口令：口令丢失将无法恢复云端备份（本地数据不受影响）。")
        PolicyParagraph("5. 你的权利")
        PolicyBody("你可以随时在时间线/日历中编辑或删除任意记录；可以在设置中一键清空全部数据（可选同时删除云端备份）；可以通过系统分享导出 CSV/JSON 文件，自行保管或迁移数据。")
        PolicyParagraph("6. 未成年人")
        PolicyBody("本 App 不面向未满 14 周岁的儿童收集任何个人信息。")
        PolicyParagraph("7. 联系我们")
        PolicyBody("如对本政策有任何疑问或投诉，请联系：support@sunnymood.app（占位邮箱，发布前请替换为你的真实邮箱）。")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PolicyParagraph(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun PolicyBody(body: String) {
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        lineHeight = 24.sp
    )
    Spacer(Modifier.height(14.dp))
}

/** 全屏查看《隐私政策》（设置页入口） */
@Composable
fun PrivacyPolicyDialog(onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            PrivacyPolicyContent()
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Text("关闭")
            }
        }
    }
}

/**
 * 首启《隐私政策》弹窗（§3.8）：不默认勾选，同意后进入并记忆；
 * 「不同意」退出应用（不进入主界面、不产生任何数据写入）。
 */
@Composable
fun PrivacyGate(onAgreed: () -> Unit, onDeclined: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var checked by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* 强制选择，不响应外部点击 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.privacy_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    PolicyBody("心晴默认把你的心情记录只保存在手机本地：无账号、无广告、无任何第三方数据收集 SDK。")
                    PolicyBody("可选开启的云同步会把数据加密后存到你自己的网盘，开发者与网盘均无法解密。")
                    PolicyBody("完整内容请查看《隐私政策》全文。")
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.privacy_dialog_agree_hint),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        (context.applicationContext as SunnyMoodApp)
                            .settings.setPrivacyAgreed(true)
                        onAgreed()
                    }
                },
                enabled = checked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(stringResource(R.string.privacy_dialog_agree))
            }
            TextButton(
                onClick = onDeclined,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = stringResource(R.string.privacy_dialog_decline),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.privacy_dialog_decline_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
