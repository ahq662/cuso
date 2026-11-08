package com.cuso.voice.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cuso.voice.BuildConfig
import com.cuso.voice.data.SessionStore
import com.cuso.voice.ui.components.PixelButton
import com.cuso.voice.ui.components.PixelChip
import com.cuso.voice.ui.components.PixelTopBar
import com.cuso.voice.ui.theme.BgElevated
import com.cuso.voice.ui.theme.BorderLight
import com.cuso.voice.ui.theme.Primary
import com.cuso.voice.ui.theme.Text
import com.cuso.voice.ui.theme.TextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val session = remember { SessionStore(context) }
    val user = session.user

    val joinDate = user?.createdAt?.takeIf { it > 0 }?.let {
        SimpleDateFormat("yyyy/M/d", Locale.CHINA).format(Date(it * 1000))
    } ?: "—"

    val avatarModel = user?.avatarUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { BuildConfig.API_BASE_URL + it }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(title = "我的")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            if (avatarModel != null) {
                AsyncImage(
                    model = avatarModel,
                    contentDescription = "头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(96.dp)
                        .background(BgElevated)
                        .border(2.dp, BorderLight),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(BgElevated)
                        .border(2.dp, BorderLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (user?.nickname ?: user?.username ?: "?").take(1),
                        color = Primary,
                        fontSize = 40.sp,
                    )
                }
            }

            Text(
                text = user?.nickname ?: "未登录",
                style = MaterialTheme.typography.headlineMedium,
                color = Text,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "@${user?.username ?: ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PixelChip("ID ${user?.id ?: "-"}")
                PixelChip("加入于 $joinDate")
            }

            Spacer(Modifier.height(32.dp))

            PixelButton(
                text = "退出登录",
                onClick = onLogout,
                danger = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
