package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CircularSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 1f..180f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color.Gray.copy(alpha = 0.3f),
    centerLabel: @Composable () -> Unit = {}
) {
    var circleCenter by remember { mutableStateOf(Offset.Zero) }
    var radius by remember { mutableStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    
    // Track previous value to trigger haptic feedback on integer changes
    var lastValue by remember { mutableFloatStateOf(value) }

    val angle = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start) * 360f) - 90f

    Box(
        modifier = modifier
            .size(240.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val touchX = change.position.x
                    val touchY = change.position.y
                    
                    val newAngle = Math.toDegrees(
                        atan2(
                            (touchY - circleCenter.y).toDouble(),
                            (touchX - circleCenter.x).toDouble()
                        )
                    ).toFloat()
                    
                    var normalizedAngle = (newAngle + 90f)
                    if (normalizedAngle < 0) normalizedAngle += 360f
                    
                    val newValue = (normalizedAngle / 360f) * (valueRange.endInclusive - valueRange.start) + valueRange.start
                    val clampedValue = newValue.coerceIn(valueRange)
                    
                    // Trigger haptic feedback if the rounded integer value changes
                    if (clampedValue.roundToInt() != lastValue.roundToInt()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastValue = clampedValue
                    }
                    
                    onValueChange(clampedValue)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            val width = size.width
            val height = size.height
            circleCenter = Offset(width / 2, height / 2)
            radius = (size.minDimension / 2) - 20.dp.toPx()

            // Inactive track
            drawArc(
                color = inactiveColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(circleCenter.x - radius, circleCenter.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // Active track
            drawArc(
                color = activeColor,
                startAngle = -90f,
                sweepAngle = if (angle >= -90f) angle + 90f else angle + 450f,
                useCenter = false,
                topLeft = Offset(circleCenter.x - radius, circleCenter.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )

            // Thumb
            val thumbX = circleCenter.x + radius * cos(Math.toRadians(angle.toDouble())).toFloat()
            val thumbY = circleCenter.y + radius * sin(Math.toRadians(angle.toDouble())).toFloat()

            drawCircle(
                color = activeColor,
                radius = 16.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
            
            drawCircle(
                color = Color.White,
                radius = 6.dp.toPx(),
                center = Offset(thumbX, thumbY)
            )
        }
        
        // Centered Label
        Box(contentAlignment = Alignment.Center) {
            centerLabel()
        }
    }
}
