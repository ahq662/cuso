package com.cuso.voice.ui.screens.lobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.cuso.voice.data.ApiClient
import com.cuso.voice.data.Channel
import com.cuso.voice.data.SessionStore
import com.cuso.voice.ui.components.ChannelCard
import com.cuso.voice.ui.components.EmptyScene
import com.cuso.voice.ui.components.PixelAlert
import com.cuso.voice.ui.components.PixelButton
import com.cuso.voice.ui.components.PixelInput
import com.cuso.voice.ui.components.PixelTopBar
import com.cuso.voice.ui.theme.TextMuted
import com.cuso.voice.ui.theme.accentAt
import kotlinx.coroutines.launch

@Composable
fun LobbyScreen(onOpenChannel: (Int) -> Unit) {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val api = remember { ApiClient(tokenProvider = { session.token }) }
    val scope = rememberCoroutineScope()

    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busyCode by remember { mutableStateOf(false) }
    var joiningId by remember { mutableStateOf<Int?>(null) }

    suspend fun load() {
        error = null
        try {
            channels = api.listPublic()
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { load() }
    }

    fun joinByCode() {
        if (code.isBlank() || busyCode) return
        scope.launch {
            busyCode = true
            error = null
            try {
                val joined = api.joinChannel(inviteCode = code.trim())
                code = ""
                onOpenChannel(joined.id)
            } catch (e: Exception) {
                error = e.message ?: "加入失败"
            } finally {
                busyCode = false
            }
        }
    }

    fun joinById(id: Int) {
        if (joiningId != null) return
        scope.launch {
            joiningId = id
            error = null
            try {
                val joined = api.joinChannel(channelId = id)
                onOpenChannel(joined.id)
            } catch (e: Exception) {
                error = e.message ?: "加入失败"
            } finally {
                joiningId = null
            }
        }
    }

    val filtered = channels.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val indexById = channels.withIndex().associate { it.value.id to it.index }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(title = "大厅")

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            PixelInput(query, { query = it }, "搜索频道名称…")
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelInput(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    placeholder = "输入 6 位邀请码",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                PixelButton(
                    text = if (busyCode) "加入中…" else "加入",
                    onClick = { joinByCode() },
                    enabled = code.isNotBlank() && !busyCode,
                    height = 48.dp,
                )
            }

            Spacer(Modifier.height(12.dp))

            if (error != null) {
                PixelAlert(error ?: "", Modifier.padding(bottom = 8.dp))
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = TextMuted)
                }

                channels.isEmpty() -> EmptyScene(
                    asset = "sprites/signpost.json",
                    title = "大厅还没有频道",
                    hint = "去「我的频道」创建一个吧",
                )

                filtered.isEmpty() -> EmptyScene(
                    asset = "sprites/cracked.json",
                    title = "没有匹配的频道",
                    hint = "换个关键词试试",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(filtered, key = { _, c -> c.id }) { index, channel ->
                        ChannelCard(
                            name = channel.name,
                            memberCount = channel.memberCount,
                            inviteCode = channel.inviteCode,
                            accent = accentAt(indexById[channel.id] ?: 0),
                            onClick = { onOpenChannel(channel.id) },
                            action = {
                                if (channel.isMember) {
                                    PixelButton(
                                        text = "打开",
                                        onClick = { onOpenChannel(channel.id) },
                                        height = 48.dp,
                                    )
                                } else {
                                    PixelButton(
                                        text = if (joiningId == channel.id) "加入中…" else "加入",
                                        onClick = { joinById(channel.id) },
                                        enabled = joiningId == null,
                                        height = 48.dp,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
