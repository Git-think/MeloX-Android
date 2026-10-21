package com.lladlam.melox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lladlam.melox.R
import com.lladlam.melox.ui.glass.MeloXGlassButton
import com.lladlam.melox.ui.glass.MeloXGlassButtonStyle

/**
 * Full first-run flow modeled on the iOS welcome + account pages. The legal
 * consent dialog stays the first step because Android needs an explicit
 * agreement before sign-in and remote features.
 */
@Composable
internal fun MeloXOnboardingWelcomePage(
    onContinue: () -> Unit,
    onLicenses: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(112.dp).clip(MaterialTheme.shapes.large),
            )
            Spacer(Modifier.height(26.dp))
            Text(
                "MeloX",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.onboarding_welcome_subtitle),
                modifier = Modifier.padding(top = 10.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                lineHeight = 23.sp,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.onboarding_disclaimer),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MeloXGlassButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.BorderedProminent,
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Text(stringResource(R.string.onboarding_continue), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            MeloXGlassButton(
                onClick = onLicenses,
                modifier = Modifier.fillMaxWidth(),
                style = MeloXGlassButtonStyle.Plain,
                contentPadding = PaddingValues(vertical = 9.dp),
            ) {
                Text(stringResource(R.string.onboarding_licenses), fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun MeloXOnboardingAccountPage(
    isLoggedIn: Boolean,
    nickname: String?,
    avatarUrl: String?,
    onLogin: () -> Unit,
    onFinish: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (isLoggedIn) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    nickname.orEmpty().ifBlank { stringResource(R.string.onboarding_account_title) },
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.onboarding_account_logged_in),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Image(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(88.dp).clip(MaterialTheme.shapes.large),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.onboarding_account_title),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.onboarding_account_body),
                    modifier = Modifier.padding(top = 10.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Column(
                    Modifier.fillMaxWidth().padding(top = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(
                        R.string.onboarding_benefit_sync,
                        R.string.onboarding_benefit_recommend,
                        R.string.onboarding_benefit_local,
                    ).forEach { benefit ->
                        Text(
                            "• ${stringResource(benefit)}",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .74f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            if (isLoggedIn) {
                MeloXGlassButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.onboarding_start), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                MeloXGlassButton(
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.BorderedProminent,
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.app_login_netease), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                MeloXGlassButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth(),
                    style = MeloXGlassButtonStyle.Plain,
                    contentPadding = PaddingValues(vertical = 9.dp),
                ) {
                    Text(stringResource(R.string.onboarding_maybe_later), fontSize = 14.sp)
                }
            }
        }
    }
}
