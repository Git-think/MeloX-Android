# DLNA / 小米妙播 接入与适配文档

状态：Android 首轮 DLNA 与小米私有 MiPlay 接入均已完成编码（2026-10-24），DLNA 单元/
模拟渲染器回归与小米私有接口真机探针已通过；真实音箱 / 电视的发现与出声待设备在线验收。
目标是让弥乐扫描到小米音箱 / 电视等设备并把歌曲流转过去，入口并入现有播放设备面板。
DLNA 实现见 §14，小米私有协议（MiPlay 音频客户端）强行适配见 §15。

## 1. 背景与结论

小米的"妙播"把手机音频流转到小米音箱 / 电视。2026-10 对小米音乐
（`com.miui.player`）与小米互联服务（`com.milink.service` v17.2.4.1）做了静态分析，
结论：

> 小米软件包含标准 DLNA 渲染器（MediaRenderer）的发现和控制路径；具体音箱 / 电视是否开放此能力取决于型号、固件和设备设置，仍需实际发现与播放验证。
> 手机侧只要实现一个轻量 **DLNA 控制点**（SSDP 发现 + UPnP AVTransport 控制），
> 即可发现并投放，不需要接入小米私有 MiPlay 协议、不需要小米账号。

（2026-10-24 修订：逆向与真机验证表明私有音频客户端入口对普通应用开放，
弥乐已另行强行接入该私有通道作为 DLNA 不可用时的兜底，见 §15。）

小米妙播架构（调研摘要，供参考）：

```
音乐App ──AIDL──▶ com.milink.service（MiPlay 核心服务）
                     ├─ 发现：SSDP 组播 239.255.255.250（内置 Cling UPnP 栈）
                     │        + mDNS（UDTJmDNSThread，用于接力/流转设备）
                     ├─ 控制：com.miui.upnp.UpnpAVControlPoint（AVTransport）
                     ├─ 传输：DLNA 标准路径——渲染器反向拉手机给出的 URL
                     │        （在线曲库直接给 CDN 地址；本地文件起 HTTP server）
                     └─ 私有增强：MiPlayCast native 协议（视频投屏 / 音频镜像 /
                                  一起听，ed25519 握手），与音频流转的主路径无关
```

弥乐只需覆盖其中的 **DLNA 标准路径**。设备类型枚举中的 `dlna.speaker` / `dlna.tv`
证实音箱与电视走的是标准 DLNA。

## 2. 接入架构

新增独立模块 `com.lladlam.melox.dlna/`，与 `lan/`（MELOX-LAN 自研协议）并存、互不影响：

| 文件（规划） | 职责 |
| --- | --- |
| `DlnaModels.kt` | `DlnaDevice`、`DlnaRendererService`（AVTransport / RenderingControl 端点）等数据类 |
| `DlnaDiscovery.kt` | SSDP M-SEARCH 发现、响应解析、设备描述拉取与去重 |
| `DlnaDescriptionParser.kt` | 设备描述 XML 与 SCPD 解析（friendlyName / UDN / controlURL） |
| `DlnaController.kt` | SOAP 调用封装：SetAVTransportURI / Play / Pause / Seek / GetPositionInfo / 音量 |
| `DlnaMetadata.kt` | DIDL-Lite 元数据构造与 XML 转义 |
| `DlnaHttpServer.kt` | 本地文件投放用的临时 HTTP 服务（含 CDN 反向代理兜底，见 §8） |
| `DlnaSession.kt` | 投放会话状态机（与 `MeloXLanSession` 同层，输出统一的 remote transport 视图） |

UI 集成点：`ui/player/MeloXAudioOutputDialog.kt` 与妙播面板。设备列表按来源分两组：

- **弥乐设备**：现有 MELOX-LAN 设备（UDP 47830 发现，PIN / 一起放等能力不变）。
- **局域网 DLNA 设备**：SSDP 发现的音箱 / 电视（含小米），标注厂商与型号。

## 3. 发现层：SSDP

### 3.1 基本参数

| 项 | 值 |
| --- | --- |
| 组播地址 | `239.255.255.250:1900` |
| 协议 | UDP，先 IPv4（IPv6 可后续补充） |
| 目标 ST | `urn:schemas-upnp-org:device:MediaRenderer:1`（主）+ `ssdp:all`（兜底） |
| MX | 2～3 秒 |

M-SEARCH 请求样例（每次发现发 2～3 遍，间隔随机 100～300ms，抗丢包）：

```
M-SEARCH * HTTP/1.1
HOST: 239.255.255.250:1900
MAN: "ssdp:discover"
MX: 2
ST: urn:schemas-upnp-org:device:MediaRenderer:1
USER-AGENT: MeloX/UPnP Stack/1.0
```

### 3.2 响应解析

响应为 `HTTP/1.1 200 OK`，需要关注的头：

| 头 | 用途 |
| --- | --- |
| `LOCATION` | 设备描述 XML 地址（`http://<ip>:<port>/...`），后续所有交互的锚点 |
| `ST` / `USN` | 服务类型与设备 UUID；**用 USN 中的 uuid 做去重键** |
| `SERVER` | 设备系统标识，可用于厂商统计（小米设备一般含 `XiaoMi/MiPlay` 等字样，待实测） |
| `CACHE-CONTROL: max-age=…` | 设备在线时长提示，用于列表过期 |

可选：发现完成后订阅 `ssdp:all` 的 NOTIFY（监听组播 `239.255.255.250:1900` 的
byebye / alive 报文）实时增删设备；第一版可只做"进入面板时扫描 + 手动刷新"，
列表项 180s 过期。

### 3.3 Android 侧注意事项

- 需要 `CHANGE_WIFI_MULTICAST_STATE`，并在扫描期间持有
  `WifiManager.MulticastLock`（否则部分机型收不到组播响应）。
- 扫描在 `Dispatchers.IO`，socket 设置 `SO_REUSEADDR`，超时 3s，总扫描时长建议 5s。
- 手机与音箱必须在同一二层网络（AP 隔离 / 访客网络会失败），面板可提示。
- Android 17 且 targetSdk ≥ 37 需要申请 `ACCESS_LOCAL_NETWORK`；复用现有弥乐局域网权限入口。

### 3.4 设备描述与服务定位

对每个 `LOCATION` 执行 `GET`，解析 XML：

1. 取 `device/friendlyName`（展示名）、`modelName`、`UDN`。
2. 在本设备与所有内嵌 `deviceList/device` 中查找：
   - `urn:schemas-upnp-org:service:AVTransport:1` → `controlURL`、`eventSubURL`（必需）
   - `urn:schemas-upnp-org:service:RenderingControl:1` → `controlURL`（音量，可选）
3. `controlURL` 可能是相对路径，按 RFC 3986 优先相对 `URLBase` 解析，未提供时再相对 `LOCATION`；服务必须属于同一渲染器设备节点，并兼容高版本服务 URN。
4. 单设备描述超时 3s；解析用 `XmlPullParser`，容错处理命名空间缺失的老设备。

## 4. 控制层：SOAP over HTTP

所有控制调用为对 `controlURL` 的 `POST`，请求头：

```
Content-Type: text/xml; charset="utf-8"
SOAPACTION: "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI"
```

信封模板（下同，仅 Body 不同）：

```xml
<?xml version="1.0"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
            s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:METHOD xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      …参数…
    </u:METHOD>
  </s:Body>
</s:Envelope>
```

### 4.1 必备动作

| 动作 | 参数 | 说明 |
| --- | --- | --- |
| `SetAVTransportURI` | `CurrentURI`（音频 URL）、`CurrentURIMetaData`（DIDL-Lite，见 §5） | 投放；部分渲染器要求先 `Stop` |
| `Play` | `Speed=1` | 开始播放 |
| `Pause` | — | 暂停（部分低端音箱不支持，失败时降级为 `Stop`） |
| `Stop` | — | 停止 |
| `Next` / `Previous` | — | 渲染器无队列时会返回错误，按"仅当前曲"处理 |
| `Seek` | `Unit=REL_TIME`，`Target=HH:MM:SS` | 拖动进度 |
| `GetPositionInfo` | — | 取 `RelTime` / `TrackDuration` / `AbsTime`，状态同步用 |
| `GetTransportInfo` | — | 取 `CurrentTransportState`：`PLAYING` / `PAUSED_PLAYBACK` / `STOPPED` / `TRANSITIONING` |

### 4.2 音量（RenderingControl，可选）

- `GetVolume` / `SetVolume`，参数 `Channel=Master`，值一般 0–100；
   dB 音量使用 `GetVolumeDB` / `GetVolumeDBRange` 等动作，需按设备 SCPD 另行适配。

### 4.3 错误处理

- HTTP 500 + SOAP Fault 内含 `UPnPError`，应保留 errorCode / errorDescription；例如 AVTransport 的 701 为状态转换不可用、714 为 MIME 类型不合法，401 为无效动作。
- SOAP 调用有界超时。`SetAVTransportURI` 后短暂等待并执行 `Play`，再通过 `GetTransportInfo` 确认 `PLAYING`；固定等待时长是兼容策略，不是协议要求。

## 5. 元数据：DIDL-Lite

`CurrentURIMetaData` 是 **XML 转义后的** DIDL-Lite 文档（注意双重转义，这是最易踩的坑）：

```xml
&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
           xmlns:dc="http://purl.org/dc/elements/1.1/"
           xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;
  &lt;item id="0" parentID="-1" restricted="1"&gt;
    &lt;dc:title&gt;歌名&lt;/dc:title&gt;
    &lt;upnp:artist&gt;歌手&lt;/upnp:artist&gt;
    &lt;upnp:album&gt;专辑&lt;/upnp:album&gt;
    &lt;upnp:class&gt;object.item.audioItem.musicTrack&lt;/upnp:class&gt;
    &lt;upnp:albumArtURI&gt;http://…/cover.jpg&lt;/upnp:albumArtURI&gt;
    &lt;res protocolInfo="http-get:*:audio/mpeg:*" duration="00:03:45"&gt;http://…/song.mp3&lt;/res&gt;
  &lt;/item&gt;
&lt;/DIDL-Lite&gt;
```

要点：

- `res protocolInfo` 格式 `传输:网络:内容:附加`；在线 mp3 用
  `http-get:*:audio/mpeg:*`，flac 用 `audio/flac`，m4a 用 `audio/mp4`。
- `duration` 为 `HH:MM:SS`，来自曲目时长；缺省时音箱可能不显示进度。
- `upnp:albumArtURI` 指向封面 URL，小米音箱 / 电视会直接拉取展示。
- 歌词不支持（DLNA 音频流转没有歌词通道）。

## 6. 音频源适配

### 6.1 在线曲库（网易云等）

- 直接把 CDN 音频 URL 作为 `CurrentURI` 投放，**手机不中转流量**。
- 已知问题与对策：
  - **URL 有时效**：网易云 URL 带过期参数，音箱侧拉流失败（`714` 或无声）时，
    重新取流并 `SetAVTransportURI` + `Play` 刷新。
  - **防盗链**：音箱拉流不带 Referer / 自定义 UA，若 CDN 拒绝则降级为
    §6.2 的本地代理模式（手机反向代理 CDN 再给音箱）。
  - http/https 均可；https 依赖音箱信任的证书链，自签证书不可用。

### 6.2 本地文件

- 手机起临时 HTTP server（`DlnaHttpServer.kt`），把文件以 HTTP 提供给音箱：
  - 随机空闲端口，绑定 `0.0.0.0`，仅服务本次会话的单文件路径（防目录泄漏）。
  - **必须支持 Range 请求**（`206 Partial Content`），部分渲染器先探 Range。
  - `Content-Type` 按扩展名给（mp3→`audio/mpeg` 等）。
  - URL 形如 `http://<手机WLAN-IP>:<port>/cast/<token>.mp3`；token 每次会话随机。
  - 会话结束 / 切歌完成交接 / 网络失效时关闭；退出面板保持服务。
- **CDN 反向代理兜底**：同一 server 为当前曲登记不透明令牌路径，把防盗链的在线歌
  经手机转发给音箱（带 Referer/UA 请求 CDN），解决 §6.1 防盗链问题。

### 6.3 格式兼容

- 优先 mp3 / flac / m4a；遇到音箱不支持的格式（如 ogg、ape），面板提示
  "该设备可能不支持此格式"，不阻断投放。
- 码率建议 ≤ 1 Mbps（常见 320k mp3 足够）；局域网带宽一般不是瓶颈。

## 7. 会话与状态同步

- 投放成功后，弥乐本地播放器停止解码，切到 remote transport 视图
  （与 MELOX-LAN 投放同层，复用现有播放页控制）。
- 状态轮询：`GetTransportInfo` + `GetPositionInfo`，当前实现 2s 一次；面板不可见时继续维持会话和状态检查。
- 渲染器状态变化（如用户按了音箱上的物理键）通过轮询反映回 UI。
- 断线：连续 3 次轮询失败 → 标记设备离线，恢复本地播放，面板可重连。
- 切歌：`SetAVTransportURI`（新曲）→ 短暂延时 → `Play`；拖动用 `Seek`。
- 与 MELOX-LAN 的"一起放 / 传输 / PIN"能力 **不适用于 DLNA 设备**，
  面板对 DLNA 分组隐藏这些操作。

## 8. UI 集成（妙播面板）

- 妙播面板设备列表分组展示：**弥乐设备** / **局域网 DLNA 设备（小米等）**。
- DLNA 组的操作：扫描、投放（当前曲）、播放页控制（播放/暂停/上下曲/拖动/音量）、
  断开（`Stop` + 关闭 HTTP server）。
- 扫描进行中显示 5s 进度；空态文案说明"需与设备处于同一 Wi-Fi"。
- 设备条目展示 `friendlyName`（小米音箱一般形如"小爱音箱 xxxxx"）、型号、厂商。

## 9. 权限与配置

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

- 面向 SDK 37 的版本还需 `ACCESS_LOCAL_NETWORK` 运行时权限。
- 本地 HTTP server 为对外服务端，不受 Android cleartext 限制影响；
  但若 App 侧要主动 `GET` 自身 server 做校验，注意 network security config 放行。

## 10. 已知限制与风险

| 限制 | 说明 |
| --- | --- |
| 无双向同步 | DLNA 渲染器自主拉流，无法做到"一起放"级多端同步，属协议本质限制 |
| 无传输 / PIN | DLNA 没有设备间文件传输与配对概念，仅限弥乐设备 |
| 防盗链 CDN | 部分在线源需走 §6.2 反向代理 |
| 格式 / 歌词 | 无歌词通道；格式受渲染器解码能力限制 |
| 厂商碎片化 | 各厂商 AVTransport 实现有差异，需按 §11 实测回归 |
| 仅限局域网 | 与 MELOX-LAN v1 相同的 non-goal（无公网发现） |

## 11. 待实测清单（小米设备专项）

以下为静态分析无法确认、需在真机 + 小米音箱环境下抓包 / 实测的点：

1. 小米音箱 / 电视 SSDP 响应中实际的 `ST` 与 `USN` 形态（是否只回
   `MediaRenderer:1`，是否附带小米自定义 ST）。
2. `LOCATION` 描述 XML 中 AVTransport / RenderingControl 的实际路径与命名空间版本。
3. `SetAVTransportURI` 的 URL 白名单策略：非白名单 http URL（如手机 IP）能否直接拉流；
   若被拒，验证 `User-Agent` / Referer 是否是放行条件。
4. 音量取值范围（0–100 还是 dB）与 `Pause` 支持情况。
5. `Next` / `Previous` 在无队列时的错误码。
6. 音乐 App 官方投放时音箱显示的元数据字段（封面尺寸、是否有来源标签）。

建议验证手段：同一 Wi-Fi 下 `tcpdump -i wlan0 port 1900 or port 80`（或抓音箱 IP 的
80/443）观察官方妙播的 SSDP 与 HTTP 报文，与本文档规范逐项对照；渲染器侧可用
Linux Rygel / `gssdp-discover` 做交叉验证。

## 12. 分期实施

| 阶段 | 内容 | 交付 |
| --- | --- | --- |
| P1 | SSDP 发现 + 描述解析 + `SetAVTransportURI`/`Play`，在线曲库直投 | 面板能看到小米设备并投放在线歌 |
| P2 | 本地文件 HTTP server（Range）+ CDN 反向代理兜底 | 本地歌曲可投放 |
| P3 | 完整控制（暂停/拖动/音量/状态轮询/断线恢复）+ 面板打磨 | 日常可用 |

P1 完成后先按 §11 抓包核对，再进入 P2 / P3，避免在未知厂商行为上返工。

---

附：本文档的小米侧结论来自 2026-10 对 `com.miui.player` 与 `com.milink.service` 的
静态分析（APK 字符串与组件表），仅用于互通性研究；弥乐实现完全基于公开的
UPnP / DLNA 标准，不依赖、不分发任何小米私有代码。

## 13. 复查记录（2026-10-24，第二轮）

对设备上在装版本重新拉取 APK 并逐条核验（新拉取 APK 与首轮 **MD5 一致**，
`miui.player` = `8639f1d2…`、`milink.service` = `0e556486…`，无版本漂移）：

| 断言 | 结果 | 证据 |
| --- | --- | --- |
| SSDP 组播 `239.255.255.250` | ✅ | 字符串命中 |
| `M-SEARCH` / `ssdp:discover` | ✅ | 命中 |
| `ssdp:all` / `ssdp:alive` / `ssdp:byebye` | ✅ | 三者均命中（alive/byebye 佐证 NOTIFY 监听存在） |
| Cling UPnP 栈 | ✅ | `org/fourthline/cling` 1049 处类引用 |
| `com.miui.upnp.UpnpAVControlPoint` | ✅ | 18 处 |
| `com.miui.upnp.MediaRenderer` 设备封装 | ✅ | 类 + 裸 `MediaRenderer` 匹配串 |
| AVTransport 全套动作（SetAVTransportURI / Play / Pause / Next / Previous / Seek / GetTransportInfo / GetPositionInfo） | ✅ | 逐个命中；`REL_TIME` / `TrackDuration` / `ABS_TIME` 均在 |
| RenderingControl 音量（GetVolume / SetVolume） | ✅ | 命中（30 / 25 处） |
| DIDL-Lite 元数据字段（DIDL-Lite URN、dc:title、upnp:artist、upnp:album、albumArtURI、musicTrack、protocolInfo、http-get） | ✅ | 全部命中 |
| `SOAPACTION` / SOAP envelope | ✅ | 命中 |
| UPnPError 错误码处理 | ✅ | `UPnPError` + `errorCode` 命中 |
| 手机端 HTTP 服务（airkan HttpServer、NanoHTTPD、`libhttpserver.so`） | ✅ | 命中 |
| mDNS 发现（`UDTJmDNSThread` / `UDTDiscoverManager`） | ✅ | 24 / 12 处 |
| MiPlayCast 私有协议（SafetyAuthAck、ed25519、native 库清单） | ✅ | 命中 |
| 设备类型 `dlna.speaker` / `dlna.tv` | ✅ | 音乐 App 命中 |
| 音乐 App 为客户端（`MiPlayAudioCastManger` → `CoreService`） | ✅ | 命中 |

一处补充说明：`urn:schemas-upnp-org:service:AVTransport:1` 的**字面量**不在
`com.milink.service` 字符串表中（Cling 的 `ServiceType` 在运行时拼接 URN），
但动作名与模型类（`support/model/AVTransport`）已充分佐证 AVTransport 控制点的存在；
本文档 §4 要求弥乐在**渲染器描述 XML 中查找标准 URN**，与该实现细节无关。

复查结论：上述静态符号支持 DLNA 控制点路径存在，不能替代对每个音箱 / 电视型号的运行时互通验证；§11 待实测清单保持不变。

## 14. Android 首轮接入（2026-10-24）

- 实现目录：`android/app/src/main/kotlin/com/lladlam/melox/dlna/`。
- `DlnaDiscovery.kt`：面板打开时周期性 SSDP 发现，持有 MulticastLock；获取描述并按渲染器 UDN 去重，跳过只有 IGD / 路由器服务的设备。
- `DlnaProtocol.kt`：描述 XML、URLBase / 内嵌设备解析、SOAP 控制、DIDL-Lite 双层转义。动态局域网 HTTP 使用限定本地地址的 Socket，不放宽整个应用的 HTTPS 策略。
- `DlnaHttpServer.kt`：仅当前会话的不透明资源 URL，绑定接收设备地址，支持本地资源 GET / HEAD / 单段及后缀 Range，在线代理保留播放解析器给出的请求头。
- `DlnaSession.kt`：复用当前播放解析器；可直投的在线地址优先直投，控制失败或未进入 PLAYING 时改走代理。本地文件与带请求头的音源直接走代理。确认播放后暂停手机本地播放器。
- 面板增加“局域网音箱 / 电视（DLNA）”；点设备启动投放，播放页按钮转发暂停、拖动、音量和切歌；关窗保留连接，提供独立断开入口。
- 这条接入不要求第三方设备理解 MELOX-LAN 的 PIN、PCM 包或一起听协议，也不把小米私有 MiPlay 的协议握手当作已实现能力。
- 自动回归：描述 / SOAP / 转义 / Range 单元测试，以及 `DlnaDeviceRunner` 模拟渲染器真机回归（使用 `-PmeloxTestInstrumentationRunner=com.lladlam.melox.dlna.DlnaDeviceRunner` 构建）。
- 当前开发电脑的 SSDP 探测只返回路由器 / 网络服务描述，尚未发现可用 AVTransport 渲染器；真实小米音箱 / 电视的播放、音量与格式兼容性待在设备在线环境下确认。

## 15. 小米私有 MiPlay 强行适配（2026-10-24）

§1 的结论曾主张"不需要接入小米私有协议"。本轮按"强行适配小米私有协议"的要求对
已安装的 `com.milink.service` v17.2.4.1（SHA-256
`abe48100cd90ef872abd40c8b5cafa34f3561e8a7871865bf60ca93d2dfb1c4e`）做了逆向与真机
验证，结论需要修正：**私有音频客户端入口对任意普通应用开放，可直接借用。**

### 15.1 入口选型（逆向结论）

| 候选入口 | 绑定方式 | 鉴权 | 结论 |
| --- | --- | --- | --- |
| `com.miui.miplay.audio.service.CoreService` | exported，`com.miui.miplay.audio.ACCESS_STATE`（normal） | `MiPlayServiceAgent` 对 `queryDeviceList` / `scanDevice` / `markUIStart` 等逐方法 `checkSignatures(callingUid, self) == 0` | 非小米签名应用在 Binder 层被拒，**不可用** |
| `com.xiaomi.miplay.mylibrary.MiPlayAudioService`（action `com.xiaomi.miplay.action.MIPLAY_AUDIO_CLIENT_SERVICE`） | exported、无 permission | `MiplayClientProxy`（`IMiPlayClient.Stub`）无调用方校验；仅受系统 CTA 同意开关约束 | **采用**。小米音乐 / 小爱等自身也走客户端路径 |
| `com.milink.sdk.cast.v2.IMiLinkCastServiceV2` 等 SDK AIDL | 服务未对第三方导出 | — | 不可用 |

关键行为链（反编译佐证）：`init(pkg, callback, id)` → `startDiscovery(type)` →
设备回调 `onDeviceFoundControlCenter(MiPlayDeviceControlCenter)` → `onPlay(uuids, mirrorParam)`
进入 `MiplayMultiDisplayManage.onPlay` → 打开系统音频采集（48kHz）并 `startMirror`
建立镜像端口。即：**音频由小米系统服务自己采集本机输出，App 不需要也不会拿到音频数据**；
`onPlay` 的第二参数是镜像参数（小米传空串），不是媒体 URL。

### 15.2 弥乐侧实现

- `android/app/src/main/java/com/lladlam/melox/miplay/MiPlayTransport.java`：不依赖
  小米任何类，按 `IMiPlayClient` 的 DESCRIPTOR 与 transaction code 手写 Parcel
  （init=1、unInit=2、startDiscovery=3、stopDiscovery=4、onPlay=6、stop=7、
  setVolume=17、getVolume=18）。回调端只解析设备 / 连接 / 音量三类 Parcel 的公共前缀，
  其余回调吞掉并回 `writeNoException`，避免加载小米 Parcelable 类。
- `MiPlaySession.kt`：面板打开时按需绑定并启动发现（15s 初始化超时）；点击设备
  `onPlay` 后等待 `onDeviceConnectStateChange(state==1)` 确认，15s 未确认自动回 `stop`。
  与 DLNA / MELOX-LAN 输出互斥（切换时先断开其它输出）。播控（暂停/切歌/进度）仍走
  本机播放器——小米镜像的就是本机输出；音量走 `setVolume`。
- Manifest 增加 `<queries><package android:name="com.milink.service"/>`（Android 11+
  包可见性）；无新增运行时权限。

### 15.3 已验证 / 未验证

真机（192.168.1.146，HyperOS，milink 17.2.4.1）验证通过：

- 普通应用（`tools/check_miplay_device.py` 生成的独立探针 APK）绑定成功并收到
  `onInitSuccess`——**私有客户端身份不被拒绝**；
- `MiPlayWireRegression`：设备 / 连接 / 音量回调 Parcel 布局、`onPlay` / `setVolume`
  请求编码在真机 Binder 上往返一致；
- 探针运行时附近无小米妙播设备在线（`deviceCallbacks=0`），故**发现回调、连接确认、
  实际出声、系统采集静音行为均未在真实音箱/电视上验证**——等设备上线后用
  `python3 tools/check_miplay_device.py --serial <serial>` 复测并人工确认。

### 15.4 边界与风险

- CTA 同意：`startDiscovery` 在用户未同意小米"互联/投屏"隐私授权（CTA 开关）时静默
  不发现。若设备列表一直为空，先在系统设置里完成一次小米互联服务授权。
- 采集范围：镜像目标是**系统音频输出**，弥乐暂停本机播放即全部静音；这与 DLNA /
  MELOX-LAN 投放"本机继续解码"的语义不同，播放页控件不需要转发（本机即遥控）。
- 该入口无公开文档，transaction code 与 Parcel 布局随 milink 大版本变化的风险由
  `MiPlayWireRegression` 探针兜底；升级 HyperOS 后建议先跑探针再发布。
- 本适配只借用小米系统服务自身能力，不触碰 DRM / 会员内容，不解析其私有网络协议。
