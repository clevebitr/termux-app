# Fork 构建指南：自编译 Termux + 自编译插件

> 本分支**新增的功能与行为**见 [`FORK.md`](FORK.md)；本文只讲怎么编译、签名、安装，
> 以及为什么自编译的 App 必须和插件用同一把签名。

本文档说明「自己 fork 并编译 Termux 后，能否继续使用官方编译的插件」这个问题的结论、
原因，以及可行的操作流程。文档里引用的文件/行号基于本仓库当前代码。

---

## 1. 结论（TL;DR）

**不能。** 你自己签名的 `com.termux` 和官方签名的插件（Termux:API、:Boot、:Float、
:Styling、:Tasker、:Widget）无法共存，插件会安装失败（`INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`，
用户看到的提示是 "App not installed"）。

要么：

* **方案 A（最快）**：主 App 和你要用的所有插件都用各仓库自带的 `testkey_untrusted.jks`
  编译 **debug** 版本。已验证 7 个仓库里的这个 keystore 是同一个文件（git blob
  `174cc95acd605bf557f9e63983f55fadc5ae44bd`），所以签名天然一致。缺点：该密钥是公开的，
  任何恶意 App 都可以用它签名并和你的 Termux 共享 UID，**只适合本地自用/测试**。
* **方案 B（推荐）**：生成**你自己的** keystore，主 App 与所有插件都用它签 release 包。
  本仓库的 `app/build.gradle` 已支持从根目录的 `signing.properties` 读取配置，插件仓库需按
  §4.2 的片段补一段（或用同一条命令统一签名）。

---

## 2. 为什么不能混用（硬约束）

1. **sharedUserId**：主 App 声明 `android:sharedUserId="com.termux"`
   （`app/src/main/AndroidManifest.xml:5`），6 个插件也全部声明了
   `android:sharedUserId="com.termux"`（各自的 `app/src/main/AndroidManifest.xml`）。
   Android 在**安装时**就强制要求同一 `sharedUserId` 的已安装包签名一致，否则拒绝安装。
2. **signature 级权限**：Termux:API 声明了
   `<permission android:name="com.termux.sharedfiles.READ_WRITE" android:protectionLevel="signature"/>`
   （`share`/SAF 那套 ContentProvider 的权限），只有同证书的应用才能拿到。
   Termux:API 的 README 原文：*"this app needs to be signed with the same key as the main Termux
   app for permissions to work (only the main Termux app are allowed to call the API methods in
   this app)"*。
3. **运行时检查**：`TermuxUtils.isTermuxAppAccessible()`（`createPackageContext` + 校验 `$PREFIX`
   权限）、`TermuxUtils.getTermuxAppAPKClassField()`（跨 APK 读 `BuildConfig`）都依赖
   shared UID / 同证书；APK 发行版也是靠签名摘要判定的
   （`termux-shared/.../TermuxConstants.java:482` 起的
   `APK_RELEASE_*_SIGNING_CERTIFICATE_SHA256_DIGEST`），自编译只会显示 `Unknown`。

**推论**：不要改包名（如 `com.termux.fork`）。包名一旦改变，插件、`$PREFIX` 路径
（`/data/data/com.termux/files`）、bootstrap、termux-packages 全都要跟着改，等于自建整套生态。
保留 `com.termux` + 统一签名是唯一低成本路径。

另外：`sharedUserId` 在 Android 10+ 已标记废弃，但仍然强制生效；每次更换签名都必须先把主 App
**和所有插件**全部卸载（同包名不同签名不能覆盖升级）。

---

## 3. 方案 A：全部用仓库自带 testkey 编 debug（最快）

```sh
# 主 App
./gradlew assembleDebug          # 产物 app/build/outputs/apk/debug/*.apk

# 每个要用的插件仓库里各执行一次
./gradlew assembleDebug
```

插件仓库列表：

| 插件 | 仓库 |
| --- | --- |
| Termux:API | https://github.com/termux/termux-api |
| Termux:Boot | https://github.com/termux/termux-boot |
| Termux:Float | https://github.com/termux/termux-float |
| Termux:Styling | https://github.com/termux/termux-styling |
| Termux:Tasker | https://github.com/termux/termux-tasker |
| Termux:Widget | https://github.com/termux/termux-widget |

注意：

* 主 App 编译需要 Android SDK + NDK（版本见 `gradle.properties` 的 `ndkVersion`），
  以及 `task downloadBootstraps` 下载的 bootstrap（需联网，见 `app/build.gradle` 末尾）。
* 插件仓库用 Java 11，主 App 用 Java 8（见各自 `compileOptions`）。
* `termux-api` 这个 **apt 包**（`pkg install termux-api`）也要装，它提供 `termux-api-*` 脚本
  和与插件 APK 通信的 socket 客户端；APK 和 apt 包缺一不可。
* 验证签名一致：

  ```sh
  apksigner verify --print-certs <apk>   # 对比所有 APK 的 SHA-256 digest 是否相同
  ```

---

## 4. 方案 B：用你自己的 keystore 签 release（推荐）

### 4.1 生成 keystore

```sh
keytool -genkeypair -v -keystore ~/termux-fork.jks -alias termuxfork \
    -keyalg RSA -keysize 2048 -validity 10000
```

### 4.2 配置主 App

在**仓库根目录**创建 `signing.properties`（该文件已在 `.gitignore` 中，不会提交）：

```properties
storeFile=/absolute/path/to/termux-fork.jks
storePassword=你的store密码
keyAlias=termuxfork
keyPassword=你的key密码
```

存在该文件时，`app/build.gradle` 会自动为 `release` 构建类型套用 `signingConfigs.release`；
不存在时行为与上游一致（release 不签名，交给 F-Droid / 你自己的流程签）。

```sh
TERMUX_APK_VERSION_TAG=my-fork-$(date +%Y%m%d) ./gradlew assembleRelease
```

### 4.3 配置每个插件仓库

插件仓库目前只有 debug 的 `testkey_untrusted.jks`，release 没有签名配置。在每个插件仓库根目录
也放一份同样的 `signing.properties`，并在其 `app/build.gradle` 里加同样的判断（放在
`android { }` 之前）：

```gradle
def signingPropertiesFile = rootProject.file("signing.properties")
def signingProperties = new Properties()
if (signingPropertiesFile.exists()) {
    signingPropertiesFile.withInputStream { signingProperties.load(it) }
}
def hasCustomSigningConfig = signingProperties.getProperty("storeFile") != null
```

然后把该仓库的 `signingConfigs { debug { ... } }` 和 `buildTypes.release { ... }` 改成：

```gradle
    signingConfigs {
        debug {
            storeFile file('testkey_untrusted.jks')
            keyAlias 'alias'
            storePassword 'xrj45yWGLbsO7W0v'
            keyPassword 'xrj45yWGLbsO7W0v'
        }

        if (hasCustomSigningConfig) {
            release {
                storeFile rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword signingProperties.getProperty("storePassword")
                keyAlias signingProperties.getProperty("keyAlias")
                keyPassword signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            minifyEnabled true
            shrinkResources false
            proguardFiles getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro"
            if (hasCustomSigningConfig) signingConfig signingConfigs.release
        }
    }
```

### 4.4 卸载 / 备份 / 恢复

**先确认你当前装的是哪个发行版**，可以免卸载直接升级：

| 当前 Termux 来源 | 签名证书 SHA-256 | 能否直接覆盖安装本 fork |
| --- | --- | --- |
| GitHub Release / GitHub Actions 构建 | `b6da01480eefd5fbf2cd3771b8d1021ec791304bdd6c4bf41d3faabad48ee5e1` | 可以（`testkey` 模式产物同签名，数据保留） |
| F-Droid | `228fb2cfe90831c1499ec3ccaf61e96e8e1ce70766b9474672ce427334d41c42` | 不行，必须先卸载（先备份） |
| Google Play | `738f0a30a04d3c8a1be304af18d0779bcf3ea88fb60808f657a3521861c2ebf9` | 不行，必须先卸载（先备份） |
| 自己之前的 fork | `testkey` 模式下是 `b6da0148…`；`ownkey` 模式下看你自己的证书 | 同一证书可以，否则先卸载 |

查当前签名：在已装的 Termux 里打开左侧抽屉 → **Report Issue**（或 About），App Info 里会列出
`SIGNING_CERTIFICATE_SHA256_DIGEST`。

同包名换签名必须**先卸载**官方 Termux 和所有官方插件，这会删除应用数据（也就是整个
`/data/data/com.termux/files` = `$PREFIX` + `$HOME`），所以务必先备份：

```sh
# 1) 备份（在官方 Termux 里执行，需要已授予存储权限）
termux-setup-storage
tar -czf /sdcard/termux-backup-$(date +%Y%m%d).tar.gz \
    -C /data/data/com.termux/files ./home ./usr
# 可选：记录已安装包列表
dpkg --get-selections > /sdcard/termux-packages.txt

# 2) 卸载官方 main app 和所有插件（系统设置里逐个卸载，或用 adb uninstall）

# 3) 安装你的 APK：主 App 先装，再装插件

# 4) 恢复数据（在**新**的 Termux 里执行；新 App 会重新生成 bootstrap）
tar -xzf /sdcard/termux-backup-<日期>.tar.gz -C /data/data/com.termux/files
# 恢复后常见收尾
termux-fix-shebang ~/bin/* 2>/dev/null || true
```

注意：

* 备份里的 `./usr` 与本机 bootstrap 版本必须兼容；如果新 App 的 bootstrap 版本更旧/更新，
  恢复后建议 `pkg upgrade`。
* 恢复后 App 的 `sharedUserId` 未变（仍是 `com.termux`），所以 apt 状态、`$PREFIX` 路径都还能用。
* 卸载插件时不要把 `com.termux.api` 留给新签名的主 App 去"覆盖安装"，那样只会得到
  `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE`。

---

## 5. 用 CI 构建（推荐，无需本地 SDK/NDK）

仓库里的 `.github/workflows/fork_build.yml`（Fork Build (signed APKs)）会一次性构建：

* Termux 主 App
* Termux:API / :Boot / :Float / :Styling / :Tasker / :Widget 六个插件（从 `termux/<repo>@master` 克隆后构建）

并**校验每个 APK 的签名证书 SHA-256 完全一致**，不一致直接 fail。构建产物会打包成
`fork-latest` 这个预发布 Release，方便在手机上直接下载安装。

触发方式：

* push 到 `master`（只改了 `app/**`、`terminal-view/**`、`termux-shared/**`、`terminal-emulator/**`、
  `*.gradle`、`gradle.properties`、`gradle/wrapper/gradle-wrapper.properties` 或该 workflow 时）
* 手动：`gh workflow run "Fork Build (signed APKs)" -f build_mode=testkey -f build_plugins=true`
  或在 Actions 页面点 Run workflow

两种模式：

| 模式 | 条件 | 产物 | 用途 |
| --- | --- | --- | --- |
| `testkey` | 默认（没配 secrets） | 全部 `assembleDebug`，用各仓库内置的 `testkey_untrusted.jks` 签名 | 本地自测；签名证书 SHA-256 为 `b6da0148…e5e1`（与 Termux GitHub 官方发行版同一把公开密钥） |
| `ownkey` | 配了 `TERMUX_KEYSTORE_BASE64` 等 secrets | 主 App `assembleRelease` + 插件 `assembleRelease`，用你的 keystore 签名 | 长期自用/分发 |

### ownkey 模式需要的 secrets

```sh
gh secret set TERMUX_KEYSTORE_BASE64   --repo <owner>/termux-app < <(base64 -w0 termux-fork.jks)
gh secret set TERMUX_KEYSTORE_PASSWORD --repo <owner>/termux-app
gh secret set TERMUX_KEY_ALIAS         --repo <owner>/termux-app
gh secret set TERMUX_KEY_PASSWORD      --repo <owner>/termux-app
```

* `TERMUX_KEYSTORE_BASE64`：keystore 文件的 base64（单行）
* 主 App 走 `signing.properties`，插件仓库不改动——通过 AGP 的
  `-Pandroid.injected.signing.*` 注入签名；如果某个仓库的 AGP 不认这套属性，产物会因未签名而
  在校验步骤直接报错（不会默默出个坏包）
* Actions 需要写权限才能建 Release：Settings → Actions → General → Workflow permissions 设为
  `Read and write permissions`（`gh api -X PUT repos/<owner>/termux-app/actions/permissions/workflow -f default_workflow_permissions=write`）

### 安装顺序

1. 先卸载官方 Termux **和所有官方插件**（签名不同不能共存），卸载前备份 `$PREFIX`（见 §4.4）
2. 装主 App，再装插件
3. `termux-api` 这个 apt 包照常在 Termux 里 `pkg install termux-api`

---

## 6. 在 Termux 里本机构建（可行，比 CI 快）

> 以前本地没法编译（没有 JDK / SDK）；现在的 Termux 里 `openjdk-21` + `android-sdk`
> （`$PREFIX/opt/android-sdk`，含 platforms 34/35 与 build-tools 35.0.0）都装好了，
> Gradle wrapper 也能正常下载运行，可以只在本地做编译/单测验证，不必等 CI。

```sh
cd ~/termux-app
export ANDROID_HOME=$PREFIX/opt/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME

# 编译主 App（含 terminal-view / terminal-shared / terminal-emulator）
./gradlew :app:compileDebugJavaWithJavac \
  -x downloadBootstraps \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2

# 单测
./gradlew test -x downloadBootstraps \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2
```

两个参数的原因：

* `-x downloadBootstraps`：`app/build.gradle` 把 `downloadBootstraps` 挂在 `javaCompile` 上，
  它会从 GitHub 下 4 个 bootstrap zip 塞进 assets。编译/单测不需要它，网络差时它会让整个
  task graph 直接失败（`SocketException: Unexpected end of file`）。
* `-Pandroid.aapt2FromMavenOverride=...`：Maven 上的 aapt2 只有 linux-x86_64 版，在 arm64
  Termux 上会报 `AAPT2 aapt2-8.x Daemon #0: Daemon startup failed`。换成本机 build-tools
  里的 aapt2 即可（AGP 会警告版本不一致，但能正常出包）。

第一次运行会下载 AGP/依赖并自动安装 SDK Platform 36（约 8 分钟），之后增量编译 15–60 秒。
已知：`app:testDebugUnitTest` 里的 `FileReceiverActivityTest` 在本机会因为 Robolectric 的
`ShadowActivityThread.reset: ActivityThread not set` 在 teardown 阶段 NPE 失败（与本仓库改动无关，
单独跑该测试同样失败），CI 上正常；其余单测全绿。

`assembleDebug` 需要 bootstrap zip（`-x` 掉也能出包，但首次启动没有环境可装），CI 的
`fork-latest` Release 仍然是最省事的出包方式。

### 在本机出一个能装的 APK（arm64）

`assembleDebug` 除了 Java 编译，还要把 bootstrap zip **嵌进 `libtermux-bootstrap.so`**
（`app/src/main/cpp/termux-bootstrap-zip.S` 里 `.incbin`），所以需要：

1. 当前 ABI 的 bootstrap zip（`downloadBootstraps` 会一次下 4 个，网络差时很容易失败；
   只想给自己手机装的话，手下一个就够，注意校验 sha256）：

```sh
cd ~/termux-app
curl -L --retry 3 -o app/src/main/cpp/bootstrap-aarch64.zip \
  "https://github.com/termux/termux-packages/releases/download/bootstrap-2026.02.12-r1%2Bapt.android-7/bootstrap-aarch64.zip"
sha256sum app/src/main/cpp/bootstrap-aarch64.zip
# 必须是 ea2aeba8819e517db711f8c32369e89e7c52cee73e07930ff91185e1ab93f4f3（aarch64 / apt-android-7）
```

2. **能跑的 NDK**：Gradle 自动下的 NDK r29 只有 `linux-x86_64` 工具链，在 aarch64 的 Termux 上
   `ndk-build` 直接报 `ERROR: Unknown host CPU architecture: aarch64`。Termux 的 `android-ndk`
   包（r27b）带 `linux-aarch64` 预编译工具链，把它挂到 SDK 里再用 `-PndkVersion` 指过去即可：

```sh
ln -sfn $PREFIX/opt/android-ndk $PREFIX/opt/android-sdk/ndk/27.1.12297006
```

3. 只构建当前 ABI（否则另外 3 个 ABI 会因为缺 zip 而失败）：

```sh
export ANDROID_HOME=$PREFIX/opt/android-sdk
./gradlew :app:assembleDebug -x downloadBootstraps \
  -PndkVersion=27.1.12297006 \
  -Pandroid.injected.build.abi=arm64-v8a \
  -Pandroid.injected.testOnly=false \
  -Pandroid.aapt2FromMavenOverride=$ANDROID_HOME/build-tools/35.0.0/aapt2
```

`-Pandroid.injected.testOnly=false` 必须加：一旦用了任何 `android.injected.*`（如上面的 ABI），
AGP 会把 debug 包标成 `android:testOnly="true"`，系统安装器会拒绝并提示
**「该安装包是测试版本，请联系开发者」**。可以用以下命令确认（没有 testOnly 那一行才对）：

```sh
$ANDROID_HOME/build-tools/35.0.0/aapt2 dump xmltree --file AndroidManifest.xml \
  app/build/intermediates/apk/debug/termux-app_apt-android-7-debug_arm64-v8a.apk | grep testOnly
```

产物（注意：用了 `android.injected.build.abi` 时不会落到 `app/build/outputs/apk/`，而在）：

```
app/build/intermediates/apk/debug/termux-app_apt-android-7-debug_arm64-v8a.apk
```

签名是仓库里的 `testkey_untrusted.jks`（SHA-256 `b6da0148…e5e1`，和官方 GitHub 版同一把公开密钥），
所以能直接覆盖安装官方 GitHub 版或本 fork 的 testkey CI 包；F-Droid/Play 签名版装不上（要先卸载，
记得先备份，见 §4.4）。**装机会杀掉正在运行的 Termux 进程**，在文件管理器里点安装或
`adb install -r` 都行，但别在需要保留当前会话时从 App 内部装。
