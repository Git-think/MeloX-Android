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
