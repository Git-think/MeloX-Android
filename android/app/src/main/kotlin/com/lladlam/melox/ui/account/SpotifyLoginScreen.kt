package com.lladlam.melox.ui.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lladlam.melox.core.network.MeloXHttpClient
import com.lladlam.melox.core.provider.spotify.SpotifyClientConfig
import com.lladlam.melox.core.provider.spotify.SpotifyOAuth
import com.lladlam.melox.core.provider.spotify.SpotifySessionStore
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigPolicy
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle
import com.lladlam.melox.ui.glass.MeloXGlassTextField
import com.lladlam.melox.ui.glass.MeloXSystemColors
import com.lladlam.melox.ui.legal.MeloXLegalLinks
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.lladlam.melox.R

@Composable
fun SpotifyLoginScreen(onDismiss: () -> Unit, onLoggedIn: () -> Unit) {
    val context = LocalContext.current.applicationContext
    if (!MeloXRemoteConfigPolicy.capabilityEnabled(context, "spotify_oauth")) {
        BackHandler(onBack = onDismiss)
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MeloXGlassButton(onClick = onDismiss, style = MeloXGlassButtonStyle.Plain) { Text("关闭") }
            Text("Spotify 登录暂时不可用", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Spotify OAuth 接口已由远程兼容性配置临时关闭。你可以稍后更新软件或重试。",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f),
            )
            MeloXLegalLinks()
        }
        return
    }
    var error by remember { mutableStateOf<String?>(null) }
    var authorizing by remember { mutableStateOf(false) }
    var clientIdInput by remember { mutableStateOf(SpotifyClientConfig.read(context)) }
    var configured by remember { mutableStateOf(SpotifyClientConfig.isConfigured(context)) }
    val scope = rememberCoroutineScope()
    val doneMessage = stringResource(R.string.account_spotify_return)

    BackHandler(onBack = onDismiss)

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MeloXGlassButton(onClick = onDismiss, style = MeloXGlassButtonStyle.Plain) {
                Text("取消", color = MeloXSystemColors.Red)
            }
            Text("登录 Spotify", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier)
        }
        Text(
            "使用系统浏览器完成 Spotify Authorization Code + PKCE 授权。MeloX 不使用 Client Secret，也不会读取 Spotify App 私有凭据。",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .62f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
        if (!configured) {
            Text(
                "请提供 Spotify Developer App 的 Client ID；需在 Spotify Dashboard 注册 ${SpotifyOAuth.RedirectUri}。该值会保存在本机，之后可直接修改。",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .62f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            MeloXGlassTextField(
                value = clientIdInput,
                onValueChange = { clientIdInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入你的 Spotify Client ID", maxLines = 1) },
                singleLine = true,
            )
            if (clientIdInput.isNotBlank()) {
                MeloXGlassButton(
                    onClick = {
                        SpotifyClientConfig.write(context, clientIdInput)
                        configured = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.Plain,
                ) { Text("保存 Client ID") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        MeloXGlassButton(
            onClick = {
                // 上游 0.6.2（22ebcb3）：改走 loopback 授权，本地起一个回环端口接回调，
                // 不再依赖 SpotifyCallbackActivity。
                if (authorizing) return@MeloXGlassButton
                authorizing = true
                error = null
                scope.launch {
                    runCatching {
                        SpotifyOAuth(context, SpotifyClientConfig.effective(context), MeloXHttpClient.shared)
                            .authorize(doneMessage)
                    }.onSuccess {
                        authorizing = false
                        onLoggedIn()
                    }.onFailure {
                        authorizing = false
                        error = it.message ?: context.getString(R.string.account_spotify_auth_failed)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = configured && !authorizing,
            style = MeloXGlassButtonStyle.BorderedProminent,
        ) { Text(stringResource(if (authorizing) R.string.account_spotify_waiting else R.string.account_spotify_browser)) }
        Spacer(Modifier.weight(1f))
        MeloXLegalLinks(tint = androidx.compose.ui.graphics.Color(0xFF1DB954))
    }
}
