package emilka.myring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import emilka.myring.ui.theme.ThemePreference
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsSheet(
    context: android.content.Context,
    onSaved: () -> Unit,
    onThemeChanged: (ThemePreference) -> Unit = {}
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

    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Ustawienia",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            Text("Wygląd", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
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
            Text(
                "Ciemny motyw nie razi w nocy. „System” podąża za telefonem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Spacer(Modifier.height(16.dp))

            Text(
                "Data ostatniego włożenia: ${startDate.format(dateFmt)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            Text(
                "Godzina przypomnień: ${reminderTime.format(timeFmt)}",
                style = MaterialTheme.typography.titleMedium
            )
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

            Spacer(Modifier.height(24.dp))
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
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Zapisz i przeplanuj alarmy", fontWeight = FontWeight.Bold)
            }
            Text(
                "Wróć gestem w prawo do okręgu",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
