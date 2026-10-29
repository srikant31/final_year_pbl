# Professor Agentic pipeline on Android

This adapter adds a separate online mode while retaining Flowx offline, camera capture and the image picker. It does not retrain, resize/quantize weights or change either trained artifact. The four-class ONNX remains on the phone. The original spalling_model.tflite runs unchanged on the laptop; Gemini remains Google's online service.

## Exact flow

Original photo bytes from Android → laptop Pillow RGB → original default PIL224×224 resize → NHWC float32 /255 → original TFLite interpreter → first output [0][0] → <=0.75 gives No Defect/LOW, otherwise original prompt and full-size RGB JPEG → gemini-3.8-flash request; on availability failure, gemini-3.6-flash with identical contents → original regex parser → original get_action rules → phone report.

pipeline.py loads only the three required teacher functions and literal prompt/model name from the original source AST. The original embedded key is never executed or used. Original helper/parser quirks remain. The original desktop handler is run with GUI stubs in parity tests to compare branch, tensor, prompt/JPEG request and action. The gate can still be inaccurate; preserved behavior is not a safety or accuracy validation.

## Setup / college demonstration

1. SETUP_AGENTIC.cmd creates .venv-agentic with Python3.12 dependencies.
2. The new key is already configured on this PC. On a new PC only, run SET_GEMINI_KEY.cmd. Paste your new key into its hidden terminal prompt. It writes ignored agentic_mobile/secrets.local.json. Never put this file or key into the public repository or APK. An explicitly set CONCRETE_VISION_GEMINI_KEY environment variable is another option. No ambient/teacher GEMINI_API_KEY is used.
3. Start START_AGENTIC_SERVER.cmd and leave its window open. It preloads TFLite, binds only127.0.0.1:8001, and does not call Gemini until an image passes the gate. Keep the laptop online.
4. Connect/unlock one physical phone, authorize USB debugging, then run CONNECT_AGENTIC_USB.cmd. Re-run forwarding after reconnecting the phone. Android Studio is not needed for ordinary demonstrations.
5. Open Concrete Vision, select Professor Agentic - online, take/choose an image, then Analyze. The selected original file is uploaded to the laptop; Google receives its full-size re-encoded JPEG only when the gate passes. Offline mode does not send the photo.
6. OPEN_PROF_AGENTIC.cmd launches the teacher desktop App with your new key injected in memory and its hosted-model keyword replaced in memory; a client adapter applies the same3.8-to3.6 fallback policy. Select the exact same file there to compare. No original file is edited and the embedded key is never passed to a client by this launcher.

Phone base URL is127.0.0.1:8001 through adb reverse, including emulator tests when reverse is configured. Cleartext is permitted only for loopback; other destinations require HTTPS. This is a localhost/USB demonstration setup, not a publicly hosted service. No network/API-key authentication is added because the API is not bound to the LAN; do not change binding to0.0.0.0 without a separate hosted-service design.

## Preserved features and display

Take photo and Choose a photograph remain available in both modes. Offline inference remains the same float32 ONNX and numerical preprocessing. Agentic displays gate score, threshold/branch, whether Gemini was called, defect, severity and original recommendations. It does not invent Gemini confidence or four-class probabilities. Completed online reports restore with Activity recreation. Changing mode clears the previous mode's report; errors never silently become an offline prediction. Reset stops the phone waiting; a cloud request already executing on the server may still finish.

Gemini outputs can vary between independent requests. Compare local gate tensors/scores, request content and parser/action behavior; do not promise identical cloud answers. The original source still specifies gemini-2.5-flash; Google returned404 for generation with the new key. The user explicitly approved gemini-3.8-flash, now selected by the backend and desktop comparison wrapper. This hosted-version difference can change Gemini answers; identical cloud scores/results are not guaranteed. Local trained weights, prompt, gate/preprocessing, parser and action rules remain unchanged. The user subsequently authorized automatic fallback to the older gemini-3.6-flash. Both models receive the identical original prompt and JPEG bytes. Only availability errors (404/408/429/500/502/503/504 or network/timeouts) trigger fallback. Bad credentials, invalid requests and blocked/empty answers do not. One attempt per model is made:25s primary timeout,45s fallback timeout, with SDK automatic retries disabled. These are per-request timeouts, not an exact wall-clock guarantee; transport/setup can add overhead. No offline prediction is substituted for a failed cloud analysis.

## Tests / files

- app.py: health and bounded multipart analysis endpoints. No static files or key-serving endpoint. Key status is boolean only; SDK exception text is not returned to avoid credential disclosure.
- pipeline.py: original-function/prompt loading, shared interpreter lock, original gate/Gemini contents, bounded availability fallback, and per-request model/attempt/timing metadata. Actual answering model is returned as gemini_model; fallback metadata is never stored on a shared mutable Pipeline instance. Tests inject responses only through Python test arguments, not an HTTP switch.
- set_key.py: local hidden key entry, never prints the value.
- run_server.py: preloads gate before accepting requests.
- run_desktop.py: original GUI with new private key and approved primary keyword substituted in memory; DesktopFallbackClient shares the backend fallback service and shows the answering model in the window title. Original source unchanged.
- tests/test_pipeline.py: threshold boundaries, original handler equivalence, actual TFLite sample scores, missing key behavior, parser quirks, HTTP validation and error redaction. Cloud is mocked during these tests.
- AgenticClient.kt: streams original URI bytes as multipart; no resizing/re-encoding on Android, no embedded key, explicit timeouts/errors.
- AgenticModeTest.kt: real local HTTP socket tests for raw file uploads, both gate-result shapes, error handling and online UI/mode restoration. Responses are fixtures, not live Gemini.

Run backend checks with `.venv-agentic\Scripts\python.exe -B -m unittest agentic_mobile.tests.test_pipeline -v`. Android instrumentation tests use the emulator. Fourteen backend checks passed after adding fallback. A real backend image request succeeded in38.775s:3.8 returned504 after25.402s, then3.6 generated a report in10.070s. A separate direct3.6 image request succeeded in15.938s. Across those two3.6 calls, the same sample produced Honeycombing/Low and Crack/Low respectively; request success is not a prediction-accuracy or consistency claim. Primary3.8 generation still has not succeeded. Physical-phone cloud report display still needs manual verification; the installed APK already displays the returned model name and requires no rebuild for this server-only change. No paid/cloud call is made by automated fixture tests; the separate live probes were real requests. This integration remains local; the already-public offline GitHub release is not changed automatically.
