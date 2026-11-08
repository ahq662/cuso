package com.cuso.voice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cuso.voice.ui.theme.BorderLight
import com.cuso.voice.ui.theme.DangerInk
import com.cuso.voice.ui.theme.Dimens
import com.cuso.voice.ui.theme.PrimaryInk
import com.cuso.voice.ui.theme.Shadow
import com.cuso.voice.ui.theme.TextDim

@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    ghost: Boolean = false,
    enabled: Boolean = true,
    height: Dp = Dimens.ButtonHeight,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val offset = if (pressed && enabled) 2.dp else 0.dp

    val bg: Brush? = when {
        ghost -> null
        danger -> Brush.verticalGradient(listOf(Color(0xFFFF6B85), Color(0xFFE03E5C)))
        else -> Brush.verticalGradient(listOf(Color(0xFF3FF2D6), Color(0xFF1BC8AE)))
    }
    val contentColor = when {
        ghost -> TextDim
        danger -> DangerInk
        else -> PrimaryInk
    }
    val borderColor = when {
        ghost -> BorderLight
        danger -> Color(0xFF3A0B12)
        else -> Color(0xFF06231F)
    }

    val base = modifier
        .height(height)
        .offset(x = offset, y = offset)

    val shaped = if (ghost) base else base.shadow(
        elevation = Dimens.Shadow,
        shape = RectangleShape,
        clip = false,
        ambientColor = Shadow,
        spotColor = Shadow,
    )

    val withBg = if (bg != null) shaped.background(bg, RectangleShape) else shaped

    Box(
        modifier = withBg
            .alpha(if (enabled) 1f else 0.45f)
            .border(Dimens.Border, borderColor, RectangleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
