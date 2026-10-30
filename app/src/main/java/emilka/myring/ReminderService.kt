package emilka.myring

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Usługa pierwszoplanowa gwarantująca poprawną emisję pełnoekranowego intencja
 * (fullScreenIntent) — na Androidzie 12+ tylko foreground service niezawodnie
 * wyświetla pełny ekran nad zablokowanym ekranem (jak budzik).
 */
class ReminderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val context = applicationContext
        val insert = intent?.getBooleanExtra("insert", false) ?: false
        ReminderCenter.createChannel(context)

        val title = if (insert) "Dziś włóż krążek!" else "Dziś wyciągnij krążek!"
        val text = if (insert) "Narysuj ✔, aby potwierdzić włożenie." else "Narysuj X, aby potwierdzić wyjęcie."

        // Intencja otwierająca pełny ekran potwierdzenia.
        val fullScreen = PendingIntent.getActivity(
            context, 0,
            Intent(context, ReminderActivity::class.java).putExtra("insert", insert),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(context, ReminderCenter.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true)
            .build()

        startForeground(ReminderCenter.FS_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else 0)

        // Otwórz pełny ekran potwierdzenia.
        context.startActivity(
            Intent(context, ReminderActivity::class.java)
                .putExtra("insert", insert)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return START_NOT_STICKY
    }
}