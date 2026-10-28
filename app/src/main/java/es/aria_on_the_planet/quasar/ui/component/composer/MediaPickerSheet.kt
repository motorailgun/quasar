package es.aria_on_the_planet.quasar.ui.component.composer

import android.content.res.Configuration
import android.net.Uri
import android.util.Log
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.photopicker.compose.EmbeddedPhotoPicker
import androidx.photopicker.compose.ExperimentalPhotoPickerComposeApi
import androidx.photopicker.compose.rememberEmbeddedPhotoPickerState
import kotlinx.coroutines.launch

/**
 * 埋め込みの Photo Picker が使えるか (Android 14 以上 + SDK 拡張 15 以上)。使えなければ OS 標準の Photo Picker
 * (`PickMultipleVisualMedia`) を別画面で開く。
 */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
fun isEmbeddedPhotoPickerAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 15

/**
 * Photo Picker を埋め込んだ下からのシート。最初は半分の高さで開き、引き上げるとピッカーも全体表示
 * (アルバム等のタブ付き) に切り替わる。権限は要らず、選んだメディアだけが読める。
 *
 * 選択はその場で反映する: 選ぶたびに [onSelect]、選択を外すたびに [onDeselect] が呼ばれる。
 * [selected] (既に添付しているもの) は選択済みとして表示する。完了 (Done) で閉じる。
 *
 * NOTE: `androidx.photopicker` は alpha。API が変わったらこのファイルだけで吸収する。
 *
 * 見た目はピッカー側が描くので、こちらから変えられるのは色味 (アクセントカラー) とダーク / ライトのみ。タブの配置等は変えられない。
 *
 * @param maxItems 選べる最大数 (既に選択済みの分を含む)。
 * @param accentColor ピッカーの強調色。既定はアプリのテーマの primary 系 (色味をアプリにそろえる)。
 * @param onError セッションが開けない等。呼び出し側はシートを閉じて OS 標準のピッカーに切り替える。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPhotoPickerComposeApi::class)
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 15)
@Composable
fun MediaPickerSheet(
    selected: Set<Uri>,
    maxItems: Int,
    onSelect: (List<Uri>) -> Unit,
    onDeselect: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
    onError: (Throwable) -> Unit,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // ピッカーはアクセントの上の文字色を輝度で決め (0.6 以上で黒、未満で白)、ダークの primary (トーン 80) は 0.6 未満なので
    // 明るい面に白文字になり、選択中のタブの文字が読みにくい。
    // ライブラリ側の未成熟と判断し、アプリの色にそろえることを優先して primary のままにしている
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val pickerState = rememberEmbeddedPhotoPickerState(
        initialMediaSelection = selected,
        onUriPermissionGranted = onSelect,
        onUriPermissionRevoked = onDeselect,
        onSelectionComplete = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
        onSessionError = onError,
    )
    // シートを引き上げたらピッカーも全体表示 (タブ付き) に、下げたら簡易表示に戻す
    LaunchedEffect(sheetState, pickerState) {
        snapshotFlow { sheetState.currentValue == SheetValue.Expanded }
            .collect { pickerState.isExpanded = it }
    }
    val featureInfo = remember(maxItems, accentColor, darkTheme) {
        val builder = EmbeddedPhotoPickerFeatureInfo.Builder()
            .setMimeTypes(listOf("image/*", "video/*"))
            .setMaxSelectionLimit(maxItems.coerceIn(1, MediaStore.getPickImagesMaxLimit()))
            // アクセントを渡すとライト前提の配色になるので、ダーク / ライトも明示する
            .setThemeNightMode(if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        // 色は ARGB を符号なしの long で渡す。受け付けられない色 (明るすぎる等) なら付けずに開く
        try {
            builder.setAccentColor(accentColor.toArgb().toLong() and 0xFFFFFFFFL)
        } catch (e: IllegalArgumentException) {
            Log.w("MediaPickerSheet", "Accent color rejected by the photo picker", e)
        }
        builder.build()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        EmbeddedPhotoPicker(
            state = pickerState,
            embeddedPhotoPickerFeatureInfo = featureInfo,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        )
    }
}
