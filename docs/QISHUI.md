# 汽水音乐音源

## 来源与接入方式

- 参考仓库：[guowenye/qishui-api](https://github.com/guowenye/qishui-api)，MIT，
  核对版本 `e409c10fe7441a10d370da7da61d62bbc1e5126c`。
- 本地克隆位于 `MusicSourcesCompare/qishui-api`，与 Android 仓库同级的参考项目目录中。
- Android 采用原生 Kotlin 直连，不要求部署 Node 服务。协议字段与流程依据该仓库
  `src/qishuiClient.js`、`src/normalizers.js` 核对；许可证随 APK 保存在
  `assets/legal/qishui-api-LICENSE.txt`。
- 音源 ID 为 `qishui`，曲目／歌单 ID 始终按字符串保存，避免 64 位 ID 经浮点数丢失精度。

## 功能与 UI

- 在既有音乐服务列表选择「汽水音乐」，继续使用公共首页、搜索、音乐库、歌单、播放器与歌词 UI。
- 搜索：`api.qishui.com/luna/pc/search/track`，按照上游返回的游标续页，不假设分页步长。
- 推荐歌单：`beta-luna.douyin.com/luna/discover/mix`。
- 歌单详情：`/luna/playlist/detail`，读取 `media_resources`，保存下一页的不透明游标。
- 歌曲／歌词／播放：`/luna/h5/seo_track`，读取原文 LRC 与服务端提供的播放变体。
- 登录后优先请求 `/luna/media-player` 的当前账号播放信息；无可用明文播放变体时回退 H5，仍按时长识别试听。
- 账号：`/passport/web/get_qrcode/` 与 `/passport/web/check_qrconnect/`，随后用 `/luna/pc/me` 校验。
  二维码创建阶段的 Cookie 仅用于本次轮询。依参考仓库当前说明，需使用已登录的抖音 App 扫码。
- 音乐库：`/luna/pc/me/playlist`；当前接口一次返回列表，Android 本地分页，不反复展示同一批歌单。
- 登录信息独立加密保存于 `melox_qishui_session`；退出登录保留设备身份，不影响其他音乐源。
- 播放解析缓存将汽水会话变化纳入认证指纹；CDN 播放请求不携带账号 Cookie。

## 播放边界

- 只使用当前请求返回的 HTTPS 音频地址，校验汽水／字节 CDN 域名。
- 对比播放流时长与原曲时长，将明显短于原曲的资源标记为 `PlaybackResolution.Preview`。
- 音质选择依据上游实际变体和码率，不把请求的无损／高解析音质直接当作实际音质。
- 当前没有接入参考仓库的 spade／加密容器解密功能；需要该路径时返回明确的不可播放结果。
- 未声明下载、收藏写入、歌单编辑、专辑详情或艺人详情能力。

## 验证记录

- 匿名在线核对：推荐歌单接口返回 8 个内容块；示例曲目的 H5 接口返回歌曲、歌词和两档 AAC/M4A 播放变体。
- 同一示例原曲长 297240ms，匿名流约 30047ms；属于试听，不能据此宣称完整播放已验证。
- PC 搜索在当前匿名请求中返回 HTTP 200 空响应。参考仓库注明该接口可能需要 Cookie 或
  `X-Helios`／`X-Medusa`；当前 Android 提供扫码会话，不生成这两个 PC 签名。
- 已添加离线回归覆盖长 ID、毫秒时长、嵌套资源、封面、试听识别、实际音质、会话隔离、
  CDN 地址边界、分页游标与未登录音乐库；执行结果以当前构建日志为准。
- 真实扫码、登录态搜索、完整歌曲播放与真机 UI 由用户测试，尚不宣称验收通过。
