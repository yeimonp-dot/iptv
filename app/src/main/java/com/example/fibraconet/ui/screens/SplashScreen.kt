package com.example.fibraconet.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.fibraconet.R
import kotlinx.coroutines.delay

// ─── Colores del logo ───────────────────────────────────────────────────────
private val BgDark     = Color(0xFF000000)
private val GlowCyan   = Color(0xFF00C8FF)
private val GlowPurple = Color(0xFF6B00FF)

@Composable
fun SplashScreen(onFinished: () -> Unit) {

    // ── Phase tracker ────────────────────────────────────────────────────────
    var phase by remember { mutableIntStateOf(0) }

    // ── Animations ───────────────────────────────────────────────────────────

    // Glow halo behind logo
    val glowAlpha by animateFloatAsState(
        targetValue  = if (phase >= 1) 0.55f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing), label = "glowAlpha"
    )
    val glowScale by animateFloatAsState(
        targetValue  = if (phase >= 1) 1.6f else 0.2f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing), label = "glowScale"
    )

    // Logo scale: elastic overshoot (spring)
    val logoScale by animateFloatAsState(
        targetValue  = if (phase >= 2) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue  = when {
            phase < 2  -> 0f
            phase >= 4 -> 0f          // fade out
            else       -> 1f
        },
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "logoAlpha"
    )

    // Shimmer sweep (infinite while visible, but we only trigger it in phase 3)
    val shimmerX by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue   = -1f,
        targetValue    = 2f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerX"
    )
    val shimmerAlpha by animateFloatAsState(
        targetValue  = if (phase == 3) 0.35f else 0f,
        animationSpec = tween(300), label = "shimmerAlpha"
    )

    // Full-screen fade-out
    val screenAlpha by animateFloatAsState(
        targetValue  = if (phase >= 4) 0f else 1f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "screenAlpha"
    )

    // ── Phase timeline ───────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        delay(200)  // brief black frame
        phase = 1   // glow expands
        delay(700)
        phase = 2   // logo scales in
        delay(900)
        phase = 3   // shimmer
        delay(900)
        phase = 4   // fade out
        delay(600)  // wait for fade out animation to finish
        onFinished()
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlpha)
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {

        // ── Radial glow halo ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(340.dp)
                .scale(glowScale)
                .alpha(glowAlpha)
                .blur(60.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(GlowCyan.copy(alpha = 0.7f), GlowPurple.copy(alpha = 0.3f), Color.Transparent)
                    )
                )
        )

        // ── Logo ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(240.dp)
                .scale(logoScale)
                .alpha(logoAlpha),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter  = painterResource(id = R.drawable.fibraconet_logo),
                contentDescription = "FibraConet",
                modifier = Modifier.fillMaxSize()
            )

            // Shimmer overlay (diagonal white sweep)
            if (shimmerAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(shimmerAlpha)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.6f),
                                    Color.Transparent
                                ),
                                start = androidx.compose.ui.geometry.Offset(shimmerX * 240f, 0f),
                                end   = androidx.compose.ui.geometry.Offset(shimmerX * 240f + 120f, 240f)
                            )
                        )
                )
            }
        }
    }
}
