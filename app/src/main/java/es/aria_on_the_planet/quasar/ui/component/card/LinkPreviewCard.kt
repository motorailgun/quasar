package es.aria_on_the_planet.quasar.ui.component.card

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import es.aria_on_the_planet.quasar.domain.model.PreviewCard
import es.aria_on_the_planet.quasar.ui.component.image.rememberBlurHashPainter
import java.net.URI

/** リンクプレビュー。デフォルトではタップでリンク先を開く。 */
@Composable
fun LinkPreviewCard(
    card: PreviewCard,
    modifier: Modifier = Modifier,
    onClick: ((PreviewCard) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    OutlinedCard(
        onClick = { onClick?.invoke(card) ?: runCatching { uriHandler.openUri(card.url) } },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (card.imageUrl != null) {
                val placeholder = rememberBlurHashPainter(card.blurhash)
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = null,
                    placeholder = placeholder,
                    error = placeholder,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = hostOf(card.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.title.ifBlank { card.url },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.description.isNotBlank()) {
                    Text(
                        text = card.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull() ?: url
