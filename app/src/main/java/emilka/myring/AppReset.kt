package emilka.myring

import android.content.Context

/** Pełny reset aplikacji: ustawienia, przypomnienia, alarmy (zapas opcjonalnie). */
object AppReset {
    fun resetToSetup(context: Context, clearStock: Boolean = false) {
        // Zatrzymaj aktywny alarm zanim wyczyścimy prefs.
        ReminderCenter.stopService(context)
        Notifications.cancelAlarmNotifications(context)
        Notifications.cancelDayBefore(context)

        SettingsStore.clearSetup(context)
        ReminderCenter.clearReminderState(context)
        // Legacy godzina w myring_prefs (AlarmScheduler) — bez tego reschedule mógłby wrócić.
        context.getSharedPreferences("myring_prefs", Context.MODE_PRIVATE).edit().clear().apply()

        if (clearStock) StockStore.clearAll(context)
        ReminderScheduler.cancelAllAlarms(context)
        WidgetRefreshScheduler.cancelMidnight(context)
        MyringWidgetProvider.refreshAll(context)
    }
}