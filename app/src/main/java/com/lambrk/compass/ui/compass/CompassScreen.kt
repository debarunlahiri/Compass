package com.lambrk.compass.ui.compass

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambrk.compass.CompassViewModel
import com.lambrk.compass.HeadingState
import com.lambrk.compass.LevelState
import com.lambrk.compass.data.ThemeMode
import com.lambrk.compass.sensor.CardinalCrossingDetector
import com.lambrk.compass.sensor.CompassMath
import com.lambrk.compass.ui.theme.LocalCompassAccents
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassScreen(
    viewModel: CompassViewModel,
    onOpenSettings: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val heading by viewModel.heading.collectAsStateWithLifecycle()
    val level by viewModel.level.collectAsStateWithLifecycle()
    val declination by viewModel.declination.collectAsStateWithLifecycle()
    val availability = viewModel.availability

    // Only listen to the light sensor here when the reading is actually shown.
    val showAmbient = settings.themeMode == ThemeMode.AMBIENT && availability.light
    val lux = if (showAmbient) viewModel.lux.collectAsStateWithLifecycle().value else null
    val ambientDark = viewModel.ambientDark.collectAsStateWithLifecycle().value

    // Haptic tick when sweeping past N / E / S / W.
    val view = LocalView.current
    val crossing = remember { CardinalCrossingDetector() }
    heading?.let { h ->
        SideEffect {
            if (crossing.onHeading(h.degrees) && settings.hapticFeedback) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Compass", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val landscape = maxWidth > maxHeight
            // Read here: layout scopes block implicit access from nested Row/Column lambdas.
            val dialHeight = maxHeight - 16.dp
            val chips: @Composable () -> Unit = {
                InfoChips(
                    heading = heading,
                    trueNorthRequested = settings.trueNorth,
                    declination = declination?.declination,
                    showAmbient = showAmbient,
                    lux = lux,
                    ambientDark = ambientDark,
                )
            }

            if (!availability.canShowHeading) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    NoCompassSensor()
                    if (settings.showLevel) level?.let { LevelCard(it) }
                }
            } else if (landscape) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompassDial(
                        heading = heading?.degrees ?: 0f,
                        level = level,
                        modifier = Modifier.height(dialHeight),
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HeadingReadout(heading, Alignment.Start)
                        CalibrationBanner(heading)
                        chips()
                        level?.let { LevelCard(it) }
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    HeadingReadout(heading, Alignment.CenterHorizontally)
                    CalibrationBanner(heading)
                    CompassDial(
                        heading = heading?.degrees ?: 0f,
                        level = level,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .widthIn(max = 420.dp),
                    )
                    chips()
                    level?.let { LevelCard(it, Modifier.widthIn(max = 520.dp)) }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HeadingReadout(heading: HeadingState?, alignment: Alignment.Horizontal) {
    Column(horizontalAlignment = alignment) {
        val degrees = heading?.degrees?.roundToInt()?.rem(360)
        Text(
            text = if (degrees != null) "$degrees°" else "—",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = heading?.let { CompassMath.cardinalName(it.degrees).uppercase(Locale.getDefault()) }
                ?: "WAITING FOR SENSOR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CalibrationBanner(heading: HeadingState?) {
    val accents = LocalCompassAccents.current
    AnimatedVisibility(
        visible = heading?.accuracy?.needsCalibration == true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = accents.warning.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = accents.warning)
                Column {
                    Text("Compass needs calibration", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Move your phone in a figure-8 a few times, away from metal and magnets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InfoChips(
    heading: HeadingState?,
    trueNorthRequested: Boolean,
    declination: Float?,
    showAmbient: Boolean,
    lux: Float?,
    ambientDark: Boolean?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val northLabel = when {
            heading?.isTrueNorth == true && declination != null ->
                "True north · ${formatSigned(declination)} decl."

            trueNorthRequested -> "Magnetic north · locating…"
            else -> "Magnetic north"
        }
        InfoChip(
            icon = if (heading?.isTrueNorth == true) Icons.Rounded.MyLocation else Icons.Rounded.Explore,
            text = northLabel,
        )
        if (showAmbient) {
            val luxText = lux?.let { "${it.roundToInt()} lx" } ?: "— lx"
            val modeText = when (ambientDark) {
                true -> "Dark"
                false -> "Light"
                null -> "Auto"
            }
            InfoChip(
                icon = if (ambientDark == true) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                text = "$luxText · $modeText",
            )
        }
    }
}

@Composable
private fun InfoChip(icon: ImageVector, text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun LevelCard(level: LevelState, modifier: Modifier = Modifier) {
    val accents = LocalCompassAccents.current
    val statusColor by animateColorAsState(
        when {
            !level.isFlatEnough -> MaterialTheme.colorScheme.onSurfaceVariant
            level.isLevel -> accents.level
            else -> MaterialTheme.colorScheme.primary
        },
        label = "levelStatus",
    )
    val statusText = when {
        !level.isFlatEnough -> "Lay phone flat"
        level.isLevel -> "Level"
        else -> "Tilted"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LEVEL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier
                        .background(statusColor.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(statusColor, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(statusText, style = MaterialTheme.typography.labelLarge, color = statusColor)
                }
            }
            VialRow("Left / right", level.roll, level.isLevel)
            VialRow("Front / back", level.pitch, level.isLevel)
        }
    }
}

@Composable
private fun ColumnScope.VialRow(label: String, degrees: Float, isLevel: Boolean) {
    val accents = LocalCompassAccents.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatSigned(degrees),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
    Vial(
        degrees = degrees,
        good = abs(degrees) <= 1f && isLevel,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        markColor = MaterialTheme.colorScheme.outline,
        bubbleColor = if (abs(degrees) <= 1f) accents.level else MaterialTheme.colorScheme.primary,
    )
}

/** A spirit-level vial: the bubble moves toward the raised side, full scale at ±10°. */
@Composable
private fun Vial(
    degrees: Float,
    good: Boolean,
    trackColor: Color,
    markColor: Color,
    bubbleColor: Color,
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(22.dp),
    ) {
        val h = size.height
        drawRoundRect(trackColor, cornerRadius = CornerRadius(h / 2f))
        val bubbleW = h * 1.8f
        val travel = (size.width - bubbleW) / 2f
        val mid = size.width / 2f
        val gap = bubbleW / 2f + 3.dp.toPx()
        drawLine(markColor, Offset(mid - gap, 3.dp.toPx()), Offset(mid - gap, h - 3.dp.toPx()), 1.5.dp.toPx())
        drawLine(markColor, Offset(mid + gap, 3.dp.toPx()), Offset(mid + gap, h - 3.dp.toPx()), 1.5.dp.toPx())
        val x = mid + (degrees / 10f).coerceIn(-1f, 1f) * travel
        val inset = 3.dp.toPx()
        drawRoundRect(
            color = bubbleColor.copy(alpha = if (good) 0.55f else 0.3f),
            topLeft = Offset(x - bubbleW / 2f, inset),
            size = androidx.compose.ui.geometry.Size(bubbleW, h - inset * 2),
            cornerRadius = CornerRadius((h - inset * 2) / 2f),
        )
        drawRoundRect(
            color = bubbleColor,
            topLeft = Offset(x - bubbleW / 2f, inset),
            size = androidx.compose.ui.geometry.Size(bubbleW, h - inset * 2),
            cornerRadius = CornerRadius((h - inset * 2) / 2f),
            style = Stroke(1.5.dp.toPx()),
        )
    }
}

@Composable
private fun NoCompassSensor() {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.Explore,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("No compass sensor", style = MaterialTheme.typography.titleMedium)
            Text(
                "This device doesn't have a magnetometer, so it can't show a heading. " +
                    "Check Settings → Sensors to see what's available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun formatSigned(degrees: Float): String =
    String.format(Locale.getDefault(), "%+.1f°", degrees)
