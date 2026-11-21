package emilka.myring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsSheet(
    context: android.content.Context,
    onSaved: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit
) {
    var startDate by remember {
        mutableStateOf(SettingsStore.loadCurrentInsert(context)
            ?: SettingsStore.loadStartDate(context)
            ?: LocalDate.now())
    }
    var weeks by remember { mutableStateOf(SettingsStore.loadWeeks(context)) }
    var reminderTime by remember {
        mutableStateOf(SettingsStore.loadReminderTime(context) ?: LocalTime.of(18, 0))
    }
    var showResetConfirm by remember { mutableStateOf(false) }
  val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

    Surface(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Ustawienia cyklu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            Text("Data ostatniego włożenia: ${startDate.format(dateFmt)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    // Użytkownik może skorygować tylko przez reset lub potwierdzenia w aplikacji.
                })

            Spacer(Modifier.height(12.dp))
            Text("Cykl (tygodnie): $weeks", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = weeks.toFloat(),
                onValueChange = { weeks = it.toInt().coerceIn(1, 6) },
                valueRange = 1f..6f,
                steps = 4,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            Text("Godzina przypomnień: ${reminderTime.format(timeFmt)}",
                style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                    SettingsStore.saveSetup(context, anchor, weeks, reminderTime)
                    ReminderCenter.createChannel(context)
                    AlarmScheduler.scheduleDaily(context, reminderTime)
                    ReminderScheduler.schedule(context, anchor, weeks, reminderTime)
                    WidgetRefreshScheduler.scheduleNextMidnight(context)
                    MyringWidgetProvider.refreshAll(context)
                    onSaved()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zapisz i przeplanuj alarmy", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { showResetConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Wyzeruj aplikację (kreator)", color = MaterialTheme.colorScheme.error)
            }

            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("← Wróć")
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Wyzerować aplikację?") },
            text = { Text("Usunie zapisany cykl i alarmy. Zapas krążków zostanie (możesz go usunąć ręcznie).") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    AppReset.resetToSetup(context, clearStock = false)
                    onReset()
                }) { Text("Wyzeruj") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Anuluj") }
            }
        )
    }
}