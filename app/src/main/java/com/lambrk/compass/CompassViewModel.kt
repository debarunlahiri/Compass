package com.lambrk.compass

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lambrk.compass.data.AppSettings
import com.lambrk.compass.data.DeclinationInfo
import com.lambrk.compass.data.LocationRepository
import com.lambrk.compass.data.SettingsRepository
import com.lambrk.compass.data.ThemeMode
import com.lambrk.compass.sensor.AmbientThemeController
import com.lambrk.compass.sensor.CompassMath
import com.lambrk.compass.sensor.SensorAccuracy
import com.lambrk.compass.sensor.SensorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class HeadingState(
    /** Smoothed heading in degrees [0, 360). True heading when [isTrueNorth], else magnetic. */
    val degrees: Float,
    val accuracy: SensorAccuracy,
    val isTrueNorth: Boolean,
)

data class LevelState(
    /** Left/right tilt in degrees; positive = right edge raised. */
    val roll: Float,
    /** Front/back tilt in degrees; positive = top edge raised. */
    val pitch: Float,
    val isLevel: Boolean,
    /** False when the phone is held upright and a flat bubble level isn't meaningful. */
    val isFlatEnough: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CompassViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepository = SettingsRepository(app)
    private val sensors = SensorRepository(app)
    private val locations = LocationRepository(app)

    val availability = sensors.availability

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val locationRefresh = MutableStateFlow(0)

    /** Magnetic declination at the user's location; only looked up while "true north" is on. */
    val declination: StateFlow<DeclinationInfo?> =
        combine(settings.map { it.trueNorth }.distinctUntilChanged(), locationRefresh) { on, _ -> on }
            .transformLatest { on -> emit(if (on) locations.declination() else null) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val heading: StateFlow<HeadingState?> = flow {
        var smoothed: Float? = null
        sensors.heading().collect { sample ->
            val s = settings.value
            val decl = if (s.trueNorth) declination.value?.declination else null
            val target = CompassMath.normalize(sample.azimuth + (decl ?: 0f))
            val next = CompassMath.smoothAngle(
                smoothed, target, CompassMath.smoothingToAlpha(s.smoothing),
            )
            smoothed = next
            emit(HeadingState(next, sample.accuracy, isTrueNorth = decl != null))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val level: StateFlow<LevelState?> = settings
        .map { it.showLevel }
        .distinctUntilChanged()
        .flatMapLatest { show ->
            if (!show) flowOf<LevelState?>(null) else sensors.gravity().map { g ->
                val (roll, pitch) = CompassMath.tiltDegrees(g.x, g.y, g.z)
                val magnitude = sqrt(g.x * g.x + g.y * g.y + g.z * g.z).coerceAtLeast(1e-3f)
                LevelState(
                    roll = roll,
                    pitch = pitch,
                    isLevel = CompassMath.isLevel(roll, pitch),
                    isFlatEnough = g.z / magnitude > 0.5f,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Live ambient light reading in lux (null until the first reading / no sensor). */
    val lux: StateFlow<Float?> = sensors.light()
        .map<Float, Float?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    private val ambientController = AmbientThemeController()

    /**
     * Whether the ambient light sensor says it's dark. Null when not in ambient mode or before
     * the first reading, in which case the UI falls back to the system setting.
     */
    val ambientDark: StateFlow<Boolean?> = settings
        .map { (it.themeMode == ThemeMode.AMBIENT) to it.darkLuxThreshold }
        .distinctUntilChanged()
        .flatMapLatest { (ambient, threshold) ->
            if (!ambient || !availability.light) {
                ambientController.reset()
                flowOf<Boolean?>(null)
            } else {
                // The ticker re-evaluates even when the light sensor goes quiet, so a pending
                // switch still happens after the hold time.
                combine(lux.filterNotNull(), ticker(500L)) { reading, _ ->
                    ambientController.update(reading, threshold, SystemClock.elapsedRealtime())
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), ambientController.isDark)

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun resetSettings() {
        viewModelScope.launch { settingsRepository.reset() }
    }

    fun hasLocationPermission(): Boolean = locations.hasPermission()

    fun refreshLocation() {
        locationRefresh.value++
    }

    private fun ticker(periodMillis: Long) = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
    }
}
