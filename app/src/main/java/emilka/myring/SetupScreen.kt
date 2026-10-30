package emilka.myring

import android.Manifest
import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SetupScreen(onFinish: (startDate: LocalDate, weeks: Int, reminderTime: LocalTime) -> Unit) {
    val context = LocalContext.current

    var step by remember { mutableStateOf(0) }
    // Dane z konfiguracji — wyciągnięte do góry, żeby trafić do ekranu Home i alarmu.
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var weeks by remember { mutableStateOf(3) }
    var reminderStart by remember { mutableStateOf(LocalTime.of(18, 0)) }

    // Grant status refreshed on each recomposition
    fun notifGranted() = ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        fun overlayGranted() = Settings.canDrawOverlays(context)
        fun cameraGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        val cameraLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    var tick by remember { mutableStateOf(0) }

                    // Zablokuj przypadkowe wyjście z aplikacji gestem/przyciskiem Wstecz:
                    // na kroku 0 nic nie robimy (nie wychodzimy), na wyższych cofamy krok.
                    BackHandler {
                        if (step > 0) step--
                    }

                    // Odśwież stany uprawnień, gdy wracamy z ustawień/systemowego okna —
                    // bez tego trzeba by klikać w kaflowe ponownie.
                    LifecycleResumeEffect(Unit) {
                        tick++
                        onPauseOrDispose { }
                    }

            // Podniesiony do góry stan kalendarza, żeby SetupScreen wiedział,
            // że podgląd kalendarza jest otwarty (i mógł ukryć strzałkę Dalej).
            var pickerOpen by remember { mutableStateOf(false) }
            var showOverlayConfirm by remember { mutableStateOf(false) }

            Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Treść wyśrodkowana w obszarze nad dolnymi przyciskami — nigdy nie ucieka
                                        // na dół ekranu. Przyciski są zakotwiczone na dole i to one są stałe.
                                        Box(Modifier.weight(1f)) {
                                            Column(
                                                Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                        AnimatedContent(
                                                                targetState = step,
                                                                transitionSpec = {
                                                                    // Kolejny etap wjeżdża z prawej, poprzedni wyjeżdża w lewo (przejście).
                                                                    val direction = if (targetState > initialState) 1 else -1
                                                                    (slideInHorizontally { it * direction } + fadeIn()) togetherWith
                                                                        (slideOutHorizontally { -it * direction } + fadeOut())
                                                                },
                                                                label = "step"
                                                            ) { targetStep ->
                                                                when (targetStep) {
                                                0 -> StepDate(
                                                                                                    startDate = startDate,
                                                                                                    onStartDateChange = { startDate = it },
                                                                                                    pickerOpen = pickerOpen,
                                                                                                    onPickerOpenChange = { pickerOpen = it }
                                                                                                )
                                                                                                1 -> StepWeeks(weeks = weeks, onWeeksChange = { weeks = it })
                                                                                                2 -> StepTimeRange(startTime = reminderStart, onStartChange = { reminderStart = it })
                                                3 -> StepPermissions(
                                                                                                                                                    notifGranted = notifGranted(),
                                                                                                                                                    overlayGranted = overlayGranted(),
                                                                                                                                                    cameraGranted = cameraGranted(),
                                                                                                                                                    requestNotif = { notifLauncher.launch(POST_NOTIFICATIONS); tick++ },
                                                                                                                                                    requestOverlay = { showOverlayConfirm = true },
                                                                                                                                                    requestCamera = { cameraLauncher.launch(Manifest.permission.CAMERA); tick++ }
                                                                                                                                                )
                                                                                            }
                                                                                        }
                                                                                }
                                                                            }

                                                                                                                            Spacer(Modifier.height(28.dp))

                                                                                                                                                if (showOverlayConfirm) {
                                                                                                                                                        AlertDialog(
                                                                                                                                                            onDismissRequest = { showOverlayConfirm = false },
                                                                                                                                                            icon = { Icon(Icons.Default.Layers, null) },
                                                                                                                                                            title = { Text("Wyświetlanie nad innymi aplikacjami") },
                                                                                                                                                            text = {
                                                                                                                                                                                                            Text("Android pozwala włączyć to tylko z poziomu ustawień systemowych. Kliknij „Otwarte ustawienia” i włącz przełącznik „Zezwalaj na wyświetlanie nad innymi aplikacjami”.")
                                                                                                                                                                                                        },
                                                                                                                                                            confirmButton = {
                                                                                                                                                                TextButton(onClick = {
                                                                                                                                                                    showOverlayConfirm = false
                                                                                                                                                                    val i = Intent(
                                                                                                                                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                                                                                                                                        Uri.parse("package:${context.packageName}")
                                                                                                                                                                    )
                                                                                                                                                                    context.startActivity(i)
                                                                                                                                                                    tick++
                                                                                                                                                                }) { Text("Otwarte ustawienia") }
                                                                                                                                                            },
                                                                                                                                                            dismissButton = {
                                                                                                                                                                TextButton(onClick = { showOverlayConfirm = false }) { Text("Anuluj") }
                                                                                                                                                            }
                                                                                                                                                        )
                                                                                                                                                    }

                                                                                                                                                // Przyciski tuż pod treścią, dynamicznie podążają za zawartością.
                                                                                                                                                                                                                                                                                                                                                                                                                                                Spacer(Modifier.height(40.dp))

                                                                                                                                                                                                                                                                                                                                                                                                                                                Row(
                                                                                                                                                    modifier = Modifier
                                                                                                                                                        .fillMaxWidth()
                                                                                                                                                        .navigationBarsPadding(),
                                                                                                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                                                                                                    horizontalArrangement = Arrangement.Center
                                                                                                                                                ) {
                                                                                                                                                    // Wstecz ukryty na ostatnim etapie (uprawnienia).
                                                                                                                                                    if (step > 0 && step < 3) {
                                                                                                                                                        Surface(
                                                                                                                                                            onClick = { step-- },
                                                                                                                                                            modifier = Modifier.size(56.dp),
                                                                                                                                                            shape = CircleShape,
                                                                                                                                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                                                                                                                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                                                                                                                        ) {
                                                                                                                                                            Box(contentAlignment = Alignment.Center) {
                                                                                                                                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                                                                                                                                                            }
                                                                                                                                                        }
                                                                                                                                                        Spacer(Modifier.width(24.dp))
                                                                                                                                                    }

                                                                                                                                                    // Na ostatnim etapie, gdy wszystkie uprawnienia są nadane,
                                                                                                                                                    // strzałka zmienia się w przycisk "Zaczynamy".
                                                                                                                                                    if (step == 3) {
                                                                                                                                                                                                                        val allGranted = notifGranted() && overlayGranted()
                                                                                                                                                                                                                        Surface(
                                                                                                                                                                                                                                                                                                onClick = { if (allGranted) onFinish(startDate, weeks, reminderStart) },
                                                                                                                                                                                                                            enabled = allGranted,
                                                                                                                                                                                                                            modifier = Modifier
                                                                                                                                                                                                                                .height(56.dp)
                                                                                                                                                                                                                                .width(180.dp),
                                                                                                                                                                                                                            shape = CircleShape,
                                                                                                                                                                                                                            color = if (allGranted) MaterialTheme.colorScheme.primary
                                                                                                                                                                                                                            else MaterialTheme.colorScheme.surfaceVariant,
                                                                                                                                                                                                                            contentColor = if (allGranted) MaterialTheme.colorScheme.onPrimary
                                                                                                                                                                                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                                                                                                                                                                            shadowElevation = if (allGranted) 8.dp else 0.dp
                                                                                                                                                                                                                        ) {
                                                                                                                                                                                                                            Box(contentAlignment = Alignment.Center) {
                                                                                                                                                                                                                                Text("Zaczynamy",
                                                                                                                                                                                                                                    style = MaterialTheme.typography.titleMedium,
                                                                                                                                                                                                                                    fontWeight = FontWeight.Bold)
                                                                                                                                                                                                                            }
                                                                                                                                                                                                                        }
                                                                                                                                                                                                                    } else if (!(step == 0 && pickerOpen)) {
                                                                                                                                                        // Strzałka Dalej ukryta, dopóki kalendarz nie jest zamknięty (data nie wybrana).
                                                                                                                                                        Surface(
                                                                                                                                                            onClick = { if (step < 3) step++ else onFinish(startDate, weeks, reminderStart) },
                                                                                                                                                            modifier = Modifier.size(64.dp),
                                                                                                                                                            shape = CircleShape,
                                                                                                                                                            color = MaterialTheme.colorScheme.primary,
                                                                                                                                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                                                                                                                                            shadowElevation = 8.dp
                                                                                                                                                        ) {
                                                                                                                                                            Box(contentAlignment = Alignment.Center) {
                                                                                                                                                                Icon(
                                                                                                                                                                    Icons.AutoMirrored.Filled.ArrowForward,
                                                                                                                                                                    contentDescription = "Dalej"
                                                                                                                                                                )
                                                                                                                                                            }
                                                                                                                                                        }
                                                                                                                                                    }
                                                                                                                                                }
                                                                                                                                            }
                                                                                                                                        }

                        @OptIn(ExperimentalMaterial3Api::class)
                        @Composable
                        fun StepDate(startDate: LocalDate, onStartDateChange: (LocalDate) -> Unit,
                                            pickerOpen: Boolean, onPickerOpenChange: (Boolean) -> Unit) {
                                val today = remember { LocalDate.now() }
                                val selected = startDate
                                val formatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }

        /*** Kalendarz pamięta wybór — stan trzymany POZA AnimatedVisibility,
             *  więc data nie znika przy ponownym otwarciu kalendarza. ***/
        val todayUtc = remember(today) { today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = selected.atStartOfDay(ZoneId.systemDefault())
                        .toInstant().toEpochMilli(),
                    selectableDates = remember(todayUtc) {
                        object : SelectableDates {
                            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                                utcTimeMillis <= todayUtc
                            override fun isSelectableYear(year: Int): Boolean = year <= today.year
                        }
                    }
                )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Kiedy założyłaś pierścień?",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(28.dp))

            // Clickable date tile — opens inline calendar below
            Surface(
                onClick = { onPickerOpenChange(!pickerOpen) },
                modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Data założenia",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selected.format(formatter),
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

        AnimatedVisibility(visible = pickerOpen) {
                            Column(
                                Modifier.padding(top = 16.dp)
                            ) {
                                // Kalendarz skalowany do szerokości ekranu (rozwiązanie z GitHub/StackOverflow):
                                // DatePicker ma naturalnie ~360dp szerokości (48dp na dzień tygodnia). Gdy ekran jest
                                // węższy, skalujemy go w dół, żeby zmieścił się bez przycinania i nakładania na daty.
                                BoxWithConstraints {
                                    val scale = remember(maxWidth) {
                                        if (maxWidth > 360.dp) 1f else (maxWidth / 360.dp)
                                    }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier
                                        .requiredWidthIn(min = 360.dp)
                                        .scale(scale),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        DatePicker(state = pickerState, showModeToggle = false)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = { onPickerOpenChange(false) }) { Text("Anuluj") }
                                            Spacer(Modifier.width(8.dp))
                                            Button(onClick = {
                                                                                            pickerState.selectedDateMillis?.let { millis ->
                                                                                                onStartDateChange(
                                                                                                    Instant.ofEpochMilli(millis)
                                                                                                        .atZone(ZoneId.systemDefault()).toLocalDate()
                                                                                                )
                                                                                            }
                                                                                            onPickerOpenChange(false)
                                                                                        }) { Text("Wybierz") }
                                        }
                                    }
                                }
                            }
                                            }
                                        }
                                    }
                                }
                            }

                            @OptIn(ExperimentalMaterial3Api::class)
                            @Composable
                            fun StepWeeks(weeks: Int, onWeeksChange: (Int) -> Unit) {
                                                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Na ile tygodni trwa cykl?",
                                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(28.dp))

                                    // Dwa duże, wybieralne kafelki z dużą liczbą tygodni
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        for (w in listOf(3, 4)) {
                                            val isSelected = weeks == w
                                            Surface(
                                                                                    onClick = { onWeeksChange(w) },
                                                                                    modifier = Modifier
                                                                                        .weight(1f)
                                                                                        .height(120.dp),
                                                shape = RoundedCornerShape(20.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text("$w",
                                                        style = MaterialTheme.typography.displayMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurface)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text("tygodnie",
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepTimeRange(startTime: LocalTime, onStartChange: (LocalTime) -> Unit) {
    var end by remember { mutableStateOf(LocalTime.of(22, 0)) }
    // Tylko jeden zegar otwarty naraz ("active").
    var active by remember { mutableStateOf<Boolean?>(null) } // true=start, false=end
    val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm") }
    val start = startTime

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Godzina przypomnień",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(28.dp))

        // Kafelek godziny startu — kliknięcie otwiera zegar (i zamyka drugi)
        TimeTile("Początek", start.format(fmt)) { active = true }
        Spacer(Modifier.height(16.dp))
        // Kafelek godziny końca
        TimeTile("Koniec", end.format(fmt)) { active = false }

        AnimatedVisibility(visible = active == true) {
            AutoTimePicker(
                initialHour = start.hour,
                initialMinute = start.minute,
                onPicked = { h, m ->
                    onStartChange(LocalTime.of(h, m))
                    active = null
                }
            )
        }

        AnimatedVisibility(visible = active == false) {
            AutoTimePicker(
                initialHour = end.hour,
                initialMinute = end.minute,
                onPicked = { h, m ->
                    end = LocalTime.of(h, m)
                    active = null
                }
            )
        }
    }
}

/*** Zegar wpasowany w UI: skalowany do ekranu, a wybrana godzina zatwierdza się sama. ***/
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoTimePicker(
    initialHour: Int,
    initialMinute: Int,
    onPicked: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    // Godzina i minuty wybierane po kolei: najpierw godzina, potem minuty.
        // Zapisuj dopiero, gdy użytkownik zatwierdzi MINUTY (nie po samej godzinie),
        // inaczej dostalibyśmy zawsze :00. Stąd obserwujemy tylko stan minut.
        var lastMinute by remember { mutableStateOf(initialMinute) }
        LaunchedEffect(state.minute) {
            val nowMinute = state.minute
            if (nowMinute != lastMinute) {
                kotlinx.coroutines.delay(300)
                onPicked(state.hour, state.minute)
            }
        }

    // Zegar w naturalnym rozmiarze (bez skalowania — nie psuje tarczy).
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(
                        state = state,
                        modifier = Modifier.widthIn(max = 320.dp)
                    )
                }
            }
        }

@Composable
private fun TimeTile(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun TimePickerCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        content()
    }
}

@Composable
fun StepPermissions(notifGranted: Boolean, overlayGranted: Boolean, cameraGranted: Boolean,
                    requestNotif: () -> Unit, requestOverlay: () -> Unit, requestCamera: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Uprawnienia",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(28.dp))

            PermissionRow(Icons.Default.NotificationsActive, "Powiadomienia",
                            notifGranted, requestNotif)
                        Spacer(Modifier.height(12.dp))
                        PermissionRow(Icons.Default.PhotoCamera, "Kamera",
                                                        cameraGranted, onClick = { if (!cameraGranted) requestCamera() })
                        Spacer(Modifier.height(12.dp))
                        PermissionRow(Icons.Default.Layers, "Nad innymi aplikacjami",
                                                        overlayGranted, onClick = { if (!overlayGranted) requestOverlay() })
                        Spacer(Modifier.height(12.dp))
                        // Działanie w tle i przy starcie — nadawane automatycznie z manifestu.
                        PermissionRow(
                            icon = Icons.Default.Layers,
                            title = "Działanie w tle i przy starcie",
                            granted = true,
                            onClick = {}
                        )
                        Spacer(Modifier.height(12.dp))
                                            }
                                        }

            @Composable
            fun PermissionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String,
                              granted: Boolean, onClick: () -> Unit) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (granted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
            .clickable { onClick() }
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                        color = if (granted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
                }
        Spacer(Modifier.weight(1f))
                                if (granted) {
                                                                                            Icon(Icons.Default.CheckCircle, contentDescription = null,
                                                                                                modifier = Modifier.size(28.dp),
                                                                                                tint = MaterialTheme.colorScheme.primary)
                                                                                        } else {
                                                                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                                                                                modifier = Modifier.size(28.dp),
                                                                                                tint = MaterialTheme.colorScheme.primary)
                                                                                        }
                    }
                }

        /*** Ekran powitalny: pierścień rysuje się (0°→360°), potem "oddycha" (puls), a napis się pokazuje. ***/
                @Composable
                fun RingSplash() {
                                    val primary = MaterialTheme.colorScheme.primary
                                    val drawProgress = remember { Animatable(0f) }
                                    val textScale = remember { Animatable(0.6f) }
                                    val fade = remember { Animatable(1f) }
                                    var done by remember { mutableStateOf(false) }

                                    // Faza 1: rysowanie pierścienia (900 ms). Faza 2: napis wjeżdża (500 ms).
                                    // Faza 3: zanik (450 ms).
                                    LaunchedEffect(Unit) {
                                        drawProgress.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
                                        textScale.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
                                        kotlinx.coroutines.delay(300)
                                        fade.animateTo(0f, animationSpec = tween(450))
                                        done = true
                                    }

                                    if (!done) {
                                                                            Box(
                                                                                Modifier
                                                                                    .fillMaxSize()
                                                                                    .background(androidx.compose.ui.graphics.Color.White)
                                                                            ) {
                                            // Główny rysujący się pierścień
                                            Canvas(
                                                Modifier
                                                    .size(220.dp)
                                                    .align(Alignment.Center)
                                                    .graphicsLayer { this.alpha = fade.value }
                                            ) {
                                                drawArc(
                                                    color = primary,
                                                    startAngle = -90f,
                                                    sweepAngle = 360f * drawProgress.value,
                                                    useCenter = false,
                                                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                                                )
                                            }
                                            // Napis pojawia się z lekkim powiększeniem
                                            Text(
                                                "Myring",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = primary,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .scale(textScale.value)
                                                    .graphicsLayer { this.alpha = fade.value }
                                            )
                                        }
                                    }
                                }
