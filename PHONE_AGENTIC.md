# Phone-only Agentic mode - v2.3

Installed and opened on Samsung SM-A276B. Select Professor Agentic - online, take or choose a photo and Analyze. USB and the laptop backend are no longer required. The phone needs internet for Gemini. Your existing private key has been provisioned and encrypted on this phone; use Gemini key settings to replace/remove it or configure another phone. No key is compiled into the APK. APK: standalone/dist/ConcreteVision-v2.3-phone-agentic-debug.apk. This v2.3 build is published in the Phone-only Agentic AI release; the older v2.2 release remains available for comparison.

## Flow and fidelity

Original photo -> phone RGB decode (no EXIF rotation or ICC correction, matching the original policy) -> Pillow-style bicubic224x224 -> NHWC float32 RGB/255 -> exact original spalling_model.tflite -> original threshold >0.75 -> original prompt/full-resolution RGB JPEG -> direct HTTPS Gemini3.8 ->3.6 fallback on availability failure -> original regex parsing and action text -> phone report. <=0.75 skips the network and key lookup. Flowx offline, camera and picker remain.

No retraining, quantization, conversion or weight resizing was performed. TFLite hash is checked at runtime. Offline ONNX and metadata match the prior APK byte for byte; all32 teacher files are unchanged. AgenticPreprocessor is separate from the existing offline bilinear code. Four PNG samples matched Pillow's resized RGB pixels exactly on emulator and Samsung; all four gate scores match original1.0 within1e-5. This verifies tested parity, not accuracy or parity for every image.

Android uses Skia decoding/JPEG encoding instead of Pillow/libjpeg. Full resolution and JPEG quality75 are retained, but JPEG bytes and decoded pixels are not promised identical across these libraries. Gemini responses also vary across calls and model versions. The sample was reported as Crack/Low in this live test; no accuracy claim is made. Original low/medium/high action text and substring matching are preserved, tested against50 teacher rule cases.

## Verification

assembleDebug, assembleDebugAndroidTest and lintDebug passed (0errors/13warnings). Emulator suite: OK19 tests, including18 executed tests and1 deliberately skipped opt-in real-cloud test. Camera/offline regressions passed. Samsung tests: real direct cloud pipeline passed and4-image gate/pixel parity passed. All USB forwarding was removed before live validation. Samsung total35.059s, gate1.045s; primary3.8 HTTP503 after24.829s, fallback3.6 success7.707s. Test-only APK was removed from the phone afterward; production app/key remain. These are single-run timings, not an ongoing latency guarantee.

## Source responsibilities

PhoneGate.kt maps and SHA-verifies the unchanged TFLite asset and runs CPU LiteRT1.4.1. AgenticPreprocessor.kt implements separate Pillow bicubic NHWC preparation. AgenticClient.kt handles phone-side gating, full-resolution JPEG, direct Google requests, bounded attempts, response parsing and original action lookup. No localhost endpoint exists in this client. GeminiKeyStore.kt encrypts the user key with Android Keystore AES-GCM; backup is disabled. MainActivity.kt adds key settings and phone-only wording. network_security_config.xml disallows cleartext. agentic_contract.json contains the original prompt/action text, with no credentials. export_agentic_assets.py reproduces these assets without extracting the professor key. Existing Python backend is retained only for optional desktop comparisons.

## Building

Open standalone/android in Android Studio and select JDK17 under Gradle settings, or set JAVA_HOME to a JDK17 installation. The hard-coded org.gradle.java.home setting was removed. Set the SDK location through Android Studio or your own local.properties (not committed). BUILD_STANDALONE.cmd now exports Agentic assets and writes the v2.3 APK. No Android build-server claim is made: Gradle ran locally and downloaded the required Maven dependencies successfully. Initial LiteRT2.1 required newer Kotlin metadata; the build uses compatible LiteRT1.4.1 with the existing Kotlin toolchain, successfully exercised on both devices. No change to model files was needed.
