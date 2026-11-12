"""Verify preserved capture/replay evidence and publish the offline checkpoint."""
import argparse
import ast
import csv
import io
import json
from pathlib import Path
import re
import subprocess
import zipfile

from cpu_profile import write_new
from native_residual_capture import inspect_runtime, parse_capture
from native_stages import inspect_runtime as inspect_clean
from run_native_profile_checks import windows_path
from summarize_tree import ROOT, evidence, read, sha


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',required=True)
    parser.add_argument('--verification-output',required=True)
    parser.add_argument('--verify-existing-result',action='store_true')
    args=parser.parse_args()
    checked={}
    def verify_refs(value):
        if isinstance(value,dict):
            if 'path' in value and 'sha256' in value:
                path=windows_path(value['path'])
                if not path.is_absolute(): path=ROOT/path
                key=str(path.resolve())
                if key in checked: assert checked[key]==value['sha256'],key
                else:
                    assert path.is_file() and sha(path)==value['sha256'],key
                    checked[key]=value['sha256']
            for item in value.values(): verify_refs(item)
        elif isinstance(value,list):
            for item in value: verify_refs(item)
    def record(relative):
        path=ROOT/relative
        value=read(path)
        verify_refs(value)
        return value

    retained_path=ROOT/'local/frozen/prototype-12g/inputs.json'
    retained=record(retained_path)
    frozen=record('local/frozen/native-residual-capture-01/inputs.json')
    capture_build=record('local/native-residual-capture-01/build.json')
    capture_checks=record('local/native-residual-capture-01/checks.json')
    assert capture_checks['passed'] and capture_checks['capture_paths_checked']==3 and capture_checks['malformed_recordings_rejected']==3
    record(windows_path(capture_build['candidate_build']['path']))
    directory=ROOT/'local/benchmarks/goal-native-residual-capture-01'
    captured=record(directory/'native-residual-captures.json')
    protocol=record(directory/'experiment.json')
    record(directory/'launch.json')
    assert captured['passed'] and captured['diagnostic'] and len(captured['captures'])==3
    assert protocol['validation']['passed'] and protocol['original_reference_unchanged'] and protocol['isolated_reference_unchanged']
    assert protocol['cpu_affinity_mask']=='fff000' and protocol['validation']['actual_nodes']==14650 and protocol['sixteen_substeps']
    assert read(directory/'runner-exit.json')['exit_code']==0 and read(directory/'shutdown-save.json')['all_dimensions_saved']
    for item in captured['captures']:
        assert json.loads(json.dumps(parse_capture(ROOT/item['file']['path'])))==item
        assert item['rows']==14925 and item['nonzeros']==57097 and item['zero_state_entries']==0 and not any(item['nonfinite'].values())
    runtime=Path(frozen['artifacts']['runtime']['path'])
    assert inspect_runtime(runtime,False)==captured['marker']
    rejected=0
    for function,enabled in ((inspect_clean,False),(inspect_clean,True),(inspect_runtime,True)):
        try: function(runtime,enabled)
        except (AssertionError,KeyError): rejected+=1
    assert rejected==3
    with zipfile.ZipFile(retained['artifacts']['runtime']['path']) as original,zipfile.ZipFile(runtime) as diagnostic:
        classes=[name for name in original.namelist() if name.endswith('.class')]
        assert len(classes)==294 and all(original.read(name)==diagnostic.read(name) for name in classes)
    failed_path=ROOT/'local/native-residual-replay-01/checks.json'
    failed=read(failed_path)
    assert not failed['passed'] and not failed['executable_started']
    attempts=[]
    for name,check_name,comparison_count in (('native-residual-replay-01','checks-02',192),
            ('native-residual-tiled-01','checks',288),('native-residual-tiled-02','checks',288)):
        build_path=ROOT/'local'/name/'build.json'
        check_path=build_path.parent/(check_name+'.json')
        build,proof=record(build_path),record(check_path)
        assert proof['passed'] and proof['offline_only'] and proof['original_reference_replays']==3
        assert proof['build']['sha256']==sha(build_path) and proof['exact_vector_comparisons']==comparison_count
        assert proof['captures']['sha256']==sha(directory/'native-residual-captures.json')
        for observation,item in zip(proof['observations'],captured['captures'],strict=True):
            assert observation['capture']==item
            text=(ROOT/observation['log']['path']).read_text()
            count=re.search(r'PASS (\d+) full-vector and MXCSR comparisons;',text)
            assert count and int(count[1])==observation['exact_vector_comparisons']
            rows=list(csv.DictReader(io.StringIO(text[text.index('role,block,calls,elapsed_ms'):])) )
            roles=set(observation['mean_ms_per_call'])
            assert len(rows)==12*len(roles)
            for role in roles:
                group=[row for row in rows if row['role']==role]
                assert len(group)==12 and {int(row['block']) for row in group}==set(range(12)) and all(int(row['calls'])==512 for row in group)
                mean=sum(float(row['elapsed_ms']) for row in group)/sum(int(row['calls']) for row in group)
                assert mean==observation['mean_ms_per_call'][role]
                if role!='reference': assert mean/observation['mean_ms_per_call']['reference']==observation['relative_to_reference'][role]
        attempts.append(dict(name=name,build=evidence(build_path),checks=evidence(check_path),
            original_reference_replays=3,exact_capture_comparisons=comparison_count,
            timings=[dict(capture=item['capture']['file']['path'],mean_ms_per_call=item['mean_ms_per_call'],
                relative_to_reference=item['relative_to_reference']) for item in proof['observations']],
            edge_checks=proof.get('edge_checks')))
    edges=attempts[-1]['edge_checks']
    assert edges['exact_comparisons']==79120 and edges['value_refreshes']==2430 and edges['structural_rebuilds']==84
    assert 'PASS 79120 tiled output/status/MXCSR comparisons; 2430 explicit value refreshes; 84 structural rebuilds; both guard boundaries' in (ROOT/edges['log']['path']).read_text()
    final=attempts[-1]['timings']
    projected=sum(item['mean_ms_per_call']['reference']-item['mean_ms_per_call']['row-tiled'] for item in final)/len(final)*48
    build_artifacts=[]
    for role in ('runtime','fixtures'):
        path=ROOT/'build/libs'/Path(retained['artifacts'][role]['path']).name
        assert sha(path)==retained['artifacts'][role]['sha256']
        build_artifacts.append(evidence(path))
    result=dict(schema=1,snapshot='2026-11-09',retained=False,gameplay_layout_integrated=False,full_suite=False,graphical_acceptance=False,
        conclusion='Four-row SIMD preserves tested results and reduces offline residual time about 30%; scalar row layouts are slower. No new TPS or client-loading gain is established.',
        capture= dict(name=directory.name,checks=evidence(directory/'native-residual-captures.json'),inputs=captured['captures'],
            runtime=evidence(runtime),native=captured['loaded_native'],gameplay_classes_compared=len(classes),
            source_saves_unchanged=True,whole_yard_checks_passed=True,normal_shutdown=True,diagnostic=True),
        capture_transparency_checks=evidence(ROOT/'local/native-residual-capture-01/checks.json'),
        failed_first_harness=dict(evidence=evidence(failed_path),executable_started=False,reason=failed['reason']),
        offline_attempts=attempts,projected_saving_for_48_calls_ms=projected,
        retained_inputs=evidence(retained_path),retained_build_artifacts=build_artifacts,
        next_step='Independent live-matrix cache invalidation and cleanup, focused mutation/callback checks, then a clean copied-yard comparison.',
        limitations=['Three recorded inputs are not exhaustive mod inputs or guaranteed stable-phase captures.',
            'Microbenchmarks include output reset but exclude plan construction and refresh. No host frequency/thermal control.',
            'The 48-call projection is arithmetic, not a measured tick gain.',
            'Fresh plan construction and explicit refresh tests do not establish live-solver automatic invalidation.',
            'No gameplay layout integration, client load, full-yard reload, full headless or graphical acceptance is added.'])
    output=ROOT/args.output
    if args.verify_existing_result: assert read(output)==result
    else: write_new(output,result)
    verify_refs(result)
    branch=subprocess.check_output(['git','branch','--show-current'],cwd=ROOT,text=True).strip()
    assert branch=='dev/experimental-plain-wire-batching'
    main_status=subprocess.check_output(['git','-C',str(ROOT.parents[1]),'status','--porcelain'],text=True).splitlines()
    assert all(line.startswith('?? ') for line in main_status),'Main worktree has tracked changes'
    subprocess.run(['git','diff','--check'],cwd=ROOT,check=True)
    links=0
    for name in ('NATIVE_RESIDUAL_LAYOUT.md','NATIVE_REDUCTION.md','PERFORMANCE_GOAL.md'):
        path=ROOT/'docs/experimental'/name
        for link in re.findall(r'\]\(([^)]+)\)',path.read_text(encoding='utf-8')):
            if '://' in link or link.startswith('#'): continue
            assert (path.parent/link.split('#')[0]).exists(),(name,link)
            links+=1
    scripts=['build_native_residual_capture.py','native_residual_capture.py','build_native_residual_replay.py',
        'run_native_residual_replay.py','verify_native_residual.py','plain_wire_benchmark.py']
    for name in scripts:
        path=ROOT/'tools/experimental'/name
        ast.parse(path.read_text(encoding='utf-8'),filename=str(path))
    verification=dict(schema=1,passed=True,branch=branch,verified_file_references=len(checked),
        retained_source_build_hashes_verified=len(retained['sources']),retained_build_artifacts=build_artifacts,
        main_tracked_worktree_clean=True,main_untracked_status_entries=len(main_status),
        diagnostic_mode_mismatches_rejected=rejected,local_document_links_checked=links,python_syntax_files=len(scripts),
        result=evidence(output),tool=evidence(Path(__file__)),full_suite=False,graphical_acceptance=False,
        note='Rechecks preserved hashes, capture metadata, CSV arithmetic, diagnostic markers and unchanged runtime. Native and game check counts describe their named completed runs; they were not rerun by this verifier.')
    write_new(ROOT/args.verification_output,verification)
    print(json.dumps(dict(passed=True,verified_file_references=len(checked),source_hashes=len(retained['sources']),
        exact_edge_comparisons=edges['exact_comparisons'],projected_saving_for_48_calls_ms=projected,result=args.output)))


if __name__=='__main__': main()
