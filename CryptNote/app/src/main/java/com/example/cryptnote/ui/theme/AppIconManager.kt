package com.example.cryptnote.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.cryptnote.data.preferences.ThemeColor

object AppIconManager {

    private val aliasMap = mapOf(
        ThemeColor.WARM_ESPRESSO to "com.example.cryptnote.MainActivityAliasEspresso",
        ThemeColor.CYBER_TEAL to "com.example.cryptnote.MainActivityAliasTeal",
        ThemeColor.NEON_BLUE to "com.example.cryptnote.MainActivityAliasBlue",
        ThemeColor.ELECTRIC_PURPLE to "com.example.cryptnote.MainActivityAliasPurple",
        ThemeColor.EMERALD_MATRIX to "com.example.cryptnote.MainActivityAliasEmerald",
        ThemeColor.SUNSET_AMBER to "com.example.cryptnote.MainActivityAliasAmber",
        ThemeColor.CRIMSON_ROSE to "com.example.cryptnote.MainActivityAliasRose"
    )

    fun changeAppIcon(context: Context, targetColor: ThemeColor) {
        val pm = context.packageManager
        val activeAlias = aliasMap[targetColor] ?: "com.example.cryptnote.MainActivityAliasEspresso"

        aliasMap.values.forEach { alias ->
            val componentName = ComponentName(context.packageName, alias)
            val state = if (alias == activeAlias) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            try {
                if (pm.getComponentEnabledSetting(componentName) != state) {
                    pm.setComponentEnabledSetting(
                        componentName,
                        state,
                        PackageManager.DONT_KILL_APP
                    )
                }
            } catch (_: Exception) {
                // Ignore component setting errors
            }
        }
    }
}
