package es.aria_on_the_planet.quasar.data.cache

import android.content.Context
import android.os.storage.StorageManager
import androidx.core.content.getSystemService
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * API の結果のキャッシュ。消えても取り直せばよいので、スキーマが変わったら移行せずに作り直す。
 *
 * * アカウントごとに別のファイル (`cacheDir/api-cache/<AccountKey>/api-cache.db`)。中身はログイン中のユーザーから見た
 *   状態 (お気に入りしたか・関係・非公開のポスト) で、ID はサーバの中でしか一意でないため。ログアウトはディレクトリごと消す
 *   ([delete])。
 * * `cacheDir` の下なので、端末の設定画面では「キャッシュ」に数えられ、「キャッシュを削除」や容量不足のときの
 *   OS の削除の対象になる (バックアップ・端末間の移行の対象にもならない)。SQLite は本体・`-wal`・`-shm` の 3 つで 1 組なので、
 *   アカウントのディレクトリに [StorageManager.setCacheBehaviorGroup] を付けて、OS が消すときはまとめて消させる
 *   (1 つずつ消されると組がばらけて最新の書き込みを失う・壊れる)。壊れたら Android の既定のエラーハンドラが消して作り直す。
 * * 耐久性は要らないので、WAL + `synchronous = OFF` (コミットでも checkpoint でも fsync しない。ディスクへは OS が書く)。
 *   アプリが落ちても書いた内容は残り、失うのは OS ごと落ちた・電源が切れたときだけ。WAL は明示する (既定の AUTOMATIC は
 *   メモリの少ない端末で従来のジャーナルになる)。端末の既定の `synchronous` (エミュレーターでは NORMAL、メーカーによっては
 *   FULL) に左右されないようにする意味もある。
 */
@Database(entities = [CacheEntry::class], version = 1, exportSchema = true)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        const val FileName = "api-cache.db"
        private const val DirectoryName = "api-cache"

        /** アカウント [name] (`AccountKey`) のキャッシュを開く (ファイルが無ければ作る)。使い終わったら [close] する。 */
        fun create(context: Context, name: String): CacheDatabase {
            val app = context.applicationContext
            val directory = File(root(app), name).apply { mkdirs() }
            app.getSystemService<StorageManager>()?.setCacheBehaviorGroup(directory, true)
            return Room.databaseBuilder(app, CacheDatabase::class.java, File(directory, FileName).absolutePath)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(DisableSync)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        /** アカウント [name] のキャッシュを消す (ログアウト。[close] した後に呼ぶ)。 */
        fun delete(context: Context, name: String) {
            File(root(context.applicationContext), name).deleteRecursively()
        }

        /**
         * [keep] 以外のアカウントのキャッシュと、アカウントごとに分ける前のもの (`api-cache/api-cache.db`・以前の置き場所の
         * `databases/api-cache.db`) を消す (起動時。ログアウトの途中で終わった分も消える)。
         */
        fun deleteOthers(context: Context, keep: Set<String>) {
            val app = context.applicationContext
            val root = root(app)
            root.mkdirs()
            // 以前はディレクトリ全体を 1 組にしていた。今はアカウントのディレクトリごと
            runCatching { app.getSystemService<StorageManager>()?.setCacheBehaviorGroup(root, false) }
            root.listFiles().orEmpty().filter { it.name !in keep }.forEach { it.deleteRecursively() }
            app.deleteDatabase(FileName)
        }

        private fun root(context: Context) = File(context.cacheDir, DirectoryName)

        private object DisableSync : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                // PRAGMA は結果の行を返しうるので execSQL ではなく query で実行する
                db.query("PRAGMA synchronous = OFF").close()
            }
        }
    }
}
