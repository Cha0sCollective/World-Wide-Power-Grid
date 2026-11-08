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
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument('--diagnostic', action='store_true')
    modes.add_argument('--reload', action='store_true')
    args = parser.parse_args()
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
        assert bench.read(directory / 'benchmark/placement-fixed.json')['mask'] == 'fff000'
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
        if args.tree_batch:
            matches = re.findall(r'WWPG experimental wire-tree batch: attachments=(\d+), fallback=(\d+), roots=(\d+), traversals=(\d+), covered=(\d+), visited=(\d+), flushes=(\d+), largest=(\d+), pending=(\d+)', log)
            assert len(matches) == 1
            stats = dict(zip(('attachments', 'fallback', 'roots', 'traversals', 'covered', 'visited', 'flushes', 'largest', 'pending'), map(int, matches[0])))
            forced = re.findall(r'WWPG experimental wire-tree batch:.*?, forced=(\d+)', log)
            if forced:
                assert len(forced) == 1
                stats['forced'] = int(forced[0])
            assert stats['attachments'] > 0 and stats['covered'] > 0 and stats['pending'] == 0
            assert stats['traversals'] + stats['covered'] == stats['roots'] == stats['attachments'] * 2
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
