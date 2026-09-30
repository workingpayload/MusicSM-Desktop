package com.example.musicsmd.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicsmd.ui.theme.OnDarkVariant

/**
 * A rounded album/playlist card for horizontal shelves — the mobile app's `AlbumCard`. Shelf cards
 * are plain artwork, not glass: glass is reserved for the chrome that floats over content.
 */
@Composable
fun AlbumCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(CARD_WIDTH)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        ArtworkImage(url = artworkUrl, size = CARD_WIDTH - 16.dp, shape = RoundedCornerShape(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        // Always one subtitle line (even if blank) so every card is the same height and the
        // artwork stays aligned across a shelf.
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = OnDarkVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A circular artist avatar with a name below — the mobile app's `ArtistCircle`. */
@Composable
fun ArtistCircle(
    name: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(130.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(url = artworkUrl, size = 114.dp, shape = CircleShape)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Mobile's 150 dp phone card is small on a desktop window; the proportions are kept. */
private val CARD_WIDTH = 172.dp
