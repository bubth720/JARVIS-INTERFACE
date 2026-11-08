package com.jarvis.interfaceapp.m51

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

/** Reads Android's accessibility/animation choice. It never writes system settings or safety thresholds. */
@Composable
fun rememberSystemMotionAllowed(): State<Boolean> {
    val resolver=LocalContext.current.contentResolver
    fun read(): Boolean = try { Settings.Global.getFloat(resolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f } catch(_: SecurityException) { false }
    val state=remember(resolver) { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange: Boolean) { state.value=read() } }
        try { resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer) }
        catch(_: SecurityException) { state.value=false }
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return state
}
