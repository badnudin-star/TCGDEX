package com.pokemontcg.sammlung.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.pokemontcg.sammlung.R

/** Displays a remote TCG image with a local fallback while loading or when loading fails. */
@Composable
fun TcgImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val fallback = painterResource(R.drawable.ic_card_placeholder)
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = fallback,
        error = fallback
    )
}
