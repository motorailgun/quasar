package es.aria_on_the_planet.quasar.ui.component.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import es.aria_on_the_planet.quasar.ui.component.image.loader.animate
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings

/**
 * サーバ上の画像。UI から画像を出すときは Coil の `AsyncImage` を直接使わず、必ずこれ (端末内の画像は [LocalImage]) を使う。
 *
 * アニメーション画像 (GIF / APNG / Motion WebP) を動かすかは [kind] と `DisplaySettings` で決まる。止めるときは
 * [staticUrl] (API の `static_url` / `avatar_static` 等) があればそれを読み (通信量も減る)、無ければ先頭のフレームを出す。
 *
 * @param onLoaded 読み込めたときに画像の本来の大きさ (px) を受け取る。縦横比を後から決める表示に使う。
 * @param onError 読み込めなかったとき。別の URL を試す等は呼び出し側で行う。
 */
@Composable
fun RemoteImage(
    url: String?,
    contentDescription: String?,
    kind: ImageKind,
    modifier: Modifier = Modifier,
    staticUrl: String? = null,
    placeholder: Painter? = null,
    error: Painter? = null,
    contentScale: ContentScale = ContentScale.Fit,
    onLoaded: ((Size) -> Unit)? = null,
    onError: (() -> Unit)? = null,
) {
    val animate = LocalDisplaySettings.current.animates(kind)
    QuasarAsyncImage(
        data = if (animate) url else staticUrl ?: url,
        animate = animate,
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        contentScale = contentScale,
        onLoaded = onLoaded,
        onError = onError,
    )
}

/** 端末内の画像 (`content://` 等。投稿欄の添付)。アニメーションは「すべての画像」の設定だけに従う。 */
@Composable
fun LocalImage(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    QuasarAsyncImage(
        data = uri,
        animate = LocalDisplaySettings.current.animates(ImageKind.Other),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
private fun QuasarAsyncImage(
    data: String?,
    animate: Boolean,
    contentDescription: String?,
    modifier: Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    contentScale: ContentScale,
    onLoaded: ((Size) -> Unit)? = null,
    onError: (() -> Unit)? = null,
) {
    val context = LocalPlatformContext.current
    val request = remember(context, data, animate) {
        ImageRequest.Builder(context)
            .data(data)
            // Coil の既定 (URL そのもの) と同じだが、APNG の展開結果のキャッシュのキーとしてデコーダに渡すため明示する
            .diskCacheKey(data)
            .animate(animate)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        contentScale = contentScale,
        onSuccess = onLoaded?.let { callback -> { state -> callback(state.painter.intrinsicSize) } },
        onError = onError?.let { callback -> { callback() } },
    )
}
