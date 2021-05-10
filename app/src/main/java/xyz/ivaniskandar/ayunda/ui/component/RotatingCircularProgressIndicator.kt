package xyz.ivaniskandar.ayunda.ui.component

import androidx.annotation.FloatRange
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate

@Composable
fun RotatingCircularProgressIndicator(@FloatRange(from = 0.0, to = 1.0) progress: Float = 0F) {
    if (progress == 0F) {
        // Show indeterminate indicator
        CircularProgressIndicator()
    } else {
        // Show rotating determinate indicator
        RotatingDeterminateCircularProgressIndicator(progress = progress)
    }
}

@Composable
private fun RotatingDeterminateCircularProgressIndicator(@FloatRange(from = 0.0, to = 1.0) progress: Float) {
    val transition = rememberInfiniteTransition()
    val currentRotation by transition.animateFloat(
        initialValue = 0F,
        targetValue = 360F,
        animationSpec = infiniteRepeatable(
            animation = tween(1332, easing = LinearEasing)
        )
    )
    CircularProgressIndicator(progress = progress, modifier = Modifier.rotate(currentRotation))
}