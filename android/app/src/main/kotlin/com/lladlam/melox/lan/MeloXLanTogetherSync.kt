package com.lladlam.melox.lan

/** Let a previous seek finish before correcting the next host heartbeat. */
internal fun shouldCorrectTogetherPosition(
    ready: Boolean,
    localPositionMs: Long,
    hostPositionMs: Long,
    elapsedSinceSeekMs: Long,
): Boolean = ready && elapsedSinceSeekMs >= 3_000L &&
    kotlin.math.abs(localPositionMs - hostPositionMs) > 250L

internal fun togetherPlaybackRate(errorMs: Long): Float = when {
    kotlin.math.abs(errorMs) <= 40L -> 1f
    else -> (1f + errorMs / 5_000f).coerceIn(.97f, 1.03f)
}

internal fun togetherTargetPosition(positionMs: Long, playing: Boolean, sampleMs: Long, nowMs: Long, offsetMs: Long): Long =
    positionMs + if (playing && sampleMs > 0L) (nowMs + offsetMs - sampleMs).coerceIn(0L, 2_000L) else 0L

internal fun togetherShouldWaitForEnd(hostPositionMs: Long, guestPositions: List<Long>): Boolean =
    guestPositions.all { hostPositionMs - it <= 5_000L }
