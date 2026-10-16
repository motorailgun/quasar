package es.aria_on_the_planet.quasar.ui.component.post

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** スレッドの縦線の位置 ([PostCard] のアイコンに合わせる)。 */
object ThreadLineDefaults {
    /** PostCard のアイコン中心の x 座標 (左余白 16dp + アイコン半径 20dp)。 */
    val AvatarCenterX: Dp = 36.dp

    /** PostCard のアイコン上端の y 座標。 */
    val AvatarTopY: Dp = 12.dp

    val Width: Dp = 2.dp
}

/** スレッドの縦線を [xs] の各位置に描く。[endY] が `null` なら下端まで。 */
fun Modifier.threadLines(
    xs: List<Dp>,
    color: Color,
    startY: Dp = 0.dp,
    endY: Dp? = null,
): Modifier = if (xs.isEmpty()) this else drawBehind {
    val top = startY.toPx()
    val bottom = endY?.toPx() ?: size.height
    xs.forEach { x ->
        val px = x.toPx()
        drawLine(color, Offset(px, top), Offset(px, bottom), strokeWidth = ThreadLineDefaults.Width.toPx())
    }
}
