# Current release: v2.3 phone-only Agentic AI

The current Android app no longer uses the laptop backend. See [PHONE_AGENTIC.md](PHONE_AGENTIC.md) and the current root README. Earlier laptop-assisted or offline-only descriptions below are historical. The new APK is compiled, tested and installed on Samsung. No key is published.

# Claude / coding-assistant handoff

This is the public standalone v2 source distribution, prepared from the tested local project. Read README.md first. Preserve the trained model, class order, preprocessing arithmetic, confidence thresholds and recommendation text unless the user explicitly requests a separately validated change. No retraining is part of this implementation.

## Active implementation

Package com.concretevision.offline, Android7/API24+, target35, ARM64 and x86_64. Kotlin2.0.21, AGP8.7.3, Gradle8.9, JDK17, ONNX Runtime1.24.3 and AndroidX Core1.13.1. Version2.1-camera/code2. The published APK is identical to the camera-enabled build installed on the development Samsung SM-A276B.

MainActivity.kt builds the UI in Kotlin. It has Take photo and Choose a photograph, preview, Analyze, status, New inspection and result views. A single worker initializes the model ahead of selection and serializes loading/analysis. Generation tokens reject stale results after reset/replacement. Activity state stores current URI/name, completed prediction and pending camera filename. The result is not a saved inspection database.

CaptureStore.kt supplies the CaptureFileProvider subclass and validates UUID-based capture filenames beneath filesDir/captures. The manifest provider is non-exported and grants temporary access only to the resource root in res/xml/camera_paths.xml. ACTION_IMAGE_CAPTURE request101 passes EXTRA_OUTPUT plus ClipData and read/write flags. Read the full file on return; ignore thumbnail extras. Cancelling/empty output deletes the new scratch file and preserves prior selection; replacing/resetting deletes app-owned captures. Old orphans are cleaned after24hours, excluding restored current/pending files. Gallery originals and model files must never be deleted. No CAMERA or broad media/storage permission is required for delegated capture; no INTERNET permission is requested. AndroidX's app-scoped signature receiver permission is expected.

The document picker uses request100 and requests persistable URI permission when supported. ImageLoader reads at most15MiB, limits source pixels to40million and each dimension to16,384, and supports JPEG/PNG/BMP. It removes supported colour-profile metadata in an in-memory copy to preserve the original Pillow raw-RGB semantics. Do not remove this: ICC-bearing samples previously caused a real prediction difference. No source photo is rewritten by this step.

PhotoPreview.kt applies EXIF orientation/mirroring only to a display thumbnail. Inference uses the unchanged raw decoded image. EXIF rotation is not applied to the model input. This limitation needs explicit consideration before changing the pipeline; matching the old model and understanding field-photo correctness are different goals.

ImagePreprocessor.kt implements Pillow-compatible separable antialiased bilinear224x224 resize with22-bit coefficients and byte rounding, followed by NCHW float32 ImageNet normalization. Its bounded coefficient cache and local loop variables reduce work without changing arithmetic. Parallel resize and precomputing tensors during selection were attempted and rejected because Activity cold-opening stalled. Do not reintroduce them on the basis of isolated benchmark timings.

OfflineClassifier.kt loads model_metadata.json, memory-maps the uncompressed ONNX asset inside the APK, verifies SHA-256, and retains a direct mapped buffer for the CPU session lifetime. It does not extract a separate94MB model copy. Two intra-op threads, one inter-op thread; float32, no quantization. predict(bitmap) includes preprocessing; predictTensor() does not. Stable softmax selects the highest score, severity follows strict original thresholds and action strings come from metadata. Close only the Activity's session; the global OrtEnvironment is shared.

The header uses a sampled logo and static Working text rather than an animated spinner, reducing display overhead on a software-rendered emulator. These are display-only changes. No live camera capture/analyzer exists; Take photo delegates one still photograph to the camera app.

## Teacher code and model

4-Class-HCSV/main.py is the unchanged Flowx reference: original ResNet50 with final Dropout(0.5)+Linear4, eval inference, classes honeycombing/crack/spalling/void, RGB conversion,224square resize, tensor/normalization, softmax, strict severity and fixed repair strings. Its Tk application starts only under __main__. Export imports this module without launching Tk and loads the checkpoint safely on CPU. The checkpoint and ONNX are release assets referenced by release-assets.json; do not replace the ONNX asset with a .pth file.

The original model lacks healthy/no-defect/unknown rejection. It can assign spalling to normal walls or to the supplied crack.jpg. This problem exists in desktop behavior too. Four-fixture numerical parity is not an accuracy study. Diagnose a user's exact same image across desktop/mobile before changing preprocessing or claiming a conversion regression. Do not claim physical severity or structural safety from confidence.

## Tests and artifacts

CameraCaptureTest.kt covers full-file capture through a separate-UID test Activity, flags/output URI, inference/reset, cancellation preservation, empty output, missing camera app, pending-state recreation, preview rotation/nonmutation and provider restrictions. FixtureCameraActivity.java is test-only and writes a bundled sample rather than using a sensor. Java avoids separate test-process Kotlin classloader issues. FixtureProvider.java exposes only supplied sample fixtures to the instrumentation APK.

OfflineInferenceTest.kt checks original tensors, lossless PNG preprocessing, JPEG probabilities, severity boundaries, malformed images/profile parsing and Activity report restoration/reset. PerfBenchmarkTest.kt measures isolated processing and synthetic frames, not camera FPS; it removes only its own disposable generated model cache. Test assets are generated by export_model.py --skip-export and excluded from Git/user APK.

All14 targeted emulator tests passed for the release. Evidence includes camera/summary.json and performance/final-parity.json. Actual Samsung installation/launch succeeded; physical sensor/shutter confirmation was left to the user. No claims of broad phone/Android-version coverage or sustained thermal/battery performance.

## Reproduction and boundaries

Use download_assets.py to retrieve checksum-pinned model/checkpoint/APK from the public release. Configure local SDK location and JDK17; machine paths are intentionally absent. To regenerate tests, install requirements-export.txt and run export_model.py --skip-export. To rebuild: gradlew assembleDebug lintDebug. To run device tests after fixture generation: connectedDebugAndroidTest. Normal builds need only the ONNX model, not the checkpoint or training environment.

This clean public repository intentionally omits personal phone captures, credential-bearing unrelated cloud code, local Python environments, build caches, local QA failures/launch scripts and full private session notes. Do not attempt to reconstruct or publish credentials from the original teacher package. Any license/attribution added later should reflect actual rights rather than inventing a license grant.
