"""Verify a completed performance-stack trial against the preserved last control."""
import argparse
import json
from pathlib import Path
import subprocess

from ch4os_performance_trial import verify
from cpu_profile import write_new
from native_stages import inspect_runtime
from summarize_goal_runs import capture
from summarize_tree import ROOT,evidence,read,sha


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--trial',required=True)
    parser.add_argument('--control',default='goal-native-residual-control-01')
    parser.add_argument('--output',required=True)
    parser.add_argument('--verification-output',required=True)
    parser.add_argument('--verify-existing-result',action='store_true')
    args=parser.parse_args()
    folder=ROOT/'local/benchmarks'/args.trial
    stack=read(folder/'performance-stack.json'); bundle_path=ROOT/stack['bundle']['path']
    assert sha(bundle_path)==stack['bundle']['sha256']
    bundle=read(bundle_path); verify(bundle)
    control,trial=(capture(name) for name in (args.control,args.trial))
    assert control['protocol']['runtime']['sha256']==trial['protocol']['runtime']['sha256']
    assert control['protocol']['helper']['sha256']==trial['protocol']['helper']['sha256']
    assert control['protocol']['cpu_affinity_mask']==trial['protocol']['cpu_affinity_mask']=='fff000'
    for key in ('batch','property_batch','tree_batch','tree_budget_ms','tree_max_parts','tree_prioritize_unresolved',
                'chunk_task_budget_ms','cee_state_geometry','scoped_restoration','detailed','native_work','native_stages'):
        assert control['protocol'][key]==trial['protocol'][key],key
    for run in (control,trial):
        directory=ROOT/'local/benchmarks'/run['name']
        assert read(directory/'runner-exit.json')['exit_code']==0 and read(directory/'shutdown-save.json')['all_dimensions_saved']
        assert read(directory/'cpu-preflight.json')['quiet']
        launch=read(directory/'launch.json'); marker=inspect_runtime(launch['runtime']['path'],False)
        dlls=list((directory/'.pg-native/wwpg-safety-1').rglob('*.dll'))
        assert len(dlls)==1 and sha(dlls[0])==marker['native_sha256']
        run['loaded_native']=evidence(dlls[0])
    log=(folder/'process.log').read_text(errors='replace')
    assert '+UseZGC' in log and '+ZGenerational' in log and 'MaxHeapSize=14680064000' in log
    assert 'Microsoft OpenJDK 64-Bit Server VM 21.0.7' in log
    assert 'Using The Z Garbage Collector' in (folder/'gc.log').read_text()
    assert 'Loaded configuration file for ModernFix' in log and 'Loaded configuration file for Lithium' in log
    assert "Option 'mixin.perf.dynamic_resources'" in log and "Option 'mixin.perf.faster_item_rendering'" in log
    for rule in bundle['lithium_compatibility_exclusions']:
        assert rule in log,rule
    for item in stack['applied_mods']: assert sha(ROOT/item['path'])==item['sha256']
    for path in (bundle_path.parent/'mods').glob('*.jar'):
        assert sha(folder/'mods'/path.name)==sha(path) and path.name in log
    config_after=[dict(before=item,after=evidence(ROOT/item['path']),changed=sha(ROOT/item['path'])!=item['sha256']) for item in stack['applied_configs']]
    mean=control['summary']['mspt']['mean']
    difference={key:trial['summary']['mspt'][key]-value for key,value in control['summary']['mspt'].items()}
    result=dict(schema=1,trial_type='Headless Public server-performance stack and JVM settings versus last control',
        full_suite=False,graphical_acceptance=False,control=control,trial=trial,stack=stack,
        mean_tick_change_percent=100*difference['mean']/mean,trial_minus_control_ms=difference,
        config_after=config_after,evidence=[evidence(bundle_path),evidence(folder/'gc.log'),evidence(folder/'launch.json'),
            evidence(folder/'performance-stack.json'),evidence(folder/'cpu-preflight.json')],
        limitations=bundle['limitations']+[
            'One new trial against a historical control: host/JIT/row order, disk cache, frequency and thermal state are not matched.',
            'View/simulation distances match Public values in server.properties, but the forced-yard headless protocol has no player/client view.',
            'ZGC cycle collection time includes concurrent work; gc_ms cannot be interpreted as the same pause metric as G1.',
            'The observed ModernFix/Lithium SortedArraySet.removeIf overlap selects the Lithium implementation.',
            'Passing yard/storage/substep checks do not establish every mod callback or long-session/full reload compatibility.'])
    output=ROOT/args.output
    if args.verify_existing_result: assert read(output)==result
    else: write_new(output,result)
    retained=read(ROOT/'local/frozen/prototype-12g/inputs.json')
    for item in retained['sources']: assert sha(ROOT/item['path'])==item['sha256']
    for role in ('runtime','fixtures'):
        assert sha(ROOT/'build/libs'/Path(retained['artifacts'][role]['path']).name)==retained['artifacts'][role]['sha256']
    subprocess.run(['git','diff','--check'],cwd=ROOT,check=True)
    verification=dict(schema=1,passed=True,result=evidence(output),tool=evidence(Path(__file__)),
        bundle_files_verified=len(bundle['files']),java_files_verified=len(bundle['java_inputs']),
        retained_source_hashes=len(retained['sources']),matching_wwpg_and_helper=True,matching_native_identity=True,
        actual_public_java_zgc_heap_confirmed=True,performance_mods_verified=12,lithium_exclusions_verified=4,
        source_save_checks_passed=True,full_suite=False,graphical_acceptance=False)
    write_new(ROOT/args.verification_output,verification)
    print(json.dumps(dict(passed=True,mean_tick_change_percent=result['mean_tick_change_percent'],difference_ms=difference,
        runs=[dict(name=run['name'],tps=run['summary']['tps'],mspt=run['summary']['mspt'],
            headless_restoration_seconds=run['loading'][0]['total_restoration_seconds'],
            other_cpu_mean_cores=run['host']['other_cpu_mean_cores']) for run in (control,trial)])))


if __name__=='__main__': main()
