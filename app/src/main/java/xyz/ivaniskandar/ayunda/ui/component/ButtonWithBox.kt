package xyz.ivaniskandar.ayunda.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ButtonWithBox(
    text: String,
    boxContent: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = ContentPadding) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center, content = boxContent)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
    }
}

private val ContentPadding = PaddingValues(
    start = 12.dp,
    top = 8.dp,
    end = 16.dp,
    bottom = 8.dp
)