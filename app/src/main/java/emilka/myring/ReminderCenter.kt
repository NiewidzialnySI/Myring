package emilka.myring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Przypomnienia cyklu:
 *  – dzień przed wyjęciem: normalne powiadomienie „Jutro wyciągnij krążek”,
 *  – w dniu wyjęcia: pełnoekranowy alarm (dźwięk + wibracja) z ekranem potwierdzenia,
 *  – „wyjątkowa sytuacja”: wyciszenie alarmów do jutra.
 */
object ReminderCenter {
    // Zachowane stałe dla kompatybilności wywołań.
    const val CHANNEL_ID = Notifications.CHANNEL_ALARM
    const val FS_ID = Notifications.ID_ALARM_FS

    private const val PREFS = "myring_reminders"

    // Bazy requestCode — cykl i = 0/1 dostaje base + i*10 (bez kolizji).
    const val PENDING_DAY_BEFORE_REMOVE = 200
    const val PENDING_REMOVE = 210
    const val PENDING_DAY_BEFORE_INSERT = 220
    const val PENDING_INSERT = 230

    fun createChannel(context: Context) = Notifications.createChannels(context)

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
        SettingsStore.recordRemoved(context)
        MyringWidgetProvider.refreshAll(context)
        ReminderScheduler.rescheduleFromSettings(context)
    }

    fun isRemovedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("ring_actioned", null) == LocalDate.now().toString()
    }

    fun markInserted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("ring_inserted", LocalDate.now().toString()).apply()
        SettingsStore.recordInserted(context)
        MyringWidgetProvider.refreshAll(context)
        ReminderScheduler.rescheduleFromSettings(context)
    }

    fun isInsertedToday(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("ring_inserted", null) == LocalDate.now().toString()

    /**
     * Pełnoekranowy alarm. Najpierw startujemy krótką usługę FGS (wymóg Android 12+
     * dla niezawodnego fullScreenIntent), a ona sama się wyłączy po otwarciu Activity
     * albo po anulowaniu — bez wiszącego powiadomienia.
     */
    fun showFullScreenReminder(context: Context, insert: Boolean = false) {
        Notifications.createChannels(context)
        val intent = Intent(context, ReminderService::class.java).putExtra("insert", insert)
        try {
            context.startForegroundService(intent)
        } catch (_: Exception) {
            // Fallback gdy FGS zablokowane (OEM) — sam ekran + zwykłe powiadomienie.
            showFallbackNotification(context, insert)
            context.startActivity(
                Intent(context, ReminderActivity::class.java)
                    .putExtra("insert", insert)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    fun showFallbackNotification(context: Context, insert: Boolean) {
        Notifications.createChannels(context)
        val title = if (insert) "Dziś włóż krążek!" else "Dziś wyciągnij krążek!"
        val text = if (insert) "Dotknij, aby potwierdzić włożenie." else "Dotknij, aby potwierdzić wyjęcie."
        val fullScreen = PendingIntent.getActivity(
            context, if (insert) 21 else 20,
            Intent(context, ReminderActivity::class.java).putExtra("insert", insert),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = androidx.core.app.NotificationCompat.Builder(context, Notifications.CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        try {
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(Notifications.ID_ALARM_FALLBACK, notification)
        } catch (_: SecurityException) { }
    }

    /** Zatrzymuje usługę, wycisza wibrację i chowa tylko powiadomienia alarmu (nie „dzień przed”). */
    fun stopService(context: Context) {
        try {
            context.stopService(Intent(context, ReminderService::class.java))
        } catch (_: Exception) { }
        Notifications.cancelAlarmNotifications(context)
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.cancel()
    }

    fun notifyDayBefore(context: Context, insert: Boolean = false) {
        Notifications.notifyDayBefore(context, insert)
    }
}

/** Odbiera alarm dnia wydarzenia i otwiera pełnoekranowy ekran potwierdzenia. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        try {
            Notifications.createChannels(context)
            val today = LocalDate.now()
            val insert = intent.getBooleanExtra("insert", false)
            val done = if (insert) ReminderCenter.isInsertedToday(context)
            else ReminderCenter.isRemovedToday(context)
            if (ReminderCenter.isSnoozed(context, today) || done) return
            ReminderCenter.showFullScreenReminder(context, insert)
            // Po wyzwoleniu — przeplanuj kolejne cykle (i+1).
            ReminderScheduler.rescheduleFromSettings(context)
            MyringWidgetProvider.refreshAll(context)
        } finally {
            pending.finish()
        }
    }
}

/** Planuje przypomnienia cyklu: wyjmowanie i wkładanie (bieżący + następny cykl). */
object ReminderScheduler {

    fun rescheduleFromSettings(context: Context) {
        val start = SettingsStore.loadCurrentInsert(context)
            ?: SettingsStore.loadStartDate(context)
            ?: return
        val weeks = SettingsStore.loadWeeks(context)
        val time = SettingsStore.loadReminderTime(context)
            ?: AlarmScheduler.loadSavedTime(context)
            ?: LocalTime.of(18, 0)
        schedule(context, start, weeks, time)
    }

    fun schedule(context: Context, startDate: LocalDate, weeks: Int, reminderTime: LocalTime) {
        Notifications.createChannels(context)
        val stepDays = weeks * 7L + 7L
        val today = LocalDate.now()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Anuluj poprzednie, żeby nie dublować po re-schedule.
        cancelAll(context, alarmManager)

        // Gdy jesteśmy w przerwie (potwierdzone wyjęcie) — kotwica włożenia = nextInsert − 7 − weeks*7?
        // Prościej: używamy currentInsert jako startDate (przekazane z zewnątrz),
        // a nextInsert z SettingsStore nadpisuje datę włożenia.
        val nextInsertOverride = SettingsStore.loadNextInsert(context)

        for (i in listOf(0L, 1L)) {
            val insertDate = startDate.plusDays(i * stepDays)
            val removeDate = insertDate.plusDays(weeks * 7L)
            val breakEnd = if (i == 0L && nextInsertOverride != null) nextInsertOverride
            else removeDate.plusDays(7L)

            val cycle = i.toInt()

            // Wyjmowanie — tylko gdy nie jesteśmy już w przerwie (nextInsert ustawione).
            if (i == 0L && nextInsertOverride != null) {
                // Już wyjęte — planuj tylko włożenie.
            } else if (!removeDate.isBefore(today)) {
                setAlarm(
                    context, alarmManager,
                    Intent(context, ReminderReceiver::class.java).putExtra("insert", false),
                    ReminderCenter.PENDING_REMOVE + cycle,
                    removeDate.atTime(reminderTime),
                    useAlarmClock = true
                )
                val db = removeDate.minusDays(1)
                if (!db.isBefore(today)) {
                    setAlarm(
                        context, alarmManager,
                        Intent(context, DayBeforeReceiver::class.java).putExtra("insert", false),
                        ReminderCenter.PENDING_DAY_BEFORE_REMOVE + cycle,
                        db.atTime(reminderTime),
                        useAlarmClock = false
                    )
                }
            }

            // Wkładanie
            if (!breakEnd.isBefore(today)) {
                setAlarm(
                    context, alarmManager,
                    Intent(context, ReminderReceiver::class.java).putExtra("insert", true),
                    ReminderCenter.PENDING_INSERT + cycle,
                    breakEnd.atTime(reminderTime),
                    useAlarmClock = true
                )
                val db = breakEnd.minusDays(1)
                if (!db.isBefore(today)) {
                    setAlarm(
                        context, alarmManager,
                        Intent(context, DayBeforeReceiver::class.java).putExtra("insert", true),
                        ReminderCenter.PENDING_DAY_BEFORE_INSERT + cycle,
                        db.atTime(reminderTime),
                        useAlarmClock = false
                    )
                }
            }
        }
    }

    private fun cancelAll(context: Context, alarmManager: AlarmManager) {
        val bases = listOf(
            ReminderCenter.PENDING_DAY_BEFORE_REMOVE,
            ReminderCenter.PENDING_REMOVE,
            ReminderCenter.PENDING_DAY_BEFORE_INSERT,
            ReminderCenter.PENDING_INSERT,
            // Stare kody z v1 (kolidujące 100–103) — wyczyść przy migracji.
            100, 101, 102, 103
        )
        for (base in bases) {
            for (cycle in 0..1) {
                val code = if (base >= 200) base + cycle else base + cycle
                cancelPending(context, alarmManager, code, ReminderReceiver::class.java)
                cancelPending(context, alarmManager, code, DayBeforeReceiver::class.java)
            }
        }
    }

    private fun cancelPending(
        context: Context,
        alarmManager: AlarmManager,
        code: Int,
        cls: Class<out BroadcastReceiver>
    ) {
        val pi = PendingIntent.getBroadcast(
            context, code, Intent(context, cls),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) alarmManager.cancel(pi)
    }

    /**
     * Exact alarm odporny na Doze.
     * [useAlarmClock] = true dla dnia wyjęcia/włożenia (najwyższa gwarancja systemu).
     */
    private fun setAlarm(
        context: Context,
        alarmManager: AlarmManager,
        intent: Intent,
        code: Int,
        at: LocalDateTime,
        useAlarmClock: Boolean
    ) {
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (triggerAt <= System.currentTimeMillis()) return

        val pending = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        when {
            useAlarmClock && canExact -> {
                // setAlarmClock — wyjątek z Doze, pokazuje ikonę budzika.
                val show = PendingIntent.getActivity(
                    context, code + 500,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pending)
            }
            canExact -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            else -> {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }
    }
}

/** Odbiera „dzień przed” i wysyła lekkie powiadomienie. */
class DayBeforeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderCenter.notifyDayBefore(context, intent.getBooleanExtra("insert", false))
    }
}
