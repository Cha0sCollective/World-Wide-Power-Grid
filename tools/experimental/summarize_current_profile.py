"""Audit one current diagnostic profile against preserved matching clean runs."""
import argparse
from collections import Counter
import json
from pathlib import Path

from summarize_goal_runs import capture
from summarize_profile import export_jfr, jfr_profile, phase_profile, rows, stats, category, ranked
from summarize_tree import ROOT, evidence, read, sha


def signature(protocol):
    keys = ('batch', 'property_batch', 'verify_batch', 'tree_batch', 'tree_budget_ms',
            'tree_max_parts', 'tree_retain_coverage', 'tree_prioritize_unresolved',
            'chunk_task_budget_ms', 'cee_state_geometry', 'terminal_bookkeeping', 'cpu_affinity_mask', 'server_affinity_mask')
    return {**{key: protocol.get(key) for key in keys},
            'runtime': protocol['runtime']['sha256'], 'helper': protocol['helper']['sha256']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--clean-report', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    assert Path(args.attempt).name == args.attempt
    frozen = ROOT / args.inputs
    inputs = read(frozen)
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in inputs['sources'])
    assert all(sha(Path(item['path'])) == item['sha256'] for item in inputs['artifacts'].values())
    directory = ROOT / 'local/benchmarks' / args.attempt
    run = capture(args.attempt)
    protocol = run['protocol']
    assert protocol['detailed'] and protocol['substep_jfr']
    assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
    clean_path = ROOT / args.clean_report
    clean = read(clean_path)
    matched = [r for r in clean['runs'] if signature(r['protocol']) == signature(protocol)]
    assert matched
    for item in matched:
        assert not item['protocol']['detailed'] and not item['protocol']['substep_jfr']
        assert item['protocol']['validation']['passed'] and item['summary']['passed']
        for proof in item['evidence']:
            assert sha(ROOT / proof['path']) == proof['sha256'], proof['path']
    stable = [row for row in rows(directory / 'benchmark/ticks.csv') if row['phase'] == 'STABLE']
    placement = [row for row in rows(directory / 'benchmark/placement-ticks.csv') if row['phase'] == 'STABLE']
    phases, _ = phase_profile(directory, stable)
    # The earlier worker prototype was retired. Preserve measured scopes here,
    # without recycling the old hypothetical worker-scaling projections.
    phases.pop('models')
    phases.pop('impossible_zero_cost_bounds')
    recording = directory / 'benchmark/substeps.jfr'
    assert sha(recording) == protocol['recording']['sha256']
    exported = export_jfr(directory, inputs['java_home'])
    jfr = jfr_profile(exported)
    allocations = sorted((e for e in read(exported)['events']
        if e['type'] == 'jdk.ObjectAllocationSample' and e['thread'] == 'Server thread'), key=lambda e: e['start'])
    sites = {}
    for event in allocations[1:]:
        scope = category(event['frames'])
        site = event['object_class'] + ' @ ' + ' <- '.join(event['frames'][:6])
        sites.setdefault(scope, Counter())[site] += event['weight']
    jfr['allocation']['sites_by_scope'] = {scope: ranked(counter, 15) for scope, counter in sites.items()}
    counters = dict(ticks=len(placement), cpu_ms=stats([int(row['cpu_tick_ns']) / 1e6 for row in placement]),
        allocated_bytes=stats([int(row['allocated_bytes']) for row in placement]),
        compilation_ms=int(placement[-1]['compile_ms']) - int(placement[0]['compile_ms']))
    proofs = [evidence(frozen), evidence(clean_path), evidence(recording), evidence(exported),
        evidence(directory / 'benchmark/jfr-compact-export.json'), evidence(directory / 'benchmark/substeps.csv')]
    proofs.extend(evidence(ROOT / 'tools/experimental' / name) for name in
        ('summarize_current_profile.py', 'summarize_profile.py', 'summarize_goal_runs.py', 'JfrCompact.java'))
    data = dict(schema=1, full_suite=False, runtime_changed=False, source_hashes_verified=len(inputs['sources']),
        diagnostic=run, matched_clean_attempts=[r['name'] for r in matched], thread_counters=counters,
        phases=phases, jfr=jfr, evidence=proofs,
        note='Instrumented diagnostic only. Clean runs establish observed performance; phase samples and allocation estimates identify candidates. No worker speedup or calibrated instrumentation overhead is claimed.')
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2) + '\n')
    print(json.dumps(dict(attempt=args.attempt, sampled_tick_ms=phases['weighted_sampled_tick_ms'],
        exclusive_mean_ms={key: value['mean'] for key, value in phases['exclusive_cost_ms'].items()},
        thread_counters=counters, java_scopes=jfr['cpu']['jdk.ExecutionSample']['server_scopes'])))


if __name__ == '__main__':
    main()
