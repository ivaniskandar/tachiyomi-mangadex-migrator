package xyz.ivaniskandar.ayunda.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Card with border and no elevation
 */
@Composable
fun FlatCard(content: @Composable () -> Unit) {
    Card(
        border = BorderStroke(1.dp, color = MaterialTheme.colors.onSurface.copy(alpha = 0.12F)),
        elevation = 0.dp,
        content = content
    )
}
