package com.lambrk.compass.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lambrk.compass.LevelState
import com.lambrk.compass.sensor.CompassMath
import com.lambrk.compass.ui.theme.LocalCompassAccents
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Max tilt (degrees) that moves the bubble all the way to the edge of its ring. */
private const val BUBBLE_FULL_SCALE_DEGREES = 15f

/**
 * The compass rose. The dial rotates so that north on the dial points to real north,
 * while the lubber mark at the top shows where the phone is pointing.
 * When [level] is non-null a bubble level is drawn in the centre.
 */
@Composable
fun CompassDial(
    heading: Float,
    level: LevelState?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accents = LocalCompassAccents.current
    val textMeasurer = rememberTextMeasurer()
    val cardinalStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val numberStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)

    val description = "Heading ${heading.roundToInt()} degrees ${CompassMath.cardinalName(heading)}"

    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        val radius = size.minDimension / 2f
        val c = center

        // Face: soft radial gradient for a bit of depth.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors.surfaceContainerHigh, colors.surfaceContainer),
                center = c,
                radius = radius,
            ),
            radius = radius,
        )
        drawCircle(colors.outlineVariant, radius - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))

        rotate(-heading, pivot = c) {
            drawTicks(radius, colors.onSurface, colors.onSurfaceVariant, accents.north)

            for (deg in 30 until 360 step 30) {
                if (deg % 90 == 0) continue
                val layout = textMeasurer.measure(
                    deg.toString(),
                    numberStyle.copy(color = colors.onSurfaceVariant),
                )
                drawRadialLabel(layout, radius * 0.74f, deg.toFloat())
            }
            listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W").forEach { (deg, text) ->
                val color = if (deg == 0) accents.north else colors.onSurface
                drawRadialLabel(
                    textMeasurer.measure(text, cardinalStyle.copy(color = color)),
                    radius * 0.72f,
                    deg.toFloat(),
                )
            }

            // North needle tip just inside the tick ring.
            val tip = Path().apply {
                moveTo(c.x, c.y - radius * 0.83f)
                lineTo(c.x - radius * 0.045f, c.y - radius * 0.76f)
                lineTo(c.x + radius * 0.045f, c.y - radius * 0.76f)
                close()
            }
            drawPath(tip, accents.north)
        }

        // Fixed lubber line: where the top of the phone points.
        val lubber = Path().apply {
            moveTo(c.x, c.y - radius * 0.86f)
            lineTo(c.x - radius * 0.05f, c.y - radius * 1.0f + 2.dp.toPx())
            lineTo(c.x + radius * 0.05f, c.y - radius * 1.0f + 2.dp.toPx())
            close()
        }
        drawPath(lubber, colors.primary)

        // Faint crosshair.
        val cross = colors.outlineVariant
        drawLine(cross, Offset(c.x - radius * 0.55f, c.y), Offset(c.x + radius * 0.55f, c.y), 1.dp.toPx())
        drawLine(cross, Offset(c.x, c.y - radius * 0.55f), Offset(c.x, c.y + radius * 0.55f), 1.dp.toPx())

        if (level != null) {
            drawBubbleLevel(
                level = level,
                zoneRadius = radius * 0.3f,
                ringColor = colors.outline,
                bubbleColor = if (level.isLevel) accents.level else colors.primary,
                targetColor = if (level.isLevel) accents.level else colors.onSurfaceVariant,
                fill = colors.surfaceContainerLowest.copy(alpha = 0.6f),
            )
        } else {
            drawCircle(colors.primary, radius * 0.025f, c)
        }
    }
}

private fun DrawScope.drawTicks(
    radius: Float,
    majorColor: Color,
    minorColor: Color,
    northColor: Color,
) {
    val outer = radius * 0.95f
    for (deg in 0 until 360 step 2) {
        val major = deg % 30 == 0
        val mid = deg % 10 == 0
        val length = when {
            major -> radius * 0.085f
            mid -> radius * 0.06f
            else -> radius * 0.035f
        }
        val color = when {
            deg == 0 -> northColor
            major -> majorColor
            mid -> minorColor.copy(alpha = 0.75f)
            else -> minorColor.copy(alpha = 0.35f)
        }
        val a = Math.toRadians(deg.toDouble())
        val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
        drawLine(
            color = color,
            start = center + dir * (outer - length),
            end = center + dir * outer,
            strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** Draws text centred on the point at [degrees] on a circle, oriented with its top outward. */
private fun DrawScope.drawRadialLabel(layout: TextLayoutResult, radius: Float, degrees: Float) {
    val a = Math.toRadians(degrees.toDouble())
    val p = center + Offset(sin(a).toFloat() * radius, -cos(a).toFloat() * radius)
    rotate(degrees, pivot = p) {
        drawText(
            layout,
            topLeft = p - Offset(layout.size.width / 2f, layout.size.height / 2f),
        )
    }
}

private fun DrawScope.drawBubbleLevel(
    level: LevelState,
    zoneRadius: Float,
    ringColor: Color,
    bubbleColor: Color,
    targetColor: Color,
    fill: Color,
) {
    val c = center
    drawCircle(fill, zoneRadius, c)
    drawCircle(ringColor, zoneRadius, c, style = Stroke(1.5.dp.toPx()))
    val bubbleRadius = zoneRadius * 0.24f
    // Target ring ≈ 1° of tilt.
    drawCircle(targetColor, bubbleRadius * 1.25f, c, style = Stroke(1.5.dp.toPx()))

    // Bubble floats toward the raised side: right edge up → right, top edge up → up.
    val travel = zoneRadius - bubbleRadius
    var dx = level.roll / BUBBLE_FULL_SCALE_DEGREES * travel
    var dy = -level.pitch / BUBBLE_FULL_SCALE_DEGREES * travel
    val dist = hypot(dx, dy)
    if (dist > travel) {
        dx *= travel / dist
        dy *= travel / dist
    }
    val alpha = if (level.isFlatEnough) 0.9f else 0.3f
    val bubbleCenter = Offset(c.x + dx, c.y + dy)
    drawCircle(bubbleColor.copy(alpha = alpha * 0.35f), bubbleRadius, bubbleCenter)
    drawCircle(bubbleColor.copy(alpha = alpha), bubbleRadius, bubbleCenter, style = Stroke(2.dp.toPx()))
}
