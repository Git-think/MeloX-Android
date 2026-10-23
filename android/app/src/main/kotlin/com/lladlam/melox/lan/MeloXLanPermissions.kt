package com.lladlam.melox.lan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal object MeloXLanPermissions {
    /** Android 17 (API 37) gates direct local-network access behind a runtime permission. */
    fun has(context: Context): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) == PackageManager.PERMISSION_GRANTED

    fun permission(): String = Manifest.permission.ACCESS_LOCAL_NETWORK
}
