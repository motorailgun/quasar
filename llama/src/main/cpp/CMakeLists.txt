cmake_minimum_required(VERSION 3.31)

project("quasar_llama" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# OpenMP は使わない (ggml 自身のスレッドプールで動く)。使うと libomp.so (約 1.2MB) を APK に入れることになる
set(GGML_OPENMP OFF)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM")
elseif(ANDROID_ABI STREQUAL "x86_64")
    # エミュレーター用 (動作確認のみ。速さは見ない)
    set(GGML_SYSTEM_ARCH "x86")
    set(GGML_HEXAGON OFF CACHE BOOL "" FORCE)
else()
    message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

add_library(quasar_llama SHARED quasar_llama.cpp)
target_link_libraries(quasar_llama llama ggml android log)

# NPU 側で動く libggml-htp-v*.so は ExternalProject (htp-v73 等) で作られ、AGP がビルドするライブラリの依存に入っていない。
# ここで依存させて作らせる (APK に入れるのは llama/build.gradle.kts の CopyHtpSkels)
if(GGML_HEXAGON)
    foreach(V v73 v75 v79 v81 v85)
        if(TARGET htp-${V})
            add_dependencies(quasar_llama htp-${V})
        endif()
    endforeach()
endif()
