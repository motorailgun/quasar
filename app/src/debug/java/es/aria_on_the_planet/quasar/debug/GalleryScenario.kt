package es.aria_on_the_planet.quasar.debug

import android.os.Bundle
import androidx.annotation.StringRes
import es.aria_on_the_planet.quasar.R
import es.aria_on_the_planet.quasar.domain.model.settings.AvatarShape
import es.aria_on_the_planet.quasar.domain.model.settings.DisplaySettings

/**
 * ギャラリーで開ける画面。[id] は adb から指定する名前 (`--es scenario <id>`)、[fixture] は `assets/gallery/` の JSON。
 * シナリオを足すときは `scripts/gallery/generate.py` に JSON を足し、ここと [GalleryScreen] に 1 つずつ足す。
 */
internal enum class GalleryScenario(val id: String, @StringRes val title: Int, val fixture: String) {
    AnimatedEmoji("animated-emoji", R.string.gallery_animated_emoji, "animated-emoji.json"),
    AnimatedAvatars("animated-avatars", R.string.gallery_animated_avatars, "animated-avatars.json"),
    EmojiStress("emoji-stress", R.string.gallery_emoji_stress, "emoji-stress.json"),
    EmojiPicker("emoji-picker", R.string.gallery_emoji_picker, "emoji-picker.json"),
    /** 投稿欄のシート (絵文字ピッカー / 埋め込みの Photo Picker) をボタンで開く。絵文字は emoji-picker を使い回す。 */
    PickerSheets("picker-sheets", R.string.gallery_picker_sheets, "emoji-picker.json"),
    Profile("profile", R.string.gallery_profile, "profile.json"),
    PostVariety("post-variety", R.string.gallery_post_variety, "post-variety.json"),
    /** 返信先の表示 (バーの縮小版 / フル画面の X 風)。データは post-variety を使い回す。 */
    Reply("reply", R.string.gallery_reply, "post-variety.json"),
    /** 通知のユーザー単位のまとめの中身。データは profile (アカウント + ポスト) を使い回す。 */
    AccountReactions("account-reactions", R.string.gallery_account_reactions, "profile.json"),
    /** 相手からのフォローリクエストが来ているプロフィールのヘッダ。データは profile を使い回す。 */
    FollowRequest("follow-request", R.string.gallery_follow_request, "profile.json"),
    /** 自分のプロフィール (タブに Favourites・Bookmarks が出る)。どちらの一覧も post-variety のポスト。 */
    MyProfile("my-profile", R.string.gallery_my_profile, "profile.json"),
    /** 他人のプロフィール (Favourites・Bookmarks のタブは出ない)。 */
    OtherProfile("other-profile", R.string.gallery_other_profile, "profile.json"),
    /**
     * ポスト詳細の本体 (`reactionDetail`: 「N boosts」「M favourites」)。アイコンで操作 (手元だけ)、件数のタップで
     * リアクションしたユーザーの一覧 (ユーザーは post-variety の投稿者)。
     */
    PostDetail("post-detail", R.string.gallery_post_detail, "post-variety.json"),
    ;

    companion object {
        fun of(id: String?): GalleryScenario? = entries.firstOrNull { it.id == id }
    }
}

/** adb から表示設定を上書きする extra の名前。ビルドし直さずに設定どうしを比べるため。 */
internal object GalleryExtras {
    const val Scenario = "scenario"
    const val AnimateImages = "animate_images"
    const val AnimatePostEmojis = "animate_post_emojis"
    const val AnimateOtherEmojis = "animate_other_emojis"
    const val AnimateAvatars = "animate_avatars"

    /** 絵文字・アイコンのアニメーションの上限 (fps)。0 以下なら上限なし。 */
    const val MaxFps = "max_fps"

    /** UI・文字の倍率 (`--ef ui_scale 1.2`)。 */
    const val UiScale = "ui_scale"
    const val FontScale = "font_scale"

    /** 長文の折りたたみの行数 (`--ei long_post_max_lines 3`)。0 以下なら折りたたまない。 */
    const val LongPostMaxLines = "long_post_max_lines"

    /** アイコンの形 (`--es avatar_shape RoundedSquare`。`AvatarShape` の名前)。 */
    const val AvatarShape = "avatar_shape"
}

/** [extras] で指定された項目だけ上書きする (指定の無い項目はアプリの設定のまま)。 */
internal fun DisplaySettings.overriddenBy(extras: Bundle?): DisplaySettings {
    if (extras == null) return this
    fun flag(key: String, current: Boolean) = if (extras.containsKey(key)) extras.getBoolean(key) else current
    fun scale(key: String, current: Float) =
        if (extras.containsKey(key)) extras.getFloat(key).coerceIn(DisplaySettings.ScaleRange) else current
    return copy(
        animateImages = flag(GalleryExtras.AnimateImages, animateImages),
        animatePostEmojis = flag(GalleryExtras.AnimatePostEmojis, animatePostEmojis),
        animateOtherEmojis = flag(GalleryExtras.AnimateOtherEmojis, animateOtherEmojis),
        animateAvatars = flag(GalleryExtras.AnimateAvatars, animateAvatars),
        emojiAvatarAnimationMaxFps = if (extras.containsKey(GalleryExtras.MaxFps)) {
            extras.getInt(GalleryExtras.MaxFps).takeIf { it > 0 }
        } else {
            emojiAvatarAnimationMaxFps
        },
        uiScale = scale(GalleryExtras.UiScale, uiScale),
        fontScale = scale(GalleryExtras.FontScale, fontScale),
        longPostMaxLines = if (extras.containsKey(GalleryExtras.LongPostMaxLines)) {
            extras.getInt(GalleryExtras.LongPostMaxLines).takeIf { it > 0 }
        } else {
            longPostMaxLines
        },
        avatarShape = extras.getString(GalleryExtras.AvatarShape)
            ?.let { name -> AvatarShape.entries.firstOrNull { it.name == name } }
            ?: avatarShape,
    )
}
