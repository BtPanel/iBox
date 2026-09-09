# iBox 文档索引

本文档目录以“要完成什么”为入口。修改代码前先阅读对应文档，修改行为后同步更新相关说明。

## 按任务选择文档

| 任务 | 必读文档 | 通常修改的位置 |
| --- | --- | --- |
| 第一次配置开发环境 | [构建与发布](BUILD.md) | `local.properties`、`vendor/media3` |
| 理解手机/TV 双端结构 | [项目架构](ARCHITECTURE.md) | `app/src/main`、`mobile`、`leanback` |
| 添加点播或直播订阅 | [配置与内置订阅](CONFIGURATION.md) | App 设置页或 `Config` 初始化 |
| 内置点播、直播和 EPG | [配置与内置订阅](CONFIGURATION.md) | `assets`、`Config`、`LiveSetting` |
| 修改颜色、按钮、海报和文字 | [UI 与组件规范](UI_DESIGN_SYSTEM.md) | `ibox_*.xml`、端侧布局 |
| 修改默认值或新增设置开关 | [设置项开发](SETTINGS.md) | `setting/`、设置页、字符串资源 |
| 检查崩溃、闪屏或播放循环 | [测试与排错](TESTING.md) | ADB、Logcat、播放器与控制器 |
| 准备提交、合并或数据库升级 | [维护指南](MAINTENANCE.md) | Git、Room Migration、测试 |
| 构建四个正式 APK 和源码包 | [构建与发布](BUILD.md) | Gradle、`Release/`、校验文件 |

## 文档清单

- `ARCHITECTURE.md`：源码集、模块、启动、配置、播放、数据和主题链路。
- `FEATURES.md`：当前源码已经实现的用户能力和使用边界。
- `CONFIGURATION.md`：TvBox 配置、直播格式、EPG、内置订阅及兼容性。
- `UI_DESIGN_SYSTEM.md`：设计令牌、组件、资源覆盖和逐类 UI 修改方法。
- `SETTINGS.md`：设置存储、默认值、双端入口、迁移和新增设置步骤。
- `TESTING.md`：测试矩阵、MuMu/ADB、日志采集和常见问题定位。
- `BUILD.md`：依赖、签名、构建、安装、版本、校验及源码归档。
- `MAINTENANCE.md`：日常分支、提交、合并、迁移和发布检查表。

## 文档维护规则

1. 文档描述必须以当前源码为准；计划中的功能必须明确写为“尚未实现”或“建议实现”。
2. 命令默认在仓库根目录执行，并注明 PowerShell、Bash 或 ADB 环境。
3. 示例地址使用 `example.com`，不提交私人订阅、令牌和签名信息。
4. 新增设置时更新 `SETTINGS.md`；新增公共 UI 资源时更新 `UI_DESIGN_SYSTEM.md`。
5. 修改构建版本、任务、输出路径或签名方式时更新 `BUILD.md` 和根目录 `README.md`。
6. 删除入口、页面或调试工具时，同时删除文档中的对应说明。
7. 提交前检查 Markdown 相对链接，确保仓库内文件均能访问。

## 当前重要约定

- `mobile` 包名为 `com.imedia.box`，`leanback` 包名为 `com.imedia.box.tv`。
- 当前版本号为 `1.0 (1)`；正式发布后 `versionCode` 必须递增。
- 手机端新安装默认跟随系统日夜模式；已有用户明确选择的主题模式保持不变。
- `artwork/app-icon/` 是正式图标母版与导出包，清理缓存或发布文件时不得删除。
- 手机端首次使用默认隐藏底部“下载”，已保存过导航设置的用户保留自己的选择。
- 手机和 TV 的启动页面都支持点播、直播和历史；TV 另有系统开机自动启动开关。
- 项目不再提供“组件展示”页面，UI 通过真实页面和状态矩阵验收。
