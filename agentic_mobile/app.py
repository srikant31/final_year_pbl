import hashlib
import io
from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.concurrency import run_in_threadpool
from PIL import Image, UnidentifiedImageError
from .pipeline import Pipeline, GEMINI_MODEL, GEMINI_FALLBACK_MODEL, KeyMissing, GeminiUnavailable, api_key

app = FastAPI(title='Professor Agentic Android adapter')
pipeline = Pipeline()
MAX_BYTES = 15*1024*1024


@app.get('/health')
def health():
    return {'status':'ok','mode':'professor_agentic','gemini_model':GEMINI_MODEL,
            'gemini_fallback_model':GEMINI_FALLBACK_MODEL,
            'key_configured':bool(api_key()),'gate_loaded':pipeline._gate is not None}


@app.post('/analyze')
async def analyze(image: UploadFile = File(...)):
    data = await image.read(MAX_BYTES+1)
    if not data or len(data)>MAX_BYTES:
        raise HTTPException(413 if data else 400,'Choose a nonempty image up to15MiB.')
    try:
        with Image.open(io.BytesIO(data)) as opened:
            if opened.format not in {'JPEG','PNG','BMP'} or opened.width*opened.height>40_000_000:
                raise HTTPException(400,'Choose a JPEG/PNG/BMP up to40million pixels.')
        return await run_in_threadpool(pipeline.analyze, data)
    except HTTPException:
        raise
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError):
        raise HTTPException(400,'Cannot decode this image.')
    except KeyMissing as exc:
        raise HTTPException(503,str(exc))
    except GeminiUnavailable as exc:
        tried = ', '.join(f"{a['model']} ({a['status']})" for a in exc.attempts)
        raise HTTPException(502,f'Gemini analysis failed: {tried}. Try again later. No prediction was substituted.')
    except Exception:
        # SDK errors can contain request/auth details. Never return credential-bearing exceptions.
        raise HTTPException(502,'Agentic analysis failed. Check the key, internet, quota and original Gemini model availability on the laptop. No offline prediction was substituted.')
