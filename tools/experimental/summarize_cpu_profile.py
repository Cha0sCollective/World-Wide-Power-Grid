"""Audit CPU-only JFR against frozen artifacts and completed clean samples."""
import argparse
from collections import Counter
import json
from pathlib import Path

from cpu_profile import filter_events, instant_ns, steady_window, validate_recording, write_new
from summarize_current_profile import signature
from summarize_goal_runs import capture
from summarize_profile import category, export_jfr, jfr_profile, ranked, rows, stats
from summarize_tree import ROOT, evidence, read, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--clean-report', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    assert Path(args.attempt).name == args.attempt
    output = ROOT / args.output
    assert not output.exists()
    frozen = ROOT / args.inputs
    inputs = read(frozen)
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in inputs['sources'])
    assert all(sha(Path(item['path'])) == item['sha256'] for item in inputs['artifacts'].values())
    directory = ROOT / 'local/benchmarks' / args.attempt
    run = capture(args.attempt)
    protocol = run['protocol']
    assert protocol['cpu_jfr'] and not protocol['detailed'] and not protocol['substep_jfr']
    assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
    assert validate_recording(directory) == protocol['recording']
    clean_path = ROOT / args.clean_report
    clean = read(clean_path)
    matched = [r for r in clean.get('runs', clean.get('steady', [])) if signature(r['protocol']) == signature(protocol)]
    assert matched
    for item in matched:
        assert not any(item['protocol'].get(key, False) for key in ('detailed', 'substep_jfr', 'cpu_jfr'))
        assert item['protocol']['validation']['passed'] and item['summary']['passed']
        for proof in item['evidence']:
            assert sha(ROOT / proof['path']) == proof['sha256'], proof['path']
    ticks = rows(directory / 'benchmark/ticks.csv')
    placed = rows(directory / 'benchmark/placement-ticks.csv')
    window = steady_window(ticks, placed)
    recording = directory / 'benchmark/cpu-profile.jfr'
    exported = export_jfr(directory, inputs['java_home'], recording='cpu-profile.jfr', export_name='cpu-jfr-compact')
    all_events = read(exported)['events']
    selected, excluded = filter_events(all_events, window)
    assert selected
    server_samples = [e for e in all_events if e['thread'] == 'Server thread' and e['type'] == 'jdk.ExecutionSample']
    sample_times = [instant_ns(e['start']) for e in server_samples]
    assert min(sample_times) < window['start_unix_ns'] < window['end_unix_ns'] < max(sample_times)
    assert sum(e['thread'] == 'Server thread' and e['type'] == 'jdk.ExecutionSample' for e in selected) >= 100
    filtered_path = directory / 'benchmark/cpu-jfr-steady.json'
    filtered = dict(schema=1, source=evidence(exported), window=window, excluded_event_counts=excluded, events=selected)
    if filtered_path.exists():
        assert read(filtered_path) == filtered, 'Existing filtered export differs; preserve it'
    else:
        write_new(filtered_path, filtered)
    jfr = jfr_profile(filtered_path)
    jfr['limitations'][-1] = window['scope']
    jfr['limitations'].append('Events crossing either inner boundary are excluded whole. The first retained server allocation weight is excluded because it can span the boundary.')
    allocations = sorted((e for e in selected if e['type'] == 'jdk.ObjectAllocationSample'
                          and e['thread'] == 'Server thread'), key=lambda e: instant_ns(e['start']))
    sites = {}
    for event in allocations[1:]:
        scope = category(event['frames'])
        site = event['object_class'] + ' @ ' + ' <- '.join(event['frames'][:6])
        sites.setdefault(scope, Counter())[site] += event['weight']
    jfr['allocation']['sites_by_scope'] = {scope: ranked(counter, 15) for scope, counter in sites.items()}
    stable = [row for row in placed if row['phase'] == 'STABLE']
    counters = dict(ticks=len(stable), cpu_ms=stats([int(row['cpu_tick_ns']) / 1e6 for row in stable]),
                    allocated_bytes=stats([int(row['allocated_bytes']) for row in stable]),
                    compilation_ms=int(stable[-1]['compile_ms']) - int(stable[0]['compile_ms']))
    proofs = [evidence(path) for path in (frozen, clean_path, recording, exported, filtered_path,
              directory / 'benchmark/cpu-jfr-compact-export.json', directory / 'cpu-profile-start.json')]
    proofs.extend(evidence(ROOT / 'tools/experimental' / name) for name in
                  ('cpu_profile.py', 'summarize_cpu_profile.py', 'summarize_current_profile.py',
                   'summarize_profile.py', 'summarize_goal_runs.py', 'JfrCompact.java'))
    data = dict(schema=1, full_suite=False, runtime_changed=False, helper_changed=False,
                debug_non_safepoints=protocol.get('cpu_jfr_non_safepoints', False),
                source_hashes_verified=len(inputs['sources']), diagnostic=run,
                matched_clean_attempts=[r['name'] for r in matched], window=window,
                raw_event_counts=dict(Counter(e['type'] for e in all_events)),
                excluded_event_counts=excluded, thread_counters=counters, jfr=jfr, evidence=proofs,
                note='CPU-only JFR diagnostic with detailed timers disabled. Frozen gameplay/helper jars are unchanged. CPU samples identify investigation candidates, not phase timings or demonstrated speedups.')
    write_new(output, data)
    print(json.dumps(dict(attempt=args.attempt, mspt=run['summary']['mspt'], thread_counters=counters,
                          java_scopes=jfr['cpu']['jdk.ExecutionSample']['server_scopes'])))


if __name__ == '__main__':
    main()
