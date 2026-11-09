"""Run a distinctly marked diagnostic counting full-sized native reduction paths."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

from cpu_profile import write_new
from native_stages import MARKER, WINDOWS_DLL
from plain_wire_benchmark import main as benchmark
from summarize_tree import ROOT, evidence, read, sha

KIND = 'native-reduction-counts'
FIELDS = ('finiteFast', 'finiteFallback', 'normFast', 'normFallback')
PATTERN = re.compile(r'WWPG_NATIVE_REDUCTION_PATHS rows=14925 finiteFast=(\d+) finiteFallback=(\d+) normFast=(\d+) normFallback=(\d+)')


def inspect_runtime(path, enabled):
    assert not enabled, 'Path counts do not expose the C-stage snapshot ABI'
    with zipfile.ZipFile(path) as jar:
        marker = json.loads(jar.read(MARKER))
        assert marker['schema'] == 1 and marker['diagnostic'] is True
        assert marker['diagnostic_kind'] == KIND and marker['tracked_rows'] == 14925
        assert 'profile_magic' not in marker and 'profile_stages' not in marker
        assert marker['native_sha256'] == hashlib.sha256(jar.read(WINDOWS_DLL)).hexdigest()
        assert re.findall(r'windows\.sha256=([0-9a-f]{64})', jar.read('native/wwpg/safe/guarded.properties').decode()) == [marker['native_sha256']]
        return marker


def parse_counts(log):
    observations = [dict(zip(FIELDS, map(int, match))) for match in PATTERN.findall(log)]
    assert observations, 'No teardown path counts were emitted'
    for before, after in zip(observations, observations[1:]):
        assert all(after[key] >= before[key] for key in FIELDS), 'Cumulative counters moved backwards'
    last = observations[-1]
    assert last['finiteFast'] + last['finiteFallback'] > 0
    assert last['normFast'] + last['normFallback'] > 0
    return observations


def validate(directory):
    protocol = read(directory / 'experiment.json')
    assert protocol['diagnostic_kind'] == KIND and protocol['detailed'] and protocol['native_work']
    assert not protocol['native_stages'] and not protocol['cpu_jfr'] and not protocol['substep_jfr']
    assert protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
    assert protocol['validation']['stable_ticks'] > 0
    assert read(directory / 'runner-exit.json')['exit_code'] == 0
    assert read(directory / 'benchmark/result.json')['passed']
    assert read(directory / 'shutdown-save.json')['all_dimensions_saved']
    launch = read(directory / 'launch.json')
    assert sha(Path(launch['runtime']['path'])) == protocol['runtime']['sha256'] == launch['runtime']['sha256']
    marker = inspect_runtime(launch['runtime']['path'], False)
    native = list((directory / '.pg-native/wwpg-safety-1').rglob('*.dll'))
    assert len(native) == 1 and sha(native[0]) == marker['native_sha256']
    for item in launch['runner_sources']:
        assert sha(Path(item['path'])) == item['sha256']
    log_path = directory / 'process.log'
    observations = parse_counts(log_path.read_text(errors='replace'))
    last = observations[-1]
    result = dict(schema=1, diagnostic=True, diagnostic_kind=KIND, passed=True,
        tracked_rows=14925, teardown_observations=len(observations), last_cumulative_counts=last,
        fallback_fraction={name: last[name + 'Fallback'] / (last[name + 'Fast'] + last[name + 'Fallback']) for name in ('finite', 'norm')},
        loaded_native=evidence(native[0]), marker=marker,
        native_work_observation=protocol['native_work_observation'],
        evidence=[evidence(directory / name) for name in ('experiment.json', 'launch.json', 'process.log', 'runner-exit.json', 'shutdown-save.json')],
        limitations=['Counts accumulate from process startup through the last solver teardown, including loading and warmup.',
            'The counters do not distinguish subnormal data from nonfinite data or identify the calling substep.',
            'Detailed timers and atomic counters are enabled; these timings are not a clean comparison.'])
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
