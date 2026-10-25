package com.lladlam.melox.core.provider.kugou

import android.content.Context
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class KugouLiteCheckInState(
    val autoEnabled: Boolean = true,
    val successDay: String? = null,
    val upgradedDay: String? = null,
    val attemptDay: String? = null,
    val attemptAtMs: Long = 0L,
    val message: String? = null,
)

data class KugouLiteCheckInResult(
    val day: String,
    /** Today's free-listen reward was claimed or had already been claimed. */
    val claimed: Boolean,
    /** Today's concept-VIP upgrade succeeded or had already been requested. */
    val upgraded: Boolean,
    val message: String,
)

/** Local state for the daily 酷狗概念版 check-in. Never leaves the device. */
object KugouLiteCheckInStore {
    private const val PreferencesName = "melox_kugou_lite_checkin"
    private const val KeyAuto = "auto_enabled"
    private const val KeySuccessDay = "success_day"
    private const val KeyUpgradedDay = "upgraded_day"
    private const val KeyAttemptDay = "attempt_day"
    private const val KeyAttemptAt = "attempt_at"
    private const val KeyMessage = "message"

    fun read(context: Context): KugouLiteCheckInState {
        val preferences = context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        return KugouLiteCheckInState(
            autoEnabled = preferences.getBoolean(KeyAuto, true),
            successDay = preferences.getString(KeySuccessDay, null),
            upgradedDay = preferences.getString(KeyUpgradedDay, null),
            attemptDay = preferences.getString(KeyAttemptDay, null),
            attemptAtMs = preferences.getLong(KeyAttemptAt, 0L),
            message = preferences.getString(KeyMessage, null),
        )
    }

    fun setAutoEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit().putBoolean(KeyAuto, enabled).apply()
    }

    internal fun record(context: Context, result: KugouLiteCheckInResult, complete: Boolean) {
        val editor = context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(KeyAttemptDay, result.day)
            .putLong(KeyAttemptAt, System.currentTimeMillis())
            .putString(KeyMessage, result.message)
        if (result.claimed) editor.putString(KeySuccessDay, result.day)
        if (result.upgraded) editor.putString(KeyUpgradedDay, result.day)
        if (complete) editor.putString(KeySuccessDay, result.day)
        editor.apply()
    }
}

/**
 * Claims both daily rewards in one run:
 *
 * 1. `receive_vip_listen_song` — today's free-listening VIP,
 * 2. `upgrade_vip_reward` — the concept-VIP upgrade.
 *
 * Failures of one step do not prevent the other from running. `131001` means
 * "already received today" and counts as success; `20028` means the account is
 * restricted for this activity.
 */
object KugouLiteDailyCheckIn {
    private val mutex = Mutex()
    private const val AUTO_RETRY_COOLDOWN_MS = 10 * 60 * 1000L

    /**
     * @param requireAutoEnabled when false the caller (the settings page) is
     *   running a manual check-in and the auto toggle is ignored.
     * @return null when the check-in was skipped as not due / not logged in.
     */
    suspend fun run(
        context: Context,
        force: Boolean = false,
        requireAutoEnabled: Boolean = true,
    ): KugouLiteCheckInResult? = mutex.withLock {
        val app = context.applicationContext
        val state = KugouLiteCheckInStore.read(app)
        val day = LocalDate.now().toString()
        val session = KugouSessionStore.readLite(app)
        if (!shouldRun(
                autoEnabled = state.autoEnabled,
                requireAutoEnabled = requireAutoEnabled,
                loggedIn = session.isLoggedIn,
                successDay = state.successDay,
                attemptDay = state.attemptDay,
                attemptAtMs = state.attemptAtMs,
                day = day,
                nowMs = System.currentTimeMillis(),
                force = force,
            )
        ) {
            return@withLock null
        }
        val provider = KugouLiteProvider(
            sessionProvider = { KugouSessionStore.readLite(app) },
            persistAuth = { auth -> KugouSessionStore.updateLiteEntitlements(app, auth = auth) },
            persistDfid = { dfid -> KugouSessionStore.updateLiteDfid(app, dfid) },
            onAuthExpired = { KugouSessionStore.clearLiteLogin(app) },
            persistVip = { value ->
                KugouSessionStore.updateLiteEntitlements(
                    context = app,
                    vipType = value.vipType,
                    vipExpiresAtEpochSeconds = value.expiresAtEpochSeconds,
                    superVip = value.superVip,
                    vipActive = value.isVip,
                    freeListenEligible = value.freeListen,
                )
            },
        )
        val freeListen = reward("畅听 VIP") { provider.claimDailyVip() }
        val concept = reward("概念版 VIP") { provider.upgradeDailyVip() }
        val message = buildString {
            append("今日签到：")
            append(freeListen.message)
            append("；")
            append(concept.message)
            append("。")
        }
        val result = KugouLiteCheckInResult(
            day = day,
            claimed = freeListen.completed,
            upgraded = concept.completed,
            message = message,
        )
        KugouLiteCheckInStore.record(
            app,
            result,
            complete = freeListen.completed && concept.completed,
        )
        runCatching { provider.vipStatus(force = true) }
        return@withLock result
    }

    private suspend fun reward(label: String, action: suspend () -> Unit): RewardOutcome =
        try {
            action()
            RewardOutcome(true, "${label}已领取")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: KugouApiException) {
            when (error.errorCode) {
                131001 -> RewardOutcome(true, "${label}今日已领取")
                20028 -> RewardOutcome(false, "${label}当前账号暂时无法领取")
                else -> RewardOutcome(false, "$label 领取失败：${error.message ?: "未知错误"}")
            }
        } catch (error: Exception) {
            RewardOutcome(false, "$label 领取失败：${error.message ?: "网络异常"}")
        }

    internal fun shouldRun(
        autoEnabled: Boolean,
        requireAutoEnabled: Boolean,
        loggedIn: Boolean,
        successDay: String?,
        attemptDay: String?,
        attemptAtMs: Long,
        day: String,
        nowMs: Long,
        force: Boolean,
    ): Boolean {
        if (!loggedIn) return false
        if (requireAutoEnabled && !autoEnabled) return false
        if (force) return true
        if (successDay == day) return false
        if (attemptDay == day && nowMs - attemptAtMs < AUTO_RETRY_COOLDOWN_MS) return false
        return true
    }

    private data class RewardOutcome(val completed: Boolean, val message: String)
}
