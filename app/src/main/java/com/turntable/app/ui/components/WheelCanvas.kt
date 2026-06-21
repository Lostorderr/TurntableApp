package com.turntable.app.ui.components

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import com.turntable.app.data.model.SpinResult
import com.turntable.app.data.model.TurntableSegmentEntity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val wheelColors = listOf(
    Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFA855F7), Color(0xFFD946EF),
    Color(0xFFEC4899), Color(0xFFF43F5E), Color(0xFFF97316), Color(0xFFEAB308),
    Color(0xFF22C55E), Color(0xFF14B8A6), Color(0xFF06B6D4), Color(0xFF3B82F6),
    Color(0xFFEF4444), Color(0xFF84CC16), Color(0xFFF59E0B), Color(0xFF10B981)
)

@Composable
fun WheelCanvas(
    segments: List<TurntableSegmentEntity>,
    isSpinning: Boolean,
    targetResult: SpinResult?,
    onSpinEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var displayAngle by remember { mutableFloatStateOf(0f) }

    Log.d("Turntable", "WheelCanvas render segments=${segments.size} spinning=$isSpinning result=${targetResult?.segmentName}")

    // Animate rotation when spinning — spring guarantees zero velocity at end
    LaunchedEffect(isSpinning, targetResult) {
        if (isSpinning && targetResult != null && segments.isNotEmpty()) {
            val totalWeight = segments.sumOf { it.weight }
            var targetIndex = 0
            var cumulative = 0
            for ((i, seg) in segments.withIndex()) {
                cumulative += seg.weight
                if (targetResult.segmentId == seg.id || (targetResult.segmentId == 0L && i == 0)) {
                    targetIndex = i
                    break
                }
            }

            cumulative = 0
            for (i in 0 until targetIndex) cumulative += segments[i].weight
            val segmentWeightD = segments[targetIndex].weight.toDouble()
            val sliceAngleD = segmentWeightD / totalWeight * 2.0 * PI
            val segCenterAngle = (cumulative.toDouble() + segmentWeightD / 2.0) / totalWeight * 2.0 * PI
            val jitter = (Math.random() - 0.5) * sliceAngleD * 0.96
            val targetSegAngle = segCenterAngle + jitter

            val startAngle = rotationAngle
            val startMod: Double = startAngle.toDouble() % (2.0 * PI)
            var rotation: Double = (-targetSegAngle) - startMod
            if (rotation > 0) rotation -= 2.0 * PI
            val minTurns = -((5 + (Math.random() * 3).toInt()) * 2.0 * PI)
            while (rotation > minTurns) {
                rotation -= 2.0 * PI
            }
            val targetAngle = startAngle + rotation

            val durationNanos = 3_000_000_000L
            val startTimeNanos = withFrameNanos { it }
            var finished = false
            while (!finished) {
                val elapsed = withFrameNanos { it } - startTimeNanos
                val t = (elapsed.toDouble() / durationNanos).coerceIn(0.0, 1.0)
                // ease-out cubic: 1-(1-t)³ → f'(0)=3 fast start, f'(1)=0 smooth stop
                val eased = 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)
                displayAngle = (startAngle + (targetAngle - startAngle) * eased).toFloat()
                finished = elapsed >= durationNanos
            }
            rotationAngle = targetAngle.toFloat()
            kotlinx.coroutines.delay(150)
            onSpinEnd()
        }
    }

    val angle = if (isSpinning) displayAngle else rotationAngle

    val scheme = MaterialTheme.colorScheme

    // Cached Paint and Path objects to avoid per-frame allocation
    val segmentTextPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    val centerPaint = remember(scheme) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(255,
                (scheme.onBackground.red * 255).toInt(),
                (scheme.onBackground.green * 255).toInt(),
                (scheme.onBackground.blue * 255).toInt())
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = 40f
            isFakeBoldText = true
        }
    }
    val pointerPath = remember { Path() }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val radius = size.minDimension / 2 * 0.88f
            val cx = size.width / 2
            val cy = size.height / 2

            if (segments.isEmpty()) {
                drawCircle(
                    color = scheme.surfaceVariant,
                    radius = radius,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = scheme.outline,
                    radius = radius,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
                return@Canvas
            }

            val totalWeight = segments.sumOf { it.weight }

            rotate(degrees = Math.toDegrees(angle.toDouble()).toFloat(), pivot = Offset(cx, cy)) {
                var startAngle = -PI / 2
                for ((i, seg) in segments.withIndex()) {
                    val sliceAngle = (seg.weight.toDouble() / totalWeight * 2.0 * PI).toFloat()

                    drawArc(
                        color = wheelColors[i % wheelColors.size],
                        startAngle = Math.toDegrees(startAngle).toFloat(),
                        sweepAngle = Math.toDegrees(sliceAngle.toDouble()).toFloat(),
                        useCenter = true,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2, radius * 2)
                    )

                    drawArc(
                        color = Color.White.copy(alpha = 0.15f),
                        startAngle = Math.toDegrees(startAngle).toFloat(),
                        sweepAngle = Math.toDegrees(sliceAngle.toDouble()).toFloat(),
                        useCenter = true,
                        topLeft = Offset(cx - radius, cy - radius),
                        size = Size(radius * 2, radius * 2),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                    )

                    // Clip to this segment's wedge + draw text radially (center → outward)
                    val nc = drawContext.canvas.nativeCanvas
                    nc.save()

                    // Wedge clip path
                    val clipDeg = Math.toDegrees(startAngle).toFloat()
                    val sweepDeg = Math.toDegrees(sliceAngle.toDouble()).toFloat()
                    nc.clipPath(android.graphics.Path().apply {
                        moveTo(cx, cy)
                        lineTo(
                            cx + radius * cos(startAngle.toDouble()).toFloat(),
                            cy + radius * sin(startAngle.toDouble()).toFloat()
                        )
                        arcTo(cx - radius, cy - radius, cx + radius, cy + radius, clipDeg, sweepDeg, false)
                        close()
                    })

                    // Translate + rotate so text sits on radius, reading center → outward
                    val midAngle = startAngle + sliceAngle / 2
                    val textRadius = radius * 0.62f
                    val tx = cx + textRadius * cos(midAngle)
                    val ty = cy + textRadius * sin(midAngle)

                    nc.save()
                    nc.translate(tx.toFloat(), ty.toFloat())
                    nc.rotate(Math.toDegrees(midAngle).toFloat())

                    segmentTextPaint.textSize = 32f
                    segmentTextPaint.textAlign = android.graphics.Paint.Align.CENTER

                    val name = seg.name
                    if (name.length <= 4) {
                        nc.drawText(name, 0f, segmentTextPaint.textSize / 3f, segmentTextPaint)
                    } else {
                        val lineH = segmentTextPaint.textSize * 1.25f
                        val splitIdx = name.length / 2
                        nc.drawText(name.substring(0, splitIdx), 0f, -lineH / 2 + segmentTextPaint.textSize / 3f, segmentTextPaint)
                        nc.drawText(name.substring(splitIdx), 0f, lineH / 2 + segmentTextPaint.textSize / 3f, segmentTextPaint)
                    }

                    nc.restore() // undo translate+rotate
                    nc.restore() // undo clip

                    startAngle += sliceAngle
                }
            }

            // Center circle
            drawCircle(
                color = scheme.background,
                radius = 50f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = scheme.primary,
                radius = 50f,
                center = Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
            )
            drawContext.canvas.nativeCanvas.drawText("GO", cx, cy + 14f, centerPaint)

            // Pointer triangle at top of wheel (reuse cached path)
            pointerPath.rewind()
            val pointerBaseY = cy - radius
            val pointerSize = 16f
            pointerPath.apply {
                moveTo(cx - pointerSize, pointerBaseY - pointerSize * 1.2f)
                lineTo(cx + pointerSize, pointerBaseY - pointerSize * 1.2f)
                lineTo(cx, pointerBaseY + 4f)
                close()
            }
            drawPath(pointerPath, color = Color(0xFFF59E0B))
        }
    }
}
