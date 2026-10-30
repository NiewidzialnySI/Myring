package emilka.myring

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Przypomnienia cyklu:
 *  – dzień przed wyjęciem: normalne powiadomienie "Jutro wyciągnij krążek",
 *  – w dniu wyjęcia: pełnoekranowy alarm (dźwięk + wibracja) z ekranem potwierdzenia X,
 *  – "wyjątkowa sytuacja": wyciszenie alarmów do jutra.
 */
object ReminderCenter {
    const val CHANNEL_ID = "myring_reminders"
    const val CHANNEL_NAME = "Przypomnienia"
    const val FS_ID = 902
    private const val PREFS = "myring_reminders"

    // Re-identyfikatory alarmów
    const val PENDING_DAY_BEFORE_REMOVE = 100
    const val PENDING_REMOVE = 101
    const val PENDING_DAY_BEFORE_INSERT = 102
    const val PENDING_INSERT = 103

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val importance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationManager.IMPORTANCE_MAX
        } else NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = "Przypomnienia o wyjęciu krążka"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500, 300, 500)
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun isSnoozed(context: Context, today: LocalDate): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val snoozeDate = prefs.getString("snooze_until", null) ?: return false
        return runCatching { LocalDate.parse(snoozeDate) }.getOrNull()?.isAfter(today) == true
    }

    fun snoozeUntilTomorrow(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("snooze_until", LocalDate.now().plusDays(1).toString())
            .apply()
    }

    fun markRemoved(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("ring_actioned", LocalDate.now().toString())
            .apply()
        SettingsStore.recordRemoved(context) // przesuń: następne włożenie = dziś + 7
        MyringWidgetProvider().refreshAll(context) // odśwież widget z dniami
    }

    /** Czy dziś krążek został już potwierdzony jako wyjęty. */
    fun isRemovedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("ring_actioned", null) == LocalDate.now().toString()
    }

    fun markInserted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("ring_inserted", LocalDate.now().toString()).apply()
        SettingsStore.recordInserted(context) // nowy cykl od dziś
        MyringWidgetProvider().refreshAll(context) // odśwież widget z dniami
    }

    fun isInsertedToday(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("ring_inserted", null) == LocalDate.now().toString()

    /** Pełnoekranowy alarm przez foreground service (gwarancja na Android 12+). */
    fun showFullScreenReminder(context: Context, insert: Boolean = false) {
        val intent = Intent(context, ReminderService::class.java).putExtra("insert", insert)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /** Zatrzymuje usługę, wycisza wibrację i chowa powiadomienia. */
    fun stopService(context: Context) {
        context.stopService(Intent(context, ReminderService::class.java))
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancelAll()
        (context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.cancel()
    }

    fun notifyDayBefore(context: Context, insert: Boolean = false) {
        createChannel(context)
        val title = if (insert) "Jutro włóż krążek" else "Jutro wyciągnij krążek"
        val text = if (insert) "Jutro czas włożyć nowy krążek." else "Jutro czas wyciągnąć krążek."
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(0, 120)) // krótka, lekka wibracja
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(context).notify(901, notification) } catch (_: SecurityException) {}
    }
}

/** Odbiera wywołanie alarmu dnia wydarzenia i otwiera pełnoekranowy ekran potwierdzenia. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val today = LocalDate.now()
        val insert = intent.getBooleanExtra("insert", false)
        val done = if (insert) ReminderCenter.isInsertedToday(context) else ReminderCenter.isRemovedToday(context)
        // Wyciszono (wyjątkowa sytuacja) lub już potwierdzono — nie przeszkadzaj.
        if (ReminderCenter.isSnoozed(context, today) || done) return
        ReminderCenter.showFullScreenReminder(context, insert)
    }
}

/** Planuje przypomnienia cyklu: wyjmowanie i wkładanie (bieżący + następny cykl). */
object ReminderScheduler {

    fun schedule(context: Context, startDate: LocalDate, weeks: Int, reminderTime: LocalTime) {
        val stepDays = weeks * 7L + 7L
        val today = LocalDate.now()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (i in listOf(0L, 1L)) {
            val insertDate = startDate.plusDays(i * stepDays)
            val removeDate = insertDate.plusDays(weeks * 7L)
            val breakEnd = removeDate.plusDays(7L)

            // Wyjmowanie: dzień (full-screen X) + dzień przed (lekkie).
            if (!removeDate.isBefore(today)) {
                setOne(context, alarmManager,
                    Intent(context, ReminderReceiver::class.java).putExtra("insert", false),
                    ReminderCenter.PENDING_REMOVE + i.toInt(), removeDate.atTime(reminderTime))
                val db = removeDate.minusDays(1)
                if (db.isAfter(today)) {
                    setOne(context, alarmManager,
                        Intent(context, DayBeforeReceiver::class.java).putExtra("insert", false),
                        ReminderCenter.PENDING_DAY_BEFORE_REMOVE + i.toInt(), db.atTime(reminderTime))
                }
            }

            // Wkładanie: dzień (full-screen ✔) + dzień przed (lekkie).
            if (!breakEnd.isBefore(today)) {
                setOne(context, alarmManager,
                    Intent(context, ReminderReceiver::class.java).putExtra("insert", true),
                    ReminderCenter.PENDING_INSERT + i.toInt(), breakEnd.atTime(reminderTime))
                val db = breakEnd.minusDays(1)
                if (db.isAfter(today)) {
                    setOne(context, alarmManager,
                        Intent(context, DayBeforeReceiver::class.java).putExtra("insert", true),
                        ReminderCenter.PENDING_DAY_BEFORE_INSERT + i.toInt(), db.atTime(reminderTime))
                }
            }
        }
    }

    private fun setOne(context: Context, alarmManager: AlarmManager, intent: Intent, code: Int, at: LocalDateTime) {
        val pending = PendingIntent.getBroadcast(context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
        if (canSchedule) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }
}

/** Odbiera "dzień przed" i wysyła lekkie powiadomienie. */
class DayBeforeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderCenter.notifyDayBefore(context, intent.getBooleanExtra("insert", false))
    }
}