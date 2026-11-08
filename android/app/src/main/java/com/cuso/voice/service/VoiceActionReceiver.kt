package com.cuso.voice.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cuso.voice.CusoApp

class VoiceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = (context.applicationContext as CusoApp).voiceSession
        when (intent.action) {
            ACTION_TOGGLE_MUTE -> session.toggleSelfMute()
            ACTION_LEAVE -> session.leave()
        }
    }

    companion object {
        const val ACTION_TOGGLE_MUTE = "com.cuso.voice.action.TOGGLE_MUTE"
        const val ACTION_LEAVE = "com.cuso.voice.action.LEAVE"
    }
}
