package emilka.myring

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import emilka.myring.ui.theme.ThemePreference
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Pełnoekranowe ustawienia (strona pagera) — bez karty/okna. */
@Composable
fun SettingsSheet(
    context: android.content.Context,
    onChanged: () -> Unit = {},
    onThemeChanged: (ThemePreference) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var startDate by remember {
        mutableStateOf(
            SettingsStore.loadCurrentInsert(context)
                ?: SettingsStore.loadStartDate(context)
                ?: LocalDate.now()
        )
    }
    var reminderTime by remember {
        mutableStateOf(SettingsStore.loadReminderTime(context) ?: LocalTime.of(18, 0))
    }
    var themePref by remember { mutableStateOf(SettingsStore.loadThemePreference(context)) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    fun applyReminderTime(time: LocalTime) {
        reminderTime = time
        SettingsStore.saveReminderTime(context, time)
        ReminderCenter.createChannel(context)
        // Jedno źródło prawdy → reschedule (bez nadpisywania start_date).
        AlarmScheduler.scheduleDaily(context, time)
        WidgetRefreshScheduler.scheduleNextMidnight(context)
        MyringWidgetProvider.refreshAll(context)
        onChanged()
    }

    fun openConfirmDemo(insert: Boolean) {
        context.startActivity(
            Intent(context, ReminderActivity::class.java)
                .putExtra("insert", insert)
                .putExtra(ReminderActivity.EXTRA_DEMO, true)
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (kotlin.math.abs(dragAmount) < 50f) return@detectHorizontalDragGestures
                    if (dragAmount > 0) onBack()
                }
            }
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Ustawienia",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(28.dp))

        Text("Wygląd", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                ThemePreference.SYSTEM to "System",
                ThemePreference.LIGHT to "Jasny",
                ThemePreference.DARK to "Ciemny"
            ).forEach { (pref, label) ->
                FilterChip(
                    selected = themePref == pref,
                    onClick = {
                        themePref = pref
                        SettingsStore.saveThemePreference(context, pref)
                        onThemeChanged(pref)
                    },
                    label = { Text(label) }
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Spacer(Modifier.height(24.dp))

        Text(
            "Data ostatniego włożenia: ${startDate.format(dateFmt)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(24.dp))
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
        Text(
            "Godzina przypomnień: ${reminderTime.format(timeFmt)}",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(8 to 0, 12 to 0, 18 to 0, 20 to 0).forEach { (h, m) ->
                val t = LocalTime.of(h, m)
                FilterChip(
                    selected = reminderTime == t,
                    onClick = { applyReminderTime(t) },
                    label = { Text(t.format(timeFmt)) }
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        Spacer(Modifier.height(24.dp))

        Text("Uprawnienia alarmu", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        if (!Notifications.canUseFullScreenIntent(context)) {
            Text(
                "Android 14+: włącz „Alarmy pełnoekranowe”, żeby przypomnienie otworzyło się przy zablokowanym ekranie.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Notifications.intentFullScreenIntentSettings(context))
                    } catch (_: Exception) { }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Otwórz ustawienia pełnego ekranu") }
            Spacer(Modifier.height(16.dp))
        }
        if (!Notifications.canScheduleExactAlarms(context)) {
            Text(
                "Brak dokładnych alarmów — przypomnienia mogą się spóźnić.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Notifications.intentExactAlarmSettings(context))
                    } catch (_: Exception) { }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Otwórz ustawienia dokładnych alarmów") }
            Spacer(Modifier.height(16.dp))
        }

        Text("Ekrany potwierdzenia", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "Podgląd bez zmiany cyklu i bez wibracji alarmu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { openConfirmDemo(insert = true) },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Pokaż ekran: włóż krążek")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { openConfirmDemo(insert = false) },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Pokaż ekran: wyjmij krążek")
        }
    }
}
