"""Add four live residual-cache checks to an isolated native fixture companion."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

from cpu_profile import write_new
from summarize_tree import ROOT,evidence,read,sha


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name',required=True); parser.add_argument('--inputs',required=True)
    args=parser.parse_args(); base_path=ROOT/args.inputs; base=read(base_path)
    for item in base['artifacts'].values(): assert sha(Path(item['path']))==item['sha256']
    folder=(ROOT/'local/frozen'/args.name).resolve(); assert folder.is_relative_to(ROOT/'local/frozen')
    folder.mkdir(parents=True,exist_ok=False)
    definition=ROOT/'tools/experimental/native-residual/NativeResidualGameTests.java'
    source=folder/'src/org/cha0scollective/wwpg/gametest/NativeResidualGameTests.java'
    source.parent.mkdir(parents=True); shutil.copy2(definition,source)
    script=ROOT/'tools/experimental/native-reduction-fixtures.gradle'
    shutil.copy2(__file__,folder/'freezer.py')
    command=['gradlew.bat','compileNativeReductionFixtures','--offline','--no-daemon','-I',str(script),
        '-PnativeFixtureDirectory='+str(folder),'-PnativeFixtureRuntime='+base['artifacts']['runtime']['path'],
        '-PnativeFixtureBase='+base['artifacts']['fixtures']['path']]
    env=dict(os.environ,JAVA_HOME=base['java_home'])
    env['PATH']=str(Path(base['java_home'])/'bin')+os.pathsep+env['PATH']
    with (folder/'compile.log').open('x',encoding='utf-8') as log:
        process=subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
    if process.returncode:
        print((folder/'compile.log').read_text(errors='replace')[-10000:]); raise SystemExit(process.returncode)
    classes=sorted((folder/'classes').rglob('*.class')); assert classes
    output=folder/'wwpg-0.2.7-experimental.native-residual-gametests.jar'
    with zipfile.ZipFile(base['artifacts']['fixtures']['path']) as old,zipfile.ZipFile(output,'w') as new:
        for item in old.infolist(): new.writestr(item,old.read(item))
        for path in classes:
            entry=path.relative_to(folder/'classes').as_posix(); assert entry not in old.namelist()
            new.writestr(entry,path.read_bytes())
    with zipfile.ZipFile(base['artifacts']['fixtures']['path']) as old,zipfile.ZipFile(output) as new:
        assert all(old.read(name)==new.read(name) for name in old.namelist())
        added=sorted(set(new.namelist())-set(old.namelist()))
        assert all(name.startswith('org/cha0scollective/wwpg/gametest/NativeResidualGameTests') for name in added)
    proof=dict(schema=1,java_home=base['java_home'],base_inputs=evidence(base_path),unchanged_original_entries=True,
        artifacts=dict(fixtures=dict(path=str(output),sha256=sha(output))),added_entries=added,added_test_methods=4,command=command,
        sources=[evidence(path) for path in (definition,source,script,folder/'freezer.py')],evidence=[evidence(folder/'compile.log')])
    write_new(folder/'inputs.json',proof)
    print(json.dumps(dict(inputs=str(folder/'inputs.json'),fixture_sha256=sha(output),added_test_methods=4)))


if __name__=='__main__': main()
