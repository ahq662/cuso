package com.cuso.voice.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cuso.voice.ui.components.FieldLabel
import com.cuso.voice.ui.components.PixelButton
import com.cuso.voice.ui.components.PixelCard
import com.cuso.voice.ui.components.PixelInput
import com.cuso.voice.ui.components.PixelSprite
import com.cuso.voice.ui.theme.BgBase
import com.cuso.voice.ui.theme.Danger
import com.cuso.voice.ui.theme.Dimens
import com.cuso.voice.ui.theme.Text
import com.cuso.voice.ui.theme.TextDim
import com.cuso.voice.ui.theme.TextMuted

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onGoRegister: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgBase)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelSprite("sprites/sheep.json", width = 128.dp)
            Text(
                text = "CUSO 语音",
                style = MaterialTheme.typography.displayLarge,
                color = Text,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "PIXEL VOICE CHAT",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))

            PixelCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            ) {
                Text("登录", style = MaterialTheme.typography.headlineMedium, color = Text)
                Text(
                    text = "欢迎回来，羊羊在等你",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                )

                FieldLabel("用户名")
                PixelInput(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "输入用户名",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                Spacer(Modifier.height(12.dp))

                FieldLabel("密码")
                PixelInput(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "输入密码",
                    password = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )

                if (state.error != null) {
                    Text(
                        text = state.error ?: "",
                        color = Danger,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                PixelButton(
                    text = if (state.loading) "登录中…" else "登 录",
                    onClick = { viewModel.login(username.trim(), password) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(
                    onClick = onGoRegister,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "没有账号？去注册",
                        color = TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
