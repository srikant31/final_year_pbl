# Standalone Android implementation

## Camera update — 29 October 2026 (current behavior)

Build **2.1-camera / versionCode2** adds **Take photo** alongside **Choose a photograph**. It opens the installed camera app, receives a full-resolution photo, previews it and runs the existing offline analysis. It is single-photo capture, not continuous camera inference. The updated APK is `standalone/dist/ConcreteVision-Standalone-v2-debug.apk`; the pre-camera backup is `standalone/dist/ConcreteVision-Standalone-v2-before-camera.apk`. Read `standalone/verification/camera/REPORT.md` and `summary.json` for current artifact identity, new-file responsibilities and evidence. Fourteen emulator tests passed (six camera, eight existing regressions). The camera fixture uses real cross-UID URI grants but no physical sensor; Samsung shutter flow still needs a manual check.

New sources: `CaptureStore.kt` (private scratch-file lifecycle + CaptureFileProvider), `PhotoPreview.kt` (display-only EXIF orientation), `res/xml/camera_paths.xml` (restricted provider root), `androidTest/CameraCaptureTest.kt`, and test-only `FixtureCameraActivity.java`. Manifest registers a non-exported provider and optional camera feature. AndroidX Core1.13.1 supplies FileProvider; no CAMERA, INTERNET or broad storage permission is requested. Its app-scoped signature receiver permission is expected. MainActivity persists pending capture filename during recreation, uses result request101, preserves old selection on cancellation, deletes scratch photos on reset/replacement and cleans old orphans. Captures are temporary, not saved inspection history. Original model/metadata bytes,32teacherfiles, trained weights, normalization and thresholds remain unchanged. Preview rotation does not rotate model inputs. This does not resolve the user's reported honeycombing/spalling prediction issue; compare the exact user photo across Python/phone before changing prediction logic. Older statements below that camera capture is unimplemented are historical and superseded by this update.


## Current performance revision (29 October 2026; authoritative update)

Read `standalone/verification/performance/REPORT.md` and its `summary.json` before using older release evidence below. The delivered APK has been updated after a matched performance pass and all eight regression tests pass again. The ONNX bytes, rules, float32 precision and all32original files are unchanged. Current first UI Analyze→report measured 3.974s vs37.919s original on this software-rendered emulator; launch→report9.315s vs42.796s, excluding picker interaction. Warm full-photo median304ms vs566ms. Synthetic processing6.72fps vs6.61fps is essentially unchanged and is NOT live-camera FPS. No physical phone or camera pipeline is tested.

Current code changes: OfflineClassifier memory-maps and SHA-verifies the uncompressed APK model instead of extracting/caching an app-private model file; prepare() eagerly initializes its session on MainActivity's existing worker, prepareInput(bitmap) exposes unchanged preprocessing for tests. ImagePreprocessor uses bounded coefficient caching/local loop variables with identical arithmetic. MainActivity still retains currentBitmap and calls predict(bitmap); no tensor-preparation cache and NO parallel-resize worker are shipped. Header-only logo decoding is sampled and unscaled, and the busy indicator is static text to reduce software rendering load. This does not change inspection-photo pixels. Older private model-cache/animated-progress descriptions below document the previous release, not this current loader/UI.

New test source: `standalone/android/app/src/androidTest/java/com/concretevision/offline/PerfBenchmarkTest.kt`. The Activity regression test now records UI stage timings and writes diagnostic thread stacks only on timeout. New report/evidence directory `standalone/verification/performance/` contains before-source snapshots, baseline/control/final timing JSON/logs, rejected-experiment diagnostics and current model/APK identity. `standalone/dist/ConcreteVision-Standalone-v2-before-perf.apk` is the preserved original APK. The ordinary v2-debug.apk path is the updated deliverable. Initial57.64s fresh-emulator latency and1.49fps were largely warm-up effects: do not claim those as a matched code speedup. Use matched controls in REPORT.md. Experimental parallel resize/early tensor caching stalled photo opening and were reverted. The benchmark's staged109ms result excludes preprocessing and is not user-visible Analyze latency.


This is a native Android application, package `com.concretevision.offline`, and can be installed beside the earlier laptop-backed `com.concretevision.mobile` application. It uses ONNX Runtime Android **1.24.3**, full-precision CPU inference, two intra-op threads and one inter-op thread. It does not contain a WebView, HTTP client, server-address screen or INTERNET permission.

Open this folder in Android Studio. SDK 35 and Build Tools 35.0.0 are expected. This PC's JDK 17 path is pinned in `gradle.properties`, and SDK location in `local.properties`. Those two paths must be updated on another PC. The Gradle wrapper is 8.9, AGP 8.7.3, Kotlin 2.0.21. Building downloads tooling/dependencies; using the completed app does not require a network connection.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug
.\gradlew.bat connectedDebugAndroidTest
```

Verified on 29 October 2026: debug APK build and `lintDebug` succeeded. A direct AndroidX instrumentation run passed **8 tests** on the Android 15/API 35 emulator, including all four real-model tensor/PNG/JPEG parity cases and the full Activity analysis/recreation/reset path. See `../verification/android-build-final.log` and `../verification/android-instrumentation-final.log`. Native JPEG probability tolerance is 0.002; original-tensor/PNG probability tolerance is 0.00002 and PNG tensor tolerance is 0.000002. These are consistency checks, not diagnostic accuracy measurements.

For retaining instrumentation evidence on the device, install both APKs with `adb install -r`, then run `adb shell am instrument -w -r com.concretevision.offline.test/androidx.test.runner.AndroidJUnitRunner`. Gradle's connected-device test task uninstalls its application packages afterward; direct instrumentation leaves them available for pulling results. The native UI screenshot is at `/sdcard/Android/data/com.concretevision.offline/files/qa-native-report.png`; private parity details are readable with `adb exec-out run-as com.concretevision.offline cat files/android-parity-results.json` on this debug build.

The model and metadata are generated by the parent `standalone/` export tooling before packaging. Do not substitute the teacher's `.pth` file into assets; Android loads the exported `.onnx` model. No training/quantization is performed by this application.

## Source files

| File | Responsibility |
|---|---|
| `settings.gradle.kts` | Defines plugin/dependency repositories and the single `app` module. |
| `build.gradle.kts` | Pins Android and Kotlin Gradle plugins. |
| `gradle.properties` | JVM heap, this PC's JDK 17 path, Kotlin style and AndroidX setting. |
| `local.properties` | This PC's SDK path; machine-specific and ignored by Git. |
| `.gitignore` | Excludes generated Gradle, IDE and build directories. |
| `gradlew`, `gradlew.bat` | Standard Unix/Windows wrapper launchers, copied from the earlier Android project. |
| `gradle/wrapper/gradle-wrapper.jar` | Standard Gradle wrapper bootstrap binary. |
| `gradle/wrapper/gradle-wrapper.properties` | Downloads Gradle 8.9 with a pinned distribution SHA-256. |
| `app/build.gradle.kts` | Defines package/version, API 24 minimum/API 35 target, Java 17, ARM64 and x86_64 ABIs, uncompressed ONNX assets, runtime dependency, AndroidX instrumentation runner and JUnit4 test-only dependencies. ONNX Runtime 1.24.3 itself requires API 24. |
| `app/src/main/AndroidManifest.xml` | Launchable Activity and app identity; no network or broad storage permissions. |
| `app/src/main/res/values/styles.xml` | Native dark Material theme, system-bar colors and accent. |
| `app/src/main/res/drawable/logo.jpeg` | Unmodified copy of the teacher's branding, used as screen logo and app icon. |
| `app/src/main/assets/flowx.onnx` | Exported four-class ResNet50 graph with float32 weights. Generated by the export script, bundled into the APK. |
| `app/src/main/assets/model_metadata.json` | Class order, normalization, model hash and all inherited action strings. Generated from the teacher's unchanged functions. |
| `app/src/main/java/com/concretevision/offline/MainActivity.kt` | Entire native screen, document-picker request/result, preview, single-worker execution, report rendering, reset, URI/result save/restore and lifecycle cleanup. |
| `app/src/main/java/com/concretevision/offline/ImageLoader.kt` | Bounded stream reading, JPEG/PNG/BMP validation, image dimension limits and raw RGB decoding. Removes embedded JPEG ICC/PNG color-profile chunks in memory so Android does not color-transform values that Pillow leaves unchanged. |
| `app/src/main/java/com/concretevision/offline/ImagePreprocessor.kt` | Pillow-compatible separable antialiased bilinear resize with 22-bit coefficients and intermediate byte rounding, then NCHW float32 normalization. |
| `app/src/main/java/com/concretevision/offline/OfflineClassifier.kt` | Metadata loading, checksum-verified first-use private model extraction, cached ONNX CPU session, tensor inference, softmax, strict severity thresholds and metadata action lookup. |
| `app/src/androidTest/java/com/concretevision/offline/OfflineInferenceTest.kt` | Eight tests: original-tensor/PNG/JPEG parity, strict severity boundaries, malformed image rejection, no-INTERNET assertion, initial/reset UI smoke, full URI-result/analysis/recreation/reset flow, bounded non-mutating ICC removal, and resize/pixel diagnostic artifact generation. |
| `app/src/androidTest/AndroidManifest.xml` | Registers a fixture provider only in the instrumentation APK. |
| `app/src/androidTest/java/com/concretevision/offline/FixtureProvider.java` | Read-only provider for the four sample JPEGs, allowing a normal Activity result callback to receive an image content URI without adding production test hooks. Java avoids a Kotlin dependency in this separate test-provider process. |
| `app/src/androidTest/assets/reference.json` | Python-generated expected probabilities/classes/severity and fixture filenames. |
| `app/src/androidTest/assets/*.jpg` | Four original sample photographs, test APK only. |
| `app/src/androidTest/assets/*.png` | Lossless Python-decoded RGB copies, isolating resize parity from JPEG decoder differences. Test APK only. |
| `app/src/androidTest/assets/*.f32` | Original normalized input tensors in little-endian float32. Test APK only. |
| `README_ANDROID.md` | This architecture, source map and build guide. |

`app/build/`, `.gradle/` and `.kotlin/` are generated tooling outputs, not handwritten application code. APKs appear under `app/build/outputs/apk/`. QA sample assets belong only to the separate instrumentation APK; they are not shipped with the regular application.

## Data flow and state

1. The Activity opens `ACTION_OPEN_DOCUMENT` for one JPEG/PNG/BMP. It requests persistent read permission so a selected URI can survive Activity/process recreation. No image is copied into inspection storage.
2. A single background executor reads at most 15 MiB and checks decode MIME type. Maximum source area is 40 million pixels, with each dimension at most 16,384. These bounds protect the mobile path and do not alter the teacher's source. Decode/analysis memory errors become a user-visible message.
3. Before decoding, JPEG ICC APP2 segments or PNG color-profile/gamma chunks are removed from an in-memory copy. This matches Pillow's raw RGB conversion rather than Skia's default profile transformation. Source files remain untouched. Decode uses ARGB_8888 with premultiplication disabled, then discards alpha. EXIF orientation is deliberately not applied, preserving the teacher's raw RGB semantics. A scaled display-only thumbnail is shown; inference retains the original decoded pixels.
4. Analyze invokes the custom Pillow-compatible resize, then ImageNet normalization into `[1,3,224,224]`. Inference starts on the same executor. The first request copies the model into app-private no-backup storage while checking its SHA-256, then creates a CPU session. Later requests reuse that session. An extracted model whose hash prefix matches the current bundled metadata is reused rather than rehashed on every launch.
5. ONNX logits are converted using numerically stable softmax. The largest probability selects the original class order `honeycombing`, `crack`, `spalling`, `void`. Confidence strictly above 0.85 selects HIGH; strictly above 0.60 selects MEDIUM; otherwise LOW. Action strings come verbatim from generated metadata.
6. UI updates occur on the main thread. Reset or a new image increments a generation token so obsolete worker results cannot repopulate cleared state. The single executor serializes pending work; resetting does not interrupt an already-running native inference.
7. The selected URI/name and completed report are saved in the Activity state bundle. Re-creation decodes the URI again and restores the report. Providers that do not support persistent URI permission may require selecting the image again. No inspection-history database exists.
8. Destruction queues session cleanup after existing executor work. A replacement Activity owns a new session. No network services, API keys or original desktop modules are initialized by the APK.

## Known inherited limitations

The classifier has no healthy/unknown/reject class and predicts one of four defects for any decodable image. The inherited severity is a confidence bucket, not measured structural damage. The fixed action text is carried over unchanged. Native JPEG decoding can differ slightly from Pillow; instrumentation tests separately compare original float tensors, lossless RGB PNGs and original JPEGs to expose where differences arise. No promise of exact JPEG probabilities should be made without the device test results.

The app currently bundles ARM64 phones and x86_64 emulators; 32-bit-only ARM phones are not supported. Android 7/API 24 or newer is required by the pinned runtime. Building/installing and passing the emulator tests does not establish real-phone performance, broad Android version coverage, or model accuracy. The Activity-flow test supplies a URI through the standard result callback, taps Analyze, captures a UI screenshot, recreates the Activity and resets it; it does not manually drive the system document picker. See the parent project's QA evidence and Claude handoff for measured results and remaining work.
