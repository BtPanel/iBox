# iBox UI 重设计规范（2026 · 清爽绿方向）

> 本文是 2026 年 11 月初 UI 重构时的**设计蓝图**，记录当时的目标方案与配色令牌。后续迭代中部分细节已演进（例如底部导航选中态从“胶囊底”改为“纯字体图标变色”、导航项数量按真实入口调整），因此**当前实现以 [UI_DESIGN_SYSTEM.md](UI_DESIGN_SYSTEM.md) 和各 `ibox_colors.xml` 源码文件为准**；本文与源码不一致时以源码为准。配色令牌表仍可作为配色体系的权威参考。

本方案废弃旧的孔雀青／珊瑚色方向，参考国产主流影视 App（爱奇艺的清新气质、优酷的稳妥中性、当贝影视／酷喵的 TV 焦点范式）重新定义视觉语言。原则：**内容是主角，界面保持安静**——中性灰阶承载界面，唯一的绿色强调色指示操作与选中。

## 设计关键词

- 简约：去装饰卡片、统一 12dp 圆角、一层视觉容器
- 沉浸：首页首屏给大图热播位，海报占满卡片
- 国产熟悉感：顶部/底部导航范式、金色评分、绿色主按钮

## 配色系统（三套同步）

### 手机日间（values/ibox_colors.xml）

| 角色 | 值 | 说明 |
| --- | --- | --- |
| ibox_primary | #00875A | 主操作绿，白字对比 4.6:1 |
| ibox_on_primary | #FFFFFF | |
| ibox_primary_container | #D7F2E4 | 选中容器浅绿 |
| ibox_on_primary_container | #00391F | |
| ibox_surface | #FFFFFF | 卡片 |
| ibox_surface_container | #F5F7F6 | 页面底 |
| ibox_surface_variant | #EBEFEC | |
| ibox_text_primary | #1A1F1C | |
| ibox_text_secondary | #5A6660 | |
| ibox_divider / surface_variant 系 | #E1E7E3 | |
| ibox_focus_outline | #00875A | |
| ibox_content_accent | #A96A10 | 评分金（国产影视惯例） |

### 手机夜间（values-night/ibox_colors.xml）

| 角色 | 值 |
| --- | --- |
| ibox_primary | #4ADE8F |
| ibox_on_primary | #052E1A |
| ibox_primary_container | #164C35 |
| ibox_on_primary_container | #A9F0CC |
| ibox_surface | #121714 |
| ibox_surface_container | #0F1412 |
| ibox_surface_variant | #212A25 |
| ibox_text_primary | #F2F6F4 |
| ibox_text_secondary | #A9B5AF |
| ibox_focus_outline | #4ADE8F |
| ibox_content_accent | #F5C065 |

### TV 影院深空（leanback ibox_tv_colors.xml，values 与 values-night 完整同步）

| 角色 | 值 |
| --- | --- |
| ibox_primary | #4ADE8F |
| ibox_on_primary | #052E1A |
| ibox_primary_container | #164C35 |
| ibox_surface | #101412 |
| ibox_surface_container | #0A0E0C |
| ibox_surface_variant | #1E2622 |
| ibox_text_primary | #F0F5F2 |
| ibox_text_secondary | #A9B5AF |
| ibox_focus_outline / ibox_focus | #FFFFFF |
| ibox_content_accent | #F5C065 |

高对比度主题保持黄色方案不变。派生的 alpha 色必须随主色同步：`ibox_navigation_item`、`ibox_interaction_selected`、`ibox_episode_selected_surface` 等一律改用新主色的 alpha 合成。

## 字阶与形状

| 令牌 | 手机 | TV |
| --- | --- | --- |
| 页面标题 | 20sp / 500 | 26sp / 500 |
| 卡片标题 | 14sp / 500 | 16sp / 500 |
| 正文 | 14sp | 16sp |
| 辅助文字 | 12sp | 14sp |
| 评分（content_accent） | 12sp / 500 | 14sp / 500 |

- 圆角统一：海报/卡片 12dp，按钮胶囊（全圆角），弹窗 16dp（手机）/12dp（TV）。
- 手机触摸区 ≥48dp，TV 操作区 ≥56dp；间距保持 4dp 网格。

## 组件规格要点

1. **主按钮**：胶囊形，primary 底 + on_primary 字；TV 上同一按钮用 primary 底 + 深绿字（#052E1A）保证暗底可读。
2. **分类 Chip**：选中 = primary_container 底 + on_primary_container 字（日间）/ primary 底 + 深色字（夜间）；按下态不覆盖选中态。
3. **海报卡**：无卡片容器，海报圆角 12dp 直出；片名一行居中尾部省略；底部遮罩用 player_scrim。评分行用 content_accent 金色。
4. **继续观看卡**：海报 + 底部 3dp 进度条（轨道 divider 色，进度 primary）；右上角剩余集数角标。
5. **TV 焦点**：白 2dp 描边 + 1.06 放大（新范式，替代旧青色焦点框）；选中且聚焦时先匹配选中再叠加描边；焦点内文字用 text_primary。
6. **底部导航（手机）**：悬浮胶囊导航（首页/直播/片库/设置/下载，默认隐藏下载）；选中项为字体图标变色（跟随主题色 `?attr/colorPrimary`），无胶囊底。

## 布局蓝图

### 手机首页（activity_home 重做）

```
状态栏（透明，内容透出）
├─ 沉浸热播区：大图（16:9→高约 210dp）+ 渐隐底
│   ├─ 左下：角标(独播/热映) + 片名(20sp) + 评分/年份/类型
│   └─ 播放胶囊按钮
├─ 继续观看（横滑行，卡宽 ~110dp，带进度条）
├─ 分类 Chip 横滑行（推荐/电影/剧集/直播/…）
├─ 海报网格 3 列（竖屏）/ 5 列（sw600dp）
└─ 底部导航 5 项（悬浮胶囊，稳定高度，主题色选中态）
```

### TV 首页（保持信息架构，更新视觉）

```
顶部横导航（首页·电影·剧集·综艺·直播·设置），选中项主色下划线
├─ 焦点大卡（左 55%）：角标 + 片名 + 评分 + 简介 + 播放/收藏
├─ 右侧推荐位两枚
└─ 内容行横滚：最近更新 / 猜你喜欢 / …
```

## 实施顺序与回滚

1. 配色令牌三套替换（本阶段，一次提交）
2. 手机首页重做（activity_home + HomeActivity + adapter）
3. TV 焦点视觉更新
4. 详情页/播放器页跟进

每步单独提交；出现问题时按页回滚，整体回滚用 `git reset --hard pre-ui-redesign`。
