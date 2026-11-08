package com.cuso.voice.rtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

enum class VoiceRoute {
    SPEAKER,
    EARPIECE,
    BLUETOOTH,
    HEADSET,
}

class AudioRouteController(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun setRoute(route: VoiceRoute): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val type = when (route) {
                VoiceRoute.SPEAKER -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                VoiceRoute.EARPIECE -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                VoiceRoute.BLUETOOTH -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                VoiceRoute.HEADSET -> AudioDeviceInfo.TYPE_WIRED_HEADSET
            }
            val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == type }
                ?: return false
            audioManager.setCommunicationDevice(device)
        } else {
            @Suppress("DEPRECATION")
            when (route) {
                VoiceRoute.SPEAKER -> {
                    audioManager.isSpeakerphoneOn = true
                    true
                }

                VoiceRoute.EARPIECE -> {
                    audioManager.isSpeakerphoneOn = false
                    true
                }

                VoiceRoute.BLUETOOTH -> {
                    @Suppress("DEPRECATION")
                    audioManager.startBluetoothSco()
                    @Suppress("DEPRECATION")
                    audioManager.isBluetoothScoOn = true
                    true
                }

                VoiceRoute.HEADSET -> {
                    audioManager.isSpeakerphoneOn = false
                    true
                }
            }
        }
    }.getOrDefault(false)
}
