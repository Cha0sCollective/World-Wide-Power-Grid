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
    parser.add_argument('--selection', choices=['wire', 'lifecycle', 'tree', 'budget'], default='wire')
    parser.add_argument('--tree-batch', action='store_true')
    parser.add_argument('--tree-budget-ms', type=int, default=0)
    parser.add_argument('--tree-max-parts', type=int, default=32)
    parser.add_argument('--name', required=True)
    parser.add_argument('--property-batch', action='store_true')
    args = parser.parse_args()
    manifest = Path(args.inputs).resolve()
    inputs = json.loads(manifest.read_text())
    artifacts = {key: Path(value['path']) for key, value in inputs['artifacts'].items()}
    for key, path in artifacts.items():
        assert sha(path) == inputs['artifacts'][key]['sha256'], key
    directory = (ROOT / 'local/checks' / args.name).resolve()
    assert directory.is_relative_to((ROOT / 'local/checks').resolve())
    directory.mkdir(parents=True, exist_ok=False)
    namespaces = 'wwpg_wire_power' if args.selection == 'wire' else 'wwpg_unload,wwpg_solver_safety'
    if args.selection in ('tree', 'budget'):
        namespaces = 'wwpg_tree_restore,wwpg_loading,wwpg_pause_epoch,wwpg_contactors,wwpg_wire_recovery'
    if args.selection == 'budget':
        assert args.tree_batch and args.tree_budget_ms > 0 and args.tree_max_parts == 1
        namespaces += ',wwpg_tree_budget'
    command = [sys.executable, str(ROOT / 'tools/run_checked.py'), '--timeout', '600', '--', 'gradlew.bat',
               'runPackagedGameTestServer', '-x', 'build', '-x', 'jar', '-x', 'test', '--offline', '--no-daemon',
               '-I', 'tools/experimental/plain-wire.gradle', '-PexperimentalCee=' + str(artifacts['cee']),
               '-PtestArtifact=' + str(artifacts['runtime']), '-PtestFixtureArtifact=' + str(artifacts['fixtures']),
               '-PtestDirectory=' + str(directory / 'server'), '-PtestBackend=' + args.backend,
               '-PtestProfile=BASE', '-PtestNamespaces=' + namespaces,
               '-PtestPropertyWireBatch=' + str(args.property_batch).lower(),
               '-PtestWireTreeBatch=' + str(args.tree_batch).lower(),
               '-PtestWireTreeBudgetMillis=' + str(args.tree_budget_ms), '-PtestWireTreeMaxParts=' + str(args.tree_max_parts)]
    env = dict(os.environ)
    env['JAVA_HOME'] = inputs['java_home']
    env['PATH'] = str(Path(inputs['java_home']) / 'bin') + os.pathsep + env['PATH']
    proof = dict(scope='Focused only; no full or graphical acceptance', namespaces=namespaces,
                 backend=args.backend, property_batch=args.property_batch, tree_batch=args.tree_batch,
                 tree_budget_ms=args.tree_budget_ms, tree_max_parts=args.tree_max_parts, command=command, artifacts=inputs['artifacts'])
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
    (directory / 'result.json').write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps({key: proof[key] for key in ('passed', 'required_tests_passed', 'artifacts_unchanged', 'exit_code')}))
    if not proof['passed']:
        print(log[-14000:])
        raise SystemExit(1)


if __name__ == '__main__':
    main()
