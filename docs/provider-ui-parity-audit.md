# 音乐源 UI / 功能接线扫描

基准：网易云当前公共 UI；范围：注册表中的 QQ、酷狗、酷我、Apple Music、Bilibili、Spotify、YouTube Music、Jellyfin、本地音乐。

## 扫描结果与本次同步

| 页面 | 发现的差异 | 同步方式 |
| --- | --- | --- |
| 首页 / 发现 / 设置 | 已共用主页面组件，数据通过 provider 适配 | 保持公共组件路径 |
| 搜索歌曲 | provider 分支将长按菜单传为 null | 接入公共歌曲菜单，由原始 MusicTrack 分发至 provider 操作 |
| 歌单 / 专辑 | 非网易云隐藏通用菜单，支持写入的音源只打开编辑面板 | 共用分享、刷新、排序菜单；批量下载按 DownloadCapability 显示；编辑入口保留 |
| 歌单歌曲 | 部分音源没有更多菜单，部分只有删除 / 移序 | 所有音源开放通用歌曲操作，编辑歌单作为子入口 |
| 歌手详情 | 音乐库与搜索使用不同的简化列表 | 两个入口统一到公共详情列表，复用背景、搜索、排序、队列、歌曲菜单 |
| 排行榜详情 | 独立列表缺少歌曲菜单 / 搜索 / 排序 | 加载后接入公共详情列表 |
| 收藏列表 / 专辑 / 歌手 / 可写歌单选择 | 多处仅读第一页 | 依据 hasMore / total 分页，去重；重复页与空页终止 |
| 批量下载 | 固定调用网易云下载器 | 保留原始 MusicTrack，按来源路由下载器与音质 |
| 分享 | provider 专辑可能生成网易云歌单链接 | 非网易云内容分享标题、作者与真实音源，不拼造网易云链接 |

## 能力边界

界面以 provider **实际实现的接口**为依据，不仅看 capabilities 文案：

- QQ / 酷狗 / Apple Music：搜索、歌单、专辑、歌手等已有读取能力接公共 UI；未实现 DownloadCapability 时不显示无效下载入口。
- 酷我 / Bilibili：接入已有搜索 / 歌单和通用队列、分享菜单，不虚构专辑、歌手或写入 API。
- Spotify / YouTube Music：已有 DownloadCapability、FavoriteCapability、PlaylistWriteCapability 和 PlaylistSyncCapability，接公共菜单与批量下载。
- Jellyfin：已有收藏、歌单写入与同步能力，沿用其原生 ID；未实现的下载能力不显示。
- 本地音乐：通用歌曲操作继续保留识曲和本地收藏 / 歌单能力。

网易云评论、动态、私信、百科、一起听等专属远端接口，不会用 UI 显示 ID 向其他音乐源伪接。

## 验证

运行 Kotlin 编译与单元测试；分页回归覆盖短页续传、重复第一页、跨音源相同 ID、空页与陈旧 total。各账号的远端读写和设备 UI 由人工验收。

## Square / YouTube Music 对照补全（2026-10-17）

对照 `Square/.../backend/youtube/YouTubeBackend.kt`、`YouTubeAccount.kt`、`YouTubeLoginScreen.kt` 和 `YouTubePlayerFactory.kt`：

| 功能 | MeloX 接入结果 |
| --- | --- |
| Google 登录与认证 | Cookie、visitorData、dataSyncId、pageId、Google 账号序号共同保存；普通账号和品牌频道都发送认证头 |
| 频道选择 | 两个音乐服务入口共用频道选择组件；pageId / dataSyncId 配对切换，验证成功才保存，拒绝或取消恢复当前已保存会话 |
| 会话检查 | 音乐服务页检查账号接口；401 / 403 提示重新登录，网络错误不清除会话；提供刷新登录入口，不预先退出账号 |
| 音乐库 | 已保存会话直接读取歌单，不再把账号资料接口失败当成未登录；切换频道后通知音乐库刷新 |
| 搜索、音乐库、歌单分页 | 接入 InnerTube continuation，缓存超出 UI 页大小的结果；缓存键隔离账号、频道和语言，首页刷新重建对应缓存；重复游标和重复结果有终止保护 |
| 首页 | 首屏不足公共首页请求数量时继续读取后续 shelves，最多 8 次续传，后续页失败保留已取回内容 |
| 歌单写入 | 可写选择器仅列出服务器标记可编辑的歌单；公共详情按可编辑标记显示编辑操作；喜欢的歌曲通过 like / removelike 修改 |
| 歌单移序 | 读取后续页，按 setVideoId 定位成员，保留同一视频在歌单中的不同成员 |
| 播放 / 下载 | 沿用 InnerTube 目录 + NewPipe 音频流解析和公共播放器、下载器接线 |
| 歌词 | 所有音乐源统一通过 MeloXProviderLyricsLoader，复用公共自动匹配、绑定、缓存、翻译语言和升级逻辑；provider 原生歌词仅作为公共链的候选 / 回退 |

Square 的独立播放器和主页组件不是 MeloX 公共 UI 的接入目标；YouTube 首页 chips、视频模式没有对应的公共能力接口，尚未实现。账号下的远端读写、频道切换与翻译效果由真机验收确认。
