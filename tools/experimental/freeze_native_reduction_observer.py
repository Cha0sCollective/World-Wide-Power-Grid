"""Freeze an explicitly diagnostic native reduction path-count artifact."""
import argparse
import json
from pathlib import Path
import re
import shutil
import zipfile

from cpu_profile import write_new
from freeze_tree import ROOT, sha
from native_stages import MARKER, WINDOWS_DLL, inspect_runtime
from run_native_profile_checks import windows_path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--candidate-inputs', required=True)
    parser.add_argument('--observer-build', required=True)
    args = parser.parse_args()
    base_path, build_path = (ROOT / p for p in (args.candidate_inputs, args.observer_build))
    base, build = (json.loads(p.read_text()) for p in (base_path, build_path))
    assert base['role'] == 'candidate' and base['gameplay_classes_unchanged']
    assert build['diagnostic'] and build['diagnostic_kind'] == 'native-reduction-counts'
    assert build['tracked_rows'] == 14925 and build['original_candidate_source_restored']
    assert all(sha(ROOT / p['path']) == p['sha256'] for p in base['sources'])
    assert all(sha(Path(p['path'])) == p['sha256'] for p in base['artifacts'].values())
    for item in build['inputs'] + build['logs'] + [build['artifact'], build['candidate_build']]:
        assert sha(windows_path(item['path'])) == item['sha256'], item['path']
    original = Path(base['artifacts']['runtime']['path'])
    original_marker = inspect_runtime(original, False)
    assert original_marker['variant'] == 'native-reduction-candidate'
    assert original_marker['native_build']['sha256'] == build['candidate_build']['sha256']
    native = windows_path(build['artifact']['path'])
    folder = (ROOT / 'local/frozen' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local/frozen')
    folder.mkdir(parents=True, exist_ok=False)
    runtime = folder / 'wwpg-0.2.7-experimental.native-reduction-counts.jar'
    marker = dict(schema=1, diagnostic=True, diagnostic_kind='native-reduction-counts',
        native_sha256=sha(native), tracked_rows=14925,
        native_build=dict(path=str(build_path), sha256=sha(build_path)),
        original_runtime_sha256=sha(original), gameplay_class_bytes_unchanged=True)
    properties_name = 'native/wwpg/safe/guarded.properties'
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(runtime, 'w') as new:
        props = old.read(properties_name).decode()
        assert len(re.findall(r'windows\.sha256=[0-9a-f]{64}', props)) == 1
        props = re.sub(r'(windows\.sha256=)[0-9a-f]{64}', lambda m: m[1] + sha(native), props)
        for item in old.infolist():
            data = (native.read_bytes() if item.filename == WINDOWS_DLL else
                    props.encode() if item.filename == properties_name else
                    (json.dumps(marker, indent=2) + '\n').encode() if item.filename == MARKER else
                    old.read(item))
            new.writestr(item, data)
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(runtime) as new:
        assert set(old.namelist()) == set(new.namelist())
        changed = sorted(n for n in old.namelist() if old.read(n) != new.read(n))
        assert set(changed) == {WINDOWS_DLL, properties_name, MARKER}
        classes = [n for n in old.namelist() if n.endswith('.class')]
        assert all(old.read(n) == new.read(n) for n in classes)
    artifacts = dict(runtime=dict(path=str(runtime), sha256=sha(runtime)))
    for role in ('fixtures', 'cee'):
        item = base['artifacts'][role]
        target = folder / Path(item['path']).name
        shutil.copy2(item['path'], target)
        artifacts[role] = dict(path=str(target), sha256=sha(target))
    record = dict(schema=1, diagnostic=True, diagnostic_kind=marker['diagnostic_kind'],
        java_home=base['java_home'], sources=base['sources'], artifacts=artifacts,
        native_unchanged=False, gameplay_classes_unchanged=True, gameplay_classes_compared=len(classes),
        changed_jar_entries=changed, native_profile_supported=False,
        evidence=[dict(path=str(p), sha256=sha(p)) for p in (base_path, build_path, Path(__file__))],
        note='Atomic counts only for 14925-row reductions, reported at solver destruction. Not a clean performance runtime.')
    write_new(folder / 'inputs.json', record)
    print(json.dumps(dict(inputs=str(folder / 'inputs.json'), runtime=artifacts['runtime'], native=build['artifact'])))


if __name__ == '__main__':
    main()
