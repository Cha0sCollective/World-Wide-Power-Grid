"""Replay captured residuals and compare offline row kernels with the original."""
import argparse
import csv
import io
import json
from pathlib import Path
import re
import shutil
import subprocess

from cpu_profile import write_new
from native_residual_capture import parse_capture
from run_native_profile_checks import windows_path
from summarize_tree import ROOT,evidence,read,sha


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--build',required=True)
    parser.add_argument('--captures',required=True)
    parser.add_argument('--name',default='checks')
    args=parser.parse_args()
    build_path,capture_path=(ROOT/p for p in (args.build,args.captures))
    build,captures=read(build_path),read(capture_path)
    assert build['offline_only'] and captures['passed']
    items=build['inputs']+[build['artifact'],build['log']]+[build[key] for key in ('edge_checks','edge_build_log') if key in build]
    for item in items:
        assert sha(windows_path(item['path']))==item['sha256'],item['path']
    folder=(build_path.parent/args.name).resolve()
    assert folder.parent==build_path.parent and folder.name==args.name
    folder.mkdir(exist_ok=False)
    shutil.copy2(__file__,folder/'runner.py')
    edges=None
    if 'edge_checks' in build:
        process=subprocess.run([str(windows_path(build['edge_checks']['path']))],cwd=folder,capture_output=True,timeout=120)
        log=folder/'edge-checks.log'
        log.write_bytes(process.stdout+process.stderr)
        matched=re.search(rb'PASS (\d+) tiled output/status/MXCSR comparisons; (\d+) explicit value refreshes; (\d+) structural rebuilds; both guard boundaries',process.stdout)
        if process.returncode or not matched:
            write_new(folder.with_suffix('.json'),dict(schema=1,passed=False,build=evidence(build_path),
                failure_stage='offline edge checks',log=evidence(log),exit_code=process.returncode,tool=evidence(folder/'runner.py')))
            print(log.read_text(errors='replace')); raise SystemExit(1)
        edges=dict(exact_comparisons=int(matched[1]),value_refreshes=int(matched[2]),structural_rebuilds=int(matched[3]),
            guarded_boundaries='start and end',log=evidence(log))
    observations=[]
    for index,item in enumerate(captures['captures']):
        path=ROOT/item['file']['path']
        assert sha(path)==item['file']['sha256'] and json.loads(json.dumps(parse_capture(path)))==item
        process=subprocess.run([str(windows_path(build['artifact']['path'])),str(path)],cwd=folder,
                               capture_output=True,timeout=120)
        log=folder/('capture-'+str(index)+'.log')
        log.write_bytes(process.stdout+process.stderr)
        text=process.stdout.decode(errors='replace')
        passed=re.search(r'PASS (\d+) full-vector and MXCSR comparisons; rows=(\d+) nnz=(\d+)',text)
        if process.returncode or not passed:
            write_new(folder.with_suffix('.json'),dict(schema=1,passed=False,build=evidence(build_path),
                failed_capture=evidence(path),log=evidence(log),exit_code=process.returncode))
            print(log.read_text(errors='replace')); raise SystemExit(1)
        assert int(passed[2])==item['rows'] and int(passed[3])==item['nonzeros']
        rows=list(csv.DictReader(io.StringIO(text[text.index('role,block,calls,elapsed_ms'):])) )
        roles=sorted({row['role'] for row in rows})
        assert set(roles) in ({'reference','row-indexed','row-packed'},{'reference','row-indexed','row-packed','row-tiled'})
        assert len(rows)==12*len(roles) and int(passed[1])==32*(len(roles)-1)
        means={}
        for role in roles:
            group=[r for r in rows if r['role']==role]
            assert len(group)==12 and {int(r['block']) for r in group}==set(range(12)) and all(int(r['calls'])==512 for r in group)
            means[role]=sum(float(r['elapsed_ms']) for r in group)/sum(int(r['calls']) for r in group)
        observations.append(dict(capture=item,exact_vector_comparisons=int(passed[1]),mean_ms_per_call=means,
            relative_to_reference={role:value/means['reference'] for role,value in means.items() if role!='reference'},log=evidence(log)))
    assert all(sha(windows_path(item['path']))==item['sha256'] for item in items)
    proof=dict(schema=1,passed=True,offline_only=True,build=evidence(build_path),captures=evidence(capture_path),
        original_reference_replays=len(observations),exact_vector_comparisons=sum(o['exact_vector_comparisons'] for o in observations),
        observations=observations,edge_checks=edges,tool=evidence(folder/'runner.py'),
        limitations=['These tests cover three real matrices/vectors over 16 FP modes and two initial status states, not all possible mod inputs.',
            'Row-plan construction and value refresh are excluded from timings; each measured call includes the same output reset.',
            'Indexed and packed prototypes require explicit rebuild/refresh after matrix changes; no gameplay integration or invalidation is implemented.',
            'Rotating-order repeated-input microbenchmarks are not game throughput or a clean TPS result.'])
    write_new(folder.with_suffix('.json'),proof)
    print(json.dumps(dict(passed=True,exact_vector_comparisons=proof['exact_vector_comparisons'],
        observations=[dict(capture=o['capture']['file']['path'],means=o['mean_ms_per_call'],relative=o['relative_to_reference']) for o in observations])))


if __name__=='__main__': main()
