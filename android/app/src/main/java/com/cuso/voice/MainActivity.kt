package com.cuso.voice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.cuso.voice.navigation.AppNav
import com.cuso.voice.ui.theme.BgBase
import com.cuso.voice.ui.theme.CusoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CusoTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgBase),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    ) {
                        AppNav()
                    }
                }
            }
        }
    }
}
