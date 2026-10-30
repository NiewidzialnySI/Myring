package emilka.myring

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Widget na ekran główny: okrąg jak w aplikacji + liczba dni do wyjęcia krążka. */
class MyringWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id ->
            val views = buildViews(context)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    /** Wymusza odświeżenie wszystkich instancji widgetu (po zmianie cyklu / zapasów). */
    fun refreshAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
            android.content.ComponentName(context, MyringWidgetProvider::class.java)
        )
        ids.forEach { id ->
            val views = buildViews(context)
            mgr.updateAppWidget(id, views)
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.myring_widget)

        // Kliknięcie w widget otwiera aplikację.
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)

        val start = SettingsStore.loadStartDate(context)
        val weeks = SettingsStore.loadWeeks(context)
        val today = LocalDate.now()

        if (start != null) {
            // Realny kotwiący punkt cyklu: ostatnie potwierdzone włożenie, w inaczej start.
            val currentInsert = SettingsStore.loadCurrentInsert(context) ?: start
            val nextInsert = SettingsStore.loadNextInsert(context)

            val (daysLeft, dateValue, label) = if (nextInsert != null) {
                // Jeżeli potwierdzono wyjęcie — przerwa do nextInsert.
                val dl = ChronoUnit.DAYS.between(today, nextInsert).coerceAtLeast(0)
                Triple(dl, nextInsert, "do włożenia")
            } else {
                val remove = currentInsert.plusDays(weeks * 7L)
                val dl = ChronoUnit.DAYS.between(today, remove).coerceAtLeast(0)
                Triple(dl, remove, "do wyjęcia")
            }

            views.setTextViewText(R.id.widget_days, daysLeft.toString())
            views.setTextViewText(R.id.widget_days_label, label)
            views.setTextViewText(R.id.widget_date, dateValue.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
        } else {
            views.setTextViewText(R.id.widget_days, "—")
            views.setTextViewText(R.id.widget_days_label, "skonfiguruj")
            views.setTextViewText(R.id.widget_date, "")
        }
        return views
    }
}