# 项目架构

本文说明代码边界、主要调用链和常见修改位置。开始改动前先判断功能属于共用业务、手机交互还是 TV 交互，避免双端复制逻辑。

## 构建模型

项目使用 Android View、ViewBinding 和两个产品维度：

```text
app/src/main       双端共用代码与资源
    + mobile       手机/平板覆盖，applicationId=com.media.ibox
    + leanback     Android TV 覆盖，applicationId=com.media.ibox.tv

ABI:
    + arm64_v8a
    + armeabi_v7a
```

`mode x abi` 组成四个变体：

- `mobileArm64_v8a`
- `mobileArmeabi_v7a`
- `leanbackArm64_v8a`
- `leanbackArmeabi_v7a`

同名 Java 类和资源可放在 `mobile`、`leanback` 两个源码集中，Gradle 只把当前变体对应的实现编进 APK。

## 品牌与包身份

手机显示名为 `iBox`，TV 显示名为 `iBoxTV`。共用资源名和文件使用 `ibox_` 前缀，样式及 Java UI 组件使用 `IBox` 前缀。`com.media.ibox` 与 `com.media.ibox.tv` 分别作为 Android 稳定应用 ID 保留，以支持各自旧安装的覆盖升级及数据继承；不要把包身份用于界面文案。

## Gradle 模块

| 模块或目录 | 职责 |
| --- | --- |
| `app` | UI、业务、数据库、播放器、服务和应用资源 |
| `catvod` | 内容扩展接口、网络层和通用模型 |
| `chaquo` | Python 3.10 扩展运行环境 |
| `quickjs` | JavaScript 扩展运行环境 |
| `vendor/media3` | 私有 Git 子模块；定制 Media3、MPV、FFmpeg 与弹幕组件 |

`settings.gradle` 通过 Gradle Composite Build 引入 `vendor/media3`，并用依赖替换把标准 `androidx.media3` 模块指向定制源码。主仓库以 Git 子模块提交固定播放器版本；缺少或未初始化该目录时播放器相关依赖无法解析。

## app 代码分层

| 路径 | 主要内容 | 修改原则 |
| --- | --- | --- |
| `api/config` | 点播、直播、规则和壁纸配置 | 配置格式和装载流程放这里 |
| `api/parser` | 直播、EPG 等解析器 | 输入格式解析和容错放这里 |
| `api/loader` | Java/JS/Python 扩展加载 | 不把 UI 逻辑放入加载器 |
| `bean`、`gson` | 数据模型与序列化适配 | 字段兼容通过适配器或 `alternate` 处理 |
| `browse`、`model` | 浏览业务、ViewModel 和异步任务 | 生命周期无关的列表逻辑放这里 |
| `playback/vod` | 点播状态、历史、自动换线和换源 | 点播播放规则的主要位置 |
| `playback/live` | 直播状态、线路、重连和超时 | 直播播放规则的主要位置 |
| `player` | 播放器入口、Exo/MPV、解析和效果 | 引擎差异封装在这里 |
| `db` | Room 数据库、DAO 和迁移 | 修改实体必须提供 Migration |
| `setting` | SharedPreferences 设置封装 | UI 不直接散写偏好键名 |
| `offline`、`sync` | 下载、离线媒体与 WebDAV | 长任务注意服务和恢复状态 |
| `server` | 本机 HTTP 管理服务 | 外部入口必须考虑鉴权和隐私 |
| `service`、`receiver` | 播放服务和系统事件 | 检查前台服务与系统版本限制 |
| `ui` | 双端共用对话框、控件和基础 Activity | 只放确实能共用的 UI |

## 应用启动

1. Android 创建 `App`，初始化网络、主题模式、通知、存储清理和 EPG 缓存计划。
2. 对应源码集的 `HomeActivity` 启动。
3. 首页后台读取 `Config.vod()`、`Config.live()` 和 `Config.wall()`。
4. `VodConfig`、`LiveConfig`、`WallConfig` 分别加载当前配置。
5. 手机或 TV 根据启动页面设置进入点播、直播或历史。

关键文件：

- `app/src/main/java/com/media/ibox/App.java`
- `app/src/mobile/java/com/media/ibox/ui/activity/HomeActivity.java`
- `app/src/leanback/java/com/media/ibox/ui/activity/HomeActivity.java`
- `app/src/main/java/com/media/ibox/bean/Config.java`

新增首次安装初始化时，应在读取三个 `Config` 之前完成，并使用一次性版本标记。不要直接改变 `Config.vod()` 的空值回退，否则用户删除内置源后可能被强制重新添加。

## 配置加载链

```text
Config(Room 订阅记录)
    -> VodConfig / LiveConfig
    -> Decoder + UrlUtil
    -> TvBox JSON / M3U / TXT / 扩展
    -> Site / Live / Channel
    -> UI 与播放器
```

点播总配置中的 `lives` 会由 `VodConfig` 交给 `LiveConfig`。独立直播订阅则由 `LiveConfig` 直接解析。EPG 订阅保存在 `LiveSetting`，XMLTV 由 `EpgParser` 解析。详细格式和内置方法见 [CONFIGURATION.md](CONFIGURATION.md)。

## 播放调用链

```text
VideoActivity / LiveActivity
    -> VodPlaybackController / LivePlaybackController
    -> PlayerManager
    -> PlayerEngineFactory
    -> ExoPlayerEngine / MpvPlayerEngine
    -> PlaybackService / Media3 PlayerView
```

职责约定：

- Activity 负责触摸、按键、焦点、弹窗和展示。
- Controller 负责集数/频道、线路、重试、换源和状态转换。
- Player 层负责媒体引擎、轨道、Seek、解码和底层错误。
- 解析层负责从订阅和站点结果得到最终播放请求。

遇到两个播放器都出现相同问题时，应优先检查 Controller、解析结果、Intent 和服务状态，而不是先修改某一个引擎。

## 数据存储

| 数据 | 存储位置 | 说明 |
| --- | --- | --- |
| 订阅、历史、收藏、直播状态、轨道 | Room 数据库 `tv` | 数据库版本在 `AppDatabase.VERSION` |
| 主题、播放器、弹幕、直播等设置 | SharedPreferences | 通过 `setting/` 类访问 |
| EPG、图片和临时数据 | 应用缓存或 iBox 目录 | 按设置和系统策略清理 |
| 离线下载 | 用户选择的媒体目录 | 删除前确认是否为用户数据 |
| WebDAV 数据 | 用户配置的远端 | 不在日志中输出密码 |

数据库升级流程见 [MAINTENANCE.md](MAINTENANCE.md)，设置新增和兼容见 [SETTINGS.md](SETTINGS.md)。

## UI 与主题链

资源覆盖优先级为：

```text
app/src/main/res
    -> app/src/mobile/res 或 app/src/leanback/res
    -> values-night、land、sw600dp 等限定目录
```

`BaseActivity` 在布局加载前应用主题色、焦点色和无障碍覆盖。修改主题相关资源时不能只检查当前 Activity，必须重新创建页面或完全重启 App 验证。

UI 修改位置：

- 共用语义颜色和夜间颜色：`main/res/values*/ibox_colors.xml`
- 字号、间距和组件高度：`main/res/values/ibox_dimens.xml`
- TV 尺寸覆盖：`leanback/res/values/tv_dimens.xml`
- 文本、按钮、卡片和输入框：`ibox_styles.xml`、`ibox_components.xml`
- 主题和弹窗：`ibox_ui_system.xml`、端侧 `styles.xml`
- 页面布局：对应源码集的 `res/layout*`

完整规范见 [UI_DESIGN_SYSTEM.md](UI_DESIGN_SYSTEM.md)。

## 常见修改定位

| 需求 | 首先检查 |
| --- | --- |
| 修改点播订阅兼容性 | `VodConfig`、`Site`、对应 Loader/Spider |
| 修改直播源格式 | `LiveConfig`、`LiveParser`、`Live`、`Channel` |
| 修改 EPG | `LiveApi`、`EpgParser`、`LiveSetting` |
| 修改自动换线/换源 | `playback/vod`、`playback/live` |
| 修改播放器按钮 | 双端 `activity_video.xml`、`view_control_*` 和 Activity |
| 修改海报与电影名 | 双端 `adapter_vod*.xml` 和 `Widget.IBox.PosterTitle` |
| 修改设置默认值 | `setting/*.java`，再检查双端设置页 |
| 修改底部导航 | `view_app_navigation.xml`、手机 `HomeActivity`、`Setting` |
| 修改 TV 焦点 | `leanback/res` selectors、styles 和焦点顺序 |
| 修改局域网管理 | `server/process`、`assets/index.html`、`assets/js/script.js` |

## 依赖边界

- 共用层不能引用只存在于 `mobile` 或 `leanback` 的类。
- 端侧 Activity 可以调用共用 Controller、设置和数据层。
- 新增网络访问应复用项目的 OkHttp、GitHub 加速和请求头处理。
- 不在 Activity 中创建长期线程；使用现有任务执行器、ViewModel 或 Service。
- 不在布局中复制大量颜色和尺寸；先判断是否应成为语义资源。
