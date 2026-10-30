package com.pokemontcg.sammlung.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(primary = Color(0xFFE53935), secondary = Color(0xFF1E88E5), tertiary = Color(0xFFFFC107))
private val LightColors = lightColorScheme(primary = Color(0xFFD32F2F), secondary = Color(0xFF1976D2), tertiary = Color(0xFFFFA000))

@Composable
fun TCGSammlungTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
