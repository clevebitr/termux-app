# Fork 指南：自编译 Termux + 自编译插件

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

## 6. Gboard 相关配置（本次补丁附带的选项）

`~/.termux/termux.properties`（改完执行 `termux-reload-settings`）：

```properties
# 允许 Gboard 在终端视图里直接使用中文/日文/韩文键盘（本 fork 新增）
# 原理：终端视图默认用 InputType.TYPE_NULL，Gboard 会把键盘强制成英文 QWERTY 并隐藏语言切换键；
# 打开后改用 text class inputType，并监听 IME 语言切换（requestCursorUpdates）重启输入，
# 同时用 TYPE_TEXT_VARIATION_NORMAL 让 Gboard 显示候选词。
# 副作用：非 CJK 语言会退化为 visible-password 风格输入（无联想/候选），与
# enforce-char-based-input=true 类似；依赖 Gboard 的行为，其他输入法表现未充分验证。
gboard-cjk-workaround=true

# 部分三星键盘等需要字符级输入时打开
#enforce-char-based-input=true

# 某些 ROM 上 ctrl+space 切换输入法失效时的 workaround
#ctrl-space-workaround=true

# keyboard 开关按钮的行为：show/hide（默认）或 enable/disable
#soft-keyboard-toggle-behaviour=show/hide

# 建议在 extra-keys 里保留 KEYBOARD 键，方便在 TUI 开了鼠标跟踪、点击不弹键盘时手动唤起
#extra-keys=[['ESC','/','-','HOME','UP','END','PGUP','KEYBOARD'],['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]
```

排障要点：

* 点击不弹键盘：TUI 开了鼠标跟踪时设计上就不弹（`TermuxTerminalViewClient.onSingleTapUp`），
  用 `KEYBOARD` 键 / `Ctrl+Alt+K` 手动唤起；键盘处于 disabled 状态时同样点击无效。
* `soft_keyboard_enabled_only_if_no_hardware` + `KeyboardUtils.isHardKeyboardConnected()` 误报
  会导致键盘被禁用。
* 日志：`logcat -s TermuxTerminalViewClient:V TerminalView:V`（需在 Termux 设置里把日志级别调成
  verbose）。

---

## 7. 虚拟键盘（extra keys 行）配色：与 Termux:Styling 主题联动

> 先明确一下“虚拟键盘”：这里指的是 **Termux 自己画的那一行功能键**（`ExtraKeysView`，
> ESC/CTRL/ALT/HOME/方向键……，也就是常说的“额外按键行/虚拟键盘”）。系统输入法（Gboard、
> 搜狗等）的外观由输入法自己控制，第三方 App 无法改——能改的只有这一行。

### 联动规则（本 fork 新增）

`~/.termux/colors.properties` 里的主题色现在也会决定 extra keys 行的配色：

| extra keys 颜色 | colors.properties 里的键 | 未定义时的默认值 |
| --- | --- | --- |
| 文字色 | `extra-keys-foreground` | `foreground` |
| 背景色 | `extra-keys-background` | `background` |
| 按下/激活文字色（CTRL/ALT 等） | `extra-keys-active-foreground` | `cursor` |
| 按下/激活背景色 | `extra-keys-active-background` | `background` 混入 25% `foreground` |

所以在 **Termux:Styling 里换主题就会同时换掉键盘配色**（Styling 写的就是 colors.properties），
浅色主题不会再出现“白色终端 + 黑色键盘”的割裂感。主题里的 `foreground`/`background`/`cursor`
是所有官方主题都定义了的键（例如 `base16-3024-light` → 键盘变成 `#f7f7f7` 底 + `#4a4543` 字）。

### 自定义（优先级更高、且不会被 Styling 覆盖）

Styling 每次应用主题都会重写 `colors.properties`，所以想要固定/自定义的键盘配色，请写进
`~/.termux/termux.properties`（同名键，支持 `#RGB` / `#ARGB` / `#RRGGBB` / `#AARRGGBB`，
带 alpha 可以做半透明）：

```properties
extra-keys-foreground=#e0e0e0
extra-keys-background=#80000000        # 半透明黑，能看到后面的终端背景
extra-keys-active-foreground=#ff8800
extra-keys-active-background=#33ffffff
```

改完执行 `termux-reload-settings` 生效。

也可以在 `colors.properties` 里直接写这四个 `extra-keys-*` 键（格式同该文件的其他颜色：
`#rgb` / `#rrggbb` / `rgb:xx/xx/xx`，该文件不支持 alpha），这样它们会随主题一起切换；
只是 Termux:Styling 换主题时会被覆盖。

### 整条底部工具栏的背景也跟着走

`extra-keys-background` 不只影响按键，还会应用到承载它的整个底部工具栏条（`ViewPager`，
包括左滑出来的文本输入框那一页——它的背景原本在布局里写死为 `@color/black`，文字色写死为白色）。
文本输入框的文字色现在会跟着 `extra-keys-foreground` 走，所以浅色主题下不会出现
“黑色条 + 白色字看不见”的情况。

### 想固定成以前的深色键盘（不跟主题变）

```properties
extra-keys-background=#ff000000
extra-keys-foreground=#ffffffff
extra-keys-active-foreground=#ffff5252
extra-keys-active-background=#ff9e9e9e
```

（这四个值就是原来主题里 `extraKeysButton*Color` 的取值：白字 / 黑底 / 红字 / 灰底。）

### 实现位置

* `terminal-emulator/.../TerminalColorScheme.java`：解析 colors.properties 里的四个
  `extra-keys-*` 键，未定义的从 color scheme 派生（`blendColors()` 做混合）。
* `termux-shared/.../TermuxPropertyConstants.java` + `TermuxSharedProperties.java`：
  termux.properties 的同名键（覆盖项）。
* `app/.../TermuxActivity.java`：`setExtraKeysViewColors()` 解析覆盖项 →
  `ExtraKeysView.setButtonColors()`；`checkForFontAndColors()`（colors.properties 变更时）和
  `reloadActivityStyling()`（termux.properties 变更时）都会重新应用并重建按键。
* `termux-shared/.../ExtraKeysView.java`：`reload()` 时立即应用背景色（原本只有触摸时才应用），
  并保留 CTRL/ALT 已激活时的文字色。

注意：Termux:Float 用的是自己的一套属性/颜色处理，本次没有把联动接过去；它的 extra keys 行只
受上面 `ExtraKeysView` 的行为调整影响。

---

## 8. 整个 App 跟随主题（`night-mode=color-scheme`）+ 状态栏/导航栏同步

之前只有 extra keys 行跟着 `colors.properties` 走，App 的浅/深色主题还是由 `night-mode=system`
决定。现在 `night-mode` 多了一个取值：

```properties
# ~/.termux/termux.properties
night-mode=color-scheme
```

* 按 `colors.properties` 的 `background` 亮度决定（阈值 **130**，与光标色自适应用的是同一个阈值）：
  亮 → 用浅色主题，暗 → 用深色主题。所以 **在 Termux:Styling 里选浅色主题，整个 App
  （抽屉、设置页、长按菜单等）都会切成浅色**，不再只有键盘跟着变。
* 状态栏和导航栏的图标会在浅色背景时自动切成深色（原仓库里**完全没有**这个处理，浅色主题下图标
  是白色的、基本看不见）。两个栏位本身仍是透明的，透出来的就是终端背景色
  （`TermuxActivity.updateWindowBackgroundColor()`）。
* 这两个栏位也会在 `onResume` 时重新应用，避免启动瞬间还是上一次的颜色。

其他取值（`true` / `false` / `system`）行为不变。设成 `color-scheme` 后切换主题时 App 会自动重建
一次（AppCompat 的标准行为），这是预期的。

实现位置：`NightMode` 新增 `COLOR_SCHEME`；`TermuxThemeUtils.resolveNightMode()` /
`isLightBackground()`；`TermuxActivity.updateNightMode()` / `updateWindowBackgroundColor()`；
`TermuxTerminalSessionActivityClient.checkForFontAndColors()` / `updateBackgroundColor()` / `onResume()`。

---

## 9. 设置页里的图形化开关（不用再手改 properties）

设置 → Termux → **Terminal Keyboard**（新页面）里现在可以直接调：

* **Input Method** 分类
  * `Gboard CJK Keyboards` = `gboard-cjk-workaround`
  * `Enforce Character Based Input` = `enforce-char-based-input`
  * `Ctrl+Space Workaround` = `ctrl-space-workaround`
* **Extra Keys Colors** 分类（4 项，点进去是色板）
  * `Text Color` / `Background Color` / `Active Text Color` / `Active Background Color`
    = `extra-keys-foreground` / `extra-keys-background` /
    `extra-keys-active-foreground` / `extra-keys-active-background`
  * 对话框里有 **22 个预设色**（含 `#80000000` 这类半透明色，可让终端背景透出来）、
    **Custom color**（手输 `#RGB` / `#ARGB` / `#RRGGBB` / `#AARRGGBB`，会校验格式）、
    以及 **Follow color scheme**（一键清除覆盖，恢复跟随 Termux:Styling 主题）

这些开关**直接读写 `~/.termux/termux.properties`**：按行替换同名键、保留文件里的注释和其他设置
（重复键会被注释掉，不会留下冲突），改完**立即生效**，不需要 `termux-reload-settings`。
所以手写 properties 和用设置页改，两者看到的是同一份配置。
（⚠️ 这里的「立即生效」在 M3 之前是**错的**：打开设置页时 TermuxActivity 处于停止状态，
广播重载会被丢掉，必须手动 reload 才生效。链路已在 M3 修好，见 §12.5。）

实现位置：`TermuxSharedProperties.getTermuxPropertyValue()` / `setTermuxPropertyValue()`（按行改写
properties 文件）、`TerminalKeyboardPreferencesFragment`（把 `PreferenceDataStore` 落到 properties
文件）、`ColorPreference`（色板 + 自定义色对话框 + 校验）、
`app/src/main/res/xml/termux_keyboard_preferences.xml`。

---

## 10. 虚拟键盘激活态高亮 + UI 多语言

### 10.1 CTRL/ALT 等组合键的激活高亮（修复）

之前按下 CTRL/ALT 只改**文字色**，而"激活时文字颜色"默认取 `cursor` 色（常常和普通文字色相同），
所以激活后几乎看不出变化。现在激活状态会**同时**使用激活文字色和**激活背景色**
（`extra-keys-active-foreground` / `extra-keys-active-background`）：

* 激活键在手指松开后保持高亮（不会再被复位成普通背景色）；
* 长按锁定（locked）状态同样保持高亮；
* 同一类键存在多个实例（例如两行都有 CTRL）时一起变化。

想让高亮更醒目，把激活背景色调亮即可，例如 `extra-keys-active-background=#ffff7043`
（或在 设置 → Termux → Terminal Keyboard → 激活时背景颜色 里用色板选）。

### 10.2 UI 多语言（已内置简体中文）

* 新增 `values-zh` 翻译，覆盖 3 个模块：
  * `app`（壳子 UI：标题栏、会话菜单、设置页、对话框、Toast…共 126 条）
  * `termux-shared`（弹窗/错误/通知/报告文案，55 条）
  * `terminal-view`（文本选择工具栏：复制/粘贴/更多，3 条）
  * 终端模拟器（VT）内部没有面向用户的文案，无需翻译。
* Android 13+ 可在 **系统设置 → 应用 → Termux → 语言** 里单独为 Termux 选语言
  （已添加 `app/src/main/res/xml/locales_config.xml` 并在 manifest 里声明 `android:localeConfig`）；
  Android 12 及以下跟随系统语言。
* **新增其他语言**：把 `app/src/main/res/values/strings.xml` 复制为
  `values-<语言码>/strings.xml`（如 `values-ja`、`values-ru`、`values-zh-rTW`）后翻译即可，
  遗漏的条目会自动回退英文。注意占位符 `%1$s` / `%2$d` / `%d`、`\n`、`\"`、
  `&TERMUX_APP_NAME;` 这类实体必须原样保留（`termux-shared`、`terminal-view` 同理）。
* `supportsRtl` 仍是上游的 `false`；若要加阿拉伯语/希伯来语等 RTL 语言，需要改成 `true` 并检查布局。

---

## 11. 备份 / 恢复（`termux-backup` / `termux-restore`）

卸载应用会删掉整个 `/data/data/com.termux/files`（$PREFIX + $HOME），换签名构建时必须卸载，
所以本 fork 内置了两个脚本 + 图形入口。

### 图形入口

**设置 → Termux → 备份与恢复**：

| 条目 | 作用 |
| --- | --- |
| 立即备份 | 提取脚本并在**新终端会话**里运行，输出可见、可 Ctrl+C 取消 |
| 从备份恢复 | 脚本会列出设备上找到的备份（含大小/时间）让你选序号，再要求确认；默认先自动备份当前环境 |
| 把脚本安装为命令 | 复制到 `$PREFIX/bin`，之后随时可 `termux-backup` / `termux-restore` |

脚本以 raw 资源形式随 App 打包（`app/src/main/res/raw/termux_backup.sh`、`termux_restore.sh`），
运行时提取到 `$PREFIX/tmp/` 用 `bash` 执行；CI 也会把这两个脚本作为独立文件放进 Release。

### 命令行用法

```sh
termux-backup                      # 备份到 ~/storage/shared/Download/termux-backup-<日期>-<时间>.tar.zst
termux-backup -o /sdcard/Download -n my-bk -z gzip   # 指定目录/名字/压缩器（zstd 优先，gzip 兜底）
termux-restore                     # 不带参数：交互列出备份并选择
termux-restore /sdcard/Download/termux-backup-20260101-120000.tar.zst --dry-run   # 只校验+报告
termux-restore <归档> --target-dir ~/restore-test --no-pre-backup                  # 解包到别处演练（不碰现有环境）
```

### 行为与安全

* 归档内容：完整的 `./usr`（$PREFIX）与 `./home`（$HOME），排除 `usr/tmp/*`、`usr/var/run/*`、
  `home/storage`（符号链接）、`home/.cache/*`；另有一个 `<名字>.metadata/` 目录记录
  dpkg 包列表、`termux.properties`、`colors.properties` 与 app/bootstrap 版本，便于恢复时对比。
* 生成后会做 `tar -t` 完整性校验并写 `<名字>.sha256`；恢复前会用这个校验值验证（存在时）。
* 恢复是 tar 覆盖式：归档里的文件被覆盖，**归档里没有的文件（例如备份后新装的包）保留**。
* 恢复前默认先自动备份当前环境（`termux-backup-before-restore-<时间>`），可用 `--no-pre-backup` 关闭。
* 共享存储不可用时（还没 `termux-setup-storage`）会自动退回到 `$HOME` 存放，并提示。
* 恢复后建议执行：`termux-reload-settings` → `pkg upgrade` → `termux-setup-storage`
  （`~/storage` 不在备份里）；`~/bin` 下若有绝对 shebang 的脚本用 `termux-fix-shebang` 修一下。

### 实测（真机）

本机（aarch64，约 1.08 GB 数据）实测：备份 26s / 333 MB / 43023 条目，校验通过；
`--target-dir` 解包演练 1.1 GB，符号链接（`usr/bin/sh -> dash`）与可执行权限位均正确保留；
交互选择、非法输入、损坏归档（假文件）都能正确处理。

---

## 12. 设置页补齐：外观 / 按键行为 + 鼠标点按唤键盘

§9 里的开关只覆盖了输入法和 extra keys 配色，其余常用属性这次也搬进了设置页，
全部**直接读写 `~/.termux/termux.properties`**（按行替换同名键、保留注释和其他设置），
改完立即生效。

### 12.1 设置 → Termux → **外观**（新页面，`AppearancePreferencesFragment`）

| 分类 | 条目 | 属性 | 说明 |
| --- | --- | --- | --- |
| 主题 | 夜间模式 | `night-mode` | 浅色 / 深色 / 跟随系统 / 跟随配色方案（`color-scheme`，见 §8） |
| 全屏 | 全屏 | `fullscreen` | 隐藏状态栏，终端绘制在状态栏后面 |
| 全屏 | 全屏兼容处理 | `use-fullscreen-workaround` | 仅在开了全屏时生效；终端被画到状态栏下面时打开 |
| 终端工具栏 | 虚拟键盘风格 | `extra-keys-style` | `default` / `arrows-only` / `arrows-all` / `all` / `none` |
| 终端工具栏 | 工具栏高度（%） | `terminal-toolbar-height` | 40–300，100 为默认；滑条存的是百分数，写回文件时会换算成 `0.4`–`3.0` 的倍率 |
| 终端工具栏 | 虚拟键盘字母大写 | `extra-keys-text-all-caps` | 默认开 |

这一页的属性都是**创建 Activity 时读取**的，所以改完会重建一次 `TermuxActivity`
（终端会话和 transcript 都在 `TermuxService` 里，不会被杀掉）。

### 12.2 设置 → Termux → **终端键盘** → 按键行为（新分类）

| 条目 | 属性 | 取值 |
| --- | --- | --- |
| 返回键 | `back-key` | `back`（默认）/ `escape` |
| 音量键 | `volume-keys` | `virtual`（默认，发方向键等虚拟键）/ `volume`（真实音量键） |
| 键盘开关按钮 | `soft-keyboard-toggle-behaviour` | `show/hide`（默认）/ `enable/disable` |
| 启动时隐藏键盘 | `hide-soft-keyboard-on-startup` | 默认关 |
| **鼠标点按唤键盘** | `mouse-tap-shows-keyboard` | 默认关，见下 |
| 停用物理键盘快捷键 | `disable-hardware-keyboard-shortcuts` | 默认关（默认由 App 处理 Ctrl+Alt+C 等） |

这些属性都在用到时读取，所以改完立即生效，不需要重建 Activity。

### 12.3 新属性：`mouse-tap-shows-keyboard`（本 fork 新增）

```properties
# 鼠标点击、以及 TUI 开了鼠标跟踪时的点击，默认只转发给终端，不弹软键盘
# （设计如此，否则 vim/tmux 的选择、点击会被键盘打断）。打开后这些点击也会弹软键盘。
mouse-tap-shows-keyboard=true
```

实现：`TermuxTerminalViewClient.onSingleTapUp()` 里原来只有
「非鼠标来源 && 未开鼠标跟踪」才弹键盘，现在这个限制在属性打开时被取消。
用 `KEYBOARD` 键 / Ctrl+Alt+K 手动唤起键盘的老办法仍然可用（§6）。

### 12.4 坑：设置页不重写 properties

`androidx.preference` 的 `ListPreference` / `SwitchPreferenceCompat` / `SeekBarPreference`
在用 `PreferenceDataStore` 初始化时会**把刚读到的值再写回去**。如果 DataStore 直接落盘，
那么光是打开设置页就会往 `termux.properties` 里塞进一堆 `back-key=back` 之类的默认值，
并且触发一次无意义的 Activity 重建。所以
`TermuxPropertyPreferenceDataStore` 会记下每个键「上次读到的值」，
写回值与之一致时直接跳过（含属性本来就没定义、读到默认值的情况）。

### 12.5 坑：`ACTION_RELOAD_STYLE` 广播在 TermuxActivity 停止时会丢（真机验证发现）

**现象**：在设置页改完属性返回 Termux 后**不生效**，必须手动 `termux-reload-settings`
（或在终端里重启 App）才生效。M1 的「终端键盘」页其实一直有同样的问题，§9 里
「改完立即生效」的说法是错的，本次一并修掉。

**原因**：`TermuxActivity` 的广播接收器原先在 `onStart()` 注册、`onStop()` 注销，而
`SettingsActivity` 是不透明全屏 Activity，打开设置页时 `TermuxActivity` 会走 `onStop`，
于是 `TermuxPropertyPreferenceDataStore` 写盘后发出的 `ACTION_RELOAD_STYLE` 广播
**根本没有接收者**（接收器里那句 `if (mIsVisible)` 判断也就永远为真、形同虚设）。
在终端里跑 `termux-reload-settings` 时 TermuxActivity 在前台，所以那条路径是好的。
上游的设置都基于 SharedPreferences（同进程共享实例，不需要重载），只有写
`termux.properties` 的这几个页面会踩到。

**修法**（`TermuxActivity`）：
* 接收器改为 `onCreate()` 注册、`onDestroy()` 注销，这样 stop 期间也收得到广播。
* 收到 `ACTION_RELOAD_STYLE` 时若 `!mIsVisible`，不立即重载（stop 期间做 UI 操作不安全），
  而是记下 `mReloadActivityStylingOnStart` 与 `mRecreateActivityOnStart`
  （后者取自广播的 `EXTRA_RECREATE_ACTIVITY`：键盘页 `false`、外观页 `true`），
  在 `onStart()` 里再调用一次 `reloadActivityStyling(recreate)`。
* 另外两个 action（`ACTION_REQUEST_PERMISSIONS` / `ACTION_NOTIFY_APP_CRASH`）仍然只在可见时处理，
  行为与改动前完全一致（改动前停止期间收不到，现在收到后直接 return）。

结果：键盘相关属性（返回键/音量键/鼠标点按唤键盘/配色…）返回后立即生效且不重建；
外观页（主题/全屏/工具栏）返回后重建一次生效——这些属性本来就只在 `onCreate` 读取。

### 12.6 实测（真机）

安装 `fork-latest` 的 arm64 包验证：外观页 6 项、按键行为 6 项均可改；打开设置页前后
`~/.termux/termux.properties` 无任何变化（确认 §12.4 的 no-op 守卫生效）。
首次验证发现「改动要手动 `termux-reload-settings` 才生效」，即 §12.5 的广播丢失问题，
修复后返回设置页即生效。
