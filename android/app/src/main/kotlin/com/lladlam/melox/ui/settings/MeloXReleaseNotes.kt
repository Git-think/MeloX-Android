package com.lladlam.melox.ui.settings

import java.util.Locale

/** Local, offline "what's new" notes for the installed build. */
internal data class MeloXReleaseNotes(
    val version: String,
    val entries: List<String>,
)

internal object MeloXReleaseNotesCatalog {
    fun current(version: String = com.lladlam.melox.BuildConfig.VERSION_NAME): MeloXReleaseNotes {
        val chinese = Locale.getDefault().language.lowercase(Locale.ROOT).startsWith("zh")
        val entries = if (chinese) chineseEntries else englishEntries
        return MeloXReleaseNotes(version, entries)
    }

    private val chineseEntries = listOf(
        "新增汽水音乐音源，接入推荐歌单、搜索、歌单、歌词与扫码登录；播放区分试听，登录态功能待用户验收。",
        "酷狗音源仅保留概念版，账号独立；扩展设置新增签到领会员与可选 VIP 升级页面，均需手动确认。",
        "按音源显示设置入口：非网易云隐藏私信和一起听；统一账号／音乐服务页转场，修复返回时 Dock 与迷你播放器突然出现。",
        "歌词罗马音按发音单元对齐原文，日语组合假名与汉字词分组显示；伴唱按开唱时间排序。",
        "设备面板支持持久连接、DLNA 与小米私有 MiPlay；局域网一起放增加修订 2 协调与旧端降级，跨平台互通待验收。",
        "新增实验性 USB 独占输出，仅限全速 UAC1 双声道 PCM16 自适应／同步设备，真实 DAC 兼容性待验收。",
        "UI 图标字形缩至原尺寸的 83%，保留点击区域；更新项目致谢，感谢中末捐赠 8.88 元。",
        "重新迁移 EVA 动态排版、Text PV 模板与全屏天际歌词。",
        "网易云音乐云盘重做分页、全量搜索、播放全部、下拉刷新与侧滑删除。",
        "云盘上传按 iOS 流程重写：元数据回退、NOS 对象键编码与失败提示。",
        "音乐库新增统一搜索，覆盖歌曲、歌单、专辑、艺人与最近播放。",
        "关于页新增版本更新日志和赞助者列表；首次启动补齐欢迎与账号页。",
        "新增独立歌曲详情页与 90 分钟睡眠定时，并显示剩余时间。",
        "恢复音乐库歌单打开和关闭动画，并保留分屏详情退场内容。",
        "设置 → 通用可配置分屏、Dock 与迷你播放器位置。",
        "修复歌词翻译语言、应用语言入口、YouTube 音乐库与账号认证。",
    )

    private val englishEntries = listOf(
        "Added Qishui Music with recommendations, search, playlists, lyrics, and QR sign-in; playback identifies previews, with signed-in features pending user validation.",
        "Retained KuGou Lite as the KuGou source with an isolated account; added manually confirmed daily VIP claims and optional upgrades in extension settings.",
        "Made settings source-aware, hiding NetEase messages and Listen Together on other sources; unified account/service transitions and fixed Dock/mini-player pop-in on return.",
        "Aligned romanization with pronunciation units, grouping Japanese contracted kana and kanji words; ordered backing vocals by their actual start times.",
        "Added persistent device sessions, DLNA and private Xiaomi MiPlay; introduced LAN Together revision 2 coordination with legacy fallback, pending cross-platform validation.",
        "Added experimental exclusive USB output for full-speed UAC1 stereo PCM16 adaptive/synchronous devices; real-DAC compatibility is pending validation.",
        "Scaled UI icon glyphs to 83% while retaining touch targets; refreshed project credits and thanked 中末 for donating CNY 8.88.",
        "Re-migrated EVA kinetic typography, Text PV templates, and full-screen Skyline lyrics.",
        "Rebuilt the NetEase cloud drive with paging, whole-drive search, play-all, pull-to-refresh, and swipe delete.",
        "Rewrote cloud upload after the iOS flow: metadata fallback, NOS object-key encoding, and clearer errors.",
        "Added library-wide search for songs, playlists, albums, artists, and recent playback.",
        "Added release notes and sponsor acknowledgements to About, plus a complete welcome/account onboarding flow.",
        "Added a standalone song detail page and a 90-minute sleep timer with remaining time.",
        "Restored library playlist open/close transitions and retained split-screen exit content.",
        "Added split-screen, Dock, and mini-player placement controls under Settings → General.",
        "Fixed lyric translation language, app language access, YouTube Music library, and account authentication.",
    )
}
