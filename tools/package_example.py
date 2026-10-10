"""Keep the revised example separate from immutable beta.2 downloads/evidence."""
import gzip
import hashlib
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
record = root / "release/examples/panel-relay-v2"
output = root / "build/distributions"
record.mkdir(parents=True, exist_ok=True)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


baseline = root / "build/distributions/0.1.0-beta.2/wwpg-0.1.0-beta.2.jar"
manifest = json.loads((root / "release/0.1.0-beta.2/distribution.json").read_text())
expected = next(a["sha256"] for a in manifest["artifacts"] if a["file"] == baseline.name)
if sha(baseline) != expected:
    raise SystemExit("The baseline must be the unchanged published beta.2 jar")
harness = root / "build/libs/wwpg-0.1.0-beta.2.jar"
with zipfile.ZipFile(baseline) as original, zipfile.ZipFile(harness) as revised:
    def runtime_entries(archive):
        return {name: archive.read(name) for name in archive.namelist()
                if not name.endswith("/") and not name.startswith("org/cha0scollective/wwpg/gametest/")}
    original_entries = runtime_entries(original)
    if original_entries != runtime_entries(revised):
        raise SystemExit("Non-GameTest entries differ from the published beta.2 runtime")
    changed = sorted(name for name in set(original.namelist()) | set(revised.namelist())
                     if not name.endswith("/") and (name not in original.namelist()
                     or name not in revised.namelist() or original.read(name) != revised.read(name)))

checks = []
evidence = {}
for name in ["native-setup", "native-verify", "java-setup", "java-verify", "export-native", "export-java"]:
    path = root / "build" / f"visible-example-{name}.log"
    text = path.read_text(encoding="utf-8", errors="replace")
    if any(marker not in text for marker in ["WWPG_EXAMPLE_VISIBLE_PASSED", "All 1 required tests passed", "GAME TESTS COMPLETE", "BUILD SUCCESSFUL"]):
        raise SystemExit(f"Missing passing example evidence: {path}")
    checks.append({"log": path.relative_to(root).as_posix(), "sha256": sha(path), "passed_fixtures": 1})
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

ci_path = record / "ci.json"
ci = json.loads(ci_path.read_text(encoding="utf-8"))
if ci.get("conclusion") != "success" or len(ci.get("jobs", [])) != 4 or any(job.get("conclusion") != "success" for job in ci["jobs"]):
    raise SystemExit("The four platform/backend CI jobs have not passed")
evidence[ci_path.relative_to(root).as_posix()] = ci_path

world = output / "wwpg-0.1.0-beta.2-example-v2.zip"
with zipfile.ZipFile(world) as archive:
    if archive.testzip() is not None or any(not name.startswith("WWPG Example - Panel Demo/") for name in archive.namelist()):
        raise SystemExit("Invalid example archive or save folder")
    if b"WWPG - Panel Relay Demo" not in gzip.decompress(archive.read("WWPG Example - Panel Demo/level.dat")):
        raise SystemExit("Missing the revised world-list name")

report = {
    "schema": 1, "example_revision": 2, "compatible_mod": "0.1.0-beta.2",
    "world": {"file": world.name, "sha256": sha(world), "bytes": world.stat().st_size},
    "published_runtime": {"file": baseline.name, "sha256": sha(baseline)},
    "test_harness": {"sha256": sha(harness), "changed_entries": changed,
                     "non_gametest_entries_identical": len(original_entries),
                     "note": "The harness contains revised fixtures; all other jar entries match the published runtime byte for byte."},
    "ci": {"url": ci["url"], "source_commit": ci["headSha"], "platform_backend_jobs": 4},
    "equation_checks": equations, "checks": checks,
    "tested_behavior": ["Native CEE panel interaction", "PG relay contacts power both CEE lamp branches",
                        "RUN on / OFF dark while enabled", "Capacitor holds RUN after opening the panel switch",
                        "Discharge transfers output to OFF", "Re-enabling recharges and restores RUN",
                        "Panel voltage/current readings", "Stored capacitor property before recharge on restart",
                        "Goggles, meters, signs, factory lamp, heater, and pumped water", "Exported ZIP reopened with both backends"],
    "known_issues": "Earlier intermittent chunk-reload and board-capacitor restart failures remain unresolved; see docs/STATUS.md.",
}
report_path = record / "verification.json"
report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
evidence[report_path.relative_to(root).as_posix()] = report_path
for path in [record / "README.md", root / "docs/INSTALL.md", root / "docs/STATUS.md",
             root / "src/main/java/org/cha0scollective/wwpg/gametest/ExampleWorldGameTests.java",
             root / "src/main/java/org/cha0scollective/wwpg/gametest/ExampleControlBoard.java",
             root / "src/main/java/org/cha0scollective/wwpg/gametest/BoardFixture.java"]:
    evidence[path.relative_to(root).as_posix()] = path
verification = output / "wwpg-0.1.0-beta.2-example-v2-verification.zip"
with zipfile.ZipFile(verification, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name, path in sorted(evidence.items()):
        info = zipfile.ZipInfo(name, (2026, 10, 9, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        archive.writestr(info, path.read_bytes())
checksums = "\n".join(f"{sha(path)}  {path.name}" for path in [world, verification]) + "\n"
(output / "wwpg-0.1.0-beta.2-example-v2-SHA256SUMS.txt").write_text(checksums, encoding="utf-8", newline="\n")
print(checksums)
