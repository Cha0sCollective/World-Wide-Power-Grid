"""Summarize a completed JNI work diagnostic, preserving actual callback sequences."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path

from cpu_profile import write_new
from native_work import validate
from summarize_goal_runs import capture
from summarize_profile import phase_profile, rows, stats
from summarize_tree import ROOT, evidence, read, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--helper-inputs', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    inputs_path, helper_path = ROOT / args.inputs, ROOT / args.helper_inputs
    inputs, helper = read(inputs_path), read(helper_path)
    assert all(sha(ROOT / p['path']) == p['sha256'] for p in inputs['sources'] + helper['sources'])
    assert all(sha(Path(p['path'])) == p['sha256'] for p in inputs['artifacts'].values())
    assert sha(Path(helper['helper']['path'])) == helper['helper']['sha256']
    directory = ROOT / 'local/benchmarks' / args.attempt
    run = capture(args.attempt)
    assert run['protocol']['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
    assert run['protocol']['helper']['sha256'] == helper['helper']['sha256']
    assert run['protocol']['native_work'] and read(directory / 'runner-exit.json')['exit_code'] == 0
    assert not list(directory.glob('**/*.jfr'))
    validation = validate(directory, True)
    ticks = [r for r in rows(directory / 'benchmark/ticks.csv') if r['phase'] == 'STABLE']
    sampled = {r['tick']: r for r in ticks if r['timing_sample'] == '1'}
    by_tick = defaultdict(list)
    for row in rows(directory / 'benchmark/native-work.csv'):
        if row['phase'] == 'STABLE': by_tick[row['tick']].append(row)
    forced = {r['tick'] for r in rows(directory / 'benchmark/substeps.csv') if r['forced_edit_sample'] == 'true'}
    forced &= set(sampled)
    weights = {t: 1 if t in forced else (len(ticks) - len(forced)) / (len(sampled) - len(forced)) for t in sampled}
    patterns = defaultdict(list)
    for tick, records in by_tick.items():
        for row in records:
            key = tuple(row[k] for k in ('max_iterations', 'jacobian_commands', 'hook_indices', 'hook_commands', 'residual_calls'))
            patterns[key].append(row)
    pattern_report = []
    for key, records in sorted(patterns.items(), key=lambda pair: -len(pair[1])):
        limits, commands, indices, hook_commands, residuals = key
        pattern_report.append(dict(sampled_calls=len(records), sampled_ticks=len({r['tick'] for r in records}),
            max_iterations=int(limits), initial_jacobian_commands=int(commands),
            hook_indices=[int(v) for v in indices.split('|')], hook_jacobian_commands=[int(v) for v in hook_commands.split('|')],
            residual_calls=int(residuals), jni_ms=stats([int(r['native_ns']) / 1e6 for r in records], [weights[r['tick']] for r in records])))
    groups = {}
    for slow in (False, True):
        selected = [t for t, r in sampled.items() if (int(r['tick_ns']) > 50_000_000) == slow]
        w = [weights[t] for t in selected]
        groups['over_50_ms' if slow else 'at_most_50_ms'] = dict(sampled_ticks=len(selected),
            tick_ms=stats([int(sampled[t]['tick_ns']) / 1e6 for t in selected], w),
            native_scope_ms=stats([int(sampled[t]['native_solve_ns']) / 1e6 for t in selected], w),
            jni_ms=stats([sum(int(r['native_ns']) for r in by_tick[t]) / 1e6 for t in selected], w),
            initial_jacobian_commands=stats([sum(int(r['jacobian_commands']) for r in by_tick[t]) for t in selected], w),
            hook_calls=stats([sum(int(r['iteration_calls']) for r in by_tick[t]) for t in selected], w),
            residual_calls=stats([sum(int(r['residual_calls']) for r in by_tick[t]) for t in selected], w),
            unweighted_calls_with_fitting_hook_histogram=dict(sorted(Counter(
                sum(int(r['iteration_calls']) > 1 for r in by_tick[t]) for t in selected).items())))
    phases, unused = phase_profile(directory, ticks)
    phases.pop('models')
    phases.pop('impossible_zero_cost_bounds')
    placement = [r for r in rows(directory / 'benchmark/placement-ticks.csv') if r['phase'] == 'STABLE']
    paths = [inputs_path, helper_path, directory / 'benchmark/substeps.csv',
        directory / 'benchmark/substep-metadata.json', directory / 'benchmark/native-work.csv',
        directory / 'benchmark/native-work-end.json', ROOT / 'local/native-work-review-01/tool-checks.log']
    paths += [ROOT / 'tools/experimental' / name for name in ('native_work.py', 'test_native_work.py',
        'summarize_native_work.py', 'summarize_profile.py', 'plain_wire_benchmark.py', 'legacy_bench.py')]
    data = dict(schema=1, gameplay_jar_unchanged=True, native_library_unchanged=True,
        full_suite=False, graphical_acceptance=False, source_hashes_verified=len(inputs['sources']),
        diagnostic=run, observation=validation, patterns=pattern_report, sampled_tick_groups=groups,
        phases=phases, compilation_ms=int(placement[-1]['compile_ms']) - int(placement[0]['compile_ms']),
        evidence=[evidence(p) for p in paths],
        limitations=['Timing is diagnostic and includes observation overhead; this is not a new clean performance result.',
            'Callback sequences reveal the path through the pinned native algorithm. Factorization and individual C function durations are not directly measured.',
            'No client run or full acceptance was launched; retained gameplay artifacts are unchanged.'])
    write_new(ROOT / args.output, data)
    print(json.dumps(dict(attempt=args.attempt, mspt=run['summary']['mspt'], patterns=pattern_report,
        groups={k:{a:b['mean'] if isinstance(b,dict) and 'mean' in b else b for a,b in v.items()} for k,v in groups.items()})))


if __name__ == '__main__':
    main()
