"""Analyze retained restoration evidence; no server launches or evidence replacement."""
import argparse
import collections
import csv
import datetime
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRACE = 'org.cha0scollective.wwpg.wiring.WireTreeTraversal.trace'
RESOLVE = 'org.patryk3211.powergrid.electricity.WorldNetworks.resolveTree'
GROUPS = [
    ('wire_tree_resolution', TRACE),
    ('electrical_schedule', 'org.cha0scollective.wwpg.bridge.ServerElectricalSchedule.preTick'),
    ('cee_preparation', 'org.cha0scollective.wwpg.bridge.WorldBridge.prepare'),
    ('cee_commit', 'org.cha0scollective.wwpg.bridge.WorldBridge.commit'),
]


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def rows(path):
    with path.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream))


def evidence(path):
    return dict(path=path.relative_to(ROOT).as_posix(),
                sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def unix_ms(event):
    return datetime.datetime.fromisoformat(event['start'].replace('Z', '+00:00')).timestamp() * 1000


def counts(counter, denominator, maximum=None):
    return [dict(name=name, samples=count, percent=100 * count / denominator)
            for name, count in counter.most_common(maximum)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', default='restoration-profile-01')
    parser.add_argument('--output', default='docs/experimental/restoration-profile-results.json')
    args = parser.parse_args()
    directory = ROOT / 'local/benchmarks' / args.attempt
    data = directory / 'benchmark'
    protocol = read(directory / 'experiment.json')
    assert protocol['validation']['passed']
    assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
    result = read(data / 'result.json')
    assert result['rejected_solves'] == result['cee_solves'] == 0
    ticks = rows(data / 'ticks.csv')
    placement = rows(data / 'placement-ticks.csv')
    timers = rows(data / 'loading-timings.csv')
    snapshots = [json.loads(line) for line in (data / 'loading.jsonl').read_text().splitlines()]
    loading = read(data / 'loading-results.json')[0]
    assert len(ticks) == len(placement) == len(timers) == loading['finish_tick']
    assert all(int(t['pg_solve_calls']) % 16 == 0 for t in ticks)
    assert all(int(t['pg_solve_calls']) == 16 and int(t['matrix_assemblies']) == 0 for t in ticks[-100:])
    frozen = read(ROOT / 'local/frozen/prototype-02c/inputs.json')
    assert protocol['runtime']['sha256'] == frozen['artifacts']['runtime']['sha256']
    assert evidence(Path(protocol['runtime']['path']))['sha256'] == protocol['runtime']['sha256']

    # Tick one includes startup: its counter baseline was captured before the
    # placement recorder existed. Exclude it from counter and JFR attribution.
    first = placement[1]
    start = int(first['wall_unix_ms']) - int(first['wall_tick_ns']) / 1e6
    end = int(placement[-1]['wall_unix_ms'])
    events = [e for e in read(data / 'loading-jfr-compact.json')['events'] if start <= unix_ms(e) <= end]
    samples = [e for e in events if e['thread'] == 'Server thread'
               and e['type'] in ('jdk.ExecutionSample', 'jdk.NativeMethodSample')]
    groups, inclusive, parents, leaves = (collections.Counter() for _ in range(4))
    for event in samples:
        frames = event['frames']
        groups[next((name for name, frame in GROUPS if frame in frames), 'other')] += 1
        inclusive.update(set(frames))
        leaves[frames[0] if frames else 'no_stack'] += 1
        if TRACE in frames:
            index = frames.index(RESOLVE) if RESOLVE in frames else len(frames)
            parents[frames[index + 1] if index + 1 < len(frames) else 'truncated'] += 1
    allocations = [e for e in events if e['thread'] == 'Server thread' and e['type'] == 'jdk.ObjectAllocationSample']
    sites = collections.Counter()
    for event in allocations:
        site = next((f for f in event['frames'] if f.startswith(('org.cha0scollective.', 'org.patryk3211.', 'com.george_vi.'))), 'other')
        sites[site] += event['weight']
    totals = {key: sum(int(row[key]) for row in timers)
              for key in timers[0] if key not in ('tick', 'phase', 'largest_allocation')}
    intervals = []
    for low, high in [(1, 20), (21, 350), (351, 700), (701, loading['chunks_ready_tick']),
                      (loading['graph_ready_tick'], loading['finish_tick'])]:
        interval = [t for t in timers if low <= int(t['tick']) <= high]
        intervals.append(dict(first_tick=low, last_tick=high,
                              matrix_stamps=sum(int(t['matrix_stamps']) for t in interval),
                              matrix_stamp_seconds=sum(int(t['matrix_stamp_ns']) for t in interval) / 1e9,
                              network_substep_calls=sum(int(t['accepted_substeps']) + int(t['rejected_substeps']) for t in interval)))
    timeline = []
    for snapshot in snapshots:
        if snapshot['tick'] % 100 and snapshot != snapshots[0]:
            continue
        row = {k: snapshot[k] for k in ('tick', 'block_chunks_present', 'ticking_chunks', 'entity_chunks_loaded',
                                        'entity_loading_inbox', 'pg_wire_entities', 'solver_networks', 'largest_matrix')}
        row['waiting_status_counts'] = dict(collections.Counter(
            f"{c['entity_load_status']}/{c['entity_visibility']}" for c in snapshot['waiting_chunks']))
        timeline.append(row)
    historical = []
    for name in ('property-enabled-clean-01', 'property-enabled-clean-02', 'profile-batched-timers-01',
                 'profile-batched-jfr-01', 'packed-quiet-control-01'):
        folder = ROOT / 'local/benchmarks' / name
        historical.append(dict(name=name, evidence=evidence(folder / 'benchmark/loading-results.json'),
                               runtime=read(folder / 'launch.json')['runtime'],
                               loading=read(folder / 'benchmark/loading-results.json')[0], evidence_mode='historical'))
    files = [directory / name for name in ('experiment.json', 'launch.json', 'invocation.json', 'restore-input.json',
             'shutdown-save.json', 'runner-exit.json', 'process.log', 'process-metrics.jsonl', 'loading.jfr')]
    files += [data / name for name in ('loading-results.json', 'loading-timings.csv', 'loading.jsonl', 'ticks.csv',
              'placement-ticks.csv', 'result.json', 'audit.json', 'ready-SETTLE-0.json', 'pre-solve-storage.json',
              'placement-fixed.json', 'placement-restored.json', 'loading-jfr-compact.json', 'loading-jfr-summary.txt')]
    source_files = [ROOT / name for name in ('src/main/java/org/cha0scollective/wwpg/wiring/WireTreeTraversal.java',
                    'src/main/java/org/cha0scollective/wwpg/mixin/PgWireTraceMixin.java',
                    'src/main/java/org/cha0scollective/wwpg/bridge/RestorationWindow.java',
                    'src/main/java/org/cha0scollective/wwpg/bridge/RestorationCoordinator.java',
                    'tools/experimental/JfrCompact.java', 'tools/experimental/restoration_profile.py')]
    source_files += sorted((ROOT / 'local/restoration-source-01').glob('*'))
    out = dict(schema=1, scope='Single instrumented copied-save restoration; no performance improvement or capacity claim',
               runtime_unchanged=True, runtime=protocol['runtime'], helper=protocol['helper'],
               validation=protocol['validation'], sixteen_substeps=True, final_hundred_ticks_one_network_no_rebuild=True,
               original_reference_unchanged=True, isolated_reference_unchanged=True, loading=loading,
               loading_totals=totals, intervals=intervals, readiness_timeline=timeline,
               first_snapshot_all_block_chunks=all(s['block_chunks_present'] == 953 for s in snapshots),
               largest_matrix=max(int(t['largest_allocation']) for t in timers),
               counters=dict(excluded_tick=1, first_tick_raw=placement[0], ticks=len(placement) - 1,
                             window_start_unix_ms=start, window_end_unix_ms=end, wall_seconds=(end - start) / 1000,
                             in_tick_wall_seconds=sum(int(t['wall_tick_ns']) for t in placement[1:]) / 1e9,
                             in_tick_cpu_seconds=sum(int(t['cpu_tick_ns']) for t in placement[1:]) / 1e9,
                             in_tick_allocated_bytes=sum(int(t['allocated_bytes']) for t in placement[1:]),
                             note='Counters cover tick bodies only; entity reconstruction also runs between ticks.'),
               jfr=dict(server_samples=len(samples), sample_types=dict(collections.Counter(e['type'] for e in samples)),
                        exclusive_groups=counts(groups, len(samples)), trace_callers=counts(parents, len(samples)),
                        inclusive_frames=counts(inclusive, len(samples), 120), leaf_frames=counts(leaves, len(samples), 30),
                        gc_pause_seconds=sum(e['duration_ns'] for e in events if e['type'] == 'jdk.GCPhasePause') / 1e9,
                        server_park_seconds=sum(e['duration_ns'] for e in events if e['thread'] == 'Server thread' and e['type'] == 'jdk.ThreadPark') / 1e9,
                        sampled_allocation_weight_bytes=sum(e['weight'] for e in allocations),
                        trace_allocation_weight_bytes=sum(e['weight'] for e in allocations if TRACE in e['frames']),
                        allocation_sites=[dict(site=k, sampled_weight_bytes=v) for k, v in sites.most_common(20)],
                        caution='CPU samples are not exclusive elapsed timers. Allocation weights are estimates for the whole window, not tick-counter totals. Native stacks may be underrepresented.'),
               historical=historical, evidence=[evidence(p) for p in files], inspected_sources=[evidence(p) for p in source_files])
    destination = ROOT / args.output
    with destination.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(out, indent=2) + '\n')
    print(json.dumps(dict(output=str(destination), loading=loading, jfr_groups=out['jfr']['exclusive_groups'],
                          matrix_stamps=totals['matrix_stamps'], matrix_stamp_seconds=totals['matrix_stamp_ns'] / 1e9)))


if __name__ == '__main__':
    main()
