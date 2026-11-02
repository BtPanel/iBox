# iBox 项目长期笔记

- 双端 Android 影音客户端：mobile（手机，iBox，com.media.ibox）+ leanback（TV，iBoxTV，com.media.ibox.tv），View+ViewBinding 体系。
- UI 规范：`ibox_*` 语义令牌体系（颜色/尺寸/组件），修改 UI 必须遵循 docs/UI_DESIGN_SYSTEM.md；改 main 资源须检查双端。
- Git：远程 origin=github.com/BtPanel/iBox.git，仓库身份 BtPanel。`pre-ui-redesign` 标签 = 2026-11-02 UI 重构前基线，可用 `git reset --hard pre-ui-redesign` 或 `git checkout pre-ui-redesign -- <path>` 回滚。
- 坑：vendor/media3 是私有子模块，未初始化时播放器依赖无法解析；`.git.corrupt` 是历史遗留（已忽略）。
