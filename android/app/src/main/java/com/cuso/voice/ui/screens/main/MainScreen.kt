package com.cuso.voice.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuso.voice.CusoApp
import com.cuso.voice.ui.components.PixelBottomBar
import com.cuso.voice.ui.screens.channels.ChannelsScreen
import com.cuso.voice.ui.screens.lobby.LobbyScreen
import com.cuso.voice.ui.screens.profile.ProfileScreen
import com.cuso.voice.ui.theme.BgBase
import com.cuso.voice.ui.theme.BgSurface
import com.cuso.voice.ui.theme.Primary
import com.cuso.voice.ui.theme.Success
import com.cuso.voice.ui.theme.Text
import com.cuso.voice.ui.theme.TextMuted

@Composable
fun MainScreen(
    onOpenChannel: (Int) -> Unit,
    onResumeVoice: (Int) -> Unit,
    onLogout: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val voiceSession = (context.applicationContext as CusoApp).voiceSession
    val activeChannel by voiceSession.channelId.collectAsStateWithLifecycle()
    val activeName by voiceSession.channelName.collectAsStateWithLifecycle()
    val members by voiceSession.members.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = BgBase,
        bottomBar = {
            PixelBottomBar(selected = tab, onSelect = { tab = it })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val id = activeChannel
            if (id != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgSurface)
                        .clickable { onResumeVoice(id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Success),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "正在语音 · ${activeName.ifBlank { "语音房" }}",
                            color = Text,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "${members.size} 人 · 点击返回语音房",
                            color = TextMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        text = "返回",
                        color = Primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (tab) {
                    0 -> ChannelsScreen(onOpenChannel = onOpenChannel)
                    1 -> LobbyScreen(onOpenChannel = onOpenChannel)
                    else -> ProfileScreen(onLogout = onLogout)
                }
            }
        }
    }
}
