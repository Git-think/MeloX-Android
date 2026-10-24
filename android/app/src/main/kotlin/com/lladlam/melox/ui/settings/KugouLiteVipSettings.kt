package com.lladlam.melox.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lladlam.melox.core.provider.kugou.KugouLiteCheckInStore
import com.lladlam.melox.core.provider.kugou.KugouLiteDailyCheckIn
import com.lladlam.melox.core.provider.kugou.KugouLiteProvider
import com.lladlam.melox.core.provider.kugou.KugouLiteVip
import com.lladlam.melox.core.provider.kugou.KugouSessionStore
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassDialog
import com.lladlam.melox.ui.glass.MeloXGlassToggle
import com.lladlam.melox.ui.glass.MeloXIosGroupedList
import com.lladlam.melox.ui.glass.MeloXIosListRow
import com.lladlam.melox.ui.provider.MeloXProviderServicesActivity
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Content hosted by the standard settings detail page, including its backdrop and navigation. */
@Composable
internal fun KugouLiteVipSettings() {
    val context = LocalContext.current
    val app = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(KugouSessionStore.readLite(app)) }
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    var vip by remember { mutableStateOf<KugouLiteVip?>(null) }
    var checkIn by remember { mutableStateOf(KugouLiteCheckInStore.read(app)) }
    var busy by remember { mutableStateOf(false) }
    var confirmCheckIn by remember { mutableStateOf(false) }
    var message by rememberSaveable(session.userId) { mutableStateOf<String?>(null) }
    val provider = remember(app) {
        KugouLiteProvider(
            sessionProvider = { KugouSessionStore.readLite(app) },
            persistAuth = { KugouSessionStore.updateLiteEntitlements(app, auth = it) },
            persistDfid = { KugouSessionStore.updateLiteDfid(app, it) },
            persistVip = { value ->
                KugouSessionStore.updateLiteEntitlements(
                    context = app, vipType = value.vipType,
                    vipExpiresAtEpochSeconds = value.expiresAtEpochSeconds,
                    superVip = value.superVip, vipActive = value.isVip,
                    freeListenEligible = value.freeListen,
                )
            },
        )
    }
    val refresh: () -> Unit = {
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    val current = KugouSessionStore.readLite(app)
                    if (current.userId != session.userId || current.token != session.token) {
                        vip = null
                    }
                    session = current
                    today = LocalDate.now().toString()
                    if (session.isLoggedIn) {
                        // Automatic daily reward; no-op when disabled or already done today.
                        KugouLiteDailyCheckIn.run(app)
                        checkIn = KugouLiteCheckInStore.read(app)
                        checkIn.message?.let { message = it }
                        vip = provider.vipStatus(force = true)
                    } else {
                        checkIn = KugouLiteCheckInStore.read(app)
                        vip = null
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    message = error.message ?: "会员信息刷新失败，请稍后重试"
                } finally {
                    busy = false
                }
            }
        }
    }
    val latestRefresh by rememberUpdatedState(refresh)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latestRefresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
        MeloXIosListRow(
            title = "酷狗概念版账号",
            subtitle = if (session.isLoggedIn) "账号 ID：${session.userId}" else "登录概念版账号后即可签到领会员",
            detail = if (session.isLoggedIn) "已登录" else "去登录",
            onClick = if (busy) null else ({ MeloXProviderServicesActivity.launch(context) }),
            showTopSeparator = false,
        )
        MeloXIosListRow(
            title = "会员状态",
            detail = when {
                !session.isLoggedIn -> "未登录"
                vip == null -> "待刷新"
                vip?.superVip == true -> "超级 VIP"
                vip?.isVip == true -> "VIP 会员"
                vip?.freeListen == true -> "畅听权益"
                else -> "暂无会员权益"
            },
            showTopSeparator = true,
        )
        vip?.expiresAtEpochSeconds?.takeIf { it > 0L }?.let { expiry ->
            MeloXIosListRow(
                title = "会员有效期",
                detail = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(expiry * 1_000L)),
                showTopSeparator = true,
            )
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("每日签到", Modifier.padding(start = 8.dp, bottom = 8.dp), fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
    MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
        MeloXIosListRow(
            title = "今日签到",
            subtitle = today,
            detail = when {
                !session.isLoggedIn -> "未登录"
                checkIn.successDay == today -> "已签到"
                checkIn.attemptDay == today -> "待重试"
                else -> "待签到"
            },
            showTopSeparator = false,
        )
        MeloXIosListRow(
            title = "今日畅听 VIP",
            detail = if (checkIn.successDay == today) "已领取" else "待领取",
            showTopSeparator = true,
        )
        MeloXIosListRow(
            title = "今日概念版 VIP",
            detail = if (checkIn.upgradedDay == today) "已升级" else "待升级",
            showTopSeparator = true,
        )
        MeloXIosListRow(
            title = "自动签到会员",
            subtitle = "每天首次打开 MeloX 时自动领取畅听 VIP，并同时申请概念版 VIP 升级",
            trailing = {
                MeloXGlassToggle(
                    checked = checkIn.autoEnabled,
                    onCheckedChange = { enabled ->
                        KugouLiteCheckInStore.setAutoEnabled(app, enabled)
                        checkIn = KugouLiteCheckInStore.read(app)
                        if (enabled && session.isLoggedIn) refresh()
                    },
                )
            },
            showTopSeparator = true,
        )
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "签到会同时领取今日畅听 VIP 并申请概念版 VIP 升级，两项分别受酷狗活动规则限制；" +
                    "已领取过的一天不会重复领取。实际权益与有效期以酷狗返回结果为准。",
                fontSize = 13.sp, lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            MeloXGlassButton(
                onClick = { confirmCheckIn = true },
                enabled = session.isLoggedIn && !busy,
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.BorderedProminent,
            ) { Text(if (busy) "处理中…" else "签到领取畅听 + 概念版 VIP") }
            MeloXGlassButton(onClick = refresh, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.Plain) { Text("刷新会员状态") }
            message?.let { Text(it, fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
    Text(
        "开启自动签到后，每天首次启动 MeloX 会各尝试一次，失败后 10 分钟内不重复请求；" +
            "关闭后仅在你手动确认时执行。每日额度、活动开放情况和账号资格由酷狗决定。",
        Modifier.padding(horizontal = 6.dp, vertical = 10.dp), fontSize = 12.sp, lineHeight = 18.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
    )

    if (confirmCheckIn) {
        MeloXGlassDialog(visible = true, onDismiss = { confirmCheckIn = false }) {
            Text("签到领会员", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "确认使用当前概念版账号领取今日畅听 VIP，并同时申请概念版 VIP 升级？",
                Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
            )
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeloXGlassButton(onClick = { confirmCheckIn = false }, modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Plain) { Text("取消") }
                MeloXGlassButton(
                    enabled = !busy,
                    onClick = {
                        confirmCheckIn = false
                        if (!busy) {
                            busy = true
                            scope.launch {
                                try {
                                    val result = KugouLiteDailyCheckIn.run(
                                        app, force = true, requireAutoEnabled = false,
                                    )
                                    checkIn = KugouLiteCheckInStore.read(app)
                                    message = result?.message ?: checkIn.message
                                    vip = provider.vipStatus(force = true)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    message = error.message ?: "领取失败，请稍后重试"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f), style = MeloXGlassButtonStyle.BorderedProminent,
                ) { Text("确认领取") }
            }
        }
    }
}
