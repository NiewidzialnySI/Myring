package emilka.myring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun StockQuickEditDialog(
    currentRemaining: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember(currentRemaining) { mutableStateOf(currentRemaining.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

  AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ile krążków zostało?") },
        text = {
            Column {
                Text(
                    "Po reinstalacji możesz tu wpisać faktyczny zapas — bez skanowania opakowania.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Pozostałe krążki") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = text.toIntOrNull()
                if (n == null) {
                    error = "Podaj liczbę całkowitą"
                    return@TextButton
                }
                if (n > 999) {
                    error = "Maksymalnie 999"
                    return@TextButton
                }
                onConfirm(n)
            }) { Text("Zapisz") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}