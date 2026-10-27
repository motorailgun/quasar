package es.aria_on_the_planet.quasar.ui.component.user

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import es.aria_on_the_planet.quasar.domain.model.settings.AvatarShape
import es.aria_on_the_planet.quasar.ui.settings.LocalDisplaySettings

/** 角丸四角形のアイコンの角の半径 (アイコンの一辺に対する割合)。 */
private const val RoundedSquareCornerFraction = 0.2f

/**
 * ユーザーのアイコンを切り取る形 (設定の [AvatarShape])。アイコンの切り取り・取得前のプレースホルダ・影に使う。
 *
 * [ring] はアイコンを背景色で縁取る枠 (アイコンの外側に [ring] の幅の余白を足した面) に使うときの縁の幅。角丸四角形なら
 * 角の半径を縁の幅だけ大きくし、アイコンの角と同心にする。
 */
@Composable
@ReadOnlyComposable
fun avatarShape(ring: Dp = 0.dp): Shape = LocalDisplaySettings.current.avatarShape.toShape(ring)

fun AvatarShape.toShape(ring: Dp = 0.dp): Shape = when (this) {
    // 凸形なので影を付けてもよい (凹みのある形の影は非常に重い。AGENTS.md)
    AvatarShape.Circle -> CircleShape
    AvatarShape.RoundedSquare -> if (ring == 0.dp) RoundedSquare else RoundedCornerShape(AvatarCornerSize(ring))
}

private val RoundedSquare = RoundedCornerShape(AvatarCornerSize(0.dp))

/** 中のアイコン (一辺から縁の幅を除いたもの) の [RoundedSquareCornerFraction] + 縁の幅。 */
private data class AvatarCornerSize(private val ring: Dp) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val ringPx = with(density) { ring.toPx() }
        return (shapeSize.minDimension - ringPx * 2).coerceAtLeast(0f) * RoundedSquareCornerFraction + ringPx
    }
}
