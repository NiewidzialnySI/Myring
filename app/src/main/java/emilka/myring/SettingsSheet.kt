package emilka.myring

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
    onSaved: () -> Unit,
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
                    onClick = { reminderTime = t },
                    label = { Text(t.format(timeFmt)) }
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                val anchor = startDate
                SettingsStore.saveSetup(context, anchor, reminderTime = reminderTime)
                ReminderCenter.createChannel(context)
                AlarmScheduler.scheduleDaily(context, reminderTime)
                ReminderScheduler.schedule(
                    context, anchor, SettingsStore.CYCLE_WEEKS, reminderTime
                )
                WidgetRefreshScheduler.scheduleNextMidnight(context)
                MyringWidgetProvider.refreshAll(context)
                onSaved()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Zapisz i przeplanuj alarmy", fontWeight = FontWeight.Bold)
        }
        Text(
            "Przesuń w prawo, żeby wrócić do okręgu",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}
