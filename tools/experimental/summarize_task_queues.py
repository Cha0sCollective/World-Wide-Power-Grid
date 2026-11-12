"""Preserve queue wait/execution observations without treating them as clean timings."""
import argparse
from collections import Counter
import json
import math
import statistics

from summarize_tree import ROOT, evidence, read


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    folder = ROOT / 'local/benchmarks' / args.attempt
    protocol = read(folder / 'experiment.json')
    result = read(folder / 'client-benchmark/result.json')
    assert protocol['validation_passed'] and result['passed'] and protocol['inspection_only']
    assert protocol['properties']['wwpg.clientBenchmark.inspectTaskQueues'] == 'true'
    assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
    assert read(folder / 'runner-exit.json')['exit_code'] == 0
    tasks = [json.loads(line) for line in (folder / 'client-benchmark/delivery-tasks.jsonl').read_text().splitlines()]
    assert tasks and all(t['thread'] == 'Server thread' for t in tasks)
    start = result['selected_unix_ms']
    ready = start + result['electrical_ready_seconds'] * 1000
    end = start + result['restored_rendered_seconds'] * 1000
    def stats(values):
        values = sorted(values)
        return dict(mean_ms=statistics.mean(values), p50_ms=values[math.ceil(len(values)*.5)-1],
                    p95_ms=values[math.ceil(len(values)*.95)-1], maximum_ms=values[-1])
    phases = {}
    for phase, lo, hi in [('electrical_restoration', start, ready), ('remaining_view', ready, end)]:
        groups = {}
        for queue in ('server', 'chunk'):
            selected = [t for t in tasks if t['queue'] == queue and lo <= t['wall_unix_ms'] <= hi]
            waits = [t['wait_nanos']/1e6 for t in selected if t['wait_nanos'] >= 0]
            groups[queue] = dict(completed_tasks=len(selected), unmatched_enqueues=len(selected)-len(waits),
                queue_wait=stats(waits), execution=stats([t['execution_nanos']/1e6 for t in selected]),
                exclusive_execution_total_ms=sum(t['exclusive_execution_nanos'] for t in selected)/1e6,
                waits_over_ten_ms=sum(w > 10 for w in waits),
                task_classes=dict(Counter(t['task_class'] for t in selected)))
        phases[phase] = dict(duration_seconds=(hi-lo)/1000, queues=groups)
    server = [json.loads(line) for line in (folder / 'client-benchmark/delivery-server.jsonl').read_text().splitlines()]
    late = [s for s in server if ready <= s['wall_unix_ms'] <= end]
    data = dict(schema=1, attempt=args.attempt, inspection_only=True, full_suite=False,
        protocol=protocol, result=result, phases=phases, total_observed_tasks=len(tasks),
        late_sampled_queue_depth_max=dict(server=max(s['pending_server_tasks'] for s in late), chunk=max(s['pending_chunk_tasks'] for s in late)),
        note='Tasks grouped by completion time. Callback execution includes observer overhead; nested execution is subtracted only from the exclusive totals. These are not total chunk-generation CPU costs.',
        evidence=[evidence(folder / p) for p in ('experiment.json', 'launch.json', 'runner-exit.json', 'shutdown-save.json',
            'cpu-preflight.json', 'process-affinity.json', 'process-metrics.jsonl', 'host-metrics.jsonl',
            'client-benchmark/result.json', 'client-benchmark/validation.json', 'client-benchmark/loaded-artifacts.json',
            'client-benchmark/delivery-server.jsonl', 'client-benchmark/delivery-batches.jsonl', 'client-benchmark/delivery-tasks.jsonl',
            'benchmark/result.json', 'benchmark/loading-results.json', 'benchmark/pre-solve-storage.json')],
        helper_inputs=evidence(ROOT / 'local/frozen/client-task-helper-01/inputs.json'),
        tool_sources=[evidence(ROOT / 'tools/experimental' / p) for p in ('client_loading_benchmark.py', 'freeze_client_loading.py', 'summarize_task_queues.py')])
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2)+'\n')
    print(json.dumps(phases['remaining_view']))


if __name__ == '__main__':
    main()
