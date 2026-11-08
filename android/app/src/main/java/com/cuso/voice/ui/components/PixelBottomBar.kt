package com.cuso.voice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuso.voice.ui.theme.BgSurface
import com.cuso.voice.ui.theme.Border
import com.cuso.voice.ui.theme.Dimens
import com.cuso.voice.ui.theme.PrimaryInk
import com.cuso.voice.ui.theme.TextMuted

private data class BottomTab(val glyph: String, val label: String)

private val Tabs = listOf(
    BottomTab("▦", "频道"),
    BottomTab("◎", "大厅"),
    BottomTab("●", "我的"),
)

@Composable
fun PixelBottomBar(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(BgSurface)
            .drawBehind {
                drawLine(
                    color = Border,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = Dimens.Border.toPx(),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tabs.forEachIndexed { index, tab ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(
                                        Brush.verticalGradient(listOf(Color(0xFF3FF2D6), Color(0xFF1BC8AE))),
                                        RectangleShape,
                                    )
                                    .padding(horizontal = 18.dp, vertical = 5.dp)
                            } else {
                                Modifier.padding(horizontal = 18.dp, vertical = 5.dp)
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = tab.glyph,
                        color = if (isSelected) PrimaryInk else TextMuted,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = tab.label,
                        color = if (isSelected) PrimaryInk else TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
