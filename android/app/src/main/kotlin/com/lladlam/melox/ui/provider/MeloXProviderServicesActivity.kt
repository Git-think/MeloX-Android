package com.lladlam.melox.ui.provider

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lladlam.melox.core.account.rememberNeteaseSessionStore
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.provider.MusicProviderSelectionStore
import com.lladlam.melox.ui.prepareMeloXPageWindow
import com.lladlam.melox.ui.theme.MeloXTheme
import com.lladlam.melox.ui.MeloXPageHost
import com.lladlam.melox.ui.startMeloXPage

class MeloXProviderServicesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prepareMeloXPageWindow()
        setContent {
            MeloXPageHost {
                MeloXTheme {
                    ProviderServicesPage(onBack = ::finish)
                }
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startMeloXPage(
                Intent(context, MeloXProviderServicesActivity::class.java)
                    .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            )
        }
    }
}

@Composable
internal fun ProviderServicesPage(
    onBack: () -> Unit,
    onSourceSelected: (MusicSource) -> Unit = {},
    session: NeteaseSessionStore = rememberNeteaseSessionStore(),
) {
    val context = LocalContext.current
    var currentSource by remember { mutableStateOf(MusicProviderSelectionStore.selectedSource(context)) }
    ProviderServicesScreen(
        currentSource = currentSource,
        onSourceSelected = { source ->
            currentSource = source
            MusicProviderSelectionStore.setSelectedSource(context, source)
            onSourceSelected(source)
        },
        neteaseSession = session,
        onNeteaseLogin = {
            com.lladlam.melox.ui.account.MeloXNeteaseLoginActivity.launch(context)
        },
        onBack = onBack,
    )
}
