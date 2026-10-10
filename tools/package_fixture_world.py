"""Publish separate expanded-yard evidence without replacing beta.2 artifacts."""
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
record = root / "release/examples/expanded-yard-v1"
output = root / "build/distributions"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


baseline = output / "0.1.0-beta.2/wwpg-0.1.0-beta.2.jar"
manifest = json.loads((root / "release/0.1.0-beta.2/distribution.json").read_text(encoding="utf-8"))
expected = next(a["sha256"] for a in manifest["artifacts"] if a["file"] == baseline.name)
if sha(baseline) != expected:
    raise SystemExit("The baseline must be the unchanged published beta.2 jar")
harness = root / "build/libs/wwpg-0.1.0-beta.2.jar"
template = "data/wwpg_showroom/structure/empty.nbt"
with zipfile.ZipFile(baseline) as original, zipfile.ZipFile(harness) as revised:
    if revised.read(template) != original.read("data/wwpg/structure/empty.nbt"):
        raise SystemExit("The additional GameTest namespace must use the original empty template")

    def runtime_entries(archive):
        return {name: archive.read(name) for name in archive.namelist()
                if not name.endswith("/") and name != template
                and not name.startswith("org/cha0scollective/wwpg/gametest/")}

    original_entries = runtime_entries(original)
    if original_entries != runtime_entries(revised):
        raise SystemExit("Runtime entries differ from the published beta.2 jar")
    changed = sorted(name for name in set(original.namelist()) | set(revised.namelist())
                     if not name.endswith("/") and (name not in original.namelist()
                     or name not in revised.namelist() or original.read(name) != revised.read(name)))

checks, evidence = [], {}
for name in ["native-setup", "native-verify", "java-setup", "java-verify", "export-native", "export-java"]:
    path = root / "build" / f"expanded-yard-{name}.log"
    text = path.read_text(encoding="utf-8", errors="replace")
    markers = ["WWPG_SHOWROOM_PASSED", "WWPG_EXAMPLE_VISIBLE_PASSED", "WWPG_EXAMPLE_CLEANUP",
               "All 1 required tests passed", "GAME TESTS COMPLETE", "BUILD SUCCESSFUL"]
    if any(marker not in text for marker in markers) or "Failed to load data for block entity" in text:
        raise SystemExit(f"Missing clean passing world evidence: {path}")
    if name.endswith("setup") and "removed 3 loose wire/spool items" not in text:
        raise SystemExit("Representative dropped-wire cleanup was not exercised")
    checks.append({"log": path.relative_to(root).as_posix(), "sha256": sha(path), "passed_world_fixtures": 1})
    evidence[path.relative_to(root).as_posix()] = path

equations = 0
for path in (root / "build/test-results/test").glob("TEST-*.xml"):
    suite = ET.parse(path).getroot()
    if int(suite.attrib.get("failures", 0)) or int(suite.attrib.get("errors", 0)):
        raise SystemExit("Equation check failed")
    equations += int(suite.attrib["tests"])
    evidence["equation-tests/" + path.name] = path
if equations != 4:
    raise SystemExit("Expected four passing equation checks")

directories = ["showroom-native-final", "showroom-java-final", "showroom-export-native", "showroom-export-java"]
coverage = None
for directory in directories:
    path = root / "run" / directory / "showroom-coverage.json"
    current = json.loads(path.read_text(encoding="utf-8"))
    if (current["live_board_components"], current["live_panel_attachments"],
        current["live_behavior_groups"], current["declared_behavior_groups"],
        current["loose_wire_items_remaining"], current["pg_connected_wire_entities"]) != (28, 11, 85, 134, 0, 282):
        raise SystemExit(f"Unexpected world coverage or connected wire count: {directory}")
    if coverage is not None and current != coverage:
        raise SystemExit("Creation/restart/export coverage differs between backends")
    coverage = current
    evidence[path.relative_to(root).as_posix()] = path
coverage_path = record / "coverage.json"
coverage_path.write_text(json.dumps(coverage, indent=2) + "\n", encoding="utf-8", newline="\n")
evidence[coverage_path.relative_to(root).as_posix()] = coverage_path

ci_path = record / "ci.json"
ci = json.loads(ci_path.read_text(encoding="utf-8"))
if ci.get("conclusion") != "success" or len(ci.get("jobs", [])) != 4 or any(job.get("conclusion") != "success" for job in ci["jobs"]):
    raise SystemExit("The four platform/backend CI jobs have not passed")
ci_hashes = ci.get("packagedArtifactHashes", [])
if len(ci_hashes) != 4 or any(entry.get("sha256") != sha(harness) for entry in ci_hashes):
    raise SystemExit("The four CI fixture jars must match the locally tested harness")
evidence[ci_path.relative_to(root).as_posix()] = ci_path

world = output / "wwpg-0.1.0-beta.2-fixture-world-v1.zip"
with zipfile.ZipFile(world) as archive:
    if archive.testzip() is not None or any(not name.startswith("WWPG Expanded Test Yard/") for name in archive.namelist()):
        raise SystemExit("Invalid world archive or save folder")
    if b"WWPG - Expanded Test Yard" not in gzip.decompress(archive.read("WWPG Expanded Test Yard/level.dat")):
        raise SystemExit("Missing the expanded world's display name")

report = {
    "schema": 1, "world_revision": "expanded-yard-v1", "compatible_mod": "0.1.0-beta.2",
    "world": {"file": world.name, "sha256": sha(world), "bytes": world.stat().st_size},
    "published_runtime": {"file": baseline.name, "sha256": sha(baseline)},
    "test_harness": {"sha256": sha(harness), "changed_entries": changed,
                     "runtime_entries_identical": len(original_entries), "additional_test_template": template,
                     "note": "GameTest changes and one byte-identical empty namespace template only; the published runtime is unchanged."},
    "ci": {"url": ci["url"], "source_commit": ci["headSha"], "platform_backend_jobs": 4,
           "all_fixture_jars_byte_identical": True},
    "coverage": {"live_behavior_groups": 85, "declared_behavior_groups": 134,
                 "live_board_components": 28, "live_panel_attachments": 11,
                 "pg_connected_wire_entities": 282, "loose_wire_items_remaining": 0},
    "equation_checks": equations, "checks": checks,
    "tested_behavior": ["25 individual board circuits, routing board, all 28 built-in board types",
                        "Nine panels containing all 11 built-in attachment types",
                        "Seven transformers/variacs and three mixed-mod meter banks",
                        "22 standalone control, connector, motor, and storage circuits",
                        "Native button, switch, relay, analog-control, and breaker interactions",
                        "Voltage/current/power/energy/frequency readings and transformer ratio changes",
                        "Saved board and CEE capacitor voltage before recharging on restart",
                        "Original factory pump, heater, lamps, and capacitor-delayed relay",
                        "Representative collectable wire cleanup preserving all PG wire entities",
                        "Exported ZIP reopened with both backends"],
    "limitations": ["49 behavior groups are cabinet parts or assembly references rather than live exhibits",
                    "PG 0.6.2 circuit design table excluded after saved-design load error",
                    "Earlier intermittent chunk-reload and board-capacitor restart failures remain open",
                    "No new real-client visual, multiplayer, or large-network acceptance is claimed"],
}
report_path = record / "verification.json"
report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
evidence[report_path.relative_to(root).as_posix()] = report_path
# Retain the discovery log rather than silently treating its powered-table check as successful persistence.
diagnostic = root / "build/showroom-native-ab.log"
if "Failed to load data for block entity powergrid:circuit_design_table" not in diagnostic.read_text(encoding="utf-8", errors="replace"):
    raise SystemExit("Missing the upstream design-table failure record")
evidence["diagnostics/design-table-reload.log"] = diagnostic
for path in [record / "README.md", root / "docs/FIXTURE_WORLD.md", root / "docs/INSTALL.md", root / "docs/STATUS.md", Path(__file__).resolve()]:
    evidence[path.relative_to(root).as_posix()] = path
for path in (root / "src/main/java/org/cha0scollective/wwpg/gametest").glob("*.java"):
    evidence[path.relative_to(root).as_posix()] = path
verification = output / "wwpg-0.1.0-beta.2-fixture-world-v1-verification.zip"
with zipfile.ZipFile(verification, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name, path in sorted(evidence.items()):
        info = zipfile.ZipInfo(name, (2026, 10, 10, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        archive.writestr(info, path.read_bytes())
checksums = "\n".join(f"{sha(path)}  {path.name}" for path in [world, verification]) + "\n"
(output / "wwpg-0.1.0-beta.2-fixture-world-v1-SHA256SUMS.txt").write_text(checksums, encoding="utf-8", newline="\n")
print(checksums)
