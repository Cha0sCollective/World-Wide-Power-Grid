"""Freeze a built experimental runtime without replacing previous inputs."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    args = parser.parse_args()
    destination = (ROOT / 'local/frozen' / args.name).resolve()
    assert destination.is_relative_to(ROOT / 'local/frozen')
    destination.mkdir(parents=True, exist_ok=False)
    prior = json.loads((ROOT / 'local/frozen/prototype-02c/inputs.json').read_text())
    version = next(s.split('=', 1)[1] for s in (ROOT / 'gradle.properties').read_text().splitlines() if s.startswith('mod_version='))
    artifacts = {}
    for kind, source in [('runtime', ROOT / f'build/libs/wwpg-{version}.jar'),
                         ('fixtures', ROOT / f'build/libs/wwpg-{version}-gametests.jar'),
                         ('cee', Path(prior['artifacts']['cee']['path']))]:
        target = destination / source.name
        shutil.copy2(source, target)
        artifacts[kind] = dict(path=str(target), sha256=sha(target))
    with zipfile.ZipFile(prior['artifacts']['runtime']['path']) as old, zipfile.ZipFile(artifacts['runtime']['path']) as new:
        names = sorted(set(old.namelist()) | set(new.namelist()))
        changed = [n for n in names if n not in old.namelist() or n not in new.namelist() or old.read(n) != new.read(n)]
        assert not any(n.startswith('native/') for n in changed)
    sources = [dict(path=p.relative_to(ROOT).as_posix(), sha256=sha(p))
               for p in sorted((ROOT / 'src').rglob('*')) if p.is_file()]
    sources += [dict(path=n, sha256=sha(ROOT / n)) for n in ('build.gradle', 'gradle.properties')]
    value = dict(schema=1, base_commit='84bb53e', java_home=prior['java_home'], artifacts=artifacts,
                 sources=sources, native_unchanged=True, changed_jar_entries=changed)
    (destination / 'inputs.json').write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(inputs=str(destination / 'inputs.json'), artifacts=artifacts)))


if __name__ == '__main__':
    main()
