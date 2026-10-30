package emilka.myring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import emilka.myring.ui.theme.MojeKrążkiTheme
import emilka.myring.update.DialogAktualizacjiHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.createChannels(this)
        enableEdgeToEdge()
        setContent {
            MojeKrążkiTheme {
                Box(Modifier.fillMaxSize()) {
                    MyringApp()
                    DialogAktualizacjiHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Po powrocie z ustawień / północy — odśwież widget i alarmy.
        MyringWidgetProvider.refreshAll(this)
        WidgetRefreshScheduler.scheduleNextMidnight(this)
    }
}

@Composable
fun MyringApp() {
    val context = LocalContext.current
    var showSplash by remember { mutableStateOf(true) }
    var started by remember { mutableStateOf(false) }
    var ringStart by remember { mutableStateOf(LocalDate.now()) }
        var cycleWeeks by remember { mutableStateOf(3) }
        var effectiveAnchor by remember { mutableStateOf(LocalDate.now()) }
            var nextBreakDate by remember { mutableStateOf<LocalDate?>(null) }

    // Jeśli konfiguracja była już zapisana, pomiń kreatora i wróć do Home z danymi.
            val setupDone = remember { SettingsStore.isSetupDone(context) }
            val savedStart = remember { SettingsStore.loadStartDate(context) }
            val savedWeeks = remember { SettingsStore.loadWeeks(context) }
            val savedReminder = remember { SettingsStore.loadReminderTime(context) }
            // Realnie potwierdzone daty cyklu (przesuwają następne etapy).
            val currentInsert = remember { SettingsStore.loadCurrentInsert(context) }
            val nextInsert = remember { SettingsStore.loadNextInsert(context) }
            LaunchedEffect(Unit) {
                        if (setupDone && savedStart != null) {
                            ringStart = savedStart
                            cycleWeeks = savedWeeks
                            // Efektywna kotwica bieżącego cyklu = ostatnie potwierdzone włożenie (lub start).
                                                        effectiveAnchor = currentInsert ?: savedStart
                                                        nextBreakDate = nextInsert
                            started = true
                            // Odtwórz codzienny alarm + przypomnienia o wyjęciu na zapisanej godzinie.
                            val time = savedReminder ?: LocalTime.of(18, 0)
                            ReminderCenter.createChannel(context)
                            AlarmScheduler.scheduleDaily(context, time)
                            ReminderScheduler.schedule(context, effectiveAnchor, savedWeeks, time)
                            WidgetRefreshScheduler.scheduleNextMidnight(context)
                            MyringWidgetProvider.refreshAll(context)
                        }
                        // Pokaż animację pierścienia przez chwilę, potem przejdź dalej.
                        delay(if (setupDone) 1300 else 1900)
                        showSplash = false
                    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!showSplash && !started) {
                SetupScreen(onFinish = { date, weeks, time ->
                                            // Zapisz ustawienia (w tym godzinę przypomnień) + start Home.
                                            SettingsStore.saveSetup(context, date, weeks, time)
                                                                                    ReminderCenter.createChannel(context)
                                                                                    AlarmScheduler.scheduleDaily(context, time)
                                                                                    ReminderScheduler.schedule(context, date, weeks, time)
                                                                                    WidgetRefreshScheduler.scheduleNextMidnight(context)
                                                                                    MyringWidgetProvider.refreshAll(context)
                                                                                    ringStart = date
                                                                                    cycleWeeks = weeks
                                                                                    effectiveAnchor = date
                                                                                    nextBreakDate = null
                                                                                    started = true
                                        })
            }
            if (started) {
                        HomeScreen(
                                                                        startDate = effectiveAnchor,
                                                                        weeks = cycleWeeks,
                                                                        nextBreakEnd = nextBreakDate,
                                                                        onClearSetup = {
                                                        SettingsStore.clearSetup(context)
                                                        started = false   // wróć do kreatora
                                                    })
                    }
            // Splash na WIERZCHU — zasłaniając ekran główny, aż do zakończenia animacji.
            AnimatedVisibility(
                visible = showSplash,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(900))
            ) {
                RingSplash()
            }
        }
    }

/*** Fazy cyklu: K = krążek na, P = przerwa (7 dni), potem ponowna recepcja. ***/
enum class CyclePhase { RING_ON, BREAK, NEW_CYCLE_READY }

data class CycleInfo(
    val phase: CyclePhase,
    val removeDate: LocalDate,
    val breakEnd: LocalDate,
    val nextStartDate: LocalDate,
    val daysLeft: Long
)

fun computeCycle(startDate: LocalDate, weeks: Int, day: LocalDate): CycleInfo {
    val removeDate = startDate.plusDays(weeks * 7L)
    val breakEnd = removeDate.plusDays(7L)
    val nextStartDate = breakEnd
    val phase = when {
        day < removeDate -> CyclePhase.RING_ON
        day < breakEnd -> CyclePhase.BREAK
        else -> CyclePhase.NEW_CYCLE_READY
    }
    val daysLeft = when (phase) {
        CyclePhase.RING_ON -> java.time.temporal.ChronoUnit.DAYS.between(day, removeDate)
        CyclePhase.BREAK -> java.time.temporal.ChronoUnit.DAYS.between(day, breakEnd)
        CyclePhase.NEW_CYCLE_READY -> 0
    }
    return CycleInfo(phase, removeDate, breakEnd, nextStartDate, daysLeft)
    }

    /** Jeden cykl historii: kiedy włożono, na ile tygodni, kiedy wyjęto, kiedy koniec przerwy. */
    data class CycleEvent(
        val insertDate: LocalDate,
        val weeks: Int,
        val removeDate: LocalDate,
        val breakEnd: LocalDate
    )

    /** Buduje powtarzalną oś: cykle przeszłe i przyszłe wokół dnia "day" (maks. po ok. 2 wstecz i 2 wprzód). */
    fun timeline(startDate: LocalDate, weeks: Int, day: LocalDate): List<CycleEvent> {
        val stepDays = weeks * 7L + 7L
        val delta = java.time.temporal.ChronoUnit.DAYS.between(startDate, day)
        // Indeks bieżącego cyklu (najbliższego, którego insert <= day)
        val currentIndex = if (delta < 0) -1 else (delta / stepDays)
        val from = (currentIndex - 2).coerceAtLeast(-8)
        val to = currentIndex + 3
        return (from..to).map { i ->
            val insert = startDate.plusDays(i * stepDays)
            val remove = insert.plusDays(weeks * 7L)
            CycleEvent(insert, weeks, remove, remove.plusDays(7L))
        }
    }

/** Status danego dnia w cyklu. */
enum class DayMark { NONE, RING, BREAK, INSERT }

fun dayMark(date: LocalDate, startDate: LocalDate, weeks: Int): DayMark {
    val step = weeks * 7L + 7L
    val delta = java.time.temporal.ChronoUnit.DAYS.between(startDate, date)
    if (delta < 0) return DayMark.NONE
    val posInCycle = delta % step
    val weeksDays = weeks * 7L
    return when {
        posInCycle < weeksDays -> DayMark.RING
        posInCycle == weeksDays -> DayMark.INSERT  // dzień wyjęcia = granica → oznacz jako dzień akcji
        posInCycle < weeksDays + 7L -> DayMark.BREAK
        else -> DayMark.RING // po przerwie nowy krążek
    }
}

/** Macierz dni miesiąca z poprzedzającym wypełnieniem, tak by tydzień zaczynał się od pon. */
fun calendarCells(year: Int, month: Int): List<LocalDate?> {
    val first = LocalDate.of(year, month, 1)
    val daysInMonth = first.lengthOfMonth()
    // Monday-first: dodajemy dni z poprzedniego miesiąca, by poniedziałek był pierwszy.
    val leading = (first.dayOfWeek.value - 1) % 7
    val start = first.minusDays(leading.toLong())
    val total = ((leading + daysInMonth + 6) / 7) * 7
    return (0 until total).map { start.plusDays(it.toLong()) }
}

/*** Ekran główny: okrąg z odliczaniem + faza cyklu + narzędzia deweloperskie. ***/
@Composable
fun HomeScreen(startDate: LocalDate, weeks: Int, nextBreakEnd: LocalDate? = null, onClearSetup: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val today = LocalDate.now()

    val effectiveDay = today
                                                val scope = rememberCoroutineScope()
                                                var stockTick by remember { mutableStateOf(0) }

        val timelineData = remember(startDate, weeks, today) { timeline(startDate, weeks, today) }

    val cycle = remember(startDate, weeks, effectiveDay, nextBreakEnd) {
        if (nextBreakEnd != null) {
            // Krążek potwierdzenie wyjęliśmy — jesteśmy w przerwie do podanej daty.
            val removeDate = startDate // data potwierdzonego wyjęcia (kotwica)
            val breakEnd = nextBreakEnd
            val phase = if (effectiveDay < breakEnd) CyclePhase.BREAK else CyclePhase.NEW_CYCLE_READY
            val daysLeft = if (phase == CyclePhase.BREAK)
                java.time.temporal.ChronoUnit.DAYS.between(effectiveDay, breakEnd)
            else 0
            CycleInfo(phase, removeDate, breakEnd, breakEnd, daysLeft)
        } else {
            computeCycle(startDate, weeks, effectiveDay)
        }
    }

        val phaseProgress = when (cycle.phase) {
        CyclePhase.RING_ON -> {
            val elapsed = java.time.temporal.ChronoUnit.DAYS.between(startDate, effectiveDay).coerceAtLeast(0)
            (elapsed.toFloat() / (weeks * 7L)).coerceIn(0f, 1f)
        }
        CyclePhase.BREAK -> {
            val breakElapsed = java.time.temporal.ChronoUnit.DAYS.between(cycle.removeDate, effectiveDay).coerceAtLeast(0)
            (breakElapsed.toFloat() / 7L).coerceIn(0f, 1f)
        }
        CyclePhase.NEW_CYCLE_READY -> 1f
    }
    val sweep = animateFloatAsState(
        when (cycle.phase) {
            CyclePhase.RING_ON -> 360f * phaseProgress * 0.72f   // krążek: wypełnia się w stronę wyjęcia
            CyclePhase.BREAK -> 360f * phaseProgress * 0.28f      // przerwa: mniejszy łuk dla kontrastu
            CyclePhase.NEW_CYCLE_READY -> 360f
        },
        animationSpec = tween(1200), label = "sweep"
    )

    val arcColor = when (cycle.phase) {
        CyclePhase.RING_ON -> primary
        CyclePhase.BREAK -> MaterialTheme.colorScheme.tertiary
        CyclePhase.NEW_CYCLE_READY -> MaterialTheme.colorScheme.primary
    }

    /*** Napis centralny: liczba dni + etykieta fazy. ***/
    val centerNumber: String = when (cycle.phase) {
        CyclePhase.RING_ON -> cycle.daysLeft.toString()
        CyclePhase.BREAK -> cycle.daysLeft.toString()
        CyclePhase.NEW_CYCLE_READY -> "✓"
    }
    val centerUnit = when (cycle.phase) {
        CyclePhase.RING_ON -> if (cycle.daysLeft <= 1L) "dzień" else "dni"
        CyclePhase.BREAK -> if (cycle.daysLeft <= 1L) "dzień" else "dni"
        CyclePhase.NEW_CYCLE_READY -> ""
    }
    val headline = when (cycle.phase) {
        CyclePhase.RING_ON -> "do wyjęcia krążka"
        CyclePhase.BREAK -> "przerwa od krążka"
        CyclePhase.NEW_CYCLE_READY -> "czas założyć nowy krążek"
    }
    val dateLabel = when (cycle.phase) {
        CyclePhase.RING_ON -> "Wyjmij krążek dnia:"
        CyclePhase.BREAK -> "Włóż nowy krążek dnia:"
        CyclePhase.NEW_CYCLE_READY -> "Nową recepcję zacznij:"
    }
    val dateValue = when (cycle.phase) {
        CyclePhase.RING_ON -> cycle.removeDate
        CyclePhase.BREAK -> cycle.breakEnd
        CyclePhase.NEW_CYCLE_READY -> cycle.nextStartDate
    }

    // HorizontalPager: 3 strony — Zapas(0) | Okrąg(1) | Kalendarz(2).
                // Przeciąganie obsługuje sam Pager (okrąg jedzie za palcem, potem przyciąga).
                Box(Modifier.fillMaxSize()) {
                val ringPage = 1
            val pagerState = rememberPagerState(initialPage = ringPage, pageCount = { 3 })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (page) {
                        0 -> StockSheet(context = context, onStockChanged = { stockTick++ },
                        onBack = { scope.launch { pagerState.animateScrollToPage(ringPage) } })
                        1 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Okrąg z wypełnieniem + liczba dni
                            Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawCircle(color = primaryContainer.copy(alpha = 0.5f), style = Stroke(width = 16.dp.toPx()))
                                    drawArc(
                                        color = arcColor, startAngle = -90f,
                                        sweepAngle = sweep.value, useCenter = false,
                                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(centerNumber, style = MaterialTheme.typography.displayLarge,
                                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    if (centerUnit.isNotEmpty()) {
                                        Text(centerUnit, style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(headline, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(10.dp))
                            Text(dateValue.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                                color = primary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text(dateLabel, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            // Status zapasów pod datą — czytelny skrót bez osobnej karty.
                            val stockLeft = remember(stockTick) { StockStore.totalRemaining(context) }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                when {
                                    stockLeft <= 0 -> "Zapas: brak krążków"
                                    stockLeft == 1 -> "Zapas: 1 krążek"
                                    else -> "Zapas: $stockLeft krążki"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (stockLeft <= 1) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            PermissionStatusHint(context)
                            // W dniu akcji pokaż przycisk potwierdzenia (X / ptaszek).
                            ActionDayButton(startDate = startDate, weeks = weeks, today = today, context = context)
                        }
                        2 -> CycleCalendarSheet(startDate = startDate, weeks = weeks,
                            onBack = { scope.launch { pagerState.animateScrollToPage(ringPage) } })
                    }
                }
            }

                                                                                                                                            // Pasek zapasów na górze ekranu: kółka pokazujące ile krążków zostało.
                                                                                                                                                        StockHeaderRow(
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    context = context,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    onTap = { scope.launch { pagerState.animateScrollToPage(ringPage) } },
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    refreshKey = stockTick,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                            modifier = Modifier
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                .align(Alignment.TopCenter)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                .padding(top = 24.dp)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                        )

                                                                                                                                                                                                                            }
                                                                                                                                                                                                                                                                                                }

                                                                                                                                                                                                                                                                                                        /*** Kalendarz cyklu (7-dniowy widok miesiąca z kolorowymi dniami). */
                @Composable
                private fun CycleCalendarSheet(startDate: LocalDate, weeks: Int, onBack: () -> Unit) {
                                                                    val primary = MaterialTheme.colorScheme.primary
                                                                    val context = androidx.compose.ui.platform.LocalContext.current
                                                                    val ringColor = Color(0xFF9CD3F2)   // jasnoniebieski: dni z krążkiem
                                                    val insertColor = Color(0xFFF6A0AC) // różowy: dzień włożenia nowego
                                                    val breakColor = Color(0xFFE55B5B)  // czerwony: kropelka krwi (bez krążka)
                                    val today = LocalDate.now()
                                    var shown by remember { mutableStateOf(today.withDayOfMonth(1)) }
                                    val fmt = java.time.format.DateTimeFormatter.ofPattern("MM.yyyy")
                                    val monthNames = arrayOf(
                                        "Styczeń","Luty","Marzec","Kwiecień","Maj","Czerwiec",
                                        "Lipiec","Sierpień","Wrzesień","Październik","Listopad","Grudzień"
                                    )

                                    Surface(
                                                                                                                                Modifier
                                                                                                                                        .fillMaxWidth()
                                                                                                                                        .pointerInput(Unit) {
                                                                                                                                            detectHorizontalDragGestures { _, dragAmount ->
                                                                                                                                                if (kotlin.math.abs(dragAmount) < 50f) return@detectHorizontalDragGestures
                                                                                                                                                if (dragAmount > 0) onBack()   // poziome gest w prawo → wróć do okręgu
                                                                                                                                            }
                                                                                                                                        },
                                                                                                                                    color = MaterialTheme.colorScheme.surface,
                                                                                                                                    tonalElevation = 2.dp,
                                                                                                                                    shape = RoundedCornerShape(20.dp)
                                                                                                                                ) {
                                                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                                                                                                            // Nagłówek: tytuł + nawigacja miesiąca.
                                                                                                                            Text("Kalendarz cyklu",
                                                                                                                                                                                                                                                            style = MaterialTheme.typography.titleLarge,
                                                                                                                                                                                                                                                            fontWeight = FontWeight.Bold,
                                                                                                                                                                                                                                                            modifier = Modifier.padding(start = 4.dp))
                                                                                                                                                                                                                                                        // Do kiedy starczą krążki (według zapasów).
                                                                                                                                                                                                                                                        val stockTotal = StockStore.load(context).sumOf { it.remaining }
                                                                                                                                                                                                                                                        if (stockTotal > 0) {
                                                                                                                                                                                                                                                            val endDate = LocalDate.now().plusDays(stockTotal * (weeks * 7L + 7L))
                                                                                                                                                                                                                                                            Text("Zapasy starczą do: ${endDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                                                                                                                                                                                                                                                                style = MaterialTheme.typography.bodyMedium,
                                                                                                                                                                                                                                                                color = MaterialTheme.colorScheme.primary,
                                                                                                                                                                                                                                                                modifier = Modifier.padding(start = 4.dp))
                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                                        Spacer(Modifier.height(8.dp))
                                                                                                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                                                                                                                                                            // Nawigacja miesiąca (wróć do okręgu przesuwając palcem w prawo).
                                                                                                                                                                                            Surface(
                                                                                                                                                                                                modifier = Modifier.size(36.dp).clickable { shown = shown.minusMonths(1) },
                                                                                                                                                                                                shape = CircleShape,
                                                                                                                                                                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                                                                                                                                                                contentColor = MaterialTheme.colorScheme.onSurface
                                                                                                                                                                                            ) { Box(contentAlignment = Alignment.Center) { Text("‹", style = MaterialTheme.typography.titleLarge) } }
                                                                                                                                Text(
                                                                                                                                    "${monthNames.getOrElse(shown.monthValue - 1){" "}} ${shown.year}",
                                                                                                                                    style = MaterialTheme.typography.titleMedium,
                                                                                                                                    fontWeight = FontWeight.Bold,
                                                                                                                                    modifier = Modifier.weight(1f),
                                                                                                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                                                                                                )
                                                                                                                                Surface(
                                                                                                                                    modifier = Modifier.size(36.dp).clickable { shown = shown.plusMonths(1) },
                                                                                                                                    shape = CircleShape,
                                                                                                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                                                                                                    contentColor = MaterialTheme.colorScheme.onSurface
                                                                                                                                ) { Box(contentAlignment = Alignment.Center) { Text("›", style = MaterialTheme.typography.titleLarge) } }
                                                                                                                            }
                                                                                                                            Spacer(Modifier.height(14.dp))
                                                                                            // Dni tygodnia.
                                                                                            Row(Modifier.fillMaxWidth()) {
                                                                                                listOf("Pn","Wt","Śr","Cz","Pt","So","Nd").forEach { d ->
                                                                                                    Text(d, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                                                                        style = MaterialTheme.typography.labelMedium,
                                                                                                        fontWeight = FontWeight.SemiBold,
                                                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                                                }
                                                                                            }
                                                                                            Spacer(Modifier.height(6.dp))
                                // Siatka dni.
                                val cells = remember(shown) { calendarCells(shown.year, shown.monthValue) }
                                cells.chunked(7).forEach { week ->
                                    Row(Modifier.fillMaxWidth()) {
                                        week.forEach { date ->
                                            DayCell(
                                                date = date,
                                                isCurrentMonth = date?.monthValue == shown.monthValue,
                                                isToday = date == today,
                                                mark = date?.let { dayMark(it, startDate, weeks) } ?: DayMark.NONE,
                                                ringColor = ringColor,
                                                insertColor = insertColor,
                                                breakColor = breakColor,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                                                // Legenda.
                                                                                                                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                                                                                                    LegendDot(ringColor, "krążek")
                                                                                                                                    LegendDot(breakColor, "przerwa")
                                                                                                                                    LegendDot(insertColor, "włóż")
                                                                                                                                }
                                                            }
                                                        }
                                                    }

                @Composable
                                                private fun DayCell(
                                                    date: LocalDate?,
                                                    isCurrentMonth: Boolean,
                                                    isToday: Boolean,
                                                    mark: DayMark,
                                                    ringColor: Color,
                                                    insertColor: Color,
                                                    breakColor: Color,
                                                    modifier: Modifier = Modifier
                                                ) {
                                                    androidx.compose.foundation.layout.Box(
                                                        modifier
                                                            .aspectRatio(1f)
                                                            .padding(3.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (date != null) {
                                                                                                                    // Koło tła: niebieskie dla dni z krążkiem, różowe dla dnia włożenia,
                                                                                                                    // dni przerwy pokazują tylko czerwoną kropelkę (bez koła).
                                                                                                                    val bgColor = when {
                                                                                                                        mark == DayMark.RING -> ringColor.copy(alpha = 0.7f)
                                                                                                                        mark == DayMark.INSERT -> insertColor.copy(alpha = 0.9f)
                                                                                                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                                                                                        isCurrentMonth -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                                                                                        else -> Color.Transparent
                                                                                                                    }
                                                                                                                    androidx.compose.foundation.layout.Box(
                                                                                                                        Modifier
                                                                                                                            .fillMaxSize()
                                                                                                                            .background(bgColor, CircleShape)
                                                                                                                    ) {
                                                                                                                        // Kropelka krwi przy dniu bez krążka (przerwa).
                                                                                                                                                                                                                                                if (mark == DayMark.BREAK) {
                                                                                                                                                                                                                                                    androidx.compose.foundation.layout.Box(
                                                                                                                                                                                                                                                        Modifier
                                                                                                                                                                                                                                                            .align(Alignment.BottomCenter)
                                                                                                                                                                                                                                                            .padding(bottom = 5.dp)
                                                                                                                                                                                                                                                            .size(7.dp)
                                                                                                                                                                                                                                                            .background(breakColor, CircleShape)
                                                                                                                                                                                                                                                    )
                                                                                                                                                                                                                                                }
                                                                                                                    }
                                                            Text(
                                                                date.dayOfMonth.toString(),
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isToday) MaterialTheme.colorScheme.primary
                                                                else if (!isCurrentMonth) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                                else MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                    }
                                                }

                                @Composable
                                                                                private fun StockHeaderRow(context: android.content.Context, onTap: () -> Unit = {},
                                                                                                                                                                    refreshKey: Int = 0, modifier: Modifier = Modifier) {
                                                                                                                                    // Pobierz świeży stan zapasów (refreshKey wymusza ponowne przeliczenie po zmianie).
                                                                                                                                    val packages = remember(refreshKey) { StockStore.load(context) }
                                                                                                                                    val totalStock = packages.sumOf { it.remaining }
                                                                                                                                    val totalCapacity = packages.sumOf { it.total }
                                                                                                                                    val shown = totalCapacity.coerceIn(1, 12)

                                                                                                                                    Box(modifier, contentAlignment = Alignment.Center) {
                                                                                                                                                                                                                                                                                                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                                                                                                                                                                                                                                                                                            Row(
                                                                                                                                                                                                                                                                                                                                                horizontalArrangement = Arrangement.Center,
                                                                                                                                                                                                                                                                                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                                                                                                                                                                                                                                                                                modifier = Modifier
                                                                                                                                                                                                                                                                                                                                                    .clickable { onTap() }
                                                                                                                                                                                                                                                                                                                                                    .background(
                                                                                                                                                                                                                                                                                                                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                                                                                                                                                                                                                                                                                                        RoundedCornerShape(50))
                                                                                                                                                                                                                                                                                                                                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(50))
                                                                                                                                                                                                                                                                                                                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                                                                                                                                                                                                                                                                                                                            ) {
                                                                                                                                                                                                                                                                                                                                                if (packages.isEmpty()) {
                                                                                                                                                                                                                                                                                                                                                    Text("Brak krążków w zapasie",
                                                                                                                                                                                                                                                                                                                                                        style = MaterialTheme.typography.bodyMedium,
                                                                                                                                                                                                                                                                                                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                                                                                                                                                                                                                                                                                                } else {
                                                                                                                                                                                                                                                                                                                                                    // Graficzny pasek: pełne kółka = zostało w zapasie, puste = zużyte.
                                                                                                                                                                                                                                                                                                                                                    for (i in 0 until shown) {
                                                                                                                                                                                                                                                                                                                                                        val filled = i < totalStock
                                                                                                                                                                                                                                                                                                                                                        Box(
                                                                                                                                                                                                                                                                                                                                                            Modifier
                                                                                                                                                                                                                                                                                                                                                                .padding(horizontal = 4.dp)
                                                                                                                                                                                                                                                                                                                                                                .size(30.dp)
                                                                                                                                                                                                                                                                                                                                                                .background(
                                                                                                                                                                                                                                                                                                                                                                    if (filled) MaterialTheme.colorScheme.primary
                                                                                                                                                                                                                                                                                                                                                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                                                                                                                                                                                                                                                                                                                                                    CircleShape
                                                                                                                                                                                                                                                                                                                                                                ),
                                                                                                                                                                                                                                                                                                                                                            contentAlignment = Alignment.Center
                                                                                                                                                                                                                                                                                                                                                        ) {
                                                                                                                                                                                                                                                                                                                                                            if (filled) {
                                                                                                                                                                                                                                                                                                                                                                Box(
                                                                                                                                                                                                                                                                                                                                                                    Modifier
                                                                                                                                                                                                                                                                                                                                                                        .size(10.dp)
                                                                                                                                                                                                                                                                                                                                                                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape))
                                                                                                                                                                                                                                                                                                                                                            }
                                                                                                                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                                                                                                                                    }
                                                                                                                                                                                                                                                                                                                                                }
                                                                                                                                                                                                                                                                                                                                            }
                                                                                                                                                                                                                                                                                                                                            // Ostrzeżenie przy 1 (lub 0) pozostałym krążku.
                                                                                                                                                                                                                                                                                                                                            if (totalStock <= 1) {
                                                                                                                                                                                                                                                                                                                                                Spacer(Modifier.height(6.dp))
                                                                                                                                                                                                                                                                                                                                                Text(
                                                                                                                                                                                                                                                                                                                                                    if (totalStock == 1) "Został 1 krążek — kup receptę!" else "Brak krążków — kup receptę!",
                                                                                                                                                                                                                                                                                                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                                                                                                                                                                                                                                                                                                    fontWeight = FontWeight.SemiBold,
                                                                                                                                                                                                                                                                                                                                                    color = MaterialTheme.colorScheme.error,
                                                                                                                                                                                                                                                                                                                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                                                                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                                                                                                                            }
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    }

}
}
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        @Composable
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            private fun LegendDot(color: Color, label: String) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(12.dp).background(color, CircleShape))
                                        Spacer(Modifier.width(6.dp))
                                        Text(label, style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

/** Krótki hint, gdy brak exact alarms / powiadomień — prowadzi do ustawień. */
@Composable
private fun PermissionStatusHint(context: android.content.Context) {
    var tick by remember { mutableStateOf(0) }
    val notifOk = remember(tick) { Notifications.areNotificationsEnabled(context) }
    val exactOk = remember(tick) { Notifications.canScheduleExactAlarms(context) }
    val batteryOk = remember(tick) { Notifications.isIgnoringBatteryOptimizations(context) }
    if (notifOk && exactOk && batteryOk) return

    Spacer(Modifier.height(12.dp))
    val msg = when {
        !notifOk -> "Włącz powiadomienia, żeby dostać przypomnienie o wyjęciu"
        !exactOk -> "Włącz dokładne alarmy — inaczej przypomnienie może się spóźnić"
        else -> "Wyłącz optymalizację baterii dla pewniejszych alarmów"
    }
    TextButton(onClick = {
        try {
            val intent = when {
                !notifOk -> Notifications.intentAppNotificationSettings(context)
                !exactOk -> Notifications.intentExactAlarmSettings(context)
                else -> Notifications.intentBatteryOptimization(context)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(Notifications.intentBatterySettings(context))
            } catch (_: Exception) { }
        }
        tick++
    }) {
        Text(msg, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun ActionDayButton(startDate: LocalDate, weeks: Int, today: LocalDate, context: android.content.Context) {
    val removeDate = startDate.plusDays(weeks * 7L)
    val breakEnd = removeDate.plusDays(7L)
    val fmt = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Określ, jaki dzień akcji to dziś (jeśli żaden — nie pokazuj przycisku).
    val isRemoveDay = today == removeDate
    val isInsertDay = today == breakEnd
    val alreadyDone = if (isRemoveDay) ReminderCenter.isRemovedToday(context)
    else if (isInsertDay) ReminderCenter.isInsertedToday(context) else false

    if ((isRemoveDay || isInsertDay) && !alreadyDone) {
        val insert = isInsertDay
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { ReminderCenter.showFullScreenReminder(context, insert) },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(
                if (isRemoveDay) "Potwierdź wyciągnięcie krążka" else "Potwierdź włożenie krążka",
                fontWeight = FontWeight.Bold
            )
        }
            }
        }
