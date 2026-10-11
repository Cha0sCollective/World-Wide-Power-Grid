"""Extract a checksum-verified public release archive into a fresh directory."""
from pathlib import Path
import sys
import zipfile

archive, destination = Path(sys.argv[1]), Path(sys.argv[2]).resolve()
if destination.exists():
    raise ValueError('Use a fresh reference directory')
with zipfile.ZipFile(archive) as source:
    entries = source.infolist()
    if len(entries) > 10000 or sum(item.file_size for item in entries) > 128 * 1024 * 1024:
        raise ValueError('Reference archive exceeds extraction limits')
    for item in entries:
        target = (destination / item.filename).resolve()
        if not target.is_relative_to(destination) or '\\' in item.filename or ':' in item.filename:
            raise ValueError('Reference archive escapes its destination')
        if ((item.external_attr >> 16) & 0o170000) not in (0, 0o100000, 0o040000):
            raise ValueError('Reference archive contains a special file')
    source.extractall(destination)
