package com.cues.app.runtime

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.SystemClock

/**
 * The "basic symbols" tab's readings: battery health, this process's CPU
 * share, GPU, and the sensor inventory.
 *
 * Every field says exactly what it measured, never more. There is no public,
 * non-root API for system-wide CPU load or GPU utilization on a stock phone,
 * so [cpuUsagePercent] is scoped to Cues' own process (labelled as such by
 * the screen that renders it) and [gpuNote] says plainly that usage isn't
 * exposed rather than inventing a number — the same rule [DiagnosticsScreen]
 * already applies to NPU eligibility and inference cost.
 */
data class DeviceHealthSnapshot(
    val batteryHealth: String,
    val batteryTempC: Float?,
    val cpuCoreCount: Int,
    val cpuAbi: String?,
    val cpuUsagePercent: Float?,
    val gpuNote: String,
    val sensorGroups: List<SensorGroup>,
)

data class SensorGroup(val label: String, val count: Int)

object DeviceHealthReadings {

    fun current(context: Context): DeviceHealthSnapshot {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return DeviceHealthSnapshot(
            batteryHealth = batteryHealthLabel(sticky?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1),
            batteryTempC = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE }
                ?.let { it / 10f },
            cpuCoreCount = Runtime.getRuntime().availableProcessors(),
            cpuAbi = Build.SUPPORTED_ABIS?.firstOrNull(),
            cpuUsagePercent = processCpuUsagePercent(),
            gpuNote = "GPU usage isn't exposed to apps without root on this build.",
            sensorGroups = sensorGroups(context),
        )
    }

    private fun batteryHealthLabel(health: Int): String = when (health) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified failure"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        else -> "Not reported"
    }

    /**
     * Average CPU time Cues' own process has consumed since it started, as a
     * share of one core. Not a system-wide figure — [DiagnosticsScreen] labels
     * it "Cues process", the same discipline as [Readings]' honest source
     * tagging.
     */
    private fun processCpuUsagePercent(): Float? {
        val cpuMillis = Process.getElapsedCpuTime()
        val startElapsed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Process.getStartElapsedRealtime()
        } else {
            return null
        }
        val wallMillis = SystemClock.elapsedRealtime() - startElapsed
        if (wallMillis <= 0) return null
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return ((cpuMillis.toFloat() / wallMillis) / cores * 100f).coerceIn(0f, 100f)
    }

    private fun sensorGroups(context: Context): List<SensorGroup> {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return emptyList()
        val all = manager.getSensorList(Sensor.TYPE_ALL)
        val motion = all.count { it.type in MOTION_TYPES }
        val position = all.count { it.type in POSITION_TYPES }
        val environment = all.count { it.type in ENVIRONMENT_TYPES }
        val other = all.size - motion - position - environment
        return listOfNotNull(
            SensorGroup("Motion", motion).takeIf { it.count > 0 },
            SensorGroup("Position", position).takeIf { it.count > 0 },
            SensorGroup("Environment", environment).takeIf { it.count > 0 },
            SensorGroup("Other", other).takeIf { it.count > 0 },
        )
    }

    private val MOTION_TYPES = setOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION,
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_SIGNIFICANT_MOTION,
        Sensor.TYPE_STEP_COUNTER,
        Sensor.TYPE_STEP_DETECTOR,
    )

    private val POSITION_TYPES = setOf(
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_ORIENTATION,
        Sensor.TYPE_PROXIMITY,
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
    )

    private val ENVIRONMENT_TYPES = setOf(
        Sensor.TYPE_LIGHT,
        Sensor.TYPE_PRESSURE,
        Sensor.TYPE_AMBIENT_TEMPERATURE,
        Sensor.TYPE_RELATIVE_HUMIDITY,
        Sensor.TYPE_TEMPERATURE,
    )
}
