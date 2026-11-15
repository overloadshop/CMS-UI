package ldyc.qy.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object DeveloperSettings {
    private const val PREFS_NAME = "developer_settings"
    private const val KEY_UNLOCKED = "developer_unlocked"
    private const val KEY_LIQUID_GLASS_OVERLAY = "liquid_glass_overlay"
    private const val KEY_DEBUG_OVERLAY = "debug_overlay"

    private var initialized = false
    private var prefs: android.content.SharedPreferences? = null

    var developerUnlocked by mutableStateOf(false)
        private set

    var liquidGlassOverlayEnabled by mutableStateOf(false)
        private set

    var debugOverlayEnabled by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (initialized) return
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        developerUnlocked = prefs?.getBoolean(KEY_UNLOCKED, false) ?: false
        liquidGlassOverlayEnabled = prefs?.getBoolean(KEY_LIQUID_GLASS_OVERLAY, false) ?: false
        debugOverlayEnabled = prefs?.getBoolean(KEY_DEBUG_OVERLAY, false) ?: false
        initialized = true
    }

    fun unlockDeveloperMode() {
        developerUnlocked = true
        prefs?.edit()?.putBoolean(KEY_UNLOCKED, true)?.apply()
    }

    fun updateLiquidGlassOverlayEnabled(enabled: Boolean) {
        liquidGlassOverlayEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_LIQUID_GLASS_OVERLAY, enabled)?.apply()
    }

    fun updateDebugOverlayEnabled(enabled: Boolean) {
        debugOverlayEnabled = enabled
        prefs?.edit()?.putBoolean(KEY_DEBUG_OVERLAY, enabled)?.apply()
    }
}
