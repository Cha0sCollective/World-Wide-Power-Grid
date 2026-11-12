"""Freeze a native-only jar variant; preserve all gameplay class and fixture bytes."""
import argparse
import json
from pathlib import Path
import re
import shutil
import zipfile

from cpu_profile import write_new
from freeze_tree import ROOT, sha
from native_stages import MAGIC, MARKER, STAGES, WINDOWS_DLL


def local_path(value):
    if value.startswith('/mnt/g/'): value = 'G:/' + value[len('/mnt/g/'):]
    return Path(value)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--base-inputs', required=True)
    parser.add_argument('--native-build', required=True)
    parser.add_argument('--native-checks', required=True)
    parser.add_argument('--role', choices=('baseline','profile'), required=True)
    args = parser.parse_args()
    base_path, build_path, checks_path = (ROOT / v for v in (args.base_inputs,args.native_build,args.native_checks))
    base, build, checks = (json.loads(p.read_text()) for p in (base_path,build_path,checks_path))
    assert checks['passed'] and checks['build']['sha256'] == sha(build_path)
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in base['sources'])
    original = Path(base['artifacts']['runtime']['path'])
    assert sha(original) == base['artifacts']['runtime']['sha256']
    name = 'libpowergridNative7.dll' if args.role == 'baseline' else 'libpowergridNativeProfile.dll'
    native = local_path(build['artifacts'][name]['path'])
    assert sha(native) == build['artifacts'][name]['sha256']
    destination = (ROOT / 'local/frozen' / args.name).resolve()
    assert destination.is_relative_to(ROOT / 'local/frozen')
    destination.mkdir(parents=True,exist_ok=False)
    runtime = destination / ('wwpg-0.2.7-experimental.native-stages-' + args.role + '.jar')
    marker = dict(schema=1, diagnostic=args.role == 'profile', native_sha256=sha(native),
        profile_magic=MAGIC, profile_stages=list(STAGES),
        native_build=dict(path=args.native_build,sha256=sha(build_path)),
        original_runtime_sha256=sha(original), gameplay_class_bytes_unchanged=True)
    manifest_name = 'native/wwpg/safe/guarded.properties'
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(runtime,'w') as new:
        assert MARKER not in old.namelist()
        properties = old.read(manifest_name).decode('utf-8')
        assert len(re.findall(r'windows\.sha256=[0-9a-f]{64}',properties)) == 1
        properties = re.sub(r'(windows\.sha256=)[0-9a-f]{64}', lambda m:m[1] + sha(native), properties)
        for item in old.infolist():
            data = native.read_bytes() if item.filename == WINDOWS_DLL else properties.encode() if item.filename == manifest_name else old.read(item)
            new.writestr(item,data)
        new.writestr(MARKER,(json.dumps(marker,indent=2) + '\n').encode())
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(runtime) as new:
        changed = [n for n in old.namelist() if old.read(n) != new.read(n)]
        assert set(changed) == {WINDOWS_DLL,manifest_name}
        assert set(new.namelist()) - set(old.namelist()) == {MARKER}
        classes = [n for n in old.namelist() if n.endswith('.class')]
        assert all(old.read(n) == new.read(n) for n in classes)
    artifacts = dict(runtime=dict(path=str(runtime),sha256=sha(runtime)))
    for role in ('fixtures','cee'):
        item = base['artifacts'][role]
        path = Path(item['path'])
        assert sha(path) == item['sha256']
        copied = destination / path.name
        shutil.copy2(path,copied)
        artifacts[role] = dict(path=str(copied),sha256=sha(copied))
    data = dict(schema=1,java_home=base['java_home'],sources=base['sources'],artifacts=artifacts,
        native_unchanged=False,gameplay_classes_unchanged=True,gameplay_classes_compared=len(classes),
        native_profile_supported=args.role == 'profile',role=args.role,changed_jar_entries=changed,added_jar_entries=[MARKER],
        evidence=[dict(path=p.relative_to(ROOT).as_posix(),sha256=sha(p)) for p in (base_path,build_path,checks_path,Path(__file__))],
        note='Native-only experimental artifact. Mod version metadata and all gameplay classes retain wiretree.11 identity. No gameplay acceptance is established by freezing.')
    write_new(destination / 'inputs.json',data)
    print(json.dumps(dict(inputs=str(destination / 'inputs.json'),runtime=artifacts['runtime'],native_sha256=sha(native),classes_compared=len(classes))))


if __name__ == '__main__':
    main()
