# android-app · Android 工程

「充电提醒」的原生 Android 实现：单 Activity + Jetpack Compose，纯本地存储，不联网。

- 项目介绍、功能与截图：[仓库首页](../README.md)
- 构建、环境、权限与排错：[docs/build.md](../docs/build.md)
- 改动前的 HTML 原型：[prototype/](../prototype/README.md)

## 环境要求

| 项 | 版本 |
|---|---|
| Android Studio | 2026.2.2（Rabbit 2）及以上，JDK 25 |
| AGP | 9.5.0 |
| Gradle | 9.7.1 |
| Kotlin | 2.4.20（AGP 9 内置，不再单独 apply `kotlin-android`） |
| Compose BOM | 2026.09.00（实际解析到 Compose 1.12.1） |
| compileSdk | 37 + minor 2 |
| minSdk / targetSdk | 26 / 36 |
| JVM target | 17 |

## 打开与运行

1. Android Studio → `File → Open`，选择**本目录**（`android-app/`）
2. 等 Gradle Sync 完成（首次会下载依赖，已配阿里云 Maven 镜像）
3. 连真机 → **Run**

> 本工程**没有**附带 `gradle-wrapper.jar` / `gradlew`，Android Studio 的 Gradle 集成不需要它们；
> 需要命令行构建时的做法见 [docs/build.md](../docs/build.md#没有-gradlew-怎么办)。
>
> `gradle/wrapper/gradle-wrapper.properties` 默认指向腾讯镜像；海外网络可改成官方地址
> `https://services.gradle.org/distributions/gradle-9.7.1-bin.zip`。

## 第一次 Sync 可能遇到的问题

| 现象 | 处理 |
|---|---|
| 提示缺 **Android SDK Platform 37.2** | SDK Manager 里安装 Android 17.x；或把 `gradle/libs.versions.toml` 的 `compileSdkMinor` 改成机器上已有的小版本（0 / 1 / 2） |
| Gradle 下载超时 / 卡住 | 用本地离线 zip，见 [构建说明](../docs/build.md#gradle-下载慢或失败) |
| 某个依赖提示找不到 | 把 `settings.gradle.kts` 里三行 `maven("https://maven.aliyun.com/...")` 注释掉再 Sync |
| `AAR metadata` 要求更高 compileSdk | 升 `compileSdk`，不要降依赖版本 |

## 代码结构

```text
app/src/main/java/com/example/chargereminder/
├── ChargeApp.kt                  Application：初始化数据、建通知渠道、全量排闹钟
├── MainActivity.kt               单 Activity + Compose
├── data/
│   ├── Models.kt                 Device / ChargeRecord / Settings / AppData（kotlinx.serialization）
│   ├── DeviceCalc.kt             间隔、到期日、剩余天数、进度、状态、自适应算法
│   └── Repo.kt                   单例仓库：JSON 持久化 + 增删改 + 导入导出
├── alarm/
│   ├── Reminders.kt              计算下次触发时间 + 排 / 取消精确闹钟
│   ├── ReminderReceiver.kt       到点发通知；处理「已充电 / 推迟 3 天」
│   └── BootReceiver.kt           开机 / 改时间 / 改时区 / 应用更新后重排
├── notify/Notifications.kt       通知渠道、通知内容、通知按钮
├── util/
│   ├── Images.kt                 图片压缩保存 / 读取
│   └── Format.kt                 日期时间中文格式化
└── ui/
    ├── Theme.kt                  配色、深浅色、动态取色
    ├── Components.kt             背景、进度环、开关、按钮、分段控件等
    ├── ChargeReminderApp.kt      根组合 + 页面切换 + 返回栈
    ├── HomeScreen.kt             首页
    ├── DetailScreen.kt           详情 + 间隔趋势图
    ├── EditorScreen.kt           添加 / 编辑
    ├── SettingsScreen.kt         设置
    └── ChargeSheet.kt            记录充电底部弹层
```

## 数据与构建产物

- 应用数据：`/data/data/com.example.chargereminder/files/data.json`
- 设备照片：`/data/data/com.example.chargereminder/files/photos/`
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- `local.properties`（SDK 路径）、`.idea/`、`build/`、`.gradle/`、`.kotlin/`
  以及根目录的 Gradle 离线 zip 都不会提交，规则见 [`.gitignore`](../.gitignore)

## 常见命令

```powershell
# 看设备有没有连上
adb devices

# 装 debug 包（Studio 构建后）
adb install -r .\app\build\outputs\apk\debug\app-debug.apk

# 看当前 compileSdk 配置
Select-String -Path .\gradle\libs.versions.toml -Pattern 'compileSdk'
```

## 许可

[MIT](../LICENSE)
