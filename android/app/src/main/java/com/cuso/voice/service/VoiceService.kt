package com.cuso.voice.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cuso.voice.CusoApp
import com.cuso.voice.MainActivity
import com.cuso.voice.R
import com.cuso.voice.rtc.VoiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class VoiceService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: VoiceSession
    private lateinit var manager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        session = (application as CusoApp).voiceSession
        manager = getSystemService(NotificationManager::class.java)
        createChannel()

        // 进程被杀后系统重启服务：不再偷偷开麦，只发「点击恢复」通知
        if (session.channelId.value == null) {
            session.persistedChannelId()?.let { postRecoveryNotification(it) }
            stopSelf()
            return
        }

        startForegroundCompat(buildNotification())
        scope.launch {
            combine(
                session.channelName,
                session.members,
                session.agora.state,
                session.selfMuted,
                session.forceMuted,
            ) { name, members, _, selfMuted, forceMuted ->
                NotificationData(name, members.size, selfMuted, forceMuted)
            }.collect { data ->
                manager.notify(NOTIF_ID, buildNotification(data))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "语音通话",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "语音房通话状态"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(data: NotificationData? = null): Notification {
        val name = data?.channelName?.ifBlank { "语音房" } ?: session.channelName.value.ifBlank { "语音房" }
        val selfMuted = data?.selfMuted ?: session.selfMuted.value
        val forceMuted = data?.forceMuted ?: session.forceMuted.value
        val count = data?.count ?: session.members.value.size
        val status = when {
            forceMuted -> "已被管理员闭麦"
            selfMuted -> "已闭麦"
            else -> "麦克风开启"
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val muteIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, VoiceActionReceiver::class.java).setAction(VoiceActionReceiver.ACTION_TOGGLE_MUTE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val leaveIntent = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, VoiceActionReceiver::class.java).setAction(VoiceActionReceiver.ACTION_LEAVE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle("CUSO 语音 · $name")
            .setContentText("$status · $count 人在语音")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, if (forceMuted) "已闭麦" else if (selfMuted) "开麦" else "闭麦", muteIntent)
            .addAction(0, "退出", leaveIntent)
            .build()
    }

    private fun postRecoveryNotification(channelId: Int) {
        val contentIntent = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("resumeChannelId", channelId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_voice)
            .setContentTitle("通话已中断")
            .setContentText("点击返回语音房恢复通话")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(RECOVERY_NOTIF_ID, notification)
    }

    private data class NotificationData(
        val channelName: String,
        val count: Int,
        val selfMuted: Boolean,
        val forceMuted: Boolean,
    )

    companion object {
        const val CHANNEL_ID = "cuso_voice"
        const val NOTIF_ID = 1001
        const val RECOVERY_NOTIF_ID = 1002

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, VoiceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceService::class.java))
        }
    }
}
