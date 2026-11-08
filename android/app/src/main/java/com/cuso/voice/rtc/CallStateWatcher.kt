package com.cuso.voice.rtc

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallStateWatcher(
    private val context: Context,
    private val onCallActive: (Boolean) -> Unit,
) {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private var callback: TelephonyCallback? = null
    private var listener: PhoneStateListener? = null
    private var lastActive: Boolean? = null

    fun start() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        update(state != TelephonyManager.CALL_STATE_IDLE)
                    }
                }
                callback = cb
                telephony.registerTelephonyCallback(ContextCompat.getMainExecutor(context), cb)
            } else {
                @Suppress("DEPRECATION")
                val l = object : PhoneStateListener() {
                    @Suppress("DEPRECATION")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        update(state != TelephonyManager.CALL_STATE_IDLE)
                    }
                }
                listener = l
                @Suppress("DEPRECATION")
                telephony.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun update(active: Boolean) {
        if (lastActive == active) return
        lastActive = active
        onCallActive(active)
    }

    fun stop() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                callback?.let { telephony.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                listener?.let { telephony.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        }
        callback = null
        listener = null
    }
}
