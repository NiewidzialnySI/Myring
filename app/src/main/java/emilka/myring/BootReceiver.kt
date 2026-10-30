package emilka.myring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            ReminderCenter.createChannel(context)
            AlarmScheduler.rescheduleFromPrefs(context)
            // Odtwórz przypomnienia o wyjęciu z zapisanych ustawień.
            val start = SettingsStore.loadStartDate(context)
            val weeks = SettingsStore.loadWeeks(context)
            val time = SettingsStore.loadReminderTime(context)
            if (start != null) {
                ReminderScheduler.schedule(context, start, weeks, time ?: java.time.LocalTime.of(18, 0))
            }
        }
    }
}