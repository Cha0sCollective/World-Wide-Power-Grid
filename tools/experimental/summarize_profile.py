"""Analyze preserved batched profiling attempts; never launch or modify a runtime."""
import argparse
from collections import Counter
import csv
import hashlib
import itertools
import json
import math
from pathlib import Path
import subprocess

from summarize import ROOT, evidence, read
from summarize_properties import benchmark

NATIVE_SHA = '1c8a4e7cbae743bd5a1553d4222d437b881367b0777987574cafb0249a6b033c'


def export_jfr(directory, java_home):
    source = directory / 'benchmark/substeps.jfr'
    destination = directory / 'benchmark/jfr-compact.json'
    metadata = directory / 'benchmark/jfr-compact-export.json'
    exporter = ROOT / 'tools/experimental/JfrCompact.java'
    source_sha = hashlib.sha256(source.read_bytes()).hexdigest()
    if destination.exists():
        proof = read(metadata)
        assert proof['source_sha256'] == source_sha
        assert proof['exporter_sha256'] == hashlib.sha256(exporter.read_bytes()).hexdigest()
        assert proof['export_sha256'] == hashlib.sha256(destination.read_bytes()).hexdigest()
        return destination
    command = [str(Path(java_home) / 'bin/java.exe'), '-Xmx512m', str(exporter), str(source), str(destination)]
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
    if result.returncode:
        raise RuntimeError(result.stderr.decode(errors='replace'))
    metadata.write_text(json.dumps(dict(command=command, source_sha256=source_sha,
                        exporter_sha256=hashlib.sha256(exporter.read_bytes()).hexdigest(),
                        export_sha256=hashlib.sha256(destination.read_bytes()).hexdigest()), indent=2) + '\n', newline='\n')
    return destination


def rows(path):
    with path.open(encoding='utf-8-sig', newline='') as stream:
        return list(csv.DictReader(stream))


def stats(values, weights=None):
    assert values
    weights = weights or [1] * len(values)
    total = sum(weights)
    ordered = sorted(zip(values, weights))
    def percentile(fraction):
        cumulative = 0
        for value, weight in ordered:
            cumulative += weight
            if cumulative >= total * fraction:
                return value
        return ordered[-1][0]
    return dict(mean=sum(v*w for v, w in zip(values, weights))/total,
                p50=percentile(.5), p95=percentile(.95), p99=percentile(.99), maximum=max(values))


def phase_profile(directory, stable):
    groups = {}
    for tick, group in itertools.groupby(rows(directory / 'benchmark/substeps.csv'), key=lambda row: row['tick']):
        trace = list(group)
        assert [int(row['step']) for row in trace] == list(range(1, 17)), tick
        assert all(row['loop_mask'] == '7' and row['rejected'] == row['overflow'] == '0'
                   and row['pg_accepted'] == row['pg_returned'] == 'true' and row['partial'] == 'false' for row in trace)
        assert len({row['forced_edit_sample'] for row in trace}) == 1
        groups[tick] = trace
    sampled = [row for row in stable if row['timing_sample'] == '1']
    assert set(groups) == {row['tick'] for row in sampled}
    forced = [groups[row['tick']][0]['forced_edit_sample'] == 'true' for row in sampled]
    forced_count = sum(forced)
    ordinary_count = len(sampled) - forced_count
    assert ordinary_count > 0
    # Every edit-triggered sample is retained, whereas ordinary ticks are sampled
    # once in 20. Use observed population counts, not equal weights for the strata.
    weights = [1 if edit else (len(stable)-forced_count)/ordinary_count for edit in forced]
    flat = []
    for row in sampled:
        ms = lambda key: int(row[key + '_ns']) / 1e6
        trace = groups[row['tick']]
        sub = lambda key: sum(int(step[key + '_ns']) for step in trace) / 1e6
        parts = dict(cee_prepare=ms('cee_prepare'), gameplay_commit=ms('gameplay_commit'),
                     pg_prepare=ms('pg_prepare'), terminal_prepare=ms('native_measurement_prepare'),
                     history_prepare=ms('history_prepare'), rhs=ms('pg_rhs'), native_solve=ms('native_solve'),
                     pg_pre_hooks=sub('pg_pre_hooks'), pg_post_hooks=sub('pg_post_hooks'),
                     power_hooks=sub('pg_micro_hooks'), bridge_after_solve=ms('history_commit'),
                     terminal_sample=ms('native_measurement_sample'), bridge_results=ms('result_bridge'),
                     terminal_finish=ms('native_measurement_finish'))
        parts['pg_other'] = ms('pg_solve') - sum(parts[key] for key in
                ('rhs', 'native_solve', 'pg_pre_hooks', 'pg_post_hooks', 'power_hooks'))
        parts['electrical_other'] = ms('electrical_schedule') - sum(value for key, value in parts.items()
                if key not in ('cee_prepare', 'gameplay_commit'))
        parts['other_tick'] = ms('tick') - ms('cee_prepare') - ms('gameplay_commit') - ms('electrical_schedule')
        assert all(value >= 0 for value in parts.values()), (row['tick'], parts)
        assert math.isclose(sum(parts.values()), ms('tick'), abs_tol=1e-6)
        flat.append(parts)
    costs = {key: stats([item[key] for item in flat], weights) for key in flat[0]}
    tick_times = [int(row['tick_ns']) / 1e6 for row in sampled]
    strata = {}
    for is_edit, name in [(False, 'ordinary'), (True, 'forced_edit')]:
        indices = [index for index, edit in enumerate(forced) if edit == is_edit]
        if indices:
            strata[name] = dict(samples=len(indices), tick_ms=stats([tick_times[i] for i in indices]),
                                 exclusive_mean_ms={key: sum(flat[i][key] for i in indices)/len(indices) for key in flat[0]})
    post = [item['power_hooks'] + item['bridge_after_solve'] + item['terminal_sample'] for item in flat]
    models = []
    elimination_bounds = {}
    for scope, budgets in [('power_hooks_only', [item['power_hooks'] for item in flat]),
                           ('postsolve_group', post), ('native_solver_only', [item['native_solve'] for item in flat])]:
        elimination_bounds[scope] = stats([tick-budget for tick,budget in zip(tick_times,budgets)], weights)
        for fraction in ([.5, .75, 1] if scope == 'postsolve_group' else [1]):
            for workers in (2, 4):
                for overhead in (0, 1, 2):
                    projected = [tick - budget*fraction*(1-1/workers) + overhead for tick, budget in zip(tick_times, budgets)]
                    models.append(dict(scope=scope, eligible_fraction=fraction, workers=workers,
                                       extra_serial_ms_per_tick=overhead, projected_sampled_ms=stats(projected, weights),
                                       estimated_mean_saving_ms=stats([t-p for t,p in zip(tick_times,projected)], weights)['mean']))
    return dict(complete_sixteen_step_traces=True, sampled_ticks=len(sampled), forced_edit_samples=forced_count,
                ordinary_samples=ordinary_count, weighting='All forced edit samples weight 1; ordinary samples share remaining stable population equally.',
                weighted_sampled_tick_ms=stats(tick_times, weights), unweighted_sampled_tick_ms=stats(tick_times),
                strata=strata,
                exclusive_cost_ms=costs, inclusive_phase_ms={key: stats([int(row[key])/1e6 for row in sampled], weights)
                    for key in sampled[0] if key.endswith('_ns') and key not in ('wall_ns', 'tick_ns')},
                postsolve_group_ms=stats(post, weights), models=models, impossible_zero_cost_bounds=elimination_bounds,
                caveat='Whole timed scopes include serial snapshots, guards and callbacks. Models are bounds/sensitivity estimates, not measured worker speedups. Sampled tails have limited resolution.'), flat


def category(frames):
    # Select a known enclosing scope, not the leaf operation (for example HashMap.get).
    rules = [('power_hooks', ('PlainWireBatch.', 'wwpg$accumulateResistivePower', 'wwpg$selectWireBatch')),
             ('terminal_prepare', ('NativeApiReadings.prepare', 'NativeApiReadings.snapshot')),
             ('terminal_sample', ('NativeApiReadings.after',)), ('terminal_finish', ('NativeApiReadings.finish',)),
             ('bridge_after_solve', ('WorldBridge.afterSolve',)), ('history_prepare', ('WorldBridge.beforeSolve',)),
             ('bridge_results', ('WorldBridge.finishSolving',)), ('gameplay_commit', ('WorldBridge.commit',)),
             ('cee_prepare', ('WorldBridge.prepare',)), ('rhs', ('ElectricalNetwork.computeRHS',)),
             ('native_solver', ('NativeMNA.singleTick',)), ('electrical_other', ('ServerElectricalSchedule.preTick',)),
             ('benchmark_observer', ('wwpg.benchmark.Session.', 'wwpg.benchmark.Substeps.write', 'wwpg.benchmark.FixedPlacement.')),
             ('wire_temperature', ('BaseWireEntity.temperatureUpdate',)),
             ('wire_contact', ('EntityWireInteraction.wireTouching',)),
             ('wire_entity_other', ('WireEntity.tick',)),
             ('block_entities', ('Level.tickBlockEntities',)),
             ('chunk_tick', ('ServerChunkCache.tick', 'ServerLevel.tickChunk'))]
    for name, needles in rules:
        if any(needle in frame for needle in needles for frame in frames):
            return name
    return 'other'


def ranked(counter, limit):
    return sorted(counter.items(), key=lambda item: (-item[1], item[0]))[:limit]


def jfr_profile(path):
    events = read(path)['events']
    types = Counter(event['type'] for event in events)
    result = dict(export=evidence(path), event_counts=dict(types), cpu={})
    for event_type in ('jdk.ExecutionSample', 'jdk.NativeMethodSample'):
        selected = [event for event in events if event['type'] == event_type]
        by_thread = Counter(row['thread'] or '(unknown)' for row in selected)
        server = [row for row in selected if row['thread'] == 'Server thread']
        leaf, inclusive, scopes = Counter(), Counter(), Counter()
        scope_frames = {}
        truncated = 0
        for row in server:
            frames = row['frames']
            truncated += row['truncated']
            if frames:
                leaf[frames[0]] += 1
                inclusive.update(set(frames))
            scopes[category(frames)] += 1
            scope_frames.setdefault(category(frames), Counter()).update(set(frames))
        result['cpu'][event_type] = dict(total=len(selected), by_thread=dict(by_thread), server_samples=len(server),
                                        truncated_server_stacks=truncated, server_scopes=dict(scopes),
                                        server_scope_inclusive_top={name: ranked(counter, 45) for name,counter in scope_frames.items()},
                                        server_leaf_top=ranked(leaf, 30), server_inclusive_top=ranked(inclusive, 40))
    allocated = sorted((event for event in events if event['type'] == 'jdk.ObjectAllocationSample'
                        and event['thread'] == 'Server thread'), key=lambda row: row['start'])
    # Recording starts after warmup. The first allocation sample can carry a
    # weight from before recording; retain it separately, never attribute that
    # weight to an allocation site in the measured window.
    boundary = allocated[0] if allocated else None
    allocation_types, allocation_scopes = Counter(), Counter()
    for row in allocated[1:]:
        allocation_types[row['object_class']] += row['weight']
        allocation_scopes[category(row['frames'])] += row['weight']
    result['allocation'] = dict(estimated_server_bytes=sum(allocation_types.values()),
                                retained_samples=max(0,len(allocated)-1), excluded_initial_sample=boundary,
                                classes=ranked(allocation_types, 30), scopes=dict(allocation_scopes))
    result['gc_pause_ms'] = sum(row['duration_ns']/1e6 for row in events if row['type'] == 'jdk.GCPhasePause')
    result['limitations'] = ['Execution samples and native-method samples use different periods (10 and 20 ms); never combine their raw percentages.',
                            'Native-method samples identify JNI entry points, not native call stacks or solver internals.',
                            'Allocation weights estimate bytes; exact per-tick allocation comes from the thread counters.',
                            'The initial server allocation sample is excluded from site attribution and retained separately because it may include pre-recording allocation.',
                            'JFR covers the stable phase including benchmark observers outside tickServer timing.']
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='docs/experimental/batched-profile-results.json')
    parser.add_argument('--attempts', nargs=3, default=['profile-batched-clean-01', 'profile-batched-timers-01', 'profile-batched-jfr-01'])
    args = parser.parse_args()
    manifest_path = ROOT / 'local/frozen/prototype-02c/inputs.json'
    frozen = read(manifest_path)
    for item in frozen['artifacts'].values():
        assert hashlib.sha256(Path(item['path']).read_bytes()).hexdigest() == item['sha256']
    for item in frozen['sources']:
        assert hashlib.sha256((ROOT / item['path']).read_bytes()).hexdigest() == item['sha256']
    output = dict(schema=1, base_commit='011fd2de8101a0844bab887d0951778956b095fd',
                  branch='dev/experimental-plain-wire-batching', runtime_changed=False, full_suite=False,
                  graphical_acceptance=False, frozen_inputs=evidence(manifest_path), artifacts=frozen['artifacts'],
                  source_inventory_verified=True, preserved_property_results=evidence(ROOT / 'docs/experimental/property-wire-results.json'),
                  threading_source_audit=evidence(ROOT / 'local/profiling/batched-source-audit.json'),
                  focused_tool_checks=evidence(ROOT / 'local/profiling/tool-checks.json'),
                  benchmarks=[])
    assert len(set(args.attempts)) == 3
    for name in args.attempts:
        assert Path(name).name == name and name.startswith('profile-batched-')
        path = ROOT / 'local/benchmarks' / name / 'experiment.json'
        protocol = read(path)
        entry = benchmark(path)
        assert protocol['batch'] and protocol['property_batch'] and not protocol['verify_batch']
        assert entry['validation']['passed'] and entry['original_reference_unchanged']
        assert entry['runtime_sha256'] == frozen['artifacts']['runtime']['sha256']
        assert entry['loaded_native_resource']['sha256'] == NATIVE_SHA
        directory = path.parent
        entry['substep_jfr'] = protocol.get('substep_jfr', False)
        stable = [row for row in rows(directory / 'benchmark/ticks.csv') if row['phase'] == 'STABLE']
        placement = [row for row in rows(directory / 'benchmark/placement-ticks.csv') if row['phase'] == 'STABLE']
        entry['thread_counters'] = dict(ticks=len(placement), cpu_ms=stats([int(row['cpu_tick_ns'])/1e6 for row in placement]),
                                        allocated_bytes=stats([int(row['allocated_bytes']) for row in placement]),
                                        compilation_ms=int(placement[-1]['compile_ms'])-int(placement[0]['compile_ms']),
                                        evidence=evidence(directory / 'benchmark/placement-ticks.csv'))
        if entry['detailed']:
            entry['phase_profile'], _ = phase_profile(directory, stable)
        if entry['substep_jfr']:
            recording = directory / 'benchmark/substeps.jfr'
            assert hashlib.sha256(recording.read_bytes()).hexdigest() == protocol['recording']['sha256']
            entry['recording'] = evidence(recording)
            entry['jfr'] = jfr_profile(export_jfr(directory, frozen['java_home']))
            entry['jfr']['export_provenance'] = evidence(directory / 'benchmark/jfr-compact-export.json')
            retention = directory / 'benchmark/verbose-export-retention.json'
            if retention.exists():
                entry['jfr']['initial_export_attempt'] = evidence(retention)
        output['benchmarks'].append(entry)
    assert len(output['benchmarks']) == 3, 'Expected one clean, one timer-only and one timer/JFR attempt'
    assert sum(not row['detailed'] for row in output['benchmarks']) == 1
    assert sum(row['substep_jfr'] for row in output['benchmarks']) == 1
    clean = next(row for row in output['benchmarks'] if not row['detailed'])
    for entry in output['benchmarks']:
        if entry['detailed']:
            for model in entry['phase_profile']['models']:
                model['cross_run_clean_mean_projection_ms'] = clean['mspt']['mean'] - model['estimated_mean_saving_ms']
    destination = ROOT / args.output
    destination.write_text(json.dumps(output, indent=2) + '\n', newline='\n')
    print(json.dumps(dict(report=str(destination), runs=len(output['benchmarks']), runtime_changed=False)))


if __name__ == '__main__':
    main()
