package emilka.myring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Po restarcie / aktualizacji apki / zmianie daty / strefy — odtwórz alarmy i widget.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            // LOCKED_BOOT: tylko alarmy północy — prefs CE mogą być niedostępne.
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                WidgetRefreshScheduler.scheduleNextMidnight(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        Notifications.createChannels(context)
                        AlarmScheduler.rescheduleFromPrefs(context)
                        ReminderScheduler.rescheduleFromSettings(context)
                        WidgetRefreshScheduler.scheduleNextMidnight(context)
                        MyringWidgetProvider.refreshAllAwait(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
