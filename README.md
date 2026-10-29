# Concrete Vision - Phone-only Agentic AI (v2.3)

Fixed the laptop dependency in Agentic mode. The Android app now runs the unchanged TFLite gate on the phone and calls Gemini directly over the phone's internet. No laptop server, USB cable or USB forwarding is needed for analysis.

## Install and use

Download the [v2.3 APK](https://github.com/srikant31/final_year_pbl/releases/download/v2.3-phone-agentic/ConcreteVision-v2.3-phone-agentic-debug.apk) from the [Phone-only Agentic AI release](https://github.com/srikant31/final_year_pbl/releases/tag/v2.3-phone-agentic). Install it, open Gemini key settings and save your own key, then select Professor Agentic - online and take or choose a photo. Keys are encrypted with Android Keystore and are excluded from the APK/repository. The development Samsung already has its owner's key configured privately.

Flowx offline remains available without internet. Agentic requires phone internet because Gemini is a cloud service. Camera capture and the image picker remain available in both modes.

## What was fixed

- Moved the original TFLite gate from the laptop backend into the APK, preserving its trained bytes, RGB preprocessing and >0.75 threshold.
- Replaced localhost uploads with direct HTTPS Gemini calls. Gemini3.8 Flash falls back to3.6 Flash for availability failures; the report shows the answering model.
- Preserved the professor's prompt, response parsing and recommendation text. Added encrypted phone key settings; no embedded API key.
- Removed the machine-specific Gradle JDK path. Select JDK17 in Android Studio or set JAVA_HOME on your computer.
- Updated the UI to explain phone-only operation. Original offline ONNX/metadata remain byte-identical to v2.2.

## Validation and limitations

Android build and lint passed (0 errors,13 warnings). Emulator suite completed19 tests including one skipped opt-in cloud test. Samsung passed the real direct-cloud test with USB forwarding removed, plus four-image pixel/gate parity checks. The live run took35.1s: primary3.8 returned503, then3.6 answered in7.7s. These are single-run observations, not a speed guarantee. Gemini predictions can vary; accuracy remains unvalidated. Android JPEG decoding/encoding is not promised byte-identical to Pillow. Read [full test evidence](standalone/verification/phone_agentic/REPORT.md) and [phone-only flow/setup](PHONE_AGENTIC.md).

## Build

Use the `codex/agentic-ai-gemini-call` branch. Run `python standalone/tools/download_assets.py --only model` to download the checksum-pinned, unchanged Flowx ONNX asset. The TFLite and contract assets are included in this branch. Open `standalone/android` in Android Studio, set JDK17 and your SDK location, then Run. Or run `gradlew.bat assembleDebug lintDebug` from that directory with JAVA_HOME and the SDK configured. Existing Python launchers are optional desktop comparison tools for the older laptop-assisted pipeline.
