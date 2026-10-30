package emilka.myring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Po restarcie / aktualizacji apki / zmianie daty / strefy — odtwórz alarmy i widget.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                Notifications.createChannels(context)
                AlarmScheduler.rescheduleFromPrefs(context)
                ReminderScheduler.rescheduleFromSettings(context)
                WidgetRefreshScheduler.scheduleNextMidnight(context)
                MyringWidgetProvider.refreshAll(context)
            }
        }
    }
}
