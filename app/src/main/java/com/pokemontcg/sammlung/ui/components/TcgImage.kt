package com.pokemontcg.sammlung.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
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

/**
 * Set logos and symbols: some catalog variants (mcd prints) point to images that do not
 * exist on the CDN. Instead of the card placeholder - which reads like a broken link
 * icon in the logo spot - a failed image simply disappears.
 */
@Composable
fun TcgLogoImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var failed by remember(model) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        onState = { state -> if (state is AsyncImagePainter.State.Error) failed = true }
    )
}
