package emilka.myring

import android.app.Application
import emilka.myring.ui.theme.ThemeMode

/** Ustawia night mode zanim wystartuje pierwsze Activity (bez białego flasha). */
class MyringApplication : Application() {
    override fun onCreate() {
        ThemeMode.applyFromStore(this)
        super.onCreate()
    }
}
