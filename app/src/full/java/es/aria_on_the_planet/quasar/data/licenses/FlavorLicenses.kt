package es.aria_on_the_planet.quasar.data.licenses

/**
 * full: 端末内の翻訳 (`:llama`) で入るもの。llama.cpp は submodule (third_party/llama.cpp) のタグを上げたら本文
 * (assets の licenses/llama.cpp.txt) も見直す。
 */
internal val FlavorBundledComponents = listOf(
    BundledComponent(
        id = "llama.cpp",
        name = "llama.cpp / ggml",
        version = "b12129",
        website = "https://github.com/ggml-org/llama.cpp",
        licenseName = "MIT License",
        licenseUrl = "https://github.com/ggml-org/llama.cpp/blob/master/LICENSE",
        textAssets = mapOf("" to "licenses/llama.cpp.txt"),
    ),
    // Hexagon (NPU) 入りのビルドだけ (本文は :llama が quasar.hexagonSdk のときだけ assets に入れる)。SDK のうち表記が
    // 要るのは BSD-3-Clause のヘッダ。それ以外 (Confidential のヘッダ・qaic の生成物・Hexagon Tools の initS.o 等) は
    // SDK の利用許諾に従う (docs/translation-ondevice.md)
    BundledComponent(
        id = "hexagon-sdk",
        name = "Qualcomm Hexagon SDK",
        version = "6.7.0.1",
        website = "https://softwarecenter.qualcomm.com/catalog/item/Hexagon_SDK",
        licenseName = "BSD 3-Clause License",
        licenseUrl = null,
        textAssets = mapOf("" to "licenses/hexagon-sdk.txt"),
        ifAssetPresent = true,
    ),
    // :llama は ANDROID_STL=c++_shared なので libc++_shared.so を APK に入れている
    BundledComponent(
        id = "libc++",
        name = "LLVM libc++",
        version = null,
        website = "https://libcxx.llvm.org/",
        licenseName = "Apache License 2.0 with LLVM Exceptions",
        licenseUrl = "https://llvm.org/LICENSE.txt",
        textAssets = mapOf("" to "licenses/libc++.txt"),
    ),
)

/**
 * 翻訳のモデル。APK には入れず Hugging Face から落とさせるが、訳文 (規約の OUTPUT) を出すので規約の写しと
 * 「Built with PLaMo」を出す (PLaMo Community License 第 4 条)。本文はモデルの取得元 (`TranslationModelSource` の
 * リビジョン) の LICENSE/en・LICENSE/ja。
 */
internal val TranslationModelComponent: BundledComponent? = BundledComponent(
    id = "plamo-3-translate",
    name = "PLaMo 3 Translate 0.8B",
    version = null,
    website = "https://huggingface.co/pfnet/plamo-3-translate-0.8b-GGUF",
    licenseName = "PLaMo Community License",
    licenseUrl = "https://plamo.preferredai.jp/info/plamo-community-license-en",
    textAssets = mapOf(
        "" to "licenses/plamo-community-license-en.txt",
        "ja" to "licenses/plamo-community-license-ja.txt",
    ),
)
