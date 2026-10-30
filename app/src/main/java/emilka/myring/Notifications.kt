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
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.ZoneId

/** Kanał i tworzenie powiadomień. */
object Notifications {
    const val CHANNEL_ID = "myring_reminders"
    const val CHANNEL_NAME = "Przypomnienia"
    const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Codzienne przypomnienia o cyklu"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun notifyReminder(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Myring")
            .setContentText("Czas na przypomnienie! 💍")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Post_NOTIFICATIONS wyłączone — cisza, bez crasha
        }
    }
}

/** Odbierze sygnał alarmu i pokaże powiadomienie. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifications.createChannel(context)
        Notifications.notifyReminder(context)
    }
}

/** Planuje codzienne, dokładne przypomnienie o podanej godzinie (działa też w tle). */
object AlarmScheduler {
    private const val PREFS = "myring_prefs"
    private const val KEY_MIN = "reminder_min"
    private const val KEY_HOUR = "reminder_hour"

    fun scheduleDaily(context: Context, time: LocalTime) {
        // Zapisz godzinę, żeby można było zaplanować na nowo po restarcie telefonu.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_HOUR, time.hour)
            .putInt(KEY_MIN, time.minute)
            .apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = ZonedDateTime.now()
        var next = now
            .withHour(time.hour)
            .withMinute(time.minute)
            .withSecond(0)
            .withNano(0)
        // Jeśli wybrana godzina już minęła dziś, planuj na jutro
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        val triggerAt = next.toInstant().toEpochMilli()

        val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (canSchedule) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pending
            )
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    /** Wywoływane z BootReceiver po restarcie — odtwarza zapisany alarm. */
    fun rescheduleFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hour = prefs.getInt(KEY_HOUR, -1)
        val minute = prefs.getInt(KEY_MIN, -1)
        if (hour in 0..23 && minute in 0..59) {
            scheduleDaily(context, LocalTime.of(hour, minute))
        }
    }
}