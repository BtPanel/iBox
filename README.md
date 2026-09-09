# iBox

iBox 是一套同时面向 Android 手机、平板和 Android TV 的本地影音客户端。手机端与 TV 端共享配置、数据、解析和播放能力，界面分别针对触摸和遥控器设计。

当前发布版本为 `1.0 (1)`：

| 平台 | 产品风味 | 应用 ID | 主要交互 |
| --- | --- | --- | --- |
| 手机/平板 | `mobile` | `com.imedia.box` | 触摸、手势、横竖屏 |
| Android TV | `leanback` | `com.imedia.box.tv` | 遥控器、焦点、横屏 |

`com.imedia.box` 是 Android 已安装应用的稳定身份，用于覆盖升级和保留本地数据，因此源码包路径仍保留 `com/imedia/box`。它不会作为产品品牌显示；资源、样式、组件和文件统一使用 `iBox` / `ibox_` 命名。

## 主要能力

- 点播分类、搜索、详情、收藏、历史、多线路与多集播放
- Media3 ExoPlayer、MPV、字幕、弹幕、音轨、倍速与画面设置
- M3U、TXT、JSON 直播源，XMLTV EPG、频道收藏、节目提醒与回看
- 手机端本地文件、离线下载、扫码和 DLNA 投屏控制
- TV 端遥控器焦点界面、DLNA 接收和局域网管理
- Java、JavaScript 与 Python 内容扩展运行环境
- Room 本地数据、WebDAV 同步、来源健康检查和诊断日志
- 日夜主题、主题色、TV 焦点色、字号、高对比度与减少动画
- 可关闭的全局 GitHub 加速和多个备用地址

项目默认不提供媒体内容。用户可添加外部订阅；如需发行自用版本，也可以按[配置与内置订阅](docs/CONFIGURATION.md)预置点播、直播和 EPG。使用者应自行确认内容及服务符合所在地法律和服务条款。

手机端首次安装默认跟随系统日夜模式；用户手动选择浅色或深色后会保留选择。Android TV 为保证远距离观看和焦点对比度固定使用深色主题。

## 源码结构

```text
app/src/main/       双端共用业务、播放器、设置、数据库和资源
app/src/mobile/     手机/平板界面和平台能力
app/src/leanback/   Android TV 界面和遥控器交互
catvod/             内容扩展接口、网络和通用工具
chaquo/             Python 运行模块
quickjs/            JavaScript 运行模块
vendor/media3/      项目使用的定制 Media3 源码
docs/               开发、配置、UI、测试和发布文档
```

`forcetech/`、`hook/`、`jianpian/`、`thunder/`、`tvbus/`、`zlive/` 等目录保存原生或扩展实现；是否进入 APK 由当前依赖和预编译库决定。

## 快速开始

构建要求：JDK 21、Android SDK 37、NDK `29.0.14206865`、CMake 和 Python 3.10。先根据 `local.properties.example` 创建 `local.properties`，再执行：

```powershell
$python310 = "C:/Path/To/Python310/python.exe"

.\gradlew.bat :app:testMobileArm64_v8aDebugUnitTest `
  -PchaquopyBuildPython=$python310

.\gradlew.bat :app:assembleMobileArm64_v8aDebug `
  -PchaquopyBuildPython=$python310
```

Debug APK 默认位于：

```text
app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
```

正式发布使用 `1.0 (1)`，执行 `:app:assembleRelease` 后四个已签名 APK 会集中到 `Release/apk/`。发布备份 `Release/iBox-1.0-release-backup.zip` 同时包含干净源码、完整图标源文件、四个 APK 和 SHA-256 校验文件，但不包含 Git 历史、本机配置或签名密钥。

完整环境准备、MuMu 安装、四变体测试、正式签名和源码归档见[构建与发布](docs/BUILD.md)。

## 文档入口

| 文档 | 解决的问题 |
| --- | --- |
| [文档索引](docs/README.md) | 不知道该先看哪一份文档 |
| [项目架构](docs/ARCHITECTURE.md) | 代码放在哪里、手机与 TV 如何共用 |
| [功能说明](docs/FEATURES.md) | 当前版本已经支持什么 |
| [配置与内置订阅](docs/CONFIGURATION.md) | 点播、直播、EPG、内置源和 GitHub 加速 |
| [UI 与组件规范](docs/UI_DESIGN_SYSTEM.md) | 配色、字号、按钮、海报、焦点和双端布局 |
| [设置项开发](docs/SETTINGS.md) | 修改默认设置或新增设置项 |
| [测试与排错](docs/TESTING.md) | MuMu、ADB、Logcat、UI 和播放问题检查 |
| [构建与发布](docs/BUILD.md) | Debug/Release APK、签名、校验和源码包 |
| [维护指南](docs/MAINTENANCE.md) | 分支、提交、数据库迁移和发布检查 |

## 修改后的最低要求

1. 共用业务优先修改 `app/src/main`，端侧交互分别修改 `mobile` 或 `leanback`。
2. UI 必须验证手机与 TV、日夜主题、长文字、焦点和高对比度。
3. 播放或配置修改必须用真实点播、直播和 EPG 地址验证，并检查 Logcat。
4. 提交前运行 `git diff --check`、相关单元测试和双端编译。
5. 正式发布前运行四变体测试、Lint、四个 Release APK 构建和 SHA-256 校验。

## 许可证与安全

项目许可证见 [LICENSE.md](LICENSE.md)。第三方组件和 `vendor/media3` 同时受各自许可证约束。

不要提交 `local.properties`、签名文件、密码、令牌或私人订阅地址。Java、JavaScript 和 Python 扩展具有较强能力，只应加载可信来源。
