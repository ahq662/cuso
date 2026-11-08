package com.cuso.voice.ui.components

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
import com.cuso.voice.ui.theme.Danger
import com.cuso.voice.ui.theme.Text
import com.cuso.voice.ui.theme.TextMuted

@Composable
fun EmptyScene(
    asset: String,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelSprite(asset, width = 192.dp)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Text,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun PixelAlert(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Danger,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}
