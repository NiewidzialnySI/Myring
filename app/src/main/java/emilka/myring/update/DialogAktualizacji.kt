package emilka.myring.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Dialog autoaktualizacji — pokazuje się przy starcie, gdy jest nowa wersja.
 * Przyciski: Zaktualizuj / Później.
 */
@Composable
fun DialogAktualizacjiHost() {
    val context = LocalContext.current
    val stan by AktualizacjaAplikacji.stan.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AktualizacjaAplikacji.sprawdzPrzyStarcie(context)
    }

    when (val s = stan) {
        is AktualizacjaAplikacji.Stan.Dostepna -> {
            AlertDialog(
                onDismissRequest = {
                    AktualizacjaAplikacji.pominTeWersje(context, s.info.versionCode)
                },
                title = { Text("Dostępna aktualizacja", fontWeight = FontWeight.Bold) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("Nowa wersja: ${s.info.versionName}")
                        Spacer(Modifier.height(8.dp))
                        if (s.info.changelog.isNotBlank()) {
                            Text(s.info.changelog)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Obecna: ${AktualizacjaAplikacji.lokalnaWersja()}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            AktualizacjaAplikacji.zaktualizuj(context, s.info)
                        }
                    }) { Text("Zaktualizuj") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        AktualizacjaAplikacji.pominTeWersje(context, s.info.versionCode)
                    }) { Text("Później") }
                }
            )
        }
        is AktualizacjaAplikacji.Stan.Pobieranie -> {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Pobieranie aktualizacji…") },
                text = {
                    Column {
                        Text("Wersja ${s.info.versionName}")
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { s.postep01 },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("${(s.postep01 * 100).toInt()}%")
                    }
                },
                confirmButton = {}
            )
        }
        is AktualizacjaAplikacji.Stan.GotowaDoInstalacji -> {
            // Automatycznie otwieramy instalator w zaktualizuj(); tu backup dialog.
            AlertDialog(
                onDismissRequest = { AktualizacjaAplikacji.wycisz() },
                title = { Text("Gotowe do instalacji") },
                text = { Text("Wersja ${s.info.versionName} pobrana. Zainstaluj teraz.") },
                confirmButton = {
                    TextButton(onClick = {
                        AktualizacjaAplikacji.zainstaluj(context, s.plik)
                    }) { Text("Zainstaluj") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        AktualizacjaAplikacji.pominTeWersje(context, s.info.versionCode)
                    }) { Text("Później") }
                }
            )
        }
        is AktualizacjaAplikacji.Stan.Blad -> {
            AlertDialog(
                onDismissRequest = { AktualizacjaAplikacji.wycisz() },
                title = { Text("Aktualizacja nieudana") },
                text = { Text(s.komunikat) },
                confirmButton = {
                    TextButton(onClick = { AktualizacjaAplikacji.wycisz() }) { Text("OK") }
                }
            )
        }
        else -> Unit
    }
}
