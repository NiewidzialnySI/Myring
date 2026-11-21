package emilka.myring.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import emilka.myring.MainActivity
import emilka.myring.SettingsStore
import emilka.myring.StockStore
import emilka.myring.WidgetLogic
import java.time.LocalDate

/**
 * Widget Glance (jak Clock / Weather):
 * - Material3 light/dark (AMOLED)
 * - SizeMode.Responsive — inne layouty dla 2×2 / szeroki / duży
 */
class MyringGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SIZE_SMALL, SIZE_WIDE, SIZE_LARGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val start = SettingsStore.loadStartDate(context)
        val weeks = SettingsStore.loadWeeks(context)
        val today = LocalDate.now()
        val state = if (start == null) {
            null
        } else {
            val currentInsert = SettingsStore.loadCurrentInsert(context) ?: start
            val nextInsert = SettingsStore.loadNextInsert(context)
            WidgetLogic.compute(today, currentInsert, weeks, nextInsert)
        }
        val stockLeft = StockStore.load(context).sumOf { it.remaining }
        val configured = start != null
        val themeColors = MyringGlanceColorScheme.colorsFor(SettingsStore.loadThemePreference(context))

        provideContent {
            GlanceTheme(colors = themeColors) {
                WidgetRoot(
                    configured = configured,
                    state = state,
                    stockLeft = stockLeft
                )
            }
        }
    }

    companion object {
        /** ~2×2 */
        val SIZE_SMALL = DpSize(110.dp, 110.dp)
        /** Szeroki pasek */
        val SIZE_WIDE = DpSize(250.dp, 110.dp)
        /** ~4×4 / wysoki */
        val SIZE_LARGE = DpSize(250.dp, 250.dp)
    }
}

@Composable
private fun WidgetRoot(
    configured: Boolean,
    state: WidgetLogic.State?,
    stockLeft: Int
) {
    val size = LocalSize.current
    val wide = size.width >= MyringGlanceWidget.SIZE_WIDE.width
    val tall = size.height >= MyringGlanceWidget.SIZE_LARGE.height

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(28.dp)
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<MainActivity>())
            .padding(if (tall) 18.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            !configured || state == null -> SetupContent(compact = !tall && !wide)
            tall -> LargeContent(state, stockLeft)
            wide -> WideContent(state, stockLeft)
            else -> SmallContent(state)
        }
    }
}

@Composable
private fun SetupContent(compact: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        DaysBadge(text = "—", compact = compact)
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = "Skonfiguruj",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
        if (!compact) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "Otwórz aplikację",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun SmallContent(state: WidgetLogic.State) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        DaysBadge(text = state.daysText, compact = true)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = state.label,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun WideContent(state: WidgetLogic.State, stockLeft: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        DaysBadge(text = state.daysText, compact = true)
        Spacer(GlanceModifier.width(14.dp))
        Column(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.label,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = state.dateText,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
            if (stockLeft >= 0) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = stockLine(stockLeft),
                    maxLines = 1,
                    style = TextStyle(
                        color = if (stockLeft <= 1) GlanceTheme.colors.error
                        else GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun LargeContent(state: WidgetLogic.State, stockLeft: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        Text(
            text = "Myring",
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
        Spacer(GlanceModifier.height(12.dp))
        DaysBadge(text = state.daysText, compact = false)
        Spacer(GlanceModifier.height(12.dp))
        Text(
            text = state.label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = state.dateText,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        )
        Spacer(GlanceModifier.height(12.dp))
        Text(
            text = stockLine(stockLeft),
            style = TextStyle(
                color = if (stockLeft <= 1) GlanceTheme.colors.error
                else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun DaysBadge(text: String, compact: Boolean) {
    val diameter = if (compact) 72.dp else 108.dp
    Box(
        modifier = GlanceModifier
            .size(diameter)
            .cornerRadius(diameter / 2)
            .background(GlanceTheme.colors.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = if (compact) 28.sp else 40.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
    }
}

private fun stockLine(stockLeft: Int): String = when {
    stockLeft <= 0 -> "Brak krążków"
    stockLeft == 1 -> "1 krążek w zapasie"
    else -> "$stockLeft krążki w zapasie"
}
