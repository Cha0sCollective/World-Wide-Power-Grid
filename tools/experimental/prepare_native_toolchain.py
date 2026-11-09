"""Extract a pinned MinGW toolchain inside this worktree; never install system packages.

Run with Python 3 in the existing Ubuntu-22.04 WSL distribution.
APT supplies authenticated package-index metadata and verifies downloads; the
manifest additionally binds every archive to that metadata's SHA-256.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PACKAGES = {
    'gcc-mingw-w64-x86-64-posix': '10.3.0-14ubuntu1+24.3',
    'gcc-mingw-w64-x86-64-posix-runtime': '10.3.0-14ubuntu1+24.3',
    'gcc-mingw-w64-base': '10.3.0-14ubuntu1+24.3',
    'binutils-mingw-w64-x86-64': '2.38-3ubuntu1+9build1',
    'mingw-w64-x86-64-dev': '8.0.0-1',
    'mingw-w64-common': '8.0.0-1',
}


def sha(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): digest.update(block)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    args = parser.parse_args()
    folder = (ROOT / 'local' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local')
    folder.mkdir(parents=True, exist_ok=False)
    archives, extracted = folder / 'archives', folder / 'root'
    archives.mkdir()
    extracted.mkdir()
    metadata = {}
    for name, version in PACKAGES.items():
        text = subprocess.check_output(['apt-cache', 'show', name + '=' + version], text=True)
        entries = []
        for paragraph in text.strip().split('\n\n'):
            fields = dict(line.split(': ', 1) for line in paragraph.splitlines() if ': ' in line and not line[0].isspace())
            if fields.get('Package') == name and fields.get('Version') == version: entries.append(fields)
        assert len(entries) == 1 and entries[0].get('SHA256'), name
        metadata[name] = entries[0]
    (folder / 'package-index.json').write_text(json.dumps(metadata, indent=2) + '\n')
    specs = [name + '=' + version for name, version in PACKAGES.items()]
    with (folder / 'download.log').open('x') as log:
        subprocess.run(['apt-get', 'download', *specs], cwd=archives, stdout=log, stderr=subprocess.STDOUT, check=True)
    records = []
    for archive in sorted(archives.glob('*.deb')):
        name = subprocess.check_output(['dpkg-deb', '-f', str(archive), 'Package'], text=True).strip()
        version = subprocess.check_output(['dpkg-deb', '-f', str(archive), 'Version'], text=True).strip()
        assert name in PACKAGES and version == PACKAGES[name]
        assert sha(archive) == metadata[name]['SHA256'], name
        records.append(dict(package=name, version=version, path=str(archive), sha256=sha(archive)))
    assert {r['package'] for r in records} == set(PACKAGES)
    with (folder / 'extract.log').open('x') as log:
        for record in records:
            subprocess.run(['dpkg-deb', '-x', record['path'], str(extracted)], stdout=log, stderr=subprocess.STDOUT, check=True)
    compiler = extracted / 'usr/bin/x86_64-w64-mingw32-gcc-posix'
    assert compiler.is_file()
    source = folder / 'probe.c'
    source.write_text('#include <windows.h>\nint main(void) { return sizeof(void *) == 8 ? 0 : 1; }\n')
    target = folder / 'probe.exe'
    command = [str(compiler), '-O2', '-static', '-mavx2', str(source), '-o', str(target)]
    with (folder / 'probe-build.log').open('x') as log:
        subprocess.run(command, cwd=folder, stdout=log, stderr=subprocess.STDOUT, check=True)
    version = subprocess.check_output([str(compiler), '--version'], text=True)
    manifest = dict(schema=1, system_install=False, host=os.uname()._asdict() if hasattr(os.uname(), '_asdict') else list(os.uname()),
        compiler=str(compiler), compiler_version=version, packages=records, command=command,
        probe=dict(path=str(target), sha256=sha(target)),
        evidence=[dict(path=str(p), sha256=sha(p)) for p in (Path(__file__), folder / 'package-index.json',
            folder / 'download.log', folder / 'extract.log', folder / 'probe-build.log', source)])
    (folder / 'inputs.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(json.dumps(dict(manifest=str(folder / 'inputs.json'), compiler=str(compiler), probe_sha256=sha(target))))


if __name__ == '__main__':
    main()
