package com.cues.app.runtime

import android.content.Context
import android.os.PowerManager
import com.cues.core.ports.DeviceAttention

/**
 * Whether someone is plausibly at the phone right now.
 *
 * [PowerManager.isInteractive] is the read: the screen is on and not in a
 * dozing/ambient-only state. `KeyguardManager.isKeyguardLocked` was
 * considered instead, but a locked screen with the display on is still
 * "someone just glanced at it", which is closer to what
 * [com.cues.core.registry.Presence.NEEDS_USER] actually needs — that a
 * background-activity-launch restriction is unlikely to apply, not that the
 * user has unlocked the device. See CLEANUP.md CL-25: this class exists, but
 * whether `isInteractive()` is the right proxy on OriginOS 7 is unverified.
 */
class AndroidDeviceAttention(private val context: Context) : DeviceAttention {
    override fun isUserPresent(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive ?: false
    }
}
