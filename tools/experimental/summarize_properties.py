"""Record the property-source experiment separately from the preserved first prototype."""
import csv
import hashlib
import itertools
import json
import re
from pathlib import Path
from summarize import ROOT, evidence, read


def benchmark(file):
    protocol = read(file)
    if 'finished_utc' not in protocol:
        raise RuntimeError(f'Attempt still running: {file.parent.name}')
    entry = dict(name=protocol['name'], evidence=evidence(file), runtime_sha256=protocol['runtime']['sha256'],
                 batch=protocol['batch'], property_batch=protocol['property_batch'], detailed=protocol['detailed'],
                 verify_batch=protocol['verify_batch'], original_reference_unchanged=protocol['original_reference_unchanged'],
                 started_utc=protocol['started_utc'], finished_utc=protocol['finished_utc'],
                 validation=protocol.get('validation'), launch_evidence=evidence(file.parent / 'launch.json'),
                 process_log_evidence=evidence(file.parent / 'process.log'))
    if 'summary' not in protocol:
        entry['classification'] = 'No completed validation; inspect the preserved attempt'
        return entry
    summary = protocol['summary']
    entry.update(tps=summary['tps'], mspt=summary['mspt'], stable_ticks=summary['samples'],
                 measured_seconds=summary['measured_seconds'], gc_ms=summary['gc_ms'], process=summary['process'],
                 audit=summary['audit'], restoration=read(file.parent / 'benchmark/loading-results.json')[0],
                 practical_20tps_target_met=summary['practical_20tps_target_met'])
    entry['mean_margin_under_50_ms'] = 50 - summary['mspt']['mean']
    entry['p95_margin_under_50_ms'] = 50 - summary['mspt']['p95']
    ticks = file.parent / 'benchmark/ticks.csv'
    with ticks.open() as stream:
        rows = [row for row in csv.DictReader(stream) if row['phase'] == 'STABLE']
    entry['ticks_over_50_ms'] = sum(int(row['tick_ns']) > 50_000_000 for row in rows)
    entry['tick_evidence'] = evidence(ticks)
    graph = read(file.parent / 'benchmark/graph-start.json')[0]
    entry['assigned_row_order_sha256'] = graph['assigned_row_order_sha256']
    entry['hook_types'] = graph['multiHooks']['types']
    entry['placement'] = read(file.parent / 'benchmark/placement-fixed.json')
    native = list((file.parent / '.pg-native/wwpg-safety-1').glob('*/libpowergridNative7.dll'))
    assert len(native) == 1
    entry['loaded_native_resource'] = evidence(native[0])
    if protocol['detailed']:
        substeps = file.parent / 'benchmark/substeps.csv'
        with substeps.open() as stream:
            records = list(csv.DictReader(stream))
        for tick, group in itertools.groupby(records, key=lambda row: row['tick']):
            trace = list(group)
            assert [int(row['step']) for row in trace] == list(range(1, 17)), tick
            assert all(row['loop_mask'] == '7' and row['overflow'] == row['rejected'] == '0'
                       and row['pg_accepted'] == row['pg_returned'] == 'true' and row['partial'] == 'false' for row in trace), tick
        entry['complete_sixteen_step_traces'] = True
        entry['profiled_ticks'] = len(records) // 16
        entry['substep_evidence'] = evidence(substeps)
        names = ['pg_micro_hooks_ns', 'pg_pre_hooks_ns', 'pg_post_hooks_ns', 'rhs_ns', 'native_solve_ns',
                 'history_prepare_ns', 'history_commit_ns', 'native_readings_ns']
        entry['profiled_mean_ms_per_tick'] = {key.removesuffix('_ns'): sum(int(row[key]) for row in records) / (len(records) / 16) / 1e6 for key in names}
    return entry


def main():
    frozen = ROOT / 'local/frozen/prototype-02c'
    inputs = read(frozen / 'inputs.json')
    for record in inputs['artifacts'].values():
        assert hashlib.sha256(Path(record['path']).read_bytes()).hexdigest() == record['sha256']
    for record in inputs['sources']:
        assert hashlib.sha256((ROOT / record['path']).read_bytes()).hexdigest() == record['sha256']
    report = dict(schema=1, base_commit=inputs['base_commit'], branch='dev/experimental-plain-wire-batching',
                  full_suite=False, graphical_acceptance=False, capacity_certified=False,
                  frozen_inputs=evidence(frozen / 'inputs.json'), artifacts=inputs['artifacts'],
                  source_inventory_verified=True, jar_diff=read(frozen / 'jar-diff.json'),
                  prior_experiment=evidence(ROOT / 'docs/experimental/plain-wire-results.json'),
                  benchmarks=[benchmark(path) for path in sorted((ROOT / 'local/benchmarks').glob('property-*/experiment.json'))],
                  focused_checks=[])
    for path in sorted((ROOT / 'local/checks').glob('property-*/result.json')):
        result = read(path)
        observed = None
        if result['passed']:
            log = (path.parent / 'process.log').read_text(encoding='utf-8', errors='replace')
            matches = re.findall(r'WWPG experimental plain-wire batch: verify=true, .*?, properties=(true|false), propertyContributions=(\d+), propertyScalarFallbacks=(\d+), largestPropertyBatch=(\d+)', log)
            assert len(matches) == 1, path
            enabled, fast, fallback, largest = matches[0]
            assert (enabled == 'true') == result['property_batch'], path
            observed = dict(enabled=enabled == 'true', property_contributions=int(fast),
                            property_scalar_fallbacks=int(fallback), largest_property_batch=int(largest), scalar_verification=True)
            if not result['property_batch']:
                assert int(fast) == int(fallback) == int(largest) == 0, path
            elif result['namespaces'] == 'wwpg_wire_power':
                assert int(fast) > 0 and int(largest) > 0, path
        report['focused_checks'].append(dict(name=path.parent.name, evidence=evidence(path), backend=result['backend'],
                                             namespaces=result['namespaces'], property_batch=result['property_batch'],
                                             passed=result['passed'], required_tests_passed=result['required_tests_passed'],
                                             observed_batch_execution=observed,
                                             artifacts_unchanged=result['artifacts_unchanged'], artifacts=result['artifacts']))
    report['passing_focused_test_count'] = sum(item['required_tests_passed'] for item in report['focused_checks'] if item['passed'])
    report['limitations'] = [
        'Short focused copied-save controls; no capacity or release acceptance.',
        'Matrix row and hook order differ across JVM launches and remain stable within each measured run.',
        'Detailed timing and scalar verification runs are not clean performance measurements.',
        'This extends only exact WWPG PropertyCurrentSourceWire power aggregation; RHS, gameplay and native solver code are unchanged.'
    ]
    output = ROOT / 'docs/experimental/property-wire-results.json'
    output.write_text(json.dumps(report, indent=2) + '\n', newline='\n')
    print(json.dumps(dict(report=str(output), benchmarks=len(report['benchmarks']),
                          passing_focused_checks=report['passing_focused_test_count'])))


if __name__ == '__main__':
    main()
