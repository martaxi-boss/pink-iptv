#!/usr/bin/env python3
"""Generate deterministic test-only media; never provider subtitles or production tracks."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile

output = Path(sys.argv[1])
output.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='pink-vod-fixtures-') as tmp:
    root = Path(tmp)
    for language in ('PT', 'EN'):
        (root / f'{language}.srt').write_text(
            f'1\n00:00:00,000 --> 00:01:00,000\nPINK fixture {language}\n')
    multi = output / 'multi-tracks.mkv'
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
        '-f', 'lavfi', '-i', 'testsrc2=size=160x90:rate=12',
        '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000',
        '-f', 'lavfi', '-i', 'sine=frequency=880:sample_rate=48000',
        '-i', str(root / 'PT.srt'), '-i', str(root / 'EN.srt'),
        '-map', '0:v', '-map', '1:a', '-map', '2:a', '-map', '3:s', '-map', '4:s',
        '-t', '60', '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '36',
        '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '32k', '-c:s', 'srt',
        '-metadata:s:a:0', 'language=por', '-metadata:s:a:1', 'language=eng',
        '-metadata:s:s:0', 'language=por', '-metadata:s:s:1', 'language=eng',
        '-disposition:s:1', 'forced', str(multi)], check=True)
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
        '-i', str(multi), '-map', '0:v', '-map', '0:a:0', '-c', 'copy',
        str(output / 'single-audio.mp4')], check=True)
for name, audio_count, text_count in [('multi-tracks.mkv', 2, 2), ('single-audio.mp4', 1, 0)]:
    streams = json.loads(subprocess.check_output(['ffprobe', '-v', 'error',
        '-show_streams', '-of', 'json', str(output / name)], text=True))['streams']
    assert sum(s['codec_type'] == 'audio' for s in streams) == audio_count
    assert sum(s['codec_type'] == 'subtitle' for s in streams) == text_count
    if text_count:
        assert any(s['codec_type'] == 'subtitle' and s['disposition']['forced'] for s in streams)
print('REAL_VOD_FIXTURE_CONTAINER_TRACKS=PASS;multi=2audio/2subtitle/forced;single=1audio/0subtitle')
