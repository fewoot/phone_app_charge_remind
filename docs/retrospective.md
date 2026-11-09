# 充电提醒 App 开发复盘

> 从一句「我想做个提醒自己给充电宝充电的 App」，到真机上跑起来的原生 Android 应用。
> 这份文档记录**过程、决策和踩过的坑**，目标是让下一个项目少走同样的弯路。

> **归档说明**：本文写于 2026-11-05。仓库后来整理成了标准 GitHub 结构，
> 文中路径已按当前仓库更新；调研 / 需求 / 复盘 / 构建说明统一放在 `docs/`。

---

## 1. 项目档案

| 项 | 内容 |
|---|---|
| 应用名 | 充电提醒 |
| 形态 | 单 Activity + Jetpack Compose，纯本地、不联网 |
| 设备 | Android 16 手机（targetSdk 36，minSdk 26） |
| 代码量 | 19 个 Kotlin 文件 / 约 3,200 行 |
| 原型 | 5 个 HTML/CSS/JS 文件 / 约 2,430 行 |
| 周期 | 2026-11-01 调研 → 11-03 原型 → 11-04 工程 → 11-05 真机跑通 |
| 产出 | `android-app/`（Android 工程）、`prototype/`（HTML 原型）、`docs/`（调研 / 需求 / 复盘 / 构建说明） |

**工具链（最终）**

| 项 | 版本 | 备注 |
|---|---|---|
| Android Studio | 2026.2.2 (Rabbit 2) | JDK 25 运行时 |
| AGP | 9.5.0 | 从 9.3.1 升级而来 |
| Gradle | 9.7.1 | 本地 zip 安装 |
| Kotlin | 2.4.20 | AGP 9 内置，不再单独 apply `kotlin-android` |
| Compose BOM | 2026.09.00 | 实际解析到 Compose 1.12.1 |
| compileSdk | 37 + minor 2 | 依赖要求 ≥ 37 |
| targetSdk / minSdk | 36 / 26 | 行为面向 Android 16 |
| JVM target | 17 | `compileOptions` + `kotlin.compilerOptions` |

---

## 2. 五个阶段

### 2.1 调研：先确认「有没有现成的」

原始需求：给充电宝、手表、耳机这些设备做「该充电了」的提醒，不想常驻后台。

- 先搜「充电提醒 / 电池类」开源 App：BatteryNotification、Healthy Battery Charging —— 都是**电量驱动 + 常驻/轮询**，与「时间驱动」不是一类。
- 再搜「周期提醒 / 习惯打卡」：MedTimer、reReminder、MainTask、Loop、Table Habit、Tasks.org。
- 结论：**没有**「上次充电时间 + 间隔天数」的成熟开源 App；语义最接近的是周期任务类（MainTask），工程最适合参考的是 reReminder（精确闹钟写得最规范）。

> 经验：**先确认「要不要从零写」**。调研花半天，可能省掉两周返工；也可能像这次一样，确认必须自己写，但顺手拿到了一份可用的版本清单和闹钟实现参考。

### 2.2 原型：先用 HTML 把界面定下来

没有直接写 Android，而是先做了 6 屏可点击的 HTML 原型（首页 / 详情 / 编辑 / 设置 / 记录充电 / 深色）。

**为什么值得**：

- 改一版视觉只要几分钟，比改 Compose 快一个量级
- 能在动手前发现信息层级问题（比如「卡片太胖，一屏只放得下 3 台」）
- 顺手验证了「换背景 + 模糊 + 暗化 + 动态取色」的观感

**原型定不了的**：通知真实行为、精确闹钟权限、国产 ROM 后台限制、拍照/相册。

> 教训：HTML 原型里「内页透明」导致页面叠加文字，直到截图才发现。**每做完一屏就截图看**，不要凭想象。

### 2.3 搭工程：版本不要猜，抄同代项目

新建空工程最大的风险不是代码，是**版本组合**。做法：

1. 找同代、活跃、单模块的开源 Android 项目（这次是 reReminder，2026-10 还在提交）
2. 直接抄它的 `gradle/libs.versions.toml`：AGP / Kotlin / Compose BOM / core / activity / lifecycle
3. 只替换应用相关的部分

这比「凭记忆写 8.0.0」可靠得多——2026 年的 Compose 生态和 2024 年已经完全不是一回事。

**最终架构**（刻意保持简单）：

| 决策 | 选择 | 理由 |
|---|---|---|
| 数据库 | 不用 Room，JSON 文件 | 设备数量个位数，省掉 KSP 和迁移 |
| 图片加载 | 不用 Coil，手写 Bitmap 采样 | 只有设备图标和背景两处 |
| 导航 | 不用 navigation-compose，sealed class + `when` | 4 个页面，返回栈就一个列表 |
| DI | 不用 Hilt，`object Repo` | 单模块 |
| 状态 | `mutableStateOf` + Compose 自动重组 | 不需要 Flow/ViewModel |
| 提醒 | `AlarmManager` + `BroadcastReceiver` | 零常驻，不耗电 |

### 2.4 踩坑与修复（这一节最值钱）

见第 4 节。

### 2.5 真机跑通

第一次 `Run` 的结果：

- `compileDebugKotlin` **通过**（只有 2 个 warning），说明代码层面没有语法/引用错误
- 卡在 `checkDebugAarMetadata`：14 条依赖要求 `compileSdk ≥ 37`
- 把 `compileSdk` 从 36.1 提到 37.2 后构建通过

真机效果：首页、拍照图标、动态取色（紫色来自壁纸）、FAB 都正常。

---

## 3. 技术方案（可复用）

### 3.1 数据模型

```
Device：id / name / emoji / photo / color / type(recurring|onetime)
        intervalValue + unit(day|week|month) / dueDate
        remindHour + remindMinute / advanceDays
        adaptive / enabled / completed / snoozeDays
        lastChargedAt / createdAt / history[]
ChargeRecord：at / pct（剩余电量）/ gap（距上次天数）
```

全部 `@Serializable`，一个 `data.json` 存 `AppData(devices, settings)`。

### 3.2 提醒引擎（核心不到 100 行）

```
到期日 = 上次充电日 + 间隔天数 + 推迟天数
触发时间 = 到期日 - 提前天数，当天 remindTime
```

- 用 `setExactAndAllowWhileIdle(RTC_WAKEUP)`，到点由系统唤醒 App
- 每台设备一个 `PendingIntent`（requestCode = id.hashCode()）
- 没有精确闹钟权限时退化为 `setAndAllowWhileIdle`
- 广播接收器：发通知 → 重新排下一次（逾期每天提醒 / 不再提醒）
- 开机、改时间、改时区、应用更新后全量重排
- 通知按钮（已充电 / 推迟 3 天）走 `BroadcastReceiver`，**不要**在通知里起 Activity（Android 12+ 禁止 trampoline）

### 3.3 充电自适应

记录充电时可选填「剩余电量」。第一版是「剩余 ≥40% 就拉长、≤15% 就缩短」，但**提前充电时电量天然很高**，会被误判成「还能用更久」。所以改成按**实际耗电速度**推算：

```
单日耗电 = (100 - 当前剩余) / 实际用了几天
到 20% 的天数 = 80 / 单日耗电
新间隔 = 这个天数（单次最多相对当前变化 ±30%）
```

这样：

| 场景 | 计划 | 实际 | 结果 |
|---|---|---|---|
| 按期充 | 14 天 | 第 14 天剩 21% | 推算 14.2 天 → 保持 14 |
| 提前充、符合预期 | 14 天 | 第 7 天剩 60% | 推算 14.0 天 → 保持 14 |
| 提前充、掉电很慢 | 14 天 | 第 7 天剩 80% | 推算 28 天 → 先调到 18（+30% 上限） |
| 忘记充、放太空 | 14 天 | 第 14 天剩 10% | 推算 12.4 天 → 调到 12 |

配套规则：

- 只存整数天，小数只用于展示（如「约 16.8 天 → 取整 17」）
- 还没到计划时间、电量 >84% 视为「补电」，不参与学习
- 同一天充两次（用了 0 天）不调整
- 单位换算：能整除就保留「周/月」，否则退回「天」

### 3.4 UI 结构

- 根 `Box`：背景层（渐变 / 照片 + 暗化）→ 当前页面
- 页面用 `sealed interface Screen`（Home / Detail / Editor / Settings）+ 一个 `BackHandler`
- 主题：`AppColors` 数据类（accent、bgTop、bgBottom、card、text 三级、danger、amber…），由「配色 + 深浅色 + 系统动态色」算出来
- 卡片：半透明底 + 1dp 描边 + 20dp 圆角 + 进度环
- 内页用不透明渐变背景，避免叠字

---

## 4. 踩坑全记录

| # | 现象 | 原因 | 解决 |
|---|---|---|---|
| 1 | `Could not install Gradle distribution … Read timed out` | services.gradle.org 国内不稳定 | 本地 zip + `distributionUrl=file:///…`，或腾讯镜像 |
| 2 | 本地 zip 装上还是失败 | 下载到的 zip **不完整**（131MB vs 官方 151MB，缺 `bin/` 和 `gradle-launcher`） | 校验大小 + SHA-256 + 关键文件，再重下 |
| 3 | 项目没有 `gradle-wrapper.jar` | 二进制文件无法用文本生成 | IDE 走 `gradle-wrapper.properties` 即可；命令行才需要 jar，或用「Specified location」 |
| 4 | Studio 弹窗要写代理到 gradle.properties | 代理配置是空的（host 空、端口 80） | **点 No**；空代理写进全局会拖垮所有项目 |
| 5 | 14 条 `AAR metadata` 报错，要求 compileSdk 37 | Compose 1.12.1 / core 1.19.0 / lifecycle 2.11 都声明 `minCompileSdk=37` | `compileSdk=37` + `compileSdkMinor=2`（Android 17 只有 37.0/37.1/37.2，没有无后缀的 37） |
| 6 | 构建脚本里的 AGP 从 9.3.1 变成 9.5.0 | Studio 提示升级，顺手点了 | 可用；与 Gradle 9.7.1 兼容 |
| 7 | HTML 原型内页文字叠加 | 内页没有背景，透明叠在首页上 | 给内页加不透明渐变背景 |
| 8 | 无头浏览器截图右侧被裁 | `--window-size` 与实际视口不一致（481 vs 430 CSS px） | 用 iframe 拼多屏、按固定尺寸裁图，不依赖窗口尺寸 |
| 9 | 底部弹层点空白处会关闭 | Compose 点击穿透到下面的遮罩 | 给弹层加 `clickable(interactionSource, indication=null){}` 消费点击 |
| 10 | 编辑页「保存」跟着列表滚走 | 保存栏放在可滚动的 Column 里 | 移到 `.app` 级 Box，按当前页面显示/隐藏 |

### 4.1 两个值得记住的排查方法

**验证下载的 zip 是否完整**

```powershell
Get-FileHash .\gradle-9.7.1-bin.zip -Algorithm SHA256

Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead("$PWD\gradle-9.7.1-bin.zip")
$z.Entries | Where-Object { $_.FullName -match 'bin/gradle\.bat|gradle-launcher-.*\.jar' }
```

官方 9.7.1：151,433,392 字节，SHA-256 `acd53f1e…04d20a`。
坏包的特征：能正常打开、CRC 也过，但**条目数少、缺目录**——「能解压」不等于「完整」。

**看 SDK 里到底装了什么**

```powershell
Get-ChildItem "$env:ANDROID_HOME\platforms" -Directory | Select-Object Name
Get-ChildItem "$env:ANDROID_HOME\build-tools" -Directory | Select-Object Name
```

`compileSdk 37` 找不到平台时，先看这里有没有 `android-37.x`，再决定是装 SDK 还是改 `compileSdkMinor`。

### 4.2 国内网络下的仓库配置

`settings.gradle.kts` 里镜像优先、官方兜底：

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google(); mavenCentral(); gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google(); mavenCentral()
    }
}
```

镜像返回 404 时会自动落到官方源；某个依赖找不到时，先把镜像三行注释掉再试。

---

## 5. 可复用清单

### 5.1 新 Android 工程启动清单（国内环境）

- [ ] 找同代活跃开源项目，抄 `libs.versions.toml`，**不要猜版本**
- [ ] 先看依赖的 `minCompileSdk`，再定 `compileSdk`（宁高勿低）
- [ ] Gradle 发行版提前下好：本地 zip 或腾讯镜像
- [ ] zip 必须校验大小 + SHA-256 + 关键文件
- [ ] 仓库加阿里云镜像，官方源兜底
- [ ] Studio 代理弹窗：host 为空时一律 **No**
- [ ] SDK 平台按 `compileSdk` 装齐（注意 Android 17 的 minor 版本）
- [ ] 真机第一次跑之前，先想好要申请哪些权限

### 5.2 编译失败排查顺序

1. **先看是哪个 task 红**：configuration → 依赖解析 → AAR metadata → Kotlin → 资源 → 打包
2. `AAR metadata` 报 `compileSdk` → 升 compileSdk，不要降一堆依赖
3. `Unresolved reference` → 漏 import / 依赖没加 / 版本不对
4. 下载超时 → 本地化或换镜像，别反复重试
5. 贴报错要**整段**（带文件、行号、task 名），只贴第一行等于没贴

### 5.3 提醒类 App 必做项

- [ ] 通知权限（Android 13+ 运行时申请）
- [ ] 精确闹钟权限（Android 12+；14+ 默认不给，要引导用户去开）
- [ ] 电池优化白名单（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）
- [ ] 开机 / 改时间 / 改时区 / 应用更新后重排
- [ ] 通知按钮不用 Activity，用广播
- [ ] 国产 ROM：小米/华为/OPPO/vivo 的自启动、后台无限制
- [ ] 测试路径：间隔 1 天 + 上次充电选昨天 + 提醒时间设 2 分钟后

### 5.4 和 AI 协作写 App 的方式

这次有效的原因不是「AI 会写代码」，而是流程：

1. **先给环境和版本**，不要让它猜（Studio 版本、SDK、JDK、网络情况）
2. **先原型后代码**：HTML 定 UI，再 1:1 转 Compose
3. **一次一个可编译的小步**，不要一次要 50 个文件
4. **人负责编译和真机**，把完整报错贴回去
5. **版本信息让 AI 去同代开源项目里查**，比模型记忆可靠

---

## 6. 成本

| 项 | 数量 |
|---|---|
| 总耗时 | 约 4 天（含等待下载） |
| 有效开发 | 约 2 天 |
| 编译失败轮次 | 2 次（Gradle 包损坏、compileSdk） |
| 需求变更 | 3 次（更紧凑、拍照图标、署名） |

---

## 7. 如果重来一次

1. **第一天就把 Gradle 和 SDK 备齐**，再开始写代码——网络问题最容易打断节奏
2. 建工程时直接把 `compileSdk` 拉到依赖要求的最高值，避免「编译全过、卡在 metadata」
3. 原型阶段同时做「紧凑版 / 宽松版」两套，比事后调间距快
4. 更早真机跑：模拟器不暴露 ROM 后台限制、通知权限这些真实问题
5. 把「提醒是否真的响」做成一个内置的自检页（现在只有手动测试通知）

---

## 8. 附录

### 8.1 目录结构

```
android-app/
├── app/src/main/java/com/example/chargereminder/
│   ├── ChargeApp.kt / MainActivity.kt
│   ├── data/      Models.kt  DeviceCalc.kt  Repo.kt
│   ├── alarm/     Reminders.kt  ReminderReceiver.kt  BootReceiver.kt
│   ├── notify/    Notifications.kt
│   ├── util/      Images.kt  Format.kt
│   └── ui/        Theme.kt  Components.kt  ChargeReminderApp.kt
│                  HomeScreen.kt  DetailScreen.kt  EditorScreen.kt
│                  SettingsScreen.kt  ChargeSheet.kt
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.properties   ← Gradle 9.7.1（默认腾讯镜像）
├── verify-gradle.ps1                          ← 可选：离线 zip 完整性校验
└── README.md

prototype/          HTML 原型（index.html 可直接打开）

# 仓库根目录
README.md  LICENSE  CHANGELOG.md  .gitignore
docs/
├── research.md        开源基座调研与方案
├── requirements.md    需求清单（定稿）
├── retrospective.md   本文
├── build.md           构建与环境说明
└── screenshots/       截图
```

### 8.2 常用命令

```powershell
cd android-app

# 校验 Gradle 离线包（见 docs/build.md）
powershell -ExecutionPolicy Bypass -File .\verify-gradle.ps1

# 看设备有没有连上
adb devices

# 看当前构建用的 SDK
Get-Content .\gradle\libs.versions.toml | Select-String compileSdk
```

### 8.3 参考

- reReminder（精确闹钟 / 权限自检的写法）：<https://github.com/ProfessorQuantumUniverse/reReminder>
- MedTimer（模块化 + 提醒产品设计）：<https://github.com/Futsch1/medTimer>
- Compose BOM 与版本映射：<https://developer.android.com/develop/ui/compose/bom/bom-mapping>
- Gradle 发行版：<https://services.gradle.org/distributions/>
- 腾讯 Gradle 镜像：<https://mirrors.cloud.tencent.com/gradle/>

---

*本文档由本次开发过程整理，欢迎直接拿去做下一个项目的模板。*
