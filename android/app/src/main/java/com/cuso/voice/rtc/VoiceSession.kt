package com.cuso.voice.rtc

import android.content.Context
import com.cuso.voice.BuildConfig
import com.cuso.voice.data.ApiClient
import com.cuso.voice.data.SessionStore
import com.cuso.voice.service.VoiceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VoiceSession(private val app: Context) {
    val agora = AgoraManager(app)

    private val store = SessionStore(app)
    private val prefs = app.getSharedPreferences("voice_state", Context.MODE_PRIVATE)
    private val api = ApiClient(tokenProvider = { store.token })
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _channelId = MutableStateFlow<Int?>(null)
    val channelId = _channelId.asStateFlow()

    private val _channelName = MutableStateFlow("")
    val channelName = _channelName.asStateFlow()

    private val _members = MutableStateFlow<List<PresenceMember>>(emptyList())
    val members = _members.asStateFlow()

    private val _forceMuted = MutableStateFlow(false)
    val forceMuted = _forceMuted.asStateFlow()

    private val _selfMuted = MutableStateFlow(false)
    val selfMuted = _selfMuted.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _kicked = MutableStateFlow(false)
    val kicked = _kicked.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted = _deleted.asStateFlow()

    private var presence: PresenceClient? = null
    private var presenceJob: Job? = null

    private val _inCall = MutableStateFlow(false)
    val inCall = _inCall.asStateFlow()

    private val routeController = AudioRouteController(app)
    private val callWatcher = CallStateWatcher(app) { active ->
        _inCall.value = active
        agora.setMuted(active || _forceMuted.value || _selfMuted.value)
    }

    init {
        // Agora 加入成功后，按当前「通话中 + 自闭麦 + 强制闭麦」状态同步一次
        scope.launch {
            agora.state.collect { s ->
                if (s.joined) {
                    agora.setMuted(_inCall.value || _forceMuted.value || _selfMuted.value)
                }
            }
        }
    }

    fun isActiveFor(id: Int): Boolean = _channelId.value == id

    fun persistedChannelId(): Int? = prefs.getInt("channelId", 0).takeIf { it > 0 }

    fun clearPersisted() {
        prefs.edit().clear().apply()
    }

    fun join(id: Int, name: String) {
        if (_channelId.value == id) return
        if (_channelId.value != null) leave()

        _channelId.value = id
        _channelName.value = name
        prefs.edit().putInt("channelId", id).putString("channelName", name).apply()
        _error.value = null
        _kicked.value = false
        _deleted.value = false
        _forceMuted.value = false
        _selfMuted.value = false
        _members.value = emptyList()

        val token = store.token
        if (token.isNullOrBlank()) {
            _error.value = "登录已过期，请重新登录"
            return
        }

        VoiceService.start(app)
        callWatcher.start()

        val p = PresenceClient(BuildConfig.API_BASE_URL, token)
        presence = p
        presenceJob = scope.launch {
            launch { p.members.collect { _members.value = it } }
            launch {
                p.forceMuted.collect {
                    _forceMuted.value = it
                    agora.setMuted(it || _selfMuted.value)
                }
            }
            launch { p.error.collect { _error.value = it } }
            launch {
                p.kicked.collect {
                    if (it) {
                        _kicked.value = true
                        leave()
                    }
                }
            }
            launch {
                p.channelDeleted.collect {
                    if (it) {
                        _deleted.value = true
                        leave()
                    }
                }
            }
        }
        p.join(id)

        scope.launch {
            try {
                val rtc = api.rtcToken(id)
                agora.init(rtc.appId)
                agora.join(rtc.token, rtc.channelName, rtc.uid)
            } catch (e: Exception) {
                _error.value = e.message ?: "无法进入语音"
                VoiceService.stop(app)
            }
        }
    }

    fun leave() {
        val id = _channelId.value
        presenceJob?.cancel()
        presenceJob = null
        presence?.let {
            if (id != null) it.leave(id)
            it.close()
        }
        presence = null
        callWatcher.stop()
        agora.leave()
        prefs.edit().clear().apply()
        _channelId.value = null
        _channelName.value = ""
        _members.value = emptyList()
        _forceMuted.value = false
        _selfMuted.value = false
        _error.value = null
        _kicked.value = false
        _deleted.value = false
        VoiceService.stop(app)
    }

    fun toggleSelfMute() {
        if (_forceMuted.value) return
        _selfMuted.value = !_selfMuted.value
        agora.setMuted(_inCall.value || _selfMuted.value || _forceMuted.value)
    }

    fun setAudioRoute(route: VoiceRoute): Boolean {
        val ok = routeController.setRoute(route)
        if (ok) agora.setSpeaker(route == VoiceRoute.SPEAKER)
        return ok
    }

    fun setSpeaker(on: Boolean) = agora.setSpeaker(on)

    fun setUserVolume(uid: Int, volume: Int) = agora.setUserVolume(uid, volume)

    fun setAins(on: Boolean) = agora.setAins(on)

    fun adminMute(channelId: Int, userId: Int, muted: Boolean): Boolean =
        presence?.adminMute(channelId, userId, muted) ?: false
}
