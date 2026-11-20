package es.aria_on_the_planet.quasar.data.account

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 端末の中でアカウントを区別するキー (端末で振る UUID)。アカウントごとのファイル (キャッシュの DB・設定) の名前にも使う。
 *
 * `hashCode` がプロセスをまたいで変わらない (中身の String のもの) ことが要る: 切り替えで画面を `key(active)` で包むと、
 * 保存した状態の合成キーにこのキーの `hashCode` が入る (docs/multi-account.md の「切り替え」)。
 */
@Serializable
@JvmInline
value class AccountKey(val value: String) {
    override fun toString(): String = value

    companion object {
        fun random(): AccountKey = AccountKey(UUID.randomUUID().toString())
    }
}
