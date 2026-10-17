package es.aria_on_the_planet.quasar.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.core.content.edit
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 機密情報 (アクセストークン・クライアントシークレット) の保存先。値は Android Keystore の AES 鍵 (端末の外に
 * 出せない) で AES-GCM 暗号化して SharedPreferences に置く。項目名を AAD にしているので、値を別の項目へ
 * 入れ替えても読めない。
 *
 * 鍵は端末から持ち出せないため、このファイルはバックアップ対象から外している (`backup_rules.xml` /
 * `data_extraction_rules.xml`。復元しても読めない)。読めない値 (鍵が消えた等) は消して `null` を返す。
 * (`EncryptedSharedPreferences` は非推奨になったので使わない。)
 */
class SecureStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KeyStoreType).apply { load(null) }

    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            decrypt(name, stored)
        } catch (e: GeneralSecurityException) {
            Log.w(TAG, "Dropping unreadable value: $name", e)
            remove(name)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Dropping malformed value: $name", e)
            remove(name)
            null
        }
    }

    fun put(name: String, value: String) {
        prefs.edit { putString(name, encrypt(name, value)) }
    }

    fun remove(name: String) {
        prefs.edit { remove(name) }
    }

    private fun encrypt(name: String, value: String): String {
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(name.toByteArray())
        val encrypted = cipher.doFinal(value.toByteArray())
        return "${encoder.encodeToString(cipher.iv)}:${encoder.encodeToString(encrypted)}"
    }

    private fun decrypt(name: String, stored: String): String {
        val iv = decoder.decode(stored.substringBefore(':'))
        val encrypted = decoder.decode(stored.substringAfter(':'))
        val cipher = Cipher.getInstance(Transformation)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TagBits, iv))
        cipher.updateAAD(name.toByteArray())
        return String(cipher.doFinal(encrypted))
    }

    private fun secretKey(): SecretKey =
        (keyStore.getEntry(KeyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey()

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreType).run {
            init(
                KeyGenParameterSpec.Builder(KeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }

    private companion object {
        const val TAG = "SecureStorage"

        /** backup_rules.xml / data_extraction_rules.xml の除外と合わせる。 */
        const val PrefsName = "secure"
        const val KeyStoreType = "AndroidKeyStore"
        const val KeyAlias = "quasar_secure_storage"
        const val Transformation = "AES/GCM/NoPadding"
        const val TagBits = 128
        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
