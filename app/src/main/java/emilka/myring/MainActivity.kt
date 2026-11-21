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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import emilka.myring.ui.theme.CalendarBreakDark
import emilka.myring.ui.theme.CalendarBreakLight
import emilka.myring.ui.theme.CalendarInsertDark
import emilka.myring.ui.theme.CalendarInsertLight
import emilka.myring.ui.theme.CalendarRingDark
import emilka.myring.ui.theme.CalendarRingLight
import emilka.myring.ui.theme.MojeKrążkiTheme
import emilka.myring.ui.theme.ThemePreference
import emilka.myring.update.DialogAktualizacjiHost
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Dopasuj night mode zanim narysuje się pierwsze okno (bez białego flasha).
        emilka.myring.ui.theme.ThemeMode.applyFromStore(this)
        super.onCreate(savedInstanceState)
        Notifications.createChannels(this)
        enableEdgeToEdge()
        setContent {
            var themePref by remember {
                mutableStateOf(SettingsStore.loadThemePreference(this))
            }
            MojeKrążkiTheme(themePreference = themePref) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(Modifier.fillMaxSize()) {
                        MyringApp(onThemeChanged = { pref ->
                            themePref = pref
                            emilka.myring.ui.theme.ThemeMode.apply(pref)
                            MyringWidgetProvider.refreshAll(this@MainActivity)
                        })
                        DialogAktualizacjiHost()
                    }
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
fun MyringApp(onThemeChanged: (ThemePreference) -> Unit = {}) {
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

            fun applyCycleState() {
                if (!SettingsStore.isSetupDone(context)) return
                val state = SettingsStore.reloadCycleState(context)
                state.startDate?.let { ringStart = it }
                cycleWeeks = state.weeks
                effectiveAnchor = state.effectiveInsert ?: state.startDate ?: effectiveAnchor
                nextBreakDate = state.nextInsert
            }

            LifecycleResumeEffect(started) {
                if (started) applyCycleState()
                onPauseOrDispose { }
            }

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
                            onCycleRefresh = { applyCycleState() },
                            onThemeChanged = onThemeChanged
                        )
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
fun HomeScreen(
    startDate: LocalDate,
    weeks: Int,
    nextBreakEnd: LocalDate? = null,
    onCycleRefresh: () -> Unit = {},
    onThemeChanged: (ThemePreference) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val today = LocalDate.now()

    val effectiveDay = today
                                                val scope = rememberCoroutineScope()
                                                var stockTick by remember { mutableStateOf(0) }
        // Po potwierdzeniu włożenia (ReminderActivity) odśwież licznik zapasów.
        LifecycleResumeEffect(Unit) {
            stockTick++
            onPauseOrDispose { }
        }

        val lastRemoved = remember(stockTick) { SettingsStore.loadLastRemoved(context) }
        val timelineData = remember(startDate, weeks, today) { timeline(startDate, weeks, today) }

    val cycle = remember(startDate, weeks, effectiveDay, nextBreakEnd, lastRemoved) {
        if (nextBreakEnd != null) {
            val removeDate = lastRemoved ?: startDate.plusDays(weeks * 7L)
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
        animationSpec = tween(900), label = "sweep"
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

    // HorizontalPager: Zapas(0) | Okrąg(1) | Kalendarz(2) | Ustawienia(3).
                Box(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                val ringPage = 1
            val pagerState = rememberPagerState(initialPage = ringPage, pageCount = { 4 })
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when (page) {
                        0 -> StockSheet(context = context, onStockChanged = { stockTick++ },
                        onBack = { scope.launch { pagerState.animateScrollToPage(ringPage) } })
                        1 -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 88.dp, bottom = 16.dp)
                        ) {
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
                                    else -> "Zapas: ${CycleMarking.polishRingsCount(stockLeft)}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (stockLeft <= 1) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            PermissionStatusHint(context)
                            // W dniu akcji pokaż przycisk potwierdzenia (X / ptaszek).
                            ActionDayButton(
                                startDate = startDate,
                                weeks = weeks,
                                today = today,
                                nextBreakEnd = nextBreakEnd,
                                context = context
                            )
                        }
                        2 -> CycleCalendarSheet(startDate = startDate, weeks = weeks,
                            onBack = { scope.launch { pagerState.animateScrollToPage(ringPage) } })
                        3 -> SettingsSheet(
                            context = context,
                            onChanged = {
                                onCycleRefresh()
                                stockTick++
                            },
                            onThemeChanged = onThemeChanged,
                            onBack = { scope.launch { pagerState.animateScrollToPage(ringPage) } }
                        )
                    }
                }
            }

                if (pagerState.currentPage != 3) {
                                                                                                                                            // Pasek zapasów na górze ekranu: kółka pokazujące ile krążków zostało.
                                                                                                                                                        StockHeaderRow(
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    context = context,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    onTap = { scope.launch { pagerState.animateScrollToPage(0) } },
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    onStockChanged = {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        stockTick++
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        MyringWidgetProvider.refreshAll(context)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    },
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    refreshKey = stockTick,
                                                                                                                                                                                                                                                                                                                                                                                                                                                                            modifier = Modifier
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                .align(Alignment.TopCenter)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                .padding(top = 12.dp)
                                                                                                                                                                                                                                                                                                                                                                                                                                                                        )
                }

                                                                                                                                                                                                                            }
                                                                                                                                                                                                                                                                                                }

                                                                                                                                                                                                                                                                                                        /*** Kalendarz cyklu (7-dniowy widok miesiąca z kolorowymi dniami). */
                @Composable
                private fun CycleCalendarSheet(startDate: LocalDate, weeks: Int, onBack: () -> Unit) {
                                                                    val primary = MaterialTheme.colorScheme.primary
                                                                    val context = androidx.compose.ui.platform.LocalContext.current
                                                                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.2f
                                                                    val ringColor = if (isDark) CalendarRingDark else CalendarRingLight
                                                                    val insertColor = if (isDark) CalendarInsertDark else CalendarInsertLight
                                                                    val breakColor = if (isDark) CalendarBreakDark else CalendarBreakLight
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
                                                                                                                                        }
                                                                                                                                        .then(
                                                                                                                                            if (isDark) Modifier.border(
                                                                                                                                                1.dp,
                                                                                                                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                                                                                                                                RoundedCornerShape(20.dp)
                                                                                                                                            ) else Modifier
                                                                                                                                        ),
                                                                                                                                    color = if (isDark) Color(0xFF121214) else MaterialTheme.colorScheme.surface,
                                                                                                                                    tonalElevation = if (isDark) 0.dp else 2.dp,
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
                                                                                                                                                                                                                                                        val nextIns = SettingsStore.loadNextInsert(context)
                                                                                                                                                                                                                                                        val lastRem = SettingsStore.loadLastRemoved(context)
                                                                                                                                                                                                                                                        if (stockTotal > 0) {
                                                                                                                                                                                                                                                            val endDate = CycleMarking.estimateStockEndDate(
                                                                                                                                                                                                                                                                today, startDate, weeks, nextIns, lastRem, stockTotal)
                                                                                                                                                                                                                                                            endDate?.let { ed ->
                                                                                                                                                                                                                                                            Text("Zapasy starczą do: ${ed.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))}",
                                                                                                                                                                                                                                                                style = MaterialTheme.typography.bodyMedium,
                                                                                                                                                                                                                                                                color = MaterialTheme.colorScheme.primary,
                                                                                                                                                                                                                                                                modifier = Modifier.padding(start = 4.dp))
                                                                                                                                                                                                                                                            }
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
                                                mark = date?.let {
                                                    CycleMarking.dayMark(
                                                        it, startDate, weeks,
                                                        SettingsStore.loadNextInsert(context),
                                                        SettingsStore.loadLastRemoved(context)
                                                    )
                                                } ?: DayMark.NONE,
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
                                                                                                                    val darkBg = MaterialTheme.colorScheme.background.luminance() < 0.2f
                                                                                                                    val bgColor = when {
                                                                                                                        mark == DayMark.RING -> ringColor.copy(alpha = if (darkBg) 0.92f else 0.75f)
                                                                                                                        mark == DayMark.INSERT -> insertColor.copy(alpha = if (darkBg) 0.95f else 0.9f)
                                                                                                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = if (darkBg) 0.28f else 0.18f)
                                                                                                                        isCurrentMonth -> if (darkBg) Color(0xFF1C1C22) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                                                                                                        else -> Color.Transparent
                                                                                                                    }
                                                                                                                    val dayTextColor = when {
                                                                                                                        mark == DayMark.RING || mark == DayMark.INSERT -> Color.White
                                                                                                                        isToday -> MaterialTheme.colorScheme.primary
                                                                                                                        !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                                                                                        else -> MaterialTheme.colorScheme.onSurface
                                                                                                                    }
                                                                                                                    androidx.compose.foundation.layout.Box(
                                                                                                                        Modifier
                                                                                                                            .fillMaxSize()
                                                                                                                            .background(bgColor, CircleShape)
                                                                                                                            .then(
                                                                                                                                if (isToday && mark == DayMark.NONE) Modifier.border(
                                                                                                                                    1.5.dp,
                                                                                                                                    MaterialTheme.colorScheme.primary,
                                                                                                                                    CircleShape
                                                                                                                                ) else Modifier
                                                                                                                            )
                                                                                                                    ) {
                                                                                                                        // Kropelka krwi przy dniu bez krążka (przerwa).
                                                                                                                                                                                                                                                if (mark == DayMark.BREAK) {
                                                                                                                                                                                                                                                    androidx.compose.foundation.layout.Box(
                                                                                                                                                                                                                                                        Modifier
                                                                                                                                                                                                                                                            .align(Alignment.BottomCenter)
                                                                                                                                                                                                                                                            .padding(bottom = 5.dp)
                                                                                                                                                                                                                                                            .size(8.dp)
                                                                                                                                                                                                                                                            .background(breakColor, CircleShape)
                                                                                                                                                                                                                                                    )
                                                                                                                                                                                                                                                }
                                                                                                                    }
                                                            Text(
                                                                date.dayOfMonth.toString(),
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                fontWeight = if (isToday || mark == DayMark.INSERT) FontWeight.Bold else FontWeight.Normal,
                                                                color = dayTextColor
                                                            )
                                                        }
                                                    }
                                                }

                                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                                @Composable
                                                                                private fun StockHeaderRow(
    context: android.content.Context,
    onTap: () -> Unit = {},
    onStockChanged: () -> Unit = {},
    refreshKey: Int = 0,
    modifier: Modifier = Modifier
) {
                                                                                                                                    var showQuickEdit by remember { mutableStateOf(false) }
                                                                                                                                    val packages = remember(refreshKey) { StockStore.load(context) }
                                                                                                                                    val totalStock = packages.sumOf { it.remaining }
                                                                                                                                    val totalCapacity = packages.sumOf { it.total }
                                                                                                                                    val shown = totalCapacity.coerceIn(1, 12)
                                                                                                                                    val showNumeric = totalCapacity > 12
                                                                                                                                    val rowGesture = Modifier
                                                                                                                                        .combinedClickable(
                                                                                                                                            onClick = onTap,
                                                                                                                                            onLongClick = { showQuickEdit = true }
                                                                                                                                        )

                                                                                                                                    if (showQuickEdit) {
                                                                                                                                        StockQuickEditDialog(
                                                                                                                                            currentRemaining = totalStock,
                                                                                                                                            onDismiss = { showQuickEdit = false },
                                                                                                                                            onConfirm = { n ->
                                                                                                                                                StockStore.setTotalRemaining(context, n)
                                                                                                                                                showQuickEdit = false
                                                                                                                                                onStockChanged()
                                                                                                                                            }
                                                                                                                                        )
                                                                                                                                    }

                                                                                                                                    Box(modifier, contentAlignment = Alignment.Center) {
                                                                                                                                                                                                                                                                                                                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                                                                                                                                                                                                                                                                                                            if (showNumeric) {
                                                                                                                                                                                                                                                                                                                                                Text(
                                                                                                                                                                                                                                                                                                                                                    "${CycleMarking.polishRingsCount(totalStock)} w zapasie",
                                                                                                                                                                                                                                                                                                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                                                                                                                                                                                                                                                                                                    fontWeight = FontWeight.SemiBold,
                                                                                                                                                                                                                                                                                                                                                    modifier = rowGesture.padding(vertical = 8.dp)
                                                                                                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                                                                                                                            } else Row(
                                                                                                                                                                                                                                                                                                                                                horizontalArrangement = Arrangement.Center,
                                                                                                                                                                                                                                                                                                                                                verticalAlignment = Alignment.CenterVertically,
                                                                                                                                                                                                                                                                                                                                                modifier = rowGesture
                                                                                                                                                                                                                                                                                                                                                    .background(
                                                                                                                                                                                                                                                                                                                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                                                                                                                                                                                                                                                                                                                        RoundedCornerShape(50))
                                                                                                                                                                                                                                                                                                                                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(50))
                                                                                                                                                                                                                                                                                                                                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                                                                                                                                                                                                                                                                                                                            ) {
                                                                                                                                                                                                                                                                                                                                                if (packages.isEmpty()) {
                                                                                                                                                                                                                                                                                                                                                    Text("Przytrzymaj — ustaw zapas",
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
                                                                                                                                                                                                                                                                                                                                            if (packages.isNotEmpty()) {
                                                                                                                                                                                                                                                                                                                                                Text(
                                                                                                                                                                                                                                                                                                                                                    "Przytrzymaj kółka — popraw liczbę",
                                                                                                                                                                                                                                                                                                                                                    style = MaterialTheme.typography.labelSmall,
                                                                                                                                                                                                                                                                                                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                                                                                                                                                                                                                                                                                                    modifier = Modifier.padding(top = 4.dp)
                                                                                                                                                                                                                                                                                                                                                )
                                                                                                                                                                                                                                                                                                                                            }
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
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
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
private fun ActionDayButton(
    startDate: LocalDate,
    weeks: Int,
    today: LocalDate,
    nextBreakEnd: LocalDate?,
    context: android.content.Context
) {
    val removeDate = startDate.plusDays(weeks * 7L)
    val breakEnd = nextBreakEnd ?: removeDate.plusDays(7L)

    val isRemoveDay = nextBreakEnd == null && today == removeDate
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
