package com.cuso.voice.ui.theme

import androidx.compose.ui.graphics.Color

val BgBase = Color(0xFF0B0E14)
val BgPage = Color(0xFF111623)
val BgSurface = Color(0xFF182033)
val BgElevated = Color(0xFF212B44)
val Border = Color(0xFF2E3A5C)
val BorderLight = Color(0xFF3F4E78)
val Primary = Color(0xFF2FE6C8)
val Primary2 = Color(0xFF22D3EE)
val Gold = Color(0xFFFFD166)
val Success = Color(0xFF4ADE80)
val Danger = Color(0xFFFF5C7A)
val Text = Color(0xFFE6EAF2)
val TextDim = Color(0xFFA3AEC4)
val TextMuted = Color(0xFF6E7A94)
val Shadow = Color(0xFF05070C)
val PrimaryInk = Color(0xFF04201B)
val DangerInk = Color(0xFF1A0508)

val Accents = listOf(
    Color(0xFF2FE6C8),
    Color(0xFF22D3EE),
    Color(0xFF38BDF8),
    Color(0xFF4F8CFF),
    Color(0xFF818CF8),
    Color(0xFFA78BFA),
    Color(0xFFC084FC),
    Color(0xFFE879F9),
    Color(0xFFF472B6),
    Color(0xFFFB7185),
    Color(0xFFF87171),
    Color(0xFFFB923C),
    Color(0xFFFBBF24),
    Color(0xFFA3E635),
    Color(0xFF4ADE80),
)

fun accentAt(index: Int): Color = Accents[index % Accents.size]

fun accentFor(id: Int): Color = Accents[Math.abs(id) % Accents.size]
