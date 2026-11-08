package com.cuso.voice

import android.app.Application
import com.cuso.voice.rtc.VoiceSession

class CusoApp : Application() {
    lateinit var voiceSession: VoiceSession
        private set

    override fun onCreate() {
        super.onCreate()
        voiceSession = VoiceSession(this)
    }
}
