package com.cuso.voice.ui.screens.channels

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.cuso.voice.data.ApiClient
import com.cuso.voice.data.Channel
import com.cuso.voice.data.SessionStore
import com.cuso.voice.ui.components.ChannelCard
import com.cuso.voice.ui.components.EmptyScene
import com.cuso.voice.ui.components.PixelAlert
import com.cuso.voice.ui.components.PixelButton
import com.cuso.voice.ui.components.PixelDialog
import com.cuso.voice.ui.components.PixelInput
import com.cuso.voice.ui.components.PixelTopBar
import com.cuso.voice.ui.theme.Primary
import com.cuso.voice.ui.theme.TextDim
import com.cuso.voice.ui.theme.TextMuted
import com.cuso.voice.ui.theme.accentAt
import kotlinx.coroutines.launch

@Composable
fun ChannelsScreen(onOpenChannel: (Int) -> Unit) {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val api = remember { ApiClient(tokenProvider = { session.token }) }
    val scope = rememberCoroutineScope()

    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    var createOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        error = null
        try {
            channels = api.listMine()
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { load() }
    }

    val filtered = channels.filter { it.name.contains(query.trim(), ignoreCase = true) }
    val indexById = channels.withIndex().associate { it.value.id to it.index }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = "我的频道",
            actions = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            name = ""
                            createError = null
                            createOpen = true
                        }
                        .semantics { contentDescription = "创建频道" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", color = Primary, fontSize = 22.sp)
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            PixelInput(query, { query = it }, "搜索频道名称…")
            Spacer(Modifier.height(12.dp))

            if (error != null) {
                PixelAlert(error ?: "", Modifier.padding(bottom = 8.dp))
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("加载中…", color = TextMuted)
                }

                channels.isEmpty() -> EmptyScene(
                    asset = "sprites/cabin.json",
                    title = "还没有加入任何频道",
                    hint = "点右上角 + 创建，或去「大厅」加入",
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
                        )
                    }
                }
            }
        }
    }

    if (createOpen) {
        PixelDialog(
            title = "创建频道",
            onDismiss = {
                if (!creating) {
                    createOpen = false
                    name = ""
                }
            },
        ) {
            Text(
                text = "给频道起个名字，创建后你自动成为创建者。",
                color = TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            PixelInput(name, { name = it }, "1-20 个字符")
            if (createError != null) {
                PixelAlert(createError ?: "", Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(
                    text = "取消",
                    onClick = { createOpen = false; name = "" },
                    ghost = true,
                    enabled = !creating,
                    modifier = Modifier.weight(1f),
                )
                PixelButton(
                    text = if (creating) "创建中…" else "创建",
                    onClick = {
                        scope.launch {
                            creating = true
                            createError = null
                            try {
                                val created = api.createChannel(name.trim())
                                createOpen = false
                                name = ""
                                onOpenChannel(created.id)
                            } catch (e: Exception) {
                                createError = e.message ?: "创建失败"
                            } finally {
                                creating = false
                            }
                        }
                    },
                    enabled = !creating && name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
