"""Run one real client against a fresh copied benchmark save, never an acceptance suite."""
import argparse
import csv
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import time

import legacy_bench as bench
from cpu_affinity import affinity, observe_group, pin_owned
from host_metrics import HostWatch
from plain_wire_benchmark import ROOT, INPUTS, inventory, validate_task_window, validate_cee_geometry, validate_terminal_bookkeeping, validate_terminal_keys
from process_metrics import ChildMetrics


def validate(directory, inputs, audit_directory=None):
    audit_directory = directory if audit_directory is None else Path(audit_directory)
    (audit_directory / 'client-benchmark').mkdir(parents=True, exist_ok=True)
    result = bench.read(directory / 'client-benchmark/result.json')
    assert result['passed'], result
    assert bench.read(directory / 'client-benchmark/shutdown.json')['save_and_quit_reached_title']
    assert bench.read(directory / 'runner-exit.json')['exit_code'] == 0
    electrical = bench.read(directory / 'benchmark/result.json')
    assert electrical['passed'] and electrical['cee_solves'] == electrical['rejected_solves'] == 0
    audit = electrical['audit']
    assert [audit[k] for k in ('electrical_nodes', 'edges', 'solver_networks', 'optimized_matrix_nodes')] == [14650, 20637, 1, 14925]
    assert audit['actual_backends'] == ['NATIVE']
    loading = bench.read(directory / 'benchmark/loading-results.json')
    assert len(loading) == 1 and loading[0]['healthy_ticks'] == 100 and loading[0]['requested_chunks'] == 953
    assert len(bench.read(directory / 'benchmark/pre-solve-storage.json')) == 1050
    with (directory / 'benchmark/ticks.csv').open(newline='') as stream:
        ticks = [r for r in csv.DictReader(stream) if r['phase'] == 'SETTLE']
    assert len(ticks) >= 100
    assert all(int(r['pg_solve_calls']) == 16 and int(r['matrix_assemblies']) == 0 for r in ticks[-100:])
    loaded = {a['id']: a for a in bench.read(directory / 'client-benchmark/loaded-artifacts.json')}
    for mod, key in [('wwpg', 'runtime'), ('electroenergetics', 'cee'), ('wwpg_benchmark', 'helper')]:
        assert loaded[mod]['sha256'] == inputs[key]['sha256'], mod
    pinned = bench.read(ROOT / 'release/artifacts.json')
    for mod, role in [('powergrid', 'pg'), ('create', 'create')]:
        assert loaded[mod]['sha256'] == next(a['sha256'] for a in pinned['artifacts'] if a['role'] == role), mod
    screenshot = directory / 'client-benchmark/screenshots/rendered.png'
    assert screenshot.is_file() and bench.sha(screenshot) == result['screenshot_sha256']
    observation = result['readiness_observation']
    assert observation['loaded_client_chunks'] == result['expected_client_chunks']
    assert observation['rendered_sections'] > 0 and observation['dirty_sections'] == observation['uncompiled_sections'] == 0
    assert observation['electrical_ready'] and observation['camera_ready'] and observation['occlusion_ready'] and observation['queue_empty']
    assert observation['free_compile_buffers'] == result['compile_buffers']
    assert result['ready_confirmation_seconds'] >= 2
    assert result['resolution'] == [1920, 1080] and result['render_distance_chunks'] == 12
    if inputs.get('inspect_delivery', False):
        # The unchanged client artifact observer lists its original mod IDs.
        # The inspector is in that same already-verified wwpg_benchmark jar.
        records = {}
        for kind in ('server', 'batches'):
            records[kind] = [json.loads(line) for line in (directory / f'client-benchmark/delivery-{kind}.jsonl').read_text().splitlines()]
            assert records[kind]
        assert sum(row['chunks'] for row in records['batches']) >= result['expected_client_chunks']
        if inputs.get('inspect_task_queues', False):
            tasks = [json.loads(line) for line in (directory / 'client-benchmark/delivery-tasks.jsonl').read_text().splitlines()]
            assert {row['queue'] for row in tasks} == {'server', 'chunk'}
            assert all(row['wait_nanos'] >= -1 and row['execution_nanos'] >= row['exclusive_execution_nanos'] >= 0 for row in tasks)
    with (directory / 'process-metrics.jsonl').open() as stream:
        samples = [json.loads(line) for line in stream]
    assert samples and all(row['affinity_mask'] == 'fff000' for row in samples)
    log = (directory / 'process.log').read_text(errors='replace')
    priority_requested = bench.read(directory / 'experiment.json')['properties'].get(
        'wwpg.experimental.wireTreePrioritizeUnresolved', 'false') == 'true'
    priority = re.findall(r'prioritizeUnresolved=(true|false), prioritySelections=(\d+)', log)
    if priority_requested:
        assert len(priority) == 1 and priority[0][0] == 'true' and int(priority[0][1]) > 0
    elif priority:
        assert priority == [('false', '0')]
    task_budget = int(bench.read(directory / 'experiment.json')['properties'].get(
        'wwpg.experimental.chunkTaskBudgetMillis', '0'))
    stats = validate_task_window(log, task_budget)
    if stats is not None:
        bench.write(audit_directory / 'client-benchmark/task-window-validation.json', stats)
    geometry = validate_cee_geometry(log, bench.read(directory / 'experiment.json')['properties'].get(
        'wwpg.experimental.ceeStateGeometry', 'false') == 'true')
    if geometry is not None:
        bench.write(audit_directory / 'client-benchmark/cee-geometry-validation.json', geometry)
    terminals = validate_terminal_bookkeeping(log, bench.read(directory / 'experiment.json')['properties'].get(
        'wwpg.experimental.terminalBookkeeping', 'false') == 'true')
    if terminals is not None:
        bench.write(audit_directory / 'client-benchmark/terminal-bookkeeping-validation.json', terminals)
    keys = validate_terminal_keys(log, bench.read(directory / 'experiment.json')['properties'].get(
        'wwpg.experimental.terminalKeyLookup', 'false') == 'true')
    if keys is not None:
        bench.write(audit_directory / 'client-benchmark/terminal-key-validation.json', keys)
    saved = bench.completed_world_save(log)
    (audit_directory / 'client-benchmark').mkdir(parents=True, exist_ok=True)
    bench.write(audit_directory / 'shutdown-save.json', saved)
    assert saved['all_dimensions_saved']
    bench.write(audit_directory / 'client-benchmark/validation.json', dict(passed=True, full_suite=False, actual_nodes=14650,
        edges=20637, storage_devices=1050, healthy_ticks=100, sixteen_substeps=True,
        loaded_artifacts_verified=True, normal_shutdown=True, process_affinity_readbacks=len(samples)))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--tree-prioritize-unresolved', action='store_true')
    parser.add_argument('--chunk-task-budget-ms', type=int, choices=range(21), default=0)
    parser.add_argument('--cee-state-geometry', action='store_true')
    parser.add_argument('--terminal-bookkeeping', action='store_true')
    parser.add_argument('--terminal-key-lookup', action='store_true')
    parser.add_argument('--inspect-delivery', action='store_true')
    parser.add_argument('--delivery-jfr', action='store_true')
    parser.add_argument('--inspect-task-queues', action='store_true')
    args = parser.parse_args()
    if args.delivery_jfr and not args.inspect_delivery:
        parser.error('--delivery-jfr requires --inspect-delivery')
    if args.inspect_task_queues and not args.inspect_delivery:
        parser.error('--inspect-task-queues requires --inspect-delivery')
    inputs_path = (ROOT / args.inputs).resolve()
    assert inputs_path.is_relative_to(ROOT / 'local/frozen')
    inputs = bench.read(inputs_path)
    assert inputs.get('inspect_delivery', False) == args.inspect_delivery, 'Use a matching clean or diagnostic helper'
    assert inputs.get('inspect_task_queues', False) == args.inspect_task_queues, 'Use a matching task observer'
    for item in inputs['sources']:
        assert bench.sha(ROOT / item['path']) == item['sha256'], item['path']
    for item in inputs['classpath'] + [inputs[k] for k in ('runtime', 'cee', 'helper')]:
        assert bench.sha(item['path']) == item['sha256'], item['path']
    template = bench.read(inputs_path.parent / 'launch-template.json')
    reference = INPUTS / 'reference'
    copy = bench.read(INPUTS / 'reference-copy.json')
    original = Path(copy['source'])
    for item in copy['files']:
        assert bench.sha(reference / item['path']) == bench.sha(original / item['path']) == item['sha256']
    before, reference_before = inventory(original), inventory(reference)
    directory = bench.safe_directory(ROOT / 'local/benchmarks' / args.name)
    directory.mkdir(parents=True, exist_ok=False)
    world_name = 'benchmark-world'
    bench.write(directory / 'restore-input.json', bench.restore_reference(reference, directory / 'saves' / world_name))
    bench.write(directory / 'WWPG_BENCHMARK_OWNED.json', dict(schema=1, root=str(directory), copied_save=True))
    (directory / 'options.txt').write_text('version:3955\nrenderDistance:12\nsimulationDistance:12\nmaxFps:60\n'
        'enableVsync:false\nfullscreen:false\npauseOnLostFocus:false\nonboardAccessibility:false\n'
        'graphicsMode:1\nguiScale:2\nentityDistanceScaling:1.0\nfov:0.0\nautoJump:false\nbobView:false\n'
        'soundCategory_music:0.0\ntutorialStep:none\n', encoding='utf-8')
    properties = {'wwpg.clientBenchmark.enabled': 'true', 'wwpg.clientBenchmark.save': world_name,
        'wwpg.clientBenchmark.inspectDelivery': str(args.inspect_delivery).lower(),
        'wwpg.clientBenchmark.inspectTaskQueues': str(args.inspect_task_queues).lower(),
        'wwpg.benchmark.autorun': 'true', 'wwpg.benchmark.owned': 'true', 'wwpg.benchmark.restore': 'true',
        'wwpg.benchmark.exit': 'false', 'wwpg.benchmark.tier': '10000', 'wwpg.benchmark.variant': 'MIXED',
        'wwpg.benchmark.backend': 'NATIVE', 'wwpg.benchmark.mode': 'coldcheck', 'wwpg.benchmark.detailed': 'false',
        'wwpg.benchmark.rhsCounters': 'false', 'wwpg.benchmark.substeps': 'false', 'wwpg.benchmark.profileEvery': '20',
        'wwpg.benchmark.loading_protocol': '2', 'wwpg.benchmark.loading_chunk_limit': '2859',
        'wwpg.benchmark.loading_graph_limit': '300', 'wwpg.benchmark.loading_acceptance_limit': '300',
        'wwpg.benchmark.loading_healthy_ticks': '100', 'wwpg.benchmark.loading_stall_seconds': '30',
        'wwpg.benchmark.cpuMask': '0xfff000', 'wwpg.restorationBatch': 'true',
        'wwpg.experimental.plainWireBatch': 'true', 'wwpg.experimental.plainWireBatch.properties': 'true',
        'wwpg.experimental.plainWireBatch.verify': 'false',
        'wwpg.experimental.wireTreeBatch': 'true', 'wwpg.experimental.wireTreeBudgetMillis': '20',
        'wwpg.experimental.wireTreeMaxParts': '32',
        'wwpg.experimental.wireTreePrioritizeUnresolved': str(args.tree_prioritize_unresolved).lower(),
        'wwpg.experimental.chunkTaskBudgetMillis': str(args.chunk_task_budget_ms),
        'wwpg.experimental.ceeStateGeometry': str(args.cee_state_geometry).lower(),
        'wwpg.experimental.terminalBookkeeping': str(args.terminal_bookkeeping).lower(),
        'wwpg.experimental.terminalKeyLookup': str(args.terminal_key_lookup).lower()}
    # Freeze launch bytes in this attempt. Frozen template dependencies are verified above.
    argument_file = directory / 'client.args'
    argument_file.write_text(''.join(f'-D{k}={v}\n' for k, v in properties.items())
                            + ('-XX:StartFlightRecording=filename=client-delivery.jfr,settings=profile,dumponexit=true,maxsize=256m\n'
                               if args.delivery_jfr else '')
                            + Path(template['arguments']).read_text(), encoding='utf-8', newline='\n')
    source_copy = directory / 'runner-sources'
    source_copy.mkdir()
    source_evidence = []
    for name in ('client_loading_benchmark.py', 'cpu_affinity.py', 'host_metrics.py', 'legacy_bench.py',
                 'plain_wire_benchmark.py', 'process_metrics.py', 'world_save_evidence.py'):
        source = ROOT / 'tools/experimental' / name
        target = source_copy / name
        shutil.copy2(source, target)
        source_evidence.append(dict(path=str(target), sha256=bench.sha(target)))
    protocol = dict(schema=1, name=args.name, helper_inputs=str(inputs_path), helper_inputs_sha256=bench.sha(inputs_path),
        runtime=inputs['runtime'], helper=inputs['helper'], cee=inputs['cee'], copied_reference=True, full_suite=False,
        inspection_only=args.inspect_delivery, delivery_jfr=args.delivery_jfr,
        cpu_affinity_mask='fff000', properties=properties, runner_sources=source_evidence,
        started_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()))
    bench.write(directory / 'experiment.json', protocol)
    process = None
    started = time.monotonic()
    try:
        preflight = observe_group(0xfff000)
        bench.write(directory / 'cpu-preflight.json', preflight)
        if not preflight['quiet']:
            raise OSError('Selected CPU group is busy; no client launched')
        environment = dict(os.environ, **template['environment'])
        command = [template['java'], '@' + str(argument_file)]
        bench.write(directory / 'launch.json', dict(command=command, environment=template['environment'],
            directory=str(directory), arguments_sha256=bench.sha(argument_file), cpu_affinity_mask='fff000'))
        print(json.dumps(dict(starting=str(directory), resolution=[1920, 1080], render_distance_chunks=12)), flush=True)
        with HostWatch(directory / 'host-metrics.jsonl'), (directory / 'process.log').open('w', encoding='utf-8') as log:
            process = subprocess.Popen(command, cwd=directory, env=environment, stdout=log, stderr=subprocess.STDOUT,
                                       creationflags=subprocess.CREATE_NO_WINDOW)
            try:
                bench.write(directory / 'process-affinity.json', pin_owned(process, 0xfff000))
                metrics = ChildMetrics(process.pid)
                try:
                    last = None
                    with (directory / 'process-metrics.jsonl').open('w') as metrics_file:
                        while process.poll() is None:
                            if time.monotonic() - started > 540:
                                raise TimeoutError('Focused client launch exceeded nine-minute limit')
                            try:
                                actual, _ = affinity(process)
                                if actual != 0xfff000: raise RuntimeError('Client affinity changed')
                                sample = metrics.sample()
                            except OSError:
                                if process.poll() is not None: break
                                raise
                            sample['affinity_mask'] = f'{actual:x}'
                            metrics_file.write(json.dumps(sample) + '\n'); metrics_file.flush()
                            status = directory / 'client-benchmark/status.json'
                            if status.is_file():
                                try: state = bench.read(status)
                                except json.JSONDecodeError: state = None
                                if state and state != last:
                                    print(json.dumps(state), flush=True); last = state
                            if (directory / 'CANCEL').exists(): raise KeyboardInterrupt('Owned run cancellation requested')
                            time.sleep(1)
                finally:
                    metrics.close()
            finally:
                if process.poll() is None:
                    # Only this Popen-owned disposable client can be stopped. Interrupted evidence never passes.
                    process.terminate()
                    try: process.wait(timeout=20)
                    except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=10)
                bench.write(directory / 'runner-exit.json', dict(pid=process.pid, exit_code=process.returncode,
                    elapsed_seconds=time.monotonic() - started))
        protocol['result'] = validate(directory, inputs)
        if args.delivery_jfr:
            recording = directory / 'client-delivery.jfr'
            assert recording.is_file() and recording.stat().st_size > 0
            protocol['recording'] = dict(path=str(recording), sha256=bench.sha(recording))
        protocol['validation_passed'] = True
        print(json.dumps(protocol['result']), flush=True)
    except BaseException as error:
        protocol['validation_passed'] = False
        protocol['failure'] = str(error)
        raise
    finally:
        protocol['original_reference_unchanged'] = inventory(original) == before
        protocol['isolated_reference_unchanged'] = inventory(reference) == reference_before
        protocol['finished_utc'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        bench.write(directory / 'experiment.json', protocol)
        assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']


if __name__ == '__main__':
    main()
