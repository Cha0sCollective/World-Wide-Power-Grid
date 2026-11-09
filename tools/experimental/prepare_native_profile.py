"""Prepare isolated, hash-locked native sources without replacing runtime assets."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): digest.update(block)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--download', action='store_true')
    args = parser.parse_args()
    folder = (ROOT / 'local' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local')
    folder.mkdir(parents=True, exist_ok=False)
    archives = ROOT / 'local/native-source-archives'
    archives.mkdir(exist_ok=True)
    source_root = folder / 'sources'
    lock_path = ROOT / 'tools/native/sources.lock.json'
    lock = json.loads(lock_path.read_text())
    records = []
    for name, entry in lock.items():
        archive = archives / (name + '-' + entry['commit'] + '.zip')
        if not archive.exists():
            assert args.download, 'Explicit --download required'
            url = 'https://api.github.com/repos/' + entry['repository'] + '/zipball/' + entry['commit']
            print(json.dumps(dict(downloading=name, url=url)), flush=True)
            request = urllib.request.Request(url, headers={'User-Agent': 'WWPG-native-profile'})
            with urllib.request.urlopen(request, timeout=60) as response, archive.open('xb') as stream:
                shutil.copyfileobj(response, stream)
        assert sha(archive) == entry['archive_sha256'], archive
        records.append(dict(path=str(archive), sha256=sha(archive), bytes=archive.stat().st_size))
        with zipfile.ZipFile(archive) as contents:
            for member in contents.infolist():
                relative = Path(*Path(member.filename).parts[1:])
                if not relative.parts or member.is_dir(): continue
                assert not relative.is_absolute() and '..' not in relative.parts
                if name == 'PowerGrid' and relative.parts[0] not in ('native','LICENSE'): continue
                path = source_root / name / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                with path.open('xb') as stream: stream.write(contents.read(member))
        print(json.dumps(dict(prepared=name, archive_sha256=sha(archive))), flush=True)
    patch_records = []
    with (folder / 'patch.log').open('x') as stream:
        for name, patch_name in (('PowerGrid','pg-safety.patch'),('superlu','superlu-safety.patch')):
            patch = ROOT / 'tools/native' / patch_name
            for line in patch.read_text().splitlines():
                if line.startswith('--- a/'):
                    path = source_root / name / line[6:]
                    path.write_text(path.read_text(), encoding='utf-8', newline='\n')
            subprocess.run(['patch','--batch','-p1','-i',str(patch)], cwd=source_root / name,
                stdout=stream, stderr=subprocess.STDOUT, check=True)
            patch_records.append(dict(path=str(patch), sha256=sha(patch)))
    baseline = source_root / 'PowerGrid'
    profile = source_root / 'PowerGridProfile'
    shutil.copytree(baseline, profile)
    inventory = [dict(path=str(path), sha256=sha(path)) for path in sorted(baseline.rglob('*')) if path.is_file()]
    data = dict(schema=1, source_lock=lock, archives=records, patches=patch_records,
        baseline_inventory=inventory, baseline=str(baseline), profile=str(profile),
        evidence=[dict(path=str(p), sha256=sha(p)) for p in (Path(__file__), lock_path, folder / 'patch.log')])
    (folder / 'prepared.json').write_text(json.dumps(data, indent=2) + '\n')
    print(json.dumps(dict(prepared=str(folder / 'prepared.json'), baseline_files=len(inventory))), flush=True)


if __name__ == '__main__':
    main()
