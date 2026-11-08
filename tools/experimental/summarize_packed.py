"""Verify frozen inputs and summarize only the named copied-save experiments."""
import argparse
import csv
import hashlib
import json
import re
from pathlib import Path
import statistics

ROOT = Path(__file__).resolve().parents[2]
def read(path): return json.loads(path.read_text())
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def evidence(path): return dict(path=path.relative_to(ROOT).as_posix(), sha256=sha(path))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempts', nargs='+', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--contended-attempts', nargs='*', default=[])
    parser.add_argument('--reuse-evidence', help='Explicit source/artifact comparison for reused focused checks')
    args = parser.parse_args()
    frozen = ROOT / 'local/frozen/prototype-03d/inputs.json'
    inputs = read(frozen)
    for item in inputs['sources']:
        assert sha(ROOT / item['path']) == item['sha256'], item['path']
    for item in inputs['artifacts'].values(): assert sha(Path(item['path'])) == item['sha256']
    checks = []
    for path in sorted((ROOT / 'local/checks').glob('packed-03*/result.json')):
        result = read(path)
        checks.append(dict(name=path.parent.name, passed=result['passed'], backend=result['backend'],
                           workers=result.get('workers'), tests=result['required_tests_passed'],
                           packed_execution=result.get('packed_execution'), evidence=evidence(path)))
    final_checks = [c for c in checks if c['name'].startswith('packed-03d-')]
    assert len(final_checks) == 6 and all(c['passed'] and c['tests'] == 43 for c in final_checks)
    reuse = None
    if args.reuse_evidence:
        path = (ROOT / args.reuse_evidence).resolve()
        assert path.is_relative_to(ROOT)
        comparison = read(path)
        assert comparison['sources_verified'] == len(inputs['sources'])
        assert comparison['prior_test_passes'] == sum(c['tests'] for c in final_checks)
        for key, item in inputs['artifacts'].items():
            assert comparison['artifacts'][key]['sha256'] == item['sha256']
        assert len(comparison['prior_checks']) == len(final_checks)
        for item in comparison['prior_checks']: assert sha(ROOT / item['path']) == item['sha256']
        reuse = evidence(path)
    attempts = []
    for name in args.attempts:
        directory = (ROOT / 'local/benchmarks' / name).resolve()
        assert directory.is_relative_to((ROOT / 'local/benchmarks').resolve())
        protocol = read(directory / 'experiment.json')
        assert protocol['validation']['passed'] and protocol['original_reference_unchanged']
        assert protocol['runtime']['sha256'] == inputs['artifacts']['runtime']['sha256']
        log = (directory / 'process.log').read_text(encoding='utf-8', errors='replace')
        execution = re.findall(r'WWPG experimental packed results: enabled=(true|false), verify=(true|false), workers=(\d+), power=(\d+), currents=(\d+), voltages=(\d+), dispatches=(\d+), ranges=(\d+), verified=(\d+)', log)
        assert len(execution) == 1
        actual = execution[0]
        assert (actual[0] == 'true') == protocol['packed'] and (actual[1] == 'true') == protocol['verify_packed']
        assert int(actual[2]) == protocol['workers']
        if not protocol['packed']: assert all(int(n) == 0 for n in actual[3:])
        for item in read(directory / 'launch.json')['profile_inputs']: assert sha(Path(item['path'])) == item['sha256']
        with (directory / 'benchmark/placement-ticks.csv').open() as stream:
            ticks = [r for r in csv.DictReader(stream) if r['phase'] == 'STABLE']
        summary = protocol['summary']
        wall_start, wall_end = float(ticks[0]['wall_unix_ms']) / 1000, float(ticks[-1]['wall_unix_ms']) / 1000
        process = [json.loads(line) for line in (directory / 'process-metrics.jsonl').read_text().splitlines()]
        process = [r for r in process if wall_start <= r['wall_unix'] <= wall_end]
        cpu_cores = ((process[-1]['cpu_seconds'] - process[0]['cpu_seconds']) /
                     (process[-1]['wall_unix'] - process[0]['wall_unix'])) if len(process) > 1 else None
        blocks = {}
        for row in ticks:
            block = int((float(row['wall_unix_ms']) / 1000 - wall_start) // 30)
            blocks.setdefault(block, []).append(row)
        block_means = [dict(block=block, ticks=len(part),
                           mean_ms=statistics.mean(int(r['wall_tick_ns']) for r in part)/1e6)
                       for block, part in sorted(blocks.items())]
        attempts.append(dict(name=name, packed=protocol['packed'], workers=protocol['workers'],
            host_contention=name in args.contended_attempts,
            comparable_to_clean_control=name not in args.contended_attempts and not protocol['verify_packed'],
            verification=protocol['verify_packed'], detailed=protocol['detailed'], tps=summary['tps'],
            samples=summary['samples'], mspt=summary['mspt'], target_met=summary['practical_20tps_target_met'],
            mean_gap_to_50_ms=summary['mspt']['mean'] - 50,
            server_cpu_mean_ms=statistics.mean(int(r['cpu_tick_ns']) for r in ticks)/1e6,
            server_allocation_mean_mib=statistics.mean(int(r['allocated_bytes']) for r in ticks)/1048576,
            process_mean_cpu_cores=cpu_cores, gc_ms=summary['gc_ms'],
            compilation_ms_delta=int(ticks[-1]['compile_ms']) - int(ticks[0]['compile_ms']),
            thirty_second_block_means=block_means,
            validation=protocol['validation'], started_utc=protocol['started_utc'], finished_utc=protocol['finished_utc'],
            evidence=[evidence(directory / p) for p in ('experiment.json', 'process.log', 'process-metrics.jsonl',
                'launch.json', 'benchmark/result.json', 'benchmark/ticks.csv', 'benchmark/summary.json',
                'benchmark/placement-ticks.csv', 'benchmark/graph-start.json', 'benchmark/graph-end.json',
                'benchmark/pre-solve-storage.json', 'shutdown-save.json')]))
    reference = read(ROOT / 'local/inputs/reference-copy.json')
    for item in reference['files']:
        assert sha(Path(reference['source']) / item['path']) == item['sha256']
        assert sha(Path(reference['destination']) / item['path']) == item['sha256']
    result = dict(schema=1, scope='Focused experimental checks only; no full or graphical acceptance',
        frozen_inputs=inputs, frozen_manifest=evidence(frozen), native_comparison=read(frozen.parent / 'jar-diff.json'),
        source_and_artifact_hashes_verified=True, original_and_copied_reference_hashes_verified=True,
        focused_checks=checks, final_required_test_passes=sum(c['tests'] for c in final_checks),
        focused_check_evidence_mode='reused' if reuse else 'recorded', evidence_reuse_comparison=reuse,
        standalone_check=dict(log=evidence(ROOT / 'local/packed-unit/result-02.log'),
                              result=(ROOT / 'local/packed-unit/result-02.log').read_text()), attempts=attempts,
        host_observations=[evidence(p) for p in sorted((ROOT / 'local/packed-host').glob('*.json'))])
    output = ROOT / args.output
    with output.open('x', encoding='utf-8') as stream: json.dump(result, stream, indent=2); stream.write('\n')
    print(json.dumps([dict(name=a['name'], tps=a['tps'], mean=a['mspt']['mean'], p95=a['mspt']['p95'],
                           server_cpu_ms=a['server_cpu_mean_ms']) for a in attempts], indent=2))

if __name__ == '__main__': main()
