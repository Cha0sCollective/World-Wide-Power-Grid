"""Retain completed two-client checks against the exact exported yard ZIP."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def collect(run, output):
    report = json.loads((run / 'multiplayer-result.json').read_text(encoding='utf-8'))
    spec = json.loads((run / 'launch.json').read_text(encoding='utf-8'))
    assert report['passed'] and report['failure'] is None, 'Incomplete multiplayer acceptance'
    assert report['real_clients'] == ['WWPG_A', 'WWPG_B'], 'Expected two real TCP clients'
    assert set(report['processes']) == {'server', 'client-a', 'client-b'}, 'Missing real processes'
    assert all(p['exit_code'] == 0 for p in report['processes'].values()), 'A process failed'
    assert sha(Path(spec['jar']['path'])) == report['jar']['sha256'], 'Frozen jar changed'
    assert sha(Path(spec['archive'])) == report['archive_sha256'], 'Exact world ZIP changed'
    assert report['profile'] == spec['profile'] and report['backend'] == spec['backend'], 'Profile mismatch'
    logs = {}
    screenshots = {}
    for role in ('server', 'client-a', 'client-b'):
        path = run / f'{role}.log'
        text = path.read_text(encoding='utf-8', errors='replace')
        assert 'WWPG real client failed' not in text, 'A client reported failure'
        if role == 'server':
            assert 'STATIONARY_TWO_REAL_CLIENTS_PASSED' in text, 'Stationary server acceptance missing'
            assert f'profile={report["profile"]}; backend={report["backend"]}' in text, 'Actual backend/profile missing'
        else:
            assert 'WWPG_REAL_CLIENT_PASSED' in text and 'STATIONARY_REAL_CLIENT_DESIGN:' in text, 'Native client workflows missing'
            if report['profile'] == 'PINOUT':
                assert 'STATIONARY_REAL_CLIENT_TERMINAL:' in text, 'Actual Lua terminal readings missing'
                assert 'STATIONARY_REAL_CLIENT_LUA:' in text, 'Actual Lua keyboard operations missing'
            player = 'A' if role == 'client-a' else 'B'
            names = [f'design-editor-{player}.png', f'design-saved-{player}.png']
            if report['profile'] == 'PINOUT':
                names += [f'stationary-pinout-{phase}-{player}.png' for phase in ('stop', 'pattern', 'volts', 'start')]
            for name in names:
                shot = run / role / 'screenshots' / name
                assert shot.is_file(), f'Native GUI screenshot missing: {name}'
                screenshots[f'{role}-{shot.name}'] = shot
        logs[role] = path
    assert not output.exists(), 'Keep each run in a separate evidence directory'
    output.mkdir(parents=True)
    report['logs'] = {}
    report['screenshots'] = {}
    for role, path in logs.items():
        data = gzip.compress(path.read_bytes(), mtime=0)
        (output / f'{role}.log.gz').write_bytes(data)
        report['logs'][role] = {'file': f'{role}.log.gz', 'sha256': hashlib.sha256(data).hexdigest()}
    for name, path in screenshots.items():
        shutil.copyfile(path, output / name)
        report['screenshots'][name] = {'sha256': sha(path), 'bytes': path.stat().st_size}
    report['mode'] = 'One dedicated server and two real TCP Minecraft clients; native menu and keyboard packets'
    (output / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = collect(args.run, args.output)
    print(json.dumps({'passed': True, 'profile': result['profile'], 'backend': result['backend'], 'output': str(args.output)}))
