"""Publish a verified checkpoint for the unretained reduction experiment."""
import argparse
from collections import defaultdict
import csv
import json
from pathlib import Path
import re
import subprocess
import zipfile

from cpu_profile import write_new
from run_native_profile_checks import windows_path
from run_native_reduction_observation import PATTERN
from summarize_tree import ROOT, evidence, read, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--comparison', required=True)
    parser.add_argument('--observation', required=True)
    parser.add_argument('--observer-inputs', required=True)
    parser.add_argument('--observer-checks', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--verification-output', required=True)
    parser.add_argument('--verify-existing-result', action='store_true', help='Compare a preserved result exactly instead of overwriting it')
    args = parser.parse_args()
    comparison_path, observation_path, observer_path, check_path = (
        ROOT / p for p in (args.comparison, args.observation, args.observer_inputs, args.observer_checks))
    comparison, observation, observer, observer_checks = map(read, (comparison_path, observation_path, observer_path, check_path))
    failed_directory = ROOT / 'local/benchmarks/goal-native-reduction-counts-01'
    failed_protocol = read(failed_directory / 'experiment.json')
    failed_counts = [tuple(map(int, row)) for row in PATTERN.findall((failed_directory / 'process.log').read_text(errors='replace'))]
    assert failed_counts and all(not any(row) for row in failed_counts)
    assert failed_protocol['validation']['passed'] and read(failed_directory / 'runner-exit.json')['exit_code'] == 0
    assert not (failed_directory / 'native-reduction-paths.json').exists()
    failed_observation = dict(name=failed_directory.name, counter_validation_passed=False, game_checks_passed=True,
        zero_only_reports=len(failed_counts), reason='The live full-sized solver was not destroyed before process exit; teardown reports were all zero.',
        evidence=[evidence(failed_directory / p) for p in ('experiment.json', 'runner-exit.json', 'process.log')])
    retained_path = ROOT / 'local/frozen/prototype-12g/inputs.json'
    retained = read(retained_path)
    assert comparison['clean_comparison'] and observation['passed'] and observation['diagnostic']
    assert observer_checks['passed'] and observer_checks['required_tests_passed'] == 20
    assert observer_checks['artifacts']['runtime']['sha256'] == observer['artifacts']['runtime']['sha256']
    checked = {}

    def verify_refs(value):
        if isinstance(value, dict):
            if 'path' in value and 'sha256' in value:
                path = windows_path(value['path'])
                if not path.is_absolute(): path = ROOT / path
                assert path.is_file() and sha(path) == value['sha256'], str(path)
                checked[str(path.resolve())] = value['sha256']
            for nested in value.values(): verify_refs(nested)
        elif isinstance(value, list):
            for nested in value: verify_refs(nested)

    for data in (comparison, observation, observer, observer_checks, retained, failed_observation): verify_refs(data)
    variants = {}
    for role, manifest in (('control', 'native-reduction-control-02'),
                           ('candidate', 'native-reduction-candidate-02'),
                           ('observer', args.observer_inputs)):
        path = ROOT / ('local/frozen/' + manifest + '/inputs.json') if role != 'observer' else observer_path
        frozen = read(path)
        verify_refs(frozen)
        runtime = Path(frozen['artifacts']['runtime']['path'])
        with zipfile.ZipFile(retained['artifacts']['runtime']['path']) as original, zipfile.ZipFile(runtime) as candidate:
            classes = [n for n in original.namelist() if n.endswith('.class')]
            assert all(original.read(n) == candidate.read(n) for n in classes)
        variants[role] = dict(inputs=evidence(path), runtime=evidence(runtime),
            gameplay_classes_compared=len(classes), fixture_sha256=frozen['artifacts']['fixtures']['sha256'])
    assert len({v['fixture_sha256'] for v in variants.values()}) == 1
    build_paths = [ROOT / 'local/native-reduction-01/build.json', windows_path(observation['marker']['native_build']['path'])]
    for path in build_paths: verify_refs(read(path))
    build_artifacts = []
    for role in ('runtime', 'fixtures'):
        path = ROOT / 'build/libs' / Path(retained['artifacts'][role]['path']).name
        assert sha(path) == retained['artifacts'][role]['sha256']
        build_artifacts.append(evidence(path))
    runs = {role: [dict(name=run['name'], tps=run['summary']['tps'], mspt=run['summary']['mspt'],
        percent_ticks_above_50_ms=run['percent_ticks_above_50_ms'],
        longest_consecutive_ticks_above_50_ms=run['longest_consecutive_ticks_above_50_ms'],
        compilation_ms=run['compilation_ms'], thirty_second_blocks=run['thirty_second_blocks'],
        host=run['host'], loaded_native=run['loaded_native']) for run in captured]
        for role, captured in comparison['runs'].items()}
    exploratory = []
    for name in ('goal-native-stages-02', 'goal-native-reduction-counts-01', observation_path.parent.name):
        directory = ROOT / 'local/benchmarks' / name
        protocol = read(directory / 'experiment.json')
        expected_calls = protocol['native_work_observation']['sampled_steady_native_calls']
        assert expected_calls > 0
        groups = defaultdict(list)
        with (directory / 'benchmark/native-work.csv').open() as stream:
            for row in csv.DictReader(stream):
                if row['phase'] == 'STABLE':
                    groups[(int(row['iteration_calls']), int(row['residual_calls']))].append(int(row['native_ns']) / 1e6)
        assert sum(map(len, groups.values())) == expected_calls
        exploratory.append(dict(name=name, runtime_sha256=protocol['runtime']['sha256'],
            c_stage_timers=protocol['native_stages'], diagnostic_kind=protocol.get('diagnostic_kind'),
            groups=[dict(iteration_callbacks=k[0], residual_callbacks=k[1], sampled_calls=len(v),
                         unweighted_mean_native_ms=sum(v)/len(v)) for k, v in sorted(groups.items())],
            evidence=[evidence(directory / p) for p in ('experiment.json', 'benchmark/native-work.csv')]))
    verify_refs(exploratory)
    result = dict(schema=1, snapshot='2026-11-09', retained=False, full_suite=False, graphical_acceptance=False,
        conclusion='The conservative finite/max candidate is slower in its first clean pair; retain prototype-12g.',
        clean_runs=runs, candidate_minus_control_ms=comparison['candidate_minus_control_ms'],
        exact_comparisons=comparison['numeric_checks']['exact_comparisons'], fp_modes=16,
        focused_fixture_executions=comparison['focused_fixture_executions'], observer_fixture_executions=20,
        variants=variants, path_observation=observation,
        failed_teardown_observation=failed_observation,
        exploratory_native_calls=exploratory,
        microbenchmark=comparison['numeric_checks']['microbenchmark'],
        evidence=[evidence(p) for p in (comparison_path, observation_path, observer_path, check_path, retained_path, *build_paths)],
        limitations=comparison['limitations'] + observation['limitations'] +
            ['Exploratory native-call means use different diagnostic instrumentation; they are not a matched speedup measurement.'])
    if args.verify_existing_result:
        assert read(ROOT / args.output) == result, 'Existing result differs; preserve it and choose a new output name'
    else:
        write_new(ROOT / args.output, result)
    branch = subprocess.check_output(['git', 'branch', '--show-current'], cwd=ROOT, text=True).strip()
    assert branch == 'dev/experimental-plain-wire-batching'
    main_status = subprocess.check_output(['git', '-C', str(ROOT.parents[1]), 'status', '--porcelain'], text=True).splitlines()
    assert all(line.startswith('?? ') for line in main_status), 'The main worktree has tracked changes'
    subprocess.run(['git', 'diff', '--check'], cwd=ROOT, check=True)
    links = 0
    for name in ('NATIVE_REDUCTION.md', 'PERFORMANCE_GOAL.md'):
        path = ROOT / 'docs/experimental' / name
        for link in re.findall(r'\]\(([^)]+)\)', path.read_text(encoding='utf-8')):
            if '://' in link or link.startswith('#'): continue
            assert (path.parent / link.split('#')[0]).exists(), (name, link)
            links += 1
    verification = dict(schema=1, passed=True, branch=branch, full_suite=False, graphical_acceptance=False,
        verified_file_references=len(checked), retained_source_build_hashes_verified=len(retained['sources']),
        retained_build_artifacts=build_artifacts, variants=variants, main_tracked_worktree_clean=True,
        main_untracked_status_entries=len(main_status),
        local_document_links_checked=links, focused_observer_identity_checks=3,
        result=evidence(ROOT / args.output), tool=evidence(Path(__file__)),
        note='The three focused observer identity/counter unit checks passed before the diagnostic run. Gameplay and numeric counts describe their preserved named runs, not reruns. The first audit rejected historical untracked files in the main worktree; this audit checks tracked cleanliness and leaves those files untouched.')
    write_new(ROOT / args.verification_output, verification)
    print(json.dumps(dict(passed=True, verified_file_references=len(checked), source_hashes=len(retained['sources']),
        path_counts=observation['last_cumulative_counts'], retained=False, result=args.output)))


if __name__ == '__main__':
    main()
