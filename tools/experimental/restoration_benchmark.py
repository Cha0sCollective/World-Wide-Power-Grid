"""Finite copied-save loading comparison, with optional separate diagnostic/reload modes."""
import argparse
import csv
import json
import re
import time
from types import SimpleNamespace

import plain_wire_benchmark as experiment
from host_metrics import HostWatch

bench = experiment.bench


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference-root', required=True)
    parser.add_argument('--name', required=True)
    parser.add_argument('--runtime', required=True)
    parser.add_argument('--tree-batch', action='store_true')
    parser.add_argument('--tree-budget-ms', type=int, default=0)
    parser.add_argument('--tree-max-parts', type=int, default=32)
    parser.add_argument('--tree-retain-coverage', action='store_true')
    parser.add_argument('--tree-prioritize-unresolved', action='store_true')
    parser.add_argument('--chunk-task-budget-ms', type=int, choices=range(21), default=0)
    parser.add_argument('--cee-state-geometry', action='store_true')
    parser.add_argument('--scoped-restoration', action='store_true')
    parser.add_argument('--terminal-bookkeeping', action='store_true')
    parser.add_argument('--terminal-key-lookup', action='store_true')
    parser.add_argument('--helper-inputs', help='Explicit frozen helper overlay manifest for queue-aware readiness')
    parser.add_argument('--cpu-mask', choices=['fff','fff000'], help='Explicit whole-process CPU group after load observation')
    parser.add_argument('--server-cpu-mask', help='Explicit hexadecimal server-thread subset of the selected process group')
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--diagnostic', action='store_true')
    modes.add_argument('--reload', action='store_true')
    args = parser.parse_args()
    assert args.tree_budget_ms >= 0 and args.tree_max_parts > 0
    assert not args.tree_budget_ms or args.tree_batch
    assert not args.tree_retain_coverage or args.tree_batch
    assert not args.tree_prioritize_unresolved or args.tree_batch
    if args.scoped_restoration and (not args.tree_batch or args.tree_budget_ms <= 0):
        parser.error('--scoped-restoration requires bounded wire-tree batching')
    args.batch = args.property_batch = True
    args.verify_batch = False
    directory, reference, original = experiment.prepare(args)
    before = experiment.inventory(original)
    reference_before = experiment.inventory(reference)
    launch = bench.read(directory / 'launch.json')
    protocol = dict(schema=1, name=args.name, scope='Focused copied-save loading only; no full suite or TPS capacity claim',
                    runtime=launch['runtime'], helper=launch['helper'], copied_reference=True,
                    batch=True, property_batch=True, tree_batch=args.tree_batch, detailed=args.diagnostic,
                    loading_trace=args.diagnostic, reload=args.reload, full_suite=False,
                    tree_budget_ms=args.tree_budget_ms, tree_max_parts=args.tree_max_parts,
                    tree_retain_coverage=args.tree_retain_coverage, helper_inputs=args.helper_inputs,
                    tree_prioritize_unresolved=args.tree_prioritize_unresolved,
                    chunk_task_budget_ms=args.chunk_task_budget_ms,
                    cee_state_geometry=args.cee_state_geometry,
                    scoped_restoration=args.scoped_restoration,
                    terminal_bookkeeping=args.terminal_bookkeeping,
                    terminal_key_lookup=args.terminal_key_lookup,
                    cpu_affinity_mask=args.cpu_mask, server_affinity_mask=args.server_cpu_mask,
                    started_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()))
    bench.write(directory / 'experiment.json', protocol)
    print(json.dumps(dict(starting=str(directory), tree_batch=args.tree_batch, diagnostic=args.diagnostic, reload=args.reload)), flush=True)
    options = SimpleNamespace(directory=str(directory), runtime_sha256=launch['runtime']['sha256'],
                              tier=10000, backend='NATIVE', variant='MIXED', mode='verify' if args.reload or args.diagnostic else 'coldcheck',
                              warmup=0, sample=1, churn=0, cycles=1, restore=str(reference),
                              detailed=args.diagnostic, timeout=900, restoration_batch=True, rhs_counters=False,
                              profile_every=20, loading_trace=args.diagnostic,
                              loading_limit=2859 if args.diagnostic else None,
                              substep_trace=False, substep_jfr=False)
    try:
        with HostWatch(directory / 'host-metrics.jsonl'):
            bench.run(options)
        result = bench.read(directory / 'benchmark/result.json')
        audit = result['audit']
        assert result['passed'] and result['diagnostic_only'] == args.diagnostic
        assert result['cee_solves'] == result['rejected_solves'] == 0
        assert (audit['electrical_nodes'], audit['edges'], audit['solver_networks'],
                audit['optimized_matrix_nodes']) == (14650, 20637, 1, 14925)
        assert audit['actual_backends'] == ['NATIVE']
        loading = bench.read(directory / 'benchmark/loading-results.json')
        assert len(loading) == (2 if args.reload else 1)
        assert all(r['healthy_ticks'] == 100 and r['requested_chunks'] == 953 for r in loading)
        assert bench.read(directory / 'shutdown-save.json')['all_dimensions_saved']
        assert len(bench.read(directory / 'benchmark/pre-solve-storage.json')) == 1050
        expected_mask = args.cpu_mask or 'fff000'
        assert bench.read(directory / 'benchmark/placement-fixed.json')['mask'] == (args.server_cpu_mask or expected_mask)
        if args.cpu_mask:
            assert bench.read(directory / 'process-affinity.json')['readback_mask'] == expected_mask
            assert all(json.loads(line)['cpu_affinity_mask'] == expected_mask for line in (directory / 'process-metrics.jsonl').read_text().splitlines())
        assert bench.read(directory / 'benchmark/placement-restored.json')['original_thread_affinity_restored']
        with (directory / 'benchmark/ticks.csv').open() as stream:
            ticks = list(csv.DictReader(stream))
        assert ticks and all(int(r['pg_solve_calls']) % 16 == 0 for r in ticks)
        for phase in ('SETTLE', 'RELOAD') if args.reload else ('SETTLE',):
            selected = [r for r in ticks if r['phase'] == phase]
            assert len(selected) >= 100
            # RELOAD rows are written twice by the frozen helper. Check the last
            # hundred distinct ticks, not fifty duplicated observations.
            distinct = {int(r['tick']): r for r in selected}
            assert all(int(r['pg_solve_calls']) == 16 and int(r['matrix_assemblies']) == 0 for r in list(distinct.values())[-100:])
        log = (directory / 'process.log').read_text(encoding='utf-8', errors='replace')
        protocol['task_window'] = experiment.validate_task_window(log, args.chunk_task_budget_ms)
        protocol['cee_geometry'] = experiment.validate_cee_geometry(log, args.cee_state_geometry)
        protocol['scope_execution'] = experiment.validate_scoped_restoration(log, args.scoped_restoration, True)
        protocol['terminal_execution'] = experiment.validate_terminal_bookkeeping(log, args.terminal_bookkeeping)
        protocol['terminal_keys'] = experiment.validate_terminal_keys(log, args.terminal_key_lookup)
        if args.tree_batch:
            if args.tree_retain_coverage:
                assert log.count('WWPG experimental wire-tree retained coverage: enabled=true') == 1
            matches = re.findall(r'WWPG experimental wire-tree batch: attachments=(\d+), fallback=(\d+), roots=(\d+), traversals=(\d+), covered=(\d+), visited=(\d+), flushes=(\d+), largest=(\d+), pending=(\d+)', log)
            assert len(matches) == 1
            stats = dict(zip(('attachments', 'fallback', 'roots', 'traversals', 'covered', 'visited', 'flushes', 'largest', 'pending'), map(int, matches[0])))
            forced = re.findall(r'WWPG experimental wire-tree batch:.*?, forced=(\d+)', log)
            if forced:
                assert len(forced) == 1
                stats['forced'] = int(forced[0])
            budget = re.findall(r'WWPG experimental wire-tree batch:.*?, discarded=(\d+), budgetMillis=(\d+), maxParts=(\d+), yielded=(\d+), maxProcessed=(\d+), maxFlushNanos=(\d+), maxEntryNanos=(\d+)', log)
            if budget:
                assert len(budget) == 1
                stats.update(zip(('discarded','budgetMillis','maxParts','yielded','maxProcessed','maxFlushNanos','maxEntryNanos'),map(int,budget[0])))
                assert stats['budgetMillis'] == args.tree_budget_ms and stats['maxParts'] == args.tree_max_parts
            if args.tree_budget_ms:
                assert budget and stats['yielded'] > 0 and 0 < stats['maxProcessed'] <= args.tree_max_parts
            assert stats['attachments'] > 0 and stats['covered'] > 0 and stats['pending'] == 0
            assert stats['traversals'] + stats['covered'] + stats.get('discarded',0) == stats['roots'] == stats['attachments'] * 2
            priority = re.findall(r'prioritizeUnresolved=(true|false), prioritySelections=(\d+)', log)
            if args.tree_prioritize_unresolved:
                assert len(priority) == 1 and priority[0][0] == 'true' and int(priority[0][1]) > 0
            elif priority:
                assert len(priority) == 1 and priority[0] == ('false', '0')
            if priority:
                stats['priority_selections'] = int(priority[0][1])
            protocol['tree_execution'] = stats
        else:
            assert 'WWPG experimental wire-tree batch:' not in log
        if args.reload:
            assert result['actual_reload_cycles'] == 1
            lifecycle = bench.read(directory / 'benchmark/lifecycle.json')
            assert lifecycle[0]['actual_chunks_absent'] and lifecycle[0]['pre_solve_storage_verified']
        protocol['validation'] = dict(passed=True, actual_nodes=14650, edges=20637, chunks=953,
                                      storage_devices=1050, healthy_ticks=100, normal_shutdown=True,
                                      sixteen_substeps=True, no_independent_cee_or_rejected_solves=True,
                                      actual_reload_cycles=result['actual_reload_cycles'], full_suite=False)
        protocol['loading'] = loading
        if args.diagnostic:
            protocol['recording'] = dict(path='loading.jfr', sha256=bench.sha(directory / 'loading.jfr'))
        print(json.dumps(dict(completed=True, loading=loading, execution=protocol.get('tree_execution'))), flush=True)
    finally:
        protocol['original_reference_unchanged'] = experiment.inventory(original) == before
        protocol['isolated_reference_unchanged'] = experiment.inventory(reference) == reference_before
        protocol['finished_utc'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        bench.write(directory / 'experiment.json', protocol)
        assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']


if __name__ == '__main__':
    main()
