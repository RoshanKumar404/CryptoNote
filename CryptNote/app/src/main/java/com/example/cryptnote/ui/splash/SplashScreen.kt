package com.example.cryptnote.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cryptnote.R
import com.example.cryptnote.data.preferences.ThemeColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    targetDestination: String,
    themeColor: ThemeColor = ThemeColor.WARM_ESPRESSO,
    onAnimationComplete: (destination: String) -> Unit
) {
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }

    val (bgColor, fgRes, titleColor, subtitleColor) = when (themeColor) {
        ThemeColor.WARM_ESPRESSO -> Quadruple(Color(0xFFF7EFE2), R.drawable.ic_launcher_fg_espresso, Color(0xFF32211C), Color(0xFFC88C42))
        ThemeColor.CYBER_TEAL -> Quadruple(Color(0xFF042F2C), R.drawable.ic_launcher_fg_teal, Color(0xFFE6FFFA), Color(0xFF2DD4BF))
        ThemeColor.NEON_BLUE -> Quadruple(Color(0xFF072B42), R.drawable.ic_launcher_fg_blue, Color(0xFFF0F9FF), Color(0xFF38BDF8))
        ThemeColor.ELECTRIC_PURPLE -> Quadruple(Color(0xFF260940), R.drawable.ic_launcher_fg_purple, Color(0xFFFAF5FF), Color(0xFFA855F7))
        ThemeColor.EMERALD_MATRIX -> Quadruple(Color(0xFF032E22), R.drawable.ic_launcher_fg_emerald, Color(0xFFECFDF5), Color(0xFF34D399))
        ThemeColor.SUNSET_AMBER -> Quadruple(Color(0xFF361603), R.drawable.ic_launcher_fg_amber, Color(0xFFFFFBEB), Color(0xFFFBBF24))
        ThemeColor.CRIMSON_ROSE -> Quadruple(Color(0xFF3B0715), R.drawable.ic_launcher_fg_rose, Color(0xFFFFF1F2), Color(0xFFFB7185))
    }

    LaunchedEffect(Unit) {
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        delay(300)
        launch {
            titleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500)
            )
        }
        delay(1200)
        onAnimationComplete(targetDestination)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .clip(RoundedCornerShape(32.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = fgRes),
                    contentDescription = "CryptoNote Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(130.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(titleAlpha.value)
            ) {
                Text(
                    text = "CryptoNote",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = titleColor
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Zero-Knowledge Encrypted Vault",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    ),
                    color = subtitleColor
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
