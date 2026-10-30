package emilka.myring

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Kanały powiadomień:
 *  – [CHANNEL_ALARM] — dzień wyjęcia/włożenia (wysoki priorytet, full-screen),
 *  – [CHANNEL_DAY_BEFORE] — „jutro” (zwykłe, auto-cancel).
 *
 * Osobne ID kanałów, żeby importance dało się ustawić poprawnie
 * (system nie podnosi importance istniejącego kanału).
 */
object Notifications {
    const val CHANNEL_ALARM = "myring_alarm_v2"
    const val CHANNEL_DAY_BEFORE = "myring_day_before_v2"
    const val CHANNEL_LEGACY = "myring_reminders"

    const val ID_DAY_BEFORE = 901
    const val ID_ALARM_FS = 902
    const val ID_ALARM_FALLBACK = 903

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Stary kanał mógł mieć złą importance — zostawiamy, ale nie używamy.
        // Nowe kanały mają świeże ID.

        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            "Alarm wyjęcia / włożenia",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Pełnoekranowe przypomnienie w dniu wyjęcia lub włożenia krążka"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 600)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, null)
        }
        manager.createNotificationChannel(alarm)

        val dayBefore = NotificationChannel(
            CHANNEL_DAY_BEFORE,
            "Dzień przed",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Lekkie przypomnienie dzień przed wyjęciem lub włożeniem"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 120)
        }
        manager.createNotificationChannel(dayBefore)
    }

    /** Czy apka może pokazywać powiadomienia (Android 13+). */
    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Czy system pozwala na exact alarms (Android 12+). */
    fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true
    }

    fun intentExactAlarmSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

    fun intentAppNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }

    fun intentBatteryOptimization(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
        }

    fun intentBatterySettings(context: Context): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun cancelAlarmNotifications(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(ID_ALARM_FS)
        nm.cancel(ID_ALARM_FALLBACK)
    }

    fun cancelDayBefore(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_DAY_BEFORE)
    }

    fun notifyDayBefore(context: Context, insert: Boolean) {
        createChannels(context)
        val title = if (insert) "Jutro włóż krążek" else "Jutro wyciągnij krążek"
        val text = if (insert) "Jutro czas włożyć nowy krążek." else "Jutro czas wyciągnąć krążek."
        val open = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DAY_BEFORE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setVibrate(longArrayOf(0, 120))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_DAY_BEFORE, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS odmówione — bez crasha
        }
    }
}

/**
 * Dawny codzienny „Czas na przypomnienie!” — już nie wysyłamy zbędnego powiadomienia.
 * Zamiast tego odświeżamy widget i upewniamy się, że cyklowe alarmy są zaplanowane.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.createChannels(context)
        MyringWidgetProvider.refreshAll(context)
        WidgetRefreshScheduler.scheduleNextMidnight(context)
        // Upewnij się, że przypomnienia cyklu nie zniknęły (OEM / Doze).
        ReminderScheduler.rescheduleFromSettings(context)
    }
}

/**
 * Zachowane API pod BootReceiver / MainActivity — zapis godziny i przeplanowanie
 * północnego odświeżenia + cykli. Nie planuje już codziennego „pustego” powiadomienia.
 */
object AlarmScheduler {
    private const val PREFS = "myring_prefs"
    private const val KEY_MIN = "reminder_min"
    private const val KEY_HOUR = "reminder_hour"

    fun scheduleDaily(context: Context, time: LocalTime) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HOUR, time.hour)
            .putInt(KEY_MIN, time.minute)
            .apply()
        // Godzina trafia do ReminderScheduler (dzień wyjęcia / dzień przed).
        ReminderScheduler.rescheduleFromSettings(context)
        WidgetRefreshScheduler.scheduleNextMidnight(context)
    }

    fun rescheduleFromPrefs(context: Context) {
        ReminderScheduler.rescheduleFromSettings(context)
        WidgetRefreshScheduler.scheduleNextMidnight(context)
    }

    fun loadSavedTime(context: Context): LocalTime? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hour = prefs.getInt(KEY_HOUR, -1)
        val minute = prefs.getInt(KEY_MIN, -1)
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }
}
