"""Export the untouched teacher checkpoint and verify desktop/ONNX parity.

Run from any directory with the standalone export environment. No cloud calls,
training, quantization, or modifications to teacher files occur here.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import shutil
import sys
import time
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "standalone"
ASSETS = BASE / "android/app/src/main/assets"
FIXTURES = BASE / "android/app/src/androidTest/assets"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-export", action="store_true", help="Recheck existing ONNX and refresh fixtures")
    args = parser.parse_args()
    import numpy as np
    import onnx
    import onnxruntime as ort
    import torch
    import torchvision
    from PIL import Image, __version__ as pillow_version

    torch.set_num_threads(2)
    # Import only Flowx. Its Tk application starts only in its __main__ guard.
    source = ROOT / "4-Class-HCSV/main.py"
    spec = importlib.util.spec_from_file_location("teacher_flowx", source)
    original = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(original)
    # Build the exact original architecture, but safely load only tensor weights
    # on CPU so the export does not depend on this machine's CUDA availability.
    checkpoint_path = Path(original.MODEL_PATH)
    checkpoint = torch.load(checkpoint_path, map_location="cpu", weights_only=True)
    state = checkpoint.get("model_state_dict", checkpoint)
    model = original.build_resnet50(len(original.CLASSES))
    model.load_state_dict(state)
    model.eval()
    ASSETS.mkdir(parents=True, exist_ok=True)
    FIXTURES.mkdir(parents=True, exist_ok=True)
    model_path = ASSETS / "flowx.onnx"
    started = time.monotonic()
    if not args.skip_export:
        with torch.inference_mode():
            torch.onnx.export(
                model, torch.zeros(1, 3, 224, 224), str(model_path),
                input_names=["image"], output_names=["logits"],
                opset_version=17, dynamo=False, external_data=False,
            )
    onnx.checker.check_model(str(model_path))
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    session = ort.InferenceSession(str(model_path), options, providers=["CPUExecutionProvider"])
    cases = []
    for file in ("crack.jpg", "honeycomb.jpg", "spalling.jpg", "void.jpg"):
        with Image.open(ROOT / "samples" / file) as opened:
            image = opened.convert("RGB")  # Preserve original: no EXIF transpose.
        tensor = original.pytorch_transform(image).unsqueeze(0)
        with torch.inference_mode():
            logits = model(tensor)
            probabilities = torch.softmax(logits, dim=1).numpy()[0]
        ort_logits = session.run(["logits"], {"image": tensor.numpy()})[0]
        shifted = ort_logits - ort_logits.max(axis=1, keepdims=True)
        ort_probs = (np.exp(shifted) / np.exp(shifted).sum(axis=1, keepdims=True))[0]
        error = float(np.max(np.abs(probabilities - ort_probs)))
        np.testing.assert_allclose(probabilities, ort_probs, rtol=1e-4, atol=1e-5)
        idx = int(probabilities.argmax())
        assert int(ort_probs.argmax()) == idx, "ONNX changed the top class"
        stem = Path(file).stem
        shutil.copy2(ROOT / "samples" / file, FIXTURES / file)
        image.save(FIXTURES / f"{stem}.png", format="PNG")
        tensor.numpy().astype("<f4").tofile(FIXTURES / f"{stem}.f32")
        case = {
            "file": file, "png_file": f"{stem}.png", "tensor_file": f"{stem}.f32",
            "width": image.width, "height": image.height,
            "probabilities": probabilities.tolist(), "onnx_probabilities": ort_probs.tolist(),
            "max_probability_error": error,
            "defect": original.CLASSES[idx], "severity": original.get_severity(float(probabilities[idx])),
        }
        cases.append(case)
        print(f"{file}: {case['defect']} {probabilities[idx]*100:.4f}% ; ONNX error={error:.8g}", flush=True)
    actions = {c: {s: original.get_action(c, s) for s in ("LOW", "MEDIUM", "HIGH")} for c in original.CLASSES}
    metadata = {
        "schema_version": 1, "model_name": original.MODEL_NAME,
        "classes": original.CLASSES, "image_size": 224,
        "mean": [0.485, 0.456, 0.406], "std": [0.229, 0.224, 0.225],
        "input_name": "image", "output_name": "logits",
        "input_shape": [1, 3, 224, 224], "actions": actions,
        "model_sha256": sha256(model_path), "source_sha256": sha256(source),
        "checkpoint_sha256": sha256(checkpoint_path),
        "preprocessing": "RGB, Pillow-compatible bilinear resize, NCHW, ImageNet normalization; no EXIF transpose",
        "severity_rule": "HIGH if confidence > 0.85; MEDIUM if > 0.60; otherwise LOW",
        "deployment": "float32 ONNX opset 17, CPU; no training or quantization",
    }
    (ASSETS / "model_metadata.json").write_text(json.dumps(metadata, indent=2, ensure_ascii=False), encoding="utf-8")
    shutil.copy2(ROOT / "logo.jpeg", ASSETS / "logo.jpeg")
    refs = {"schema_version": 1, "probability_tolerance": 0.002, "cases": cases}
    (FIXTURES / "reference.json").write_text(json.dumps(refs, indent=2), encoding="utf-8")
    results = {
        "status": "PASS", "model_bytes": model_path.stat().st_size,
        "metadata": metadata, "cases": cases, "seconds": time.monotonic() - started,
        "versions": {"torch": torch.__version__, "torchvision": torchvision.__version__,
                     "numpy": np.__version__, "pillow": pillow_version,
                     "onnx": onnx.__version__, "onnxruntime": ort.__version__},
        "scope": "Four supplied fixtures; consistency test, not an accuracy benchmark",
    }
    report = BASE / "verification/export-parity.json"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"PASS: {model_path} ({model_path.stat().st_size:,} bytes)", flush=True)


if __name__ == "__main__":
    main()
