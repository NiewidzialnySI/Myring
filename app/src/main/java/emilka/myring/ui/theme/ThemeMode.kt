package emilka.myring.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Synchronizuje tryb nocny Androida z preferencją aplikacji (splash, ikona night, widget). */
object ThemeMode {
    fun apply(preference: ThemePreference) {
        val mode = when (preference) {
            ThemePreference.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePreference.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemePreference.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    fun applyFromStore(context: Context) {
        apply(emilka.myring.SettingsStore.loadThemePreference(context))
    }
}
