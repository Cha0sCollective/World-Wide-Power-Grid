"""Add wide native safety fixtures to a copied test companion only."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

from cpu_profile import write_new
from freeze_tree import ROOT, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--inputs', required=True)
    args = parser.parse_args()
    base_path = ROOT / args.inputs
    base = json.loads(base_path.read_text())
    assert all(sha(Path(p['path'])) == p['sha256'] for p in base['artifacts'].values())
    folder = (ROOT / 'local/frozen' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local/frozen')
    folder.mkdir(parents=True,exist_ok=False)
    source = folder / 'src/org/cha0scollective/wwpg/gametest/NativeReductionGameTests.java'
    source.parent.mkdir(parents=True)
    definition = ROOT / 'tools/experimental/native-reduction/NativeReductionGameTests.java'
    shutil.copy2(definition,source)
    script = ROOT / 'tools/experimental/native-reduction-fixtures.gradle'
    command = ['gradlew.bat','compileNativeReductionFixtures','--offline','--no-daemon',
        '-I',str(script),'-PnativeFixtureDirectory=' + str(folder),
        '-PnativeFixtureRuntime=' + base['artifacts']['runtime']['path'],
        '-PnativeFixtureBase=' + base['artifacts']['fixtures']['path']]
    env = dict(os.environ,JAVA_HOME=base['java_home'])
    env['PATH'] = str(Path(base['java_home']) / 'bin') + os.pathsep + env['PATH']
    with (folder / 'compile.log').open('x',encoding='utf-8') as log:
        result = subprocess.run(command,cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
    if result.returncode:
        print((folder / 'compile.log').read_text(errors='replace')[-12000:])
        raise SystemExit(result.returncode)
    classes = sorted((folder / 'classes').rglob('*.class'))
    assert classes
    output = folder / 'wwpg-0.2.7-experimental.native-reduction-gametests.jar'
    with zipfile.ZipFile(base['artifacts']['fixtures']['path']) as old, zipfile.ZipFile(output,'w') as new:
        for item in old.infolist(): new.writestr(item,old.read(item))
        for path in classes:
            entry = path.relative_to(folder / 'classes').as_posix()
            assert entry not in old.namelist()
            new.writestr(entry,path.read_bytes())
    with zipfile.ZipFile(base['artifacts']['fixtures']['path']) as old, zipfile.ZipFile(output) as new:
        assert all(old.read(n) == new.read(n) for n in old.namelist())
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert all(n.startswith('org/cha0scollective/wwpg/gametest/NativeReductionGameTests') for n in added)
    record = dict(schema=1,java_home=base['java_home'],base_inputs=dict(path=str(base_path),sha256=sha(base_path)),
        artifacts=dict(fixtures=dict(path=str(output),sha256=sha(output))),unchanged_original_entries=True,
        added_entries=added,added_test_methods=4,command=command,
        sources=[dict(path=p.relative_to(ROOT).as_posix(),sha256=sha(p)) for p in (definition,source,script,Path(__file__))],
        evidence=[dict(path=str(folder / 'compile.log'),sha256=sha(folder / 'compile.log'))])
    write_new(folder / 'inputs.json',record)
    print(json.dumps(dict(inputs=str(folder / 'inputs.json'),added_entries=added,fixture_sha256=sha(output))))


if __name__ == '__main__':
    main()
