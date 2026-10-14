package app.coolapk.kmp.ui.theme

import android.content.Context

/** 外观偏好：跟随系统、固定浅色或固定深色。 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

internal class ThemePreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "coolapk_kmp_settings",
        Context.MODE_PRIVATE,
    )

    fun load(): ThemeMode =
        runCatching { ThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, null).orEmpty()) }
            .getOrDefault(ThemeMode.SYSTEM)

    fun save(mode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
