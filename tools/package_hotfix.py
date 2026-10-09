"""Package beta.2 without rewriting beta.1's published evidence or hashes."""
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
version = "0.1.0-beta.2"
record = root / "release" / version
output = root / "build/distributions" / version
record.mkdir(parents=True, exist_ok=True)
output.mkdir(parents=True, exist_ok=True)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def archive(path, entries):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name, source in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 9, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            target.writestr(info, source.read_bytes())


checks = []
evidence = {}
for name, expected in [
    ("native-setup", 117), ("native-verify", 117),
    ("java-setup", 117), ("java-verify", 117),
    ("example-setup", 1), ("example-verify", 1),
    ("multiplayer-server", 1),
    ("multiplayer-client-a", None), ("multiplayer-client-b", None),
]:
    log = root / "build" / f"handheld-release-{name}.log"
    text = log.read_text(encoding="utf-8", errors="replace")
    if "BUILD SUCCESSFUL" not in text:
        raise SystemExit(f"Incomplete or failed process: {log}")
    if expected is None:
        if "WWPG_REAL_CLIENT_PASSED" not in text:
            raise SystemExit(f"Real client did not pass: {log}")
    elif f"All {expected} required tests passed" not in text or "GAME TESTS COMPLETE" not in text:
        raise SystemExit(f"Expected {expected} passing fixtures: {log}")
    relative = log.relative_to(root).as_posix()
    checks.append({"log": relative, "passed_fixtures": expected, "sha256": sha(log)})
    evidence[relative] = log

equations = 0
for path in (root / "build/test-results/test").glob("TEST-*.xml"):
    suite = ET.parse(path).getroot()
    if int(suite.attrib.get("failures", 0)) or int(suite.attrib.get("errors", 0)):
        raise SystemExit("Equation tests failed")
    equations += int(suite.attrib["tests"])
    evidence["equation-tests/" + path.name] = path
if equations != 4:
    raise SystemExit(f"Expected four equation tests, got {equations}")

ci_path = record / "ci.json"
ci = json.loads(ci_path.read_text(encoding="utf-8"))
jobs = ci.get("jobs", [])
if ci.get("conclusion") != "success" or len(jobs) != 4 or any(j.get("conclusion") != "success" for j in jobs):
    raise SystemExit("The four platform/backend CI jobs have not passed")

report = {
    "schema": 1, "target": version, "equation_tests": equations,
    "packaged_electrical_gameplay_fixtures": 117,
    "checks": checks, "ci": ci["url"],
    "local_platform": "Windows x86-64, Java 21; native v7 and Java",
    "real_clients": "Two packaged Windows clients; terminal voltage and both wire systems' handheld current readings",
    "known_issue": "Earlier beta.1 Linux/native chunk-reload failure remains unexplained; no fix claimed.",
}
report_path = record / "verification.json"
report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8", newline="\n")
evidence[report_path.relative_to(root).as_posix()] = report_path
evidence[ci_path.relative_to(root).as_posix()] = ci_path
verification = output / f"wwpg-{version}-verification.zip"
archive(verification, evidence)

jar = root / "build/libs" / f"wwpg-{version}.jar"
with zipfile.ZipFile(jar) as built:
    metadata = built.read("META-INF/neoforge.mods.toml").decode()
    if f'version="{version}"' not in metadata or 'license="MIT"' not in metadata:
        raise SystemExit("Jar version or license is incorrect")
    if built.read("META-INF/WWPG-LICENSE.txt") != (root / "LICENSE").read_bytes():
        raise SystemExit("Jar's WWPG license differs from the approved license")
release_jar = output / jar.name
shutil.copyfile(jar, release_jar)
world = root / "build/distributions" / f"wwpg-{version}-example.zip"
release_world = output / world.name
shutil.copyfile(world, release_world)
distribution = {
    "schema": 1, "version": version,
    "artifacts": [{"file": p.name, "sha256": sha(p), "bytes": p.stat().st_size}
                  for p in [release_jar, release_world, verification]],
    "runtime": {"minecraft": "1.21.1", "neoforge": "21.1.231", "java": 21,
                "create": "6.0.10-280", "cee": "1.21.1-1.1.3", "powergrid": "0.6.2", "architectury": "13.0.8"},
    "solver": {"primary": "PG native v7", "fallback": "PG Java", "substeps": 16},
    "support": "134 original stationary behavior groups plus two handheld meters; see docs/SUPPORT.md",
}
manifest = record / "distribution.json"
manifest.write_text(json.dumps(distribution, indent=2) + "\n", encoding="utf-8", newline="\n")
entries = {p.name: p for p in [release_jar, release_world, verification]}
for path in [root / "LICENSE", root / "README.md", root / "CHANGELOG.md", *sorted((root / "docs").glob("*.md")),
             root / "release/artifacts.json", root / "release/content-matrix.json", *sorted(record.glob("*.json"))]:
    entries[path.relative_to(root).as_posix()] = path
bundle = output / f"wwpg-{version}-release.zip"
archive(bundle, entries)
checksums = "\n".join(f"{sha(p)}  {p.name}" for p in [release_jar, release_world, verification, bundle]) + "\n"
(output / "SHA256SUMS.txt").write_text(checksums, encoding="utf-8", newline="\n")
print(checksums)
