"""Finite copied-save experiments. No release or full-acceptance commands."""
from pathlib import Path
import argparse
import csv
import json
import os
import re
import shlex
import shutil
import subprocess
import time
from types import SimpleNamespace

import legacy_bench as bench
from host_metrics import HostWatch

ROOT = Path(__file__).resolve().parents[2]
INPUTS = ROOT / 'local/inputs'
RELEASE_SHA = '6c1060b17f14df765188a60da154b723705947503a813508a53f62013e625053'
HELPER_SHA = 'cb67f3bbe08568992e1664558a3dc503c876be8cad5badf7872efbd763e3224e'
CEE_SHA = '2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f'


def inventory(directory):
    return [dict(path=p.relative_to(directory).as_posix(), sha256=bench.sha(p))
            for p in sorted(directory.rglob('*')) if p.is_file()
            and p.name not in ('session.lock', 'level.dat_old')]


def prepare(args):
    reference_root = Path(args.reference_root).resolve()
    profile = reference_root / 'local/benchmarks/release-baselines-20261014/profile-231'
    manifest = bench.read(profile / 'profile.json')
    original = reference_root / 'local/benchmarks/fix-10k-20261012/extracted-10000-dev2/WWPG Benchmark - MIXED - 10000 nodes'
    reference = INPUTS / 'reference'
    if not reference.exists():
        evidence = bench.restore_reference(original, reference)
        bench.write(INPUTS / 'reference-copy.json', evidence)
    saved = bench.read(INPUTS / 'reference-copy.json')
    for item in saved['files']:
        assert bench.sha(reference / item['path']) == item['sha256']
        assert bench.sha(original / item['path']) == item['sha256']
    assert bench.sha(INPUTS / 'wwpg-0.2.7.jar') == RELEASE_SHA
    helper = INPUTS / 'wwpg-benchmark-0.1.0.jar'
    assert bench.sha(helper) == HELPER_SHA
    helper_overlay = None
    if getattr(args, 'helper_inputs', None):
        helper_manifest = Path(args.helper_inputs).resolve()
        assert helper_manifest.is_relative_to(ROOT / 'local/frozen')
        helper_overlay = bench.read(helper_manifest)
        helper = Path(helper_overlay['helper']['path'])
        assert helper.is_relative_to(ROOT / 'local/frozen') and bench.sha(helper) == helper_overlay['helper']['sha256']
        for item in helper_overlay['sources']:
            assert bench.sha(ROOT / item['path']) == item['sha256']
    cpu_mask = getattr(args, 'cpu_mask', None)
    server_mask = getattr(args, 'server_cpu_mask', None)
    if getattr(args, 'preparation_sections', False) and not (helper_overlay and helper_overlay.get('preparation_sections_supported')):
        raise ValueError('Preparation sections require the explicit diagnostic helper')
    if getattr(args, 'native_work', False) and not (helper_overlay and helper_overlay.get('native_work_supported')):
        raise ValueError('Native work observation requires its explicit diagnostic helper')
    if cpu_mask:
        if not helper_overlay or not helper_overlay.get('process_affinity_supported'):
            raise ValueError('Whole-process pinning requires the explicit affinity-aware frozen helper')
        if int(cpu_mask, 16) not in (0xfff, 0xfff000):
            raise ValueError('Select one of the two observed whole L3 groups')
    if server_mask:
        if not cpu_mask or not helper_overlay.get('server_subset_supported'):
            raise ValueError('Server CPU subset requires a process group and a supporting frozen helper')
        if int(server_mask,16) <= 0 or int(server_mask,16) & ~int(cpu_mask,16):
            raise ValueError('Server CPU subset must remain inside the selected process group')
    runtime = Path(args.runtime).resolve() if args.runtime else INPUTS / 'wwpg-0.2.7.jar'
    assert runtime.is_relative_to(ROOT) and runtime.is_file()
    directory = bench.safe_directory(ROOT / 'local/benchmarks' / args.name)
    if directory.exists():
        raise ValueError('Preserve prior attempts; choose a new name')
    # Existing loader libraries are read-only, verified individually before reuse.
    inputs = []
    for item in manifest['libraries']:
        path = profile / item['file']
        assert bench.sha(path) == item['sha256'], path
        inputs.append(dict(path=str(path), sha256=item['sha256']))
    directory.mkdir(parents=True)
    (directory / 'mods').mkdir()
    for mod in manifest['mods']:
        if mod['role'] in ('wwpg', 'cee'):
            continue
        source = profile / mod['file']
        assert bench.sha(source) == mod['installed_sha256']
        shutil.copy2(source, directory / mod['file'])
    cee = reference_root / 'local/cee-compatibility-20261101/electroenergetics-1.21.1-1.1.4.jar'
    assert bench.sha(cee) == CEE_SHA, 'Frozen CEE 1.1.4 input changed'
    for source in (cee, runtime, helper):
        shutil.copy2(source, directory / 'mods' / source.name)
    for path in sorted((directory / 'mods').glob('*.jar')):
        inputs.append(dict(path=str(path), sha256=bench.sha(path)))
    runner_sources = []
    source_copy = directory / 'runner-sources'
    source_copy.mkdir()
    for name in ('plain_wire_benchmark.py','restoration_benchmark.py','legacy_bench.py','process_metrics.py',
                 'host_metrics.py','cpu_affinity.py','cpu_profile.py','preparation_sections.py','native_work.py','world_save_evidence.py'):
        source = ROOT / 'tools/experimental' / name
        target = source_copy / name
        shutil.copy2(source,target)
        runner_sources.append(dict(path=str(target),sha256=bench.sha(target)))
    launch_source = profile / 'libraries/net/neoforged/neoforge/21.1.231/win_args.txt'
    libraries = (profile / 'libraries').as_posix()
    tokens = []
    for token in shlex.split(launch_source.read_text(), comments=True, posix=True):
        token = token.replace('libraries/', libraries + '/')
        if token.startswith('-DlibraryDirectory='):
            token = '-DlibraryDirectory=' + libraries
        tokens.append('"' + token.replace('"', '\\"') + '"')
    flags = '-Xms512M\n-Xmx8G\n-Dwwpg.experimental.plainWireBatch=' + str(args.batch).lower() + '\n'
    if getattr(args, 'cpu_jfr_non_safepoints', False):
        flags += '-XX:+UnlockDiagnosticVMOptions\n-XX:+DebugNonSafepoints\n'
    flags += '-Dwwpg.experimental.plainWireBatch.verify=' + str(args.verify_batch).lower() + '\n'
    flags += '-Dwwpg.experimental.plainWireBatch.properties=' + str(args.property_batch).lower() + '\n'
    flags += '-Dwwpg.experimental.wireTreeBatch=' + str(getattr(args, 'tree_batch', False)).lower() + '\n'
    flags += '-Dwwpg.experimental.wireTreeBudgetMillis=' + str(getattr(args, 'tree_budget_ms', 0)) + '\n'
    flags += '-Dwwpg.experimental.wireTreeMaxParts=' + str(getattr(args, 'tree_max_parts', 32)) + '\n'
    flags += '-Dwwpg.experimental.wireTreeRetainCoverage=' + str(getattr(args, 'tree_retain_coverage', False)).lower() + '\n'
    flags += '-Dwwpg.experimental.wireTreePrioritizeUnresolved=' + str(getattr(args, 'tree_prioritize_unresolved', False)).lower() + '\n'
    flags += '-Dwwpg.experimental.chunkTaskBudgetMillis=' + str(getattr(args, 'chunk_task_budget_ms', 0)) + '\n'
    flags += '-Dwwpg.experimental.ceeStateGeometry=' + str(getattr(args, 'cee_state_geometry', False)).lower() + '\n'
    flags += '-Dwwpg.experimental.scopedRestoration=' + str(getattr(args, 'scoped_restoration', False)).lower() + '\n'
    flags += '-Dwwpg.experimental.indexedWaveform=' + str(getattr(args, 'indexed_waveform', False)).lower() + '\n'
    flags += '-Dwwpg.experimental.terminalBookkeeping=' + str(getattr(args, 'terminal_bookkeeping', False)).lower() + '\n'
    flags += '-Dwwpg.experimental.terminalKeyLookup=' + str(getattr(args, 'terminal_key_lookup', False)).lower() + '\n'
    if cpu_mask:
        flags += '-Dwwpg.benchmark.cpuMask=0x' + cpu_mask + '\n'
    if server_mask:
        flags += '-Dwwpg.benchmark.serverCpuMask=0x' + server_mask + '\n'
    (directory / 'launch.args').write_text(flags + '\n'.join(tokens) + '\nnogui\n', newline='\n')
    java = Path(manifest['java']['path'])
    assert java.is_file()
    sources = [dict(path=p.relative_to(ROOT).as_posix(), sha256=bench.sha(p))
               for folder in ('helper-src', 'helper-resources') for p in sorted((INPUTS / folder).rglob('*')) if p.is_file()]
    if helper_overlay:
        sources += helper_overlay['sources']
    bench.write(directory / 'launch.json', dict(schema=1, neoforge='21.1.231',
        runtime=dict(path=str(directory / 'mods' / runtime.name), sha256=bench.sha(runtime), kind='local-candidate', source=str(runtime)),
        helper=dict(path=str(directory / 'mods' / helper.name), sha256=bench.sha(helper)), sources=sources,
        profile_inputs=inputs, profile=dict(name='EXPERIMENTAL_CLEAN', heap_gib=8, cee='1.1.4', pg='0.6.2'),
        command=[str(java), '@' + str(directory / 'launch.args')], environment={}, directory=str(directory),
        cpu_affinity_mask=cpu_mask, server_affinity_mask=server_mask, runner_sources=runner_sources,
        debug_non_safepoints=getattr(args, 'cpu_jfr_non_safepoints', False)))
    bench.write(directory / 'WWPG_BENCHMARK_OWNED.json', dict(root=str(directory), purpose='Isolated plain-wire experiment'))
    (directory / 'eula.txt').write_text('eula=true\n')
    (directory / 'server.properties').write_text('level-name=world\nlevel-type=minecraft:flat\ngenerate-structures=false\nlevel-seed=2101248\ngamemode=creative\ndifficulty=peaceful\nserver-ip=127.0.0.1\nserver-port=25889\nonline-mode=true\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-players=1\nmax-tick-time=60000\n')
    return directory, reference, original


def validate(directory, detailed, batch=False, verify=False, properties=False):
    report = bench.read(directory / 'benchmark/result.json')
    audit = report['audit']
    assert report['passed'] and report['cee_solves'] == report['rejected_solves'] == 0
    assert (audit['electrical_nodes'], audit['edges'], audit['solver_networks'], audit['optimized_matrix_nodes']) == (14650, 20637, 1, 14925)
    assert audit['actual_backends'] == ['NATIVE']
    assert bench.read(directory / 'shutdown-save.json')['all_dimensions_saved']
    assert bench.read(directory / 'benchmark/placement-restored.json')['original_thread_affinity_restored']
    expected_mask = bench.read(directory / 'launch.json').get('cpu_affinity_mask') or 'fff000'
    expected_thread_mask = bench.read(directory / 'launch.json').get('server_affinity_mask') or expected_mask
    assert bench.read(directory / 'benchmark/placement-fixed.json')['mask'] == expected_thread_mask
    if bench.read(directory / 'launch.json').get('cpu_affinity_mask'):
        assert bench.read(directory / 'process-affinity.json')['readback_mask'] == expected_mask
        assert all(json.loads(line)['cpu_affinity_mask'] == expected_mask for line in (directory / 'process-metrics.jsonl').read_text().splitlines())
    assert len(bench.read(directory / 'benchmark/pre-solve-storage.json')) == 1050
    assert len(bench.read(directory / 'world/WWPG_BENCHMARK.json')['chunks']) == 953
    with (directory / 'benchmark/ticks.csv').open() as stream:
        rows = [r for r in csv.DictReader(stream) if r['phase'] == 'STABLE']
    assert rows and all(int(r['pg_solve_calls']) == 16 and int(r['matrix_assemblies']) == 0 for r in rows)
    assert bench.read(directory / 'benchmark/graph-start.json') == bench.read(directory / 'benchmark/graph-end.json')
    if not detailed:
        assert all(r['timing_sample'] == '0' for r in rows)
    else:
        with (directory / 'benchmark/substeps.csv').open() as stream:
            records = list(csv.DictReader(stream))
        assert records and len(records) % 16 == 0
        assert all(r['pg_accepted'] == r['pg_returned'] == 'true' and r['partial'] == 'false' for r in records)
    validation = dict(passed=True, full_suite=False, capacity_certified=False, stable_ticks=len(rows),
                actual_nodes=14650, edges=20637, chunks=953, storage_devices=1050,
                sixteen_substeps=True, topology_unchanged=True, normal_shutdown=True)
    if batch:
        log = (directory / 'process.log').read_text(encoding='utf-8', errors='replace')
        matches = re.findall(r'WWPG experimental plain-wire batch: verify=(true|false), plans=(\d+), unsupported=(\d+), steps=(\d+), contributions=(\d+), voltageReads=(\d+), scalarFallbacks=(\d+), largestPlainBatch=(\d+)', log)
        assert len(matches) == 1, 'Missing or repeated batch execution proof'
        values = matches[0]
        assert (values[0] == 'true') == verify
        stats = dict(zip(('plans', 'unsupported', 'steps', 'contributions', 'voltage_reads', 'scalar_fallbacks', 'largest_plain_batch'), map(int, values[1:])))
        assert stats['largest_plain_batch'] == 10368
        assert stats['contributions'] >= len(rows) * 16 * 10368, 'Batch did not process enough of the measured yard'
        property_matches = re.findall(r', properties=(true|false), propertyContributions=(\d+), propertyScalarFallbacks=(\d+), largestPropertyBatch=(\d+)', log)
        assert len(property_matches) <= 1
        if properties:
            assert property_matches, 'Runtime did not expose property-batch execution proof'
        if property_matches:
            enabled, fast, fallback, largest = property_matches[0]
            assert (enabled == 'true') == properties
            fast, fallback, largest = int(fast), int(fallback), int(largest)
            stats.update(property_contributions=fast, property_scalar_fallbacks=fallback, largest_property_batch=largest)
            if properties:
                assert largest == 9443 and fast > 0
                assert fast + fallback >= len(rows) * 16 * 9443, 'Property batch did not cover the measured yard'
                assert stats['contributions'] - fast >= len(rows) * 16 * 10368, 'Plain-wire coverage regressed'
            else:
                assert fast == fallback == largest == 0, 'Disabled property batching executed'
        assert 'differs from scalar PG power' not in log
        validation['batch_execution'] = stats
        validation['bitwise_verification'] = verify
    return validation


def validate_task_window(log, budget):
    lines = re.findall(r'WWPG experimental chunk-task window: (.+)', log)
    if not budget:
        assert not lines
        return None
    assert len(lines) == 1
    stats = {key: int(value) for key, value in re.findall(r'(\w+)=(\d+)', lines[0])}
    assert stats['budgetMillis'] == budget and stats['windows'] > 0 and stats['completed'] > 0
    assert 0 < stats['maxCompleted'] <= stats['maxTasks'] == 512
    assert stats['probes'] > 0 and stats['maxWindowNanos'] >= stats['maxPollNanos']
    return stats


def validate_cee_geometry(log, enabled):
    lines = re.findall(r'WWPG experimental CEE state geometry: (.+)', log)
    if not enabled:
        assert not lines
        return None
    assert len(lines) == 1
    stats = {key: int(value) for key, value in re.findall(r'(\w+)=(\d+)', lines[0])}
    assert stats['hits'] > 0 and 0 < stats['builds'] == stats['states'] <= 2048
    assert stats['requests'] == stats['hits'] + stats['builds'] + stats['fallback']
    return stats


def validate_scoped_restoration(log, enabled, require_activity=False):
    lines = re.findall(r'WWPG experimental scoped restoration: (.+)', log)
    if not enabled:
        assert not lines, 'Disabled scoped restoration executed'
        return None
    assert len(lines) == 1, 'Missing or duplicate scope report'
    stats = {key: int(value) for key, value in re.findall(r'(\w+)=(-?\d+)', lines[0])}
    assert set(stats) == set(('snapshots', 'membershipChecks', 'savedParts', 'nodeBindings',
        'seedEndpoints', 'heldNetworks', 'fallbackWorlds', 'totalNanos', 'maxNanos',
        'topologyPasses', 'topologyNanos', 'maxTopologyNanos'))
    assert all(value >= 0 for value in stats.values())
    assert stats['totalNanos'] >= stats['maxNanos']
    assert stats['topologyNanos'] >= stats['maxTopologyNanos']
    if require_activity:
        assert all(stats[key] > 0 for key in ('snapshots', 'savedParts', 'nodeBindings', 'seedEndpoints', 'heldNetworks'))
    return stats


def validate_indexed_waveform(log, enabled):
    lines = re.findall(r'WWPG experimental indexed waveform: (.+)', log)
    if not enabled:
        assert not lines, 'Disabled indexed waveform executed'
        return None
    assert len(lines) == 1, 'Missing or duplicate indexed waveform report'
    stats = {key: int(value) for key, value in re.findall(r'(\w+)=(-?\d+)', lines[0])}
    assert set(stats) == {'passes', 'directVisits', 'middleVisits'}
    assert stats['passes'] > 0 and stats['directVisits'] > 0 and stats['middleVisits'] >= 0
    return stats


def validate_terminal_bookkeeping(log, enabled):
    lines = re.findall(r'WWPG experimental terminal bookkeeping: (.+)', log)
    if not enabled:
        assert not lines
        return None
    assert len(lines) == 1
    stats = {key: int(value) for key, value in re.findall(r'(\w+)=(\d+)', lines[0])}
    assert stats['maps'] >= 2 and stats['maps'] % 2 == 0 and stats['passes'] > 0
    assert stats['frames'] >= stats['largest'] > 0
    return stats


def validate_terminal_keys(log, enabled):
    lines = re.findall(r'WWPG experimental terminal key lookup: (.+)', log)
    if not enabled:
        assert not lines
        return None
    assert len(lines) == 1
    stats = {key: int(value) for key, value in re.findall(r'(\w+)=(\d+)', lines[0])}
    assert stats['passes'] > 0 and stats['frames'] >= stats['largest'] > 0
    return stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference-root', required=True)
    parser.add_argument('--name', required=True)
    parser.add_argument('--runtime')
    parser.add_argument('--detailed', action='store_true')
    parser.add_argument('--substep-jfr', action='store_true', help='Record stable-phase CPU/allocation samples; requires --detailed')
    parser.add_argument('--cpu-jfr', action='store_true', help='Start JFR during warmup with detailed timers disabled; diagnostic only')
    parser.add_argument('--cpu-jfr-non-safepoints', action='store_true', help='Add compiled-code debug locations from JVM startup; requires --cpu-jfr')
    parser.add_argument('--preparation-sections', action='store_true', help='Sparse whole-section CEE preparation diagnostic; requires its helper overlay')
    parser.add_argument('--native-work', action='store_true', help='Observe actual JNI inputs and callback counts; requires its helper and --detailed')
    parser.add_argument('--batch', action='store_true')
    parser.add_argument('--verify-batch', action='store_true')
    parser.add_argument('--property-batch', action='store_true')
    parser.add_argument('--tree-batch', action='store_true')
    parser.add_argument('--tree-budget-ms', type=int, default=0)
    parser.add_argument('--tree-max-parts', type=int, default=32)
    parser.add_argument('--tree-retain-coverage', action='store_true')
    parser.add_argument('--tree-prioritize-unresolved', action='store_true')
    parser.add_argument('--chunk-task-budget-ms', type=int, choices=range(21), default=0)
    parser.add_argument('--cee-state-geometry', action='store_true')
    parser.add_argument('--scoped-restoration', action='store_true')
    parser.add_argument('--indexed-waveform', action='store_true')
    parser.add_argument('--terminal-bookkeeping', action='store_true')
    parser.add_argument('--terminal-key-lookup', action='store_true')
    parser.add_argument('--helper-inputs', help='Frozen queue-aware loading helper manifest')
    parser.add_argument('--cpu-mask', choices=['fff','fff000'], help='Explicit whole-process logical CPU group, selected after a load observation')
    parser.add_argument('--server-cpu-mask', help='Explicit hexadecimal server-thread subset of the selected process group')
    args = parser.parse_args()
    if (args.property_batch or args.verify_batch) and not args.batch:
        parser.error('--property-batch and --verify-batch require --batch')
    if args.substep_jfr and not args.detailed:
        parser.error('--substep-jfr requires --detailed')
    if args.cpu_jfr and (args.detailed or args.substep_jfr):
        parser.error('--cpu-jfr requires detailed and substep diagnostics disabled')
    if args.cpu_jfr_non_safepoints and not args.cpu_jfr:
        parser.error('--cpu-jfr-non-safepoints requires --cpu-jfr')
    if args.preparation_sections and (args.detailed or args.cpu_jfr or args.substep_jfr):
        parser.error('--preparation-sections requires other timing and JFR diagnostics disabled')
    if args.native_work and (not args.detailed or args.cpu_jfr or args.substep_jfr or args.preparation_sections):
        parser.error('--native-work requires detailed timers with JFR and preparation sections disabled')
    if args.tree_budget_ms < 0 or args.tree_max_parts < 1:
        parser.error('Invalid restoration budget')
    if (args.tree_budget_ms or args.tree_retain_coverage or args.tree_prioritize_unresolved) and not args.tree_batch:
        parser.error('Restoration budget, coverage and priority require --tree-batch')
    if args.scoped_restoration and (not args.tree_batch or args.tree_budget_ms <= 0):
        parser.error('--scoped-restoration requires bounded wire-tree batching')
    directory, reference, original = prepare(args)
    before = inventory(original)
    reference_before = inventory(reference)
    protocol = dict(name=args.name, runtime=bench.read(directory / 'launch.json')['runtime'],
        scope='Focused 120-second warmup and 120-second stable control; not full capacity acceptance',
        copied_reference=True, sixteen_substeps=True, full_suite=False, batch=args.batch,
        verify_batch=args.verify_batch, property_batch=args.property_batch, detailed=args.detailed,
        tree_batch=args.tree_batch, tree_budget_ms=args.tree_budget_ms, tree_max_parts=args.tree_max_parts,
        tree_retain_coverage=args.tree_retain_coverage,
        tree_prioritize_unresolved=args.tree_prioritize_unresolved,
        chunk_task_budget_ms=args.chunk_task_budget_ms,
        cee_state_geometry=args.cee_state_geometry,
        scoped_restoration=args.scoped_restoration,
        indexed_waveform=args.indexed_waveform,
        terminal_bookkeeping=args.terminal_bookkeeping,
        terminal_key_lookup=args.terminal_key_lookup,
        cpu_affinity_mask=args.cpu_mask,
        server_affinity_mask=args.server_cpu_mask,
        helper=bench.read(directory / 'launch.json')['helper'],
        substep_jfr=args.substep_jfr, cpu_jfr=args.cpu_jfr,
        cpu_jfr_non_safepoints=args.cpu_jfr_non_safepoints,
        preparation_sections=args.preparation_sections,
        native_work=args.native_work,
        started_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()))
    bench.write(directory / 'experiment.json', protocol)
    print(json.dumps(dict(starting=str(directory), protocol=protocol)), flush=True)
    options = SimpleNamespace(directory=str(directory), runtime_sha256=protocol['runtime']['sha256'],
        tier=10000, backend='NATIVE', variant='MIXED', mode='control', warmup=120, sample=120,
        churn=0, cycles=1, restore=str(reference), detailed=args.detailed, timeout=1200,
        restoration_batch=True, rhs_counters=False, profile_every=20,
        substep_trace=args.detailed, substep_jfr=args.substep_jfr, cpu_jfr=args.cpu_jfr,
        cpu_jfr_non_safepoints=args.cpu_jfr_non_safepoints, preparation_sections=args.preparation_sections,
        native_work=args.native_work)
    try:
        with HostWatch(directory / 'host-metrics.jsonl'):
            bench.run(options)
        protocol['validation'] = validate(directory, args.detailed, args.batch, args.verify_batch, args.property_batch)
        protocol['task_window'] = validate_task_window((directory / 'process.log').read_text(errors='replace'), args.chunk_task_budget_ms)
        protocol['cee_geometry'] = validate_cee_geometry((directory / 'process.log').read_text(errors='replace'), args.cee_state_geometry)
        protocol['scope_execution'] = validate_scoped_restoration((directory / 'process.log').read_text(errors='replace'), args.scoped_restoration, True)
        protocol['waveform_execution'] = validate_indexed_waveform((directory / 'process.log').read_text(errors='replace'), args.indexed_waveform)
        if args.indexed_waveform:
            assert protocol['waveform_execution']['passes'] >= protocol['validation']['stable_ticks'] * 16
        protocol['terminal_execution'] = validate_terminal_bookkeeping((directory / 'process.log').read_text(errors='replace'), args.terminal_bookkeeping)
        protocol['terminal_keys'] = validate_terminal_keys((directory / 'process.log').read_text(errors='replace'), args.terminal_key_lookup)
        if args.tree_prioritize_unresolved:
            log = (directory / 'process.log').read_text(encoding='utf-8', errors='replace')
            priority = re.findall(r'prioritizeUnresolved=(true|false), prioritySelections=(\d+)', log)
            assert len(priority) == 1 and priority[0][0] == 'true' and int(priority[0][1]) > 0
            protocol['tree_priority_selections'] = int(priority[0][1])
        if args.substep_jfr:
            recording = directory / 'benchmark/substeps.jfr'
            log = (directory / 'process.log').read_text(encoding='utf-8', errors='replace')
            assert log.count('WWPG_SUBSTEP_JFR_STARTED: stable phase only') == 1
            assert log.count('WWPG_SUBSTEP_JFR_SAVED') == 1
            assert recording.is_file() and recording.stat().st_size > 0
            protocol['recording'] = dict(path=str(recording), sha256=bench.sha(recording), bytes=recording.stat().st_size)
        if args.cpu_jfr:
            from cpu_profile import validate_recording
            protocol['recording'] = validate_recording(directory)
        from preparation_sections import validate as validate_preparation
        protocol['preparation_observation'] = validate_preparation(directory, args.preparation_sections)
        from native_work import validate as validate_native_work
        protocol['native_work_observation'] = validate_native_work(directory, args.native_work)
        protocol['summary'] = bench.read(directory / 'benchmark/summary.json')
        print(json.dumps(dict(completed=True, summary=protocol['summary']['mspt'], tps=protocol['summary']['tps'])), flush=True)
    finally:
        protocol['original_reference_unchanged'] = inventory(original) == before
        protocol['isolated_reference_unchanged'] = inventory(reference) == reference_before
        protocol['finished_utc'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        bench.write(directory / 'experiment.json', protocol)
        assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']


if __name__ == '__main__':
    main()
