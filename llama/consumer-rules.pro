# JNI (src/main/cpp/quasar_llama.cpp) から名前で呼ぶメソッド
-keep interface es.aria_on_the_planet.quasar.llama.GenerationCallback { *; }
-keepclassmembers class * implements es.aria_on_the_planet.quasar.llama.GenerationCallback {
    boolean onText(byte[]);
    boolean shouldStop();
}
