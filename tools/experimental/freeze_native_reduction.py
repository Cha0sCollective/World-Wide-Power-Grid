"""Freeze same-compiler control/candidate native jars with matching wide fixtures."""
import argparse
import json
from pathlib import Path
import re
import shutil
import zipfile

from cpu_profile import write_new
from freeze_tree import ROOT, sha
from native_stages import MARKER, WINDOWS_DLL
from run_native_profile_checks import windows_path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name',required=True)
    parser.add_argument('--role',choices=('control','candidate'),required=True)
    parser.add_argument('--base-inputs',required=True)
    parser.add_argument('--native-build',required=True)
    parser.add_argument('--native-checks',required=True)
    parser.add_argument('--fixture-inputs',required=True)
    args = parser.parse_args()
    paths = [ROOT / p for p in (args.base_inputs,args.native_build,args.native_checks,args.fixture_inputs)]
    base,build,checks,fixtures = (json.loads(p.read_text()) for p in paths)
    assert checks['passed'] and checks['build']['sha256'] == sha(paths[1]) and checks['artifacts_unchanged']
    assert all(sha(ROOT / p['path']) == p['sha256'] for p in base['sources'] + fixtures['sources'])
    assert all(sha(Path(p['path'])) == p['sha256'] for p in base['artifacts'].values())
    baseline_path = windows_path(build['baseline_build']['path'])
    assert sha(baseline_path) == build['baseline_build']['sha256']
    baseline = json.loads(baseline_path.read_text())
    native = build['artifacts']['libpowergridNativeReduction.dll'] if args.role == 'candidate' else baseline['artifacts']['libpowergridNative7.dll']
    dll = windows_path(native['path'])
    assert sha(dll) == native['sha256']
    folder = (ROOT / 'local/frozen' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local/frozen')
    folder.mkdir(parents=True,exist_ok=False)
    output = folder / ('wwpg-0.2.7-experimental.native-reduction-' + args.role + '.jar')
    properties_name = 'native/wwpg/safe/guarded.properties'
    marker = dict(schema=1,diagnostic=False,native_sha256=sha(dll),variant='native-reduction-' + args.role,
        native_build=dict(path=str(paths[1]),sha256=sha(paths[1])),
        original_runtime_sha256=base['artifacts']['runtime']['sha256'],gameplay_class_bytes_unchanged=True)
    with zipfile.ZipFile(base['artifacts']['runtime']['path']) as old, zipfile.ZipFile(output,'w') as new:
        assert MARKER not in old.namelist()
        props = old.read(properties_name).decode()
        assert len(re.findall(r'windows\.sha256=[0-9a-f]{64}',props)) == 1
        props = re.sub(r'(windows\.sha256=)[0-9a-f]{64}',lambda m:m[1]+sha(dll),props)
        for item in old.infolist():
            data = dll.read_bytes() if item.filename == WINDOWS_DLL else props.encode() if item.filename == properties_name else old.read(item)
            new.writestr(item,data)
        new.writestr(MARKER,(json.dumps(marker,indent=2)+'\n').encode())
    with zipfile.ZipFile(base['artifacts']['runtime']['path']) as old, zipfile.ZipFile(output) as new:
        changed = sorted(n for n in old.namelist() if old.read(n) != new.read(n))
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert set(changed) == {WINDOWS_DLL,properties_name} and added == [MARKER]
        classes = [n for n in old.namelist() if n.endswith('.class')]
        assert all(old.read(n) == new.read(n) for n in classes)
    artifacts = dict(runtime=dict(path=str(output),sha256=sha(output)))
    for role,item in (('fixtures',fixtures['artifacts']['fixtures']),('cee',base['artifacts']['cee'])):
        path = Path(item['path'])
        assert sha(path) == item['sha256']
        target = folder / path.name
        shutil.copy2(path,target)
        artifacts[role] = dict(path=str(target),sha256=sha(target))
    record = dict(schema=1,java_home=base['java_home'],sources=base['sources'],artifacts=artifacts,
        role=args.role,native_unchanged=False,gameplay_classes_unchanged=True,
        gameplay_classes_compared=len(classes),native_profile_supported=False,changed_jar_entries=changed,added_jar_entries=added,
        fixture_inputs=dict(path=str(paths[3]),sha256=sha(paths[3])),
        evidence=[dict(path=str(p),sha256=sha(p)) for p in paths + [baseline_path,Path(__file__)]],
        note='Native-only experimental comparison; all Java gameplay classes unchanged. Four wide native fixtures added to matching test companions.')
    write_new(folder / 'inputs.json',record)
    print(json.dumps(dict(inputs=str(folder / 'inputs.json'),runtime_sha256=sha(output),native_sha256=sha(dll))))


if __name__ == '__main__':
    main()
