"""Export the isolated, acceptance-built stationary yard and preserve its Nether/computers."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import zipfile


def sha(data):
    return hashlib.sha256(data).hexdigest()


def required_yard_tickets(world):
    """Validate native forceload data after the GameTest runner has exited."""
    tickets = {}
    for dimension, cx, cz in (("", 16, 14), ("DIM-1", 2, 1)):
        path = world / dimension / "data/chunks.dat"
        raw = gzip.decompress(path.read_bytes())
        marker = b"\x0c\x00\x06Forced"
        if raw.count(marker) != 1:
            raise ValueError("Missing unambiguous native Forced long array")
        start = raw.index(marker) + len(marker)
        count = int.from_bytes(raw[start:start + 4], "big", signed=True)
        if count < 0 or start + 4 + count * 8 > len(raw):
            raise ValueError("Invalid native forced chunk data")
        actual = {int.from_bytes(raw[start + 4 + i * 8:start + 12 + i * 8], "big") for i in range(count)}
        required = [(x, z) for x in range(cx - 1, cx + 2) for z in range(cz - 1, cz + 2)]
        if any(((z & 0xffffffff) << 32 | (x & 0xffffffff)) not in actual for x, z in required):
            raise ValueError("GameTest cleanup removed the yard's native Nether source/load tickets; do not export an unpowered route")
        tickets[dimension or "overworld"] = required
    return tickets


def renamed_level(data, name):
    raw = gzip.decompress(data)
    marker = b"\x08\x00\x09LevelName"
    if raw.count(marker) != 1:
        raise ValueError("Expected exactly one native LevelName tag")
    start = raw.index(marker) + len(marker)
    length = int.from_bytes(raw[start:start + 2], "big")
    encoded = name.encode("utf-8")
    return gzip.compress(raw[:start] + len(encoded).to_bytes(2, "big") + encoded + raw[start + 2 + length:], mtime=0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, required=True, help="Dedicated wwpg_yard run directory")
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--profile", choices=["BASE", "PINOUT"], required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    run, root = args.run.resolve(), args.root.resolve()
    world = run / "world"
    if args.output.resolve().is_relative_to(world):
        raise ValueError("Export outside the source save; never overwrite its native files")
    stations = json.loads((run / "stationary-yard-stations.json").read_text())
    coverage = json.loads((run / "stationary-yard-coverage.json").read_text())
    proofs = [json.loads(p.read_text()) for p in sorted(run.glob("packaged-*.json"))]
    if not proofs or any(p["namespaces"] != "wwpg_yard" or p["profile"] != args.profile for p in proofs):
        raise ValueError("Export only a dedicated yard world, never a regression/combined test world")
    artifact_hash = sha(args.jar.read_bytes())
    if proofs[-1]["sha256"] != artifact_hash:
        raise ValueError("Latest packaged yard proof does not match the supplied jar")
    if not proofs[-1].get("completed") or proofs[-1].get("required_tests_passed", 0) <= 0:
        raise ValueError("Latest packaged yard run did not complete successfully")
    if stations.get("profile") != args.profile or not stations["stations"] or not all(s["checked"] for s in stations["stations"]):
        raise ValueError("Missing matching, checked station record")
    if coverage.get("profile") != args.profile or not coverage["stations"] or not all(s["checked"] for s in coverage["stations"]):
        raise ValueError("Missing matching, checked complete yard coverage")
    matrix = json.loads((root / "release/0.1.0-beta.5/content-matrix.json").read_text())
    mapping = []
    for behavior in matrix["content"]:
        if not behavior["release_scope"] or args.profile == "BASE" and behavior["id"] == "pinout:pinout":
            continue
        observed, workflows = [], []
        for station in coverage["stations"]:
            if any(item["id"] == behavior["id"] and item["registry"] == behavior["registry"] for item in station["observed_content"]):
                observed.append(station["code"])
            elif behavior["registry"] == "block" and behavior["id"] in station["checked_workflow_content"]:
                workflows.append(station["code"])
        if not observed and not workflows:
            raise ValueError(f"No checked exhibit or assembly/repair workflow for {behavior['registry']} {behavior['id']}")
        mapping.append(dict(id=behavior["id"], registry=behavior["registry"],
                            observed_stations=observed, checked_workflow_stations=workflows,
                            electrical_expectation=behavior["acceptance_circuit"], fixtures=behavior["fixtures"]))
    if not (world / "level.dat").is_file():
        raise ValueError("Missing native saved level")
    native_tickets = required_yard_tickets(world)
    # The complete base yard fits in the first 512 x 512 region. Keep all
    # native SavedData, including forced chunks, link identities and node data.
    selected = {world / "level.dat"}
    for directory in ("data", "serverconfig", "DIM-1/data"):
        selected.update(p for p in (world / directory).rglob("*") if p.is_file())
    for dimension in ("", "DIM-1"):
        for kind in ("region", "entities", "poi"):
            region = world / dimension / kind / "r.0.0.mca"
            if region.is_file():
                selected.add(region)
    if not (world / "DIM-1/region/r.0.0.mca").is_file():
        raise ValueError("Missing native Nether transformer route")
    if args.profile == "PINOUT":
        selected.update(p for p in (world / "computercraft").rglob("*") if p.is_file())
        if not any(p.name == "startup.lua" for p in selected) or not any(p.name == "wwpg.lua" for p in selected):
            raise ValueError("Pinout export has no playable computer programs")
    elif (world / "computercraft").exists():
        raise ValueError("Base world unexpectedly contains optional computer state")
    name = "Stationary Test Yard" + (" — Pinout" if args.profile == "PINOUT" else "")
    contents = {}
    for path in sorted(selected):
        if path.is_symlink() or not path.resolve().is_relative_to(world):
            raise ValueError(f"Unsafe source path: {path}")
        data = path.read_bytes()
        contents[path.relative_to(world).as_posix()] = renamed_level(data, name) if path.name == "level.dat" else data
    dependencies = json.loads((root / "release/artifacts.json").read_text())
    optional = json.loads((root / "release/optional-artifacts.json").read_text()) if args.profile == "PINOUT" else None
    manifest = dict(schema=1, target="0.1.0-beta.5", profile=args.profile, save_name=name,
                    acceptance_record="Published beta.5 verification record, matched by this exact ZIP's SHA-256",
                    release_claim="This immutable manifest records export inputs; final release acceptance is recorded separately after testing this exact ZIP",
                    jar=dict(name=args.jar.name, sha256=artifact_hash), dependencies=dependencies,
                    native_nether_forced_chunks=native_tickets,
                    stations=coverage, behavior_mapping=mapping, packaged_yard_proof=proofs[-1],
                    files={p: dict(bytes=len(data), sha256=sha(data)) for p, data in contents.items()})
    if optional is not None:
        manifest["optional_dependencies"] = optional
    contents["WWPG_MANIFEST.json"] = (json.dumps(manifest, indent=2) + "\n").encode()
    instructions = """WWPG Stationary Test Yard

Extract this save folder into your Minecraft instance's saves directory.
Use Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create 6.0.10-280,
CEE 1.1.3, Power Grid 0.6.2 and the exact WWPG candidate jar listed in
WWPG_MANIFEST.json. Install the pinned dependencies listed in that manifest.
"""
    if args.profile == "PINOUT":
        instructions += "Also install CC:Tweaked 1.120.2 and CC;CPG: Pinout 0.2.1.\n"
    instructions += """
At spawn (8, 64, 16), take goggles, meters and the yard guide from the chest.
The white paths lead to boards, panels, transformers, meters and live equipment.
Click a station's feed switch with an empty hand to stop and restart it.
Station books give expected readings, actions and reset instructions.
S65 includes a walkable Nether route with a mixed load at the other end.
Wear goggles to see precise gauge readings; board currents can be milliamps.
Dark glass-insulator examples are expected: they carry essentially no power.
Switch OFF before repair. A burned PG bulb takes one click to remove and a
second click with a healthy, correctly rated bulb to install the replacement.
Keep a clean copy before destructive experiments. Native player wire drops
are not deleted during play; export cleanup removed only construction debris.
"""
    if args.profile == "PINOUT":
        instructions += """
At the final two stations, open a computer and type:
  wwpg start       All eight pins ON
  wwpg stop        All eight pins OFF
  wwpg demo        Cycle the visible lamps
  wwpg pattern 85  Odd pins ON
  wwpg volts       Native signed readings relative to common pin 9
  wwpg layout      Pin identities and help
Pin 3 controls a board relay and lamp; a panel reads pin 5.
An ON pin reads slightly below zero relative to common because of native
contact resistance. An OFF pin reads about -70 V in these loaded examples.
"""
    contents["WWPG_README.txt"] = instructions.encode()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path, data in sorted(contents.items()):
            info = zipfile.ZipInfo(name + "/" + path, (2026, 10, 10, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    # Validate the actual ZIP payload against the native source bytes, rather
    # than trusting the development save or the list we intended to export.
    with zipfile.ZipFile(args.output) as archive:
        if archive.testzip() is not None:
            raise ValueError("Exported ZIP failed its CRC check")
        for path, record in manifest["files"].items():
            if sha(archive.read(name + "/" + path)) != record["sha256"]:
                raise ValueError(f"Exported native file changed: {path}")
    print(json.dumps(dict(path=str(args.output.resolve()), sha256=sha(args.output.read_bytes()),
                          files=len(contents), profile=args.profile, save_name=name,
                          reopened=False, stationary_completion=False)))


if __name__ == "__main__":
    main()
