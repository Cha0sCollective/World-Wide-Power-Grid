"""Inspect saved solver timing variation without launching another benchmark."""
import argparse
from collections import Counter, defaultdict
import json

from cpu_profile import write_new
from summarize_profile import rows, stats
from summarize_tree import ROOT, evidence, read, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--phase-report', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    report_path = ROOT / args.phase_report
    report = read(report_path)
    directory = ROOT / 'local/benchmarks' / report['diagnostic']['name']
    ticks_path = directory / 'benchmark/ticks.csv'
    steps_path = directory / 'benchmark/substeps.csv'
    for item in report['evidence']:
        assert sha(ROOT / item['path']) == item['sha256'], item['path']
    ticks = [r for r in rows(ticks_path) if r['phase'] == 'STABLE']
    sampled = {r['tick']: r for r in ticks if r['timing_sample'] == '1'}
    by_tick = defaultdict(list)
    for row in rows(steps_path):
        if row['tick'] in sampled:
            by_tick[row['tick']].append(row)
    assert set(by_tick) == set(sampled)
    forced = {t for t, records in by_tick.items() if records[0]['forced_edit_sample'] == 'true'}
    weights = {t: 1 if t in forced else (len(ticks) - len(forced)) / (len(sampled) - len(forced)) for t in sampled}
    for tick, records in by_tick.items():
        assert [int(r['step']) for r in records] == list(range(1, 17))
        assert all(r['native_solve_calls'] == '1' for r in records)
        assert sum(int(r['native_solve_ns']) for r in records) == int(sampled[tick]['native_solve_ns'])
    groups = {}
    for slow in (False, True):
        selected = [t for t, r in sampled.items() if (int(r['tick_ns']) > 50_000_000) == slow]
        w = [weights[t] for t in selected]
        scopes = {key: stats([int(sampled[t][key]) / 1e6 for t in selected], w)
                  for key in ('tick_ns', 'native_solve_ns', 'native_iteration_hooks_ns', 'native_residual_flush_ns')}
        scopes['native_excluding_java_callbacks_ns'] = stats([
            (int(sampled[t]['native_solve_ns']) - int(sampled[t]['native_iteration_hooks_ns'])
             - int(sampled[t]['native_residual_flush_ns'])) / 1e6 for t in selected], w)
        groups['over_50_ms' if slow else 'at_most_50_ms'] = dict(samples=len(selected),
            scopes_ms=scopes,
            step_ms=[stats([int(by_tick[t][i]['native_solve_ns']) / 1e6 for t in selected], w) for i in range(16)],
            unweighted_steps_over_0_3_ms_histogram=dict(sorted(Counter(
                sum(int(r['native_solve_ns']) > 300_000 for r in by_tick[t]) for t in selected).items())))
    fast = groups['at_most_50_ms']['scopes_ms']
    slow = groups['over_50_ms']['scopes_ms']
    tick_gap = slow['tick_ns']['mean'] - fast['tick_ns']['mean']
    native_gap = slow['native_solve_ns']['mean'] - fast['native_solve_ns']['mean']
    source_groups = {}
    for parity in (0, 1):
        # vary() runs after tickServer at tick % 200 == 0; changes apply from the next tick.
        selected = [t for t in sampled if ((int(t) - 1) // 200 & 1) == parity]
        source_groups[str(parity)] = dict(samples=len(selected), native_ms=stats(
            [int(sampled[t]['native_solve_ns']) / 1e6 for t in selected], [weights[t] for t in selected]))
    data = dict(schema=1, attempt=report['diagnostic']['name'], offline_analysis=True,
        runtime_changed=False, full_suite=False, graphical_acceptance=False,
        sampled_ticks=len(sampled), substeps_per_tick=16, groups=groups,
        tick_gap_ms=tick_gap, native_gap_ms=native_gap, native_share_of_gap=native_gap/tick_gap,
        source_edit_parity_groups=source_groups,
        evidence=[evidence(p) for p in (report_path, ticks_path, steps_path,
            ROOT / 'local/inputs/helper-src/org/cha0scollective/wwpg/benchmark/NativeGrid.java',
            ROOT / 'local/inputs/helper-src/org/cha0scollective/wwpg/benchmark/Session.java',
            ROOT / 'tools/experimental/summarize_profile.py', ROOT / 'tools/experimental/summarize_native_variation.py')],
        note='Weighted sparse diagnostic scopes, not a clean speedup. The 0.3 ms classification is descriptive; callback/iteration and factorization counts were not recorded. Equal source-edit parity means do not establish equal physical state.')
    write_new(ROOT / args.output, data)
    print(json.dumps(dict(samples=len(sampled), tick_gap_ms=tick_gap, native_gap_ms=native_gap,
        native_share_of_gap=native_gap/tick_gap, groups={k:dict(samples=v['samples'],
            scopes={a:b['mean'] for a,b in v['scopes_ms'].items()},
            costly_step_histogram=v['unweighted_steps_over_0_3_ms_histogram']) for k,v in groups.items()})))


if __name__ == '__main__':
    main()
