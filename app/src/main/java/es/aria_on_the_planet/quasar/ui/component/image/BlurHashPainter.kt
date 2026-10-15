package es.aria_on_the_planet.quasar.ui.component.image

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter

private const val BlurHashSize = 24

/** 画像読み込み中のプレースホルダ用に BlurHash を [Painter] にする。 */
@Composable
fun rememberBlurHashPainter(blurHash: String?): Painter? = remember(blurHash) {
    blurHash ?: return@remember null
    val pixels = decodeBlurHash(blurHash, BlurHashSize, BlurHashSize) ?: return@remember null
    val bitmap = Bitmap.createBitmap(pixels, BlurHashSize, BlurHashSize, Bitmap.Config.ARGB_8888)
    BitmapPainter(bitmap.asImageBitmap())
}
