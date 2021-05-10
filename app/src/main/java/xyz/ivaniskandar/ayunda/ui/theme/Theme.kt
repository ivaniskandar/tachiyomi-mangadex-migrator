package xyz.ivaniskandar.ayunda.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import xyz.ivaniskandar.ayunda.R

@Composable
fun AyundaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val accent = if (darkTheme) {
        Color(0xFF748EAE)
    } else {
        Color(0xFF54759E)
    }
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