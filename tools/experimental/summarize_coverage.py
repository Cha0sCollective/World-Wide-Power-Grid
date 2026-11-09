"""Capture the retained-coverage experiment, including its negative timing result."""
import argparse
import collections
import csv
import datetime
import json
from pathlib import Path
import statistics
import xml.etree.ElementTree as ET

from summarize_budget import measurement
from summarize_tree import ROOT, evidence, read, sha


def profile(name):
    folder = ROOT / 'local/benchmarks' / name
    result = measurement(name)
    with (folder / 'benchmark/placement-ticks.csv').open() as stream:
        ticks = list(csv.DictReader(stream))
    start = int(ticks[1]['wall_unix_ms']) - int(ticks[1]['wall_tick_ns']) / 1e6
    end = int(ticks[-1]['wall_unix_ms'])
    compact = folder / 'benchmark/loading-jfr-compact.json'
    samples = [e for e in read(compact)['events']
               if e['thread'] == 'Server thread' and e['type'] == 'jdk.ExecutionSample'
               and start <= datetime.datetime.fromisoformat(e['start'].replace('Z', '+00:00')).timestamp()*1000 <= end]
    inclusive = collections.Counter(f for e in samples for f in set(e['frames']))
    result['server_java_samples'] = len(samples)
    result['inclusive_methods'] = [dict(method=f, samples=n, percent=100*n/len(samples))
                                    for f,n in inclusive.most_common()
                                    if f.startswith(('org.cha0scollective.', 'org.patryk3211.', 'com.george_vi.')) and n >= .02*len(samples)]
    result['evidence'].append(evidence(compact))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', default='local/frozen/prototype-06a/inputs.json')
    parser.add_argument('--output', default='docs/experimental/retained-coverage-results.json')
    args = parser.parse_args()
    manifest = ROOT / args.inputs
    frozen = read(manifest)
    assert all(sha(ROOT / s['path']) == s['sha256'] for s in frozen['sources'])
    assert all(sha(Path(a['path'])) == a['sha256'] for a in frozen['artifacts'].values())
    checks = []
    for path in sorted((ROOT / 'local/checks').glob('coverage-06a-*/result.json')):
        check = read(path)
        assert check['passed'] and check['artifacts'] == frozen['artifacts'] and check['tree_retain_coverage']
        checks.append(dict(name=path.parent.name, result=check,
                           evidence=[evidence(path), evidence(path.parent / 'process.log')]))
    assert sorted(c['result']['required_tests_passed'] for c in checks) == [36,36,38]
    units = []
    for path in sorted((ROOT / 'local/checks/coverage-units-01/results').glob('TEST-*.xml')):
        xml = ET.parse(path).getroot()
        assert xml.attrib['failures'] == xml.attrib['errors'] == '0'
        units.append(dict(suite=xml.attrib['name'], tests=int(xml.attrib['tests']), evidence=evidence(path)))
    assert sum(u['tests'] for u in units) == 12
    runs = [measurement(n) for n in ('coverage-on-01','coverage-off-01','coverage-off-02','coverage-on-02')]
    helper = read(ROOT / 'local/frozen/budget-helper-01/inputs.json')
    assert sha(Path(helper['helper']['path'])) == helper['helper']['sha256']
    for run in runs:
        p = run['protocol']
        assert p['validation']['passed'] and not p['reload'] and not p['loading_trace']
        assert p['runtime']['sha256'] == frozen['artifacts']['runtime']['sha256']
        assert p['helper']['sha256'] == helper['helper']['sha256']
        assert p['tree_batch'] and p['tree_budget_ms'] == 20 and p['tree_max_parts'] == 32
    groups = {}
    for name, enabled in [('control',False), ('retained',True)]:
        selected = [r for r in runs if r['protocol']['tree_retain_coverage'] == enabled]
        assert len(selected) == 2
        groups[name] = dict(mean_seconds=statistics.mean(r['protocol']['loading'][0]['total_restoration_seconds'] for r in selected),
                            durations=[r['protocol']['loading'][0]['total_restoration_seconds'] for r in selected],
                            visits=[r['protocol']['tree_execution']['visited'] for r in selected],
                            worst_ticks=[r['tick_metrics']['SETTLE']['longest_tick_seconds'] for r in selected])
    comparison = dict(**groups, increase_percent=100*(groups['retained']['mean_seconds']/groups['control']['mean_seconds']-1),
                      conclusion='No loading improvement on this yard. Retire runtime change; preserve frozen prototype and evidence.',
                      limits='Two samples per mode; fresh-process saves, not flushed caches; no significance, rendering or sustained-TPS claim.')
    result = dict(schema=1, inputs=evidence(manifest), artifacts=frozen['artifacts'],
                  sources_verified=len(frozen['sources']), native_unchanged=frozen['native_unchanged'],
                  checks=checks, unit_checks=units, full_suite=False, comparison=comparison,
                  measurements=runs, prior_runtime_profile=profile('goal-budget-profile-01'),
                  helper_reused=evidence(ROOT / 'local/frozen/budget-helper-01/inputs.json'),
                  tool_sources=[evidence(ROOT / 'tools/experimental' / n) for n in
                                ('summarize_coverage.py','summarize_budget.py','summarize_tree.py',
                                 'restoration_benchmark.py','plain_wire_benchmark.py','focused_checks.py','plain-wire.gradle')])
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result,indent=2)+'\n')
    print(json.dumps(comparison))


if __name__ == '__main__':
    main()
