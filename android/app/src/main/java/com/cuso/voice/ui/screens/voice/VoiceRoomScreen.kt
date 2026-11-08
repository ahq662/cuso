package com.cuso.voice.ui.screens.voice

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.cuso.voice.BuildConfig
import com.cuso.voice.CusoApp
import com.cuso.voice.data.ApiClient
import com.cuso.voice.data.ChannelDetail
import com.cuso.voice.data.SessionStore
import com.cuso.voice.rtc.PresenceMember
import com.cuso.voice.rtc.VoiceRoute
import com.cuso.voice.ui.components.PixelButton
import com.cuso.voice.ui.components.PixelChip
import com.cuso.voice.ui.components.PixelDialog
import com.cuso.voice.ui.components.PixelSprite
import com.cuso.voice.ui.components.PixelTopBar
import com.cuso.voice.ui.theme.BgBase
import com.cuso.voice.ui.theme.BgElevated
import com.cuso.voice.ui.theme.BgSurface
import com.cuso.voice.ui.theme.Border
import com.cuso.voice.ui.theme.BorderLight
import com.cuso.voice.ui.theme.Danger
import com.cuso.voice.ui.theme.Dimens
import com.cuso.voice.ui.theme.Primary
import com.cuso.voice.ui.theme.Success
import com.cuso.voice.ui.theme.Text
import com.cuso.voice.ui.theme.TextDim
import com.cuso.voice.ui.theme.TextMuted

@Composable
fun VoiceRoomScreen(
    channelId: Int,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as CusoApp
    val session = app.voiceSession
    val store = remember { SessionStore(context) }
    val api = remember { ApiClient(tokenProvider = { store.token }) }

    val voice by session.agora.state.collectAsStateWithLifecycle()
    val presenceMembers by session.members.collectAsStateWithLifecycle()
    val forceMuted by session.forceMuted.collectAsStateWithLifecycle()
    val selfMuted by session.selfMuted.collectAsStateWithLifecycle()
    val sessionError by session.error.collectAsStateWithLifecycle()
    val kicked by session.kicked.collectAsStateWithLifecycle()
    val deleted by session.deleted.collectAsStateWithLifecycle()
    val activeChannel by session.channelId.collectAsStateWithLifecycle()
    val sessionChannelName by session.channelName.collectAsStateWithLifecycle()

    var detail by remember { mutableStateOf<ChannelDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    var selectedMember by remember { mutableStateOf<PresenceMember?>(null) }
    var volumes by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var ains by remember { mutableStateOf(true) }
    var route by remember { mutableStateOf(VoiceRoute.SPEAKER) }
    var settingsOpen by remember { mutableStateOf(false) }
    var wasActive by remember { mutableStateOf(false) }
    var navigatingBack by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (!granted) error = "需要麦克风权限才能进入语音"
    }

    val phoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    LaunchedEffect(Unit) {
        val needsNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        val needsPhone = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE,
        ) != PackageManager.PERMISSION_GRANTED
        when {
            needsNotifications -> notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            needsPhone -> phoneLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        }
    }

    LaunchedEffect(Unit) {
        try {
            detail = api.channelDetail(channelId)
        } catch (e: Exception) {
            error = e.message ?: "加载频道失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(permissionGranted, detail, retryKey) {
        val d = detail ?: return@LaunchedEffect
        if (!permissionGranted) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return@LaunchedEffect
        }
        if (session.isActiveFor(channelId)) return@LaunchedEffect
        session.join(channelId, d.channel.name)
    }

    LaunchedEffect(activeChannel) {
        if (activeChannel == channelId) {
            wasActive = true
        } else if (wasActive && !navigatingBack) {
            navigatingBack = true
            onBack()
        }
    }

    LaunchedEffect(kicked) {
        if (kicked) {
            Toast.makeText(context, "你已被移出频道", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(deleted) {
        if (deleted) {
            Toast.makeText(context, "频道已被删除", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun leaveAndBack() {
        if (navigatingBack) return
        navigatingBack = true
        session.leave()
        onBack()
    }

    // 返回 = 最小化，语音继续；只有底部「退出」才真正离开
    BackHandler { onBack() }

    val selfId = store.user?.id
    val isCreator = detail?.channel?.creatorId == selfId
    val creator = detail?.creator

    val fallbackMembers = remember(detail, voice.remoteUids, store.user) {
        val base = detail?.members.orEmpty().map {
            PresenceMember(it.userId, it.username, it.nickname, it.avatarUrl, false)
        }
        buildList {
            val self = base.firstOrNull { it.userId == selfId }
                ?: store.user?.let {
                    PresenceMember(it.id, it.username, it.nickname, it.avatarUrl, false)
                }
            if (self != null) add(self)
            voice.remoteUids.sorted().forEach { uid ->
                val member = base.firstOrNull { it.userId == uid }
                if (member != null) add(member)
                else add(PresenceMember(uid, "用户$uid", "用户$uid", null, false))
            }
        }
    }
    val displayMembers = presenceMembers.ifEmpty { fallbackMembers }
    val err = voice.error ?: sessionError ?: error

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase),
    ) {
        PixelTopBar(
            title = detail?.channel?.name ?: sessionChannelName.ifBlank { "语音房" },
            onBack = { onBack() },
            actions = {
                if (voice.joined) {
                    PixelChip("${displayMembers.size} 人")
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { settingsOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙", color = TextDim, fontSize = 20.sp)
                }
            },
        )

        if (voice.joined) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Success),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "已加入语音",
                    color = TextDim,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${displayMembers.size} 人在语音",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        if (forceMuted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x33FF5C7A))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "你已被管理员闭麦，需管理员解除",
                    color = Danger,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (sessionError != null && voice.joined) {
            Text(
                text = sessionError ?: "",
                color = Danger,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载中…", color = TextMuted)
            }

            !permissionGranted -> PermissionPanel(
                onGrant = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                onBack = { leaveAndBack() },
            )

            err != null && !voice.joined -> ErrorPanel(
                message = err,
                onRetry = {
                    session.leave()
                    error = null
                    retryKey += 1
                },
                onBack = { leaveAndBack() },
            )

            !voice.joined -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelSprite("sprites/sheep.json", width = 112.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("正在进入语音…", color = TextDim)
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(displayMembers, key = { it.userId }) { member ->
                    val isSelf = member.userId == selfId
                    val speaking = voice.speakingUids.contains(member.userId)
                    val muted = member.muted || (isSelf && (forceMuted || selfMuted))
                    VoiceMemberTile(
                        member = member,
                        isSelf = isSelf,
                        speaking = speaking,
                        muted = muted,
                        forceMuted = isSelf && forceMuted,
                        onClick = { selectedMember = member },
                    )
                }
            }
        }

        if (voice.joined) {
            VoiceControls(
                muted = selfMuted,
                forceMuted = forceMuted,
                speakerOn = voice.speakerOn,
                onToggleMute = {
                    if (!forceMuted) session.toggleSelfMute()
                },
                onToggleSpeaker = {
                    val next = !voice.speakerOn
                    session.setSpeaker(next)
                    route = if (next) VoiceRoute.SPEAKER else VoiceRoute.EARPIECE
                },
                onLeave = { leaveAndBack() },
            )
        }
    }

    val target = selectedMember
    if (target != null) {
        val liveTarget = displayMembers.firstOrNull { it.userId == target.userId } ?: target
        val targetIsSelf = liveTarget.userId == selfId
        var volume by remember(liveTarget.userId) {
            mutableStateOf(volumes[liveTarget.userId] ?: 100)
        }

        PixelDialog(
            title = liveTarget.nickname.ifBlank { liveTarget.username },
            onDismiss = { selectedMember = null },
        ) {
            if (targetIsSelf) {
                Text(
                    text = "这是你自己，音量由系统控制。",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    text = "调节 ${liveTarget.nickname.ifBlank { liveTarget.username }} 的播放音量",
                    color = TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = volume.toFloat(),
                    onValueChange = {
                        volume = it.toInt()
                        volumes = volumes + (liveTarget.userId to volume)
                        session.setUserVolume(liveTarget.userId, volume)
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = Primary,
                        activeTrackColor = Primary,
                        inactiveTrackColor = Border,
                    ),
                )
                Text(
                    text = "音量 $volume",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            if (isCreator && !targetIsSelf) {
                Spacer(Modifier.height(16.dp))
                PixelButton(
                    text = if (liveTarget.muted) "解除闭麦" else "强制闭麦",
                    onClick = {
                        val ok = session.adminMute(channelId, liveTarget.userId, !liveTarget.muted)
                        if (!ok) {
                            Toast.makeText(context, "连接已断开，请稍后重试", Toast.LENGTH_SHORT).show()
                        }
                        selectedMember = null
                    },
                    danger = !liveTarget.muted,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(10.dp))
            PixelButton(
                text = "关闭",
                onClick = { selectedMember = null },
                ghost = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (settingsOpen) {
        PixelDialog(
            title = "语音设置",
            onDismiss = { settingsOpen = false },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "AI 降噪",
                        color = Text,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "抑制键盘、风扇等背景噪音",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                PixelButton(
                    text = if (ains) "关闭" else "开启",
                    onClick = {
                        ains = !ains
                        session.setAins(ains)
                    },
                    ghost = true,
                    height = 40.dp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "当前：${if (ains) "已开启" else "已关闭"}",
                color = TextDim,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "音频输出",
                color = Text,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RouteButton("扬声器", VoiceRoute.SPEAKER, route) {
                    if (session.setAudioRoute(VoiceRoute.SPEAKER)) route = VoiceRoute.SPEAKER
                    else Toast.makeText(context, "未检测到扬声器", Toast.LENGTH_SHORT).show()
                }
                RouteButton("听筒", VoiceRoute.EARPIECE, route) {
                    if (session.setAudioRoute(VoiceRoute.EARPIECE)) route = VoiceRoute.EARPIECE
                    else Toast.makeText(context, "未检测到听筒", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RouteButton("蓝牙", VoiceRoute.BLUETOOTH, route) {
                    if (session.setAudioRoute(VoiceRoute.BLUETOOTH)) route = VoiceRoute.BLUETOOTH
                    else Toast.makeText(context, "未检测到蓝牙设备", Toast.LENGTH_SHORT).show()
                }
                RouteButton("耳机", VoiceRoute.HEADSET, route) {
                    if (session.setAudioRoute(VoiceRoute.HEADSET)) route = VoiceRoute.HEADSET
                    else Toast.makeText(context, "未检测到有线耳机", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.height(16.dp))
            PixelButton(
                text = "关闭",
                onClick = { settingsOpen = false },
                ghost = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PermissionPanel(
    onGrant: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelSprite("sprites/sheep.json", width = 96.dp)
        Text(
            text = "需要麦克风权限",
            style = MaterialTheme.typography.titleLarge,
            color = Text,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "允许后才能进入语音房",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(20.dp))
        PixelButton(
            text = "授权麦克风",
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PixelButton(
            text = "返回",
            onClick = onBack,
            ghost = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "无法进入语音",
            style = MaterialTheme.typography.titleLarge,
            color = Text,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Danger,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        PixelButton(
            text = "重试",
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        PixelButton(
            text = "返回",
            onClick = onBack,
            ghost = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VoiceMemberTile(
    member: PresenceMember,
    isSelf: Boolean,
    speaking: Boolean,
    muted: Boolean,
    forceMuted: Boolean,
    onClick: () -> Unit,
) {
    val name = member.nickname.ifBlank { member.username }
    val avatarModel = member.avatarUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { BuildConfig.API_BASE_URL + it }

    val showSpeaking = speaking && !muted && !forceMuted && !member.muted

    val status = when {
        forceMuted -> "已被闭麦"
        member.muted -> "已被闭麦"
        muted -> "已闭麦"
        showSpeaking -> "说话中"
        else -> "在语音"
    }
    val statusColor = when {
        forceMuted || member.muted || muted -> Danger
        showSpeaking -> Primary
        else -> TextMuted
    }
    val borderColor = when {
        forceMuted || member.muted -> Danger
        showSpeaking -> Primary
        else -> Border
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSurface)
            .border(2.dp, borderColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(BgElevated)
                .border(2.dp, if (showSpeaking) Primary else BorderLight),
            contentAlignment = Alignment.Center,
        ) {
            if (avatarModel != null) {
                AsyncImage(
                    model = avatarModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = name.take(1),
                    color = Primary,
                    fontSize = 24.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = name + if (isSelf) "（你）" else "",
            color = Text,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = status,
            color = statusColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun RowScope.RouteButton(
    label: String,
    value: VoiceRoute,
    current: VoiceRoute,
    onClick: () -> Unit,
) {
    PixelButton(
        text = label,
        onClick = onClick,
        ghost = current != value,
        height = 40.dp,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun VoiceControls(
    muted: Boolean,
    forceMuted: Boolean,
    speakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSurface)
            .drawBehind {
                drawLine(
                    color = Border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = Dimens.Border.toPx(),
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PixelButton(
            text = when {
                forceMuted -> "🔇 已被闭麦"
                muted -> "🔇 已闭麦"
                else -> "🎙 麦克风"
            },
            onClick = onToggleMute,
            danger = forceMuted || muted,
            enabled = !forceMuted,
            modifier = Modifier.weight(1.2f),
        )
        PixelButton(
            text = if (speakerOn) "🔊 扬声器" else "🔈 听筒",
            onClick = onToggleSpeaker,
            ghost = true,
            modifier = Modifier.weight(1.2f),
        )
        PixelButton(
            text = "退出",
            onClick = onLeave,
            danger = true,
            modifier = Modifier.weight(0.8f),
        )
    }
}
