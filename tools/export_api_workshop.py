"""Export an acceptance-built API Workshop, locking both independently built add-ons."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import zipfile

from export_stationary_yard import renamed_level


VERSION = "0.2.0"
API_VERSION = "1.0.0"
NAMESPACE = "wwpg_api_world"
STATION_IDS = {
    **{f"A{i:02}": "wwpg_api_example:" + kind for i, kind in enumerate(
        ("load", "source", "ac_source", "capacitor", "inductor", "diode",
         "transformer", "voltage_control", "current_control"), 1)},
    "A10": "wwpg_api_upstream:panel_storage_indicator",
    "A11": "wwpg_api_upstream:api_shunt",
}
TICKETS = [(x, z) for x in range(8992 >> 4, (9072 >> 4) + 1)
           for z in range(8992 >> 4, (9072 >> 4) + 1)]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def native_tickets(world):
    """Check actual native saved chunk tickets after GameTest cleanup and shutdown."""
    raw = gzip.decompress((world / "data/chunks.dat").read_bytes())
    marker = b"\x0c\x00\x06Forced"
    if raw.count(marker) != 1:
        raise ValueError("Missing unambiguous native forced-chunk array")
    start = raw.index(marker) + len(marker)
    count = int.from_bytes(raw[start:start + 4], "big", signed=True)
    if count < 0 or start + 4 + count * 8 > len(raw):
        raise ValueError("Malformed native forced-chunk array")
    actual = {int.from_bytes(raw[start + 4 + i * 8:start + 12 + i * 8], "big")
              for i in range(count)}
    for x, z in TICKETS:
        if ((z & 0xffffffff) << 32 | (x & 0xffffffff)) not in actual:
            raise ValueError("Workshop tickets were lost after GameTest cleanup; export would contain unloaded wires/devices")
    return [list(pair) for pair in TICKETS]


def checked_jar(path):
    path = Path(path).resolve()
    if not path.is_file():
        raise ValueError(f"Missing installed artifact: {path}")
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None or "META-INF/neoforge.mods.toml" not in archive.namelist():
            raise ValueError(f"Invalid NeoForge mod jar: {path}")
    return dict(name=path.name, sha256=sha(path.read_bytes()), bytes=path.stat().st_size)


def export(run, jar, example, upstream_example, profile, backend, output, root):
    run, root, output = Path(run).resolve(), Path(root).resolve(), Path(output).resolve()
    world = run / "world"
    if not world.is_dir() or world.is_symlink():
        raise ValueError("Missing native workshop save or unsafe source symlink")
    if output.is_relative_to(world) or output.exists():
        raise ValueError("Use a new output ZIP outside the source save; existing artifacts are never replaced")
    artifacts = {"runtime": checked_jar(jar), "example": checked_jar(example),
                 "upstream_example": checked_jar(upstream_example)}
    proofs = [read_json(path) for path in sorted(run.glob("packaged-*.json"))]
    if len(proofs) < 4:
        raise ValueError("Export requires a setup pass followed by three recorded cold restart passes")
    for proof in proofs:
        if (proof.get("namespaces") != NAMESPACE or proof.get("profile") != profile
                or proof.get("backend") != backend or proof.get("sha256") != artifacts["runtime"]["sha256"]):
            raise ValueError("Run directory contains another namespace/profile/backend or runtime artifact")
        if not proof.get("completed") or proof.get("required_tests_passed", 0) < 1:
            raise ValueError("A workshop pass failed or did not complete; retain and investigate it before export")
        addons = proof.get("addon_artifacts", {})
        for role in ("example", "upstream_example"):
            locked = addons.get(role, {})
            if (locked.get("sha256") != artifacts[role]["sha256"]
                    or locked.get("artifact") != artifacts[role]["name"]):
                raise ValueError(f"Packaged run does not lock the supplied independent {role} jar")
    if proofs[0].get("phase") != "SETUP" or any(proof.get("phase") != "VERIFY" for proof in proofs[-3:]):
        raise ValueError("Missing three consecutive successful cold VERIFY passes after SETUP")
    stations = read_json(run / "api-workshop-stations.json")
    if (stations.get("target") != VERSION or stations.get("api_version") != API_VERSION
            or stations.get("profile") != profile or stations.get("backend") != backend
            or stations.get("phase") != "VERIFY" or stations.get("completed") is not True
            or stations.get("runtime_station_checks") != 11
            or stations.get("native_item_workflows") is not True
            or stations.get("loose_construction_wire_items") != 0):
        raise ValueError("Missing successful matching eleven-station workshop record")
    observed = {}
    for station in stations.get("stations", []):
        code = station.get("code")
        if (code in observed or station.get("checked") is not True
                or STATION_IDS.get(code) != station.get("registry_id")):
            raise ValueError("Workshop has a duplicate, unchecked or unexpected station")
        observed[code] = station["registry_id"]
    if observed != STATION_IDS:
        raise ValueError("Workshop omits a declared general, panel or board example")
    tickets = native_tickets(world)
    if stations.get("native_forced_chunks") != tickets:
        raise ValueError("Station report and native forced-chunk tickets disagree")
    selected = {world / "level.dat"}
    for directory in ("data", "serverconfig"):
        selected.update(path for path in (world / directory).rglob("*") if path.is_file())
    for kind in ("region", "entities", "poi"):
        path = world / kind / "r.17.17.mca"
        if path.is_file():
            selected.add(path)
    if not (world / "region/r.17.17.mca").is_file() or not (world / "entities/r.17.17.mca").is_file():
        raise ValueError("Missing native workshop region or PG wire entities")
    name = "Extension API Workshop" + (" — Pinout" if profile == "PINOUT" else "")
    contents = {}
    for path in sorted(selected):
        if not path.is_file() or path.is_symlink() or not path.resolve().is_relative_to(world):
            raise ValueError(f"Unsafe or missing native source file: {path}")
        data = path.read_bytes()
        contents[path.relative_to(world).as_posix()] = renamed_level(data, name) if path.name == "level.dat" else data
    manifest = dict(schema=1, target=VERSION, api_version=API_VERSION, profile=profile,
                    backend=backend, save_name=name, jar=artifacts["runtime"],
                    example_addons={role: artifacts[role] for role in ("example", "upstream_example")},
                    dependencies=read_json(root / "release/artifacts.json"),
                    stations=stations, packaged_workshop_proof=proofs[-1],
                    cold_restart_proofs=proofs[-3:], native_forced_chunks=tickets,
                    release_claim="Export inputs and development-save checks only. Final acceptance must reopen this exact ZIP and record its SHA256.",
                    files={path: dict(bytes=len(data), sha256=sha(data)) for path, data in contents.items()})
    if profile == "PINOUT":
        manifest["optional_dependencies"] = read_json(root / "release/optional-artifacts.json")
    contents["WWPG_MANIFEST.json"] = (json.dumps(manifest, indent=2, ensure_ascii=False) + "\n").encode("utf-8")
    contents["WWPG_README.txt"] = instructions(profile).encode("utf-8")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path, data in sorted(contents.items()):
            info = zipfile.ZipInfo(name + "/" + path, (2026, 10, 11, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    with zipfile.ZipFile(output) as archive:
        if archive.testzip() is not None:
            raise ValueError("Exact workshop ZIP failed CRC verification")
        for path, record in manifest["files"].items():
            data = archive.read(name + "/" + path)
            if len(data) != record["bytes"] or sha(data) != record["sha256"]:
                raise ValueError(f"Exact workshop ZIP changed native file: {path}")
    return dict(path=str(output), sha256=sha(output.read_bytes()), profile=profile,
                backend=backend, save_name=name, station_count=11, files=len(contents),
                reopened=False, release_acceptance=False)


def instructions(profile):
    optional = "Also install CC:Tweaked 1.120.2 and CC;CPG: Pinout 0.2.1.\n" if profile == "PINOUT" else ""
    return """WWPG Extension API Workshop

Extract this folder into your Minecraft instance's saves directory.
Install Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280,
CEE 1.1.3, Power Grid 0.6.2 and WWPG 0.2.0, plus BOTH example mod jars:
  wwpg-api-example-1.0.0.jar
  wwpg-upstream-api-example-1.0.0.jar
The exact file hashes and dependency pins are in WWPG_MANIFEST.json.
""" + optional + """
Spawn: (9004,64,8995). The tool chest is at (9000,64,8997).
Take goggles, both meters and the guide. Walk south along the white paths.
All API blocks have red/blue terminal markers on their north face.
Top pair: input/primary; lower pair, when present: output/secondary.
Empty-hand click an API block for readings. Sneak-click to change its setting.
The blocks glow when they receive accepted power or retain charge.

A01 Variable load: sneak-click the load. ON is 20 V, 0.4 A total, 8 W.
A02 DC supply: sneak-click the gold supply. The native resistor sees 20 V.
A03 AC supply: sneak-click the gold supply. ON is 50 Hz, 20 V peak,
    14.14 V RMS and 4 W total load power. Instantaneous voltage can be zero.
A04 Capacitor: sneak-click green storage. Its chat stored voltage settles
    from 20 to 10 V. Re-enable to restore 20 V. Toggle the gold feed OFF
    with discharge enabled to drain it completely. The gauge measures
    external terminal voltage; chat labels the separate internal stored value.
A05 Inductor: toggle the gold feed. Accepted current is about 0.2 A ON.
A06 Diode: toggle the gold feed. The native load sees about 19 V ON.
A07 Transformer: sneak-click copper coupling to change output 40 to 10 V.
    This is an ideal API model; native transformers retain native assemblies.
A08 Voltage control: sneak-click purple control. Output changes 40 to 0 V.
A09 Current control: sneak-click orange control. ON is -20 V and 0.2 A.
    The negative voltage follows current-source orientation and is expected.
A10 Native CEE panel: open/close the adjacent feed switch. Its custom storage
    indicator visibly fades OFF and returns ON at 70 V after a few seconds.
A11 Native PG board: open/close the adjacent feed switch. Its assembled custom
    100-ohm shunt and gauge change between 20 V/about 0.2 A and zero.

Restore all gold feeds and controls enabled ON after testing. Restore the
green capacitor's discharge setting OFF. The native panel/board feed switches
should be closed. These examples use ideal developer supplies, not progression
balanced machines. API workshop coverage is separate from the Stationary Yard.
Make a clean backup before dismantling; native cutters, spools and meters work
on these example terminals. Normal player wire drops remain collectible.
Construction debris was removed only during export preparation.
"""


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--example", type=Path, required=True)
    parser.add_argument("--upstream-example", type=Path, required=True)
    parser.add_argument("--profile", choices=("BASE", "PINOUT"), required=True)
    parser.add_argument("--backend", choices=("NATIVE", "JAVA"), required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    print(json.dumps(export(args.run, args.jar, args.example, args.upstream_example,
                            args.profile, args.backend, args.output, args.root)))
