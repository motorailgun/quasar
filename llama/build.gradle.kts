import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

// Snapdragon の NPU (Hexagon) のバックエンドも作るか。Hexagon SDK の場所を local.properties (または -P) の
// quasar.hexagonSdk で渡したときだけ (SDK は Qualcomm の Docker イメージから取り出す。docs/translation-ondevice.md)
val hexagonSdk: String? = providers.gradleProperty("quasar.hexagonSdk").orNull
    ?: rootProject.file("local.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) }.getProperty("quasar.hexagonSdk") }

// llama.cpp (third_party/llama.cpp、git submodule) を NDK でビルドし、薄い JNI (src/main/cpp/quasar_llama.cpp) で包む。
// llama.cpp の API はここの外に出さない (Kotlin の LlamaModel だけを見せる)
android {
    namespace = "es.aria_on_the_planet.quasar.llama"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 33
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            // 実機 (arm64) とエミュレーター (x86_64)
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    // libllama / libggml* を別々の .so にし、CPU のバックエンドは CPU の機能ごとの版 (dotprod / i8mm 等) を
                    // 全部作って実行時に選ぶ (GGML_BACKEND_DL)。選ぶには .so がファイルとして展開されている必要がある
                    // (app の useLegacyPackaging)
                    "-DANDROID_STL=c++_shared",
                    "-DBUILD_SHARED_LIBS=ON",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_BACKEND_DL=ON",
                    "-DGGML_CPU_ALL_VARIANTS=ON",
                    "-DGGML_LLAMAFILE=OFF",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_BUILD_APP=OFF",
                    "-DLLAMA_OPENSSL=OFF",
                )
                if (hexagonSdk != null) {
                    // arm64 だけ (CMakeLists.txt で x86_64 は切る)
                    arguments += listOf("-DGGML_HEXAGON=ON", "-DHEXAGON_SDK_ROOT=$hexagonSdk", "-DPREBUILT_LIB_DIR=android_aarch64")
                }
            }
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

/**
 * NPU 側で動く libggml-htp-v*.so (Hexagon の版ごと) は llama.cpp の CMake が ExternalProject で作るので、AGP は APK に入れない。
 * ビルドの結果から arm64-v8a の jniLibs に写す (実行時は ADSP_LIBRARY_PATH = nativeLibraryDir から FastRPC が読む)。
 */
abstract class CopyHtpSkels : DefaultTask() {
    @get:InputFiles
    abstract val skels: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val abiDir = outputDir.get().dir("arm64-v8a").asFile
        abiDir.deleteRecursively()
        abiDir.mkdirs()
        check(!skels.isEmpty) { "libggml-htp-v*.so not found (Hexagon build failed?)" }
        skels.files.forEach { it.copyTo(File(abiDir, it.name), overwrite = true) }
    }
}

androidComponents {
    onVariants { variant ->
        if (hexagonSdk == null) return@onVariants
        val name = variant.name.replaceFirstChar(Char::uppercase)
        val copy = tasks.register<CopyHtpSkels>("copy${name}HtpSkels") {
            dependsOn("externalNativeBuild$name")
            skels.from(fileTree(".cxx") { include("**/arm64-v8a/build-llama/ggml/src/ggml-hexagon/libggml-htp-v*.so") })
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(copy, CopyHtpSkels::outputDir)
        // SDK の BSD-3-Clause のヘッダの表記 (app の設定の「ライセンス」が、あれば出す)。SDK を上げたら著作権の行も見直す
        variant.sources.assets?.addStaticSourceDirectory("src/hexagon/assets")
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
