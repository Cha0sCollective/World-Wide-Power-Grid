"""Preserve a completed focused client measurement and hashes, without rerunning the client."""
import argparse
import csv
import json
import re

from plain_wire_benchmark import ROOT, bench, validate_task_window, validate_cee_geometry, validate_terminal_bookkeeping, validate_terminal_keys
from plain_wire_benchmark import validate_scoped_restoration


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    folder = bench.safe_directory(ROOT / 'local/benchmarks' / args.attempt)
    protocol = bench.read(folder / 'experiment.json')
    result = bench.read(folder / 'client-benchmark/result.json')
    validation = bench.read(folder / 'client-benchmark/validation.json')
    assert protocol['validation_passed'] and result['passed'] and validation['passed']
    assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
    assert bench.read(folder / 'runner-exit.json')['exit_code'] == 0
    with (folder / 'benchmark/ticks.csv').open(newline='') as stream:
        ticks = [r for r in csv.DictReader(stream) if r['phase'] == 'SETTLE']
    frame_times = bench.read(folder / 'client-benchmark/frame-times-ns.json')
    tree_line = [line for line in (folder / 'process.log').read_text(errors='replace').splitlines()
                 if 'WWPG experimental wire-tree batch: attachments=' in line]
    assert len(tree_line) == 1
    tree = {name: int(number) for name, number in re.findall(r'(\w+)=(\d+)', tree_line[0])}
    assert tree['pending'] == tree['discarded'] == 0
    # A saved closed loop may need its ordinary final connection after both
    # root traversals; prioritizing unresolved parts reaches those seeds sooner.
    assert 0 <= tree['forced'] <= tree['attachments']
    priority_requested = protocol['properties'].get('wwpg.experimental.wireTreePrioritizeUnresolved', 'false') == 'true'
    observed_priority = re.search(r'prioritizeUnresolved=(true|false)', tree_line[0])
    if priority_requested:
        assert observed_priority and observed_priority[1] == 'true' and tree['prioritySelections'] > 0
    elif observed_priority:
        assert observed_priority[1] == 'false' and tree['prioritySelections'] == 0
    assert tree['budgetMillis'] == 20 and 0 < tree['maxProcessed'] <= tree['maxParts'] == 32
    assert tree['roots'] == tree['attachments'] * 2 == tree['traversals'] + tree['covered']
    task_window = validate_task_window((folder / 'process.log').read_text(errors='replace'),
        int(protocol['properties'].get('wwpg.experimental.chunkTaskBudgetMillis', '0')))
    geometry = validate_cee_geometry((folder / 'process.log').read_text(errors='replace'),
        protocol['properties'].get('wwpg.experimental.ceeStateGeometry', 'false') == 'true')
    terminals = validate_terminal_bookkeeping((folder / 'process.log').read_text(errors='replace'),
        protocol['properties'].get('wwpg.experimental.terminalBookkeeping', 'false') == 'true')
    keys = validate_terminal_keys((folder / 'process.log').read_text(errors='replace'),
        protocol['properties'].get('wwpg.experimental.terminalKeyLookup', 'false') == 'true')
    scope = validate_scoped_restoration((folder / 'process.log').read_text(errors='replace'),
        protocol['properties'].get('wwpg.experimental.scopedRestoration', 'false') == 'true', True)
    paths = ['experiment.json', 'restore-input.json', 'launch.json', 'client.args', 'options.txt', 'cpu-preflight.json',
        'process-affinity.json', 'process-metrics.jsonl', 'host-metrics.jsonl', 'process.log', 'runner-exit.json',
        'shutdown-save.json', 'benchmark/result.json', 'benchmark/loading-results.json', 'benchmark/ticks.csv',
        'benchmark/pre-solve-storage.json', 'benchmark/placement-fixed.json', 'benchmark/placement-restored.json',
        'client-benchmark/start.json', 'client-benchmark/result.json', 'client-benchmark/validation.json',
        'client-benchmark/loaded-artifacts.json', 'client-benchmark/render-observations.json',
        'client-benchmark/frame-times-ns.json', 'client-benchmark/shutdown.json', 'client-benchmark/screenshots/rendered.png']
    if task_window is not None:
        paths.append('client-benchmark/task-window-validation.json')
        assert task_window == bench.read(folder / paths[-1])
    if geometry is not None:
        paths.append('client-benchmark/cee-geometry-validation.json')
        assert geometry == bench.read(folder / paths[-1])
    if terminals is not None:
        paths.append('client-benchmark/terminal-bookkeeping-validation.json')
        assert terminals == bench.read(folder / paths[-1])
    if keys is not None:
        paths.append('client-benchmark/terminal-key-validation.json')
        assert keys == bench.read(folder / paths[-1])
    if scope is not None:
        paths.append('client-benchmark/scoped-restoration-validation.json')
        assert scope == bench.read(folder / paths[-1])
    evidence = [dict(path=(folder / path).relative_to(ROOT).as_posix(), sha256=bench.sha(folder / path)) for path in paths]
    for kind in ('runtime', 'helper', 'cee'):
        assert bench.sha(protocol[kind]['path']) == protocol[kind]['sha256']
    assert bench.sha(protocol['helper_inputs']) == protocol['helper_inputs_sha256']
    tick_ns = [int(row['tick_ns']) for row in ticks]
    gaps = [int(b['wall_ns']) - int(a['wall_ns']) for a, b in zip(ticks, ticks[1:])]
    data = dict(schema=1, full_suite=False, attempt=args.attempt, protocol=protocol, result=result, validation=validation,
        server_loading=bench.read(folder / 'benchmark/loading-results.json'), tree_execution=tree,
        task_window=task_window,
        cee_geometry=geometry,
        terminal_execution=terminals,
        terminal_keys=keys,
        scope_execution=scope,
        worst_loading_tick_seconds=max(tick_ns) / 1e9, longest_loading_tick_gap_seconds=max(gaps) / 1e9,
        world_frame_intervals=dict(samples=len(frame_times), mean_ms=sum(frame_times) / len(frame_times) / 1e6,
            p95_ms=bench.percent(frame_times, .95) / 1e6, maximum_ms=max(frame_times) / 1e6),
        loaded_artifacts=bench.read(folder / 'client-benchmark/loaded-artifacts.json'), evidence=evidence,
        note='One stationary view at the recorded settings. Loading phases overlap with rendering. No sustained client TPS or complete graphical acceptance claim.')
    # The shared host summarizer expects the headless invocation clock; the client
    # records its own selection clock, so retain its raw host evidence without relabeling.
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2) + '\n')
    print(json.dumps({key: data[key] for key in ('attempt', 'worst_loading_tick_seconds', 'longest_loading_tick_gap_seconds', 'world_frame_intervals')}))


if __name__ == '__main__':
    main()
