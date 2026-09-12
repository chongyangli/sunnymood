package com.sunnymood.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sunnymood.app.R
import com.sunnymood.app.ui.calendar.CalendarScreen
import com.sunnymood.app.ui.home.HomeScreen
import com.sunnymood.app.ui.policy.PrivacyGate
import com.sunnymood.app.ui.report.ReportScreen
import com.sunnymood.app.ui.theme.SunnyMoodTheme
import com.sunnymood.app.widget.WidgetUpdater

/**
 * 主界面入口（LAUNCHER）：底部导航「首页 / 日历」（M2；M3 增加「报告」Tab）。
 * onResume 主动刷新小组件——App 内编辑/删除后回到桌面也保持状态一致。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SunnyMoodTheme {
                MainScaffold()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WidgetUpdater.refreshAll(this)
    }
}

@Composable
private fun MainScaffold() {
    var tab by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val app = context.applicationContext as SunnyMoodApp

    // 首启《隐私政策》门禁（§3.8）：同意后 DataStore 记忆，不再弹出
    val privacyAgreed by app.settings.privacyAgreed.collectAsState(initial = true)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = {
                            Icon(
                                Icons.Filled.Home,
                                contentDescription = stringResource(R.string.cd_nav_home)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_home)) }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = {
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = stringResource(R.string.cd_nav_calendar)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_calendar)) }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = {
                            Image(
                                painter = painterResource(R.drawable.mood_5),
                                contentDescription = stringResource(R.string.cd_nav_report),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_report)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (tab) {
                0 -> HomeScreen()
                1 -> CalendarScreen()
                else -> ReportScreen()
            }
        }
    }

    // 未同意《隐私政策》时强制弹窗（不默认勾选；拒绝则退出应用）
    if (!privacyAgreed) {
        PrivacyGate(
            onAgreed = { /* DataStore 已置 true，collect 后门禁自动消失 */ },
            onDeclined = { (context as? Activity)?.finishAffinity() }
        )
    }
}
