package com.cuso.voice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cuso.voice.ui.theme.BgSurface
import com.cuso.voice.ui.theme.Border
import com.cuso.voice.ui.theme.Dimens
import com.cuso.voice.ui.theme.Shadow

@Composable
fun PixelCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .shadow(
                elevation = Dimens.Shadow,
                shape = RectangleShape,
                clip = false,
                ambientColor = Shadow,
                spotColor = Shadow,
            )
            .background(BgSurface)
            .border(Dimens.Border, Border),
    ) {
        if (accent != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accent),
            )
        }
        Column(
            modifier = Modifier.padding(Dimens.CardPadding),
            content = content,
        )
    }
}
