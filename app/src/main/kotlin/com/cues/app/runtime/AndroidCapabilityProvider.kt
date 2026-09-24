package com.cues.app.runtime

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.cues.core.model.Capability
import com.cues.core.ports.CapabilityProvider

/**
 * Reads live OS permission and access state.
 *
 * [com.cues.core.approval.Approvals.arm] calls this at the moment of arming,
 * not at review time, so a permission granted during review and revoked
 * before the user taps "Approve & arm" is caught rather than assumed.
 *
 * WRITTEN AGAINST REAL APIs, VERIFIED ON NOTHING — same caveat as
 * [AndroidActionExecutor]. `canScheduleExactAlarms()` in particular is worth
 * confirming early: some OEM skins pre-grant it, others don't, and that
 * difference changes what the review screen has to ask for.
 */
class AndroidCapabilityProvider(private val context: Context) : CapabilityProvider {

    override fun granted(): Set<Capability> = buildSet {
        if (hasBluetoothConnect()) add(Capability.BLUETOOTH_CONNECT)
        if (notificationPolicyAccessGranted()) add(Capability.NOTIFICATION_POLICY_ACCESS)
        if (postNotificationsGranted()) add(Capability.POST_NOTIFICATIONS)
        if (exactAlarmGranted()) add(Capability.EXACT_ALARM)
        // ACCESS_NETWORK_STATE is a normal install-time permission; the Wi-Fi adapter reads only transport state.
        add(Capability.NETWORK_STATE)
        // Reading current charging state needs no special permission on
        // modern Android; granted whenever the BatteryManager service exists.
        add(Capability.BATTERY_STATE)
        if (accessibilityServiceEnabled()) add(Capability.ACCESSIBILITY_SERVICE)
        if (readCalendarGranted()) add(Capability.READ_CALENDAR)
    }

    private fun readCalendarGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Whether "Cues: iQOO utility bindings" is enabled in system
     * Accessibility settings — read live from [Settings.Secure], the same
     * source the system Accessibility screen itself reads, rather than from
     * [CuesAccessibilityService.isRunning] alone: the service can be bound
     * and then killed by the OS well before the user disables it in
     * Settings, and this is meant to answer "did the user grant this",
     * not "is a process currently alive".
     */
    private fun accessibilityServiceEnabled(): Boolean {
        val expected = "${context.packageName}/${CuesAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        // No .asSequence(): SimpleStringSplitter implements both Iterable<String>
        // and Iterator<String>, which makes that call an ambiguous overload.
        // Iterating it directly with .any() needs no such choice.
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
            .any { it.equals(expected, ignoreCase = true) }
    }

    private fun hasBluetoothConnect(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Pre-Android 12, plain BLUETOOTH access covers this and is a
            // normal (install-time) permission — no adapter needed to know
            // it is available.
            return true
        }
        val bluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return bluetoothAdapter != null &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun notificationPolicyAccessGranted(): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.isNotificationPolicyAccessGranted
    }

    private fun postNotificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun exactAlarmGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return manager.canScheduleExactAlarms()
    }
}
