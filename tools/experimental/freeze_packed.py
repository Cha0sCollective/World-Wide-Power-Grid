"""Freeze one already-built experimental artifact set; never overwrite evidence."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    directory = ROOT / 'local/frozen' / sys.argv[1]
    assert directory.resolve().is_relative_to((ROOT / 'local/frozen').resolve())
    directory.mkdir(exist_ok=False)
    version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                   if line.startswith('mod_version='))
    previous = json.loads((ROOT / 'local/frozen/prototype-02c/inputs.json').read_text())
    sources = {'runtime': ROOT / f'build/libs/wwpg-{version}.jar',
               'fixtures': ROOT / f'build/libs/wwpg-{version}-gametests.jar',
               'cee': Path(previous['artifacts']['cee']['path'])}
    if len(sys.argv) > 2:
        release = json.loads((ROOT / 'release/0.2.7/verification.json').read_text())
        for key in ('example', 'upstream_example'):
            source = Path(sys.argv[2]) / release['artifacts'][key]['path']
            assert sha(source) == release['artifacts'][key]['sha256'], key
            sources[key] = source
    artifacts = {}
    for key, source in sources.items():
        target = directory / source.name
        shutil.copy2(source, target)
        assert sha(source) == sha(target)
        artifacts[key] = dict(path=str(target.resolve()), sha256=sha(target))
    paths = sorted(p for p in (ROOT / 'src').rglob('*') if p.is_file())
    paths += [ROOT / 'build.gradle', ROOT / 'gradle.properties']
    manifest = dict(schema=1, base_commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                    java_home=previous['java_home'], artifacts=artifacts,
                    sources=[dict(path=p.relative_to(ROOT).as_posix(), sha256=sha(p)) for p in paths])
    (directory / 'inputs.json').write_text(json.dumps(manifest, indent=2) + '\n')
    def contents(path):
        with zipfile.ZipFile(path) as jar:
            return {n: hashlib.sha256(jar.read(n)).hexdigest() for n in jar.namelist() if not n.endswith('/')}
    old = contents(previous['artifacts']['runtime']['path'])
    new = contents(artifacts['runtime']['path'])
    changed = sorted(n for n in old.keys() | new.keys() if old.get(n) != new.get(n))
    native = {n: value for n, value in new.items() if n.endswith(('.dll', '.so', '.dylib'))}
    assert native and all(old.get(n) == value for n, value in native.items())
    (directory / 'jar-diff.json').write_text(json.dumps(dict(changed=changed, native_unchanged=native), indent=2) + '\n')
    print(json.dumps(dict(manifest=str(directory / 'inputs.json'), artifacts=artifacts, changed_entries=len(changed))))

if __name__ == '__main__': main()
