# 发布与分发说明

> 目标：把「充电提醒」以**已签名的 APK** 发到 GitHub Releases，
> 自己装、发给朋友装。上 Google Play 是另一条路，见最后一节。

## 0. 先分清三个概念

| 概念 | 说明 |
|---|---|
| **Tag** | 指向某个提交的版本标记，例如 `v1.0.0` |
| **Release** | GitHub 上的一条发布记录：标题 + 说明 + 附带的 APK 文件 |
| **签名** | Android 用证书证明「这个 APK 是谁发的」；**升级必须同一个签名** |

发一次版 = 改版本号 → 打签名包 → 建 Release → 上传 APK。下面的顺序就是实际操作顺序。

## 1. 一次性准备：创建签名密钥

### 为什么不能一直用 debug 包

- debug 包用 `~/.android/debug.keystore` 签名，证书是临时性质的，换台电脑就变
- 正式版必须用自己的 keystore，否则以后无法覆盖升级
- 现在的 `app/build.gradle.kts` 里 `release` **没有配置签名**，
  直接 `assembleRelease` 得到的会是不能安装的 unsigned 包

### 用 Android Studio 创建（推荐）

1. `Build → Generate Signed App Bundle / APK…`
2. 选 **APK** → `Next`
3. `Key store path` → **Create new…**
4. 路径放在**仓库外面**，例如 `D:\keys\chargereminder.jks`
5. 填密码、`Key alias`（例如 `chargereminder`）、有效期（默认 25 年足够）
6. 记下 **keystore 密码** 和 **key 密码**（可以设成同一个）

### 或者用 keytool

```powershell
# keytool 在 JDK 的 bin 目录里（Studio 自带的 jbr\bin 也有）
keytool -genkeypair -v -keystore D:\keys\chargereminder.jks `
  -alias chargereminder -keyalg RSA -keysize 4096 -validity 10000
```

### 创建后必须做两件事

1. **备份 `.jks` 文件 + 两个密码**（网盘 / 加密 U 盘 / 密码管理器）。
   丢了就永远无法给已装的用户发升级包，只能换包名或让所有人卸载重装。
2. **确认没进 git**：

   ```powershell
   git status --short
   git check-ignore -v .\android-app\keystore.properties
   ```

   `.gitignore` 已忽略 `*.jks`、`*.keystore`、`keystore.properties`、`signing.properties`。

## 2. 每次发版的检查单

- [ ] 在 [`CHANGELOG.md`](../CHANGELOG.md) 增加本次版本段落（例如 `## [1.0.1] - 2026-11-xx`）
- [ ] `android-app/app/build.gradle.kts` 里 `versionCode` +1、`versionName` 改为新版本
- [ ] 真机回归（见第 5 节 5 条）
- [ ] 构建**已签名**的 release APK
- [ ] 校验签名、装到手机试一遍
- [ ] 算 APK 的 SHA-256
- [ ] 写 Release 正文（模板见 [`docs/releases/v1.0.0.md`](releases/v1.0.0.md)）
- [ ] 在 GitHub 建 Release、上传 APK
- [ ] 从 Release 页面重新下载一次，核对 SHA-256 并安装

## 3. 改版本号

`android-app/app/build.gradle.kts` 的 `defaultConfig`：

```kotlin
defaultConfig {
    applicationId = "com.example.chargereminder"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 2          // 必须是整数，每次发版 +1
    versionName = "1.0.1"    // 给人看的版本号，和 tag 保持一致
}
```

- `versionCode` 小了，手机不会覆盖安装（会提示「应用未安装」或更新失败）
- 当前仓库是 `versionCode = 1`、`versionName = "1.0"`；
  首发前建议把 `versionName` 改成 `"1.0.0"`，和 tag `v1.0.0` 对齐
- 版本号规则建议 `主.次.修订`：功能大改用主版本，平常加小功能用次版本，修 bug 用修订号

## 4. 构建签名 APK

### 方式 A：Android Studio（首选）

1. `Build → Generate Signed App Bundle / APK…`
2. 选 **APK**（不是 Android App Bundle）→ `Next`
3. 选第 1 步创建的 keystore，填密码和 alias
4. 选 **release**
5. 签名版本保持默认（V1 + V2 都勾）
6. `Finish`
7. 产物：向导里的 **Destination Folder** 下的 `app-release.apk`
   （默认是 `android-app/app/release/app-release.apk`）

> 这个对话框只影响这一次构建，不会改动 `build.gradle.kts`，适合偶尔发版。

### 方式 B：配置到工程里（可选，适合以后经常发版）

在 `android-app/` 下新建 `keystore.properties`（**不要提交**）：

```properties
storeFile=D:/keys/chargereminder.jks
storePassword=你的keystore密码
keyAlias=chargereminder
keyPassword=你的key密码
```

在 `android-app/app/build.gradle.kts` 的 `plugins { … }` 之后加：

```kotlin
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = java.util.Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
```

再在 `android { … }` 里加：

```kotlin
signingConfigs {
    if (keystorePropsFile.exists()) {
        create("release") {
            storeFile = file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }
}
```

然后把 `release` 构建类型补一行：

```kotlin
buildTypes {
    release {
        signingConfig = signingConfigs.findByName("release")
        isMinifyEnabled = false
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

没有 `keystore.properties` 时这段逻辑不会生效，不会影响别人编译。

配好后在 Android Studio 的 Gradle 面板运行 `app → Tasks → build → assembleRelease`，
产物在 `android-app/app/build/outputs/apk/release/app-release.apk`。
（仓库没带 wrapper；要用命令行 `gradlew` 先看 [构建说明](build.md#没有-gradlew-怎么办)。）

> 这段代码没有在当前的 AGP 9.5 上实际编译验证过；如果 Sync 报错就先回退，
> 用方式 A 出包。改完记得再跑一次真机。

## 5. 本机验证（别跳过）

### 校验签名

```powershell
# 按你实际的产物路径改：方式 A 一般在 app\release，方式 B 在 app\build\outputs\apk\release
$apk = ".\android-app\app\release\app-release.apk"

# build-tools 版本按自己 SDK 里装的改（本例是 37.0.0）
# ANDROID_HOME 没设置的话，这里换成 SDK 的完整路径
& "$env:ANDROID_HOME\build-tools\37.0.0\apksigner.bat" verify --print-certs $apk
```

看到 `CN=...` 的证书信息、没有 `ERROR` 即可；`--print-certs` 里的指纹要和你 keystore 对得上。

### ⚠️ 从 debug 版换到 release 版：先卸载

debug 包和 release 包的签名不同，**不能覆盖安装**。第一次切换时必须：

1. 打开 App → `设置 → 数据 → 导出 JSON`（先备份）
2. 卸载手机上的 debug 版
3. 安装 release APK
4. 用刚导出的 JSON 导入

注意：**导出不含照片**，自定义图标和背景图需要重新选。

### 装到手机

```powershell
adb install -r $apk
```

### 回归 5 条

对应 [需求清单的验收标准](requirements.md#11-验收标准改完我自己按这个测)：

1. 添加 3 台设备，首页能显示倒计时
2. 某台间隔改 0 天 → 1 分钟内收到通知 → 通知里点【已充电】→ 首页天数刷新
3. 换一张相册图片当背景 → 杀进程重开，背景还在
4. 重启手机 → 提醒还在（提醒时间设成 5 分钟后验证）
5. 关掉通知权限 → 首页出现黄条提示，且不崩溃

## 6. 发到 GitHub Releases

> **GitHub Desktop 不能创建 Release**，这一步用浏览器（或者 `gh` 命令），
> 代码的 commit / push 仍然用 GitHub Desktop。

### 步骤

1. 先把本次发版的代码提交并推送到 GitHub（GitHub Desktop → `Commit to main` → `Push origin`）
2. 浏览器打开：<https://github.com/fewoot/phone_app_charge_remind/releases>
3. 点 **Draft a new release**
4. **Choose a tag**：输入 `v1.0.0` → 选择 **Create new tag: v1.0.0 on publish**
5. **Target**：`main`
6. **Release title**：`v1.0.0 · 首个版本`
7. **Describe this release**：粘贴 Release 正文（模板见 [`docs/releases/v1.0.0.md`](releases/v1.0.0.md)）
8. **Attach binaries**：把 `app-release.apk` 拖进上传区，
   建议先改名成 `ChargeReminder-v1.0.0.apk`（纯 ASCII，避免个别客户端下载出问题）
9. 只是给朋友试装可以勾 **Set as a pre-release**；正式版不勾
10. 点 **Publish release**

### 发布后自检

- 退出登录（或用无痕窗口）打开 Release 页，确认 APK 能下载
- 下载后用 `Get-FileHash` 核对正文里的 SHA-256
- 在手机上安装下载到的文件，确认不是坏包
- 回仓库首页，右侧 **Releases** 应显示 `v1.0.0`

### 命令行替代（可选）

装了 GitHub CLI 的话，可以一条命令建 Release：

```powershell
gh release create v1.0.0 .\ChargeReminder-v1.0.0.apk `
  --title "v1.0.0 · 首个版本" `
  --notes-file .\docs\releases\v1.0.0.md `
  --target main
```

### 发错了怎么办

1. 在 Release 页面点 `Delete` 删掉这条 Release
2. 到 <https://github.com/fewoot/phone_app_charge_remind/tags> 删掉对应 tag
   （或命令行 `git push --delete origin v1.0.0`）
3. 重新发

> 已经下载的用户无法收回；`v1.0.0` 这种 tag 尽量一次发对。

## 7. Release 正文模板

完整示例见 [`docs/releases/v1.0.0.md`](releases/v1.0.0.md)。每次发版复制一份、改版本号和内容即可。
最少要包含：

- 这个版本新增 / 修复了什么（从 CHANGELOG 抄）
- 安装步骤（尤其是权限和国产 ROM 那几步）
- 支持的最低 Android 版本
- APK 的 SHA-256
- 从旧版本升级的注意事项（要不要卸载）

## 8. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 安装时提示「应用未安装」 | 与已装版本签名冲突 → 先卸载旧版；或 APK 下载不完整 |
| 覆盖安装失败 / 版本没更新 | `versionCode` 没加 |
| Play Protect 提示「未知来源应用」 | 侧载 APK 都会提示，选「仍要安装」；这是正常的 |
| 安装后通知不响 | 通知权限、精确闹钟、电池优化；国产 ROM 还要开自启动 |
| `apksigner` 报 `DOES NOT VERIFY` | 用的不是 release 签名包，或上传的是 debug 包 |
| keystore 丢了 | 无法覆盖升级，只能换 `applicationId` 或让用户卸载重装 |
| APK 太大（>30 MB） | 当前 `isMinifyEnabled = false`；可开 R8 + `shrinkResources`，但要重新回归 |
| Release 里传错文件 | 删除该 asset 重新上传（Release 可以编辑） |

## 9. 以后可以自动化

现在手动发版完全够用。等发版频繁了，可以加 GitHub Actions：

```text
打 tag v* → 跑 workflow → 构建 release APK → 自动创建 Release 并上传
```

需要提前准备：

- runner 上能安装 **Android SDK Platform 37.2**（GitHub 托管 runner 不一定自带，要显式装）
- keystore 转成 base64 存进仓库 Secrets，构建时写回临时文件；密码同样走 Secrets
- 注意 fork / PR 的 Secrets 保护，避免签名密钥泄露

自用项目不建议一开始就上 CI——出问题时本地手动出包更快。

## 10. 如果以后要上 Google Play

GitHub Releases 是侧载分发，和上架是两条路：

- 产物改成 **AAB**（Android App Bundle），不是 APK
- 需要 Play Console 账号、隐私政策（即使不联网也要写明「不收集数据」）
- 包名不能是 `com.example.*`，要改成自己的域名（换包名 = 新应用，老用户数据不迁移）
- 2026 起 Google 对认证设备的侧载应用有开发者验证要求；
  自己 `adb install` 不受影响，发给别人安装可能会遇到
  （参考 [keepandroidopen.org](https://keepandroidopen.org/)）
