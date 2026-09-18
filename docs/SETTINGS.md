# 设置项开发指南

本文说明设置数据存在哪里、手机和 TV 的设置界面如何关联，以及以后修改默认值或新增设置时需要同步哪些文件。

## 设置架构

设置值保存在 SharedPreferences，由 `com.github.catvod.utils.Prefers` 访问。业务代码应通过 `app/src/main/java/com/imedia/box/setting/` 下的封装类读写，不要在 Activity、Dialog 或 Adapter 中重复字符串键名。

```text
设置页面或弹窗
    -> Setting / PlayerSetting / LiveSetting / ...
    -> Prefers
    -> SharedPreferences
```

这样可以统一默认值、范围限制、旧键迁移和双端行为。

## 设置类职责

| 类 | 主要职责 |
| --- | --- |
| `Setting` | 主题、字号、布局、首页、导航、启动页、搜索、换源、GitHub 加速等全局设置 |
| `PlayerSetting` | 播放引擎、尺寸、缓冲、轨道和常用播放行为 |
| `DecodeSetting` | 硬解/软解、隧道、帧率和解码兼容选项 |
| `PreloadSetting` | 预加载和缓冲策略 |
| `DanmakuSetting` | 弹幕加载、显示、样式、速度、密度、过滤和偏移 |
| `LiveSetting` | 直播重连、超时、线路、显示、解码、EPG 和源覆盖 |
| `DownloadSetting` | Wi-Fi、并发下载、分片线程和重试 |
| `SubtitleSetting` | 字幕服务、语言、同步和基础行为 |
| `SubtitleStyleOverride` | 字幕外观覆盖 |
| `AudioSetting` | 音效预设、均衡、对白、增益和平衡 |
| `VideoSetting` | 画面和视频效果设置 |
| `SpeedSetting` | 倍速选择和记忆 |

设置类应保持静态、无 Activity 引用，并在 getter 中处理默认值和合法范围。

## 当前重要默认值

以下默认值以当前源码为准：

| 设置 | 手机默认 | TV 默认 | 代码位置 |
| --- | --- | --- | --- |
| 主题模式 | 跟随系统 | 固定深色 | `Setting.applyThemeMode()` |
| 主题色 | 品牌蓝 `#3D7FFF` | 品牌蓝主题覆盖 | `Setting.getThemeColor()` |
| TV 焦点色 | - | `#69B4FF` | `Setting.getFocusColor()` |
| 字号 | 大 | 标准 | `Setting.getFontScaleMode()` |
| 高对比度 | 关 | 关 | `Setting.isHighContrast()` |
| 减少动画 | 关 | 关 | `Setting.isReduceMotion()` |
| 布局模式 | 自动 | TV 固定布局 | `Setting.getLayoutMode()` |
| 首页内容 | 推荐 | 点播主页 | `Setting.getHomeContent()` |
| 底部导航隐藏项 | 下载 | 不适用 | `Setting.getHiddenNavigationItems()` |
| 启动页面 | 关闭 | 点播 | `Setting.getBootStartMode()` |
| 系统开机自动启动 | 由启动页控制 | 关闭 | `Setting.isBootAutoStart()` |
| 点播失败自动换线路 | 开 | 开 | `Setting.isAutoFallbackLine()` |
| 点播失败自动跨站换源 | 开 | 开 | `Setting.isAutoFallbackSource()` |
| 点播自动播放下一集 | 开 | 开 | `PlayerSetting.isAutoNextEnabled()` |
| 首选音轨语言 | 跟随设备 | 跟随设备 | `PlayerSetting.getAudioLanguageIndex()` |
| 首选字幕语言 | 跟随设备 | 跟随设备 | `PlayerSetting.getTextLanguageIndex()` |
| GitHub 加速 | 关 | 关 | `Setting.isGitHubAcceleration()` |
| 配置通知 | 关 | 关 | `Setting.isConfigNoticeEnabled()` |
| 广告地址过滤 | 开 | 开 | `Setting.isAdblock()` |
| 搜索线程 | 5 | 5 | `Setting.getSearchThreads()` |
| 弹幕加载 | 开 | 开 | `DanmakuSetting.isLoad()` |
| 弹幕自动搜索 | 开 | 开 | `DanmakuSetting.isAuto()` |
| 弹幕默认显示 | 关 | 关 | `DanmakuSetting.isShow()` |
| 直播自动换线路 | 开 | 开 | `LiveSetting.isChange()` |
| 直播重连延迟 | 3 秒 | 3 秒 | `LiveSetting.getReconnectDelaySeconds()` |
| 直播播放超时 | 10 秒 | 10 秒 | `LiveSetting.getPlayTimeoutSeconds()` |

修改默认值前必须考虑已有用户：SharedPreferences 已保存的值不会随代码默认值变化。只改 getter 默认值只影响从未写过该键的新用户。

`theme_mode` 未保存时返回 `THEME_SYSTEM`。因此本次 `1.0` 发布中，手机端新安装会跟随系统，升级用户此前主动选择的浅色、深色或跟随系统不会被覆盖；TV 仍由 `applyThemeMode()` 强制使用深色。

## 设置界面位置

### 手机

主设置页面：

```text
app/src/mobile/res/layout/fragment_setting.xml
app/src/mobile/java/com/imedia/box/ui/fragment/SettingFragment.java
```

分页面：

- `fragment_setting_player.xml` / `SettingPlayerFragment.java`
- `fragment_setting_decode.xml` / `SettingDecodeFragment.java`
- `fragment_setting_preload.xml` / `SettingPreloadFragment.java`
- `fragment_setting_danmaku.xml` / `SettingDanmakuFragment.java`

### TV

主设置页面：

```text
app/src/leanback/res/layout/activity_setting.xml
app/src/leanback/java/com/imedia/box/ui/activity/SettingActivity.java
```

分页面：

- `activity_setting_player.xml` / `SettingPlayerActivity.java`
- `activity_setting_decode.xml` / `SettingDecodeActivity.java`
- `activity_setting_preload.xml` / `SettingPreloadActivity.java`
- `activity_setting_danmaku.xml` / `SettingDanmakuActivity.java`

### 共用弹窗

无障碍、音频、视频、字幕、弹幕、下载策略、直播设置、WebDAV 和存储隐私等弹窗主要位于：

```text
app/src/main/java/com/imedia/box/ui/dialog/
app/src/main/res/layout/dialog_*.xml
app/src/main/res/layout/view_*.xml
```

只有交互确实不同的弹窗才在 `mobile` 和 `leanback` 分别实现。

### 局域网管理页面

局域网页面读取和写入设置时还要同步：

```text
app/src/main/assets/index.html
app/src/main/assets/js/script.js
app/src/main/java/com/imedia/box/server/process/Manage.java
```

`Manage` 负责 Java 设置与 Web 表单键的映射。只修改网页而不修改 `Manage` 不会保存；只修改 Java 而不修改脚本则网页不会显示入口。

## 修改已有默认值

### 只影响新用户

在对应 getter 中修改默认参数：

```java
public static boolean isExampleEnabled() {
    return Prefers.getBoolean("example_enabled", true);
}
```

已有用户如果已经保存 `example_enabled`，继续使用保存值。

### 迁移已有用户

只有产品明确要求时才迁移。使用独立迁移标记，并区分“键不存在”和“用户主动选择了旧值”：

```java
private static final String MIGRATION = "settings_migration_v2";

public static void migrate() {
    if (Prefers.getBoolean(MIGRATION)) return;
    if (!Prefers.getPrefers().contains("example_enabled")) {
        Prefers.put("example_enabled", true);
    }
    Prefers.put(MIGRATION, true);
}
```

不要每次启动强制写默认值，否则用户无法关闭或删除设置。

### 兼容旧键

现有代码常用下面模式读取旧值：

```java
int legacy = Prefers.getBoolean("old_key") ? NEW_ON : NEW_OFF;
int value = Prefers.getPrefers().contains("new_key")
        ? Prefers.getInt("new_key", legacy)
        : legacy;
```

确认新键已稳定使用后也不要立即删除旧键兼容，至少覆盖仍受支持的升级路径。

## 新增二元设置

假设新增“显示示例信息”：

### 1. 在设置类封装

```java
public static boolean isExampleVisible() {
    return Prefers.getBoolean("example_visible", true);
}

public static void putExampleVisible(boolean visible) {
    Prefers.put("example_visible", visible);
}
```

键名使用稳定的 `snake_case`，一旦发布不要随意改名。

### 2. 添加字符串

至少同步：

```text
app/src/main/res/values/strings.xml
app/src/main/res/values-zh-rCN/strings.xml
app/src/main/res/values-zh-rTW/strings.xml
```

只属于手机或 TV 的字符串放到对应源码集的同名语言目录。不要在 XML 或 Java 中硬编码用户可见文字。

### 3. 添加手机设置行

在 `fragment_setting.xml` 使用 `Widget.IBox.SettingSwitch`，或沿用当前设置页的完整标题/摘要/开关行。然后在 `SettingFragment`：

1. 初始化 checked/text 状态。
2. 注册 click 或 checked listener。
3. 调用设置类的 put 方法。
4. 立即刷新受影响界面或提示重启。

### 4. 添加 TV 设置行

在 `activity_setting.xml` 添加可聚焦控件，并在 `SettingActivity` 完成初始化和点击处理。检查上下左右焦点顺序，不能依赖触摸。

### 5. 应用行为

- 即时开关：保存后立即通知业务层。
- 主题/字号：保存后触发页面 `recreate()` 或全局主题刷新。
- 播放器参数：明确是立刻作用于当前播放器，还是下次播放生效。
- 网络参数：取消旧请求并安全刷新，不要同时启动多个重复加载任务。
- 系统级行为：检查 Manifest 权限、Receiver 和不同 Android 版本限制。

### 6. 同步局域网页面

需要远程管理时，在 `Manage.values()` 输出当前值，在保存 switch 中接收值，并在 `script.js` 中增加控件。用户可见键名和 Java 偏好键可以不同，但映射必须唯一。

### 7. 添加测试

至少覆盖：

- 默认值。
- 写入后读取。
- 超范围值 clamp。
- 旧键迁移。
- 手机和 TV 不同默认值。
- 需要刷新时对应事件是否发出。

## 新增枚举或数字设置

使用常量和 clamp，避免业务层接收到非法值：

```java
public static final int MODE_AUTO = 0;
public static final int MODE_ONE = 1;
public static final int MODE_TWO = 2;

public static int getExampleMode() {
    return Math.clamp(Prefers.getInt("example_mode", MODE_AUTO), MODE_AUTO, MODE_TWO);
}

public static void putExampleMode(int mode) {
    Prefers.put("example_mode", Math.clamp(mode, MODE_AUTO, MODE_TWO));
}
```

界面选项使用 `string-array` 或明确的 id/value 映射。不要假定数组下标永远等于持久化值；两者不一致时建立显式转换。

## 启动页面设置

当前值：

| 常量 | 值 | 含义 |
| --- | ---: | --- |
| `BOOT_START_OFF` | 0 | 手机不跳转 |
| `BOOT_START_VOD` | 1 | 点播 |
| `BOOT_START_LIVE` | 2 | 直播 |
| `BOOT_START_HISTORY` | 3 | 历史 |

手机桌面启动和系统开机 Receiver 都需要处理新增值。TV 的启动页面和系统“开机自动启动”是两个设置：前者决定进入哪个页面，后者决定设备开机时是否启动 App。

修改时同步检查：

- `Setting.getBootStartMode()` 的范围。
- 三套语言中的 `select_boot_start`。
- 手机 `HomeActivity`、手机 `BootReceiver`。
- TV `HomeActivity`、TV `BootReceiver`、TV 设置页。
- 从桌面启动、完全停止后启动、系统开机广播三条路径。

## 手机底部导航设置

底部导航使用位掩码：

- `NAVIGATION_HOME`
- `NAVIGATION_LIVE`
- `NAVIGATION_LIBRARY`
- `NAVIGATION_SETTINGS`
- `NAVIGATION_DOWNLOADS`

设置页自身不能被隐藏，`NAVIGATION_HIDEABLE` 只包含允许用户隐藏的项目。手机首次使用默认隐藏下载，已有 `hidden_navigation_items` 的用户保留原选择。

修改导航时同步检查：

- `Setting` 中的位掩码、默认值和旧 `navigation_hidden` 兼容。
- `app/src/mobile/res/layout/view_app_navigation.xml`。
- 手机 `HomeActivity` 的目的地、选中状态和返回逻辑。
- 设置页隐藏项目弹窗及其摘要。
- 所有项目隐藏边界；至少保留可进入设置的路径。

## 主题和无障碍设置

主题、主题色、焦点色、字号、高对比度和减少动画在布局加载前由 `BaseActivity` 和 `AppAccessibility` 应用。修改时注意：

- `Setting.applyThemeMode()` 在 TV 强制深色，在高对比度时也使用深色。
- 自定义主题色必须有可读的 on-color。
- TV 焦点色使用独立 overlay，不应直接覆盖主主题色。
- 字号通过 Context/DisplayMetrics 应用，不能只改一个 TextView。
- 减少动画要覆盖 Activity 和 Dialog，不只关闭某一个动画文件。

详细视觉规则见 [UI_DESIGN_SYSTEM.md](UI_DESIGN_SYSTEM.md)。

## 设置修改验收

- 新安装默认值正确。
- 升级安装保留已有用户选择。
- 手机和 TV 页面都能读取、修改并立即显示当前值。
- 关闭再启动 App 后设置仍然存在。
- 输入越界或旧数据不会导致数组越界、崩溃或错误摘要。
- 主题相关设置在日间、夜间、高对比度和 TV 焦点下可读。
- 启动页、Receiver、播放器或网络等受影响入口实际工作。
- 局域网页面如有对应入口，读取和保存一致。
- 三套语言没有缺失字符串或按钮省略。
- 相关单元测试、手机和 TV 编译通过。
