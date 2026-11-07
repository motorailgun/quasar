package es.aria_on_the_planet.quasar.ui.component.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.ConstraintsSizeResolver
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.SizeResolver
import es.aria_on_the_planet.quasar.ui.component.image.loader.animate
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings

/**
 * サーバ上の画像。UI から画像を出すときは Coil の `AsyncImage` を直接使わず、必ずこれ (端末内の画像は [LocalImage]) を使う。
 *
 * アニメーション画像 (GIF / APNG / Motion WebP) を動かすかは [kind] と `DisplaySettings` で決まる。止めるときは
 * [staticUrl] (API の `static_url` / `avatar_static` 等) があればそれを読み (通信量も減る)、無ければ先頭のフレームを出す。
 * 絵文字・アイコンは描き直しの上限 (`DisplaySettings.emojiAvatarAnimationMaxFps`) も掛かる (今は APNG のみ)。
 *
 * @param onLoaded 読み込めたときに画像の本来の大きさ (px) を受け取る。縦横比を後から決める表示に使う。
 * @param fitHeight 読み込む大きさを表示の高さだけで決める (幅は画像の縦横比のまま)。読み込んでから枠の幅を画像に合わせる
 *   表示 (絵文字) 用。既定では最初に測った枠に収まるよう縮めて読み、後から枠が広がっても読み直さないので、仮の正方形の
 *   枠で読んだ横長の画像が引き伸ばされてぼやける。
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
    fitHeight: Boolean = false,
) {
    val settings = LocalDisplaySettings.current
    val animate = settings.animates(kind)
    QuasarAsyncImage(
        data = if (animate) url else staticUrl ?: url,
        animate = animate,
        maxFps = settings.animationMaxFps(kind),
        contentDescription = contentDescription,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        contentScale = contentScale,
        onLoaded = onLoaded,
        onError = onError,
        fitHeight = fitHeight,
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
    maxFps: Int? = null,
    placeholder: Painter? = null,
    error: Painter? = null,
    contentScale: ContentScale,
    onLoaded: ((Size) -> Unit)? = null,
    onError: (() -> Unit)? = null,
    fitHeight: Boolean = false,
) {
    val context = LocalPlatformContext.current
    val heightResolver = if (fitHeight) remember { HeightSizeResolver() } else null
    val request = remember(context, data, animate, maxFps, heightResolver) {
        ImageRequest.Builder(context)
            .data(data)
            // Coil の既定 (URL そのもの) と同じだが、APNG の展開結果のキャッシュのキーとしてデコーダに渡すため明示する
            .diskCacheKey(data)
            .animate(animate, maxFps)
            .apply {
                // 大きめに読んだ結果がメモリキャッシュにあればそれを使えるよう INEXACT (縮めずに出す)
                if (heightResolver != null) size(heightResolver).precision(Precision.INEXACT)
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = if (heightResolver != null) modifier.then(heightResolver.constraints) else modifier,
        placeholder = placeholder,
        error = error,
        contentScale = contentScale,
        onSuccess = onLoaded?.let { callback -> { state -> callback(state.painter.intrinsicSize) } },
        onError = onError?.let { callback -> { callback() } },
    )
}

/** 測った枠の高さだけを読み込む大きさにする (幅は決めない = 画像の縦横比のまま)。 */
private class HeightSizeResolver : SizeResolver {
    /** 枠の大きさを測る。AsyncImage の Modifier に付ける。 */
    val constraints = ConstraintsSizeResolver()

    override suspend fun size(): coil3.size.Size = coil3.size.Size(Dimension.Undefined, constraints.size().height)
}
