plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "es.aria_on_the_planet.quasar"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "es.aria_on_the_planet.quasar"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // リリース鍵は環境変数から読む (CI の release ワークフローが入れる)。無ければ release は未署名で作る。
    // 鍵・パスワードはリポジトリ・gradle.properties に置かない
    val releaseKeystore = providers.environmentVariable("QUASAR_KEYSTORE_PATH").orNull
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("QUASAR_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("QUASAR_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("QUASAR_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
            }
        }
        // 性能の測定用 (release と同じ設定で、debug 鍵で署名する。applicationId が同じなので debug と入れ替えてもデータは残る)。
        // 測定にデバッグ用のギャラリーを使えるよう、ソースは debug と共有する (src/debug)
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }
    sourceSets {
        getByName("benchmark").setRoot("src/debug")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // 予測型「戻る」(NavigationBackHandler)
    implementation(libs.androidx.navigationevent.compose)
    // 画面遷移 (Activity は 1 つで、画面の行き来は Navigation 3)。ViewModel は画面 (エントリ) ごと
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    // 設定の保存
    implementation(libs.androidx.datastore.preferences)
    // OAuth のログイン画面を開く (Custom Tabs)
    implementation(libs.androidx.browser)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    // Material 3 の Custom colors (harmonize / ColorRoles) に使う
    implementation(libs.material.components)
    implementation(libs.coil.network.okhttp)
    // 添付する動画 (端末内) のサムネイル
    implementation(libs.coil.video)
    // アニメーション画像 (GIF / Motion WebP は coil-gif = Android の ImageDecoder、APNG は LINE の apng-drawable)
    implementation(libs.coil.gif)
    implementation(libs.apng.drawable)
    // 投稿画面に埋め込む Photo Picker (Android 14 + SDK 拡張 15 以上。それ以外は OS 標準の Photo Picker)
    implementation(libs.androidx.photopicker.compose)
    // クイック投稿バーの背景ブラー (後ろの一覧をぼかす)
    implementation(libs.haze.blur)
    // メディアの再生 (インライン / メディアビューア)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.androidx.media3.ui.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // 性能の測定: Perfetto のトレースに Composable 単位の区間を出す (composition tracing)
    "benchmarkImplementation"(libs.androidx.compose.runtime.tracing)
    "benchmarkImplementation"(libs.androidx.tracing.perfetto.binary)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
