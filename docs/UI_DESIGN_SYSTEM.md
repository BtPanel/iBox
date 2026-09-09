# UI 与组件规范

本文是 iBox 手机与 TV 界面的项目级约定，也说明以后如何修改颜色、布局、按钮、海报、文字和焦点。新页面应复用已有语义资源，避免重新建立一套局部样式。

当前项目不提供“组件展示”页面。UI 验收必须进入真实页面，覆盖正常、按下、选中、禁用、错误和 TV 聚焦状态。

## 资源分层

| 资源 | 路径 | 用途 |
| --- | --- | --- |
| 日间语义颜色 | `app/src/main/res/values/ibox_colors.xml` | 手机日间和共用基础色 |
| 夜间语义颜色 | `app/src/main/res/values-night/ibox_colors.xml` | 手机夜间覆盖 |
| TV 语义颜色 | `app/src/leanback/res/values/ibox_tv_colors.xml` | TV 稳定深色方案 |
| TV 夜间语义覆盖 | `app/src/leanback/res/values-night/ibox_tv_colors.xml` | 与 TV 默认完整同步，覆盖共用夜间限定符 |
| 间距、字号、组件尺寸 | `app/src/main/res/values/ibox_dimens.xml` | 双端默认令牌 |
| TV 尺寸覆盖 | `app/src/leanback/res/values/tv_dimens.xml` | 大屏字号、边距和操作区 |
| 主题与文字/按钮样式 | `app/src/main/res/values/ibox_styles.xml` | Material 主题和基础组件 |
| 可复用组件 | `app/src/main/res/values/ibox_components.xml` | 输入框、海报名、设置行等 |
| 弹窗和动画 | `app/src/main/res/values/ibox_ui_system.xml` | Alert、BottomSheet、SideSheet |
| 主题色覆盖 | `app/src/main/res/values/ibox_theme_overlays.xml` | 预设主题颜色和无障碍覆盖 |
| 手机差异 | `app/src/mobile/res/values/styles.xml` | 底部导航、手机按钮和页面主题 |
| TV 差异 | `app/src/leanback/res/values/styles.xml` | 焦点、TV 按钮和页面主题 |

Android 资源会按源码集和限定符覆盖。修改 `main` 后必须检查两端；只想改一个端时放到 `mobile` 或 `leanback`，不要让端侧判断散落在共用布局里。

## 核心原则

1. 布局不直接新增十六进制颜色；使用 `ibox_*` 语义色或 selector。
2. 间距采用 4dp 网格，优先使用 `ibox_space_1` 至 `ibox_space_7`。
3. 手机触摸区域至少 48dp，TV 图标操作区域默认 56dp。
4. TV 可操作控件必须能获得焦点，并有边框、背景或尺寸变化，不能只靠文字颜色。
5. 命令按钮文字保持一行，优先调整布局宽度和间距，最后才使用已有自动缩放。
6. 海报电影名固定一行、居中、尾部省略，手机与 TV 字号一致性由平台令牌控制。
7. 地址编辑框保持一行并允许横向滚动；列表地址使用中间或尾部省略。
8. 说明正文允许换行，不为了追求整齐把重要信息强行截断。
9. 页面区块不套多层装饰卡片；一个组件只使用一个主要视觉容器。
10. 修改播放器深色界面时使用 `ibox_player_*`，不要复用普通页面的浅色 surface。

## 颜色系统

### 语义颜色

常用资源：

| 角色 | 资源 |
| --- | --- |
| 主操作和选中 | `ibox_primary`、`ibox_on_primary` |
| 次级选中容器 | `ibox_primary_container`、`ibox_on_primary_container` |
| 页面和卡片 | `ibox_surface`、`ibox_surface_variant`、`ibox_surface_container*` |
| 主/次文字 | `ibox_text_primary`、`ibox_text_secondary` |
| 禁用 | `ibox_text_disabled`、`ibox_surface_disabled` |
| 边框与分隔 | `ibox_outline`、`ibox_outline_strong`、`ibox_divider` |
| 错误/成功/警告 | `ibox_error`、`ibox_success`、`ibox_warning` |
| 播放器 | `ibox_player_surface`、`ibox_player_text_*`、`ibox_player_control_*` |
| 内容强调 | `ibox_content_accent`（珊瑚色，不替代正文色） |
| TV 焦点 | `ibox_focus`、`ibox_focus_surface`、`ibox_focus_outline` 和端侧 focus selector |

默认孔雀青／珊瑚色方案的精确值如下。自定义主题覆盖仍须满足相同的语义配对和对比度要求。

| 资源 | 手机日间 | 手机夜间 | TV 固定深色 |
| --- | --- | --- | --- |
| `ibox_surface_container` | `#F6F9F8` | `#0D1513` | `#080F0D` |
| `ibox_surface` | `#FFFFFF` | `#17201E` | `#141D1A` |
| `ibox_surface_variant` | `#E1E5E3` | `#26312E` | `#23302C` |
| `ibox_text_primary` | `#13201D` | `#E8F1EE` | `#F0F7F5` |
| `ibox_text_secondary` | `#52615D` | `#AABAB5` | `#A9B8B3` |
| `ibox_primary` | `#006B5F` | `#62DAC4` | `#68DFC9` |
| `ibox_on_primary` | `#FFFFFF` | `#003731` | `#003731` |
| `ibox_primary_container` | `#B7EBDD` | `#164E46` | `#164E46` |
| `ibox_on_primary_container` | `#00201C` | `#B7EBDD` | `#B7EBDD` |
| `ibox_content_accent` | `#D95D45` | `#FFB4A8` | `#FFB4A8` |
| `ibox_outline` | `#71817C` | `#71817C` | `#71817C` |
| `ibox_outline_strong` | `#697975` | `#73847F` | `#72827D` |
| `ibox_focus_surface` | `#FFFFFF` | `#26312E` | `#23302C` |
| `ibox_focus_outline` | `#006B5F` | `#62DAC4` | `#E2FFF8` |

正文和辅助文字与实际背景的对比度至少 **4.5:1**；必要的输入边界和焦点指示至少 **3:1**。透明色必须先与底图合成再计算，不能直接忽略 alpha。珊瑚色用于内容强调；日间珊瑚色不用于小字号正文。装饰分隔线不承担输入边界或焦点指示职责。

播放器和艺术图上的深色元数据使用跨日间／夜间／TV 一致的角色：

| 资源 | 值 |
| --- | --- |
| `ibox_player_surface` | `#0E1013` |
| `ibox_player_scrim` | `#B30E1013` |
| `ibox_player_text_primary` | `#F5F7FA` |
| `ibox_player_text_secondary` | `#CCD2DA` |
| `ibox_player_control_surface` | `#E6262D35` |
| `ibox_player_control_surface_focused` | `#F23A4550` |
| `ibox_player_control_outline` | `#8A7F8D9C` |

夜间和 TV 未重复声明的播放器控制角色沿用 `main/values`，不需要为相同值制造重复覆盖。海报站点、备注使用 `ibox_player_scrim` 与 `ibox_player_text_primary`；纯白／纯黑底图合成后的文字对比度均满足 4.5:1。不要把更透明的 `ibox_player_scrim_soft` 用作小字元数据的唯一底色。

应用文字通知统一使用 `Notify.show`／`Notify.showCompact` 的同一自定义布局：`ibox_player_surface`、`ibox_player_text_primary` 与 `ibox_player_control_outline`，正文为手机 14sp／TV 16sp，最小高度为 48dp／56dp，水平内边距 16dp、垂直内边距 12dp、圆角 8dp。短消息保持单行，长错误最多三行并在末尾省略。`ibox_notification_max_width` 为手机 360dp／TV 560dp，同时受屏幕宽度减去两侧 16dp／48dp 边距限制；底部偏移分别为 72dp／48dp。普通通知保持 LONG 时长，compact 保持 SHORT 与 1500ms 重复抑制；不要恢复系统 `Toast.makeText` 的另一种外观。

生产 Alert 统一使用 `IBoxAlertDialogBuilder`，由它在 Material 3 背景上应用 1dp `colorOutline` 语义描边。不要直接实例化上游 `MaterialAlertDialogBuilder`，也不要在截图或测试代码里临时补描边；高对比度下 Alert 必须继承调用页面的黑色表面、白色正文与黄色描边。

修改颜色时同时做以下检查：

- 手机日间和夜间的文字/背景对比度。
- 主题色覆盖后的 `onPrimary`、`onPrimaryContainer` 是否仍可读。
- TV 普通、聚焦、选中和“选中且聚焦”是否能区分。
- 高对比度开启后是否还有硬编码颜色逃过覆盖。
- 半透明背景叠在不同海报和壁纸上是否仍可读。

### 日夜模式和 TV

手机支持跟随系统、日间和夜间。基础模式由 `Setting.applyThemeMode()` 设置，主题覆盖在手机 `BaseActivity` 布局加载前应用。

TV 通过 `MODE_NIGHT_YES` 固定使用深色语义方案，不跟随系统切换为浅色。TV 仍支持主题色和独立焦点框颜色。Android 先按限定符选择资源，再按源码集覆盖；只有 `leanback/values` 无法阻止 `main/values-night` 胜出。因此 TV 的 `values/ibox_tv_colors.xml` 和 `values-night/ibox_tv_colors.xml` 必须保持完整角色与数值同步。修改共用夜间色时也要检查 TV 夜间有效资源，不能仅检查 TV 默认文件。

## 尺寸和文字

当前公共尺寸：

| 项目 | 手机 | TV |
| --- | ---: | ---: |
| 页面标题 | 20sp | 28sp |
| 弹窗标题 | 20sp | 22sp |
| 区块标题 | 14sp | 18sp |
| 列表标题 | 14sp | 16sp |
| 正文 | 14sp | 16sp |
| 辅助文字 | 12sp | 14sp |
| 地址 | 13sp | 15sp |
| 海报电影名 | 12sp | 14sp |
| 手机触摸区域 | 48dp | - |
| TV 图标操作区 | - | 56dp |
| 弹窗内容内边距 | 24dp | 24dp |
| 弹窗组间距 | 16dp | 16dp |
| 弹窗圆角 | 16dp | 12dp |

弹窗分别使用 `ibox_dialog_content_padding`、`ibox_dialog_group_spacing`；手机弹窗形状引用 `ibox_dialog_radius_mobile`（16dp），TV 弹窗形状引用 `ibox_dialog_radius_tv`（12dp）。Material 3 Alert 实际读取 `shapeAppearanceCornerExtraLarge`，因此 Alert overlay 必须把该属性连接到对应的 `ShapeAppearance.IBox.Dialog`／`.TV`，仅设置 `shapeAppearanceMediumComponent` 不会改变窗口裁切圆角。配置弹窗背景使用对应的平台圆角。共享弹窗动作高度使用 `ibox_touch_target`，解析为手机 48dp、TV 56dp。

文字样式：

- `TextAppearance.IBox.PageTitle`
- `TextAppearance.IBox.DialogTitle`
- `TextAppearance.IBox.Section`
- `TextAppearance.IBox.ItemTitle`
- `TextAppearance.IBox.Body`
- `TextAppearance.IBox.Supporting`
- `TextAppearance.IBox.Label`
- `TextAppearance.IBox.Address`
- `TextAppearance.IBox.PosterTitle`
- `TextAppearance.IBox.Player*`

除紧凑按钮的自动缩放下限外，普通界面文字不要低于 12sp。不要使用随屏幕宽度连续缩放的字号；通过限定布局、固定令牌和合理换行适配。

## 组件

### 按钮

| 样式 | 使用场景 |
| --- | --- |
| `Widget.IBox.Button.Primary` | 页面或弹窗唯一主操作 |
| `Widget.IBox.Button.Tonal` | 有强调但不是唯一主操作 |
| `Widget.IBox.Button.Outlined` | 次级命令、并列操作 |
| `Widget.IBox.Button.Text` | 取消、重置等低强调操作 |
| `Widget.IBox.Button.Danger` | 删除、清空等破坏性操作 |
| `Widget.IBox.Button.Segment` | 模式和互斥选项 |
| `Widget.IBox.Button.IconOnly` | 熟悉且无需文字的图标命令 |
| `Widget.IBox.IconButton` | 工具栏和列表图标操作 |

一个操作组只保留一个 Primary。熟悉的返回、搜索、删除、编辑、收藏、历史和下载操作优先使用现有矢量图标。新增图标优先复用项目已有 Lucide/Material 风格，不在同一组混用粗细、填充和光学尺寸明显不同的图标。

图标按钮必须有 `contentDescription`；TV 或不常见图标应提供 tooltip。不要用带文字的圆角矩形代替大家已经熟悉的纯图标操作。

示例：

```xml
<com.google.android.material.button.MaterialButton
    style="@style/Widget.IBox.Button.Outlined"
    android:layout_width="wrap_content"
    android:layout_height="@dimen/ibox_component_height"
    android:text="@string/action_name" />
```

### 输入和选择

- `Widget.IBox.TextInputLayout`：标准描边输入容器。
- `Widget.IBox.TextInputEditText`：普通文本。
- `Widget.IBox.TextInputEditText.Address`：URL、路径和订阅地址。
- `Widget.IBox.TextInputEditText.Compact`：搜索栏或紧凑输入。
- `Widget.IBox.SettingSwitch`：即时生效的二元设置。
- `Widget.IBox.CheckBox`：提交前的多项选择。
- `Widget.IBox.Chip.Filter`：筛选项和紧凑选项。

订阅地址输入框建议：

```xml
<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.IBox.TextInputLayout"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="@string/config_url">

    <com.google.android.material.textfield.TextInputEditText
        style="@style/Widget.IBox.TextInputEditText.Address"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="textUri"
        android:maxLines="1"
        android:scrollHorizontally="true" />
</com.google.android.material.textfield.TextInputLayout>
```

浮动 hint 被边框遮挡时，先检查是否错误设置了固定高度、负 margin、额外背景或过小顶部内边距，不要通过缩小 hint 字号掩盖问题。

### 订阅列表

使用 `Widget.IBox.RepositoryItem`。名称允许最多两行，地址保持一行；编辑和删除使用独立图标操作。当前订阅使用选中容器，TV 焦点再叠加明确边框，不能让“当前项”和“当前焦点”混成一个状态。

相关布局：

- `app/src/mobile/res/layout/adapter_repository.xml`
- `app/src/leanback/res/layout/adapter_repository.xml`
- 双端 `dialog_repository.xml` 和 `dialog_config.xml`

### 集数

集数使用 `Widget.IBox.Episode` 和对应 selector。要求：

- 普通、当前播放、不可用和 TV 聚焦颜色均可读。
- 当前集数使用主题 `colorPrimary`／`colorOnPrimary` 配对，所有主题与日夜模式下正文对比度至少 4.5:1。
- 长集数名保持单行，使用自动缩放下限，不改变网格高度。
- TV 焦点与“正在播放”必须是两个可同时识别的状态。
- 网格列宽、行高和容器边界稳定，动态文字不能推动整体布局。
- 手机版详情页的集数标题独占一行；排列与“全部”操作位于下一行并等宽伸缩，按钮使用自适应高度、至少 48dp 触控高度并允许最多两行，避免窄屏、大字号和较长翻译互相挤压或裁切。

相关资源：`selector_ibox_episode*`、双端 `adapter_episode*.xml`、集数弹窗和详情页布局。

## 海报和电影名

双端海报名使用 `Widget.IBox.PosterTitle`，固定一行、居中、尾部省略。主要布局：

- 手机：`adapter_vod.xml`、`adapter_vod_rect.xml`、`adapter_vod_oval.xml`
- TV：`adapter_vod.xml`、`adapter_vod_rect.xml`、`adapter_vod_oval.xml`、`adapter_home_vod.xml`

电影名位于海报底部半透明层。修改透明度和高度时：

1. 高度改 `ibox_vod_name_height`，手机默认 32dp，TV 覆盖为 40dp。
2. 字号改 `ibox_text_poster`，不要在每个布局分别硬编码。
3. 背景透明度改 `shape_vod_name` 所引用的颜色或渐变。
4. 同时检查浅色海报、深色海报和无图占位图。
5. 不改为双行，也不按电影名长度动态改变字号。

修复海报底部圆角时需要同时检查三层：

- `ShapeableImageView` 的 `shapeAppearanceOverlay`。
- 根容器的 foreground/focus drawable。
- 底部电影名背景 `shape_vod_name` 的左右下角。

只给图片设置圆角而让底部遮罩保持直角，会再次出现底部两个尖角。

## 手机布局

- 最小触摸区域 48dp，按下必须有 ripple 或明确状态。
- 分类标签的选中、checked 和按下使用主题的 `colorPrimaryContainer`／`colorOnPrimaryContainer` 配对；选中和 checked 优先于按下。必须按 Android 夜间限定符优先级解析每个主题覆盖，不能假设主题的 `colorOnPrimary` 在亮色夜间主色上仍可读。
- 竖屏、横屏和 `sw600dp` 可能使用不同布局，修改播放器时要检查三个目录。
- 底部导航使用稳定高度，标签保持完整；隐藏项由设置控制，不删除对应目的地逻辑。
- 系统栏、键盘、画中画和横竖屏切换不能遮挡主要操作。
- 播放器左侧快捷按钮应保持固定位置，显示条件只改可见性，不让其他控件跳位。

## TV 布局和焦点

- 所有可点击 View 必须 `focusable=true`；复杂区域显式配置 `nextFocus*`。
- 焦点从页面返回后应恢复到来源项，不跳到不可见或被遮挡控件。
- 远距离观看时焦点边框至少 2dp，并与背景保持对比。
- 焦点放大不能裁切到父容器外，也不能遮挡相邻关键文字。
- 滚动容器中的按钮、简介和集数应随焦点自动滚入可见区域。
- TV 不依赖 hover 或触摸反馈，必须使用 selector 覆盖 focused/selected/disabled。

主页分类和订阅项的“选中且聚焦”状态必须先于单一状态匹配；不能用聚焦填充抹掉选中信息。分类选中填充和文字读取 `?attr/colorPrimaryContainer`／`?attr/colorOnPrimaryContainer`，聚焦时额外叠加 2dp `?attr/focusFrameColor` 边框。仅聚焦使用 `ibox_focus_surface` 与主文字色。订阅项聚焦边框和海报浅色描边同样读取 `?attr/focusFrameColor`，响应独立的焦点色偏好；海报保留 4dp 深色底层、内缩 1dp 的 2dp 浅色描边与内外深色边界，保证亮图、暗图和中间亮度图上至少一个边界达到 3:1。使用焦点色填充的命令必须搭配 `?attr/focusContentColor`；默认／White 使用深色内容，高对比度使用黄色焦点与黑色内容。

## 播放器和详情页

- 播放器使用深色语义色，控制文字不得引用普通页面的次级灰色。
- 共享详情加载页使用 `ibox_surface_container`；诊断弹窗使用 DialogTitle、正文和辅助文字令牌，媒体名称保持一行尾部省略。播放器控制仍只使用 `ibox_player_*` 角色，页面主题切换不改变控制色。
- 固定的主要操作和可滚动的简介/集数区域应分开，避免文字增多时覆盖按钮。
- 同一信息不要重复显示，例如线路已在下方区域展示时不再放在进度条上方。
- 进度区域显示有意义的集数文本，例如“第 2 集”，不要只显示裸数字。
- 手机和 TV 按钮名称应短而明确；布局不足时先调整操作组，再依赖自动缩放。
- 弹幕开关关闭时可以隐藏发送入口；重新开启时应恢复并触发正确加载流程。

## 文字省略检查

按钮或标签出现 `...` 时按顺序处理：

1. 确认字符串本身没有手写省略号。
2. 检查父布局固定宽度、weight、margin 和相邻图标占用。
3. 对命令按钮使用已有自动缩放，不设置 `ellipsize=end`。
4. 地址、电影名等允许省略的内容再使用明确的 `ellipsize`。
5. 中文、英文、繁体分别检查最长字符串。
6. 手机检查窄屏和大字号；TV 检查聚焦放大后是否裁切。

可用以下命令查找高风险布局：

```powershell
rg -n "ellipsize|maxLines|singleLine|layout_width|textSize" `
  app/src/main/res app/src/mobile/res app/src/leanback/res
```

## UI 修改流程

1. 找到真实页面布局和 Adapter，不根据截图猜文件。
2. 判断修改属于共用令牌、手机覆盖还是 TV 覆盖。
3. 优先修改语义资源和公共组件，再修改页面局部布局。
4. 同步默认、夜间、聚焦、选中、禁用和错误状态。
5. 编译手机和 TV 变体，避免资源只在一个源码集通过。
6. 在模拟器/真机检查标准字号、大字号和超大字号。
7. 用长电影名、长地址、长按钮名、大量集数和空数据测试。
8. 截图对比日夜主题和 TV 焦点，并检查 Logcat 无资源或布局异常。

## UI 验收清单

- 没有新增页面局部十六进制颜色、重复字号和重复半径。
- 手机日间、夜间和高对比度均可读。
- TV 普通、聚焦、选中、禁用状态均可区分。
- 海报名一行居中、大小一致、底部遮罩透明度合适、四角完整。
- 集数字体在普通和选中状态均清楚。
- 所有按钮文字完整，没有无意义省略号或遮挡。
- 订阅名称、地址和输入框边框互不遮挡。
- 播放器控制、简介、集数和进度区域不重叠。
- 图标风格一致，图标按钮有无障碍描述。
- 动态内容不会改变工具栏、网格、按钮或导航的稳定尺寸。
