package com.cuso.voice.rtc

import android.content.Context
import android.util.Log
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.IRtcEngineEventHandler.AudioVolumeInfo
import io.agora.rtc2.IRtcEngineEventHandler.RtcStats
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoiceState(
    val joined: Boolean = false,
    val joining: Boolean = false,
    val localUid: Int = 0,
    val remoteUids: Set<Int> = emptySet(),
    val speakingUids: Set<Int> = emptySet(),
    val localMuted: Boolean = false,
    val speakerOn: Boolean = true,
    val error: String? = null,
)

class AgoraManager(private val context: Context) {
    private var engine: RtcEngine? = null
    private var ainsEnabled = true
    private val _state = MutableStateFlow(VoiceState())
    val state = _state.asStateFlow()

    private val handler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            _state.update {
                it.copy(joined = true, joining = false, localUid = uid, error = null)
            }
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            _state.update { it.copy(remoteUids = it.remoteUids + uid) }
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            _state.update {
                it.copy(
                    remoteUids = it.remoteUids - uid,
                    speakingUids = it.speakingUids - uid,
                )
            }
        }

        override fun onAudioVolumeIndication(speakers: Array<AudioVolumeInfo>?, totalVolume: Int) {
            if (speakers == null) return
            val localUid = _state.value.localUid
            val speaking = speakers
                .filter { it.volume > 8 }
                .map { if (it.uid == 0) localUid else it.uid }
                .toSet()
            _state.update { it.copy(speakingUids = speaking) }

            val maxLocal = speakers.firstOrNull { it.uid == 0 }?.volume ?: 0
            val maxRemote = speakers.filter { it.uid != 0 }.maxOfOrNull { it.volume } ?: 0
            if (maxLocal > 20 || maxRemote > 20) {
                Log.d("CusoAgora", "vol local=$maxLocal remote=$maxRemote")
            }
        }

        override fun onError(err: Int) {
            _state.update { it.copy(joining = false, error = "语音错误（$err）") }
        }

        override fun onLocalAudioStateChanged(state: Int, error: Int) {
            Log.d("CusoAgora", "localAudio state=$state error=$error")
        }

        override fun onRemoteAudioStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            Log.d("CusoAgora", "remoteAudio uid=$uid state=$state reason=$reason")
        }

        override fun onLeaveChannel(stats: RtcStats?) {
            _state.update {
                it.copy(joined = false, remoteUids = emptySet(), speakingUids = emptySet())
            }
        }
    }

    fun init(appId: String) {
        if (engine != null) return
        val appContext = context.applicationContext
        val config = RtcEngineConfig().apply {
            mAppId = appId
            mContext = appContext
            mEventHandler = handler
        }
        Log.d("CusoAgora", "init appIdLen=${appId.length}")
        val created = runCatching { RtcEngine.create(config) }.getOrNull()
        Log.d("CusoAgora", "create result=$created")
        if (created == null) {
            _state.update { it.copy(error = "语音引擎创建失败，请检查 App ID 与 native 库") }
            return
        }
        runCatching {
            created.setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            created.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
            created.enableAudioVolumeIndication(200, 3, true)
            created.setDefaultAudioRoutetoSpeakerphone(true)
            created.enableAudio()
            runCatching { created.setAINSMode(ainsEnabled, 1) }
        }.onSuccess {
            engine = created
        }.onFailure {
            Log.e("CusoAgora", "init failed", it)
            _state.update { s -> s.copy(error = "语音初始化失败：${it.message}") }
        }
    }

    fun join(token: String, channelName: String, uid: Int) {
        val e = engine ?: return
        _state.update { it.copy(joining = true, error = null) }
        runCatching { e.enableLocalAudio(true) }
        runCatching { e.muteLocalAudioStream(false) }
        e.joinChannel(token, channelName, "", uid)
    }

    fun setMuted(muted: Boolean) {
        engine?.muteLocalAudioStream(muted)
        _state.update { it.copy(localMuted = muted) }
    }

    fun setSpeaker(on: Boolean) {
        engine?.setEnableSpeakerphone(on)
        _state.update { it.copy(speakerOn = on) }
    }

    fun setUserVolume(uid: Int, volume: Int) {
        runCatching { engine?.adjustUserPlaybackSignalVolume(uid, volume.coerceIn(0, 100)) }
    }

    fun setAins(enabled: Boolean) {
        ainsEnabled = enabled
        runCatching { engine?.setAINSMode(enabled, 1) }
    }

    fun leave() {
        val e = engine ?: return
        engine = null
        runCatching { e.leaveChannel() }
        runCatching { RtcEngine.destroy() }
        _state.update { VoiceState() }
    }
}
