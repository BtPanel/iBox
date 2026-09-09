# 维护指南

本文规定日常修改、分支、提交、合并、数据迁移、清理和发布流程。构建命令见 [BUILD.md](BUILD.md)，测试细节见 [TESTING.md](TESTING.md)。

## 修改原则

- 共用业务放在 `app/src/main`，只在确有交互差异时拆到 `mobile` 和 `leanback`。
- 播放规则放在 `playback/`、`player/` 或解析层，Activity 主要处理输入、焦点和展示。
- 设置键通过 `setting/` 类封装，不在多个 UI 文件重复 `Prefers` 键名。
- UI 使用语义颜色、尺寸和公共组件，TV 同步检查焦点状态。
- Room 实体变更必须有数据库迁移，不能依赖破坏性重建。
- 网络、文件和数据库操作不能阻塞主线程。
- 不提交私人订阅、签名文件、密码、令牌、日志和构建缓存。

## 开始修改前

```powershell
git status --short --branch
git log --oneline --decorate -5
```

确认：

1. 当前分支是否符合任务要求。
2. 工作区是否已有未提交改动。
3. 未提交改动属于谁、是否与本次文件重叠。
4. 当前版本号和最近标签。
5. 问题是否可以在当前 APK 稳定复现。

已有改动默认需要保留。不要用 `git reset --hard`、`git checkout --` 或清理命令覆盖不明来源的工作。

## 分支流程

UI 或较大功能不要直接在 `master` 开发。工作区干净时：

```powershell
git switch master
git switch -c codex/ui-refinement
```

分支名使用 `codex/` 前缀和明确主题，例如：

- `codex/builtin-sources`
- `codex/tv-focus-fix`
- `codex/player-danmaku`
- `codex/settings-history-start`

开发期间定期检查：

```powershell
git status --short
git diff --check
git diff --stat
```

不要把无关格式化、构建产物或用户已有改动混进同一提交。

## 提交流程

提交前：

1. 阅读完整 diff。
2. 运行与风险相称的测试。
3. 安装到手机或 TV 实测。
4. 更新行为相关文档。
5. 确认没有密钥、订阅和日志。

```powershell
git diff --check
git status --short
git add <本次修改的明确文件>
git diff --cached --check
git diff --cached --stat
git commit -m "描述本次行为变化"
```

不要使用 `git add .` 作为默认习惯，尤其是在工作区有其他改动时。提交信息描述用户可观察行为，例如“Add history startup option”，不要只写“update”。

## 合并到 master

功能分支测试通过并得到确认后：

```powershell
git switch master
git merge --ff-only codex/ui-refinement
```

如果 `--ff-only` 失败，先查看分支关系：

```powershell
git log --oneline --graph --decorate --all -20
```

再决定 rebase 或保留 merge commit，不要通过重置强行制造快进。合并后重新运行至少一次关键测试和双端编译，因为最终 `master` 状态才是发布对象。

删除已合并本地分支：

```powershell
git branch -d codex/ui-refinement
```

## Git 历史和发布历史

- 普通开发不清空 Git 历史。
- 已推送或已发布标签对应的提交不应 amend、rebase 或强制移动标签。
- “源码包不带 Git 历史”应通过归档时排除 `.git/` 实现，而不是删除仓库历史。
- 只有明确要建立全新仓库且已备份时，才考虑重写根提交；这不是日常发布步骤。
- 构建缓存、Release 产物和 Git 历史是三类不同内容，不要混用“清理”操作。

本项目在 `1.0` 发布前执行过一次明确授权的历史压缩：最终源码作为 `master` 的单一根提交，`v1.0` 标签指向该提交。此后恢复正常增量提交规则，不再重写已发布的 `v1.0` 历史；发布备份始终排除 `.git/`。

## 双端修改检查

| 改动类型 | 手机 | TV | 共用层 |
| --- | --- | --- | --- |
| 业务/解析 | 编译和实测 | 编译和实测 | 优先实现 |
| 设置默认值 | 设置页摘要 | 设置页摘要和焦点 | getter/putter |
| UI 颜色/字号 | 日夜和大字号 | 深色、主题和焦点 | 语义令牌 |
| 播放控制 | 触摸、手势、PIP | 遥控器、全屏焦点 | Controller/Player |
| 系统启动 | 手机 Home/Receiver | TV Home/Receiver | 设置常量 |
| 订阅管理 | 触摸弹窗 | 遥控器弹窗 | Config/Setting |

只改共用资源也可能导致端侧覆盖失配；必须编译两个 mode。

## Room 数据库迁移

数据库入口：

```text
app/src/main/java/com/imedia/box/db/AppDatabase.java
app/src/main/java/com/imedia/box/db/Migrations.java
app/schemas/
```

修改实体字段时：

1. 修改 Entity 和 DAO。
2. `AppDatabase.VERSION` 加 1。
3. 在 `Migrations` 添加从旧版本到新版本的 SQL。
4. 把 Migration 加入 `Migrations.ALL`。
5. 确认 Room 导出的新 schema JSON 已更新。
6. 添加迁移测试，至少从上一个发布版本升级。
7. 使用包含旧数据库的安装包执行覆盖安装。
8. 验证历史、收藏、订阅和播放进度没有丢失。

不要使用 `fallbackToDestructiveMigration()` 解决迁移失败。用户数据不能作为可再生缓存处理。

新增非空列时必须给旧行提供默认值。重命名字段通常应新建表、复制数据、删除旧表并改名，不能假设所有 Android SQLite 版本都支持最新语法。

## SharedPreferences 迁移

设置迁移见 [SETTINGS.md](SETTINGS.md)。基本规则：

- getter 对非法值 clamp。
- 使用 `contains()` 区分未设置和用户主动选择默认值。
- 旧键读取逻辑保留受支持升级周期。
- 迁移使用一次性版本标记。
- 不在每次启动时覆盖用户选择。

## 订阅和解析兼容

新增 TvBox 字段或旧格式兼容时：

1. 保存一份脱敏的最小复现输入到测试代码。
2. 在模型或 Gson Adapter 中兼容字段，不用字符串替换 JSON。
3. 区分“配置解析成功”“站点内容成功”“详情成功”“播放地址成功”。
4. 相对地址统一走 `UrlUtil`，GitHub 地址统一走网络加速层。
5. 网络失败保留第一个真实异常，不用空列表掩盖所有错误。
6. 用至少两个正常源和一个错误源做回归。

具体配置规则见 [CONFIGURATION.md](CONFIGURATION.md)。

## UI 维护

修改公共组件时先用 `rg` 找到所有引用：

```powershell
rg -n "Widget.IBox.Button|Widget.IBox.PosterTitle|ibox_text_" `
  app/src/main app/src/mobile app/src/leanback
```

公共样式改动影响面通常大于单页布局，需要检查：

- 日间、夜间和 TV 深色。
- 主题色、自定义色、焦点色和高对比度。
- 普通、按下、选中、聚焦和禁用。
- 中文、英文、繁体长文字。
- 手机窄屏、大字号、横屏和平板。
- TV 远距离焦点、滚动和焦点恢复。

完整规则见 [UI_DESIGN_SYSTEM.md](UI_DESIGN_SYSTEM.md)。

## 依赖升级

版本集中在 `gradle/libs.versions.toml`。升级前：

1. 阅读 Android Gradle Plugin、Kotlin/Java、SDK 和库的兼容要求。
2. 确认定制 `vendor/media3` 与声明的 Media3 接口兼容。
3. 单独提交大型依赖升级，不混入 UI 功能。
4. 运行四变体测试、Lint、AndroidTest 编译和 Release 构建。
5. 验证 R8、原生 ABI、Python/JS 扩展和播放器。

固定 Media3 提交同时记录在 `BUILD.md` 和 CI 工作流。变更时三处一起更新。

## 生成文件和清理

允许删除的可再生内容：

- 模块 `build/`。
- 根目录 `build/`。
- `.gradle/` 项目缓存，在确有缓存故障时。
- 临时日志、截图和测试报告。
- 明确准备重建的旧 `Release` 产物。

不能当作缓存删除：

- 用户下载和离线媒体。
- `vendor/media3`，除非准备按固定提交重新下载。
- Room schema 历史。
- 签名证书唯一副本。
- Git 未提交改动。

优先使用 Gradle `clean` 和 `cleanRelease`。递归删除前必须确认绝对目标在仓库或专用临时目录内。

## 故障修复流程

1. 用最短步骤稳定复现。
2. 清空日志，只复现一次并保存完整 Logcat。
3. 找第一个异常或错误状态转换，不从最终现象猜原因。
4. 建立最小修复，避免同时重构无关区域。
5. 添加能失败的自动测试或明确设备回归步骤。
6. 用原问题路径、相邻路径和双端路径验证。
7. 检查是否引入闪屏、重复弹窗、状态丢失或焦点变化。

播放、MuMu 和日志细节见 [TESTING.md](TESTING.md)。

## 文档同步表

| 改动 | 必须更新 |
| --- | --- |
| 构建版本、SDK、签名或输出 | `BUILD.md`、根 `README.md` |
| 新功能或删除功能 | `FEATURES.md` |
| 配置格式、内置源或 EPG | `CONFIGURATION.md` |
| 公共 UI 令牌或组件 | `UI_DESIGN_SYSTEM.md` |
| 默认设置或新设置项 | `SETTINGS.md` |
| 测试命令或模拟器流程 | `TESTING.md` |
| 模块、目录或调用链 | `ARCHITECTURE.md` |

## 发布检查表

### 代码和版本

- 工作区只包含本次发布内容。
- `versionCode` 已递增，`versionName` 正确。
- 文档和三套语言字符串同步。
- `git diff --check` 通过。
- 没有私人 URL、密钥、日志和临时文件。

### 测试

- 四变体 JVM 单元测试通过。
- 手机与 TV Lint 通过或每个警告有明确结论。
- 双端 AndroidTest 可编译。
- 手机和 TV arm64 Debug 实机/模拟器验证通过。
- 日夜、高对比度、大字号、长文字和 TV 焦点通过。
- 点播、直播、EPG、弹幕、历史、收藏和设置核心流程通过。

### 产物

- `cleanRelease` 后重新构建四个 Release APK。
- 四个 APK 包名、版本、ABI 和签名正确。
- SHA-256 基于最终 APK 重新生成。
- 源码 ZIP 包含 `vendor/media3`、APK 和校验文件。
- 源码 ZIP 完整保留 `artwork/app-icon/` 图标母版和导出资产。
- 源码 ZIP 不包含 `.git`、缓存、签名和本机配置。
- 在临时目录解压并检查结构。

### Git

- 最终提交已经在 `master`。
- 标签指向最终提交。
- 标签名与 `versionName` 一致。
- 发布后不再重写该提交和标签。
