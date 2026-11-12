"""Build offline row-layout replay against the exact frozen SuperLU archive."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name',required=True)
    parser.add_argument('--capture-build',required=True)
    args=parser.parse_args()
    base_path=ROOT/args.capture_build
    base=json.loads(base_path.read_text())
    candidate_path=Path(base['candidate_build']['path'])
    assert sha(candidate_path)==base['candidate_build']['sha256']
    candidate=json.loads(candidate_path.read_text())
    for item in base['inputs']+list(base['artifacts'].values())+candidate['inputs']:
        assert sha(Path(item['path']))==item['sha256'],item['path']
    folder=(ROOT/'local'/args.name).resolve()
    assert folder.is_relative_to(ROOT/'local')
    (folder/'src').mkdir(parents=True,exist_ok=False)
    definitions=[ROOT/'tools/experimental/native-residual'/name for name in ('row_layout.h','row_layout.c','tile_layout.c','replay.c','row_checks.c')]
    for source in definitions: shutil.copy2(source,folder/'src'/source.name)
    shutil.copy2(__file__,folder/'build-script.py')
    command=list(base['commands'][-1])
    command[command.index('-o')+1]=str(folder/'replay.exe')
    old=str(base_path.parent/'src/capture_checks.c')
    at=command.index(old)
    command[at:at+1]=[str(folder/'src/row_layout.c'),str(folder/'src/tile_layout.c'),str(folder/'src/replay.c')]
    command.insert(1,'-ffp-contract=off')
    inputs=[base_path,candidate_path,folder/'build-script.py',*sorted((folder/'src').iterdir())]
    hashes={str(p):sha(p) for p in inputs}
    compiler=Path(command[0])
    env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ['PATH'])
    working=Path(candidate['baseline_build']['path']).parent/'build'
    with (folder/'build.log').open('x') as log:
        result=subprocess.run(command,cwd=working,env=env,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:
        print((folder/'build.log').read_text(errors='replace')); raise SystemExit(result.returncode)
    check_command=[value.replace(str(folder/'replay.exe'),str(folder/'row_checks.exe')).replace(str(folder/'src/replay.c'),str(folder/'src/row_checks.c')) for value in command]
    with (folder/'checks-build.log').open('x') as log:
        result=subprocess.run(check_command,cwd=working,env=env,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:
        print((folder/'checks-build.log').read_text(errors='replace')); raise SystemExit(result.returncode)
    assert all(sha(Path(p))==value for p,value in hashes.items())
    output=folder/'replay.exe'
    record=dict(schema=1,offline_only=True,capture_build=dict(path=str(base_path),sha256=sha(base_path)),
        artifact=dict(path=str(output),sha256=sha(output)),command=command,
        inputs=[dict(path=p,sha256=h) for p,h in hashes.items()],log=dict(path=str(folder/'build.log'),sha256=sha(folder/'build.log')),
        edge_checks=dict(path=str(folder/'row_checks.exe'),sha256=sha(folder/'row_checks.exe')),
        edge_build_log=dict(path=str(folder/'checks-build.log'),sha256=sha(folder/'checks-build.log')),edge_command=check_command,
        note='Immutable source snapshots. No runtime jar or native gameplay DLL is changed.')
    (folder/'build.json').write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps(dict(built=str(folder/'build.json'),artifact=record['artifact'])))


if __name__=='__main__': main()
