"""Record the bounded-restoration experiment without replacing historical evidence."""
import argparse
import csv
import json
import math
from pathlib import Path
import statistics
import xml.etree.ElementTree as ET

from summarize_tree import ROOT, attempt, evidence, read, sha


def measurement(name):
    value = attempt(name)
    folder = ROOT / 'local/benchmarks' / name
    if not value['protocol'].get('validation', {}).get('passed'):
        value['failure_evidence'] = [evidence(p) for p in (folder / 'process.log', folder / 'runner-exit.json',
                                     folder / 'benchmark/result.json', folder / 'benchmark/loading-timeout.json') if p.exists()]
        return value
    with (folder / 'benchmark/placement-ticks.csv').open() as stream:
        ticks = list(csv.DictReader(stream))
    for phase, metrics in value['tick_metrics'].items():
        durations = sorted(int(t['wall_tick_ns']) / 1e9 for t in ticks if t['phase'] == phase)
        gaps = [(int(b['wall_unix_ms']) - int(a['wall_unix_ms'])) / 1000
                for a, b in zip(ticks, ticks[1:]) if a['phase'] == b['phase'] == phase]
        metrics.update(p95_tick_seconds=durations[math.ceil(.95 * len(durations)) - 1],
                       p99_tick_seconds=durations[math.ceil(.99 * len(durations)) - 1],
                       longest_end_to_end_gap_seconds=max(gaps, default=0))
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', default='local/frozen/prototype-05b/inputs.json')
    parser.add_argument('--helper-inputs', default='local/frozen/budget-helper-01/inputs.json')
    parser.add_argument('--attempts', nargs='+', required=True)
    parser.add_argument('--output', default='docs/experimental/wire-tree-budget-results.json')
    args = parser.parse_args()
    manifest = ROOT / args.inputs
    frozen = read(manifest)
    helper_manifest = ROOT / args.helper_inputs
    helper = read(helper_manifest)
    assert helper['runtime'] == frozen['artifacts']['runtime'] and helper['gate_checks'] == 7
    assert sha(Path(helper['helper']['path'])) == helper['helper']['sha256']
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in helper['sources'])
    assert all(sha(ROOT / source['path']) == source['sha256'] for source in frozen['sources'])
    assert all(sha(Path(artifact['path'])) == artifact['sha256'] for artifact in frozen['artifacts'].values())
    checks = []
    for path in sorted((ROOT / 'local/checks').glob('budget-*/result.json')):
        checks.append(dict(name=path.parent.name, result=read(path),
                           evidence=[evidence(path), evidence(path.parent / 'invocation.json'), evidence(path.parent / 'process.log')]))
    passing = [x['result'] for x in checks if x['result']['passed'] and x['result']['artifacts'] == frozen['artifacts']]
    expected = {('NATIVE',20,1,38), ('JAVA',20,1,38), ('NATIVE',0,32,36), ('NATIVE',20,32,11), ('JAVA',20,32,11)}
    assert len(passing) == 5
    assert {(p['backend'],p['tree_budget_ms'],p['tree_max_parts'],p['required_tests_passed']) for p in passing} == expected
    units = []
    for path in sorted((ROOT / 'local').glob('wirebudget-unit-*/TEST-*.xml')):
        xml = ET.parse(path).getroot()
        units.append(dict(attempt=path.parent.name, evidence=evidence(path), suite=xml.attrib['name'],
                          tests=int(xml.attrib['tests']), failures=int(xml.attrib['failures']), errors=int(xml.attrib['errors'])))
    final_units = [u for u in units if u['attempt'] == 'wirebudget-unit-02']
    assert sum(u['tests'] for u in final_units) == 10 and all(u['failures'] == u['errors'] == 0 for u in final_units)
    measurements = [measurement(name) for name in args.attempts]
    clean = [x for x in measurements if x['protocol'].get('validation',{}).get('passed')
             and not x['protocol']['reload'] and not x['protocol']['loading_trace']]
    assert len(clean) == 4 and [x['protocol']['tree_budget_ms'] for x in clean] == [20,0,0,20]
    assert all(x['protocol']['runtime']['sha256'] == frozen['artifacts']['runtime']['sha256'] for x in measurements)
    assert all(x['protocol']['helper']['sha256'] == clean[0]['protocol']['helper']['sha256'] for x in measurements)
    assert clean[0]['protocol']['helper']['sha256'] == helper['helper']['sha256']
    assert all(x['protocol']['tree_batch'] and x['protocol']['tree_max_parts'] == 32 for x in clean)
    groups = {}
    for name, budget in [('unbounded',0), ('bounded',20)]:
        subset = [x for x in clean if x['protocol']['tree_budget_ms'] == budget]
        durations = [x['protocol']['loading'][0]['total_restoration_seconds'] for x in subset]
        worst = [x['tick_metrics']['SETTLE']['longest_tick_seconds'] for x in subset]
        gaps = [x['tick_metrics']['SETTLE']['longest_end_to_end_gap_seconds'] for x in subset]
        groups[name] = dict(restoration_seconds=durations, mean_seconds=statistics.mean(durations),
                            longest_tick_seconds=worst, longest_end_to_end_gap_seconds=gaps)
    comparison = dict(**groups, restoration_reduction_percent=100*(1-groups['bounded']['mean_seconds']/groups['unbounded']['mean_seconds']),
                      worst_tick_reduction_percent=100*(1-max(groups['bounded']['longest_tick_seconds'])/max(groups['unbounded']['longest_tick_seconds'])),
                      note='Two fresh samples per mode in bounded/unbounded/unbounded/bounded order; no significance or sustained-TPS claim.')
    result = dict(schema=1, frozen_inputs=evidence(manifest), artifacts=frozen['artifacts'],
                  matched_source_entries=len(frozen['sources']), native_unchanged=frozen['native_unchanged'],
                  changed_runtime_entries=frozen['changed_jar_entries'], reference_inventory=evidence(ROOT / 'local/inputs/reference-copy.json'),
                  focused_checks=checks, unit_checks=units, focused_passing_gametests=134, focused_passing_unit_checks=10,
                  full_suite=False, measurements=measurements, comparison=comparison,
                  helper_inputs=evidence(helper_manifest), helper=helper, focused_helper_gate_checks=7,
                  failed_benchmark_attempts=[measurement('budget20-01')],
                  previous_experiment=evidence(ROOT / 'docs/experimental/wire-tree-results.json'),
                  frozen_attempts=[evidence(ROOT / 'local/frozen' / n / 'inputs.json') for n in ('prototype-05a','prototype-05b')],
                  build_evidence=[evidence(p) for p in sorted((ROOT / 'local').glob('wirebudget-build-*.log'))],
                  tool_sources=[evidence(ROOT / 'tools/experimental' / n) for n in ('freeze_tree.py','focused_checks.py',
                                'restoration_benchmark.py','host_metrics.py','summarize_tree.py','summarize_budget.py',
                                'plain-wire.gradle','plain_wire_benchmark.py','freeze_budget_helper.py','budget-helper.gradle',
                                'budget-helper/LoadingGate.java','budget-helper/LoadingGateChecks.java')])
    with (ROOT / args.output).open('x',encoding='utf-8',newline='\n') as stream:
        stream.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(comparison))


if __name__ == '__main__':
    main()
