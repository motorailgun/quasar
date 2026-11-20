package es.aria_on_the_planet.quasar.data.session

/**
 * 文字列の保存先。実体は [SecureStorage] (Android Keystore で暗号化)。ユニットテストでは手元の Map で置き換える
 * (Keystore は JVM では動かない)。
 */
interface KeyValueStore {
    fun get(name: String): String?

    fun put(name: String, value: String)

    fun remove(name: String)
}
