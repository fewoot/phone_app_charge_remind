# 构建与环境说明

> 面向第一次打开这个工程的人。内容主要来自 2026-11 真机跑通的过程记录，
> 完整踩坑清单见 [项目复盘](retrospective.md)。

## 环境要求

| 项 | 要求 | 备注 |
|---|---|---|
| Android Studio | 2026.2.2（Rabbit 2）或更新 | 本项目在 JDK 25 运行时下构建通过 |
| JDK | 25 | Studio 自带的 jbr 即可，不必单独装 |
| Android SDK Platform | **37.2**（Android 17.x） | 依赖要求 `minCompileSdk=37` |
| Android SDK Build-Tools | 随 Studio 安装 | — |
| Gradle | 9.7.1 | 由 `gradle/wrapper/gradle-wrapper.properties` 指定 |
| JVM 版本 | 25 | `gradle/gradle-daemon-jvm.properties` 固定；机器上没有时 Gradle 会自动下载 |
| 测试机 | Android 8.0（API 26）及以上 | 开发机为 Android 16 / API 36 |

## 打开工程

1. `File → Open`，选择仓库里的 **`android-app/`**（不是仓库根目录）
2. 等 Sync 完成。首次会下载 Gradle 发行版和 Maven 依赖
3. 连上真机，点 **Run**

工程没有附带 `gradle-wrapper.jar` / `gradlew`，Android Studio 的 Gradle 集成不需要它们，
直接 Sync 即可。需要命令行构建时见 [没有 gradlew 怎么办](#没有-gradlew-怎么办)。

## 版本一览

| 项 | 版本 | 说明 |
|---|---|---|
| AGP | 9.5.0 | 从 9.3.1 升级而来 |
| Gradle | 9.7.1 | 本地 zip 或镜像安装 |
| Kotlin | 2.4.20 | AGP 9 内置 Kotlin，不再单独 apply `kotlin-android` |
| Compose BOM | 2026.09.00 | 实际解析到 Compose 1.12.1 |
| compileSdk | 37 + minor 2 | `android.compileSdk` + `compileSdkMinor` |
| targetSdk / minSdk | 36 / 26 | 行为面向 Android 16 |
| JVM target | 17 | `compileOptions` + `kotlin.compilerOptions` |

版本统一写在 `gradle/libs.versions.toml`。**不要凭记忆猜版本**：
这份组合是照着同代、活跃、单模块的开源项目（reReminder）抄下来再替换的。

## Gradle 下载慢或失败

### 方案 A：镜像（默认已配）

`gradle/wrapper/gradle-wrapper.properties` 默认指向腾讯镜像：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-9.7.1-bin.zip
```

海外网络可换成官方地址：

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip
```

### 方案 B：本地 zip 离线安装

如果公司网络 / 国内网络下镜像也慢：

1. 下载 `gradle-9.7.1-bin.zip`（官方 **151,433,392 字节**）
   - 腾讯镜像：<https://mirrors.cloud.tencent.com/gradle/gradle-9.7.1-bin.zip>
   - 官方：<https://services.gradle.org/distributions/gradle-9.7.1-bin.zip>
   - SHA-256：`acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`
2. 放到 `android-app/` 根目录，运行校验脚本：

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\verify-gradle.ps1
   ```

   看到绿色 `OK：这个 zip 是完整的` 再继续。**「能解压」不等于「完整」**：
   坏包也能通过 CRC，但会缺 `bin/` 和 `lib/gradle-launcher-*.jar`。
3. 把 `gradle-wrapper.properties` 的 `distributionUrl` 临时改成：

   ```properties
   distributionUrl=file:///D:/你的路径/android-app/gradle-9.7.1-bin.zip
   validateDistributionUrl=false
   ```

4. Sync。

> 这个本地路径只适合自己机器，**不要提交**（`.gitignore` 已忽略 `android-app/gradle-*.zip`）。
> 提交前把 `distributionUrl` 改回镜像地址。

### 方案 C：让 Studio 直接用已解压的 Gradle

`File → Settings → Build, Execution, Deployment → Build Tools → Gradle`，
把 **Gradle distribution** 改成 **Specified location**，指向解压出来的 `gradle-9.7.1` 文件夹
（里面应有 `bin/`、`lib/`）。

## 没有 gradlew 怎么办

仓库跟着 Android Studio 的用法走，没有提交 `gradle-wrapper.jar`、`gradlew`、`gradlew.bat`。
如果要用命令行构建，任选一种方式生成：

- **在 Studio 里**：Gradle 工具窗口 → `Tasks → build setup → wrapper`，运行一次即可
- **用已安装的 Gradle**：

  ```bash
  gradle wrapper --gradle-version 9.7.1 --distribution-type bin
  ```

生成后这几份文件可以正常提交到仓库。

## SDK 平台与 compileSdk

Android 17 的 SDK 有 `37.0`、`37.1`、`37.2` 三个小版本，**没有不带后缀的 37**，
所以 `app/build.gradle.kts` 里同时设置了：

```kotlin
compileSdk = 37
compileSdkMinor = 2
```

如果 Sync 报 `Failed to find target with hash string 'android-37.2'` 或提示缺平台：

1. 打开 SDK Manager → SDK Platforms → 安装 Android 17.x 对应版本；或
2. 把 `gradle/libs.versions.toml` 里的 `compileSdkMinor` 改成机器上已有的小版本

常见报错 `Dependency ... requires libraries and applications that depend on it to compile against
version 37 or later` 的正确解法是**升 compileSdk**，不是降一堆依赖。

## 国内网络：Maven 镜像

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

镜像 404 时会自动回落到官方源。如果某个依赖一直提示找不到，
先把这几行阿里云镜像注释掉再 Sync，确认是不是镜像缺包。

## 首次运行：权限

| 权限 | 什么时候要 | 不给的后果 |
|---|---|---|
| 通知 `POST_NOTIFICATIONS` | Android 13+ 首次启动 | 通知静默失败 |
| 精确闹钟 `SCHEDULE_EXACT_ALARM` | Android 12+；14+ 新装默认不授予 | 退化为不精确闹钟，可能延迟 |
| 忽略电池优化 | 需要在设置页手动开 | 国产 ROM 可能杀后台、闹钟不响 |
| 开机广播 `RECEIVE_BOOT_COMPLETED` | 重启后重排提醒 | 重启后提醒消失 |

设置页有「权限自检」和「发送一条测试通知」，可以逐项确认。

国产 ROM 额外步骤：在系统设置里允许**自启动**，省电策略设为**无限制**
（小米 / 红米、华为 / 荣耀、OPPO、vivo 的入口名称略有差别）。

## 2 分钟验证提醒链路

- 间隔设 `1 天`
- 上次充电日期选 `昨天`
- 提醒时间设成「当前时间 + 2 分钟」
- 保存 → 等通知 → 在通知上点【已充电】→ 首页应立刻变成「还有 N 天」

把提醒时间设成 5 分钟后重启手机，可以顺便验证开机重排。

## 编译失败排查顺序

1. **先看是哪个 task 红**：configuration → 依赖解析 → AAR metadata → Kotlin → 资源 → 打包
2. `AAR metadata` 报 `compileSdk` → 升 compileSdk，不要降依赖
3. `Unresolved reference` → 漏 import / 依赖没加 / 版本不对
4. 下载超时 → 本地化或换镜像，别反复重试
5. `Could not install Gradle distribution ... Read timed out` → 见上面的 Gradle 方案
6. 贴报错要**整段**（带文件、行号、task 名），只贴第一行等于没贴

## 数据与备份

- 应用数据：`/data/data/com.example.chargereminder/files/data.json`
- 照片：`/data/data/com.example.chargereminder/files/photos/`
- 设置页可导出 / 导入 JSON；**导出不含照片**
- 卸载会丢数据，换机前先导出

## 常用命令

```powershell
# 校验 Gradle 离线包
powershell -ExecutionPolicy Bypass -File .\verify-gradle.ps1

# 看设备有没有连上
adb devices

# 装 debug 包
adb install -r .\app\build\outputs\apk\debug\app-debug.apk

# 看 SDK 里装了哪些平台（路径换成你自己的 SDK 目录）
Get-ChildItem "$env:ANDROID_HOME\platforms" -Directory | Select-Object Name
```
