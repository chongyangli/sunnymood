# 心晴 SunnyMood 🌤️

面向职场人的心情记录 App——**桌面小组件一键记录，月底自动生成情绪报告，端到端加密云同步**。

当前为 **v1.0.0（MVP 完成线）**：开发文档 §5 定义的 M1~M5 全部交付。

## 里程碑进度

- ✅ **M1**（v0.1.0）：工程骨架 / Widget 一键记录 / 记录面板 + 身体感觉 / 双记录模式 / 诊断日志
- ✅ **M2**（v0.2.0）：底部导航 / 近 7 日趋势 / 日历着色 / 记录编辑与删除
- ✅ **M3**（v1.0.0）：月度报告（评分 / 环比 / 分布环形图 / 日趋势+7日均线 / 身体感觉 Top3 与心情×身体关联 / 洞察 / 规则建议 0~3 条 / 触发式关怀卡片含热线 12356 / 免责说明）
- ✅ **M4**（v1.0.0）：数据导出 CSV（UTF-8 BOM，Excel 中文无乱码）/ JSON、《隐私政策》（首启弹窗不默认勾选 + 设置常驻入口）、清空全部数据（二次确认 + 可选联动删除云端备份）、小组件添加引导卡、空状态
- ✅ **M5**（v1.0.0）：WebDAV 云同步——绑定坚果云/NextCloud、PBKDF2 + AES-256-GCM 端到端加密、UUID + LWW + 墓碑合并、记录后 30s 去抖自动同步 + 启动同步 + 手动立即同步、卸载重装完整恢复、同步状态展示

## M5 云同步说明（重要）

- 绑定：设置 → 云同步 → 填服务器（默认坚果云 `https://dav.jianguoyun.com/dav/`）+ 账号 + **应用密码**（坚果云「账户信息 → 安全选项」生成，勿用登录密码）→ 测试连接
- 口令：设置同步口令（二次输入）→ 数据先加密后上传，网盘与开发者均无法解密；**口令丢失云端备份不可恢复**，请抄写备份
- 恢复：卸载重装后绑定同一网盘 + 输入同一口令 → 启动时自动同步 → 全部记录完整恢复
- 隐私：同步默认关闭，未开启时 App 零网络请求（飞行模式/抓包可验证）

## 环境要求

| 项 | 要求 |
|---|---|
| Android Studio | Koala (2024.1.1) 及以上 |
| JDK | 17 |
| compileSdk / targetSdk | 34 |
| minSdk | 26（Android 8.0） |
| Gradle | 8.7（wrapper 已生成） |

## 打开与编译

```bash
# Android Studio：File → Open → 选择本目录，等 Sync 完成后 Run
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

> 工程路径含中文时已在 `gradle.properties` 中加 `android.overridePathCheck=true`；
> 若出现资源/注解处理器相关的诡异编译错误，请把工程移到纯英文路径。

## 工程结构

```
app/src/main/java/com/sunnymood/app/
├── SunnyMoodApp.kt            # Application：appScope + 数据层单例 + 同步初始化
├── MainActivity.kt            # 底部导航（首页/日历/报告）+ 隐私门禁
├── data/
│   ├── db/                    # Room：MoodRecord（UUID + updatedAt + 墓碑）/ DAO
│   ├── MoodRepository.kt      # 唯一写入口（写操作联动同步调度）
│   ├── SettingsStore.kt       # DataStore：记录模式 / 隐私同意位
│   └── Exporter.kt            # CSV / JSON 导出 + 系统分享（M4）
├── diagnostic/Diagnostics.kt  # 崩溃捕获 + 按天日志 + zip 导出
├── report/
│   ├── MoodReportGenerator.kt # 月度统计（纯 Kotlin，§3.6.1）
│   └── AdviceEngine.kt        # 建议规则引擎 + 关怀判定（§3.6.2/3.6.3）
├── sync/                      # WebDAV 云同步（M5，§3.10）
│   ├── SyncCrypto.kt          # PBKDF2 口令派生 + AES-256-GCM
│   ├── WebDavClient.kt        # OkHttp：PROPFIND/MKCOL/PUT/GET/DELETE，仅 HTTPS
│   ├── SyncSettingsStore.kt   # EncryptedSharedPreferences（网盘凭据 + 口令）
│   ├── SyncEngine.kt          # 快照序列化 / LWW+墓碑合并 / 删除云端
│   └── SyncScheduler.kt       # 30s 去抖 + 启动触发（无后台常驻）
├── ui/
│   ├── home/HomeScreen.kt     # 7 档直点 + 近 7 日趋势 + 时间线 + 设置面板
│   ├── calendar/CalendarScreen.kt  # 月历着色 + 当日明细
│   ├── report/ReportScreen.kt # 月度报告（Canvas 图表，§4.5）
│   ├── record/RecordPanelActivity.kt  # 记录 / 编辑双形态面板
│   ├── policy/PrivacyPolicy.kt# 隐私政策全文 + 首启门禁弹窗
│   ├── settings/SyncSettingsSheet.kt  # 云同步绑定 / 口令 / 状态
│   └── theme/                 # ColorOS 光场风格主题（深浅色 + 动态取色）
├── util/                      # MoodSpec（7 级唯一事实源）/ 时间工具 / 震动
└── widget/                    # 4x1/4x2 Provider + 点击接收器 + 刷新器
```

## v1.0.0 真机验收要点（对应文档 §10）

**M3 报告**：评分/天数与手工核算一致；分布 7 色；趋势柱+均线；身体 Top3 与关联方向合理；建议 0~3 条措辞无评判性；构造连续低落数据出现关怀卡片（热线 12356）；空月份空状态。

**M4 合规与数据**：首启弹窗不默认勾选、同意后不再弹、设置有入口；导出 CSV Excel 打开无乱码、JSON 可解析；清空全部数据二次确认、全页面回空状态；诊断日志导出正常。

**M5 云同步**：坚果云连接测试通过；开启后云端出现 `sunnymood/backup.json.enc`，网页端打开全密文；**卸载重装 → 同一网盘同一口令 → 记录完整恢复**；错误口令提示解密失败不崩溃；一端删除另一端不复活；未开启同步时飞行模式全程可用。

**M1/M2 回归**：一键记录、面板秒关不丢数据、纯点击模式、编辑/删除、日历着色、升级安装数据保留。

完整产品定义见《心情记录App-开发文档.md》（v1.6）。
