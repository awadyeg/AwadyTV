package com.awadytv.feature.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.awadytv.core.designsystem.Artwork
import com.awadytv.core.designsystem.AwadyColors
import com.awadytv.core.designsystem.AwadyDimens
import com.awadytv.core.designsystem.AwadyType
import com.awadytv.core.designsystem.GlassSurface

/** Full-bleed artwork behind a detail page, faded into the canvas on the left and bottom. */
@Composable
fun DetailBackdrop(imageUrl: String?, title: String) {
    Box(Modifier.fillMaxSize()) {
        if (!imageUrl.isNullOrBlank()) {
            Artwork(imageUrl, title, Modifier.fillMaxSize(), showInitials = false)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(0f to AwadyColors.Canvas.copy(alpha = 0.96f), 0.55f to AwadyColors.Canvas.copy(alpha = 0.75f), 1f to AwadyColors.Canvas.copy(alpha = 0.25f))),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to AwadyColors.Canvas)),
        )
    }
}

/** Poster (2:3) in a glass frame on the left, text column + actions on the right. */
@Composable
fun DetailHeader(
    title: String,
    poster: String?,
    meta: String,
    modifier: Modifier = Modifier,
    tagline: String? = null,
    plot: String? = null,
    credits: List<Pair<String, String?>> = emptyList(),
    actions: @Composable RowScope.() -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = AwadyDimens.SafeHorizontal)) {
        GlassSurface(shape = RoundedCornerShape(AwadyDimens.RadiusLarge), elevation = 24.dp) {
            Artwork(
                poster,
                title,
                Modifier
                    .padding(6.dp)
                    .size(width = 180.dp, height = 270.dp)
                    .clip(RoundedCornerShape(AwadyDimens.RadiusMedium)),
            )
        }
        Spacer(Modifier.width(36.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = AwadyType.Display.copy(fontSize = AwadyType.Display.fontSize * 0.85f), color = AwadyColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) Text(meta, style = AwadyType.Label, color = AwadyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!tagline.isNullOrBlank()) Text(tagline, style = AwadyType.Caption, color = AwadyColors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!plot.isNullOrBlank()) {
                Text(plot, style = AwadyType.Body, color = AwadyColors.TextSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
            credits.filter { !it.second.isNullOrBlank() }.forEach { (label, value) ->
                Row {
                    Text("$label  ", style = AwadyType.Caption, color = AwadyColors.TextTertiary)
                    Text(value!!, style = AwadyType.Caption, color = AwadyColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            extra?.invoke(this)
        }
    }
}

fun metaLine(vararg parts: String?): String = parts.filter { !it.isNullOrBlank() }.joinToString("  ·  ")

/**
 * Plays a YouTube trailer. The YouTube TV app only starts playback for a watch URL sent to it directly;
 * "vnd.youtube:" just opens its home / profile picker, so that is the last resort.
 */
fun openTrailer(context: Context, youtubeId: String) {
    val id = youtubeId.substringAfter("v=").substringBefore('&').substringAfterLast('/').trim()
    val watch = Uri.parse("https://www.youtube.com/watch?v=$id")
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, watch).setPackage("com.google.android.youtube.tv"),
        Intent(Intent.ACTION_VIEW, watch).setPackage("com.google.android.youtube"),
        Intent(Intent.ACTION_VIEW, watch),
        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // try the next handler
        }
    }
    Toast.makeText(context, "No app available to play the trailer", Toast.LENGTH_SHORT).show()
}
