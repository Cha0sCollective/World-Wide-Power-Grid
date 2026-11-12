"""Audit focused key-compatibility checks and matched terminal lookup measurements."""
import argparse
import ast
import csv
import json
from pathlib import Path
import re
import statistics

from summarize_budget import measurement
from summarize_current_profile import signature
from summarize_tree import ROOT, evidence, read, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--steady-report', required=True)
    parser.add_argument('--client-report')
    parser.add_argument('--reload-attempt')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    frozen = ROOT / 'local/frozen/prototype-11b/inputs.json'
    inputs = read(frozen)
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in inputs['sources'])
    assert all(sha(Path(item['path'])) == item['sha256'] for item in inputs['artifacts'].values())
    proofs = [evidence(frozen), evidence(ROOT / 'local/terminal-keys-build-02.log')]
    inspection = ROOT / 'local/terminal-keys-source-01'
    inspected = read(inspection / 'inspection.json')
    assert sha(Path(inspected['jar'])) == inspected['jar_sha256']
    assert sha(inspection / 'BlockWireEndpoint.bytecode.txt') == inspected['bytecode_sha256']
    proofs.extend(evidence(inspection / name) for name in ('inspection.json', 'BlockWireEndpoint.bytecode.txt'))
    checks = []
    for name, backend, enabled in [('terminal-keys-native-02', 'NATIVE', True),
                                   ('terminal-keys-java-02', 'JAVA', True), ('terminal-keys-off-02', 'NATIVE', False)]:
        folder = ROOT / 'local/checks' / name
        result = read(folder / 'result.json')
        assert result['passed'] and result['required_tests_passed'] == 34
        assert result['artifacts'] == inputs['artifacts'] and result['backend'] == backend
        assert result['cee_state_geometry'] and result['terminal_key_lookup'] == enabled
        assert not result['terminal_bookkeeping'] and bool(result['terminal_keys']) == enabled
        assert result['addons_unchanged'] and result['addon_classpath_verified']
        for item in result['addon_artifacts'].values():
            assert sha(Path(item['path'])) == item['sha256']
        log = (folder / 'process.log').read_text(errors='replace')
        assert re.findall(r'WWPG terminal key equivalence: nativePairs=(\d+), referenceCases=(\d+), enabled=(true|false)', log) == [('512', '3', str(enabled).lower())]
        assert re.findall(r'WWPG terminal key equivalence: endpointCases=(\d+), enabled=(true|false)', log) == [('40', str(enabled).lower())]
        checks.append(dict(name=name, result=result, native_key_pairs=512, reference_cases=3, endpoint_cases=40))
        proofs.extend(evidence(folder / path) for path in ('invocation.json', 'result.json', 'process.log'))
        proofs.extend(evidence(path) for path in (folder / 'server').glob('packaged-*.json'))
    superseded = ROOT / 'local/frozen/prototype-11a/inputs.json'
    proofs.append(evidence(superseded))
    proofs.append(evidence(ROOT / 'local/terminal-keys-build-01.log'))
    for name in ('NativeApiReadings.java', 'TerminalKeyLookupGameTests.java'):
        archived = ROOT / 'local/terminal-keys-source-01/pre-correction' / name
        expected = next(item['sha256'] for item in read(superseded)['sources'] if item['path'].endswith('/' + name))
        assert sha(archived) == expected
        proofs.append(evidence(archived))
    for name in ('terminal-keys-native-01', 'terminal-keys-java-01', 'terminal-keys-off-01'):
        folder = ROOT / 'local/checks' / name
        proofs.extend(evidence(folder / path) for path in ('invocation.json', 'result.json', 'process.log'))
    interrupted = ROOT / 'local/benchmarks/goal-terminal-keys-on-01'
    assert not read(interrupted / 'runner-failure.json')['passed']
    proofs.extend(evidence(interrupted / name) for name in ('experiment.json', 'runner-failure.json', 'runner-exit.json', 'process.log', 'CANCEL'))
    path = ROOT / args.steady_report
    steady = read(path)
    proofs.append(evidence(path))
    groups, signatures = {False: [], True: []}, set()
    for run in steady['runs']:
        protocol = run['protocol']
        assert protocol['validation']['passed'] and run['summary']['passed'] and not protocol['detailed']
        assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        assert protocol['batch'] and protocol['property_batch'] and not protocol['verify_batch']
        assert protocol['tree_batch'] and protocol['tree_prioritize_unresolved'] and protocol['cee_state_geometry']
        assert protocol['tree_budget_ms'] == 20 and protocol['tree_max_parts'] == 32 and protocol['chunk_task_budget_ms'] == 2
        assert protocol['cpu_affinity_mask'] == 'fff000' and protocol['server_affinity_mask'] is None
        assert not protocol['terminal_bookkeeping']
        mode = protocol['terminal_key_lookup']
        assert bool(protocol['terminal_keys']) == mode
        match = signature(protocol)
        match.pop('terminal_key_lookup')
        signatures.add(json.dumps(match, sort_keys=True))
        for item in run['evidence']:
            assert sha(ROOT / item['path']) == item['sha256'], item['path']
        with (ROOT / 'local/benchmarks' / run['name'] / 'benchmark/placement-ticks.csv').open(newline='') as stream:
            rows = [row for row in csv.DictReader(stream) if row['phase'] == 'STABLE']
        assert len(rows) == run['summary']['samples']
        groups[mode].append(dict(attempt=run['name'], tps=run['summary']['tps'], mspt=run['summary']['mspt'],
            target_met=run['summary']['practical_20tps_target_met'],
            ticks_over_50ms_percent=100 * sum(int(row['wall_tick_ns']) > 50_000_000 for row in rows) / len(rows),
            mean_cpu_ms=statistics.mean(int(row['cpu_tick_ns']) / 1e6 for row in rows),
            mean_allocated_bytes=statistics.mean(int(row['allocated_bytes']) for row in rows),
            compilation_ms=int(rows[-1]['compile_ms']) - int(rows[0]['compile_ms']),
            gc_ms=run['summary']['gc_ms'], terminal_keys=protocol['terminal_keys']))
    assert len(signatures) == 1 and all(groups.values())
    comparison = dict(enabled=groups[True], disabled=groups[False],
        mean_tick_reduction_percent=100 * (1 - statistics.mean(r['mspt']['mean'] for r in groups[True]) /
            statistics.mean(r['mspt']['mean'] for r in groups[False])))
    client = None
    if args.client_report:
        path = ROOT / args.client_report
        client = read(path)
        assert client['validation']['passed'] and client['protocol']['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        assert client['terminal_keys'] and not client['protocol']['inspection_only']
        for item in client['evidence']:
            assert sha(ROOT / item['path']) == item['sha256'], item['path']
        proofs.append(evidence(path))
    reload = None
    if args.reload_attempt:
        assert Path(args.reload_attempt).name == args.reload_attempt
        reload = measurement(args.reload_attempt)
        protocol = reload['protocol']
        assert protocol['validation']['passed'] and protocol['reload'] and not protocol['loading_trace']
        assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        assert protocol['terminal_key_lookup'] and protocol['terminal_keys'] and not protocol['terminal_bookkeeping']
        assert protocol['cee_state_geometry'] and protocol['cee_geometry'] and protocol['chunk_task_budget_ms'] == 2
        assert protocol['tree_batch'] and protocol['tree_prioritize_unresolved']
        assert protocol['tree_budget_ms'] == 20 and protocol['tree_max_parts'] == 32
        assert protocol['cpu_affinity_mask'] == 'fff000'
        folder = ROOT / 'local/benchmarks' / args.reload_attempt
        assert read(folder / 'runner-exit.json')['exit_code'] == 0
        for kind in ('runtime', 'helper'):
            artifact = read(folder / 'launch.json')[kind]
            assert sha(Path(artifact['path'])) == artifact['sha256']
        assert {run['protocol']['helper']['sha256'] for run in steady['runs']} == {protocol['helper']['sha256']}
        samples = [json.loads(line) for line in (folder / 'process-metrics.jsonl').read_text().splitlines()]
        assert samples and all(row['cpu_affinity_mask'] == 'fff000' for row in samples)
        placement = read(folder / 'process-affinity.json')
        assert placement['requested_mask'] == placement['readback_mask'] == 'fff000'
        reload['evidence'].extend(evidence(folder / name) for name in ('process-affinity.json', 'process-metrics.jsonl'))
    for name in ('summarize_terminal_keys.py', 'summarize_current_profile.py', 'focused_checks.py',
                 'plain_wire_benchmark.py', 'restoration_benchmark.py', 'client_loading_benchmark.py', 'summarize_client_loading.py'):
        path = ROOT / 'tools/experimental' / name
        ast.parse(path.read_text())
        proofs.append(evidence(path))
    data = dict(schema=1, full_suite=False, runtime=inputs['artifacts']['runtime'], source_hashes_verified=len(inputs['sources']),
        focused_tests=102, checks=checks, comparison=comparison, steady_report=args.steady_report,
        superseded_inputs=evidence(superseded), interrupted_attempt='goal-terminal-keys-on-01',
        client_report=args.client_report, reload=reload, client_result=None if client is None else client['result'], evidence=proofs,
        note='Finite same-jar samples; no statistical-significance or full-acceptance claim. Keys retain identical hashes and equality; ownership validity is freshly recomputed.')
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2) + '\n')
    print(json.dumps(comparison))


if __name__ == '__main__':
    main()
