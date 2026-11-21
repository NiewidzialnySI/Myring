package emilka.myring

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import emilka.myring.widget.MyringGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Receiver widgetu — klasa zostaje (żeby nie zgubić już dodanych widgetów),
 * UI renderuje Glance ([MyringGlanceWidget]).
 */
class MyringWidgetProvider : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = MyringGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.scheduleNextMidnight(context)
        refreshAll(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefreshScheduler.scheduleNextMidnight(context)
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

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Wymusza odświeżenie wszystkich instancji widgetu (Glance). */
        @JvmStatic
        fun refreshAll(context: Context) {
            val appContext = context.applicationContext
            scope.launch {
                runCatching { MyringGlanceWidget().updateAll(appContext) }
            }
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

    fun cancelMidnight(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, WidgetRefreshReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) alarmManager.cancel(pi)
    }
}

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        MyringWidgetProvider.refreshAll(context)
        WidgetRefreshScheduler.scheduleNextMidnight(context)
    }
}
