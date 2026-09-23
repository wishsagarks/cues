package com.cues.app.runtime

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.cues.app.CuesApplication
import com.cues.core.model.AudioKind
import com.cues.core.model.ContextSource
import com.cues.core.model.ContextValue
import com.cues.core.model.EventKind
import com.cues.core.model.Routine
import com.cues.core.model.TriggerEvent
import com.cues.core.ports.ListenerHealth
import com.cues.core.ports.SignalAdapter

/** Audio routes are observable without a dangerous runtime permission. */
class AudioOutputAdapter(private val context: Context) : SignalAdapter {
    override val key = "audio-output"
    private var callback: AudioDeviceCallback? = null
    private var detail: String? = "Live only while the Cues process is alive."

    override fun start(armed: List<Routine>) {
        if (callback != null) return
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: run { detail = "AudioManager unavailable"; return }
        callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) = addedDevices.forEach { dispatch(EventKind.AUDIO_OUTPUT_ADDED, it) }
            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) = removedDevices.forEach { dispatch(EventKind.AUDIO_OUTPUT_REMOVED, it) }
        }
        manager.registerAudioDeviceCallback(requireNotNull(callback), null)
        detail = "Live only while the Cues process is alive."
    }

    override fun stop() {
        val current = callback ?: return
        (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.unregisterAudioDeviceCallback(current)
        callback = null
    }

    override fun health() = ListenerHealth(key, callback != null, detail)

    private fun dispatch(kind: EventKind, device: AudioDeviceInfo) {
        val audioKind = device.toAudioKind()
        val now = System.currentTimeMillis()
        val app = context.applicationContext as CuesApplication
        app.monitoring.recordEvent(key, now)
        GraceScheduler.apply(app, app.cueService.onDeviceEvent(
            TriggerEvent(kind, now, deviceId = audioKind.name),
            charging = Readings.charging(app, now),
            batteryPercent = Readings.batteryPercent(app, now),
            audioOutputs = currentOutputs(app, now),
        ))
    }

    companion object {
        fun currentOutputs(context: Context, atMillis: Long): ContextValue<Set<AudioKind>> {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return ContextValue.Unknown(com.cues.core.model.UnknownReason.ADAPTER_UNAVAILABLE, ContextSource.AUDIO_MANAGER)
            val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.toAudioKind() }.toSet()
            return ContextValue.Known(outputs, ContextSource.AUDIO_MANAGER, atMillis)
        }

        private fun AudioDeviceInfo.toAudioKind(): AudioKind = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> AudioKind.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioKind.BLUETOOTH
            else -> AudioKind.ANY
        }
    }
}
