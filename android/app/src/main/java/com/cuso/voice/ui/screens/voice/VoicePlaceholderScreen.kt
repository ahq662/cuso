package com.cuso.voice.ui.screens.voice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cuso.voice.ui.components.PixelSprite
import com.cuso.voice.ui.components.PixelTopBar
import com.cuso.voice.ui.theme.Text
import com.cuso.voice.ui.theme.TextDim

@Composable
fun VoicePlaceholderScreen(
    channelId: Int,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PixelTopBar(title = "语音房", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PixelSprite("sprites/sheep.json", width = 128.dp)
            Text(
                text = "语音房 M6 接入",
                style = MaterialTheme.typography.headlineMedium,
                color = Text,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "频道 #$channelId",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
