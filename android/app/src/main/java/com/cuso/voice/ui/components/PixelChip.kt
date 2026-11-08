package com.cuso.voice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cuso.voice.ui.theme.BgSurface
import com.cuso.voice.ui.theme.Border
import com.cuso.voice.ui.theme.Dimens
import com.cuso.voice.ui.theme.TextDim

@Composable
fun PixelChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextDim,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(BgSurface)
            .border(Dimens.Border, Border)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
