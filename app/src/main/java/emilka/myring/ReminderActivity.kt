package emilka.myring

import android.content.Context
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import emilka.myring.ui.theme.MojeKrążkiTheme
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot

/** Tryb potwierdzenia: REMOVE = rysujesz X, INSERT = rysujesz helecek (ptaszek). */
enum class ConfirmMode { REMOVE, INSERT }

class ReminderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ekran nad lockscreenem — jak budzik.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val insert = intent.getBooleanExtra("insert", false)
        val mode = if (insert) ConfirmMode.INSERT else ConfirmMode.REMOVE
        enableEdgeToEdge()
        setContent {
            MojeKrążkiTheme {
                ConfirmationScreen(
                    mode = mode,
                    onConfirmed = {
                        ReminderCenter.stopService(this)
                        finish()
                    },
                    onSnooze = {
                        ReminderCenter.snoozeUntilTomorrow(this)
                        ReminderCenter.stopService(this)
                        finish()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        // Back / zabicie Activity nie może zostawić sticky FGS.
        // Przy rotacji (isChangingConfigurations) nie gasimy alarmu.
        if (isFinishing) {
            ReminderCenter.stopService(this)
        }
        super.onDestroy()
    }
}

@Composable
private fun ConfirmationScreen(mode: ConfirmMode, onConfirmed: () -> Unit, onSnooze: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val primary = MaterialTheme.colorScheme.primary

    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

    // Segmenty rysowania: każdy pociągnięty palcem odcinek to osobna linia.
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var complete by remember { mutableStateOf(false) }

    val isRemove = mode == ConfirmMode.REMOVE
    val title = if (isRemove) "Dziś wyciągnij krążek!" else "Dziś włóż krążek!"
    val guideSplit = if (isRemove) {
        "Połącz kropki na krzyż — 2 linie"
    } else "Połącz kropki w ptaszka (✔)"

    // Przełącznik wibracji: startowa, potem zatrzymujemy po potwierdzeniu.
    fun stopAlarm() {
        vibrator?.cancel()
        ReminderCenter.stopService(context)
        onConfirmed()
    }

    fun tryComplete() {
        if (complete) return
        val ok = if (isRemove) isX(strokes) else isCheck(strokes)
        if (ok) {
            complete = true
            vibrator?.cancel()
            if (isRemove) ReminderCenter.markRemoved(context)
            else ReminderCenter.markInserted(context)
            // Po krótkim potwierdzeniu zamknij ekran i wycisz.
            ReminderCenter.stopService(context)
        }
    }

    LaunchedEffect(Unit) {
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 250, 600, 250, 600), 0))
    }
    LaunchedEffect(complete) {
        if (complete) { delay(700); onConfirmed() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))
            Text(if (isRemove) "🔔" else "💊", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(if (isRemove) "Narysuj X, aby potwierdzić." else "Narysuj ptaszek, aby potwierdzić.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(24.dp))

            // Pole do rysowania z punktami pomocniczymi.
            Box(
                Modifier
                    .size(280.dp)
                    .background(
                        if (complete) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(20.dp)
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset -> current = listOf(offset) },
                            onDrag = { change, _ -> current = current + change.position },
                            onDragEnd = {
                                if (current.size >= 2) strokes = strokes + listOf(current)
                                current = emptyList()
                                tryComplete()
                            },
                            onDragCancel = { current = emptyList() }
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // Punkty pomocnicze (ślad, gdzie rysować).
                    val guide = if (isRemove) listOf(
                        Offset(size.width * 0.15f, size.height * 0.15f),
                        Offset(size.width * 0.85f, size.height * 0.85f),
                        Offset(size.width * 0.85f, size.height * 0.15f),
                        Offset(size.width * 0.15f, size.height * 0.85f)
                    ) else listOf(
                        Offset(size.width * 0.18f, size.height * 0.55f),
                        Offset(size.width * 0.45f, size.height * 0.78f),
                        Offset(size.width * 0.82f, size.height * 0.22f)
                    )
                    guide.forEach { p ->
                        drawCircle(Color(0xFFB0B0C0), radius = 7.dp.toPx(), center = p)
                        drawCircle(Color.White, radius = 3.dp.toPx(), center = p)
                    }
                    // Linie narysowane przez użytkownika.
                    (strokes + listOf(current)).filter { it.size >= 2 }.forEach { seg ->
                        val path = Path()
                        seg.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                        drawPath(path, Color(0xFFE47C9C), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(if (complete) "Potwierdzono ✔" else guideSplit,
                style = MaterialTheme.typography.labelSmall,
                color = if (complete) primary else MaterialTheme.colorScheme.onSurfaceVariant)

            // Przycisk "Wyjątkowa sytuacja" zakotwiczony na dole.
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSnooze, modifier = Modifier.padding(bottom = 16.dp)) {
                Text("Wyjątkowa sytuacja — wycisz do jutra")
            }
        }
    }
}

/** X = co najmniej dwie wyraźnie ukośne linie (krzyż). */
private fun isX(strokes: List<List<Offset>>): Boolean {
    if (strokes.size < 2) return false
    val sorted = strokes.sortedByDescending { strokeLen(it) }.take(2)
    return sorted.all { isDiagonal(it) }
}

/** Ptaszek = jedna linia z wyraźnym załamaniem (w dół-lewo, potem w górę-prawo). */
private fun isCheck(strokes: List<List<Offset>>): Boolean {
    val seg = strokes.maxByOrNull { strokeLen(it) } ?: return false
    if (seg.size < 4) return false
    val first = seg.first(); val last = seg.last()
    val up = last.y < first.y - 30f
    val right = last.x > first.x + 30f
    val midDown = seg[seg.size / 2].y > first.y
    return up && right && midDown
}

private fun isDiagonal(seg: List<Offset>): Boolean {
    if (seg.size < 3) return false
    val dx = abs(seg.last().x - seg.first().x)
    val dy = abs(seg.last().y - seg.first().y)
    val len = hypot(dx, dy)
    return len > 120f && dx > len * 0.3f && dy > len * 0.3f
}

private fun strokeLen(seg: List<Offset>): Float =
    if (seg.size < 2) 0f else hypot(seg.last().x - seg.first().x, seg.last().y - seg.first().y)