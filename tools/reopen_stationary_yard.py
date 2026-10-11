"""Extract an exact exported yard into a new, empty acceptance run directory."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import zipfile


def extract(archive_path, run, client=False):
    archive_path, run = Path(archive_path).resolve(), Path(run).resolve()
    if run.exists() and any(run.iterdir()):
        raise ValueError("Use a new empty run directory; existing saves are never replaced")
    with zipfile.ZipFile(archive_path) as archive:
        names = archive.namelist()
        manifests = [name for name in names if name.endswith('/WWPG_MANIFEST.json')]
        if len(manifests) != 1 or len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError("Invalid yard ZIP: manifest, duplicate entries or CRC error")
        manifest = json.loads(archive.read(manifests[0]))
        save_name = manifest['save_name']
        if not save_name or '/' in save_name or '\\' in save_name or save_name in ('.', '..'):
            raise ValueError("Invalid save folder name")
        payload = {}
        for info in archive.infolist():
            path = PurePosixPath(info.filename)
            if path.is_absolute() or '..' in path.parts or '\\' in info.filename or ':' in info.filename or not path.parts or path.parts[0] != save_name:
                raise ValueError(f"Unsafe ZIP path: {info.filename}")
            if info.is_dir():
                continue
            if info.external_attr >> 16 & 0o170000 == 0o120000:
                raise ValueError(f"ZIP symlink is not a native save file: {info.filename}")
            relative = PurePosixPath(*path.parts[1:]).as_posix()
            payload[relative] = archive.read(info)
        for name, record in manifest['files'].items():
            data = payload.get(name)
            if data is None or len(data) != record['bytes'] or hashlib.sha256(data).hexdigest() != record['sha256']:
                raise ValueError(f"Native save payload does not match its manifest: {name}")
        expected = set(manifest['files']) | {'WWPG_MANIFEST.json', 'WWPG_README.txt'}
        if set(payload) != expected:
            raise ValueError("ZIP contains unrecorded or missing save files")
    world = run / 'saves' / save_name if client else run / 'world'
    # Validate all payload paths before writing anything, then create a fresh save.
    for name in payload:
        if not (world / name).resolve().is_relative_to(world):
            raise ValueError(f"Save path escapes the destination: {name}")
    for name, data in payload.items():
        destination = world / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
    evidence = dict(schema=1, archive=str(archive_path), sha256=hashlib.sha256(archive_path.read_bytes()).hexdigest(),
                    profile=manifest['profile'], jar=manifest['jar'], files=len(payload),
                    extracted=True, reopened=False, source_save_name=save_name,
                    destination='client' if client else 'server')
    (run / 'extracted-yard.json').write_text(json.dumps(evidence, indent=2)+'\n', newline='\n')
    return evidence


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('--run', type=Path, required=True)
    parser.add_argument('--client', action='store_true', help='Extract into saves/<native save name> for graphical acceptance')
    args = parser.parse_args()
    print(json.dumps(extract(args.archive, args.run, args.client)))
