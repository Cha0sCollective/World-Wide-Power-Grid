"""Build the pinned, guarded PG native library; never runs the gameplay suite.

Run with a POSIX CMake/compiler environment (WSL is supported). Set --cc and
--ar for MinGW cross builds. Existing sources and published binaries are not
edited. Archive downloads are optional and checked against the source lock.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / 'tools/native'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def inputs():
    files = [p for p in NATIVE.iterdir()
             if p.name in ('sources.lock.json', 'pg-safety.patch', 'superlu-safety.patch', 'CMakeLists.txt')]
    files.append(Path(__file__).resolve())
    files.extend((NATIVE / 'jni-linux').glob('*.h'))
    return {str(p.relative_to(ROOT)): sha(p) for p in sorted(files)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform', choices=('windows', 'linux'), required=True)
    parser.add_argument('--archives', type=Path, required=True)
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--jni-include', type=Path, required=True)
    parser.add_argument('--jni-platform-include', type=Path)
    parser.add_argument('--cc')
    parser.add_argument('--ar')
    parser.add_argument('--ranlib')
    parser.add_argument('--jobs', type=int, default=4)
    parser.add_argument('--download', action='store_true')
    parser.add_argument('--source-tree', type=Path, help='Reuse a generated source tree inside --work')
    parser.add_argument('--reuse-config', action='store_true', help='Keep an existing matching CMake configuration')
    args = parser.parse_args()
    fingerprint = inputs()
    lock = json.loads((NATIVE / 'sources.lock.json').read_text())
    args.archives.mkdir(parents=True, exist_ok=True)
    args.work.mkdir(parents=True, exist_ok=True)
    source_root = args.source_tree or args.work / ('sources-' + hashlib.sha256(json.dumps(fingerprint, sort_keys=True).encode()).hexdigest()[:16])
    if not source_root.resolve().is_relative_to(args.work.resolve()):
        raise RuntimeError('Generated sources must remain inside the native work directory')
    prepared = source_root / 'PREPARED.json'
    previous = json.loads(prepared.read_text()) if prepared.exists() else None
    fresh = not source_root.exists()
    if fresh or previous != fingerprint:
        if not fresh and previous is None:
            raise RuntimeError('Incomplete source preparation; preserve it and use a new work directory')
        if previous and previous.get('tools/native/sources.lock.json') != fingerprint['tools/native/sources.lock.json']:
            raise RuntimeError('Use a new source tree when changing upstream revisions')
        source_root.mkdir(exist_ok=True)
        for name, entry in lock.items():
            archive = args.archives / (name + '-' + entry['commit'] + '.zip')
            if not archive.exists() and args.download:
                url = 'https://api.github.com/repos/' + entry['repository'] + '/zipball/' + entry['commit']
                request = urllib.request.Request(url, headers={'User-Agent': 'WWPG-native-build'})
                with urllib.request.urlopen(request, timeout=120) as response:
                    archive.write_bytes(response.read())
            if not archive.exists() or sha(archive) != entry['archive_sha256']:
                raise RuntimeError('Missing or mismatched pinned archive: ' + str(archive))
            with zipfile.ZipFile(archive) as contents:
                for member in contents.infolist():
                    relative = Path(*Path(member.filename).parts[1:])
                    if not relative.parts or member.is_dir():
                        continue
                    if relative.is_absolute() or '..' in relative.parts:
                        raise RuntimeError('Unsafe source archive path')
                    if name == 'PowerGrid' and relative.parts[0] not in ('native', 'LICENSE'):
                        continue
                    if not fresh:
                        # Only restore patched text on an explicit incremental build.
                        # Leave immutable BLAS sources and their compiled objects alone.
                        patch = {'PowerGrid': 'pg-safety.patch', 'superlu': 'superlu-safety.patch'}.get(name)
                        patched = [] if patch is None else [line[6:] for line in (NATIVE / patch).read_text().splitlines() if line.startswith('--- a/')]
                        if relative.as_posix() not in patched:
                            continue
                    path = source_root / name / relative
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(contents.read(member))
        for name, patch in (('PowerGrid', 'pg-safety.patch'), ('superlu', 'superlu-safety.patch')):
            # Upstream mixes CRLF and LF. Normalize only the patched text files.
            for line in (NATIVE / patch).read_text().splitlines():
                if line.startswith('--- a/'):
                    path = source_root / name / line[6:]
                    path.write_text(path.read_text(), encoding='utf-8', newline='\n')
            subprocess.run(['patch', '--batch', '-p1', '-i', str(NATIVE / patch)],
                           cwd=source_root / name, check=True)
        (source_root / 'PREPARED.json').write_text(json.dumps(fingerprint, indent=2))
    if not (source_root / 'PREPARED.json').exists():
        raise RuntimeError('Incomplete source preparation; preserve evidence and use a new work directory')
    build = args.work / args.platform
    command = ['cmake', '-S', str(NATIVE), '-B', str(build), '-DCMAKE_BUILD_TYPE=Release',
               '-DPG_SOURCE=' + str(source_root / 'PowerGrid'),
               '-DSUPERLU_SOURCE=' + str(source_root / 'superlu'),
               '-DOPENBLAS_SOURCE=' + str(source_root / 'OpenBLAS'),
               '-DJNI_INCLUDE=' + str(args.jni_include),
               '-DJNI_PLATFORM_INCLUDE=' + str(args.jni_platform_include or NATIVE / 'jni-linux')]
    if args.platform == 'windows':
        command += ['-DCMAKE_SYSTEM_NAME=Windows', '-DHOSTCC=gcc']
    for option, value in (('CMAKE_C_COMPILER', args.cc), ('CMAKE_AR', args.ar), ('CMAKE_RANLIB', args.ranlib)):
        if value:
            command.append('-D' + option + '=' + value)
    if args.reuse_config:
        cache = build / 'CMakeCache.txt'
        if not cache.exists() or previous is None or previous.get('tools/native/CMakeLists.txt') != fingerprint['tools/native/CMakeLists.txt']:
            raise RuntimeError('No matching CMake configuration to reuse')
        if str(source_root / 'PowerGrid') not in cache.read_text():
            raise RuntimeError('CMake configuration belongs to another source tree')
    else:
        subprocess.run(command, check=True)
    subprocess.run(['cmake', '--build', str(build), '--parallel', str(args.jobs), '--target',
                    'powergridNative', 'sparse_safety_tests'], check=True)
    if fingerprint != inputs():
        raise RuntimeError('Native inputs changed during build; no artifact staged')
    name = 'libpowergridNative7.' + ('dll' if args.platform == 'windows' else 'so')
    output = ROOT / 'build/guarded-pg-native' / args.platform
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(build / name, output / name)
    (output / 'manifest.json').write_text(json.dumps(dict(platform=args.platform,
        sha256=sha(output / name), inputs=fingerprint, source_lock=lock), indent=2) + '\n')
    licenses = ROOT / 'build/guarded-pg-native/licenses'
    licenses.mkdir(exist_ok=True)
    for project, source in (('PowerGrid', 'LICENSE'), ('superlu', 'License.txt'), ('OpenBLAS', 'LICENSE')):
        shutil.copyfile(source_root / project / source, licenses / (project + '-LICENSE.txt'))
    print(json.dumps(dict(platform=args.platform, library=str(output / name), sha256=sha(output / name))))


if __name__ == '__main__':
    main()
