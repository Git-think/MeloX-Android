# 酷狗概念版音乐源（KuGou Lite）

MeloX 的 `MusicSource.KugouLite` / `KugouLiteProvider` 是独立于标准
`MusicSource.Kugou` 的音乐源。两套客户端的 appid、签名盐值、登录 Token 和会员权益
完全不同，因此实现上不共享账号会话。

## 参考仓库与许可证

| 仓库 | 用途 | 许可证 |
| --- | --- | --- |
| [MakcRe/KuGouMusicApi](https://github.com/MakcRe/KuGouMusicApi) `514f870d` | 协议、签名、概念版接口字段 | MIT |
| [MoeKoeMusic/MoeKoeMusic](https://github.com/MoeKoeMusic/MoeKoeMusic) `f2574785` | 仅用于核对用户流程与已知限制 | GPL-2.0 |
| [MoeKoeMusic/MoeKoeMusic-mobile](https://github.com/MoeKoeMusic/MoeKoeMusic-mobile) `ab71195d` | 仅用于核对移动端交互 | GPL-2.0 |

MeloX 本体为 GPL-3.0。MoeKoe 系列仓库的 GPL-2.0 代码没有复制进 MeloX；本实现只依据
MIT 仓库的接口行为重新编写 Kotlin 代码。MoeKoe 的界面仅作为流程参考，未复制其源码、
资源或文案。

## 实现文件

- `KugouLiteProvider.kt`：Provider 门面，接入现有 `MusicProvider` 抽象。
- `KugouLiteApiClient.kt`：搜索、权益、Auth 播放、推荐、设备注册、VIP 查询/领取。
- `KugouPlatform.kt`：标准版/概念版 appid、clientver、签名盐值、UA。
- `KugouSessionStore.readLite/updateLiteLogin/...`：独立加密偏好文件。
- `KugouRequestClient`：按 `KugouSession.platform` 选择签名与默认参数。
- 复用 `KugouCatalogClient` / `KugouDiscoveryClient` / `KugouRankingClient` /
  `KugouPlaylistClient` / `KugouLyricsClient`，通过 `source` 参数区分资源 ID。
- `KugouLoginScreen(lite = true)`：概念版 QR 登录入口。
- `ProviderSettingsHub`：概念版账号、退出、领取今日 VIP（需二次确认）。

## 接口映射

| 功能 | 接口 |
| --- | --- |
| QR 登录 | `login-user.kugou.com/v2/qrcode`、`/v2/get_userinfo_qrcode` |
| 用户权益 | `gateway.kugou.com/v1/user_verify`、`kugouvip.kugou.com/v1/get_union_vip` |
| 搜索 | `gateway.kugou.com/v2/search/song`、`/v1/search/{album,author,special}` |
| 歌曲权益 | `gateway.kugou.com/v2/get_res_privilege/lite` |
| 播放授权 | `gateway.kugou.com/v1/authorization` + `/v5/url`（Auth 参数） |
| 匿名/试听 | `gateway.kugou.com/v5/url`（`IsFreePart=1`） |
| 设备注册 | `userservice.kugou.com/risk/v2/r_register_dev` |
| 免费听 | `gateway.kugou.com/concepts/v1/ai/recommend_song` |
| 歌词 | `lyrics.kugou.com/v1/search` + `/download`（KRC/LRC） |
| 专辑/歌手 | `/kmr/v2/albums`、`/v1/album_audio/lite`、`/kmr/v3/author` |
| 歌单 | `/v3/get_list_info`、`/pubsongs/v2/get_other_list_file_nofilt`、`/v7/get_all_list` |
| 排行榜 | `/openapi/kmr/v2/rank/audio`、`/ocean/v6/rank/*` |
| 每日 VIP | `/youth/v1/recharge/receive_vip_listen_song`（仅用户确认后调用） |

## 安全与隔离

- 概念版 Token 保存在 `melox_kugou_lite_session` 独立加密偏好中；标准版保存在
  `melox_kugou_session`，两者即使同一账号也不会互相覆盖。
- 概念版使用独立的 GUID/MID/DEV 设备身份。
- 日志只输出 hash、音质和状态，不输出 Cookie、Token、auth 或完整播放 URL。
- 播放 URL 的请求头只带 UA/Referer，不把账号 Cookie 交给 CDN。
- 不破解 DRM、不伪造会员权益、不绕过付费限制。音质是否可用完全依据上游
  `relate_goods` 的 `privilege/status/pay_type` 和账号返回的 VIP 状态。
- 每日 VIP 领取只在设置页由用户点击「确认领取」后执行一次；没有定时或启动时自动领取。

## 音质策略

请求音质由高到低回退：

| MeloX 音质 | 概念版 quality |
| --- | --- |
| Standard | `128` |
| High | `320` → `128` |
| Lossless | `flac` → `320` → `128` |
| HiResolution | `high` → `flac` → `320` → `128` |
| Immersive | `viper_atmos` → `viper_clear` → `high` → … |
| Master | `viper_clear` → `viper_atmos` → `high` → … |

`viper_tape` 需要上游转码、`super` 为 DSD，Media3 默认解码器不能直接播放，因此不会
主动请求；如果上游返回未知格式也会被解码器格式白名单过滤。标准版 128 在概念版中
可能是 OGG，`need_ogg=1` 已包含在 Auth URL 请求中。

## 验证状态

已执行（本机，无真实账号）：

- `:app:testDebugUnitTest`：通过，包含搜索/免费听嵌套字段、权益、VIP、音质回退、
  URL 解析和两平台密钥隔离的单元测试。
- Python 直连匿名验证（仅用于确认协议，不在仓库保存凭据）：
  - 概念版搜索返回真实结果。
  - `privilege/lite` 返回 `relate_goods`、OGG hash、`privilege`、`status`、`pay_type`。
  - `register_dev` 返回有效 `dfid`。
  - 使用该 dfid 调用 `/v5/url` 能获取免费曲目的完整 URL，付费曲目返回试听 URL。

**未验证（没有真实账号/真机，禁止声称通过）**：

- 概念版 QR 登录真实扫码。
- 登录用户的 Auth URL、概念版 VIP、超级 VIP 音质。
- 每日 VIP 领取是否仍开放、是否受账号/地区/活动时间限制。
- 歌词逐字/翻译在真实曲目上的完整性。
- 歌单写入、推荐/排行榜在概念版账号下的完整行为。
- 手机端 ExoPlayer 对 OGG/FLAC/Hi-Res 的实际解码与 seek。

## 已知限制

- 目前只实现 QR 登录，没有手机号/密码登录；Token 失效后需要重新扫码，未实现
  `/login/token` 自动续期。
- 标准版 128 与概念版 128 的权益判定不同；以 `privilege/lite` 的 `_msg` 为准，
  不能把 `privilege != 0` 一律当成付费。
- 概念版接口可能随官方客户端升级变化；错误会返回可诊断信息，不做静默降级。
- 收藏单曲没有稳定的官方写入接口，因此未声明 `FavoriteCapability`；歌单写入使用
  现有 `PlaylistWriteCapability`，真实写入需账号验证。
- 不使用任何 DRM 绕过或会员伪造逻辑。
- 概念版 CDN 仍返回 `http://fs.youthandroid*.kugou.com`，且该主机证书与域名不匹配；
  `network_security_config.xml` 只对 `kugou.com` 子域放开明文媒体流量，其他音源仍强制
  HTTPS。API 请求本身仍全部使用 HTTPS。
