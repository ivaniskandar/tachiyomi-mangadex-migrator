package xyz.ivaniskandar.ayunda

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp

@Composable
fun AyundaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val accent = colorResource(id = R.color.ic_launcher_background)
    val colors = if (darkTheme) {
        darkColors(
            primary = accent,
            primaryVariant = accent,
            secondary = accent,
            secondaryVariant = accent
        )
    } else {
        lightColors(
            primary = accent,
            primaryVariant = accent,
            secondary = accent,
            secondaryVariant = accent
        )
    }
    MaterialTheme(colors = colors, shapes = Shapes(medium = RoundedCornerShape(8.dp)), content = content)
}