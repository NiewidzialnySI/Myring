package emilka.myring

import android.content.Context

/** Pełny reset aplikacji: ustawienia, przypomnienia, alarmy (zapas opcjonalnie). */
object AppReset {
    fun resetToSetup(context: Context, clearStock: Boolean = false) {
        SettingsStore.clearSetup(context)
        ReminderCenter.clearReminderState(context)
        if (clearStock) StockStore.clearAll(context)
        ReminderScheduler.cancelAllAlarms(context)
        WidgetRefreshScheduler.cancelMidnight(context)
        MyringWidgetProvider.refreshAll(context)
    }
}