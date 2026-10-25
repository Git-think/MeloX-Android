package com.lladlam.melox.playback

/**
 * User-selectable action for the two extra system-media buttons Mei/MeloX
 * publish next to the standard transport controls.
 */
internal enum class MeloXNotificationAction {
    /** Cycles shuffle → repeat-one → repeat-all. */
    PlayMode,
    /** Toggles the favorite state of the current NetEase/provider song. */
    Favorite,
    /** Enters/leaves floating-lyrics picture-in-picture. */
    FloatingLyrics,
    /** No button on this side. */
    None,
    ;

    companion object {
        fun fromPreference(value: String?, default: MeloXNotificationAction): MeloXNotificationAction =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default
    }
}
