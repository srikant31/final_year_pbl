"""Reuse teacher functions/prompt without importing or using his embedded API key."""
from __future__ import annotations
import ast
from dataclasses import dataclass
import hashlib
import io
import json
import os
from pathlib import Path
import re
import threading
import time

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'Agentic-AI/main.py'
MODEL = ROOT / 'Agentic-AI/model/spalling_model.tflite'
KEY_FILE = Path(__file__).with_name('secrets.local.json')


def contract():
    tree = ast.parse(SOURCE.read_text(encoding='utf-8'))
    funcs = [n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name in
             {'load_tflite', 'parse_response', 'get_action'}]
    namespace = {'re': re}
    exec(compile(ast.Module(body=funcs, type_ignores=[]), str(SOURCE), 'exec'), namespace)
    classify = next(n for n in ast.walk(tree) if isinstance(n, ast.FunctionDef) and n.name == 'classify_image')
    prompt = next(ast.literal_eval(n.value) for n in ast.walk(classify) if isinstance(n, ast.Assign)
                  and any(isinstance(t, ast.Name) and t.id == 'prompt' for t in n.targets))
    model_name = next(ast.literal_eval(k.value) for n in ast.walk(classify) if isinstance(n, ast.Call)
                      and isinstance(n.func, ast.Attribute) and n.func.attr == 'generate_content'
                      for k in n.keywords if k.arg == 'model')
    return namespace, prompt, model_name


FUNCTIONS, PROMPT, ORIGINAL_GEMINI_MODEL = contract()
# Explicitly approved compatibility change: Google rejects2.5 Flash for this new account.
# Original source and trained local artifacts are unchanged.
GEMINI_MODEL = 'gemini-3.8-flash'
GEMINI_FALLBACK_MODEL = 'gemini-3.6-flash'
# One attempt per model: avoid SDK retries consuming the phone's150s timeout.
GEMINI_ATTEMPTS = ((GEMINI_MODEL, 25_000), (GEMINI_FALLBACK_MODEL, 45_000))


def api_key():
    key = os.environ.get('CONCRETE_VISION_GEMINI_KEY', '').strip()
    if not key and KEY_FILE.is_file():
        key = str(json.loads(KEY_FILE.read_text(encoding='utf-8')).get('api_key', '')).strip()
    return key


class KeyMissing(Exception):
    pass


@dataclass(frozen=True)
class GeminiReply:
    text: str
    model: str
    attempts: tuple[dict, ...]


class GeminiUnavailable(Exception):
    """Only sanitized model/status/timing metadata; never raw SDK exception text."""
    def __init__(self, attempts):
        self.attempts = tuple(attempts)
        super().__init__('Gemini could not complete analysis; no prediction was substituted.')


def failure_kind(exc):
    """Fallback only for availability errors, not bad credentials or safety decisions."""
    import httpx
    code = getattr(exc, 'code', None)
    if code in {404, 408, 429, 500, 502, 503, 504}:
        return f'http_{code}'
    if isinstance(exc, (TimeoutError, httpx.TimeoutException)):
        return 'timeout'
    if isinstance(exc, (ConnectionError, httpx.NetworkError, httpx.RemoteProtocolError)):
        return 'connection_error'
    return None


def generate_with_fallback(contents):
    key = api_key()
    if not key:
        raise KeyMissing('Configure the new Gemini key on the laptop; no professor key is used.')
    from google import genai
    from google.genai import types
    attempts = []
    for model, timeout in GEMINI_ATTEMPTS:
        started = time.perf_counter()
        try:
            options = types.HttpOptions(timeout=timeout,
                retry_options=types.HttpRetryOptions(attempts=1))
            with genai.Client(api_key=key, http_options=options) as client:
                response = client.models.generate_content(model=model, contents=contents)
                text = response.text
        except Exception as exc:
            kind = failure_kind(exc)
            attempts.append({'model': model, 'status': kind or 'request_rejected',
                             'elapsed_ms': (time.perf_counter()-started)*1000})
            if kind is None:
                raise GeminiUnavailable(attempts) from None
            continue
        # A blocked/empty answer is not a capacity failure: do not try to bypass it.
        if not isinstance(text, str) or not text.strip():
            attempts.append({'model': model, 'status': 'empty_or_blocked_response',
                             'elapsed_ms': (time.perf_counter()-started)*1000})
            raise GeminiUnavailable(attempts)
        attempts.append({'model': model, 'status': 'success',
                         'elapsed_ms': (time.perf_counter()-started)*1000})
        return GeminiReply(text, model, tuple(attempts))
    raise GeminiUnavailable(attempts)


class Pipeline:
    def __init__(self):
        self._lock = threading.Lock()
        self._gate = None

    def _load(self):
        if self._gate is None:
            import tensorflow as tf
            FUNCTIONS['load_tflite'].__globals__['tf'] = tf
            self._gate = FUNCTIONS['load_tflite'](str(MODEL))
        return self._gate

    def gate(self, image):
        import numpy as np
        with self._lock:
            interp, in_det, out_det = self._load()
            # Exact teacher expression: default PIL resize, NHWC float32, divide by255.
            arr = np.expand_dims(np.array(image.resize((224,224)), dtype=np.float32)/255.0, axis=0)
            interp.set_tensor(in_det[0]['index'], arr)
            interp.invoke()
            return float(interp.get_tensor(out_det[0]['index'])[0][0])

    def gemini(self, jpeg):
        from google.genai import types
        return generate_with_fallback(
            [PROMPT, types.Part.from_bytes(data=jpeg, mime_type='image/jpeg')])

    def analyze(self, data, *, gate_override=None, gemini_override=None):
        from PIL import Image
        started = time.perf_counter()
        with Image.open(io.BytesIO(data)) as opened:
            image = opened.convert('RGB')  # Exact original; no EXIF/ICC transformation.
        gate_start = time.perf_counter()
        score = (gate_override or self.gate)(image)
        gate_ms = (time.perf_counter()-gate_start)*1000
        called = score > 0.75
        raw = None
        used_model = GEMINI_MODEL
        attempts = ()
        if not called:
            defect, severity = 'No Defect', 'LOW'
            action = FUNCTIONS['get_action']('no defect', 'low')
        else:
            buf = io.BytesIO()
            image.save(buf, format='JPEG')  # Full-size RGB, original Pillow JPEG defaults.
            reply = (gemini_override or self.gemini)(buf.getvalue())
            if isinstance(reply, GeminiReply):
                raw, used_model, attempts = reply.text, reply.model, reply.attempts
            else:
                raw = reply  # Existing Python-only parity fixtures return strings.
            defect, severity = FUNCTIONS['parse_response'](raw)
            action = FUNCTIONS['get_action'](defect, severity)
        return {'mode':'professor_agentic','model_name':'Concrete Vision',
                'gate_score':score, 'gate_threshold':0.75,
                'gate_verdict':'Defect Detected' if called else 'No Defect',
                'gemini_called':called,'gemini_model':used_model,
                'gemini_primary_model':GEMINI_MODEL,
                'gemini_fallback_used':called and used_model != GEMINI_MODEL,
                'gemini_attempts':list(attempts),
                'defect':defect,'severity':severity,'action':action,
                'raw_response':raw,'gate_ms':gate_ms,
                'elapsed_ms':(time.perf_counter()-started)*1000,
                'image_sha256':hashlib.sha256(data).hexdigest(),
                'prompt_sha256':hashlib.sha256(PROMPT.encode()).hexdigest()}
