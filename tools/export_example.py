"""Export the isolated acceptance-built world; do not export the regression world."""
import argparse
import gzip
import hashlib
import json
import zipfile
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument("--world",type=Path,default=Path("run/example/world"))
parser.add_argument("--output",type=Path,default=Path("build/distributions/wwpg-0.1.0-beta.1-example.zip"))
parser.add_argument("--name",default="WWPG Example",help="Save folder inside the ZIP")
parser.add_argument("--level-name",help="ASCII name shown in Minecraft's world list")
args=parser.parse_args()
if not args.name or Path(args.name).name != args.name or "/" in args.name or "\\" in args.name or args.name in {".",".."}:
    raise SystemExit("The save folder must be a single folder name.")
world=args.world.resolve()
if not (world/"level.dat").is_file():raise SystemExit("Example world has not been built.")
files=[world/"level.dat"]
for directory in ["data","serverconfig"]:
    if (world/directory).exists():files.extend(p for p in (world/directory).rglob("*") if p.is_file())
for directory in ["region","entities","poi"]:
    region=world/directory/"r.0.0.mca"
    if region.is_file():files.append(region)
args.output.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(args.output,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for path in sorted(files):
        info=zipfile.ZipInfo(args.name+"/"+path.relative_to(world).as_posix(),(2026,10,6,0,0,0))
        info.compress_type=zipfile.ZIP_DEFLATED
        content=path.read_bytes()
        if path.name=="level.dat" and args.level_name is not None:
            # Replace only the named NBT string, preserving every other saved
            # tag. Keeping this optional leaves historical exports unchanged.
            raw=gzip.decompress(content)
            marker=b"\x08\x00\x09LevelName"
            if raw.count(marker)!=1:raise SystemExit("Expected one LevelName string in level.dat")
            start=raw.index(marker)+len(marker)
            length=int.from_bytes(raw[start:start+2],"big")
            name=args.level_name.encode("ascii")
            if len(name)>65535:raise SystemExit("World display name is too long")
            raw=raw[:start]+len(name).to_bytes(2,"big")+name+raw[start+2+length:]
            content=gzip.compress(raw,mtime=0)
        archive.writestr(info,content)
print(json.dumps({"path":str(args.output.resolve()),"sha256":hashlib.sha256(args.output.read_bytes()).hexdigest(),"files":len(files)}))
