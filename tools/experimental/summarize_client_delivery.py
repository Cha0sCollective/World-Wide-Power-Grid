"""Summarize read-only client delivery captures; keep failed-run audits explicit."""
import argparse
from collections import Counter
from datetime import datetime
import json
from pathlib import Path
import statistics
import zipfile

from summarize_tree import ROOT, evidence, read, sha


def capture(name, audit_path=None):
    folder = ROOT / 'local/benchmarks' / name
    protocol = read(folder / 'experiment.json')
    result = read(folder / 'client-benchmark/result.json')
    assert protocol['inspection_only'] and result['passed']
    assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
    audit = read(ROOT / audit_path) if audit_path else None
    if audit:
        assert not protocol['validation_passed'] and audit['passed'] and audit['original_runner_failed']
        assert audit['original_protocol_sha256'] == sha(folder / 'experiment.json')
        audit_folder = (ROOT / audit_path).parent
    else:
        assert protocol['validation_passed']
        audit_folder = folder
    validation = read(audit_folder / 'client-benchmark/validation.json')
    assert validation['passed'] and read(folder / 'runner-exit.json')['exit_code'] == 0
    assert read(folder / 'client-benchmark/shutdown.json')['save_and_quit_reached_title']
    assert read(audit_folder / 'shutdown-save.json')['all_dimensions_saved']
    helper = read(Path(protocol['helper_inputs']))
    assert helper['inspection_only'] and helper['inspect_delivery']
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in helper['sources'])
    for kind in ('runtime', 'helper', 'cee'):
        assert sha(Path(protocol[kind]['path'])) == protocol[kind]['sha256']
    clean = read(ROOT / 'local/frozen/client-loading-helper-04/inputs.json')
    assert clean['runtime'] == helper['runtime']
    assert sha(Path(clean['helper']['path'])) == clean['helper']['sha256']
    with zipfile.ZipFile(clean['helper']['path']) as before, zipfile.ZipFile(helper['helper']['path']) as after:
        changed = [p for p in before.namelist() if p not in after.namelist() or before.read(p) != after.read(p)]
        assert changed == ['META-INF/neoforge.mods.toml']
    server = [json.loads(line) for line in (folder / 'client-benchmark/delivery-server.jsonl').read_text().splitlines()]
    batches = [json.loads(line) for line in (folder / 'client-benchmark/delivery-batches.jsonl').read_text().splitlines()]
    render = read(folder / 'client-benchmark/render-observations.json')
    start = result['selected_unix_ms']
    seconds = lambda row: (row['wall_unix_ms'] - start) / 1000
    assert all(s['memory_connection'] and s['view_chunks'] == 609 for s in server)
    assert sum(b['chunks'] for b in batches) >= 609
    timeline = []
    for target in range(20, int(result['restored_rendered_seconds']) + 1, 10):
        s = min(server, key=lambda row: abs(seconds(row) - target))
        r = min(render, key=lambda row: abs(row['elapsed_seconds'] - target))
        timeline.append(dict(target_seconds=target, server_seconds=seconds(s), render_seconds=r['elapsed_seconds'],
            full_chunks=s['view_full_chunks'], ticking_chunks=s['view_ticking_chunks'], sendable=s['view_sendable'],
            pending_send=s['pending_chunks'], unacknowledged=s['unacknowledged_batches'],
            desired_chunks_per_tick=s['desired_chunks_per_tick'], received=r['loaded_client_chunks'],
            render_queue_empty=r['queue_empty'], dirty_sections=r['dirty_sections'], uncompiled_sections=r['uncompiled_sections']))
    late = [s for s in server if seconds(s) >= result['electrical_ready_seconds']]
    assert late
    probes = [s['probe_nanos'] / 1e6 for s in server]
    summary = dict(server_samples=len(server), batch_samples=len(batches), received_chunks=sum(b['chunks'] for b in batches),
        batch_threads=dict(Counter(b['thread'] for b in batches)), probe_mean_ms=statistics.mean(probes), probe_max_ms=max(probes),
        late_max_pending_send=max(s['pending_chunks'] for s in late),
        late_max_unacknowledged=max(s['unacknowledged_batches'] for s in late),
        late_ack_limit_samples=sum(s['unacknowledged_batches'] >= s['maximum_unacknowledged_batches'] for s in late),
        late_min_desired_chunks_per_tick=min(s['desired_chunks_per_tick'] for s in late),
        late_send_dependency_wait_samples=sum(s['view_waiting_send_dependencies'] > 0 for s in late),
        first_observed_full_view_seconds=next((seconds(s) for s in server if s['view_full_chunks'] == 609), None),
        first_observed_tick_ready_view_seconds=next((seconds(s) for s in server if s['view_ticking_chunks'] == 609), None))
    paths = ['experiment.json', 'launch.json', 'restore-input.json', 'runner-exit.json', 'process.log',
        'cpu-preflight.json', 'process-affinity.json', 'process-metrics.jsonl', 'host-metrics.jsonl',
        'benchmark/result.json', 'benchmark/loading-results.json', 'benchmark/pre-solve-storage.json',
        'client-benchmark/result.json', 'client-benchmark/render-observations.json',
        'client-benchmark/loaded-artifacts.json', 'client-benchmark/shutdown.json',
        'client-benchmark/delivery-server.jsonl', 'client-benchmark/delivery-batches.jsonl']
    item = dict(name=name, protocol=protocol, result=result, validation=validation, audit=audit,
        shared_client_observer_classes_unchanged=True, summary=summary, timeline=timeline,
        evidence=[evidence(folder / p) for p in paths]
        + [evidence(audit_folder / 'client-benchmark/validation.json'), evidence(audit_folder / 'shutdown-save.json'),
           evidence(Path(protocol['helper_inputs']))])
    if audit_path:
        item['evidence'].append(evidence(ROOT / audit_path))
    if protocol.get('delivery_jfr'):
        assert sha(Path(protocol['recording']['path'])) == protocol['recording']['sha256']
        compact = folder / 'client-delivery-compact.json'
        events = read(compact)['events']
        finish_ms = start + result['restored_rendered_seconds'] * 1000
        electrical_ms = start + result['electrical_ready_seconds'] * 1000
        samples = [e for e in events if e['type'] == 'jdk.ExecutionSample'
            and electrical_ms <= datetime.fromisoformat(e['start'].replace('Z', '+00:00')).timestamp() * 1000 <= finish_ms]
        groups = {}
        for thread, count in Counter(e['thread'] for e in samples).most_common():
            selected = [e for e in samples if e['thread'] == thread]
            inclusive = Counter(frame for event in selected for frame in set(event['frames']))
            leaves = Counter(event['frames'][0] for event in selected if event['frames'])
            groups[thread] = dict(samples=count, truncated=sum(e['truncated'] for e in selected),
                top_frames=[dict(method=frame, samples=n) for frame, n in leaves.most_common(20)],
                inclusive=[dict(method=frame, samples=n, percent=100*n/count) for frame,n in inclusive.most_common(50)])
        item['late_jfr'] = dict(start_seconds=result['electrical_ready_seconds'], end_seconds=result['restored_rendered_seconds'],
            threads=groups, note='Inclusive Java execution sample counts overlap; not wall-time shares or native CPU percentages.')
        item['evidence'] += [evidence(Path(protocol['recording']['path'])), evidence(compact)]
    return item


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inspection-attempt', required=True)
    parser.add_argument('--audit', required=True)
    parser.add_argument('--profile-attempt', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    runs = [capture(args.inspection_attempt, args.audit), capture(args.profile_attempt)]
    data = dict(schema=1, full_suite=False, inspection_only=True, measurements=runs,
        scope='Read-only delivery diagnosis; neither capture is clean timing evidence. Original failed runner and subsequent audit remain distinct.',
        inspected_source_inventory=evidence(ROOT / 'local/client-delivery-source-01/inputs.json'),
        tool_sources=[evidence(ROOT / 'tools/experimental' / p) for p in
            ('client_loading_benchmark.py', 'freeze_client_loading.py', 'summarize_client_delivery.py', 'JfrCompact.java')])
    with (ROOT / args.output).open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(json.dumps(data, indent=2) + '\n')
    print(json.dumps([dict(name=r['name'], summary=r['summary']) for r in runs]))


if __name__ == '__main__':
    main()
