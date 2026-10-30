package emilka.myring

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Trwały zapis ustawień/cyklu — dzięki temu po ponownym uruchomieniu
 * aplikacja od razu pokazuje ekran główny z zapisanymi danymi. */
object SettingsStore {
    private const val PREFS = "myring_settings"
    private const val KEY_START_DATE = "start_date"
    private const val KEY_WEEKS = "weeks"
    private const val KEY_REMINDER = "reminder_time"
    private const val KEY_SETUP_DONE = "setup_done"
    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    fun isSetupDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP_DONE, false)

    fun saveSetup(context: Context, startDate: LocalDate, weeks: Int, reminderTime: LocalTime) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_DONE, true)
            .putString(KEY_START_DATE, startDate.format(ISO))
            .putInt(KEY_WEEKS, weeks)
            .putString(KEY_REMINDER, reminderTime.toString())
            .apply()
    }

    fun loadStartDate(context: Context): LocalDate? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_START_DATE, null) ?: return null
        return runCatching { LocalDate.parse(raw, ISO) }.getOrNull()
    }

    fun loadWeeks(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_WEEKS, 3)

    fun loadReminderTime(context: Context): LocalTime? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REMINDER, null) ?: return null
        return runCatching { LocalTime.parse(raw) }.getOrNull()
    }

    // ---- Realnie potwierdzone daty cyklu (przesuwają następne etapy) ----

    private const val KEY_CURRENT_INSERT = "current_insert"
    private const val KEY_NEXT_INSERT = "next_insert"

    /** Bieżąca data, od której liczymy obecny cykl (data ostatniego potwierdzonego włożenia). */
    fun loadCurrentInsert(context: Context): LocalDate? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CURRENT_INSERT, null) ?: return null
        return runCatching { LocalDate.parse(raw, ISO) }.getOrNull()
    }

    /** Data następnego włożenia (ustawiana po potwierdzeniu wyjęcia: dziś + 7 dni). */
    fun loadNextInsert(context: Context): LocalDate? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NEXT_INSERT, null) ?: return null
        return runCatching { LocalDate.parse(raw, ISO) }.getOrNull()
    }

    /** Rejestruje potwierdzone wyjęcie: przerwa liczy się od dziś, następne włożenie = dziś + 7. */
    fun recordRemoved(context: Context) {
        val insert = loadCurrentInsert(context) ?: loadStartDate(context) ?: LocalDate.now()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_INSERT, insert.format(ISO))          // zostawiamy obecny (historia)
            .putString(KEY_NEXT_INSERT, LocalDate.now().plusDays(7).format(ISO))
            .apply()
    }

    /** Rejestruje potwierdzone włożenie: od dziś liczy się nowy cykl, następne wyjęcie = dziś + tygodnie. */
    fun recordInserted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_INSERT, LocalDate.now().format(ISO))
            .remove(KEY_NEXT_INSERT)
            .apply()
    }

    fun clearSetup(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}