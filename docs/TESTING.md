# 测试与排错指南

本文用于手机、MuMu 模拟器和 Android TV 的开发验证。测试目标不是只确认“能打开”，而是验证双端行为、状态恢复、长内容、播放链和错误路径。

## 测试层级

| 层级 | 目的 | 典型工具 |
| --- | --- | --- |
| 静态检查 | 格式、资源、Lint 和明显错误 | `git diff --check`、Android Lint |
| JVM 单元测试 | 设置、解析、策略和纯业务逻辑 | Gradle `test...UnitTest` |
| AndroidTest 编译 | 确认设备测试代码和双端资源可编译 | `assemble...DebugAndroidTest` |
| 设备测试 | Activity、数据库、主题、播放和系统事件 | ADB、MuMu、TV 真机 |
| Release 验证 | 混淆、资源压缩、签名和 ABI | 四个 Release APK |

风险越高，验证范围越大。只改文案可以编译双端；修改播放器、配置、数据库或系统 Receiver 必须做设备测试。

## 开发前基线

先确认工作区和设备状态：

```powershell
git status --short --branch
adb devices
```

记录当前分支、未提交文件、安装包版本、订阅地址类型和问题复现步骤。不要在不清楚现有改动来源时重置工作区。

## JVM 单元测试

完整四变体测试：

```powershell
$python310 = "C:/Path/To/Python310/python.exe"

.\gradlew.bat `
  :app:testMobileArm64_v8aDebugUnitTest `
  :app:testMobileArmeabi_v7aDebugUnitTest `
  :app:testLeanbackArm64_v8aDebugUnitTest `
  :app:testLeanbackArmeabi_v7aDebugUnitTest `
  -PchaquopyBuildPython=$python310
```

只改手机端时至少运行对应手机变体，但合并或发布前仍运行四变体。

测试源码主要位于：

```text
app/src/test/java/
app/src/androidTest/java/
```

新增解析规则、设置默认值、URL 转换、自动换线和数据库迁移时应补充对应测试。

## Lint 和 AndroidTest

```powershell
.\gradlew.bat `
  :app:lintMobileArm64_v8aDebug `
  :app:lintLeanbackArm64_v8aDebug `
  -PchaquopyBuildPython=$python310

.\gradlew.bat `
  :app:assembleMobileArm64_v8aDebugAndroidTest `
  :app:assembleLeanbackArm64_v8aDebugAndroidTest `
  -PchaquopyBuildPython=$python310
```

`assemble...DebugAndroidTest` 只编译测试 APK，不会在设备上执行。连接兼容设备后可运行：

```powershell
.\gradlew.bat :app:connectedMobileArm64_v8aDebugAndroidTest `
  -PchaquopyBuildPython=$python310
```

TV AndroidTest 应在 TV 模拟器或实际 TV 环境执行，手机模拟器不能替代遥控器焦点验证。

## MuMu 模拟器连接

当前常用 MuMu ADB 地址为 `127.0.0.1:16384`，不同 MuMu 版本或实例可能使用其他端口。先执行：

```powershell
adb connect 127.0.0.1:16384
adb devices
```

输出应包含：

```text
127.0.0.1:16384    device
```

出现 `offline` 时先在 MuMu 中确认 ADB 已开启，再断开重连：

```powershell
adb disconnect 127.0.0.1:16384
adb connect 127.0.0.1:16384
```

## 构建和安装 Debug APK

手机 arm64：

```powershell
.\gradlew.bat :app:assembleMobileArm64_v8aDebug `
  -PchaquopyBuildPython=$python310

adb -s 127.0.0.1:16384 install -r `
  app/build/outputs/apk/mobileArm64_v8a/debug/app-mobile-arm64_v8a-debug.apk
```

`install -r` 保留应用数据。出现签名不一致时，通常是设备装过不同签名的 APK。卸载会清除应用数据，必须先确认备份，再执行：

```powershell
adb -s 127.0.0.1:16384 uninstall com.imedia.box
```

TV 包名是 `com.imedia.box.tv`，可以与手机版同时安装。

启动和完全停止手机版：

```powershell
adb -s 127.0.0.1:16384 shell am force-stop com.imedia.box
adb -s 127.0.0.1:16384 shell monkey `
  -p com.imedia.box -c android.intent.category.LAUNCHER 1
```

查看当前顶部 Activity：

```powershell
adb -s 127.0.0.1:16384 shell dumpsys activity activities |
  Select-String "mResumedActivity|topResumedActivity"
```

## Logcat 标准流程

用户准备手动测试前先清空日志：

```powershell
adb -s 127.0.0.1:16384 logcat -c
```

测试完成后立即保存完整日志：

```powershell
New-Item -ItemType Directory -Force logs | Out-Null
adb -s 127.0.0.1:16384 logcat -d -v threadtime > logs/mumu-logcat.txt
```

查找崩溃和关键异常：

```powershell
Select-String -Path logs/mumu-logcat.txt `
  -Pattern "FATAL EXCEPTION|AndroidRuntime|ANR in|Process: com.imedia.box|Caused by:"
```

查看当前进程实时日志：

```powershell
$appPid = adb -s 127.0.0.1:16384 shell pidof com.imedia.box
adb -s 127.0.0.1:16384 logcat --pid=$appPid -v threadtime
```

应用崩溃后 PID 会变化，因此崩溃诊断仍应保存完整 `logcat -d`。公开日志前删除订阅地址、Cookie、令牌、局域网 IP 和私人媒体地址。

## 手机 UI 验收

每次 UI 修改至少检查：

- 竖屏窄屏、横屏和 `sw600dp` 平板布局。
- 日间、夜间、跟随系统、高对比度。
- 标准、大、超大字号。
- 系统返回、手势返回、旋转和 Activity 恢复。
- 底部导航隐藏项、选中项和所有入口。
- 长电影名、长订阅名、长 URL 和长按钮文字。
- 空列表、加载、错误、无网络和重试。
- 播放页左侧按钮、底部操作、弹幕开关和画中画。
- 海报电影名半透明层、底部圆角和图片加载失败占位。

## TV UI 验收

不能只用鼠标点击。必须用遥控器方向键、确认键和返回键检查：

- 初始焦点是否落在合理控件。
- 上下左右移动是否进入隐藏或不可见控件。
- 列表滚动后焦点是否保持并自动滚入可见区域。
- 返回详情、搜索、历史和收藏后是否恢复来源焦点。
- 普通、聚焦、选中、选中且聚焦、禁用状态是否可区分。
- 焦点边框是否跟随设置颜色，主题色切换后是否可读。
- 长简介、大量集数、多个线路和五个详情按钮是否重叠。
- 全屏播放进度、当前集数、控制框和弹窗边界。
- 推送、局域网控制、云盘入口等 Activity 是否崩溃。

## 配置和订阅测试

至少准备以下测试输入：

- 一个标准 TvBox 点播总配置。
- 一个包含 `lives` 的总配置。
- 一个独立 M3U。
- 一个 `#genre#` TXT。
- 一个 XMLTV 或 `.xml.gz` EPG。
- 一个错误 JSON、404 地址和超时地址。
- 一个 GitHub Raw 地址和一个已带加速前缀的地址。

验证：

1. 订阅能添加、编辑、切换和删除。
2. 地址输入框保持单行，边框不遮挡文字。
3. 配置能看到内容后，再实际播放多个不同站点的影片。
4. 直播频道、分组、线路、台标和 EPG 匹配正确。
5. GitHub 加速开关打开、关闭均符合预期，已加速 URL 不重复包装。
6. 错误源不会无限加载、闪屏或导致 Activity 崩溃。
7. 来源健康检查给出可理解的失败阶段。

## 播放问题定位

### 固定秒数循环或跳回

1. 关闭自动换线路和自动换源，减少变量。
2. 记录媒体 URL 类型、线路、集数、时间点和播放器引擎。
3. 分别测试 ExoPlayer 和 MPV。
4. 两个引擎都复现时检查 `VodPlaybackController`、播放 Intent、服务状态和解析返回。
5. 查找日志中的 `seekTo`、`positionDiscontinuity`、重连、换线、重新 prepare 和 Activity 重建。
6. 直接用已解析媒体 URL 测试，区分站点/解析问题与播放器问题。

### 刚进影片返回首页或崩溃

1. 清日志并单独复现一次。
2. 查第一个 `FATAL EXCEPTION` 和最底层 `Caused by`。
3. 检查弹幕搜索、快搜弹窗、详情 Activity 和播放器 Activity 的启动顺序。
4. 检查 Activity 是否因配置变化、权限或异常 Intent 被 finish。
5. 修复后重复进入、返回、换集、切线路至少十次。

### 直播或 EPG 很慢

1. 分开测试直播清单下载、频道播放和 EPG 下载。
2. 检查 EPG 文件大小、压缩、缓存命中和刷新原因。
3. 检查 `tvg-id` 匹配，避免下载成功但节目无法绑定。
4. 检查主线程是否进行网络、XML 或大列表处理。
5. 网络失败时确认是否使用旧缓存，而不是清空当前页面。

## 系统入口测试

新增或修改启动页、Receiver、推送、局域网控制时检查：

- 桌面冷启动。
- App 已在后台时再次点击图标。
- `force-stop` 后启动。
- 横竖屏/配置变化后恢复。
- 系统开机广播或模拟广播。
- 手机和 TV 对应包名及 Activity。
- 无订阅、无权限和服务未启动状态。

系统广播可能受 Android 或模拟器策略限制，不能仅凭手动 `am broadcast` 成功认定真实开机路径可用。

## 提交前检查

```powershell
git diff --check
git status --short
```

确认：

- 没有意外生成的日志、截图、密钥或私人配置。
- 相关单元测试通过。
- 手机和 TV 至少完成一次编译。
- 实际设备问题已复现并验证消失。
- 文档与当前行为一致。

正式发布的完整检查见 [BUILD.md](BUILD.md) 和 [MAINTENANCE.md](MAINTENANCE.md)。
