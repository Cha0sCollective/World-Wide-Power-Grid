"""Audit sparse existing phase timers with JFR absent from the entire process."""
import argparse
import json
from pathlib import Path

from cpu_profile import write_new
from summarize_current_profile import signature
from summarize_goal_runs import capture
from summarize_profile import phase_profile, rows, stats
from summarize_tree import ROOT, read, sha, evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--clean-report', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    output = ROOT / args.output
    assert not output.exists()
    inputs = read(ROOT / args.inputs)
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in inputs['sources'])
    assert all(sha(Path(item['path'])) == item['sha256'] for item in inputs['artifacts'].values())
    run = capture(args.attempt)
    protocol = run['protocol']
    assert protocol['detailed'] and not protocol['cpu_jfr'] and not protocol['substep_jfr'] and not protocol['preparation_sections']
    assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
    clean = read(ROOT / args.clean_report)
    matching = [item for item in clean['runs'] if signature(item['protocol']) == signature(protocol)
                and not any(item['protocol'].get(key, False) for key in ('detailed','cpu_jfr','substep_jfr','preparation_sections'))]
    assert matching
    for item in matching:
        for proof in item['evidence']:
            assert sha(ROOT / proof['path']) == proof['sha256'], proof['path']
    directory = ROOT / 'local/benchmarks' / args.attempt
    assert not list(directory.glob('**/*.jfr'))
    assert read(directory / 'runner-exit.json')['exit_code'] == 0
    ticks = [row for row in rows(directory / 'benchmark/ticks.csv') if row['phase'] == 'STABLE']
    placement = [row for row in rows(directory / 'benchmark/placement-ticks.csv') if row['phase'] == 'STABLE']
    assert [row['tick'] for row in ticks] == [row['tick'] for row in placement]
    phases, flat = phase_profile(directory, ticks)
    phases.pop('models')
    phases.pop('impossible_zero_cost_bounds')
    sampled = [row for row in ticks if row['timing_sample'] == '1']
    forced_ticks = {row['tick'] for row in rows(directory / 'benchmark/substeps.csv') if row['forced_edit_sample'] == 'true'}
    forced_count = sum(row['tick'] in forced_ticks for row in sampled)
    ordinary_count = len(sampled) - forced_count
    weights = [1 if row['tick'] in forced_ticks else (len(ticks)-forced_count)/ordinary_count for row in sampled]
    groups = {}
    for slow in (False, True):
        indices = [i for i, row in enumerate(sampled) if (int(row['tick_ns']) > 50_000_000) == slow]
        assert indices
        groups['over_50_ms' if slow else 'at_most_50_ms'] = dict(samples=len(indices),
            tick_ms=stats([int(sampled[i]['tick_ns'])/1e6 for i in indices], [weights[i] for i in indices]),
            phases={key:stats([flat[i][key] for i in indices], [weights[i] for i in indices]) for key in flat[0]})
    counters = dict(cpu_ms=stats([int(row['cpu_tick_ns'])/1e6 for row in placement]),
        allocated_mib=stats([int(row['allocated_bytes'])/2**20 for row in placement]),
        compilation_ms=int(placement[-1]['compile_ms'])-int(placement[0]['compile_ms']))
    paths = [ROOT / args.inputs, ROOT / args.clean_report, directory / 'benchmark/substeps.csv',
             ROOT / 'tools/experimental/summarize_phase_timers.py', ROOT / 'tools/experimental/summarize_profile.py']
    data = dict(schema=1, gameplay_jar_unchanged=True, full_suite=False, graphical_acceptance=False,
        source_hashes_verified=len(inputs['sources']), diagnostic=run, matched_clean_attempts=[r['name'] for r in matching],
        phases=phases, sampled_tick_groups=groups, thread_counters=counters, evidence=[evidence(path) for path in paths],
        note='Sparse phase/substep timing without JFR. Timing and tracing can perturb compiled code; these are diagnostic scopes and associations, not removable CPU or measured worker gains.')
    write_new(output, data)
    print(json.dumps(dict(attempt=args.attempt, mspt=run['summary']['mspt'], counters=counters,
        phases={k:v['mean'] for k,v in phases['exclusive_cost_ms'].items()}, tick_groups=groups)))


if __name__ == '__main__':
    main()
