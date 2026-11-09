package com.awadytv.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlin.math.absoluteValue

private val placeholderGradients = listOf(
    listOf(Color(0xFF1B2A6B), Color(0xFF3A1462)),
    listOf(Color(0xFF0F3D3E), Color(0xFF14213D)),
    listOf(Color(0xFF4A1942), Color(0xFF1B1B3A)),
    listOf(Color(0xFF3D2C0F), Color(0xFF2B1B3A)),
    listOf(Color(0xFF123B5C), Color(0xFF0B0B0F)),
)

/**
 * Artwork with a deterministic gradient + initials placeholder, drawn only until the image loads (or when
 * it fails). The placeholder used to stay under every loaded image, doubling the GPU fill of every card.
 */
@Composable
fun Artwork(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    showInitials: Boolean = true,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    val placeholder = if (loaded) Modifier else Modifier.background(Brush.linearGradient(placeholderGradients[title.hashCode().absoluteValue % placeholderGradients.size]))
    Box(modifier.then(placeholder), contentAlignment = Alignment.Center) {
        if (showInitials && !loaded) {
            Text(
                text = title.initials(),
                style = AwadyType.Title,
                color = Color(0x66FFFFFF),
                textAlign = TextAlign.Center,
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Channel logo on a solid neutral tile. Logos come in every aspect ratio (mostly square, often with
 * transparency), so the logo is fitted inside padding on one flat colour; the gradient + initials
 * placeholder only appears when there is no logo or it fails to load (it used to show through as
 * coloured bands on both sides of every fitted logo).
 */
@Composable
fun ChannelLogo(url: String?, name: String, modifier: Modifier = Modifier, padding: Dp = 6.dp) {
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (failed) Brush.linearGradient(placeholderGradients[name.hashCode().absoluteValue % placeholderGradients.size]) else SolidColor(AwadyColors.LogoTile)),
        contentAlignment = Alignment.Center,
    ) {
        if (failed) {
            Text(name.initials(), style = AwadyType.Label, color = Color(0xB3FFFFFF), textAlign = TextAlign.Center, maxLines = 1)
        } else {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

private fun String.initials(): String =
    split(' ', '-', ':', '.').filter { it.isNotBlank() && it.first().isLetterOrDigit() }.take(2).joinToString("") { it.first().uppercase() }

/** Thin progress line (watch progress, EPG now-playing). */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp, color: Color = AwadyColors.TextPrimary) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color(0x40FFFFFF)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}

/** 2:3 poster card with lift focus; title and optional caption (e.g. AI reason) below. */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = AwadyDimens.PosterWidth,
    progress: Float? = null,
    caption: String? = null,
    badge: String? = null,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FocusLift(
            onClick = onClick,
            onFocus = onFocus,
            onLongClick = onLongClick,
            modifier = Modifier
                .width(width)
                .height(width * 1.5f),
        ) {
            Artwork(imageUrl, title, Modifier.fillMaxSize())
            if (badge != null) {
                Text(
                    badge,
                    style = AwadyType.Caption,
                    color = AwadyColors.TextPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (progress != null && progress > 0f) {
                ProgressLine(
                    progress,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(10.dp),
                )
            }
        }
        Text(title, style = AwadyType.Caption.copy(fontSize = AwadyType.Label.fontSize), color = AwadyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!caption.isNullOrBlank()) {
            Text(caption, style = AwadyType.Caption, color = AwadyColors.TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 16:9 card (continue watching, episodes) with title on an artwork scrim. */
@Composable
fun LandscapeCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    width: Dp = AwadyDimens.LandscapeWidth,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    FocusLift(
        onClick = onClick,
        onFocus = onFocus,
        onLongClick = onLongClick,
        modifier = modifier
            .width(width)
            .height(width * 9f / 16f),
    ) {
        Artwork(imageUrl, title, Modifier.fillMaxSize(), contentScale = contentScale)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xD9000000))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Text(title, style = AwadyType.Label, color = AwadyColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = AwadyType.Caption, color = AwadyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null && progress > 0f) {
                Spacer(Modifier.height(6.dp))
                ProgressLine(progress, Modifier.fillMaxWidth(), height = 3.dp)
            }
        }
    }
}

/** Channel tile: logo centred on glass, name below. */
@Composable
fun ChannelCard(
    name: String,
    logoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: Int? = null,
    onFocus: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier.width(176.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FocusLift(
            onClick = onClick,
            onFocus = onFocus,
            onLongClick = onLongClick,
            parallax = false,
            modifier = Modifier
                .width(176.dp)
                .height(99.dp),
        ) {
            ChannelLogo(logoUrl, name, Modifier.fillMaxSize(), padding = 14.dp)
            if (number != null) {
                Text(
                    number.toString(),
                    style = AwadyType.Caption,
                    color = AwadyColors.TextSecondary,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
        }
        Text(name, style = AwadyType.Caption.copy(fontSize = AwadyType.Label.fontSize), color = AwadyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
