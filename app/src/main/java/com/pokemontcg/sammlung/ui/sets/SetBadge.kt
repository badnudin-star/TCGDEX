package com.pokemontcg.sammlung.ui.sets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text-based set mark for sets whose printed symbol image is unavailable in the catalog.
 * Shows the official set abbreviation (or a name fragment) on a deterministic background
 * color, so every set stays visually identifiable without loading any image.
 */
@Composable
fun SetBadge(
    setAbbreviation: String?,
    setName: String?,
    setId: String,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    contentDescription: String? = null
) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary,
        MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary,
        MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary,
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    )
    val (backgroundColor, foregroundColor) = palette[setBadgeColorIndex(setId, palette.size)]
    val label = setBadgeLabel(setAbbreviation, setName)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = foregroundColor,
            fontSize = (size.value * 0.32f).coerceAtLeast(6f).sp,
            lineHeight = (size.value * 0.32f).coerceAtLeast(6f).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
