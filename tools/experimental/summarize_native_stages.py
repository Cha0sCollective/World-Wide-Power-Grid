"""Summarize nested C-stage timings from a completed, validated diagnostic."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path

from cpu_profile import write_new
from native_stages import ROOT_CHILDREN, STAGES, validate as validate_stages
from native_work import validate as validate_work
from summarize_goal_runs import capture
from summarize_profile import phase_profile, rows, stats
from summarize_tree import ROOT, evidence, read, sha


def measured(row):
    frequency = int(row['profile_frequency'])
    values = {stage: int(row['c_' + stage + '_qpc']) * 1000 / frequency for stage in STAGES}
    values['jni'] = int(row['native_ns']) / 1e6
    values['body_other'] = values['total'] - sum(values[k] for k in ROOT_CHILDREN)
    values['sparse_other_including_permutation'] = values['sparse_solve'] - values['factorization'] - values['triangular_solve']
    values['jni_outside_timed_body'] = values['jni'] - values['total']
    return values


def aggregate(records, weights):
    values = [measured(row) for row in records]
    return dict(sampled_calls=len(records), sampled_ticks=len({r['tick'] for r in records}),
        inclusive_and_derived_ms_per_call={k: stats([v[k] for v in values], weights) for k in values[0]},
        calls_per_invocation={k: stats([int(r['c_' + k + '_calls']) for r in records], weights) for k in STAGES},
        unweighted_count_histograms={k: dict(sorted(Counter(int(r['c_' + k + '_calls']) for r in records).items())) for k in STAGES})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--helper-inputs', required=True)
    parser.add_argument('--focused-checks', nargs='+', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    inputs_path, helper_path = (ROOT / v for v in (args.inputs, args.helper_inputs))
    inputs, helper = read(inputs_path), read(helper_path)
    assert inputs['native_profile_supported'] and not inputs['native_unchanged'] and inputs['gameplay_classes_unchanged']
    assert all(sha(ROOT / p['path']) == p['sha256'] for p in inputs['sources'] + helper['sources'])
    assert all(sha(Path(p['path'])) == p['sha256'] for p in inputs['artifacts'].values())
    assert sha(Path(helper['helper']['path'])) == helper['helper']['sha256']
    checks_paths = [ROOT / p for p in args.focused_checks]
    checks = [read(p) for p in checks_paths]
    assert all(c['passed'] and c['artifacts_unchanged'] and c['backend'] == 'NATIVE' for c in checks)
    assert inputs['artifacts']['runtime']['sha256'] in {c['artifacts']['runtime']['sha256'] for c in checks}
    directory = ROOT / 'local/benchmarks' / args.attempt
    run = capture(args.attempt)
    assert run['protocol']['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
    assert run['protocol']['helper']['sha256'] == helper['helper']['sha256']
    assert run['protocol']['native_stages'] and read(directory / 'runner-exit.json')['exit_code'] == 0
    assert not list(directory.glob('**/*.jfr'))
    work, stages = validate_work(directory, True), validate_stages(directory, True)
    stable = [r for r in rows(directory / 'benchmark/ticks.csv') if r['phase'] == 'STABLE']
    sampled = {r['tick']: r for r in stable if r['timing_sample'] == '1'}
    observed = [r for r in rows(directory / 'benchmark/native-work.csv') if r['phase'] == 'STABLE']
    by_tick, patterns = defaultdict(list), defaultdict(list)
    for row in observed:
        by_tick[row['tick']].append(row)
        key = tuple(row[k] for k in ('jacobian_commands', 'hook_indices', 'hook_commands', 'residual_calls'))
        patterns[key].append(row)
    forced = {r['tick'] for r in rows(directory / 'benchmark/substeps.csv') if r['forced_edit_sample'] == 'true'} & set(sampled)
    weights = {t: 1 if t in forced else (len(stable) - len(forced)) / (len(sampled) - len(forced)) for t in sampled}
    pattern_report = []
    for key, records in sorted(patterns.items(), key=lambda pair: -len(pair[1])):
        commands, indices, hook_commands, residuals = key
        pattern_report.append(dict(initial_jacobian_commands=int(commands),
            hook_indices=[int(v) for v in indices.split('|')],
            hook_jacobian_commands=[int(v) for v in hook_commands.split('|')], residual_calls=int(residuals),
            **aggregate(records, [weights[r['tick']] for r in records])))
    tick_values = {t: {k: sum(measured(r)[k] for r in records) for k in measured(records[0])} for t, records in by_tick.items()}
    groups = {}
    for slow in (False, True):
        selected = [t for t, row in sampled.items() if (int(row['tick_ns']) > 50_000_000) == slow]
        if not selected:
            continue
        w = [weights[t] for t in selected]
        records = [r for t in selected for r in by_tick[t]]
        groups['over_50_ms' if slow else 'at_most_50_ms'] = dict(sampled_ticks=len(selected),
            tick_ms=stats([int(sampled[t]['tick_ns']) / 1e6 for t in selected], w),
            stage_ms_per_tick={k: stats([tick_values[t][k] for t in selected], w) for k in tick_values[selected[0]]},
            unweighted_solve_call_histogram=dict(sorted(Counter(sum(int(r['c_sparse_solve_calls']) for r in by_tick[t]) for t in selected).items())),
            per_call=aggregate(records, [weights[r['tick']] for r in records]))
    phases, unused = phase_profile(directory, stable)
    phases.pop('models')
    phases.pop('impossible_zero_cost_bounds')
    placement = [r for r in rows(directory / 'benchmark/placement-ticks.csv') if r['phase'] == 'STABLE']
    paths = [inputs_path, helper_path] + checks_paths
    paths += [directory / 'benchmark' / p for p in ('native-work.csv', 'native-work-end.json', 'substeps.csv', 'substep-metadata.json')]
    paths += [ROOT / 'tools/experimental' / p for p in ('native_stages.py', 'summarize_native_stages.py')]
    data = dict(schema=1, gameplay_class_bytes_unchanged=True, runtime_jar_changed=True,
        native_library_changed=True, diagnostic_only=True, full_suite=False, graphical_acceptance=False,
        source_hashes_verified=len(inputs['sources']), diagnostic=run, work_observation=work,
        stage_observation=stages, patterns=pattern_report, sampled_tick_groups=groups,
        all_calls=aggregate(observed, [weights[r['tick']] for r in observed]), phases=phases,
        compilation_ms=int(placement[-1]['compile_ms']) - int(placement[0]['compile_ms']),
        focused_fixture_executions=sum(c['required_tests_passed'] for c in checks),
        evidence=[evidence(p) for p in paths],
        timing_scope_notes=[
            'C timers run on every invocation; the helper copies counters on its sampled ticks. Observer overhead is included.',
            'Total contains the root scopes; sparse solve contains factorization, triangular solve and permutation validation.',
            'Permutation validation may also be inside factorization. Do not sum all inclusive stages.',
            'body_other subtracts disjoint root scopes; sparse_other_including_permutation subtracts only factorization and triangular solve.',
            'JNI includes profile setup and timing overhead outside the measured solver body. Tiny clock-boundary differences are retained.',
            'Weights retain every forced edit with weight one; ordinary samples share the remaining stable tick population.'],
        limitations=[
            'The compiler and native library differ from the retained runtime. These timings locate work; they are not evidence of a speedup.',
            'No new client load or full acceptance is established by this diagnostic.',
            'Focused fixtures and this copied-yard run do not establish all mod functionality.'])
    write_new(ROOT / args.output, data)
    print(json.dumps(dict(attempt=args.attempt, mspt=run['summary']['mspt'],
        patterns=[dict(calls=p['sampled_calls'], residual_calls=p['residual_calls'],
            mean_ms={k:v['mean'] for k,v in p['inclusive_and_derived_ms_per_call'].items()}) for p in pattern_report],
        groups={k:dict(samples=v['sampled_ticks'], tick_ms=v['tick_ms']['mean'],
            stage_ms={a:b['mean'] for a,b in v['stage_ms_per_tick'].items()}) for k,v in groups.items()})))


if __name__ == '__main__':
    main()
