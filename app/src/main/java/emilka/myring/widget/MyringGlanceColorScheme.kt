package emilka.myring.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.material3.ColorProviders
import emilka.myring.ui.theme.Background
import emilka.myring.ui.theme.DarkBackground
import emilka.myring.ui.theme.DarkOnBackground
import emilka.myring.ui.theme.DarkOnPrimary
import emilka.myring.ui.theme.DarkOnSurface
import emilka.myring.ui.theme.DarkOnSurfaceVariant
import emilka.myring.ui.theme.DarkOutline
import emilka.myring.ui.theme.DarkPrimary
import emilka.myring.ui.theme.DarkPrimaryContainer
import emilka.myring.ui.theme.DarkSecondary
import emilka.myring.ui.theme.DarkSurface
import emilka.myring.ui.theme.DarkSurfaceVariant
import emilka.myring.ui.theme.OnBackground
import emilka.myring.ui.theme.OnPrimary
import emilka.myring.ui.theme.OnSurface
import emilka.myring.ui.theme.Primary
import emilka.myring.ui.theme.PrimaryContainer
import emilka.myring.ui.theme.Secondary
import emilka.myring.ui.theme.Surface

/**
 * Motyw widgetu Glance — Material3 ColorProviders (day/night),
 * jak w dokumentacji Google (Clock / Weather style).
 */
object MyringGlanceColorScheme {
    private val light = lightColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnSurface,
        secondary = Secondary,
        onSecondary = OnPrimary,
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = Color(0xFFE8E6F2),
        onSurfaceVariant = Color(0xFF4A4758),
        outline = Color(0xFF7A778A),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
    )

    private val dark = darkColorScheme(
        primary = DarkPrimary,
        onPrimary = DarkOnPrimary,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnSurface,
        secondary = DarkSecondary,
        onSecondary = DarkOnPrimary,
        background = DarkBackground,
        onBackground = DarkOnBackground,
        surface = DarkSurface,
        onSurface = DarkOnSurface,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = DarkOnSurfaceVariant,
        outline = DarkOutline,
        error = Color(0xFFFF8A80),
        onError = Color(0xFF40000A),
    )

    val colors = ColorProviders(light = light, dark = dark)
}
