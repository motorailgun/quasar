- `quasar-*-full.apk`: on-device translation (PLaMo 3 Translate + llama.cpp, with the Snapdragon NPU backend). The model (about 550 MB) is downloaded when you turn translation on in Settings. For phones and tablets (arm64).
- `quasar-*-full-x86_64.apk`: the same for x86_64 devices (emulators, x86 Chromebooks).
- `quasar-*-lite.apk`: without on-device translation (smaller).

Both use the same package name and signing key, so you can install one over the other. Switching to lite deletes the downloaded translation model.

Verify an APK with `gh attestation verify <apk> --repo motorailgun/quasar`.
