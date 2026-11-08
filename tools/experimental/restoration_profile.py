"""Profile one cold restoration on a fresh copied save, without a stable TPS trial."""
import argparse
import json
import time
from types import SimpleNamespace

import plain_wire_benchmark as experiment

bench = experiment.bench


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference-root', required=True)
    parser.add_argument('--name', required=True)
    parser.add_argument('--runtime', required=True)
    args = parser.parse_args()
    args.batch = args.property_batch = True
    args.verify_batch = False
    directory, reference, original = experiment.prepare(args)
    before = experiment.inventory(original)
    reference_before = experiment.inventory(reference)
    launch = bench.read(directory / 'launch.json')
    protocol = dict(schema=1, name=args.name, scope='One diagnostic cold restoration; no full suite or TPS measurement',
                    runtime=launch['runtime'], helper=launch['helper'], copied_reference=True,
                    batch=True, property_batch=True, detailed=True, profile_every=20,
                    loading_trace=True, full_suite=False,
                    started_utc=time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()))
    bench.write(directory / 'experiment.json', protocol)
    print(json.dumps(dict(starting=str(directory))), flush=True)
    options = SimpleNamespace(directory=str(directory), runtime_sha256=launch['runtime']['sha256'],
                              tier=10000, backend='NATIVE', variant='MIXED', mode='verify',
                              warmup=0, sample=1, churn=0, cycles=1, restore=str(reference),
                              detailed=True, timeout=600, restoration_batch=True, rhs_counters=False,
                              profile_every=20, loading_trace=True, loading_limit=2859,
                              substep_trace=False, substep_jfr=False)
    try:
        bench.run(options)
        result = bench.read(directory / 'benchmark/result.json')
        audit = result['audit']
        assert result['passed'] and result['diagnostic_only']
        assert result['cee_solves'] == result['rejected_solves'] == 0
        assert (audit['electrical_nodes'], audit['edges'], audit['solver_networks'],
                audit['optimized_matrix_nodes']) == (14650, 20637, 1, 14925)
        assert audit['actual_backends'] == ['NATIVE']
        loading = bench.read(directory / 'benchmark/loading-results.json')
        assert len(loading) == 1 and loading[0]['healthy_ticks'] == 100
        assert loading[0]['requested_chunks'] == 953
        assert bench.read(directory / 'shutdown-save.json')['all_dimensions_saved']
        assert len(bench.read(directory / 'benchmark/pre-solve-storage.json')) == 1050
        assert bench.read(directory / 'benchmark/placement-fixed.json')['mask'] == 'fff000'
        assert bench.read(directory / 'benchmark/placement-restored.json')['original_thread_affinity_restored']
        assert (directory / 'loading.jfr').stat().st_size > 0
        protocol['validation'] = dict(passed=True, actual_nodes=14650, edges=20637, chunks=953,
                                      storage_devices=1050, healthy_ticks=100, normal_shutdown=True,
                                      no_independent_cee_solves=True, full_suite=False)
        protocol['loading'] = loading[0]
        protocol['recording'] = dict(path='loading.jfr', sha256=bench.sha(directory / 'loading.jfr'))
        print(json.dumps(dict(completed=True, loading=loading[0])), flush=True)
    finally:
        protocol['original_reference_unchanged'] = experiment.inventory(original) == before
        protocol['isolated_reference_unchanged'] = experiment.inventory(reference) == reference_before
        protocol['finished_utc'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
        bench.write(directory / 'experiment.json', protocol)
        assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']


if __name__ == '__main__':
    main()
