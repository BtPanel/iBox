# 播放平台功能参考

查阅日期：2026-09-13。以下仅整理爱奇艺、优酷、YouTube 和 **YouTube TV** 官方公开页面可核实的观看行为；YouTube TV 是提供直播与点播节目的独立服务，不等同于普通 YouTube。[11] 它们是交互参考，不是 iBox 可接入这些平台的内容、账号或服务的证明，也不应复制专有界面。

| 主题 | 官方已记录的行为 | 对独立 IPTV/VOD 客户端的启示 |
| --- | --- | --- |
| 基础控制、倍速 | 爱奇艺帮助中心说明手机全屏播放可选倍速；YouTube 说明视频可快进/倒退、调整速度，但智能电视和流媒体设备不一定支持倍速；优酷帮助中心列有连续播放、跳过片头、倍速和弹幕开关等播放问题。[1][4][6] | 暂停、Seek、连续播放、倍速、跳片头等可作为客户端控制，但直播 Seek/回看及倍速须以媒体可定位性和播放引擎支持为准；不照搬各平台手势或布局。 |
| 清晰度、音轨 | YouTube 可在 TV、手机和电脑手动选画质，实际可用档位受源视频、连接、设备/编码支持限制；多语言音轨只有视频提供时才能切换。优酷帮助中心列有默认清晰度、切换视频语言条目。[5][7][6] | 只展示实际可用的码率/轨道；自动画质、轨道偏好可在本地做。不能通过菜单生成源端不存在的 4K、配音或音轨。 |
| 字幕、无障碍 | YouTube 在 TV/游戏机端允许选择现有字幕语言，并调整字体外观、背景和字幕窗口；视频没有字幕时入口可能不可用。YouTube TV 的部分节目/兼容电视设备有 5.1 音频，启用后会关闭可能包含描述性音频或其他语言的次音轨。[8][11] | 字幕开关、轨道与样式、遥控器焦点和高对比度可由客户端实现；字幕/描述性音频及 5.1 必须源端提供且设备支持。切换音轨时保留明确状态，避免误以为辅助音频仍启用。 |
| 历史、继续观看、稍后看 | 爱奇艺提供 App/电脑播放记录入口；优酷观看记录页区分本地记录与登录后云端记录；YouTube 历史可查看、暂停和删除，暂停期间观看内容不会进入历史；YouTube 的 Watch Later 可加入和移除视频。[2][9][10][13] | 本地历史、进度、继续观看和收藏/稍后看可独立实现，并给删除/停用历史的隐私控制。跨设备同步必须另有用户账号及同步后端；平台的云端记录不可继承。 |
| EPG、直播回看 | YouTube TV 的 Live 页按网络显示当前节目并可隐藏/重排网络，Library 显示已录制和计划录制节目；其云 DVR 可添加节目、录制未来播出，并在录制节目中选择从头、续播或看当前直播，部分赛事受限制。[11][12] | EPG 列表、频道排序/收藏和本地节目提醒可用合法的节目单数据实现。回看、从头播、定时录制和 DVR 需要上游回看地址/可 Seek 窗口、录制权限及存储服务；节目表本身不是视频回看。 |
| 离线下载 | YouTube 仅在指定地区对部分视频提供移动端下载；某些视频或地区要求 Premium，离线文件需周期性联网校验可用性。优酷帮助中心列有缓存、会员仍不能缓存及已缓存内容要求会员等问题。YouTube TV 明确不支持离线模式。[14][6][11] | 只对许可下载的源开放离线操作；需要文件空间、失败/过期提示和来源限制。不能把平台缓存、云 DVR 或受 DRM 保护的节目当作普通可下载文件。 |
| 投屏、多端 | 爱奇艺说明手机与电视同 Wi-Fi、选择投屏设备后可用手机控制，并注明未成年人模式不支持投屏；优酷帮助中心列有 TV 端扫码登录、投屏问题及独立的酷喵 VIP/电视端权益问题。[3][6] | 局域网发现/投屏控制和 TV 遥控器交互是可独立设计的能力；接收端仍须能访问并获准播放该源。平台账号、会员权益和跨设备控制协议不可假设可互通。 |

**优先级建议：** TV 端先保证遥控器可达的播放/暂停、频道与节目切换、真实可用的音轨/字幕/画质、恢复进度和清晰的不可用状态；手机端再优化触控、离线任务和投屏。直播回看与下载始终按单个来源的能力显示，不能用平台功能清单承诺所有订阅均可用。

## 官方来源

以下每项均于 **2026-09-13** 查阅；帮助中心条目和权益可能随地区、设备、账号及内容变化。

1. 爱奇艺帮助中心，[如何倍速播放影片](https://help.iqiyi.com/iqiyi-pcw-help/questionInfo/2083.html)。
2. 爱奇艺帮助中心，[如何查看播放记录](https://help.iqiyi.com/m/iqiyi-pcw-help/questionInfo/2572.html)。
3. 爱奇艺帮助中心，[如何操作投屏](https://help.iqiyi.com/iqiyi-pcw-help/questionInfo/3047.html)。
4. YouTube Help, [Speed up or slow down YouTube videos](https://support.google.com/youtube/answer/7509567?hl=en).
5. YouTube Help, [Change the quality of your video](https://support.google.com/youtube/answer/91449?hl=en).
6. 优酷帮助中心，[播放、上传缓存、TV 端和会员问题目录](https://csc.youku.com/feedback-web/help/index.html?style=1)。该目录只用于确认其列出的主题，不据条目标题推断具体操作步骤或权益。
7. YouTube Help, [Watch videos in your preferred language](https://support.google.com/youtube/answer/13339776?hl=en).
8. YouTube Help, [Manage captions on TV & game consoles](https://support.google.com/youtube/answer/15921047?hl=en).
9. YouTube Help, [View, delete, or turn on or off watch history](https://support.google.com/youtube/answer/95725?hl=en).
10. YouTube Help, [Add & remove videos from Watch later](https://support.google.com/youtube/answer/56101?hl=en).
11. YouTube TV Help, [Watch shows, sports, events, & movies on YouTube TV](https://support.google.com/youtubetv/answer/7067974?hl=en).
12. YouTube TV Help, [Record programs on YouTube TV](https://support.google.com/youtubetv/answer/7129564?hl=en).
13. 优酷，[观看记录](https://faxian.youku.com/watch_record)。未登录页明确区分本地记录和登录后的云端记录。
14. YouTube Help, [Watch videos offline on mobile in select countries & regions](https://support.google.com/youtube/answer/6141269?hl=en).
