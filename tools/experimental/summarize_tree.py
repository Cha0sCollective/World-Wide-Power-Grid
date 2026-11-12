"""Summarize immutable focused wire-tree checks and copied-save measurements."""
import argparse
import collections
import csv
import hashlib
import json
from pathlib import Path
import statistics
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def evidence(path):
    return dict(path=path.relative_to(ROOT).as_posix(), sha256=sha(path))


def host_summary(directory):
    snapshots = [json.loads(line) for line in (directory / 'host-metrics.jsonl').read_text().splitlines()]
    assert snapshots and all('error' not in row for row in snapshots)
    with (directory / 'benchmark/placement-ticks.csv').open() as stream:
        ticks = list(csv.DictReader(stream))
    start = int(ticks[1]['wall_unix_ms']) / 1000 - int(ticks[1]['wall_tick_ns']) / 1e9
    end = int(ticks[-1]['wall_unix_ms']) / 1000
    child = str(read(directory / 'runner-exit.json')['pid'])
    peaks, totals, names = collections.Counter(), collections.Counter(), {}
    elapsed, intervals = 0.0, 0
    game_present = any('FactoryGame' in process['name'] for snap in snapshots for process in snap['processes'].values())
    for first, second in zip(snapshots, snapshots[1:]):
        if first['wall_unix'] < start or second['wall_unix'] > end:
            continue
        seconds = second['wall_unix'] - first['wall_unix']
        elapsed += seconds
        intervals += 1
        for pid, value in second['processes'].items():
            old = first['processes'].get(pid)
            if pid == child or old is None or old['created'] != value['created']:
                continue
            key = f"{pid}:{value['created']}"
            delta = max(0, value['cpu_seconds'] - old['cpu_seconds'])
            peaks[key] = max(peaks[key], delta / seconds)
            totals[key] += delta
            names[key] = value['name']
    assert intervals > 0
    return dict(sample_period_seconds=5, in_window_intervals=intervals, in_window_seconds=elapsed,
                monitored_processes=max(len(s['processes']) for s in snapshots), competing_game_present=game_present,
                other_cpu_mean_cores=sum(totals.values()) / elapsed,
                other_process_peaks=[dict(name=names[k], identity=k, peak_cores=v, mean_cores=totals[k] / elapsed)
                                     for k, v in peaks.most_common(8)],
                note='Read-only process CPU counters; overlapping whole-window samples only; not CPU-frequency or thermal control.')


def attempt(name):
    directory = ROOT / 'local/benchmarks' / name
    protocol = read(directory / 'experiment.json')
    entry = dict(name=name, protocol=protocol, evidence=[evidence(directory / 'experiment.json')])
    if protocol.get('validation', {}).get('passed'):
        assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
        entry['host'] = host_summary(directory)
        with (directory / 'benchmark/ticks.csv').open() as stream:
            distinct = {(r['phase'], r['tick']): r for r in csv.DictReader(stream)}
        entry['tick_metrics'] = {}
        for phase in sorted({key[0] for key in distinct}):
            ticks = [r for (p, _), r in distinct.items() if p == phase]
            entry['tick_metrics'][phase] = dict(ticks=len(ticks),
                longest_tick_seconds=max(int(t['tick_ns']) for t in ticks) / 1e9,
                tick_body_seconds=sum(int(t['tick_ns']) for t in ticks) / 1e9,
                matrix_assemblies=sum(int(t['matrix_assemblies']) for t in ticks),
                network_substep_calls=sum(int(t['pg_solve_calls']) for t in ticks))
        entry['evidence'] += [evidence(directory / p) for p in ('launch.json', 'invocation.json', 'restore-input.json',
                               'shutdown-save.json', 'runner-exit.json', 'host-metrics.jsonl', 'process.log',
                               'benchmark/result.json', 'benchmark/loading-results.json', 'benchmark/ticks.csv',
                               'benchmark/placement-ticks.csv', 'benchmark/pre-solve-storage.json',
                               'benchmark/placement-fixed.json', 'benchmark/placement-restored.json')]
        if protocol['reload']:
            entry['evidence'].append(evidence(directory / 'benchmark/lifecycle.json'))
        if protocol['loading_trace']:
            entry['evidence'] += [evidence(directory / p) for p in ('loading.jfr', 'benchmark/loading-timings.csv', 'benchmark/loading.jsonl')]
            with (directory / 'benchmark/loading-timings.csv').open() as stream:
                timers = list(csv.DictReader(stream))
            entry['loading_counter_totals'] = {k: sum(int(row[k]) for row in timers)
                                              for k in timers[0] if k not in ('tick', 'phase', 'largest_allocation')}
    return entry


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inputs', default='local/frozen/prototype-04c/inputs.json')
    parser.add_argument('--attempts', nargs='+', required=True)
    parser.add_argument('--output', default='docs/experimental/wire-tree-results.json')
    args = parser.parse_args()
    manifest = ROOT / args.inputs
    frozen = read(manifest)
    assert all(sha(ROOT / s['path']) == s['sha256'] for s in frozen['sources'])
    assert all(sha(Path(a['path'])) == a['sha256'] for a in frozen['artifacts'].values())
    checks = []
    for file in sorted((ROOT / 'local/checks').glob('tree-*/result.json')):
        result = read(file)
        checks.append(dict(name=file.parent.name, evidence=[evidence(file),
                           evidence(file.parent / 'invocation.json'), evidence(file.parent / 'process.log')], result=result))
    passing = [x for x in checks if x['result']['passed'] and x['result']['artifacts'] == frozen['artifacts']]
    assert len(passing) == 5 and sum(x['result']['required_tests_passed'] for x in passing) == 130
    units = []
    for directory in sorted((ROOT / 'local').glob('wiretree-unit-*')):
        for path in sorted(directory.glob('TEST-*.xml')):
            xml = ET.parse(path).getroot()
            units.append(dict(attempt=directory.name, evidence=evidence(path), suite=xml.attrib['name'],
                              tests=int(xml.attrib['tests']), failures=int(xml.attrib['failures']), errors=int(xml.attrib['errors'])))
    final_units = [u for u in units if u['attempt'] == 'wiretree-unit-04']
    assert sum(u['tests'] for u in final_units) == 6 and all(u['failures'] == u['errors'] == 0 for u in final_units)
    measurements = [attempt(name) for name in args.attempts]
    clean = [a for a in measurements if a['protocol'].get('validation', {}).get('passed')
             and not a['protocol']['loading_trace'] and not a['protocol']['reload']]
    assert len(clean) == 4
    assert all(a['protocol']['runtime']['sha256'] == frozen['artifacts']['runtime']['sha256'] for a in measurements)
    off = [a['protocol']['loading'][0]['total_restoration_seconds'] for a in clean if not a['protocol']['tree_batch']]
    on = [a['protocol']['loading'][0]['total_restoration_seconds'] for a in clean if a['protocol']['tree_batch']]
    assert len(off) == len(on) == 2
    assert [a['protocol']['tree_batch'] for a in clean] == [False, True, True, False]
    assert all(a['protocol']['helper']['sha256'] == clean[0]['protocol']['helper']['sha256'] for a in measurements)
    comparison = dict(control_seconds=off, enabled_seconds=on, control_mean=statistics.mean(off), enabled_mean=statistics.mean(on),
                      reduction_percent=100 * (1 - statistics.mean(on) / statistics.mean(off)),
                      speedup=statistics.mean(off) / statistics.mean(on),
                      note='Two fresh samples per mode in off/on/on/off order; no significance or sustained-capacity claim.')
    result = dict(schema=1, frozen_inputs=evidence(manifest), artifacts=frozen['artifacts'],
                  reference_inventory=evidence(ROOT / 'local/inputs/reference-copy.json'),
                  matched_source_entries=len(frozen['sources']), native_unchanged=frozen['native_unchanged'],
                  changed_runtime_entries=frozen['changed_jar_entries'], focused_checks=checks, unit_checks=units,
                  focused_passing_gametests=130, focused_passing_unit_checks=6, full_suite=False,
                  superseded_measurements=[attempt(n) for n in ('tree-control-01', 'tree-enabled-01', 'tree-enabled-02', 'tree-control-02')],
                  measurements=measurements, comparison=comparison,
                  build_evidence=[evidence(p) for p in sorted((ROOT / 'local').glob('wiretree-build-*.log'))],
                  frozen_attempts=[evidence(ROOT / 'local/frozen' / n / 'inputs.json')
                                   for n in ('prototype-04a', 'prototype-04b', 'prototype-04c')],
                  tool_sources=[evidence(ROOT / 'tools/experimental' / n) for n in ('freeze_tree.py', 'focused_checks.py',
                                'restoration_benchmark.py', 'host_metrics.py', 'summarize_tree.py', 'plain-wire.gradle', 'plain_wire_benchmark.py')])
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(result, indent=2) + '\n')
    print(json.dumps(comparison))


if __name__ == '__main__':
    main()
