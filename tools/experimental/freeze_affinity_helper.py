"""Freeze a helper overlay that accepts an explicitly pinned whole JVM process."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import zipfile
from freeze_tree import ROOT, sha


def replace_once(text, old, new):
    assert text.count(old) == 1, old
    return text.replace(old,new)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name',required=True)
    parser.add_argument('--inputs',required=True)
    parser.add_argument('--base-helper-inputs',required=True)
    args=parser.parse_args()
    runtime=json.loads((ROOT/args.inputs).read_text())
    parent=json.loads((ROOT/args.base_helper_inputs).read_text())
    original=Path(parent['helper']['path'])
    assert sha(original)==parent['helper']['sha256']
    assert all(sha(ROOT/s['path'])==s['sha256'] for s in parent['sources'])
    folder=(ROOT/'local/frozen'/args.name).resolve()
    assert folder.is_relative_to(ROOT/'local/frozen')
    folder.mkdir(parents=True,exist_ok=False)
    package='org/cha0scollective/wwpg/benchmark'
    source=folder/'src'/package;source.mkdir(parents=True)
    text=(ROOT/'local/inputs/helper-src'/package/'CpuPlacement.java').read_text()
    text=replace_once(text,'info.processorMask.longValue() & originalMask','info.processorMask.longValue()')
    text=replace_once(text,'(cacheMasks.get(0) | cacheMasks.get(1)) != originalMask',
                      '((cacheMasks.get(0) | cacheMasks.get(1)) & originalMask) != originalMask')
    (source/'CpuPlacement.java').write_text(text,encoding='utf-8',newline='\n')
    text=(ROOT/'local/inputs/helper-src'/package/'FixedPlacement.java').read_text()
    text=replace_once(text,'long mask = placement.cacheMasks.get(1);',
                      'long mask = Long.decode(System.getProperty("wwpg.benchmark.cpuMask", "0xfff000"));')
    text=replace_once(text,'if (mask != 0xfff000L) throw new IllegalStateException("Unexpected verified groupB mask");',
                      'if (!placement.cacheMasks.contains(mask)) throw new IllegalStateException("Requested mask is not an observed whole L3 group");')
    text=replace_once(text,'"fixed-l3-b"','"fixed-l3"')
    (source/'FixedPlacement.java').write_text(text,encoding='utf-8',newline='\n')
    env=dict(os.environ);env['JAVA_HOME']=runtime['java_home'];env['PATH']=str(Path(runtime['java_home'])/'bin')+os.pathsep+env['PATH']
    command=['gradlew.bat','compileBudgetBenchmarkHelper','--offline','--no-daemon','-I','tools/experimental/budget-helper.gradle',
             '-PbudgetHelperDirectory='+str(folder),'-PbudgetRuntime='+runtime['artifacts']['runtime']['path']]
    with (folder/'compile.log').open('w') as stream:
        result=subprocess.run(command,cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT)
    if result.returncode:
        print((folder/'compile.log').read_text(errors='replace')[-12000:]);raise SystemExit(1)
    expected={package+'/'+name+'.class' for name in ('CpuPlacement','FixedPlacement')}
    output=folder/'wwpg-benchmark-0.1.0-affinity.jar'
    with zipfile.ZipFile(original) as old,zipfile.ZipFile(output,'w') as new:
        for item in old.infolist():
            replacement=folder/'classes'/item.filename
            new.writestr(item,replacement.read_bytes() if item.filename in expected else old.read(item))
    with zipfile.ZipFile(original) as old,zipfile.ZipFile(output) as new:
        assert old.namelist()==new.namelist()
        assert {n for n in old.namelist() if old.read(n)!=new.read(n)}==expected
    sources=parent['sources']+[dict(path=p.relative_to(ROOT).as_posix(),sha256=sha(p)) for p in sorted((folder/'src').rglob('*.java'))]
    manifest=dict(schema=1,helper=dict(path=str(output),sha256=sha(output)),base_helper=parent['helper'],
                  parent_inputs=str(ROOT/args.base_helper_inputs),runtime=runtime['artifacts']['runtime'],sources=sources,
                  process_affinity_supported=True,changed_entries=sorted(expected),gate_checks_reused=parent['gate_checks'],
                  command=command,compile_log=dict(path=(folder/'compile.log').relative_to(ROOT).as_posix(),sha256=sha(folder/'compile.log')))
    (folder/'inputs.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(dict(inputs=str(folder/'inputs.json'),helper=manifest['helper'],changed_entries=sorted(expected))))


if __name__=='__main__':main()
