"""Check and freeze isolated native residual-cache variants."""
import argparse
import json
from pathlib import Path,PurePosixPath
import re
import shutil
import subprocess
import sys
import zipfile

from cpu_profile import write_new
from native_stages import MARKER,WINDOWS_DLL
from run_native_profile_checks import windows_path,linux_path
from summarize_tree import ROOT,evidence,read,sha


def verify(build):
    assert build['original_solver_restored'] and build['original_header_restored'] and build['identical_checked_row_kernels']
    items=build['inputs']+build['logs']+[item for role in build['artifacts'].values() for item in role.values()]
    for item in items: assert sha(windows_path(item['path']))==item['sha256'],item['path']


def checks(args):
    path=ROOT/args.build; build=read(path); verify(build)
    folder=path.parent/'checks'; folder.mkdir(exist_ok=False)
    shutil.copy2(__file__,folder/'runner.py')
    observations={}
    baseline=read(ROOT/'local/native-stages-01/native-safety-checks.json')['pe_audits']['baseline']
    objdump=str(PurePosixPath(build['commands'][0][0]).with_name('x86_64-w64-mingw32-objdump'))
    for role,artifacts in build['artifacts'].items():
        process=subprocess.run([str(windows_path(artifacts['checks']['path']))],cwd=folder,capture_output=True,timeout=120)
        log=folder/(role+'.log'); log.write_bytes(process.stdout+process.stderr)
        matched=re.search(rb'PASS (\d+) live matrix comparisons; (\d+) lifecycle cycles; (\d+) allocation failures; cache_enabled=(\d+)',process.stdout)
        if process.returncode or not matched:
            write_new(path.parent/'checks.json',dict(schema=1,passed=False,build=evidence(path),role=role,log=evidence(log),exit_code=process.returncode))
            print(log.read_text(errors='replace')); raise SystemExit(1)
        assert int(matched[4])==int(role=='candidate')
        if role=='candidate': assert int(matched[3])>0
        audit=subprocess.run(['wsl','-d','Ubuntu-22.04','--',objdump,'-p',linux_path(artifacts['dll']['path'])],capture_output=True,timeout=60)
        pe=folder/(role+'-pe.txt'); pe.write_bytes(audit.stdout+audit.stderr)
        text=audit.stdout.decode(errors='replace')
        imports=sorted(re.findall(r'DLL Name:\s*(\S+)',text))
        exports=sorted(re.findall(r'^\s*\[\s*\d+\]\s+(\S+)\s*$',text,re.MULTILINE))
        assert audit.returncode==0 and imports==baseline['imports'] and exports==baseline['exports']
        observations[role]=dict(live_comparisons=int(matched[1]),lifecycle_cycles=int(matched[2]),allocation_failures=int(matched[3]),
            imports=imports,exports=exports,evidence=[evidence(log),evidence(pe)])
    verify(build)
    proof=dict(schema=1,passed=True,build=evidence(path),observations=observations,artifacts_unchanged=True,tool=evidence(folder/'runner.py'),
        scope='Focused standalone cache mutation, lifecycle and allocation-failure checks; no new gameplay acceptance.')
    write_new(path.parent/'checks.json',proof)
    print(json.dumps(dict(passed=True,observations={role:{key:value for key,value in item.items() if key in ('live_comparisons','lifecycle_cycles','allocation_failures')} for role,item in observations.items()})))


def freeze(args):
    build_path,checks_path,base_path,fixtures_path=(ROOT/p for p in (args.build,args.checks,args.base_inputs,args.fixture_inputs))
    build,checked,base,fixtures=map(read,(build_path,checks_path,base_path,fixtures_path)); verify(build)
    assert checked['passed'] and checked['build']['sha256']==sha(build_path) and checked['artifacts_unchanged']
    for item in base['sources']+fixtures['sources']: assert sha(ROOT/item['path'])==item['sha256']
    for item in base['artifacts'].values(): assert sha(Path(item['path']))==item['sha256']
    folder=(ROOT/'local/frozen'/args.name).resolve(); assert folder.is_relative_to(ROOT/'local/frozen')
    folder.mkdir(parents=True,exist_ok=False)
    output=folder/('wwpg-0.2.7-experimental.native-residual-'+args.role+'.jar')
    dll=windows_path(build['artifacts'][args.role]['dll']['path'])
    marker=dict(schema=1,diagnostic=False,variant='native-residual-'+args.role,native_sha256=sha(dll),
        native_build=evidence(build_path),original_runtime_sha256=base['artifacts']['runtime']['sha256'],gameplay_class_bytes_unchanged=True)
    props_name='native/wwpg/safe/guarded.properties'
    with zipfile.ZipFile(base['artifacts']['runtime']['path']) as old,zipfile.ZipFile(output,'w') as new:
        assert MARKER not in old.namelist()
        props=old.read(props_name).decode(); assert len(re.findall(r'windows\.sha256=[0-9a-f]{64}',props))==1
        props=re.sub(r'(windows\.sha256=)[0-9a-f]{64}',lambda match:match[1]+sha(dll),props)
        for item in old.infolist():
            data=dll.read_bytes() if item.filename==WINDOWS_DLL else props.encode() if item.filename==props_name else old.read(item)
            new.writestr(item,data)
        new.writestr(MARKER,(json.dumps(marker,indent=2)+'\n').encode())
    with zipfile.ZipFile(base['artifacts']['runtime']['path']) as old,zipfile.ZipFile(output) as new:
        changed=sorted(name for name in old.namelist() if old.read(name)!=new.read(name))
        assert changed==sorted([WINDOWS_DLL,props_name]) and set(new.namelist())-set(old.namelist())=={MARKER}
        classes=[name for name in old.namelist() if name.endswith('.class')]
        assert len(classes)==294 and all(old.read(name)==new.read(name) for name in classes)
    artifacts=dict(runtime=dict(path=str(output),sha256=sha(output)))
    for role,item in (('fixtures',fixtures['artifacts']['fixtures']),('cee',base['artifacts']['cee'])):
        source=Path(item['path']); assert sha(source)==item['sha256']
        target=folder/source.name; shutil.copy2(source,target)
        artifacts[role]=dict(path=str(target),sha256=sha(target))
    shutil.copy2(__file__,folder/'freezer.py')
    record=dict(schema=1,role=args.role,java_home=base['java_home'],sources=base['sources'],artifacts=artifacts,
        native_unchanged=False,gameplay_classes_unchanged=True,gameplay_classes_compared=len(classes),changed_jar_entries=changed,
        added_jar_entries=[MARKER],fixture_inputs=evidence(fixtures_path),
        evidence=[evidence(p) for p in (build_path,checks_path,base_path,fixtures_path,folder/'freezer.py')])
    write_new(folder/'inputs.json',record)
    print(json.dumps(dict(inputs=str(folder/'inputs.json'),runtime=artifacts['runtime'],native_sha256=sha(dll))))


def main():
    mode=sys.argv.pop(1)
    parser=argparse.ArgumentParser(description=__doc__); parser.add_argument('--build',required=True)
    if mode=='freeze':
        parser.add_argument('--checks',required=True); parser.add_argument('--base-inputs',required=True)
        parser.add_argument('--fixture-inputs',required=True); parser.add_argument('--role',choices=('control','candidate'),required=True)
        parser.add_argument('--name',required=True)
    assert mode in ('checks','freeze'); args=parser.parse_args()
    (checks if mode=='checks' else freeze)(args)


if __name__=='__main__': main()
