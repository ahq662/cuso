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
fun RegisterScreen(
    onRegistered: () -> Unit,
    onGoLogin: () -> Unit,
    viewModel: AuthViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onRegistered()
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
            PixelSprite("sprites/sheep.json", width = 96.dp)
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

            Spacer(Modifier.height(20.dp))

            PixelCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            ) {
                Text("注册", style = MaterialTheme.typography.headlineMedium, color = Text)
                Text(
                    text = "创建账号，加入频道一起开黑",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextDim,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
                )

                FieldLabel("用户名")
                PixelInput(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "3-20 位，字母/数字/下划线/中文",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                Spacer(Modifier.height(12.dp))

                FieldLabel("昵称（可选）")
                PixelInput(
                    value = nickname,
                    onValueChange = { nickname = it },
                    placeholder = "别人看到的名字",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                Spacer(Modifier.height(12.dp))

                FieldLabel("密码")
                PixelInput(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "至少 6 位",
                    password = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )

                Spacer(Modifier.height(12.dp))

                FieldLabel("确认密码")
                PixelInput(
                    value = confirm,
                    onValueChange = { confirm = it },
                    placeholder = "再输入一次",
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
                    text = if (state.loading) "注册中…" else "注 册",
                    onClick = {
                        viewModel.register(username.trim(), password, confirm, nickname.trim())
                    },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                )

                TextButton(
                    onClick = onGoLogin,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "已有账号？去登录",
                        color = TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
