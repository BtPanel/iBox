# iBox UI「去 AI 味」设计走查报告

> 方法论来源：[unslop](https://github.com/heydamir/unslop)（Damir Sokolovsky，CC BY 4.0）
> 本报告把 unslop 的九遍「redline」走查翻译到 iBox 的 Android 原生 View 体系，对照 `docs/UI_DESIGN_SYSTEM.md` 与 `ibox_*` 语义令牌。
> 走查日期：2026-11-05

## 一、总体结论

**iBox 的 UI 已经走在了正确的方向上，几乎没有典型的「AI 味」。**

- 配色采用「中性灰阶承载界面 + 单一绿色强调 + 金色评分」的语义令牌体系，没有紫色渐变、发光、渐变文字这些标志性 slop。
- 文案干净，全库扫描**未发现任何 emoji 图标、感叹号口号、夸大词**（如 supercharge / seamless / AI-powered）。
- 间距遵循 4dp 网格，语义色统一收敛在 `ibox_colors.xml` / `ibox_tv_colors.xml`。

按 unslop 的判定标准，这属于 **「轻 slop / 已接近设计完成」** 一档：不是要重设计，而是把残留的几处零散值收敛进令牌体系，让每个数值都「有意为之」。

## 二、发现的残留问题（按优先级）

### P1 · 硬编码字号偏离语义令牌

`UI_DESIGN_SYSTEM.md` 已定义 `TextAppearance.IBox.*` 字号体系（14/16/18/20/22/28sp 等），但部分布局仍在局部硬编码，出现了令牌之外、或与该语义场景不符的零散值：

| 字号 | 位置 | 问题 |
| --- | --- | --- |
| `15sp` | `adapter_live_setting_action.xml`、`dialog_danmaku_search.xml` | 不在令牌字号阶梯内，介于 14/16 之间 |
| `13sp` | `dialog_epg_subscription.xml` | 与「辅助文字 12sp / 正文 14sp」之外的孤立值 |
| `18sp` | `dialog_danmaku_search.xml`、`dialog_timer.xml` | 手机端正文应为 14sp，18sp 疑似误用 TV 字号 |
| `28sp` | `dialog_remote_access.xml` | 手机端页面标题应为 20sp，28sp 疑似误用 TV 字号 |
| `42sp` | 双端 `dialog_timer.xml` | 计时器大数字，可考虑收敛为专用令牌 |
| `62sp`/`22sp` | `view_widget_live.xml`（TV） | 直播大数字与小字号，建议命名语义令牌 |

**建议**：手机端把 `13/15/18/28sp` 收敛回 `TextAppearance.IBox.*`；TV 的 `62sp` 这类「特殊展示数字」建一个明确的 `ibox_text_*` 令牌，而不是散落在布局里。

### P2 · 少数「非 4 倍数」间距需人工确认

绝大多数非 4 倍数是合理的平台/比例尺寸（`44dp` 手机导航栏、`52/56dp` TV 焦点、`66/78/108dp` 海报宽高比、`68dp` 工具栏等），**不要机械改成 4 倍数**。需要人工确认是否偏离节奏的少量值：

- `adapter_search_word.xml` 行高 `42dp`（与列表通用行高不一致）
- `dialog_danmaku_search.xml` / `dialog_control.xml` 多处 `44dp`（若是按钮高度，应与 `ibox_component_height` 统一）
- `view_home_loading.xml` 骨架屏 `150dp`（骨架卡片高度是否与真实海报比例一致）

### P3 · 文案规范的两处遗留

- `values-zh-rTW/strings.xml` 与 `values-zh-rCN/strings.xml` 中 `danmaku_color_gradient` = `渐变`：这是弹幕颜色选项，属功能语义，**保留**，非 UI slop。
- 建议抽空核对 settings/提示类文案是否还有多余句号、全大写标签（`NAVIGATION` 类），本次扫描未发现，但中文翻译需人工读一遍。

## 三、unslop 九遍映射到 Android 的要点

unslop 面向 Web（Tailwind/CSS），以下是它在 iBox 的对应物，供后续走查直接套用：

| unslop 遍 | Web 概念 | iBox / Android 对应 |
| --- | --- | --- |
| 1. Spacing | 4px 网格、gap/padding | `ibox_space_1..7`（4dp 网格）、`ibox_dimens.xml` |
| 2. Color | 中性 ramp + 单一 accent | `ibox_*` 语义色、`ibox_primary` 唯一强调、`ibox_content_accent` 金色 |
| 3. Icons | lucide 线框、单一 stroke | 已有 Lucide/Material 矢量、单一 `contentDescription` |
| 4. Copy | 去 emoji/感叹号/夸大 | `strings.xml` 三语（zh-rCN / zh-rTW / default） |
| 5. Type | 字号阶梯、400/500 字重 | `TextAppearance.IBox.*` |
| 6. Layout | 桌面比例、信息层级 | 手机 48dp 触摸 / TV 56dp 焦点，双端布局 |
| 7. Details | 嵌套圆角、图表层级 | `shape_*`、selector、`ibox_dialog_radius_*` |
| 8. Visuals | 真实产品预览 | 海报真实数据、电影名遮罩 `shape_vod_name` |
| 9. Motion | 去无意义循环动画 | ripple、焦点 selector、少量淡入 |

**注意**：iBox 是影音客户端，其「视觉」来自真实海报/剧集数据，本身就满足 unslop 的「show the product, not a glossy blob」——这是它天然免疫大多数 slop 的根本原因。

## 四、行动建议

1. **P1 先做**：收敛手机端 `13/15/18/28sp` 到 `TextAppearance.IBox.*`，为 TV 的展示数字建专用令牌。这是唯一「动手改」的部分。
2. **P2 逐条确认**：`42/44/150dp` 三处，确认后要么对齐令牌要么加注释说明「有意为之」。
3. **P3 人工读一遍**中文翻译文案即可，无需改代码。

其余部分不建议改动——iBox 已经是「设计完成」状态，符合 unslop 的「No slop: 不要为了显得不一样而改动一个好界面」原则。

## 五、已实施改动（2026-11-05）

按 P1 收敛了手机端 5 处偏离令牌的字号（`git` 基线 `3077adf`，master）：

| 文件 | 原值 | 改为 |
| --- | --- | --- |
| `app/src/main/res/layout/adapter_live_setting_action.xml` | 列表标题 `15sp` | `@dimen/ibox_text_item_title`(14sp) |
| `app/src/mobile/res/layout/dialog_danmaku_search.xml` | 标题 `18sp`+bold | `@style/TextAppearance.IBox.DialogTitle`(20sp) |
| `app/src/mobile/res/layout/dialog_danmaku_search.xml` | 搜索输入框 `15sp` | `@dimen/ibox_text_body`(14sp) |
| `app/src/mobile/res/layout/dialog_timer.xml` | 标题 `18sp`+bold | `@style/TextAppearance.IBox.DialogTitle` |
| `app/src/mobile/res/layout/dialog_epg_subscription.xml` | 说明文字 `13sp` | `@style/TextAppearance.IBox.Supporting`(12sp) |

**保留的功能性大数字（非 slop，已确认）：**
- `dialog_remote_access.xml` PIN 码 autoSize `18–28sp`（配对码展示）
- 双端 `dialog_timer.xml` 计时器 `42sp`（monospace + tnum）
- TV `view_widget_live.xml` 时钟 `20sp`、直播大数字 `62sp`、频道名 `22sp`

验证：手机 + TV 两端 `compile*JavaWithJavac` 与 `process*DebugResources` 均通过。

## 六、P2 间距收敛（已实施）

分析结论：`44dp` 是项目里跨双端、跨 10+ 页面**一致使用的「紧凑控件高度」共识值**（分段筛选组、紧凑按钮、搜索框、设置行、chip 行等），语义清晰但未命名。按 unslop「Same thing, same size + 给共识值一个名字」原则提炼为令牌：

- 新增 `@dimen/ibox_control_height` = `44dp`（`ibox_dimens.xml`）。
- 将 28 处 `layout_height="44dp"` 替换为 `@dimen/ibox_control_height`（跨 `main` / `mobile` / `leanback` 三源码集，含离线下载、远程访问、弹幕搜索、线路控制、直播、TV 搜索、TV 仓库弹窗等）。
- 未误伤 `layout_width="44dp"`（图标/宽度用途，保持不变）。

**保留为「有意为之」的值（不改）：**
- `adapter_search_word.xml` 热词行 `42dp`、TV `dialog_live_source_input.xml` 底部按钮行 `42dp`——语义不同（手机热词 vs TV 按钮），各自孤立，强行合并令牌反而混淆，留待人工确认。
- `view_home_loading.xml` 骨架海报占位 `150dp`——注释已说明模拟 3 列海报网格比例，合理。

验证：`processMobileArm64_v8aDebugResources` + `processLeanbackArm64_v8aDebugResources` 通过。

## 七、P3 文案通读（已实施）

三语（default / zh-rCN / zh-rTW）× 三源码集（main / mobile / leanback）全量扫描，结论：**文案高度干净，无 AI 味**。

- 唯一命中 unslop「No exclamation marks」的是播放器切集边界提示 `error_play_next`/`error_play_prev`（「已经是最后一集了！」→ 去感叹号），三语共 6 处已改。
- 全大写标签仅 `DIYP`（协议名，translatable=false）与 `LIVE`（直播状态标记），均为专有名词/状态，保留。
- 无问候式标题、无尾随句号短标签、无 `PRO/New ✨/Beta` 徽章、无 `Loved by/Trusted by` 造假宣称、无 `supercharge/seamless/AI-powered` 夸大词。
- 「推荐」为内容分类 tab 的功能标签，非装饰徽章，正常。

验证：两端 `process*DebugResources` 通过。

