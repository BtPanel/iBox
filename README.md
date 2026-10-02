# iBox / iBoxTV

iBox 是 Android 手机、平板和电视双端影音客户端。手机端使用触摸界面和名称 `iBox`；电视端使用遥控器焦点界面和名称 `iBoxTV`。两端共用内容配置、播放器、数据库和多数设置，但安装包及数据互不覆盖。

| 平台 | Gradle 风味 | 应用 ID | 显示名称 |
| --- | --- | --- | --- |
| 手机、平板 | `mobile` | `com.media.ibox` | iBox |
| Android TV | `leanback` | `com.media.ibox.tv` | iBoxTV |

目前源码的 `versionName` 为 `1.0`、`versionCode` 为 `1`。应用 ID 和 Java 包路径保留旧身份，以便同一平台的已安装版本升级；它们不是界面品牌。

## 能做什么

- 点播分类、聚合搜索、收藏、历史、多线路和续播。
- Media3 ExoPlayer / MPV 播放、字幕、弹幕、音轨、倍速和缓冲设置。
- M3U、TXT、JSON 直播源，XMLTV EPG、频道分组、多线路和服务端支持时的回看。
- 手机端离线下载、本地文件和 DLNA 投屏；TV 端遥控器交互、DLNA 接收和局域网管理。
- Java、JavaScript、Python 内容扩展，WebDAV 同步与主题、无障碍设置。

项目不提供影视、直播或 EPG 内容。外部订阅的可用性、合法性和服务条款由使用者自行确认。支持的格式及限制见[功能说明](docs/FEATURES.md)和[配置说明](docs/CONFIGURATION.md)。

## 开发入口

源码按 `app/src/main`（共用）、`app/src/mobile`（手机）和 `app/src/leanback`（电视）划分。`catvod`、`chaquo`、`quickjs` 是扩展运行模块；`vendor/media3` 是构建所需的私有定制播放器子模块。首次克隆请使用 `git clone --recurse-submodules`，现有检出按[构建文档](docs/BUILD.md)初始化子模块。

环境要求为 JDK 21、Android SDK 37、Build Tools 36.0.0、NDK 29.0.14206865、CMake 3.31.6 和 Python 3.10。完成本机 `local.properties` 后，在仓库根目录执行，例如：

```powershell
$python310 = "C:/Path/To/Python310/python.exe"
.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest `
  :app:testLeanbackArm64_v8aDebugUnitTest `
  -PchaquopyBuildPython=$python310
.\gradlew.bat :app:assembleMobileArm64_v8aDebug `
  :app:assembleLeanbackArm64_v8aDebug `
  -PchaquopyBuildPython=$python310
```

正式版有手机和 TV 各两个 ABI（arm64-v8a、armeabi-v7a），共四个 APK。构建、签名、校验及源码打包步骤见[构建与发布](docs/BUILD.md)。不要把目录中已有的 APK 或 ZIP 当作当前提交的产物；以各次发布的清单、哈希和源码标识为准。

## 文档

从[文档索引](docs/README.md)按任务进入。常用文档：[架构](docs/ARCHITECTURE.md)、[UI 规范](docs/UI_DESIGN_SYSTEM.md)、[设置](docs/SETTINGS.md)、[测试](docs/TESTING.md)、[维护](docs/MAINTENANCE.md)。

修改后至少检查 `git diff --check`、相关测试及双端编译；播放器、直播或界面改动还需在对应设备实际验证。正式分发前运行四变体测试、Lint，并检查每个 Release APK 的签名、包名、版本和 ABI。

许可证见 [LICENSE.md](LICENSE.md)。不要提交本机配置、签名密钥、密码、私人订阅地址或调试日志；只加载可信扩展。
