package com.cuso.voice.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class Sprite(
    val palette: Map<Char, Color>,
    val rows: List<String>,
)

private fun loadSprite(context: Context, assetName: String): Sprite? = runCatching {
    val text = context.assets.open(assetName).bufferedReader().use { it.readText() }
    val json = JSONObject(text)
    val paletteJson = json.getJSONObject("palette")
    val palette = buildMap {
        for (key in paletteJson.keys()) {
            put(key[0], Color(android.graphics.Color.parseColor(paletteJson.getString(key))))
        }
    }
    val rowsJson = json.getJSONArray("rows")
    val rows = buildList {
        for (i in 0 until rowsJson.length()) add(rowsJson.getString(i))
    }
    Sprite(palette, rows)
}.getOrNull()

@Composable
fun PixelSprite(
    assetName: String,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sprite by produceState<Sprite?>(initialValue = null, key1 = assetName) {
        value = withContext(Dispatchers.IO) { loadSprite(context, assetName) }
    }

    val s = sprite ?: return
    if (s.rows.isEmpty()) return

    val cols = s.rows[0].length
    if (cols == 0) return
    val rowCount = s.rows.size
    val height = (width.value * rowCount / cols).dp

    Canvas(modifier.size(width, height)) {
        val px = size.width / cols
        val py = size.height / rowCount
        s.rows.forEachIndexed { y, row ->
            var x = 0
            while (x < row.length) {
                val ch = row[x]
                val color = s.palette[ch]
                if (color == null) {
                    x += 1
                    continue
                }
                var run = 1
                while (x + run < row.length && row[x + run] == ch) run += 1
                drawRect(
                    color = color,
                    topLeft = Offset(x * px, y * py),
                    size = Size(run * px + 0.5f, py + 0.5f),
                )
                x += run
            }
        }
    }
}
