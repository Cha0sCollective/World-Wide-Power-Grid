"""Capture unresolved-wire diagnosis and same-jar restoration-priority comparisons."""
import argparse
import json
from pathlib import Path
import re
import statistics

from summarize_budget import measurement
from summarize_tree import ROOT, evidence, read, sha


def diagnostic(name):
    folder = ROOT / 'local/benchmarks' / name
    value = measurement(name)
    assert value['protocol']['validation']['passed']
    manifest = ROOT / value['protocol']['helper_inputs']
    helper = read(manifest)
    assert helper['inspection_only'] and helper['helper']['sha256'] == value['protocol']['helper']['sha256']
    rows = [json.loads(line) for line in (folder / 'benchmark/unresolved-tree.jsonl').read_text().splitlines()]
    assert rows and rows[-1]['phase'] == 'FINISH' and rows[-1]['parts'] == rows[-1]['resolved'] == 5943
    assert rows[-1]['unresolved_owned'] == rows[-1]['unresolved_unowned'] == rows[-1]['pending_roots'] == 0
    value['scope'] = 'Read-only diagnostic snapshots add work; excluded from clean timing comparisons.'
    value['snapshots'] = [{k: v for k, v in row.items() if k != 'examples'} for row in rows]
    value['evidence'] += [evidence(folder / 'benchmark/unresolved-tree.jsonl'), evidence(manifest)]
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', default='local/frozen/prototype-07a/inputs.json')
    parser.add_argument('--helper-inputs', default='local/frozen/affinity-helper-02/inputs.json')
    parser.add_argument('--diagnostic', default='goal-unresolved-inspect-01')
    parser.add_argument('--attempts', nargs='+', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    manifest = ROOT / args.inputs
    frozen = read(manifest)
    assert all(sha(ROOT / s['path']) == s['sha256'] for s in frozen['sources'])
    assert all(sha(Path(a['path'])) == a['sha256'] for a in frozen['artifacts'].values())
    helper_manifest = ROOT / args.helper_inputs
    helper = read(helper_manifest)
    assert sha(Path(helper['helper']['path'])) == helper['helper']['sha256']
    checks = []
    for name in ('priority-native-budget-01', 'priority-java-budget-01',
                 'priority-native-lifecycle-01', 'priority-java-lifecycle-01', 'priority-off-tree-01'):
        folder = ROOT / 'local/checks' / name
        check = read(folder / 'result.json')
        assert check['passed'] and check['artifacts'] == frozen['artifacts']
        assert check['tree_prioritize_unresolved'] == ('off' not in name)
        observed = re.findall(r'prioritizeUnresolved=(true|false), prioritySelections=(\d+)',
                              (folder / 'process.log').read_text(errors='replace'))
        assert len(observed) == 1 and (observed[0][0] == 'true') == check['tree_prioritize_unresolved']
        if 'budget' in name:
            assert int(observed[0][1]) > 0
        checks.append(dict(name=name, result=check, evidence=[evidence(folder / p)
            for p in ('result.json', 'invocation.json', 'process.log')]))
    assert sorted(c['result']['required_tests_passed'] for c in checks) == [11, 11, 36, 38, 38]
    units = read(ROOT / 'local/checks/priority-units-01/result.json')
    assert units['passed'] and units['unit_tests'] == 15
    for suite in units['suites']:
        assert sha(Path(suite['path'])) == suite['sha256']
    runs = [measurement(name) for name in args.attempts]
    for run in runs:
        p = run['protocol']
        assert p['validation']['passed'] and not p['loading_trace']
        assert p['runtime']['sha256'] == frozen['artifacts']['runtime']['sha256']
        assert p['helper']['sha256'] == helper['helper']['sha256']
        assert p['tree_batch'] and p['tree_budget_ms'] == 20 and p['tree_max_parts'] == 32
        assert p['cpu_affinity_mask'] == 'fff000' and p['server_affinity_mask'] is None
        folder = ROOT / 'local/benchmarks' / run['name']
        preflight = read(folder / 'cpu-preflight.json')
        assert preflight['quiet']
        samples = [json.loads(line) for line in (folder / 'process-metrics.jsonl').read_text().splitlines()]
        assert samples and all(row['cpu_affinity_mask'] == 'fff000' for row in samples)
        run['evidence'] += [evidence(folder / p) for p in
            ('cpu-preflight.json', 'process-affinity.json', 'process-metrics.jsonl')]
    clean = [r for r in runs if not r['protocol']['reload']]
    assert len(clean) == 4 and [r['protocol']['tree_prioritize_unresolved'] for r in clean] == [True, False, False, True]
    groups = {}
    for name, enabled in [('control', False), ('priority', True)]:
        selected = [r for r in clean if r['protocol']['tree_prioritize_unresolved'] == enabled]
        groups[name] = dict(
            restoration_seconds=[r['protocol']['loading'][0]['total_restoration_seconds'] for r in selected],
            graph_recovery_seconds=[r['protocol']['loading'][0]['graph_recovery_seconds'] for r in selected],
            endpoint_visits=[r['protocol']['tree_execution']['visited'] for r in selected],
            worst_tick_seconds=[r['tick_metrics']['SETTLE']['longest_tick_seconds'] for r in selected],
            worst_gap_seconds=[r['tick_metrics']['SETTLE']['longest_end_to_end_gap_seconds'] for r in selected])
        groups[name]['mean_restoration_seconds'] = statistics.mean(groups[name]['restoration_seconds'])
    comparison = dict(**groups,
        restoration_reduction_percent=100 * (1 - groups['priority']['mean_restoration_seconds'] / groups['control']['mean_restoration_seconds']),
        endpoint_visit_reduction_percent=100 * (1 - statistics.mean(groups['priority']['endpoint_visits']) / statistics.mean(groups['control']['endpoint_visits'])),
        limits='Two fresh-process samples per mode in on/off/off/on order; caches not flushed; no significance, rendering or sustained-TPS claim.')
    result = dict(schema=1, inputs=evidence(manifest), artifacts=frozen['artifacts'],
        matched_source_entries=len(frozen['sources']), native_unchanged=frozen['native_unchanged'],
        helper_inputs=evidence(helper_manifest), focused_checks=checks,
        units=units, unit_result=evidence(ROOT / 'local/checks/priority-units-01/result.json'),
        focused_unit_tests=15, focused_gametests=134, full_suite=False,
        diagnostic=diagnostic(args.diagnostic), measurements=runs, comparison=comparison,
        tool_sources=[evidence(ROOT / 'tools/experimental' / name) for name in
            ('UnresolvedTreeDiagnostic.java', 'freeze_unresolved_helper.py', 'summarize_priority.py',
             'plain_wire_benchmark.py', 'restoration_benchmark.py', 'focused_checks.py', 'plain-wire.gradle')])
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2) + '\n')
    print(json.dumps(comparison))


if __name__ == '__main__':
    main()
