"""Retain actual graphical/TCP API Workshop acceptance against frozen artifacts."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import shutil

from run_api_workshop_multiplayer import verify_inputs

ROOT = Path(__file__).resolve().parents[1]
PHASES = {'wire', 'configure', 'configure_on', 'meter', 'panel_edit',
          'board_edit', 'repair', 'repair_wire'}
VIEWS = {f'A{index:02d}' for index in range(1, 12)}
BEHAVIORS = ['machine', 'storage', 'instrument', 'panel_attachment', 'board_component']


def descriptor(path):
    return dict(file=path.name, sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                bytes=path.stat().st_size)


def verify_helper_provenance(spec, run):
    """Verify attempt-frozen bytes, never infer compilation inputs from current source."""
    records = [spec.get('acceptance_helper'), *spec.get('acceptance_helper_sources', [])]
    assert isinstance(records[0], dict), 'Launch did not freeze its acceptance helper'
    sources = records[1:]
    assert sources and {'java', 'init', 'resource'} <= {item.get('kind') for item in sources}, \
        'Launch lacks actual Java/init/resource helper inputs'
    assert len({item['source_path'] for item in sources}) == len(sources), 'Duplicate helper source identity'
    frozen = (run / 'acceptance-helper').resolve()
    for item in records:
        assert isinstance(item, dict) and re.fullmatch(r'[0-9a-f]{64}', item.get('sha256', '')), \
            'Invalid helper descriptor'
        path = Path(item['path']).resolve()
        assert path.is_file() and path.is_relative_to(frozen), 'Helper input escaped its frozen attempt'
        assert path.stat().st_size == item.get('bytes'), 'Helper input size changed'
        assert hashlib.sha256(path.read_bytes()).hexdigest() == item['sha256'], 'Helper input hash changed'
    for item in sources:
        source = Path(item['source_path'])
        assert not source.is_absolute() and '..' not in source.parts and ':' not in item['source_path'], \
            'Invalid captured helper source identity'
    return records


def retain_helper_provenance(spec, spec_file, run, output, root=ROOT):
    records = verify_helper_provenance(spec, run)
    assert output.resolve().is_relative_to(root.resolve()), 'Retained helper provenance must remain in the workspace'
    targets = []
    for index, item in enumerate(records):
        relative = 'helper.jar' if index == 0 else 'inputs/' + item['source_path']
        target = output / 'helper-provenance' / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(item['path'], target)
        saved = dict(path=target.resolve().relative_to(root.resolve()).as_posix(),
                     sha256=item['sha256'], bytes=item['bytes'])
        if index:
            saved.update(source_path=item['source_path'], kind=item['kind'])
        targets.append(saved)
    # Keep the exact absolute-path launch record compressed. Its copied helper
    # inputs below use root-relative descriptors for transitive release bundling.
    launch = output / 'helper-provenance' / 'launch.json.gz'
    launch.write_bytes(gzip.compress(spec_file.read_bytes(), mtime=0))
    return dict(acceptance_helper=targets[0], acceptance_helper_sources=targets[1:],
        launch_descriptor=dict(path=launch.resolve().relative_to(root.resolve()).as_posix(),
            sha256=hashlib.sha256(launch.read_bytes()).hexdigest(), bytes=launch.stat().st_size))


def collect(run, mode, output, log=None):
    spec_file = run / ('api-client-launch.json' if mode == 'graphical' else 'launch.json')
    spec = json.loads(spec_file.read_text(encoding='utf-8'))
    verify_helper_provenance(spec, run)
    hashes = verify_inputs(spec)
    assert re.fullmatch(r'[0-9a-f]{40}', spec['source_sha']), 'Missing frozen source commit'
    assert spec['profile'] in {'BASE', 'PINOUT'} and spec['backend'] in {'NATIVE', 'JAVA'}
    if mode == 'multiplayer':
        result = json.loads((run / 'api-multiplayer-result.json').read_text(encoding='utf-8'))
        assert result['completed'] and result['passed'] and result['failure'] is None
        assert result['artifact_hashes'] == hashes and result['source_sha'] == spec['source_sha']
        assert result['world_sha256'] == spec['archive_sha256']
        assert set(result['processes']) == {'server', 'client-a', 'client-b'}
        assert all(item['exit_code'] == 0 for item in result['processes'].values())
        assert result['real_clients'] == ['WWPG_A', 'WWPG_B']
        logs = {role: run / f'{role}.log' for role in result['processes']}
    else:
        assert log is not None, 'Graphical evidence needs its actual Gradle/client log'
        logs = {'graphical': log}
    screenshots, checkpoints = {}, {}
    for role, path in logs.items():
        text = path.read_text(encoding='utf-8', errors='replace')
        assert 'API_WORKSHOP_CLIENT_FAILED' not in text and 'API_WORKSHOP_SERVER_FAILED' not in text
        if role == 'server':
            assert 'API_WORKSHOP_TWO_CLIENTS_PASSED' in text
            continue
        marker = 'API_WORKSHOP_GRAPHICAL_PASSED' if mode == 'graphical' else 'API_WORKSHOP_CLIENT_PASSED'
        assert marker in text, f'{role} did not complete actual client acceptance'
        if mode == 'graphical':
            assert 'BUILD SUCCESSFUL' in text, 'Client/Gradle process did not finish successfully'
        views = re.findall(r'API_WORKSHOP_VIEW_PASSED:\s*(A\d\d)\b', text)
        phases = re.findall(r'API_WORKSHOP_PHASE_PASSED:\s*(\w+)\b', text)
        assert set(views) == VIEWS, f'{role} did not visit all eleven API stations'
        assert PHASES <= set(phases), f'{role} lacks real interaction phases: {PHASES - set(phases)}'
        checkpoints[role] = dict(views=views, phases=phases)
        directory = run / 'screenshots' if mode == 'graphical' else run / role / 'screenshots'
        names = {f'api-{view}.png' for view in VIEWS}
        if mode == 'graphical':
            names |= {'api-control-off.png', 'api-control-on.png', 'api-panel-edit.png', 'api-board-edit.png'}
        else:
            player = 'A' if role == 'client-a' else 'B'
            names |= {f'api-{phase}-{player}.png' for phase in PHASES}
        for name in names:
            shot = directory / name
            assert shot.is_file(), f'Actual screenshot missing: {role}/{name}'
            screenshots[f'{role}-{name}'] = shot
    assert not output.exists(), 'Keep each attempt in a new evidence directory'
    output.mkdir(parents=True)
    report = dict(schema=1, completed=True, passed=True, unresolved_failures=[],
        source_sha=spec['source_sha'], artifact_hashes=hashes, mode=mode,
        profile=spec['profile'], backend=spec['backend'], world_sha256=spec['archive_sha256'],
        api_fixture_behaviors=BEHAVIORS, real_graphical_client=True,
        checkpoints=checkpoints, logs={}, screenshots={})
    report['provenance'] = retain_helper_provenance(spec, spec_file, run, output)
    if mode == 'multiplayer':
        report.update(real_clients=['WWPG_A', 'WWPG_B'], real_tcp_clients=True,
                      processes=result['processes'], elapsed_seconds=result['elapsed_seconds'])
    for role, path in logs.items():
        target = output / f'{role}.log.gz'
        target.write_bytes(gzip.compress(path.read_bytes(), mtime=0))
        report['logs'][role] = descriptor(target)
    for name, path in screenshots.items():
        target = output / name
        shutil.copyfile(path, target)
        report['screenshots'][name] = descriptor(target)
    (output / 'verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--mode', choices=['graphical', 'multiplayer'], required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--log', type=Path)
    args = parser.parse_args()
    report = collect(args.run, args.mode, args.output, args.log)
    print(json.dumps(dict(passed=True, mode=args.mode, profile=report['profile'],
                          backend=report['backend'], output=str(args.output))))
