"""Run locally in a terminal; key is hidden while typed, never placed in source/APK."""
import getpass
import json
from pathlib import Path

if __name__ == '__main__':
    key = getpass.getpass('Paste your NEW Gemini API key (hidden), then press Enter: ').strip()
    if not key or any(x.isspace() for x in key):
        raise SystemExit('Key not saved: enter one nonempty key without whitespace.')
    Path(__file__).with_name('secrets.local.json').write_text(json.dumps({'api_key':key}),encoding='utf-8')
    print('Key saved privately for the laptop backend. It is not in the APK or GitHub.')
