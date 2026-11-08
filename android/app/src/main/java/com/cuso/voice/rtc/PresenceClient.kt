package com.cuso.voice.rtc

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URI

data class PresenceMember(
    val userId: Int,
    val username: String,
    val nickname: String,
    val avatarUrl: String?,
    val muted: Boolean,
)

class PresenceClient(baseUrl: String, token: String) {
    private val socket: Socket = IO.socket(
        URI.create(baseUrl),
        IO.Options().apply {
            auth = mapOf("token" to token)
            reconnection = true
        },
    )

    private val _members = MutableStateFlow<List<PresenceMember>>(emptyList())
    val members = _members.asStateFlow()

    private val _forceMuted = MutableStateFlow(false)
    val forceMuted = _forceMuted.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _kicked = MutableStateFlow(false)
    val kicked = _kicked.asStateFlow()

    private val _channelDeleted = MutableStateFlow(false)
    val channelDeleted = _channelDeleted.asStateFlow()

    private var pendingChannel: Int? = null
    private var currentChannel: Int? = null

    init {
        socket.on(Socket.EVENT_CONNECT) { _ ->
            pendingChannel?.let { emitJoin(it) }
        }

        socket.on("presence:update") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            val array = payload.optJSONArray("members") ?: return@on
            val list = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        PresenceMember(
                            userId = item.optInt("userId"),
                            username = item.optString("username"),
                            nickname = item.optString("nickname", item.optString("username")),
                            avatarUrl = if (item.isNull("avatarUrl")) {
                                null
                            } else {
                                item.optString("avatarUrl").takeIf { it.isNotBlank() }
                            },
                            muted = item.optBoolean("muted", false),
                        ),
                    )
                }
            }
            _members.value = list
            _error.value = null
        }

        socket.on("force:mute") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            _forceMuted.value = payload.optBoolean("muted", false)
        }

        socket.on("presence:error") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            _error.value = payload.optString("error").takeIf { it.isNotBlank() }
        }

        socket.on("member:kicked") { _ ->
            _kicked.value = true
        }

        socket.on("channel:deleted") { args ->
            val payload = args.firstOrNull() as? JSONObject ?: return@on
            if (payload.optInt("channelId") == currentChannel) {
                _channelDeleted.value = true
            }
        }

        socket.connect()
    }

    fun join(channelId: Int) {
        pendingChannel = channelId
        currentChannel = channelId
        if (socket.connected()) emitJoin(channelId)
    }

    private fun emitJoin(channelId: Int) {
        socket.emit("channel:join", JSONObject().put("channelId", channelId))
    }

    fun leave(channelId: Int) {
        if (socket.connected()) {
            socket.emit("channel:leave", JSONObject().put("channelId", channelId))
        }
        pendingChannel = null
        currentChannel = null
    }

    fun adminMute(channelId: Int, userId: Int, muted: Boolean): Boolean {
        if (!socket.connected()) return false
        socket.emit(
            "admin:mute",
            JSONObject()
                .put("channelId", channelId)
                .put("userId", userId)
                .put("muted", muted),
        )
        return true
    }

    fun close() {
        runCatching { socket.off() }
        runCatching { socket.disconnect() }
    }
}
