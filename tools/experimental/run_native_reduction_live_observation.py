"""Observe periodic native path counts without depending on final solver cleanup."""
import argparse
import json
from pathlib import Path

from cpu_profile import write_new
from plain_wire_benchmark import main as benchmark
from run_native_profile_checks import windows_path
from run_native_reduction_observation import KIND, inspect_runtime as inspect_counts, parse_counts
from summarize_tree import ROOT, evidence, read, sha


def inspect_runtime(path, enabled):
    marker = inspect_counts(path, enabled)
    build_path = windows_path(marker['native_build']['path'])
    assert sha(build_path) == marker['native_build']['sha256']
    build = read(build_path)
    assert build['periodic_report_per_path_calls'] == 4096
    assert build['original_observer_source_restored'] and build['original_candidate_source_restored']
    return marker


def validate(directory):
    protocol = read(directory / 'experiment.json')
    assert protocol['diagnostic_kind'] == KIND and protocol['detailed'] and protocol['native_work']
    assert not protocol['native_stages'] and not protocol['cpu_jfr'] and not protocol['substep_jfr']
    assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
    assert protocol['validation']['passed'] and protocol['validation']['stable_ticks'] > 0
    assert read(directory / 'runner-exit.json')['exit_code'] == 0
    assert read(directory / 'benchmark/result.json')['passed']
    assert read(directory / 'shutdown-save.json')['all_dimensions_saved']
    launch = read(directory / 'launch.json')
    assert sha(Path(launch['runtime']['path'])) == protocol['runtime']['sha256'] == launch['runtime']['sha256']
    marker = inspect_runtime(launch['runtime']['path'], False)
    native = list((directory / '.pg-native/wwpg-safety-1').rglob('*.dll'))
    assert len(native) == 1 and sha(native[0]) == marker['native_sha256']
    for item in launch['runner_sources']: assert sha(Path(item['path'])) == item['sha256']
    observations = parse_counts((directory / 'process.log').read_text(errors='replace'))
    last = observations[-1]
    assert sum(last.values()) >= 4096
    result = dict(schema=1, diagnostic=True, diagnostic_kind=KIND, passed=True,
        tracked_rows=14925, cumulative_observations=len(observations), last_cumulative_counts=last,
        periodic_report_per_path_calls=4096,
        fallback_fraction={name: last[name + 'Fallback'] / (last[name + 'Fast'] + last[name + 'Fallback']) for name in ('finite', 'norm')},
        loaded_native=evidence(native[0]), marker=marker, native_work_observation=protocol['native_work_observation'],
        evidence=[evidence(directory / name) for name in ('experiment.json', 'launch.json', 'process.log', 'runner-exit.json', 'shutdown-save.json')],
        limitations=['Counts accumulate from process startup through the last report, including loading and warmup; trailing calls may be omitted.',
            'Periodic reports occur every 4096 calls of each path and at solver teardown; these are not stable-only counts.',
            'Counters do not distinguish subnormal from nonfinite data or identify the calling substep.',
            'Detailed timers, atomic counters and sparse logging are enabled; timings are not a clean comparison.'])
    write_new(directory / 'native-reduction-paths.json', result)
    return result


def main():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument('--name', required=True)
    args, _ = parser.parse_known_args()
    benchmark(runtime_inspector=inspect_runtime, diagnostic_kind=KIND)
    result = validate(ROOT / 'local/benchmarks' / args.name)
    print(json.dumps(dict(path_counts=result['last_cumulative_counts'], fallback_fraction=result['fallback_fraction'])))


if __name__ == '__main__':
    main()
