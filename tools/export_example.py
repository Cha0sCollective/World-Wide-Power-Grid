"""Export the isolated acceptance-built world; do not export the regression world."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument("--world",type=Path,default=Path("run/example/world"))
parser.add_argument("--output",type=Path,default=Path("build/distributions/wwpg-0.1.0-beta.1-example.zip"))
args=parser.parse_args()
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
        info=zipfile.ZipInfo("WWPG Example/"+path.relative_to(world).as_posix(),(2026,10,6,0,0,0))
        info.compress_type=zipfile.ZIP_DEFLATED
        archive.writestr(info,path.read_bytes())
print(json.dumps({"path":str(args.output.resolve()),"sha256":hashlib.sha256(args.output.read_bytes()).hexdigest(),"files":len(files)}))
