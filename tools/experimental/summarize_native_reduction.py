"""Compare completed clean native control/candidate runs with frozen artifacts."""
import argparse
import json
from pathlib import Path
import zipfile

from cpu_profile import write_new
from native_stages import inspect_runtime
from summarize_goal_runs import capture
from summarize_profile import rows
from summarize_tree import ROOT, evidence, read, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--controls',nargs='+',required=True)
    parser.add_argument('--candidates',nargs='+',required=True)
    parser.add_argument('--control-inputs',required=True)
    parser.add_argument('--candidate-inputs',required=True)
    parser.add_argument('--helper-inputs',required=True)
    parser.add_argument('--native-checks',required=True)
    parser.add_argument('--focused-checks',nargs='+',required=True)
    parser.add_argument('--output',required=True)
    args = parser.parse_args()
    input_paths = [ROOT / p for p in (args.control_inputs,args.candidate_inputs,args.helper_inputs,args.native_checks)]
    control,candidate,helper,numeric = map(read,input_paths)
    assert numeric['passed'] and numeric['artifacts_unchanged']
    assert control['artifacts']['fixtures']['sha256'] == candidate['artifacts']['fixtures']['sha256']
    for frozen in (control,candidate):
        assert frozen['gameplay_classes_unchanged'] and not frozen['native_unchanged']
        assert all(sha(ROOT / p['path']) == p['sha256'] for p in frozen['sources'])
        assert all(sha(Path(p['path'])) == p['sha256'] for p in frozen['artifacts'].values())
    assert sha(Path(helper['helper']['path'])) == helper['helper']['sha256']
    assert all(sha(ROOT / p['path']) == p['sha256'] for p in helper['sources'])
    with zipfile.ZipFile(control['artifacts']['runtime']['path']) as old, zipfile.ZipFile(candidate['artifacts']['runtime']['path']) as new:
        assert set(old.namelist()) == set(new.namelist())
        changed = sorted(n for n in old.namelist() if old.read(n) != new.read(n))
        assert len(changed) == 3 and all(n.startswith('native/wwpg/safe/') for n in changed)
        classes = [n for n in old.namelist() if n.endswith('.class')]
        assert all(old.read(n) == new.read(n) for n in classes)
    check_paths = [ROOT / p for p in args.focused_checks]
    checks = [read(p) for p in check_paths]
    assert all(c['passed'] and c['required_tests_passed'] == 20 and c['backend'] == 'NATIVE' for c in checks)
    assert {c['artifacts']['runtime']['sha256'] for c in checks} == {f['artifacts']['runtime']['sha256'] for f in (control,candidate)}
    results = {}
    for role,names,frozen in (('control',args.controls,control),('candidate',args.candidates,candidate)):
        observed = []
        for name in names:
            run = capture(name)
            folder = ROOT / 'local/benchmarks' / name
            assert run['protocol']['runtime']['sha256'] == frozen['artifacts']['runtime']['sha256']
            assert run['protocol']['helper']['sha256'] == helper['helper']['sha256']
            assert all(not run['protocol'][k] for k in ('detailed','substep_jfr','cpu_jfr','native_work','native_stages','preparation_sections'))
            assert read(folder / 'runner-exit.json')['exit_code'] == 0
            assert read(folder / 'cpu-preflight.json')['quiet']
            marker = inspect_runtime(frozen['artifacts']['runtime']['path'],False)
            native = list((folder / '.pg-native/wwpg-safety-1').rglob('*.dll'))
            assert len(native) == 1 and sha(native[0]) == marker['native_sha256']
            stable = [r for r in rows(folder / 'benchmark/ticks.csv') if r['phase'] == 'STABLE']
            placement = [r for r in rows(folder / 'benchmark/placement-ticks.csv') if r['phase'] == 'STABLE']
            longest = current = 0
            for row in stable:
                current = current + 1 if int(row['tick_ns']) > 50_000_000 else 0
                longest = max(longest,current)
            run['ticks_above_50_ms'] = sum(int(r['tick_ns']) > 50_000_000 for r in stable)
            run['percent_ticks_above_50_ms'] = 100 * run['ticks_above_50_ms'] / len(stable)
            run['longest_consecutive_ticks_above_50_ms'] = longest
            run['compilation_ms'] = int(placement[-1]['compile_ms']) - int(placement[0]['compile_ms'])
            run['loaded_native'] = evidence(native[0])
            run['evidence'].append(evidence(folder / 'cpu-preflight.json'))
            observed.append(run)
        results[role] = observed
    aggregate = {role:{metric:sum(r['summary']['mspt'][metric] for r in runs)/len(runs)
        for metric in ('mean','p50','p95','p99','maximum')} for role,runs in results.items()}
    difference = {metric:aggregate['candidate'][metric] - aggregate['control'][metric] for metric in aggregate['control']}
    record = dict(schema=1, full_suite=False, graphical_acceptance=False,
        native_candidate='exact finite/max reduction with original scalar fallback for exceptional/subnormal inputs',
        gameplay_classes_compared=len(classes),changed_control_candidate_entries=changed,
        clean_comparison=True,runs=results,mean_of_run_metrics_ms=aggregate,candidate_minus_control_ms=difference,
        numeric_checks=numeric,focused_fixture_executions=sum(c['required_tests_passed'] for c in checks),
        source_hashes_verified=len(control['sources']),
        evidence=[evidence(p) for p in input_paths + check_paths + [Path(__file__)]],
        limitations=['Same-compiler control and candidate; neither native library is the earlier retained release compiler output.',
            'Run metrics are reported individually and averaged equally, without treating a single pair as repeatability evidence.',
            'The standalone microbenchmark is not game throughput; its subnormal path is slower.',
            'No client loading or full acceptance is established by this steady comparison.'])
    write_new(ROOT / args.output,record)
    print(json.dumps(dict(metrics=aggregate,candidate_minus_control_ms=difference,
        runs={role:[dict(name=r['name'],tps=r['summary']['tps'],mspt=r['summary']['mspt'],
            over_50_percent=r['percent_ticks_above_50_ms'],compile_ms=r['compilation_ms']) for r in runs] for role,runs in results.items()})))


if __name__ == '__main__':
    main()
