package emilka.myring

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Krótka usługa pierwszoplanowa — gwarantuje fullScreenIntent na Androidzie 12+.
 * Po otwarciu [ReminderActivity] sama się wyłącza (stopForeground REMOVE),
 * żeby nie zostawało wiszące powiadomienie FGS.
 */
class ReminderService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { wyciszIZamknij() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val insert = intent?.getBooleanExtra("insert", false) ?: false
        Notifications.createChannels(applicationContext)

        val title = if (insert) "Dziś włóż krążek!" else "Dziś wyciągnij krążek!"
        val text = if (insert) "Narysuj ✔, aby potwierdzić włożenie." else "Narysuj X, aby potwierdzić wyjęcie."

        val fullScreen = PendingIntent.getActivity(
            this, if (insert) 31 else 30,
            Intent(this, ReminderActivity::class.java)
                .putExtra("insert", insert)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, Notifications.CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_ALARM_FS,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else 0
            )
        } catch (_: Exception) {
            ReminderCenter.showFallbackNotification(this, insert)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            startActivity(
                Intent(this, ReminderActivity::class.java)
                    .putExtra("insert", insert)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (_: Exception) {
            // Full-screen intent z powiadomienia powinien wystarczyć.
        }

        // Bezpiecznik: jeśli Activity nie zamknie usługi, nie wisimy wiecznie.
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, 3 * 60 * 1000L)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        // Usuń powiadomienie FGS — to klucz przeciw „wiszącemu” sticky.
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) { }
        Notifications.cancelAlarmNotifications(this)
        super.onDestroy()
    }

    private fun wyciszIZamknij() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) { }
        Notifications.cancelAlarmNotifications(this)
        stopSelf()
    }
}
