"""Audit a preparation-section diagnostic and its same-helper disabled control."""
import argparse
from collections import defaultdict
import json
from pathlib import Path

from cpu_profile import write_new
from preparation_sections import SECTIONS, validate
from summarize_current_profile import signature
from summarize_goal_runs import capture
from summarize_profile import rows, stats
from summarize_tree import ROOT, evidence, read, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--diagnostic', required=True)
    parser.add_argument('--control', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--helper-inputs', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    output = ROOT / args.output
    assert not output.exists()
    frozen_path, helper_path = ROOT / args.inputs, ROOT / args.helper_inputs
    frozen, helper = read(frozen_path), read(helper_path)
    for manifest in (frozen, helper):
        assert all(sha(ROOT / item['path']) == item['sha256'] for item in manifest['sources'])
    assert all(sha(Path(item['path'])) == item['sha256'] for item in frozen['artifacts'].values())
    assert sha(Path(helper['helper']['path'])) == helper['helper']['sha256']
    assert sha(Path(helper['instrumentation_checks']['path'])) == helper['instrumentation_checks']['sha256']
    checks = read(Path(helper['instrumentation_checks']['path']))
    assert checks['passed'] and all(proof['originalInstructionsPreserved'] for proof in checks['proofs'])
    runs = []
    for name, enabled in ((args.diagnostic, True), (args.control, False)):
        assert Path(name).name == name
        run = capture(name)
        protocol = run['protocol']
        assert protocol['preparation_sections'] == enabled
        assert not any(protocol[key] for key in ('detailed', 'substep_jfr', 'cpu_jfr'))
        assert protocol['runtime']['sha256'] == frozen['artifacts']['runtime']['sha256']
        assert protocol['helper']['sha256'] == helper['helper']['sha256']
        directory = ROOT / 'local/benchmarks' / name
        assert validate(directory, enabled) == protocol['preparation_observation']
        placed = [row for row in rows(directory / 'benchmark/placement-ticks.csv') if row['phase'] == 'STABLE']
        run['thread_counters'] = dict(cpu_ms=stats([int(row['cpu_tick_ns'])/1e6 for row in placed]),
            allocated_mib=stats([int(row['allocated_bytes'])/2**20 for row in placed]),
            compilation_ms=int(placed[-1]['compile_ms']) - int(placed[0]['compile_ms']))
        assert read(directory / 'runner-exit.json')['exit_code'] == 0
        runs.append(run)
    assert signature(runs[0]['protocol']) == signature(runs[1]['protocol'])
    directory = ROOT / 'local/benchmarks' / args.diagnostic
    observation = [row for row in rows(directory / 'benchmark/preparation-sections.csv') if row['phase'] == 'STABLE']
    all_ticks = [row for row in rows(directory / 'benchmark/ticks.csv') if row['phase'] == 'STABLE']
    tick_times = {int(row['tick']):int(row['tick_ns'])/1e6 for row in all_ticks}
    by_tick = defaultdict(list)
    for row in observation:
        by_tick[int(row['tick'])].append(row)
    forced_population = sum(tick % 200 == 1 for tick in tick_times)
    forced_samples = sum(tick % 200 == 1 for tick in by_tick)
    assert forced_population == forced_samples
    ordinary_samples = len(by_tick) - forced_samples
    assert ordinary_samples > 0
    weights, values = [], []
    for tick, world_rows in sorted(by_tick.items()):
        item = {key[:-3]:sum(int(row[key]) for row in world_rows)/1e6 for key in SECTIONS}
        item['other_prepare'] = sum(int(row['prepare_ns']) - int(row['stable_ns']) for row in world_rows)/1e6
        item['prepare_total'] = sum(int(row['prepare_ns']) for row in world_rows)/1e6
        item['stable_total'] = sum(int(row['stable_ns']) for row in world_rows)/1e6
        item['tick'] = tick_times[tick]
        item['other_tick'] = item['tick'] - item['prepare_total']
        assert all(value >= 0 for value in item.values())
        values.append((tick, item))
        weights.append(1 if tick % 200 == 1 else (len(all_ticks)-forced_population)/ordinary_samples)
    weighted = {key:stats([item[key] for _, item in values], weights) for key in values[0][1]}
    strata = {}
    for forced in (False, True):
        selected = [item for tick, item in values if (tick % 200 == 1) == forced]
        strata['forced_edit' if forced else 'ordinary'] = dict(samples=len(selected),
            ms={key:stats([item[key] for item in selected]) for key in selected[0]})
    tick_groups = {}
    for slow in (False, True):
        indices = [i for i, (_, item) in enumerate(values) if (item['tick'] > 50) == slow]
        assert indices
        tick_groups['over_50_ms' if slow else 'at_most_50_ms'] = dict(samples=len(indices),
            ms={key:stats([values[i][1][key] for i in indices], [weights[i] for i in indices]) for key in values[0][1]})
    proof_paths = [frozen_path, helper_path, Path(helper['instrumentation_checks']['path']),
                   directory / 'benchmark/preparation-sections.csv', directory / 'benchmark/preparation-sections-end.json',
                   directory / 'benchmark/preparation-install-prepare.json', directory / 'benchmark/preparation-install-stable.json']
    proof_paths += [ROOT / 'tools/experimental' / name for name in ('summarize_preparation_sections.py','preparation_sections.py',
                    'test_preparation_sections.py','plain_wire_benchmark.py','legacy_bench.py')]
    data = dict(schema=1, gameplay_jar_unchanged=True, helper_changed=True, full_suite=False, graphical_acceptance=False,
        source_hashes_verified=len(frozen['sources']), runs=runs,
        sample=dict(ticks=len(by_tick), forced_edit_samples=forced_samples, ordinary_samples=ordinary_samples,
                    weighting='All source-edit ticks have weight one. Other samples share the remaining steady tick population.'),
        weighted_ms=weighted, strata=strata, sampled_tick_groups=tick_groups, instrumentation_checks=checks,
        evidence=[evidence(path) for path in proof_paths],
        limitations=['Section timings are diagnostic scopes, including observer effects; not removable-cost estimates.',
                    'One sequential on/off pair cannot isolate timing overhead from host and JIT variation.',
                    'No client load or full acceptance was run. Prior named functionality evidence applies to the unchanged gameplay artifacts.'])
    write_new(output, data)
    print(json.dumps(dict(runs=[dict(name=r['name'],tps=r['summary']['tps'],mspt=r['summary']['mspt']) for r in runs],
                         weighted_mean_ms={key:value['mean'] for key,value in weighted.items()},sample=data['sample'])))


if __name__ == '__main__':
    main()
