"""Audit focused CEE geometry equivalence and matched steady/client measurements."""
import argparse
import ast
import csv
import json
from pathlib import Path
import re
import statistics

from summarize_tree import ROOT, evidence, read, sha
from summarize_budget import measurement


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--steady-report', required=True)
    parser.add_argument('--client-report')
    parser.add_argument('--reload-attempt')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    frozen = ROOT / 'local/frozen/prototype-09c/inputs.json'
    inputs = read(frozen)
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in inputs['sources'])
    assert all(sha(Path(item['path'])) == item['sha256'] for item in inputs['artifacts'].values())
    proofs = [evidence(frozen)]
    checks = []
    for name, backend, enabled in [('cee-geometry-native-03', 'NATIVE', True),
                                   ('cee-geometry-java-01', 'JAVA', True), ('cee-geometry-off-01', 'NATIVE', False)]:
        folder = ROOT / 'local/checks' / name
        result = read(folder / 'result.json')
        assert result['passed'] and result['required_tests_passed'] == 33
        assert result['artifacts'] == inputs['artifacts'] and result['backend'] == backend and result['cee_state_geometry'] == enabled
        log = (folder / 'process.log').read_text(errors='replace')
        geometry = re.findall(r'WWPG CEE geometry: (\d+) reviewed types, (\d+) states compared, enabled=(true|false)', log)
        assert geometry == [('10', '2716', str(enabled).lower())]
        checks.append(dict(name=name, result=result, reviewed_types=10, states_compared=2716))
        proofs.extend(evidence(folder / path) for path in ('invocation.json', 'result.json', 'process.log'))
    failed = []
    for name in ('cee-geometry-native-01', 'cee-geometry-native-02'):
        folder = ROOT / 'local/checks' / name
        result = read(folder / 'result.json')
        assert not result['passed']
        failed.append(dict(name=name, result=result))
        proofs.extend(evidence(folder / path) for path in ('invocation.json', 'result.json', 'process.log'))
    prior_inputs = [ROOT / f'local/frozen/prototype-09{letter}/inputs.json' for letter in ('a', 'b')]
    assert all(read(path)['artifacts']['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256'] for path in prior_inputs)
    proofs.extend(evidence(path) for path in prior_inputs)
    source = ROOT / 'local/cee-geometry-source-01'
    inspection = read(source / 'inspection.json')
    assert inspection['cee_sha256'] == inputs['artifacts']['cee']['sha256']
    assert inspection['bytecode_sha256'] == sha(source / 'javap.txt')
    proofs.extend(evidence(source / path) for path in ('inspection.json', 'javap.txt'))
    report = ROOT / args.steady_report
    steady = read(report)
    proofs.append(evidence(report))
    groups = {False: [], True: []}
    signatures = set()
    for run in steady['runs']:
        protocol = run['protocol']
        assert protocol['validation']['passed'] and run['summary']['passed'] and not protocol['detailed']
        assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        assert protocol['batch'] and protocol['property_batch'] and protocol['tree_batch'] and protocol['tree_prioritize_unresolved']
        assert protocol['tree_budget_ms'] == 20 and protocol['tree_max_parts'] == 32 and protocol['chunk_task_budget_ms'] == 2
        assert protocol['cpu_affinity_mask'] == 'fff000' and protocol['server_affinity_mask'] is None
        signatures.add((protocol['runtime']['sha256'], protocol['helper']['sha256']))
        groups[protocol['cee_state_geometry']].append(run)
        assert bool(protocol['cee_geometry']) == protocol['cee_state_geometry']
        for item in run['evidence']: assert sha(ROOT / item['path']) == item['sha256'], item['path']
    assert len(signatures) == 1 and all(groups.values())
    comparison = {}
    for enabled, runs in groups.items():
        counters = []
        for run in runs:
            path = ROOT / 'local/benchmarks' / run['name'] / 'benchmark/placement-ticks.csv'
            with path.open(newline='') as stream:
                rows = [row for row in csv.DictReader(stream) if row['phase'] == 'STABLE']
            assert len(rows) == run['summary']['samples']
            counters.append(dict(attempt=run['name'],
                mean_cpu_ms=statistics.mean(int(row['cpu_tick_ns']) / 1e6 for row in rows),
                mean_allocated_bytes=statistics.mean(int(row['allocated_bytes']) for row in rows),
                compilation_ms=int(rows[-1]['compile_ms']) - int(rows[0]['compile_ms'])))
        comparison['enabled' if enabled else 'disabled'] = dict(samples=len(runs),
            mean_tick_ms=[r['summary']['mspt']['mean'] for r in runs], p95_tick_ms=[r['summary']['mspt']['p95'] for r in runs],
            tps=[r['summary']['tps'] for r in runs], target_met=[r['summary']['practical_20tps_target_met'] for r in runs],
            thread_counters=counters)
    comparison['mean_tick_reduction_percent'] = 100 * (1 - statistics.mean(comparison['enabled']['mean_tick_ms']) / statistics.mean(comparison['disabled']['mean_tick_ms']))
    client = None
    if args.client_report:
        path = ROOT / args.client_report
        client = read(path)
        assert client['validation']['passed'] and client['protocol']['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        assert client['cee_geometry'] and not client['protocol']['inspection_only']
        for item in client['evidence']: assert sha(ROOT / item['path']) == item['sha256'], item['path']
        proofs.append(evidence(path))
    reload = None
    if args.reload_attempt:
        assert Path(args.reload_attempt).name == args.reload_attempt
        reload = measurement(args.reload_attempt)
        protocol = reload['protocol']
        assert protocol['validation']['passed'] and protocol['reload'] and not protocol['loading_trace']
        assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        assert (protocol['runtime']['sha256'], protocol['helper']['sha256']) in signatures
        assert protocol['cee_state_geometry'] and protocol['cee_geometry']
        assert protocol['chunk_task_budget_ms'] == 2 and protocol['tree_prioritize_unresolved']
        assert protocol['tree_budget_ms'] == 20 and protocol['tree_max_parts'] == 32
        folder = ROOT / 'local/benchmarks' / args.reload_attempt
        assert read(folder / 'runner-exit.json')['exit_code'] == 0
        for kind in ('runtime', 'helper'):
            artifact = read(folder / 'launch.json')[kind]
            assert sha(Path(artifact['path'])) == artifact['sha256']
        samples = [json.loads(line) for line in (folder / 'process-metrics.jsonl').read_text().splitlines()]
        assert samples and all(row['cpu_affinity_mask'] == 'fff000' for row in samples)
        reload['evidence'].extend(evidence(folder / name) for name in ('process-affinity.json', 'process-metrics.jsonl'))
    for name in ('summarize_cee_geometry.py', 'focused_checks.py', 'plain_wire_benchmark.py', 'restoration_benchmark.py',
                 'client_loading_benchmark.py', 'summarize_client_loading.py'):
        path = ROOT / 'tools/experimental' / name
        ast.parse(path.read_text())
        proofs.append(evidence(path))
    data = dict(schema=1, full_suite=False, runtime=inputs['artifacts']['runtime'], source_hashes_verified=len(inputs['sources']),
        focused_tests=99, checks=checks, failed_fixture_attempts=failed, comparison=comparison,
        steady_report=args.steady_report, client_report=args.client_report,
        client_result=None if client is None else client['result'], reload=reload, evidence=proofs,
        note='Finite same-jar focused samples; no statistical-significance or full-acceptance claim. Dynamic/custom geometry retains original callbacks.')
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2)+'\n')
    print(json.dumps(comparison))


if __name__ == '__main__':
    main()
