"""Run only the selected experimental fixtures against immutable packaged jars."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--backend', choices=['NATIVE', 'JAVA'], required=True)
    parser.add_argument('--selection', choices=['wire', 'lifecycle', 'results', 'packed'], default='wire')
    parser.add_argument('--name', required=True)
    parser.add_argument('--property-batch', action='store_true')
    parser.add_argument('--packed', action='store_true')
    parser.add_argument('--workers', type=int, choices=[0, 2, 4], default=0)
    args = parser.parse_args()
    manifest = Path(args.inputs).resolve()
    inputs = json.loads(manifest.read_text())
    artifacts = {key: Path(value['path']) for key, value in inputs['artifacts'].items()}
    for key, path in artifacts.items():
        assert sha(path) == inputs['artifacts'][key]['sha256'], key
    directory = (ROOT / 'local/checks' / args.name).resolve()
    assert directory.is_relative_to((ROOT / 'local/checks').resolve())
    directory.mkdir(parents=True, exist_ok=False)
    namespaces = {'wire': 'wwpg_wire_power', 'lifecycle': 'wwpg_unload,wwpg_solver_safety',
                  'results': 'wwpg_packed_results,wwpg_meter_sampling',
                  'packed': 'wwpg_wire_power,wwpg_unload,wwpg_solver_safety,wwpg_packed_results,wwpg_meter_sampling'}[args.selection]
    command = [sys.executable, str(ROOT / 'tools/run_checked.py'), '--timeout', '600', '--', 'gradlew.bat',
               'runPackagedGameTestServer', '-x', 'build', '-x', 'jar', '-x', 'test', '--offline', '--no-daemon',
               '-I', 'tools/experimental/plain-wire.gradle', '-PexperimentalCee=' + str(artifacts['cee']),
               '-PtestArtifact=' + str(artifacts['runtime']), '-PtestFixtureArtifact=' + str(artifacts['fixtures']),
               '-PtestDirectory=' + str(directory / 'server'), '-PtestBackend=' + args.backend,
               '-PtestProfile=BASE', '-PtestNamespaces=' + namespaces,
               '-PtestPropertyWireBatch=' + str(args.property_batch).lower(),
               '-PtestPackedResults=' + str(args.packed).lower(), '-PtestPackedWorkers=' + str(args.workers)]
    if args.selection in ('results', 'packed'):
        command += ['-PtestApiAddon=true', '-PtestExampleArtifact=' + str(artifacts['example']),
                    '-PtestUpstreamExampleArtifact=' + str(artifacts['upstream_example'])]
    env = dict(os.environ)
    env['JAVA_HOME'] = inputs['java_home']
    env['PATH'] = str(Path(inputs['java_home']) / 'bin') + os.pathsep + env['PATH']
    proof = dict(scope='Focused only; no full or graphical acceptance', namespaces=namespaces,
                 backend=args.backend, property_batch=args.property_batch, packed=args.packed, workers=args.workers,
                 command=command, artifacts=inputs['artifacts'])
    (directory / 'invocation.json').write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps(dict(starting=str(directory), namespaces=namespaces, backend=args.backend)), flush=True)
    with (directory / 'process.log').open('w', encoding='utf-8') as stream:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
    log = (directory / 'process.log').read_text(encoding='utf-8', errors='replace')
    passed = re.findall(r'All (\d+) required tests passed', log)
    proof['exit_code'] = result.returncode
    proof['required_tests_passed'] = int(passed[-1]) if passed else 0
    proof['artifacts_unchanged'] = all(sha(path) == inputs['artifacts'][key]['sha256'] for key, path in artifacts.items())
    proof['passed'] = result.returncode == 0 and 'GAME TESTS COMPLETE' in log and bool(passed) and proof['artifacts_unchanged']
    if args.packed:
        matches = re.findall(r'WWPG experimental packed results: enabled=true, verify=true, workers=(\d+), power=(\d+), currents=(\d+), voltages=(\d+), dispatches=(\d+), ranges=(\d+), verified=(\d+)', log)
        proof['packed_execution'] = matches
        proof['packed_minimum_items'] = 1
        good = len(matches) == 1
        if good:
            workers, power, currents, voltages, dispatches, ranges, verified = map(int, matches[0])
            good = workers == args.workers and power > 0 and verified > 0 and ranges == dispatches * workers
            if workers: good &= dispatches > 0
            if args.selection in ('results', 'packed'): good &= currents > 0 and voltages > 0
        proof['passed'] &= good and 'Packed result differs' not in log and 'Packed worker failed' not in log
    (directory / 'result.json').write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps({key: proof[key] for key in ('passed', 'required_tests_passed', 'artifacts_unchanged', 'exit_code')}))
    if not proof['passed']:
        print(log[-14000:])
        raise SystemExit(1)


if __name__ == '__main__':
    main()
