package com.cuso.voice.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import coil.compose.AsyncImage
import com.cuso.voice.BuildConfig
import com.cuso.voice.data.ApiClient
import com.cuso.voice.data.ChannelDetail
import com.cuso.voice.data.ChannelMember
import com.cuso.voice.data.SessionStore
import com.cuso.voice.ui.components.EmptyScene
import com.cuso.voice.ui.components.PixelAlert
import com.cuso.voice.ui.components.PixelButton
import com.cuso.voice.ui.components.PixelCard
import com.cuso.voice.ui.components.PixelChip
import com.cuso.voice.ui.components.PixelDialog
import com.cuso.voice.ui.components.PixelTopBar
import com.cuso.voice.ui.theme.BgElevated
import com.cuso.voice.ui.theme.BgSurface
import com.cuso.voice.ui.theme.Border
import com.cuso.voice.ui.theme.BorderLight
import com.cuso.voice.ui.theme.Gold
import com.cuso.voice.ui.theme.Primary
import com.cuso.voice.ui.theme.Text
import com.cuso.voice.ui.theme.TextDim
import com.cuso.voice.ui.theme.TextMuted
import com.cuso.voice.ui.theme.accentFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChannelDetailScreen(
    channelId: Int,
    onBack: () -> Unit,
    onEnterVoice: () -> Unit,
) {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val api = remember { ApiClient(tokenProvider = { session.token }) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var detail by remember { mutableStateOf<ChannelDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var kickTarget by remember { mutableStateOf<ChannelMember?>(null) }
    var copied by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() {
        error = null
        try {
            detail = api.channelDetail(channelId)
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { load() }
    }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(title = "频道详情", onBack = onBack)

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = TextMuted)
            }

            detail == null -> EmptyScene(
                asset = "sprites/cracked.json",
                title = error ?: "频道不存在",
                hint = "返回列表再试一次",
            )

            else -> {
                val data = detail!!
                val channel = data.channel
                val isCreator = channel.creatorId == session.user?.id
                val creatorName = data.creator?.let { it.nickname.ifBlank { it.username } } ?: "未知"

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                ) {
                    item {
                        PixelCard(accent = accentFor(channel.id)) {
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.displayLarge,
                                color = Text,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${data.members.size} 位成员 · 创建者：$creatorName${if (isCreator) "（你）" else ""}",
                                color = TextDim,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PixelChip("邀请码 ${channel.inviteCode}", color = Primary)
                                Spacer(Modifier.width(8.dp))
                                PixelButton(
                                    text = if (copied) "已复制" else "复制",
                                    onClick = {
                                        clipboard.setText(AnnotatedString(channel.inviteCode))
                                        copied = true
                                        scope.launch {
                                            delay(1500)
                                            copied = false
                                        }
                                    },
                                    ghost = true,
                                    height = 48.dp,
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            PixelButton(
                                text = "🎙 进入语音",
                                onClick = onEnterVoice,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    item {
                        Text(
                            text = "成员列表",
                            style = MaterialTheme.typography.titleLarge,
                            color = Text,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }

                    items(data.members, key = { it.userId }) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgSurface)
                                .border(2.dp, Border)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val avatarModel = member.avatarUrl
                                ?.takeIf { it.isNotBlank() }
                                ?.let { BuildConfig.API_BASE_URL + it }
                            if (avatarModel != null) {
                                AsyncImage(
                                    model = avatarModel,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(BgElevated)
                                        .border(2.dp, BorderLight),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(BgElevated)
                                        .border(2.dp, BorderLight),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = (member.nickname.ifBlank { member.username }).take(1),
                                        color = Primary,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = member.nickname.ifBlank { member.username },
                                color = Text,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (member.userId == channel.creatorId) {
                                PixelChip("创建者", color = Gold)
                            }
                            if (isCreator && member.userId != session.user?.id) {
                                Spacer(Modifier.width(8.dp))
                                PixelButton(
                                    text = "踢出",
                                    onClick = { kickTarget = member },
                                    danger = true,
                                    height = 48.dp,
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(4.dp))
                        if (channel.isMember) {
                            PixelButton(
                                text = "退出频道",
                                onClick = { confirm = "leave" },
                                ghost = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (isCreator) {
                            Spacer(Modifier.height(8.dp))
                            PixelButton(
                                text = "删除频道",
                                onClick = { confirm = "delete" },
                                danger = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (error != null) {
                        item { PixelAlert(error ?: "") }
                    }
                }
            }
        }
    }

    val action = confirm
    if (action != null) {
        PixelDialog(
            title = if (action == "delete") "删除频道" else "退出频道",
            onDismiss = { if (!busy) confirm = null },
        ) {
            Text(
                text = if (action == "delete") {
                    "确定删除该频道吗？此操作不可恢复，所有成员都会被移出。"
                } else {
                    "确定退出该频道吗？退出后可以再用邀请码重新加入。"
                },
                color = TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(
                    text = "取消",
                    onClick = { confirm = null },
                    ghost = true,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                PixelButton(
                    text = if (busy) "处理中…" else "确定",
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                if (action == "delete") api.deleteChannel(channelId) else api.leaveChannel(channelId)
                                confirm = null
                                onBack()
                            } catch (e: Exception) {
                                error = e.message ?: "操作失败"
                                confirm = null
                            } finally {
                                busy = false
                            }
                        }
                    },
                    danger = action == "delete",
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    val target = kickTarget
    if (target != null) {
        PixelDialog(
            title = "踢出成员",
            onDismiss = { if (!busy) kickTarget = null },
        ) {
            Text(
                text = "确定将「${target.nickname.ifBlank { target.username }}」踢出该频道吗？对方需要邀请码才能重新加入。",
                color = TextDim,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelButton(
                    text = "取消",
                    onClick = { kickTarget = null },
                    ghost = true,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                PixelButton(
                    text = if (busy) "处理中…" else "确定",
                    onClick = {
                        scope.launch {
                            busy = true
                            try {
                                api.kickMember(channelId, target.userId)
                                kickTarget = null
                                load()
                            } catch (e: Exception) {
                                error = e.message ?: "踢出失败"
                                kickTarget = null
                            } finally {
                                busy = false
                            }
                        }
                    },
                    danger = true,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
