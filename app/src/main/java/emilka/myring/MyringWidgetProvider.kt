package emilka.myring

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
/** Widget na ekran główny: okrąg + liczba dni do wyjęcia / włożenia krążka. */
class MyringWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
        // Przy każdym updatePeriodMillis też doglądamy północnego alarmu.
        WidgetRefreshScheduler.scheduleNextMidnight(context)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshScheduler.scheduleNextMidnight(context)
        refreshAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                refreshAll(context)
                WidgetRefreshScheduler.scheduleNextMidnight(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "emilka.myring.action.WIDGET_REFRESH"

        /** Wymusza odświeżenie wszystkich instancji widgetu. */
        @JvmStatic
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, MyringWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            ids.forEach { id -> mgr.updateAppWidget(id, views) }
        }

        fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.myring_widget)

            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openApp)

            val start = SettingsStore.loadStartDate(context)
            val weeks = SettingsStore.loadWeeks(context)
            val today = LocalDate.now()

            if (start == null) {
                views.setTextViewText(R.id.widget_days, "—")
                views.setTextViewText(R.id.widget_days_label, "skonfiguruj")
                views.setTextViewText(R.id.widget_date, "Otwórz aplikację")
                return views
            }

            val currentInsert = SettingsStore.loadCurrentInsert(context) ?: start
            val nextInsert = SettingsStore.loadNextInsert(context)

            val state = WidgetLogic.compute(today, currentInsert, weeks, nextInsert)
            views.setTextViewText(R.id.widget_days, state.daysText)
            views.setTextViewText(R.id.widget_days_label, state.label)
            views.setTextViewText(R.id.widget_date, state.dateText)
            return views
        }
    }
}

/** Alarm o północy (+1 min) — odświeża liczbę dni na widgetcie bez otwierania apki. */
object WidgetRefreshScheduler {
    private const val REQUEST_CODE = 7701

    fun scheduleNextMidnight(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WidgetRefreshReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Jutro 00:01 — po zmianie daty kalendarzowej.
        val next = ZonedDateTime.now(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
            .atTime(LocalTime.of(0, 1))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        MyringWidgetProvider.refreshAll(context)
        WidgetRefreshScheduler.scheduleNextMidnight(context)
    }
}
