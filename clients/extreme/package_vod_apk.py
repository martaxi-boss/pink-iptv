"""Deliver the exact APK already exercised by this CI run; never rebuild it."""
import hashlib
import json
import os
from pathlib import Path

root = Path(os.environ['RUNNER_TEMP'])
source = os.environ['GITHUB_SHA']
original = root / 'pink-deliverable/PINK-IPTV-Extreme-1.9.0-debug.apk'
assert json.loads(original.with_name('provenance.json').read_text())['implementation_sha'] == source
apk = original.read_bytes()
digest = hashlib.sha256(apk).hexdigest()
checksums = original.with_name('SHA256SUMS').read_text().splitlines()
assert any(line.split() == [digest, original.name] for line in checksums)
output = root / 'pink-vod-delivery'
output.mkdir()
parts = []
size = 24 * 1024 * 1024
for offset in range(0, len(apk), size):
    data = apk[offset:offset + size]
    name = f'part-{len(parts):02d}'
    output.joinpath(name).write_bytes(data)
    parts.append({'name': name, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
assert 0 < len(parts) <= 4
output.joinpath('manifest.json').write_text(json.dumps({
    'filename': f'PINK-IPTV-VOD-{source[:8]}.apk', 'source': source,
    'source_ci_run': int(os.environ['GITHUB_RUN_ID']), 'size': len(apk),
    'sha256': digest, 'parts': parts}, indent=2) + '\n')
with open(os.environ['GITHUB_OUTPUT'], 'a') as handle:
    handle.write(f'part_count={len(parts)}\n')
print(f'EXACT_VOD_TESTED_APK_BYTES=PASS;source={source};sha256={digest}')
