# 构建与发布

本文以 Windows PowerShell 为主要环境，说明环境准备、四个签名 Release APK、校验和源码归档。源码当前版本为 `1.0 (1)`；磁盘上已有的 `Release/` 文件不自动代表当前源码，需要重新构建和校验。

## 构建矩阵

项目有两个界面风味和两个 ABI：

| 风味 | ABI | 应用 ID |
| --- | --- | --- |
| `mobile` | `arm64_v8a` | `com.imedia.box` |
| `mobile` | `armeabi_v7a` | `com.imedia.box` |
| `leanback` | `arm64_v8a` | `com.imedia.box.tv` |
| `leanback` | `armeabi_v7a` | `com.imedia.box.tv` |

手机显示名为 `iBox`，TV 显示名为 `iBoxTV`。常见手机和较新的 TV 使用 arm64；只有明确需要 32 位兼容时才安装 armeabi-v7a 包。两个 ABI 属于相同应用 ID，不能同时安装在同一设备上。

## 环境要求

| 工具 | 当前要求 |
| --- | --- |
| JDK | 21 |
| Android SDK Platform | 37 |
| Android Build Tools | 36.0.0 |
| Android NDK | 29.0.14206865 |
| CMake | 3.31.6，或与当前原生模块兼容版本 |
| Python | 3.10，供 Chaquopy 构建使用 |
| Gradle | 使用仓库中的 Wrapper |

确认环境：

```powershell
java -version
& "C:/Path/To/Python310/python.exe" --version
adb version
```

如果 `java` 不在当前终端 PATH，可只为当前 PowerShell 会话指定 Android Studio 自带 JDK：

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
java -version
```

不需要为验证一次构建永久修改系统环境变量。

不要使用系统中其他 Python 版本代替 3.10。Chaquopy 的运行环境和构建解释器版本必须兼容。

## 准备 Android SDK

可以通过 Android Studio SDK Manager 安装，也可以用 `sdkmanager`：

```powershell
sdkmanager "platforms;android-37.0" `
  "build-tools;36.0.0" `
  "ndk;29.0.14206865" `
  "cmake;3.31.6"
```

接受许可证：

```powershell
sdkmanager --licenses
```

## 准备定制 Media3

`vendor/media3` 是私有 Git 子模块。首次克隆主仓库时使用：

```powershell
git clone --recurse-submodules https://github.com/BtPanel/iBox.git
```

已有主仓库检出执行：

```powershell
git submodule sync --recursive
git submodule update --init --recursive
```

两个仓库都是私有仓库，命令行 Git 必须使用对 `BtPanel/iBox` 和 `BtPanel/iBox-media3` 都有读取权限的 GitHub 凭据。源码归档已经包含子模块源码快照，不需要联网初始化。

验证：

```powershell
Test-Path vendor/media3/settings.gradle.kts
git -C vendor/media3 rev-parse HEAD
```

第二条应输出固定提交 `162e750c5aefcd0be0a7663cfe195bce9dbbd3c8`。`settings.gradle` 会用该目录替换标准 Media3 依赖，缺失时不能正常构建播放器模块。

## local.properties

`app/build.gradle` 在配置阶段读取 `local.properties`，因此 Debug 和 Release 都需要该文件。复制示例：

```powershell
Copy-Item local.properties.example local.properties
```

填写：

```properties
sdk.dir=C:/Users/your-name/AppData/Local/Android/Sdk
storeFile=signing/release.jks
storePassword=replace-with-store-password
keyAlias=release
keyPassword=replace-with-key-password
```

Windows 路径建议使用 `/`。`storeFile` 相对仓库根目录解析。

`local.properties`、`signing/`、JKS、密码和私钥不得提交 Git，也不得放进公开源码包。

### 仅本机测试签名

没有正式证书时可创建本地测试密钥，并把上面的签名字段指向它：

```powershell
New-Item -ItemType Directory -Force signing | Out-Null
keytool -genkeypair `
  -keystore signing/local-test.jks `
  -storepass android `
  -keypass android `
  -alias androiddebugkey `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000 `
  -dname "CN=Android Debug,O=Android,C=US"
```

测试密钥不能替代正式发布证书。已经发布的应用后续升级必须使用相同正式证书，否则 Android 拒绝覆盖安装。

## Debug 构建

在仓库根目录设置 Python 路径：

```powershell
$python310 = "C:/Path/To/Python310/python.exe"
```

构建手机版 arm64：

```powershell
.\gradlew.bat :app:assembleMobileArm64_v8aDebug `
  -PchaquopyBuildPython=$python310
```

输出：

```text
app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
```

构建 TV arm64：

```powershell
.\gradlew.bat :app:assembleLeanbackArm64_v8aDebug `
  -PchaquopyBuildPython=$python310
```

一次构建全部 Debug APK：

```powershell
.\gradlew.bat :app:assembleDebug `
  -PchaquopyBuildPython=$python310
```

## 安装到 MuMu

MuMu 常用连接地址是 `127.0.0.1:16384`，实际端口以当前模拟器为准：

```powershell
adb connect 127.0.0.1:16384
adb devices
```

保留数据覆盖安装：

```powershell
adb -s 127.0.0.1:16384 install -r `
  app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
```

签名不一致时不能直接覆盖。卸载会删除应用数据，确认备份后再执行：

```powershell
adb -s 127.0.0.1:16384 uninstall com.imedia.box
```

完整 ADB 和 Logcat 流程见 [TESTING.md](TESTING.md)。

## 测试与 Lint

发布前运行四变体 JVM 测试：

```powershell
.\gradlew.bat `
  :app:testMobileArm64_v8aDebugUnitTest `
  :app:testMobileArmeabi_v7aDebugUnitTest `
  :app:testLeanbackArm64_v8aDebugUnitTest `
  :app:testLeanbackArmeabi_v7aDebugUnitTest `
  -PchaquopyBuildPython=$python310
```

运行手机和 TV Lint：

```powershell
.\gradlew.bat `
  :app:lintMobileArm64_v8aDebug `
  :app:lintLeanbackArm64_v8aDebug `
  -PchaquopyBuildPython=$python310
```

编译双端 AndroidTest：

```powershell
.\gradlew.bat `
  :app:assembleMobileArm64_v8aDebugAndroidTest `
  :app:assembleLeanbackArm64_v8aDebugAndroidTest `
  -PchaquopyBuildPython=$python310
```

## 清理构建产物

清理应用和本项目模块的构建目录：

```powershell
.\gradlew.bat `
  :app:clean `
  :catvod:clean `
  :chaquo:clean `
  :quickjs:clean
```

清理正式发布目录中的 APK、ZIP 和校验文件：

```powershell
.\gradlew.bat cleanRelease
```

`cleanRelease` 会删除 `Release/apk/`、`Release/*.zip` 和 `Release/SHA256SUMS.txt`。只有准备完整重建发布包时才执行。

一般不需要删除全局 Gradle 缓存。缓存损坏时先停止 Gradle Daemon，再针对具体缓存处理，不要把用户主目录作为递归删除目标。

## 版本号

修改 `app/build.gradle`：

```groovy
defaultConfig {
    versionCode 2
    versionName "1.1"
}
```

- `versionCode`：Android 内部整数，每次对外发布必须递增。
- `versionName`：用户可见版本，例如 `1.0`、`1.1`、`1.0.1`。
- 手机和 TV 共用版本号。
- Git 标签与 `versionName` 对应，例如 `v1.1`。

不要为重建同一份源码随意改变 `versionCode`。只要 APK 将用于覆盖已发布版本，就必须使用更大的 `versionCode`。

## Release 构建

先确认 `local.properties` 指向预期的签名证书。`Release/` 中可能有以前的包；若要保留旧产物，先把它们移到明确命名的备份目录，再清理并完整构建：

```powershell
.\gradlew.bat cleanRelease

.\gradlew.bat :app:assembleRelease `
  -PchaquopyBuildPython=$python310
```

`assembleRelease` 构建四个变体。Gradle 会把 APK 复制到 `Release/apk/`。本地签名文件若只是测试密钥，产物虽为 Release 构建，也**不是可覆盖正式已发布版本的签名包**：

| 文件 | 平台 | ABI |
| --- | --- | --- |
| `iBox-1.0-mobile-arm64_v8a.apk` | 手机/平板 | arm64-v8a |
| `iBox-1.0-mobile-armeabi_v7a.apk` | 手机/平板 | armeabi-v7a |
| `iBox-1.0-leanback-arm64_v8a.apk` | Android TV | arm64-v8a |
| `iBox-1.0-leanback-armeabi_v7a.apk` | Android TV | armeabi-v7a |

原始输出同时位于 `app/build/outputs/apk/**/release/`。

确认四个文件都存在：

```powershell
Get-ChildItem Release/apk -Filter *.apk |
  Sort-Object Name |
  Select-Object Name, Length
```

## APK 签名验证

使用 Android Build Tools 中的 `apksigner`：

```powershell
$apksigner = "C:/Path/To/Android/Sdk/build-tools/36.0.0/apksigner.bat"
Get-ChildItem Release/apk/*.apk | ForEach-Object {
    & $apksigner verify --verbose --print-certs $_.FullName
}
```

检查四个 APK 的证书摘要一致，并与已发布版本一致；`verify` 成功只说明签名有效，不证明它是正式证书。还应使用 `aapt dump badging` 或 `apkanalyzer manifest application-id` 检查包名和版本，在匹配 ABI 的设备安装、启动和播放测试。

## SHA-256 校验文件

```powershell
$lines = Get-ChildItem Release/apk/*.apk |
  Sort-Object Name |
  ForEach-Object {
      $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
      "$hash  apk/$($_.Name)"
  }

$lines | Set-Content Release/SHA256SUMS.txt -Encoding ascii
Get-Content Release/SHA256SUMS.txt
```

APK 重新构建后必须重新生成校验文件，不能沿用旧哈希。

## 源码与产物归档

发布备份包应包含：

- 应用和所有本项目模块源码。
- Gradle Wrapper、配置和文档。
- `vendor/media3` 定制源码。
- 此次构建的四个 APK。
- `Release/SHA256SUMS.txt`。
- `artwork/app-icon/` 下的 SVG、PNG、ICO、预览、生成器和图标资产 ZIP。

不得包含：

- `.git/` 历史和工作树元数据。
- `.gradle/`、`.idea/`、`.vscode/` 和任意 `build/`。
- `local.properties`、`signing/`、JKS、证书、密码和私钥。
- 日志、报告、临时文件和旧 ZIP。
- Media3 的 `buildout/`、`out/` 等生成目录。

使用仓库脚本按 Git 跟踪文件复制当前工作树，再加入 `vendor/media3` 子模块源码快照、四个 APK 和 SHA-256。`SOURCE-MANIFEST.txt` 记录主仓库 HEAD、Media3 子模块 HEAD、工作区变更及归档标签。归档前应先人工检查主仓库和子模块的 `git status`，避免把未提交的私人内容带入包中：

```powershell
.\scripts\package-release.ps1 -Label "1.0-pre-audit"
```

归档可能较大，压缩需要较长时间。生成后检查：

```powershell
Get-Item Release/iBox-1.0-pre-audit-source.zip | Select-Object Name, Length
tar -tf Release/iBox-1.0-pre-audit-source.zip | Select-String `
  "(^|/)local.properties$|(^|/)signing/|(^|/).git/|\.jks$"
```

第二条不应发现敏感文件。再解压到临时目录确认以下内容存在：

```text
gradlew.bat
settings.gradle
app/
catvod/
chaquo/
quickjs/
vendor/media3/settings.gradle.kts
Release/apk/
Release/SHA256SUMS.txt
artwork/app-icon/ibox-app-icon.svg
SOURCE-MANIFEST.txt
```

源码包不得包含 `.git/`，因此不会携带提交历史或远端信息。若先构建打包、后修复代码，旧包仍是**修复前**快照；修复完成后要重新构建、校验，并用不同标签另打源码包，不能把旧 APK 说成修复后的版本。版本号相同的不同构建应以标签及 SHA-256 区分，正式分发升级时按版本策略递增 `versionCode`。

## 自动构建

仓库提供两个独立工作流，可在 GitHub 仓库的 **Actions** 页面中选择后点击 **Run workflow**：

- `Build Debug APK`（`.github/workflows/android-debug.yml`）：手动点击、分支推送或 Pull Request 时运行四变体单元测试、双端 Lint、AndroidTest 编译，并生成四个 Debug APK 和 SHA-256 校验文件。
- `Build Release APK`（`.github/workflows/android-release.yml`）：手动点击时生成四个签名 Release APK 和 SHA-256 校验文件；推送 `v*` 标签时还会创建 GitHub Release。

构建完成后打开对应运行记录，在页面底部 **Artifacts** 下载 `iBox-debug-apks-*` 或 `iBox-release-apks-*`。工作流会先检查所需 Secret，缺少配置时会在日志顶部列出具体名称。

自动构建需要仓库 Secrets：

- `MEDIA3_REPOSITORY_TOKEN`：对两个私有仓库具有只读 Contents 权限的细粒度 GitHub Token；Debug 和 Release 都需要。
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

CI 按 `.gitmodules` 中记录的固定提交检出 `vendor/media3`，不会回退到公开上游源码。

## Git 标签

所有检查通过并提交后再创建标签：

```powershell
git tag -a v1.0 -m "iBox 1.0"
git show v1.0 --stat
```

推送标签会触发正式工作流：

```powershell
git push origin v1.0
```

不要把标签放在仍有未提交修复的提交上。已经公开发布的标签不应随意删除或移动。

## 常见构建故障

| 问题 | 检查 |
| --- | --- |
| 找不到 Media3 模块 | `vendor/media3/settings.gradle.kts` 和固定提交 |
| Chaquopy 找不到 Python | 使用 Python 3.10，并传 `-PchaquopyBuildPython` |
| `local.properties` 读取失败 | 文件存在且所有必填键有值 |
| SDK/NDK/CMake 不匹配 | `gradle/libs.versions.toml` 和 CI 工作流版本 |
| Release 签名失败 | `storeFile`、别名、密码和证书格式 |
| APK 不能覆盖安装 | 包名、`versionCode` 和签名证书 |
| 只有一个端编译失败 | 对应 `mobile`/`leanback` 资源或同名类缺失 |
| R8 后运行崩溃 | `proguard-rules*.pro`、反射类和扩展加载 |
| Release 目录混有旧 APK | 先运行 `cleanRelease` 再完整构建 |

正式交付前再执行 [MAINTENANCE.md](MAINTENANCE.md) 的发布检查表。
