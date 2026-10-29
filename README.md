# final_year_pbl — Concrete Vision Offline

Standalone Android concrete-defect inspection app. **Take a photo** using the phone's camera or **choose an existing photograph**, then run the bundled four-class model locally. No laptop server or internet connection is needed for analysis.

## Install on Android

Download **[ConcreteVision-Standalone-v2-debug.apk](https://github.com/srikant31/final_year_pbl/releases/download/v2.1-camera/ConcreteVision-Standalone-v2-debug.apk)** from the **[v2.1-camera release](https://github.com/srikant31/final_year_pbl/releases/tag/v2.1-camera)** and install it on your phone.

- Android 7/API 24 or newer; ARM64 phones supported. x86_64 emulator libraries are also included.
- Approximately 154 MB download; allow additional RAM/storage for inference.
- App name: **Concrete Vision Offline**. Version: **2.1-camera**, versionCode **2**.
- This is a development-signed test APK, not a Play Store release.
- Take photo → use/confirm the captured photo → preview → Analyze photograph.
- Captures are temporary inspection inputs, not a permanent gallery. Reset/replacement removes app-owned captures.

The released APK is the same artifact installed on the development Samsung SM-A276B. Installation/launch succeeded there; the automated capture tests use a substitute camera Activity, not the phone sensor.

## Features

- Full-resolution camera capture through the installed camera app and restricted temporary content-URI grants.
- Existing-image picker and preview, including display-only EXIF orientation correction.
- Offline float32 ONNX Runtime CPU inference using the original Flowx ResNet50 weights.
- Honeycombing, crack, spalling and void predictions; confidence and four probability bars.
- Inherited confidence-based severity and original fixed recommendation text.
- Background model preparation/analysis, reset and Activity-state restoration.
- JPEG/PNG/BMP validation, 15 MiB input limit and bounded dimensions.

No live-camera/continuous inference, healthy/unknown category, saved site gallery, PDF export or quality-warning system is implemented. Future ideas are in [features.md](features.md).

## Build in Android Studio

Requirements: Android Studio, JDK **17**, SDK **35** / Build Tools **35.0.0**, Python 3.12 for asset download/export tools.

```powershell
git clone https://github.com/srikant31/final_year_pbl.git
cd final_year_pbl
python standalone/tools/download_assets.py --only model
```

Open **`standalone/android`** in Android Studio. Let Studio set your SDK location in its untracked `local.properties`, and select JDK17 for Gradle. Then select `app`, choose a device and press Run. The native screen is created in Kotlin; use the running app rather than an XML layout preview.

Command-line build after configuring your SDK and JAVA_HOME:

```powershell
cd standalone/android
.\gradlew.bat assembleDebug lintDebug
```

The APK is generated under `app/build/outputs/apk/debug/app-debug.apk`. Large model/APK/checkpoint files are release assets, not Git blobs. Downloads verify their pinned SHA-256 values from [release-assets.json](release-assets.json). Build/setup needs internet; installed analysis is offline.

## Reproduce export and device tests

The original four-class checkpoint is needed only for export/reference generation, not for ordinary Android builds.

```powershell
python standalone/tools/download_assets.py --only checkpoint
python -m venv .venv-export
.\.venv-export\Scripts\python.exe -m pip install torch==2.12.1 torchvision==0.27.1 --index-url https://download.pytorch.org/whl/cpu
.\.venv-export\Scripts\python.exe -m pip install -r standalone/requirements-export.txt
.\.venv-export\Scripts\python.exe -B standalone/tools/export_model.py --skip-export
cd standalone/android
.\gradlew.bat connectedDebugAndroidTest
```

`--skip-export` checks the released ONNX against the checkpoint and generates the test JPEG/PNG/tensor fixtures. Omit it to export a new float32 artifact from the same original checkpoint. No retraining or quantization is performed. Python's Windows installation must include Tkinter, which the original Flowx module imports; the exporter does not start its GUI.

## Model limitations and verification

This is a four-class classifier, not a structural safety assessment. It always picks one defect, including for healthy walls or unrelated images. High confidence is not proof of correctness. The inherited severity buckets (>85% HIGH, >60% MEDIUM, otherwise LOW) do not measure damage. Recommendations are original fixed strings. A supplied `crack.jpg` is classified as spalling by the original model as well.

Four matching examples establish conversion parity, not dataset accuracy. All14 targeted emulator tests passed: six capture/storage/provider/preview scenarios and eight existing inference/UI checks. Model and metadata bytes were unchanged by the camera addition; normalized PNG sample inputs matched exactly. Sanitized evidence is in `standalone/verification/`. Performance varies with emulator warm-up, device, image size and graphics; no live-camera FPS is established.

## Source map

| Path | Purpose |
|---|---|
| `standalone/android/app/src/main/java/com/concretevision/offline/` | Native Activity, loader, exact resize, ONNX inference, capture storage/provider and display preview |
| `standalone/android/app/src/main/res/xml/camera_paths.xml` | Restricts camera-sharing to app-owned capture files |
| `standalone/android/app/src/androidTest/` | Camera substitute, cross-UID URI tests, inference regressions and capacity benchmark |
| `standalone/android/app/src/main/assets/model_metadata.json` | Original class order, normalization, thresholds/action strings and model hashes |
| `standalone/tools/` | Public release downloader and original-model export/parity tooling |
| `4-Class-HCSV/main.py` | Unmodified teacher Flowx desktop implementation used by export |
| `samples/`, `logo.jpeg` | Supplied project sample images and branding |
| `release-assets.json` | Published binary destinations, sizes and SHA-256 hashes |
| `llm_handoff.md` | Public continuation notes for coding assistants |

The unrelated Agentic/cloud and three-class desktop branches, local environments/build caches, machine-specific configuration, credentials and personal phone-test images are excluded from this publication.
