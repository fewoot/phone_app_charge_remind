# 给充电器做定时提醒的安卓 App：开源基座调研 & 落地方案

> 调研时间：2026-11-01　｜　目标：个人自用、基于成熟开源项目改、界面要好看、能换背景

> **归档说明**：本文是立项时的调研记录。最终**没有 Fork 任何现成项目**，
> 而是从零写了一个原生 Kotlin + Compose 工程（原因见 [项目复盘](retrospective.md)）。
> 文中对开源项目的评价保留当时的判断，供以后选型参考。

---

## 0. 结论先行

1. **技术上不复杂，不需要常驻后台。** 用系统精确闹钟（`AlarmManager`）到点唤醒 App 发一条通知即可，平时不占内存、不耗电。你担心的"每天都得点开一下"不必作为主方案，它只作为**没给权限时的降级方案**。
2. **今天就能用，0 行代码**：Tasks.org 建一个"每 14 天、从完成日算起"的循环任务，就是你要的功能。先用一周验证"提醒节奏"是否合身，再决定怎么改代码。
3. **要改成自己的 App**，推荐顺序：
   - **MedTimer**（MIT 许可、Kotlin、自带中文、提醒引擎成熟、模块化）→ 最稳
   - **reReminder**（GPL-3、Kotlin + Compose、代码最短最好读、精确闹钟写得最规范）→ 想自己看懂代码选它
   - **MainTask**（GPL-3、"每 N 天"语义最贴合、有小组件和主题）→ 语义最像，但没有中文
4. **现成的"电池类"开源 App 都不适合作基座**（BatteryNotification、Healthy Battery Charging）：它们是"电量驱动 + 常驻通知/轮询"，跟你"时间驱动"是两类产品，只能借它们读电池状态的那几行代码。

---

## 1. 先把需求翻译成模型

你要的其实不是"电池 App"，而是一个**带倒计时的设备保养清单**：

```
上次充电时间 + 间隔天数 = 该充电的日期
到该充电的日期（可提前 N 天）→ 发通知
点"已充电" → 记录今天 → 重新计算下一次
```

所以真正需要的零件只有四个：

| 零件 | 说明 |
|---|---|
| 设备列表 | 充电宝 / 手表 / 耳机 / 牙刷 / 剃须刀 / 相机电池…… 每台一个"上次充电时间 + 间隔天数" |
| 倒计时 | 首页显示"还有 2 天 / 今天 / 逾期 3 天" |
| 定时提醒 | 到点发通知，通知上直接有【已充电】【推迟 3 天】 |
| 设置 | 提醒时间、提前几天、换背景、主题、备份 |

### 1.1 三档提醒强度（建议做成设置项）

| 档位 | 做法 | 需要权限 | 效果 |
|---|---|---|---|
| **L1 打开即刷新** | 每次打开 App 重算天数，首页显示待办 | 无 | 不主动提醒，只在你想起它时告诉你（你说的"每天点开一下"就是这个） |
| **L2 精确闹钟通知** | `AlarmManager` + 广播接收器 → 发通知 | 通知权限 + 精确闹钟权限 | 日常够用，Doze 休眠下也能按时响 |
| **L3 闹钟式提醒** | `setAlarmClock()`，状态栏出现闹钟图标 | 同上 | 最强、最抗杀后台，但存在感强，会显示闹钟图标 |

建议默认 L2；用户在设置里可以升到 L3；权限被拒 → 自动降级 L1，并在首页顶部显示一条"提醒未开启"的提示。这样"复杂/不可靠"的风险就被产品设计兜住了。

### 1.2 为什么不用常驻后台

- `AlarmManager` 的闹钟条目存在**系统**里，到点由系统发广播把 App 唤醒几十毫秒，发完通知进程就可以被回收。
- 对比"轮询"方案（很多电池 App 的做法：每 15 分钟查一次电量），需要常驻进程/前台服务，耗电、还会被国产 ROM 杀掉。
- `WorkManager` 不适合这个场景：最小周期 15 分钟，且**不保证准点**，只适合刷新小组件。
- Doze（打盹模式）下 `setExactAndAllowWhileIdle()` 仍会触发，只是有频率限制（约每 9 分钟一次/应用），对"一天几次"的提醒完全无影响。

### 1.3 必须处理的六个坑（决定"到底靠不靠谱"）

| 坑 | 表现 | 处理方式 |
|---|---|---|
| **精确闹钟权限** | Android 12+ 需要 `SCHEDULE_EXACT_ALARM`；Android 14+ 新安装应用默认**不授予** | 首次进入引导跳 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`；设置页常驻"权限自检"；被拒就降级 L1 |
| **通知权限** | Android 13+ 需要 `POST_NOTIFICATIONS`，拒绝后通知静默失败 | 首次申请 + 被拒时首页提示 |
| **重启 / 改时间 / 改时区** | 重启后闹钟全部丢失；时区一变全部错位 | 监听 `BOOT_COMPLETED`、`TIME_SET`、`TIMEZONE_CHANGED`、`MY_PACKAGE_REPLACED` 全量重排 |
| **国产 ROM 后台管控** | 小米/红米、华为/荣耀、OPPO、vivo 会杀后台，闹钟不响 | 设置页给"自启动 + 省电策略：无限制"的机型指引；必要时用 L3 闹钟式 |
| **被"强行停止"** | 应用被 force stop 后所有闹钟失效，直到你手动打开一次 | 每次启动全量重排；首页显示"上次同步 xx:xx" |
| **通知渠道被关** | 用户单独关掉通知渠道，代码以为发了 | 设置页放"发一条测试通知"按钮 + 检查 `areNotificationsEnabled()` |

> 结论：功能本身简单，**难点全在权限引导和 ROM 适配上**。这也正是"拿成熟项目改"比"从零写"划算的原因——这些坑别人已经踩过并写好了兜底逻辑（reReminder 甚至内置了"可靠性自检"页面）。

---

## 2. 0 代码方案：先验证需求，再决定改不改

| 方案 | 怎么配 | 优点 | 缺点 |
|---|---|---|---|
| **Tasks.org**（开源 GPL-3，老牌任务应用） | 新建任务"给充电宝充电" → 到期日=上次充电日 → 重复"每 14 天" → **重复依据选"完成日期"** → 提醒 09:00 | 语义完全对：完成后自动从今天 +14 天；通知里能完成；有小组件 | 大而全，界面不是"充电"主题；部分外观/同步功能是 Pro |
| **MainTask**（F-Droid，GPL-3） | 建任务 → 间隔"每 14 天" → 提前 1 天提醒 → 通知时间 09:00 | 就是为"每 N 天"设计的；通知里有【完成】【推迟】；有小组件和快捷开关；5 套主题 + 动态取色 | 只有英/法文，没有中文 |
| **reReminder**（F-Droid，GPL-3） | 建"每 2 周"的循环提醒 | 精确闹钟最规范，设置里有可靠性自检 | 固定网格重复：到点就提醒，**不按"上次完成时间"顺延**，充早了/充晚了节奏不会跟着变 |
| **系统日历** | 日程"每 14 天重复"+ 提醒 | 不用装任何东西，系统闹钟最可靠 | 不会"从完成日算起"，改期要手动 |

**建议**：今晚先装 Tasks.org 配 2–3 台设备，跑一周。你会立刻知道"提前几天提醒""几点提醒""逾期了要不要每天催"这些参数该怎么定——这些结论比代码更值钱。

---

## 3. 可改造的开源基座：横向对比

| 项目 | 定位 | 技术栈 | 许可证 | 中文 | 现成能力 | 改造成本 | 社区 |
|---|---|---|---|---|---|---|---|
| **[MedTimer](https://github.com/Futsch1/medTimer)** | 吃药提醒 | Kotlin，XML + 逐步迁移 Compose，Hilt、Room、`app/core/feature` 模块化 | **MIT** | ✅ 有（Weblate 社区翻译） | 间隔/指定日提醒、周末模式、稍后提醒、补服、库存与断货提醒、CSV 导出、JSON 备份、图表、指纹锁、F-Droid + Play 双渠道 | 中（工程大，但功能全） | 647★，2026-11 仍在提交 |
| **[reReminder](https://github.com/ProfessorQuantumUniverse/reReminder)** | 通用循环提醒 | Kotlin 2.4、**纯 Jetpack Compose + M3**、单模块、JSON 存储 | GPL-3 | ❌ 英/德 | 多提醒、每 N 分钟~每 N 月、提前提醒、通知/闹钟两种模式、通知渠道自检、开机/时区/更新后重排、动态取色 | 低（代码短、结构清晰） | 新项目，更新活跃 |
| **[MainTask](https://codeberg.org/kapoue/MainTask)** | 周期保养任务 | 源码在 Codeberg（F-Droid 构建，现代 Material 3 外观） | GPL-3.0-or-later | ❌ 英/法 | 间隔/每周/每月、一次性任务、推迟/跳过、完成历史、月历、搜索、小组件、快捷设置磁贴、5 主题 + 动态取色、备份 | 低-中（语义最贴合） | F-Droid 4.3.0，2026-10 更新 |
| **[Loop Habit Tracker](https://github.com/iSoron/uhabits)** | 习惯打卡 | Kotlin/Java 混合，View 体系 | GPL-3 | ✅ | 打卡、频率（每周 N 次/隔天）、每习惯独立提醒、桌面小组件、CSV/SQLite 导出、极省电 | 高（UI 老旧、模型是"打卡"不是"倒计时"） | 10.3k★，最成熟 |
| **[Table Habit](https://github.com/FriesI23/mhabit)** | 习惯打卡 | **Flutter/Dart**、SQLite、Provider | **Apache-2.0** | ✅ | Material 3 + 动态取色、每习惯自定义颜色、提醒、成长曲线、WebDAV 同步、跨平台 | 中（要写 Dart；Android 单端不划算） | 1.7k★，更新活跃 |
| **[Tasks.org](https://github.com/tasks/tasks)** | 任务管理 | Kotlin Multiplatform，超大型工程 | GPL-3 | ✅ | 重复（含"从完成日算起"）、提醒、小组件、CalDAV 同步 | 极高（不适合当基座） | 老牌 Astrid 血脉 |

> 另外两个**只能当参考、不能当基座**的电池类项目：
> - [BatteryNotification](https://github.com/TylerWilliamson/BatteryNotification)：常驻通知显示电量/温度/充电状态（GPL-3）。抄它读 `BatteryManager` 的部分即可。
> - [Healthy Battery Charging](https://github.com/vbresan/HealthyBatteryCharging)：提醒你在 40%–80% 之间插拔充电器（MIT）。思路是"电量阈值"，不是"时间"。

### 3.1 逐个点评（帮你判断"改哪里"）

**MedTimer —— 综合最优，适合"我主要让 AI 改，自己负责编译测试"**
- 为什么好：MIT（改完可以不开源、可以上架）、有现成中文、模块化（`feature/reminders` 提醒引擎、`feature/ui` 界面、`core` 数据层），提醒/通知/重排/备份这些"脏活"全都写好了。
- 改哪里：把"药品"改成"设备"（字符串 + 图标）、把"剂量/库存"换成"上次充电时间/间隔天数"、首页换成卡片 + 进度环、设置里加"背景图"。
- 风险：工程偏大（含大量测试与截图脚本），首次构建慢；UI 是 View + 部分 Compose 混编。

**reReminder —— 最好读，适合"我想自己搞懂每一行"**
- 为什么好：单模块、纯 Compose、无数据库（一个 JSON 文件存 device-protected storage），闹钟逻辑写得非常规范：精确闹钟 + 开机/时区/应用更新后自愈重排 + 设置里的"可靠性自检"。
- 改哪里：数据模型加 `lastChargedAt`（现在的模型是"固定网格重复"，要改成"上次完成 + N 天"）；补中文；加背景图；加小组件。
- 风险：GPL-3（发 APK 给别人/上架要开源）；只有英/德文，中文得自己补（可以让 AI 批量翻译，工作量半天）。

**MainTask —— 语义最贴合，但栈未知、无中文**
- 为什么好：它本来就是"每 N 天/每周/每月"的周期任务，有【完成】【推迟】、完成历史、月历、小组件、5 套主题 + 动态取色、备份导入导出——你想要的"完整设置界面 + 流程"它都有。
- 风险：源码在 Codeberg（GitHub 上没有镜像），技术栈和代码质量要 clone 下来才能判断；只有英/法文；GPL-3。

**Loop —— 成熟但不好改**
- 模型是"打卡"（今天做了/没做），不是"倒计时"；频率是"每周几次/隔天"，不是"上次之后 N 天"。UI 是十年前的 Material 风格，换背景、重做首页都等于重写 UI 层。除非你只想要"稳定提醒"，否则别选。

**Table Habit —— 颜值高、许可证友好，但是 Flutter**
- Apache-2.0 最宽松、界面现代、有中文、代码质量好。但 Android 单端做 Flutter 会把包体和构建链搞复杂，而且它的提醒是"每习惯固定时间"，同样缺"上次完成后 N 天"。

---

## 4. 三条推荐路线

### 路线 A（推荐）：Fork MedTimer，只动三处
1. **文案/图标/包名**：药品 → 设备，剂量 → 上次充电时间，库存 → 可选（当"充电宝电量"用也行）。
2. **首页重做**：卡片流 + 进度环 + 一键【已充电】。
3. **设置加一项**：背景图（相册选图 + 模糊 + 暗化）。
> 提醒引擎、权限引导、备份、图表全部白拿。适合想两周内用上自己 App 的人。

### 路线 B：Fork reReminder，自己长肉
1. 数据模型加"上次完成时间 + 间隔天数"，调度改成"完成后再排下一次"。
2. 补中文（一次性）。
3. 首页换卡片、加背景。
> 代码量小、看得懂、改得动；代价是 GPL 和更多自己动手的部分。

### 路线 C：先不改代码，用 MainTask + 自己写一个"皮肤版"壳
> 不推荐：套壳体验差，且维护两套。

**不建议从零写**：`AlarmManager` + 权限 + 开机重排 + 通知动作 + 设置页 + 主题 + 备份，独立做完约 2–3 周，且 90% 是重复造轮子。

---

## 5. 改造工作量拆解（以 MedTimer 为例，含 AI 辅助）

| 任务 | 具体动作 | 预估 |
|---|---|---|
| 跑通构建 | Android Studio + JDK 21，`assembleDebug` 装到真机 | 0.5–1 天 |
| 品牌替换 | `applicationId`、应用名、图标、关于页、删 F-Droid 元数据 | 0.5 天 |
| 领域改造 | 药品→设备、剂量→上次充电、间隔天数（Room 迁移 + DAO + 仓库） | 1–2 天 |
| 中文与文案 | 去医疗化措辞、补设备模板（充电宝/手表/耳机/牙刷…） | 0.5–1 天 |
| 首页重做 | 卡片 + 进度环 + 【已充电】+ 空状态 | 1–2 天 |
| 背景/主题 | 相册选图、持久化 URI、模糊 + 暗化遮罩、动态取色 | 0.5–1 天 |
| 通知动作 | 【已充电】【推迟 3 天】→ 写库 + 重排 | 0.5–1 天 |
| 权限与自检 | 精确闹钟/通知/电池优化引导页 | 0.5 天 |
| 真机适配 | 小米/华为各测一遍，补机型指引 | 1 天 |
| 小组件（可选） | Glance 2×2 显示最近到期设备 | 1 天 |

**合计约 6–10 个工作日（业余两周）**，其中真正写业务逻辑的只有 2–3 天。

---

## 6. 目标 App 规格（可直接当需求文档）

### 6.1 信息架构

```
首页（设备卡片流）
 ├─ 设备详情（历史时间线 / 平均间隔 / 下次提醒）
 │    └─ 编辑（名称/图标/颜色/间隔/提醒时间/提前天数）
 ├─ 添加设备（预设模板：充电宝 / 手表 / 耳机 / 牙刷 / 剃须刀 / 相机 / 扫地机）
 └─ 设置
      ├─ 外观：主题模式 / 动态取色 / 背景（无·纯色·渐变·相册图片 + 模糊 + 暗化）
      ├─ 提醒：总开关 / 默认时间 / 提前天数 / 提醒强度(L2/L3) / 声音震动 / 免打扰时段
      ├─ 权限自检：通知 / 精确闹钟 / 电池优化 / 发测试通知
      ├─ 数据：导出 JSON / 导入 / 清空
      └─ 关于：版本 / 开源许可 / 隐私说明
```

### 6.2 首页草图

```
┌─────────────────────────────┐
│  我的设备              ⚙  ＋ │
│                             │
│ ┌─────────────────────────┐ │
│ │ 🔋 充电宝               │ │
│ │  ▓▓▓▓▓▓▓▓▓░░  12/14 天 │ │
│ │  还有 2 天 · 11/03      │ │
│ │                [已充电] │ │
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │ ⌚ 手表                 │ │
│ │  ▓▓▓▓▓▓▓▓▓▓▓  逾期 3 天│ │
│ │                [已充电] │ │
│ └─────────────────────────┘ │
│                             │
│   全部正常时展示插画/空状态  │
└─────────────────────────────┘
```

### 6.3 数据模型

```sql
devices(
  id, name, icon_key, color_argb,
  interval_days, remind_hour, remind_minute, advance_days,
  last_charged_at, snooze_until, enabled, sort_order
)
charges(id, device_id, charged_at, note)   -- 历史，用于"平均间隔"
settings(theme_mode, background_uri, background_blur, background_dim, dynamic_color, alarm_strength, ...)
```

### 6.4 调度逻辑（核心不到 20 行）

```kotlin
fun schedule(device: Device) {
    val due = device.lastChargedAt.plusDays(device.intervalDays.toLong())
        .atTime(device.remindHour, device.remindMinute)
        .minusDays(device.advanceDays.toLong())
    val pi = PendingIntent.getBroadcast(
        context, device.id, Intent(context, ReminderReceiver::class.java)
            .putExtra("deviceId", device.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP, max(due, now + 5_000).toEpochMilli(), pi
    )
}
```

`ReminderReceiver` 里：读库 → 发通知（带【已充电】【推迟 3 天】两个 action）→ 若开启"逾期每日提醒"，再排明天同一时间。
【已充电】→ 写一条 `charges` + 更新 `lastChargedAt` → 重新 `schedule()` → 取消通知。

### 6.5 背景图怎么做（这是"好看"的关键）

- 选图用 `ActivityResultContracts.PickVisualMedia`（**不需要存储权限**），拿 URI 后 `takePersistableUriPermission` 持久化。
- 铺底：`AsyncImage` 全屏 + 上面盖一层 `verticalGradient`（黑 40%→70%）保证文字可读。
- 可选模糊：Android 12+ 用 `Modifier.blur(24.dp)`。
- 主题色：Material 3 `dynamicColor`（Android 12+），或从背景图取主色生成配色。
- 卡片用半透明 + 大圆角 + 细描边（玻璃拟态），深色模式下自动换 scrim。

### 6.6 MVP 与 v2

| 版本 | 内容 |
|---|---|
| **MVP** | 设备 CRUD、间隔、精确闹钟、通知两按钮、首页倒计时、设置（主题/背景/提醒时间/权限自检）、JSON 备份 |
| **v2** | 桌面小组件、历史时间线与平均间隔、逾期图表、按电量提醒（手机自身 <20%）、多语言、指纹锁 |

---

## 7. 许可证与发布避坑

| 许可证 | 代表 | 你能做什么 | 必须做什么 |
|---|---|---|---|
| **MIT** | MedTimer | 闭源、改名、上架、商用都行 | 保留版权声明 |
| **Apache-2.0** | Table Habit | 同上，更明确 | 保留 LICENSE/NOTICE，标注修改 |
| **GPL-3.0** | Loop、reReminder、MainTask、Tasks.org、BatteryNotification | 自用随意；**分发**（发 APK 给朋友、上架）时衍生作品也必须 GPL-3.0 | 提供完整源码、保留许可证 |

其他注意：
- 换掉 `applicationId`、应用名、图标、截图；删掉原项目的 `fastlane/`、F-Droid 元数据，否则会跟原作者的发布渠道打架。
- 自用签名即可；上架要新建签名 + 隐私政策（即使不联网也要写"不收集数据"）。
- 从 2026/2027 起，Google 对认证设备上的侧载应用要求开发者验证；**自己 adb 安装不受影响**，发给别人装可能会遇到（[keepandroidopen.org](https://keepandroidopen.org/)）。
- 想上架 Google Play 的话，`USE_EXACT_ALARM` 权限只给闹钟/日历类应用，你的应用应使用 `SCHEDULE_EXACT_ALARM` 并在应用内引导用户授权。

---

## 8. 你自己搜索的入口 & 关键词

**入口**
- F-Droid 分类：[Time](https://f-droid.org/en/categories/time/)（提醒/闹钟都在这）、[Sports & Health](https://f-droid.org/en/categories/sports-health/)（习惯类）
- GitHub Topics：`android` + `reminder` / `habit-tracker` / `recurring-tasks` / `interval-reminder` / `maintenance`
- 关键词：`repeat after completion`、`interval reminder`、`exact alarm`、`AlarmManager`、`WorkManager`、`Glance widget`
- 中文关键词：周期提醒、间隔提醒、充电提醒、保养提醒、习惯打卡、开源安卓提醒

**评估一个项目能不能当基座（按顺序看）**
1. LICENSE（能不能闭源/上架）
2. 最近一次提交（>1 年没动就放弃）
3. 语言与 UI 体系（Kotlin + Compose 最好改；Java/XML 次之；Flutter/RN 要另学）
4. 是否已有：精确闹钟 + 开机重排 + 通知动作（这三样是"脏活"，有就省一周）
5. 是否已有中文（省半天翻译）
6. 模块大小（单模块 < 200 个文件最适合新手）
7. 有没有测试（有测试说明作者靠谱，改坏了能发现）

---

## 9. 下一步：需要你拍板的三件事

1. **你要"最快用上"还是"自己看得懂"？**
   - 最快用上 → 路线 A（MedTimer，MIT + 中文）
   - 看得懂 → 路线 B（reReminder，Compose 单模块）
2. **你之前写过 Android 吗？会不会用 Android Studio 编译？**（决定我把步骤拆多细，以及要不要先给你一份"环境搭建 + 首次构建"清单）
3. **只自己用，还是想发给别人/上架？**（决定许可证红线：GPL 项目只能自用或开源分发）

拍板后我可以直接开始：拉代码 → 改包名/图标 → 出第一版首页（卡片 + 进度环 + 换背景）→ 装到你手机上试。
