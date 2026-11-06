package com.lambrk.compass.ui.settings

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lambrk.compass.CompassViewModel
import com.lambrk.compass.R
import com.lambrk.compass.data.AppSettings
import com.lambrk.compass.data.ThemeMode
import com.lambrk.compass.sensor.CompassMath
import com.lambrk.compass.sensor.SensorAccuracy
import com.lambrk.compass.ui.theme.LocalCompassAccents
import com.lambrk.compass.ui.theme.dynamicColorSupported
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CompassViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lux by viewModel.lux.collectAsStateWithLifecycle()
    val ambientDark by viewModel.ambientDark.collectAsStateWithLifecycle()
    val heading by viewModel.heading.collectAsStateWithLifecycle()
    val declination by viewModel.declination.collectAsStateWithLifecycle()
    val availability = viewModel.availability

    var locationDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        locationDenied = !granted
        if (granted) {
            viewModel.updateSettings { it.copy(trueNorth = true) }
            viewModel.refreshLocation()
        }
    }

    // If True north is on but permission was revoked in system settings, turn it back off.
    LaunchedEffect(settings.trueNorth) {
        if (settings.trueNorth && !viewModel.hasLocationPermission()) {
            viewModel.updateSettings { it.copy(trueNorth = false) }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Appearance ──────────────────────────────────────────────
            item { SectionHeader("Appearance") }
            item {
                SettingsGroup {
                    ThemeModePicker(
                        selected = settings.themeMode,
                        ambientAvailable = availability.light,
                        onSelect = { mode -> viewModel.updateSettings { it.copy(themeMode = mode) } },
                    )
                    AnimatedVisibility(
                        visible = settings.themeMode == ThemeMode.AMBIENT && availability.light,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column {
                            GroupDivider()
                            LuxThresholdSetting(
                                threshold = settings.darkLuxThreshold,
                                currentLux = lux,
                                isDark = ambientDark,
                                onThresholdChange = { value ->
                                    viewModel.updateSettings { it.copy(darkLuxThreshold = value) }
                                },
                            )
                        }
                    }
                    GroupDivider()
                    SwitchSetting(
                        icon = Icons.Rounded.Palette,
                        title = "Dynamic color",
                        subtitle = if (dynamicColorSupported) {
                            "Use colors from your wallpaper"
                        } else {
                            "Requires Android 12 or newer"
                        },
                        checked = settings.dynamicColor && dynamicColorSupported,
                        enabled = dynamicColorSupported,
                        onCheckedChange = { on -> viewModel.updateSettings { it.copy(dynamicColor = on) } },
                    )
                }
            }

            // ── Compass ─────────────────────────────────────────────────
            item { SectionHeader("Compass") }
            item {
                SettingsGroup {
                    SwitchSetting(
                        icon = Icons.Rounded.MyLocation,
                        title = "True north",
                        subtitle = when {
                            locationDenied -> "Location permission denied — using magnetic north"
                            settings.trueNorth && declination != null ->
                                "Declination here: ${
                                    String.format(Locale.getDefault(), "%+.1f°", declination!!.declination)
                                }"

                            settings.trueNorth -> "Finding your approximate location…"
                            else -> "Correct for magnetic declination (uses approximate location once)"
                        },
                        checked = settings.trueNorth,
                        enabled = availability.canShowHeading,
                        onCheckedChange = { on ->
                            if (!on) {
                                viewModel.updateSettings { it.copy(trueNorth = false) }
                            } else if (viewModel.hasLocationPermission()) {
                                locationDenied = false
                                viewModel.updateSettings { it.copy(trueNorth = true) }
                                viewModel.refreshLocation()
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                    ),
                                )
                            }
                        },
                    )
                    GroupDivider()
                    SliderSetting(
                        icon = Icons.Rounded.Tune,
                        title = "Smoothing",
                        value = settings.smoothing,
                        startLabel = "Responsive",
                        endLabel = "Smooth",
                        enabled = availability.canShowHeading,
                        onValueCommitted = { v -> viewModel.updateSettings { it.copy(smoothing = v) } },
                    )
                    GroupDivider()
                    SwitchSetting(
                        icon = Icons.Rounded.Vibration,
                        title = "Haptic feedback",
                        subtitle = "Tick when passing N, E, S and W",
                        checked = settings.hapticFeedback,
                        enabled = availability.canShowHeading,
                        onCheckedChange = { on -> viewModel.updateSettings { it.copy(hapticFeedback = on) } },
                    )
                }
            }

            // ── Level & display ─────────────────────────────────────────
            item { SectionHeader("Level & display") }
            item {
                SettingsGroup {
                    SwitchSetting(
                        icon = Icons.Rounded.Straighten,
                        title = "Show level",
                        subtitle = if (availability.canShowLevel) {
                            "Bubble level in the dial and tilt readout"
                        } else {
                            "No accelerometer on this device"
                        },
                        checked = settings.showLevel && availability.canShowLevel,
                        enabled = availability.canShowLevel,
                        onCheckedChange = { on -> viewModel.updateSettings { it.copy(showLevel = on) } },
                    )
                    GroupDivider()
                    SwitchSetting(
                        icon = Icons.Rounded.ScreenLockPortrait,
                        title = "Keep screen on",
                        subtitle = "Don't let the screen sleep while the compass is open",
                        checked = settings.keepScreenOn,
                        onCheckedChange = { on -> viewModel.updateSettings { it.copy(keepScreenOn = on) } },
                    )
                }
            }

            // ── Sensor check ────────────────────────────────────────────
            item { SectionHeader("Sensor check") }
            item {
                SettingsGroup {
                    SensorRow("Magnetometer", "Needed for the compass", availability.magnetometer)
                    GroupDivider()
                    SensorRow("Accelerometer", "Needed for the level", availability.accelerometer)
                    GroupDivider()
                    SensorRow(
                        "Rotation vector",
                        "Fused sensor for a steadier heading",
                        availability.rotationVector,
                        optional = true,
                    )
                    GroupDivider()
                    SensorRow("Gyroscope", "Improves stability", availability.gyroscope, optional = true)
                    GroupDivider()
                    SensorRow("Gravity", "Cleaner level readings", availability.gravity, optional = true)
                    GroupDivider()
                    SensorRow(
                        "Ambient light",
                        "Needed for automatic light/dark theme",
                        availability.light,
                        optional = true,
                    )
                    GroupDivider()
                    AccuracyRow(heading?.accuracy, availability.canShowHeading)
                }
            }

            item {
                SectionHeader(stringResource(R.string.privacy_and_support))
            }
            item {
                val supportEmail = stringResource(R.string.support_email)
                SettingsGroup {
                    SettingsLink(
                        icon = Icons.Rounded.PrivacyTip,
                        title = stringResource(R.string.privacy_policy),
                        uri = stringResource(R.string.privacy_policy_url),
                    )
                    GroupDivider()
                    SettingsLink(
                        icon = Icons.Rounded.Mail,
                        title = stringResource(R.string.contact_support),
                        uri = "mailto:$supportEmail",
                        subtitle = supportEmail,
                    )
                }
            }

            item {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    TextButton(onClick = {
                        locationDenied = false
                        viewModel.resetSettings()
                    }) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null)
                        Text("  Reset to defaults")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsLink(
    icon: ImageVector,
    title: String,
    uri: String,
    subtitle: String? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.clickable {
            try {
                uriHandler.openUri(uri)
            } catch (_: IllegalArgumentException) {
                Toast.makeText(context, R.string.no_app_to_open_link, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun SettingIcon(icon: ImageVector, enabled: Boolean = true) {
    Icon(
        icon,
        contentDescription = null,
        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(24.dp),
    )
}

@Composable
private fun SwitchSetting(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingIcon(icon, enabled)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModePicker(
    selected: ThemeMode,
    ambientAvailable: Boolean,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.AMBIENT to "Auto",
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark",
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingIcon(Icons.Rounded.BrightnessAuto)
            Column {
                Text("Theme", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (selected) {
                        ThemeMode.AMBIENT -> if (ambientAvailable) {
                            "Switches with the light around you"
                        } else {
                            "No light sensor — following system instead"
                        }

                        ThemeMode.SYSTEM -> "Follows the system dark theme setting"
                        ThemeMode.LIGHT -> "Always light"
                        ThemeMode.DARK -> "Always dark"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SingleChoiceSegmentedButtonRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    enabled = mode != ThemeMode.AMBIENT || ambientAvailable,
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun LuxThresholdSetting(
    threshold: Float,
    currentLux: Float?,
    isDark: Boolean?,
    onThresholdChange: (Float) -> Unit,
) {
    val min = AppSettings.MIN_LUX_THRESHOLD
    val max = AppSettings.MAX_LUX_THRESHOLD
    // Keep slider position locally while dragging; persist only when the drag ends.
    var position by remember(threshold) { mutableFloatStateOf(CompassMath.luxToSlider(threshold, min, max)) }
    val shownThreshold = CompassMath.sliderToLux(position, min, max)

    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingIcon(Icons.Rounded.WbSunny)
            Column(Modifier.weight(1f)) {
                Text("Go dark below ${shownThreshold.roundToInt()} lx", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Right now: ${currentLux?.roundToInt()?.let { "$it lx" } ?: "waiting for sensor…"}" +
                        when (isDark) {
                            true -> " · dark theme"
                            false -> " · light theme"
                            null -> ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onThresholdChange(CompassMath.sliderToLux(position, min, max)) },
            modifier = Modifier.padding(start = 40.dp, top = 4.dp),
        )
        Row(Modifier.padding(start = 40.dp)) {
            Text(
                "Dim room",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Bright room",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (currentLux != null) {
            TextButton(
                onClick = { onThresholdChange(currentLux.coerceIn(min, max)) },
                modifier = Modifier.padding(start = 28.dp),
            ) {
                Text("Use current light level")
            }
        }
    }
}

@Composable
private fun SliderSetting(
    icon: ImageVector,
    title: String,
    value: Float,
    startLabel: String,
    endLabel: String,
    enabled: Boolean,
    onValueCommitted: (Float) -> Unit,
) {
    var position by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingIcon(icon, enabled)
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                "${(position * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onValueCommitted(position) },
            enabled = enabled,
            modifier = Modifier.padding(start = 40.dp, top = 4.dp),
        )
        Row(Modifier.padding(start = 40.dp)) {
            Text(
                startLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(endLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SensorRow(name: String, purpose: String, available: Boolean, optional: Boolean = false) {
    val accents = LocalCompassAccents.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            if (available) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = if (available) "Available" else "Not available",
            tint = when {
                available -> accents.level
                optional -> MaterialTheme.colorScheme.outline
                else -> accents.north
            },
        )
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            when {
                available -> "Available"
                optional -> "Not present"
                else -> "Missing"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccuracyRow(accuracy: SensorAccuracy?, hasCompass: Boolean) {
    val accents = LocalCompassAccents.current
    val (label, color) = when {
        !hasCompass -> "Unavailable" to MaterialTheme.colorScheme.outline
        accuracy == SensorAccuracy.HIGH -> "High" to accents.level
        accuracy == SensorAccuracy.MEDIUM -> "Medium" to accents.level
        accuracy == SensorAccuracy.LOW -> "Low — calibrate" to accents.warning
        accuracy == SensorAccuracy.UNRELIABLE -> "Unreliable — calibrate" to accents.north
        else -> "Checking…" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingIcon(Icons.Rounded.Explore, hasCompass)
        Column(Modifier.weight(1f)) {
            Text("Compass accuracy", style = MaterialTheme.typography.bodyLarge)
            Text(
                "To calibrate, move the phone in a figure-8",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
