# 配置与内置订阅

iBox 默认通过外部配置加载点播、直播、EPG 和网络规则，不随官方 APK 提供内容。本文同时说明用户添加订阅的方法，以及制作自用版本时如何安全地预置默认源。

## 基本概念

订阅记录由 `Config` 保存到 Room 数据库：

| `type` | 类型 | 读取入口 |
| ---: | --- | --- |
| `0` | 点播/TvBox 总配置 | `VodConfig` |
| `1` | 直播配置或频道清单 | `LiveConfig` |
| `2` | 网络壁纸 | `WallConfig` |

EPG 订阅不使用 `Config` 表，而由 `LiveSetting` 保存到 SharedPreferences。

相关代码：

- `app/src/main/java/com/imedia/box/bean/Config.java`
- `app/src/main/java/com/imedia/box/api/config/VodConfig.java`
- `app/src/main/java/com/imedia/box/api/config/LiveConfig.java`
- `app/src/main/java/com/imedia/box/setting/LiveSetting.java`

用户可以在手机或 TV 的“订阅管理”中添加点播和直播地址，在直播 EPG 管理中添加节目单。配置可来自 HTTP(S)、本地文件和应用支持的 `assets://` 地址。

## TvBox 点播总配置

常见顶层字段：

| 字段 | 用途 |
| --- | --- |
| `spider` | Java 扩展/JAR 地址 |
| `sites` | 点播站点列表 |
| `parses` | 播放解析接口 |
| `lives` | 直播源列表 |
| `doh` | DNS over HTTPS |
| `proxy` | HTTP/SOCKS 代理 |
| `hosts` | 主机名解析覆盖 |
| `headers` | 指定主机的全局请求头 |
| `rules` | 解析和过滤规则 |
| `ads` | 地址过滤规则 |
| `wallpaper` | 网络壁纸配置 |
| `danmaku` | 弹幕服务地址 |
| `assrt` | 字幕服务地址 |
| `notice` | 配置通知文本，是否展示由设置控制 |

简化示例：

```json
{
  "spider": "https://example.com/spider.jar",
  "sites": [
    {
      "key": "demo",
      "name": "示例站点",
      "type": 3,
      "api": "csp_Demo",
      "searchable": 1,
      "quickSearch": 1,
      "filterable": 1,
      "ext": "https://example.com/site.json"
    }
  ],
  "parses": [
    {
      "name": "示例解析",
      "type": 1,
      "url": "https://example.com/parse?url="
    }
  ],
  "lives": [
    {
      "name": "示例直播",
      "url": "https://example.com/live.m3u",
      "epg": "https://example.com/epg.xml.gz"
    }
  ]
}
```

配置中出现 `lives` 时，`VodConfig` 会自动创建或同步对应的直播配置并交给 `LiveConfig`。同一个总配置 URL 因此可以同时作为点播和直播订阅。

站点能显示分类或影片，不代表一定能播放。播放还取决于站点详情结果、线路地址、解析接口、请求头、扩展版本和媒体服务器状态。

### Java 扩展

远程 `spider` JAR 可以直接使用旧版地址格式，也兼容带 MD5 摘要的历史写法：

```text
https://example.com/spider.jar
https://example.com/spider.jar;md5;<32 位十六进制 MD5>
```

- 远程 JAR 支持 `http` 和 `https`。
- 带 `;md5;` 时会优先复用本地缓存，摘要不匹配则重新下载。
- 网络层兼容证书异常的旧源地址。
- `file:` 和 `assets://` 本地扩展仍可按原格式加载，但应只打包或选择可信文件。

## 订阅集合

应用支持以顶层 `urls` 数组提供多个子订阅的 TvBox 集合格式：

```json
{
  "urls": [
    { "name": "订阅一", "url": "https://example.com/one.json" },
    { "name": "订阅二", "url": "https://example.com/two.json" }
  ]
}
```

将集合地址添加到点播或直播订阅后，应用会过滤空地址、按 URL 去重，并按数组顺序把所有子订阅保存到对应的“订阅管理”中。第一条有效子订阅会被加载并设为当前默认项，导入完成后当前打开的订阅管理会自动刷新。

集合用于批量导入和切换订阅，不会把所有子订阅的站点内容合并到同一个首页。集合本身不会作为订阅项保留；上游集合新增、删除或改名后，需要重新添加集合地址以同步条目。

## 直播源格式

### M3U

```m3u
#EXTM3U x-tvg-url="https://example.com/epg.xml.gz"
#EXTINF:-1 tvg-id="CCTV1" tvg-name="CCTV-1" tvg-logo="https://example.com/cctv1.png" group-title="央视",CCTV-1
https://example.com/live/cctv1.m3u8
```

EPG 匹配优先依赖 `tvg-id`，其次会尝试 `tvg-name` 和频道显示名。直播清单与 XMLTV 中的频道标识应保持一致。

### TXT

```text
央视,#genre#
CCTV-1,https://example.com/live/cctv1.m3u8
CCTV-2,https://example.com/live/cctv2.m3u8
卫视,#genre#
示例卫视,https://example.com/live/demo.m3u8
```

同一频道可以用 `#` 分隔多条播放线路；请求头格式应遵循项目 `LiveParser` 已支持的规则。

### JSON

直播也可以作为 TvBox 总配置的 `lives` 数组出现。每项常用字段包括：

- `name`：直播源名称。
- `url`：M3U、TXT 或频道 JSON 地址。
- `epg`：XMLTV 地址、gzip 地址或带占位符的 EPG API。
- `logo`：频道台标模板。
- `ua`、`referer`、`origin`、`header`：请求参数。
- `api`、`ext`、`jar`：由扩展生成直播内容时使用。
- `timeout`：该源播放超时秒数。
- `timeZone`：EPG 时区，例如 `Asia/Shanghai`。
- `catchup`：回看类型、天数和地址模板。

完整字段以 `bean/Live.java`、`bean/Channel.java` 和 `bean/Catchup.java` 为准。

## EPG 节目单

### XMLTV

EPG 支持普通 XMLTV 和 `.xml.gz` 压缩文件。频道匹配要求：

1. 直播频道有稳定的 `tvg-id` 或 `tvg-name`。
2. XMLTV 的 `<channel id>` 或 `<display-name>` 能与直播频道对应。
3. 节目时间包含正确时区，或在设置中配置 EPG 时间偏移。

### 按频道请求的 JSON EPG

EPG 订阅类型 `1` 表示按频道请求的 API 模板，可使用：

- `{id}`：频道 `tvg-id`
- `{name}`：频道 `tvg-name`
- `{epg}`：频道自身 EPG 值
- `{date}`：查询日期，由 EPG 请求阶段替换

示例：

```text
https://example.com/epg?channel={name}&date={date}
```

EPG 订阅类型 `0` 表示 XMLTV/XMLTV gzip。

### 内置备用 EPG

当前全局备用地址定义在：

```text
app/src/main/java/com/imedia/box/api/LiveApi.java
```

`DEFAULT_EPG` 是按频道请求的备用 API，`DEFAULT_EPG_XML` 是备用 XMLTV。修改它们会影响所有没有有效自定义 EPG 的直播源，发布前必须检查可用性、响应速度、隐私和长期稳定性。

## 内置默认订阅

“内置”有两种含义：

1. APK 首次运行自动添加一个远程配置 URL。配置内容仍在服务器上，更新方便，推荐使用。
2. 把配置和直播清单打进 `assets`。无网络也能读配置文件，但实际站点和媒体地址通常仍需联网。

当前源码没有 `BuiltinSources` 类；下面是以后需要内置时建议采用的实现方式。

### 方案 A：预置远程 URL

新增文件：

```text
app/src/main/java/com/imedia/box/setting/BuiltinSources.java
```

参考实现：

```java
package com.imedia.box.setting;

import com.imedia.box.bean.Config;
import com.github.catvod.utils.Prefers;

import java.util.Collections;

public final class BuiltinSources {

    private static final String SEEDED = "builtin_sources_v1";
    private static final String VOD_URL = "https://example.com/config.json";
    private static final String LIVE_URL = "https://example.com/live.m3u";
    private static final String EPG_URL = "https://example.com/epg.xml.gz";

    private BuiltinSources() {
    }

    public static void seedIfNeeded() {
        if (Prefers.getBoolean(SEEDED)) return;

        if (Config.getAll(0).isEmpty()) {
            Config.find(VOD_URL, "默认点播", 0).update();
        }
        if (Config.getAll(1).isEmpty()) {
            Config.find(LIVE_URL, "默认直播", 1).update();
        }
        if (LiveSetting.getEpgSubscriptions().isEmpty()) {
            LiveSetting.saveEpgSubscription(
                    null,
                    "默认节目单",
                    EPG_URL,
                    0,
                    Collections.emptyMap());
        }

        Prefers.put(SEEDED, true);
    }
}
```

如果 `VOD_URL` 的总配置已经包含 `lives`，直播默认项可以把同一个总配置作为 `type=1` 写入，也可以省略独立 `LIVE_URL`，由总配置加载后同步直播。

在手机和 TV 首页的 `initConfig()` 后台任务中，读取 `Config.vod()` 之前调用：

```java
Task.execute(() -> {
    BuiltinSources.seedIfNeeded();
    Config vod = Config.vod();
    Config live = Config.live();
    Config wall = Config.wall();
    App.post(() -> applyConfig(vod, live, wall));
});
```

必须在 `Task.execute` 中运行，因为 Room 查询不能阻塞主线程。

### 方案 B：随 APK 打包

建议目录：

```text
app/src/main/assets/builtin/config.json
app/src/main/assets/builtin/live.m3u
```

总配置中的直播项可以写成：

```json
{
  "lives": [
    {
      "name": "内置直播",
      "url": "assets://builtin/live.m3u",
      "epg": "https://example.com/epg.xml.gz"
    }
  ]
}
```

首次安装的点播地址写为：

```java
Config.find("assets://builtin/config.json", "内置点播", 0).update();
```

`UrlUtil.convert()` 会把 `assets://` 转成 App 本机 HTTP 服务地址，点播配置和直播清单都支持这种方式。当前 `EpgParser` 的下载链没有统一调用 `UrlUtil.convert()`，因此 EPG 建议继续使用 HTTP(S) 地址；如需把 EPG 也打包进 APK，应先为 EPG 下载链补齐 `assets://` 转换并添加测试。

### 首次安装与升级策略

- 使用版本化标记，例如 `builtin_sources_v1`，只执行一次。
- 只在对应订阅列表为空时设为默认，避免覆盖老用户当前选择。
- 用户删除内置项后不要在每次启动时重新添加。
- 发布新版默认源时，优先新增但不激活；除非产品明确要求，否则不要强制切换老用户。
- 不要把默认 URL 直接作为 `Config.vod()` 或 `Config.live()` 的永久空值回退。
- 私人令牌、Cookie 和账号不要打入 APK；APK 很容易被反编译。

## GitHub 加速

全局开关位于设置页，默认关闭。开启后网络层会识别未加速的 GitHub、Raw GitHub 和相关资源地址，依次尝试加速地址、备用地址、原始地址及适用的 jsDelivr 地址。

- 已包含受支持加速前缀的 URL 不会重复添加。
- 关闭开关后保留用户填写的原地址。
- 加速只改善网络连接，不修复失效仓库、错误 JSON、缺少播放地址或不兼容扩展。
- 修改加速规则时同时更新 `GitHubUrl` 单元测试。

## 配置兼容性

iBox 不能保证支持网络上的所有订阅。排查不兼容配置时按以下顺序：

1. 确认返回内容是有效 JSON、M3U、TXT 或项目支持的加密格式。
2. 检查顶层 `sites`、`lives`、`parses` 类型是否正确。
3. 检查相对路径是否能基于配置 URL 解析。
4. 检查扩展 JAR/JS/Python 是否可下载并与当前接口兼容。
5. 检查站点是否真正返回播放线路，而不是只返回搜索结果。
6. 检查请求头、Cookie、Referer、UA、地区限制和证书错误。
7. 分别测试 ExoPlayer 和 MPV；两者同样失败时检查上游地址和控制器。
8. 使用来源健康检查和 Logcat 获取第一个真实异常。

## 本地数据与安全

- 删除应用数据会清除订阅、设置、历史、收藏和 EPG 缓存。
- WebDAV 同步前确认远端账号和目录由用户控制。
- 本机 HTTP 管理服务只应在可信局域网开启，并使用配对控制。
- 不在公开日志、截图、Issue 或文档中暴露私人配置、令牌和 Cookie。
- Java、JavaScript 和 Python 扩展可以访问网络及应用接口，只加载可信代码。
