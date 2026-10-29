"""Download checksum-pinned deployment assets from this repository's public release."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--only', choices=['model', 'checkpoint', 'apk', 'all'], default='model')
    args = parser.parse_args()
    manifest = json.loads((ROOT / 'release-assets.json').read_text())
    suffixes = {'model': '.onnx', 'checkpoint': '.pth', 'apk': '.apk'}
    for item in manifest['assets']:
        if args.only != 'all' and not item['name'].endswith(suffixes[args.only]):
            continue
        target = ROOT / item['path']
        if target.is_file() and sha256(target) == item['sha256']:
            print('Already verified:', target.relative_to(ROOT))
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_name(target.name + '.part')
        url = f"https://github.com/{manifest['repository']}/releases/download/{manifest['tag']}/{item['name']}"
        print('Downloading', item['name'], flush=True)
        try:
            request = urllib.request.Request(url, headers={'User-Agent': 'ConcreteVision-Asset-Download'})
            with urllib.request.urlopen(request, timeout=120) as response, temporary.open('wb') as output:
                while block := response.read(1024 * 1024):
                    output.write(block)
            if temporary.stat().st_size != item['bytes'] or sha256(temporary) != item['sha256']:
                raise ValueError('Release asset checksum mismatch: ' + item['name'])
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        print('Verified:', target.relative_to(ROOT), flush=True)


if __name__ == '__main__':
    main()
