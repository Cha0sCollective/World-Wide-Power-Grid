"""Verify and summarize the completed native residual-cache comparison."""
import argparse
import ast
import csv
import json
from pathlib import Path
import re
import subprocess
import zipfile

from cpu_profile import write_new
from native_stages import inspect_runtime
from run_native_profile_checks import windows_path
from summarize_goal_runs import capture
from summarize_tree import ROOT,evidence,read,sha


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--controls',nargs='+',required=True)
    parser.add_argument('--candidates',nargs='+',required=True)
    parser.add_argument('--build',default='local/native-residual-runtime-02/build.json')
    parser.add_argument('--control-inputs',default='local/frozen/native-residual-control-01/inputs.json')
    parser.add_argument('--candidate-inputs',default='local/frozen/native-residual-candidate-01/inputs.json')
    parser.add_argument('--helper-inputs',default='local/frozen/affinity-helper-02/inputs.json')
    parser.add_argument('--focused-checks',nargs='+',default=['local/checks/native-residual-control-01/result.json','local/checks/native-residual-candidate-01/result.json'])
    parser.add_argument('--output',required=True)
    parser.add_argument('--verification-output',required=True)
    parser.add_argument('--verify-existing-result',action='store_true')
    args=parser.parse_args()
    verified={}
    def refs(value):
        if isinstance(value,dict):
            if 'path' in value and 'sha256' in value:
                path=windows_path(value['path'])
                if not path.is_absolute(): path=ROOT/path
                key=str(path.resolve())
                if key in verified: assert verified[key]==value['sha256'],key
                else:
                    assert path.is_file() and sha(path)==value['sha256'],key
                    verified[key]=value['sha256']
            for nested in value.values(): refs(nested)
        elif isinstance(value,list):
            for nested in value: refs(nested)
    def record(path):
        value=read(path); refs(value); return value

    build_path=ROOT/args.build; build=record(build_path); native=record(build_path.parent/'checks.json')
    assert native['passed'] and native['artifacts_unchanged'] and native['build']['sha256']==sha(build_path)
    assert native['observations']['candidate']['allocation_failures']==17
    frozen={role:record(ROOT/path) for role,path in (('control',args.control_inputs),('candidate',args.candidate_inputs))}
    helper=record(ROOT/args.helper_inputs)
    retained_path=ROOT/'local/frozen/prototype-12g/inputs.json'; retained=record(retained_path)
    fixtures_path=ROOT/frozen['candidate']['fixture_inputs']['path']; fixtures=record(fixtures_path)
    assert fixtures['added_test_methods']==4 and fixtures['unchanged_original_entries']
    assert len({item['artifacts']['fixtures']['sha256'] for item in frozen.values()})==1
    for role,item in frozen.items():
        assert item['gameplay_classes_unchanged'] and item['gameplay_classes_compared']==294
        marker=inspect_runtime(item['artifacts']['runtime']['path'],False)
        assert marker['variant']=='native-residual-'+role and marker['native_build']['sha256']==sha(build_path)
        assert marker['native_sha256']==build['artifacts'][role]['dll']['sha256']
        with zipfile.ZipFile(retained['artifacts']['runtime']['path']) as old,zipfile.ZipFile(item['artifacts']['runtime']['path']) as new:
            classes=[name for name in old.namelist() if name.endswith('.class')]
            assert len(classes)==294 and all(old.read(name)==new.read(name) for name in classes)
    focused=[]
    for name in args.focused_checks:
        path=ROOT/name; proof=record(path)
        assert proof['passed'] and proof['required_tests_passed']==24 and proof['backend']=='NATIVE' and proof['artifacts_unchanged']
        runtime=Path(proof['artifacts']['runtime']['path']); marker=inspect_runtime(runtime,False)
        dlls=list((path.parent/'server/.pg-native/wwpg-safety-1').rglob('*.dll'))
        assert len(dlls)==1 and sha(dlls[0])==marker['native_sha256']
        focused.append(dict(result=evidence(path),selected_tests=24,loaded_native=evidence(dlls[0]),runtime_sha256=sha(runtime),log=evidence(path.parent/'process.log')))
    assert {item['runtime_sha256'] for item in focused}=={item['artifacts']['runtime']['sha256'] for item in frozen.values()}
    runs={}
    for role,names in (('control',args.controls),('candidate',args.candidates)):
        runs[role]=[]
        for name in names:
            run=capture(name); folder=ROOT/'local/benchmarks'/name
            assert run['protocol']['runtime']['sha256']==frozen[role]['artifacts']['runtime']['sha256']
            assert run['protocol']['helper']['sha256']==helper['helper']['sha256']
            assert all(not run['protocol'][key] for key in ('detailed','substep_jfr','cpu_jfr','native_work','native_stages','preparation_sections'))
            assert read(folder/'runner-exit.json')['exit_code']==0 and read(folder/'cpu-preflight.json')['quiet']
            assert read(folder/'shutdown-save.json')['all_dimensions_saved']
            dlls=list((folder/'.pg-native/wwpg-safety-1').rglob('*.dll'))
            assert len(dlls)==1 and sha(dlls[0])==build['artifacts'][role]['dll']['sha256']
            with (folder/'benchmark/ticks.csv').open() as stream: ticks=[row for row in csv.DictReader(stream) if row['phase']=='STABLE']
            with (folder/'benchmark/placement-ticks.csv').open() as stream: placed=[row for row in csv.DictReader(stream) if row['phase']=='STABLE']
            run['percent_ticks_above_50_ms']=100*sum(int(row['tick_ns'])>50000000 for row in ticks)/len(ticks)
            run['compilation_ms']=int(placed[-1]['compile_ms'])-int(placed[0]['compile_ms'])
            run['loaded_native']=evidence(dlls[0]); run['evidence'].append(evidence(folder/'cpu-preflight.json'))
            refs(run); runs[role].append(run)
    metrics={role:{key:sum(run['summary']['mspt'][key] for run in data)/len(data) for key in ('mean','p50','p95','p99','maximum')} for role,data in runs.items()}
    difference={key:metrics['candidate'][key]-metrics['control'][key] for key in metrics['control']}
    artifacts=[]
    for role in ('runtime','fixtures'):
        path=ROOT/'build/libs'/Path(retained['artifacts'][role]['path']).name
        assert sha(path)==retained['artifacts'][role]['sha256']; artifacts.append(evidence(path))
    result=dict(schema=1,snapshot='2026-11-09',native_candidate='Per-solver four-row SIMD residual cache with independent value invalidation',
        retained=False,full_suite=False,graphical_acceptance=False,clean_comparison=True,
        common_control_change='Resolve current logical A at every residual and request fresh factorization when a fitting-loop structure update discarded old factors.',
        gameplay_classes_compared=294,native_checks=native,focused_checks=focused,focused_fixture_executions=sum(item['selected_tests'] for item in focused),
        runs=runs,mean_of_run_metrics_ms=metrics,candidate_minus_control_ms=difference,
        candidate_mean_change_percent=100*difference['mean']/metrics['control']['mean'],
        retained_inputs=evidence(retained_path),retained_build_artifacts=artifacts,
        evidence=[evidence(ROOT/path) for path in (args.build,args.control_inputs,args.candidate_inputs,args.helper_inputs)]+[evidence(fixtures_path)],
        limitations=['One ordered pair does not establish repeatability or a consistent 20 TPS result.',
            'The model-switch interval separated the control and candidate by about an hour; host and thermal conditions are not matched.',
            'The cache applies to the private native matrix written through reviewed JNI command, clear and resize paths; arbitrary external native memory writes are outside that API.',
            'Steady timings include cache overhead, and headless loading includes cache construction; setup/refresh costs are not separately isolated here.',
            'No new client load, full-yard reload, full headless acceptance or graphical acceptance was run.',
            'The retained prototype-12g gameplay build is unchanged; these native-only jars remain experimental.'])
    output=ROOT/args.output
    if args.verify_existing_result: assert read(output)==result
    else: write_new(output,result)
    refs(result)
    branch=subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()
    assert branch=='dev/experimental-plain-wire-batching'
    main_status=subprocess.check_output(['git','-C',str(ROOT.parents[1]),'status','--porcelain'],text=True).splitlines()
    assert all(line.startswith('?? ') for line in main_status)
    subprocess.run(['git','diff','--check'],cwd=ROOT,check=True)
    links=0
    for name in ('NATIVE_RESIDUAL_RUNTIME.md','NATIVE_RESIDUAL_LAYOUT.md','PERFORMANCE_GOAL.md'):
        path=ROOT/'docs/experimental'/name
        for link in re.findall(r'\]\(([^)]+)\)',path.read_text(encoding='utf-8')):
            if '://' in link or link.startswith('#'): continue
            assert (path.parent/link.split('#')[0]).exists(),(name,link); links+=1
    scripts=['build_native_residual_runtime.py','native_residual_runtime.py','freeze_native_residual_fixtures.py','summarize_native_residual_runtime.py']
    for name in scripts: ast.parse((ROOT/'tools/experimental'/name).read_text(encoding='utf-8'))
    verification=dict(schema=1,passed=True,branch=branch,verified_file_references=len(verified),retained_source_hashes=len(retained['sources']),
        retained_build_artifacts=artifacts,main_tracked_worktree_clean=True,main_untracked_entries=len(main_status),
        local_document_links_checked=links,python_syntax_files=len(scripts),full_suite=False,graphical_acceptance=False,
        result=evidence(output),tool=evidence(Path(__file__)),
        note='Checks frozen artifacts, raw measurement summaries, native identity, saved-world validation and source integrity. Preserved check counts are not relabeled as new executions.')
    write_new(ROOT/args.verification_output,verification)
    print(json.dumps(dict(passed=True,mean_ms=metrics,candidate_minus_control_ms=difference,verified_refs=len(verified),
        runs={role:[dict(name=run['name'],tps=run['summary']['tps'],mspt=run['summary']['mspt'],loading_seconds=run['loading'][0]['total_restoration_seconds'],
            above_50_percent=run['percent_ticks_above_50_ms']) for run in data] for role,data in runs.items()})))


if __name__=='__main__': main()
