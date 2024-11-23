package com.tonecolor.slamandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.tonecolor.slamandroid.ui.theme.SlamAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlamAndroidTheme {
                AppContent()
            }
        }
    }

    @Composable
    fun AppContent() {
        val showSplash = remember { mutableStateOf(true) }

        if (showSplash.value) {
            Splash(onAnimationEnd = { showSplash.value = false })
        } else {
            MainScreen()
        }
    }

    @Composable
    fun Splash(onAnimationEnd: () -> Unit) {
        val composition = rememberLottieComposition(LottieCompositionSpec.Asset("animations/splash.json"))
        val progress = animateLottieCompositionAsState(composition.value)

        if (progress.isAtEnd && progress.isPlaying) {
            onAnimationEnd()
        }

        LottieAnimation(
            composition = composition.value,
            progress = { progress.value },
        )
    }

    @Composable
    fun MainScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Red),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Main Screen")
        }
    }

    @Preview
    @Composable
    fun PreviewAppContent() {
        SlamAndroidTheme {
            AppContent()
        }
    }
}