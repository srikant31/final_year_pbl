"""Preload the unchanged gate, then serve the USB-only laptop API."""
import uvicorn
from .app import app, pipeline

if __name__ == '__main__':
    print('Loading professor TFLite gate (no Gemini request)...',flush=True)
    pipeline._load()
    print('Gate ready. Waiting for phone requests. Key is read only when the gate calls Gemini.',flush=True)
    uvicorn.run(app,host='127.0.0.1',port=8001)
