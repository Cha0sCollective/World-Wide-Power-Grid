"""Summarize retained experimental attempts without launching or relabelling tests."""
import csv
import hashlib
import itertools
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path):
    return json.loads(path.read_text())


def evidence(path):
    return dict(path=path.relative_to(ROOT).as_posix(), sha256=hashlib.sha256(path.read_bytes()).hexdigest())


def main():
    report = dict(schema=1, base_commit='cca3c09b36059cc44ec04cc2f03eea449936a460',
                  branch='dev/experimental-plain-wire-batching', full_suite=False, graphical_acceptance=False,
                  capacity_certified=False, benchmarks=[], focused_checks=[])
    frozen = ROOT / 'local/frozen/prototype-01'
    report['frozen_inputs'] = evidence(frozen / 'inputs.json')
    report['artifacts'] = read(frozen / 'inputs.json')['artifacts']
    report['jar_diff'] = read(frozen / 'jar-diff.json')
    for file in sorted((ROOT / 'local/benchmarks').glob('*/experiment.json')):
        protocol = read(file)
        if 'finished_utc' not in protocol:
            raise RuntimeError(f'Attempt still running: {file.parent.name}')
        entry = dict(name=protocol['name'], evidence=evidence(file), started_utc=protocol['started_utc'],
                     finished_utc=protocol['finished_utc'], batch=protocol['batch'], detailed=protocol['detailed'],
                     verify_batch=protocol['verify_batch'], original_reference_unchanged=protocol['original_reference_unchanged'],
                     runtime_sha256=protocol['runtime']['sha256'], validation=protocol.get('validation'))
        entry['launch_evidence'] = evidence(file.parent / 'launch.json')
        entry['process_log_evidence'] = evidence(file.parent / 'process.log')
        if 'summary' in protocol:
            summary = protocol['summary']
            entry.update(tps=summary['tps'], mspt=summary['mspt'], stable_ticks=summary['samples'],
                         gc_ms=summary['gc_ms'], process=summary['process'], audit=summary['audit'],
                         restoration=read(file.parent / 'benchmark/loading-results.json')[0],
                         tick_evidence=evidence(file.parent / 'benchmark/ticks.csv'))
            graph = read(file.parent / 'benchmark/graph-start.json')[0]
            entry['assigned_row_order_sha256'] = graph['assigned_row_order_sha256']
            entry['hook_types'] = graph['multiHooks']['types']
            native = list((file.parent / '.pg-native/wwpg-safety-1').glob('*/libpowergridNative7.dll'))
            assert len(native) == 1
            entry['loaded_native_resource'] = evidence(native[0])
            entry['placement'] = read(file.parent / 'benchmark/placement-fixed.json')
            if protocol['detailed']:
                substeps = file.parent / 'benchmark/substeps.csv'
                with substeps.open() as stream:
                    rows = list(csv.DictReader(stream))
                for tick, group in itertools.groupby(rows, key=lambda row: row['tick']):
                    trace = list(group)
                    assert [int(row['step']) for row in trace] == list(range(1, 17)), tick
                    assert all(row['loop_mask'] == '7' and row['overflow'] == '0' and row['rejected'] == '0' for row in trace), tick
                entry['complete_sixteen_step_traces'] = True
                names = ['pg_micro_hooks_ns', 'pg_pre_hooks_ns', 'pg_post_hooks_ns', 'rhs_ns', 'native_solve_ns',
                         'history_prepare_ns', 'history_commit_ns', 'native_readings_ns']
                entry['profiled_ticks'] = len(rows) // 16
                entry['substep_evidence'] = evidence(substeps)
                entry['profiled_mean_ms_per_tick'] = {key.removesuffix('_ns'): sum(int(r[key]) for r in rows) / (len(rows) / 16) / 1e6 for key in names}
        else:
            entry['classification'] = 'Failed launch; no benchmark pass or performance sample'
        report['benchmarks'].append(entry)
    for file in sorted((ROOT / 'local/checks').glob('*/result.json')):
        result = read(file)
        report['focused_checks'].append(dict(name=file.parent.name, evidence=evidence(file), backend=result['backend'],
                                             namespaces=result['namespaces'], passed=result['passed'],
                                             required_tests_passed=result['required_tests_passed'], artifacts_unchanged=result['artifacts_unchanged']))
    report['limitations'] = [
        'Short focused controls, not capacity or release acceptance.',
        'Assigned matrix row and hook iteration order vary across fresh JVM processes; they are stable within each measured run.',
        'Verification and detailed instrumentation add overhead; keep those runs separate from clean timing comparisons.',
        'Loading times are observational; this prototype does not change chunk I/O or graph construction.',
        'Opt-in serial experiment on copied saves; no worker threads or native binary changes.'
    ]
    output = ROOT / 'docs/experimental/plain-wire-results.json'
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(dict(report=str(output), benchmarks=len(report['benchmarks']), focused_checks=len(report['focused_checks']))))


if __name__ == '__main__':
    main()
