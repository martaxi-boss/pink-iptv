"""Bounded immutable takeover audit; historical059 events are never rewritten."""
import fnmatch
import hashlib
import json
from pathlib import Path
import subprocess

TASK = '.project-leader/tasks/PINK-IPTV-PHYSICAL-UI-CERTIFICATION-061.json'
AUTH = 'b0aca9850ed55b8be0fc424af553e64fb574956b'
DIGEST = '65b7f6b86c07a1763c1903c25d04caef02f72e81643d538c6ee093e5adc7149c'


def git(*args):
    return subprocess.check_output(['git', *args])


def main():
    raw = Path(TASK).read_bytes()
    assert hashlib.sha256(raw).hexdigest() == DIGEST
    assert raw == git('show', AUTH + ':' + TASK)
    assert git('diff', '--name-only', AUTH + '^', AUTH).decode().splitlines() == [TASK]
    subprocess.run(['git', 'merge-base', '--is-ancestor', AUTH, 'HEAD'], check=True)
    task = json.loads(raw)
    assert task['integrity_mode'] == 'IMMUTABLE_AUTHORIZATION_V1'
    assert task['recovery']['mode'] == 'APPEND_ONLY_V1'
    files = git('diff', '--name-only', task['starting_state']['base_sha'], 'HEAD').decode().splitlines()
    assert all(any(fnmatch.fnmatchcase(path, pattern) for pattern in task['mutation_scope']) for path in files)
    assert not git('diff', '--name-only', task['starting_state']['base_sha'], 'HEAD', '--',
        '.project-leader/tasks/PINK-IPTV-PHYSICAL-UI-RECOVERY-059.json',
        '.project-leader/recovery-events/PINK-IPTV-PHYSICAL-UI-RECOVERY-059/')
    print('IMMUTABLE_061_AUTHORITY_SCOPE_AND_059_HISTORY_PRESERVATION=PASS')


if __name__ == '__main__':
    main()
