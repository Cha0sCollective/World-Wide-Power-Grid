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
    parser.add_argument('--selection', choices=['wire', 'lifecycle', 'tree', 'budget', 'geometry', 'terminals'], default='wire')
    parser.add_argument('--tree-batch', action='store_true')
    parser.add_argument('--tree-budget-ms', type=int, default=0)
    parser.add_argument('--tree-max-parts', type=int, default=32)
    parser.add_argument('--tree-retain-coverage', action='store_true')
    parser.add_argument('--tree-prioritize-unresolved', action='store_true')
    parser.add_argument('--chunk-task-budget-ms', type=int, choices=range(21), default=0)
    parser.add_argument('--cee-state-geometry', action='store_true')
    parser.add_argument('--terminal-bookkeeping', action='store_true')
    parser.add_argument('--addon-inputs', help='Frozen manifest supplying independent API example jars')
    parser.add_argument('--name', required=True)
    parser.add_argument('--property-batch', action='store_true')
    args = parser.parse_args()
    if args.selection == 'terminals' and not args.addon_inputs:
        parser.error('Terminal ownership fixtures require --addon-inputs')
    if args.tree_prioritize_unresolved and not args.tree_batch:
        parser.error('--tree-prioritize-unresolved requires --tree-batch')
    manifest = Path(args.inputs).resolve()
    inputs = json.loads(manifest.read_text())
    artifacts = {key: Path(value['path']) for key, value in inputs['artifacts'].items()}
    for key, path in artifacts.items():
        assert sha(path) == inputs['artifacts'][key]['sha256'], key
    addons = {}
    if args.addon_inputs:
        addon_manifest = (ROOT / args.addon_inputs).resolve()
        addon_inputs = json.loads(addon_manifest.read_text())
        addons = {key: addon_inputs['artifacts'][key] for key in ('example', 'upstream_example')}
        assert all(sha(Path(item['path'])) == item['sha256'] for item in addons.values())
    directory = (ROOT / 'local/checks' / args.name).resolve()
    assert directory.is_relative_to((ROOT / 'local/checks').resolve())
    directory.mkdir(parents=True, exist_ok=False)
    namespaces = 'wwpg_wire_power' if args.selection == 'wire' else 'wwpg_unload,wwpg_solver_safety'
    if args.selection in ('tree', 'budget'):
        namespaces = 'wwpg_tree_restore,wwpg_loading,wwpg_pause_epoch,wwpg_contactors,wwpg_wire_recovery'
    if args.selection == 'budget':
        assert args.tree_batch and args.tree_budget_ms > 0 and args.tree_max_parts == 1
        namespaces += ',wwpg_tree_budget'
    if args.selection == 'geometry':
        namespaces = 'wwpg_cee_state_geometry,wwpg_loading,wwpg_unload,wwpg_solver_safety'
    if args.selection == 'terminals':
        namespaces = 'wwpg_loading,wwpg_api_ownership,wwpg_unload,wwpg_solver_safety'
    command = [sys.executable, str(ROOT / 'tools/run_checked.py'), '--timeout', '600', '--', 'gradlew.bat',
               'runPackagedGameTestServer', '-x', 'build', '-x', 'jar', '-x', 'test', '--offline', '--no-daemon',
               '-I', 'tools/experimental/plain-wire.gradle', '-PexperimentalCee=' + str(artifacts['cee']),
               '-PtestArtifact=' + str(artifacts['runtime']), '-PtestFixtureArtifact=' + str(artifacts['fixtures']),
               '-PtestDirectory=' + str(directory / 'server'), '-PtestBackend=' + args.backend,
               '-PtestProfile=BASE', '-PtestNamespaces=' + namespaces,
               '-PtestPropertyWireBatch=' + str(args.property_batch).lower(),
               '-PtestWireTreeBatch=' + str(args.tree_batch).lower(),
               '-PtestWireTreeBudgetMillis=' + str(args.tree_budget_ms), '-PtestWireTreeMaxParts=' + str(args.tree_max_parts),
               '-PtestWireTreeRetainCoverage=' + str(args.tree_retain_coverage).lower(),
               '-PtestWireTreePrioritizeUnresolved=' + str(args.tree_prioritize_unresolved).lower(),
               '-PtestChunkTaskBudgetMillis=' + str(args.chunk_task_budget_ms),
               '-PtestCeeStateGeometry=' + str(args.cee_state_geometry).lower(),
               '-PtestTerminalBookkeeping=' + str(args.terminal_bookkeeping).lower()]
    if addons:
        command += ['-PtestApiAddon=true', '-PtestExampleArtifact=' + addons['example']['path'],
                    '-PtestUpstreamExampleArtifact=' + addons['upstream_example']['path']]
    env = dict(os.environ)
    env['JAVA_HOME'] = inputs['java_home']
    env['PATH'] = str(Path(inputs['java_home']) / 'bin') + os.pathsep + env['PATH']
    proof = dict(scope='Focused only; no full or graphical acceptance', namespaces=namespaces,
                 backend=args.backend, property_batch=args.property_batch, tree_batch=args.tree_batch,
                 tree_budget_ms=args.tree_budget_ms, tree_max_parts=args.tree_max_parts,
                 tree_retain_coverage=args.tree_retain_coverage, command=command, artifacts=inputs['artifacts'])
    proof['tree_prioritize_unresolved'] = args.tree_prioritize_unresolved
    proof['chunk_task_budget_ms'] = args.chunk_task_budget_ms
    proof['cee_state_geometry'] = args.cee_state_geometry
    proof['terminal_bookkeeping'] = args.terminal_bookkeeping
    proof['addon_artifacts'] = addons
    if addons:
        proof['addon_inputs'] = dict(path=str(addon_manifest), sha256=sha(addon_manifest))
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
    if addons:
        proof['addons_unchanged'] = all(sha(Path(item['path'])) == item['sha256'] for item in addons.values())
        packaged = list((directory / 'server').glob('packaged-*.json'))
        proof['addon_classpath_verified'] = len(packaged) == 1 and all(
            json.loads(packaged[0].read_text()).get('addon_artifacts', {}).get(key, {}).get('sha256') == item['sha256']
            for key, item in addons.items())
        proof['passed'] &= proof['addons_unchanged'] and proof['addon_classpath_verified']
    if proof['passed']:
        from plain_wire_benchmark import validate_terminal_bookkeeping
        try:
            proof['terminal_execution'] = validate_terminal_bookkeeping(log, args.terminal_bookkeeping)
        except AssertionError:
            proof['passed'] = False
            proof['terminal_validation_error'] = 'Missing or inconsistent terminal bookkeeping execution counters'
    (directory / 'result.json').write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps({key: proof[key] for key in ('passed', 'required_tests_passed', 'artifacts_unchanged', 'exit_code')}))
    if not proof['passed']:
        print(log[-14000:])
        raise SystemExit(1)


if __name__ == '__main__':
    main()
