"""Package beta.3 only after its CI artifacts and graphical clients pass.

Inputs: build/beta3-build.log, build/beta3-client-{native,java}.log,
build/beta3-ci (four downloaded Actions artifacts and ci-*.log job logs),
release/0.1.0-beta.3/ci.json, and the unchanged published example ZIPs.
Historical beta.1/beta.2 records and assets are never rewritten.
"""
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.1.0-beta.3"
RECORD = ROOT / "release" / VERSION
OUTPUT = ROOT / "build/distributions" / VERSION
CI = ROOT / "build/beta3-ci"


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def describe(path):
    return {"file": path.name, "sha256": sha(path), "bytes": path.stat().st_size}


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8", newline="\n")


def archive(path, entries):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name, source in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 10, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            target.writestr(info, source.read_bytes())


def main():
    RECORD.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    jar = ROOT / "build/libs" / f"wwpg-{VERSION}.jar"
    tested = ROOT / "build/libs/wwpg-0.1.0-beta.2.jar"
    require(sha(tested) == "ecd39ee6d5815a3ea08d3320444121735d417f3b7ae21c969421f725ddc8d4a7",
            "The comparison jar must be the previously tested grounding-fix artifact")
    with zipfile.ZipFile(tested) as previous, zipfile.ZipFile(jar) as current:
        require(set(previous.namelist()) == set(current.namelist()), "Jar entry sets differ")
        changed = [name for name in current.namelist() if current.read(name) != previous.read(name)]
        metadata = "META-INF/neoforge.mods.toml"
        require(changed == [metadata], f"Unexpected jar changes: {changed}")
        require(previous.read(metadata).replace(b"0.1.0-beta.2", VERSION.encode()) == current.read(metadata),
                "Metadata has changes beyond the mod version")
        require('license="MIT"' in current.read(metadata).decode(), "Incorrect license metadata")
        require(current.read("META-INF/WWPG-LICENSE.txt") == (ROOT / "LICENSE").read_bytes(),
                "Bundled WWPG license differs from the approved MIT license")
        require(not any("wwpg/acceptance/" in name for name in current.namelist()),
                "Separate graphical-client helper leaked into the release jar")

    evidence = {}
    build_log = ROOT / "build/beta3-build.log"
    require("BUILD SUCCESSFUL" in build_log.read_text(encoding="utf-8", errors="replace"), "Build did not pass")
    evidence[build_log.relative_to(ROOT).as_posix()] = build_log
    equations = 0
    for path in sorted((ROOT / "build/test-results/test").glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        require(not int(suite.attrib.get("failures", 0)) and not int(suite.attrib.get("errors", 0)),
                "Equation tests failed")
        equations += int(suite.attrib["tests"])
        evidence["equation-tests/" + path.name] = path
    require(equations == 4, f"Expected four equation tests, got {equations}")

    clients = []
    for backend in ("NATIVE", "JAVA"):
        path = ROOT / f"build/beta3-client-{backend.lower()}.log"
        log = path.read_text(encoding="utf-8", errors="replace")
        for marker in ("BUILD SUCCESSFUL", f"World-Wide Power Grid {VERSION} (wwpg)",
                       f"EXPANDED_REAL_CLIENT_PASSED: backend={backend}; views=69;",
                       "server PG wires=282;", "loose wires=0",
                       "EXPANDED_CLIENT_PANEL_PACKETS_PASSED", "EXPANDED_CLIENT_P2_PACKETS_PASSED"):
            require(marker in log, f"Missing graphical-client checkpoint {marker}: {path}")
        require("EXPANDED_REAL_CLIENT_FAILED" not in log, f"Graphical client failed: {path}")
        require(log.count("EXPANDED_CLIENT_VIEW_PASSED:") == 69, "Incomplete client station tour")
        require(log.count("EXPANDED_CLIENT_READING:") == 54, "Incomplete client gauge checks")
        evidence[path.relative_to(ROOT).as_posix()] = path
        clients.append({"backend": backend, "result": "PASS", "phase": "Fresh copy of the published downloaded yard",
                        "station_views": 69, "synchronized_gauges": 54, "retained_server_PG_wires": 282,
                        "loose_client_wires": 0, "factory_and_P2_interaction_packets": "PASS",
                        "log": {"path": path.relative_to(ROOT).as_posix(), **describe(path)}})
        for name in ("35-T1.png", "68-factory.png", "30-P5.png", "01-B01.png"):
            shot = ROOT / f"run/beta3-client-{backend.lower()}/screenshots" / name
            require(shot.is_file(), f"Missing graphical-client screenshot: {shot}")
            evidence[f"client-screenshots/{backend.lower()}-{name}"] = shot
    for path in sorted((ROOT / "build/beta3-client-harness").rglob("*")):
        if path.is_file() and (path.suffix == ".java" or path.name in ("client.init.gradle", "neoforge.mods.toml")):
            evidence["client-helper/" + path.relative_to(ROOT / "build/beta3-client-harness").as_posix()] = path

    ci_path = RECORD / "ci.json"
    ci = json.loads(ci_path.read_text(encoding="utf-8"))
    jobs = ci.get("jobs", [])
    expected_names = {f"build ({os}, {backend})" for os in ("ubuntu-24.04", "windows-latest")
                      for backend in ("NATIVE", "JAVA")}
    require(ci.get("conclusion") == "success" and {job["name"] for job in jobs} == expected_names
            and len(jobs) == 4 and all(job.get("conclusion") == "success" for job in jobs),
            "The four platform/backend CI jobs must pass")
    ci_hashes = ci.get("packagedArtifactHashes", [])
    jars = sorted(CI.glob(f"wwpg-*/build/libs/wwpg-{VERSION}.jar"))
    require(len(jars) == 4 and all(sha(path) == sha(jar) for path in jars),
            "Four downloaded passing CI jars must match the final local jar")
    require(len(ci_hashes) == 4 and all(item.get("sha256") == sha(jar) for item in ci_hashes),
            "CI record must identify the four matching artifact hashes")
    for job in jobs:
        path = CI / f"ci-{job['databaseId']}.log"
        log = path.read_text(encoding="utf-8", errors="replace")
        require(log.count("All 121 required tests passed") == 2, f"Missing complete SETUP/VERIFY suite: {path}")
        require(log.count("All 1 required tests passed") >= 4, f"Missing example/yard restart fixtures: {path}")
        evidence["ci-logs/" + path.name] = path

    worlds = [
        (Path.home() / "Downloads/wwpg-0.1.0-beta.2-fixture-world-v1.zip",
         "7463af3876daa72064b0fa226f1cd88a633af6a1b2ab7471720d0fd6f3dd128f"),
        (ROOT / "build/distributions/wwpg-0.1.0-beta.2-example-v2.zip",
         "ef7547db80a9ce80246c2825008e24bca6712104f82e8e2f5701e9871947c413")]
    for path, expected in worlds:
        require(sha(path) == expected, f"Published world changed: {path}")
    for folder in (ROOT / "release/fixes/grounding", ROOT / "release/0.1.0-beta.2"):
        for path in sorted(folder.rglob("*")):
            if path.is_file():
                evidence[path.relative_to(ROOT).as_posix()] = path
    for name, marker in (("grounding-java-negative-control.log", "3 required tests failed"),
                         ("grounding-ci-linux-java-push.log", "Restart discarded PG board capacitor charge: 0.0")):
        path = ROOT / "build" / name
        require(marker in path.read_text(encoding="utf-8", errors="replace"), f"Missing historical failure evidence: {path}")
        evidence[path.relative_to(ROOT).as_posix()] = path
    for path in sorted((ROOT / "build").glob("handheld-*failure*.log")):
        evidence[path.relative_to(ROOT).as_posix()] = path

    report = {"schema": 1, "target": VERSION, "tested_artifact": describe(jar),
              "ci_source_commit": ci["headSha"], "ci": ci["url"], "packaged_checks": 121,
              "electrical_gameplay_lifecycle_checks": 120, "startup_registration_checks": 1,
              "new_grounding_checks": 3, "equation_tests": equations,
              "comparison": {"previous_tested_grounding_jar": describe(tested), "changed_entries": changed,
                             "only_mod_version_metadata_changed": True},
              "real_clients": clients, "worlds_reused_unchanged": [describe(path) for path, _ in worlds],
              "earlier_multiplayer_and_handheld_evidence": "release/0.1.0-beta.2/verification.json",
              "known_issues": ["Intermittent zero reading after chunk reload remains unresolved.",
                               "Intermittent board-capacitor restart zero reading remains unresolved, including PR #5 Linux/native run 38046505940 attempt 1; retry passed.",
                               "PG 0.6.2 circuit design-table saved-design loading error remains open."],
              "scope": "Same stationary content set as beta.2; no scale or moving-system certification"}
    write_json(RECORD / "verification.json", report)
    for path in (RECORD / "verification.json", ci_path, RECORD / "RELEASE_NOTES.md"):
        evidence[path.relative_to(ROOT).as_posix()] = path
    verification = OUTPUT / f"wwpg-{VERSION}-verification.zip"
    archive(verification, evidence)
    downloads = []
    for source in [jar, *(path for path, _ in worlds)]:
        target = OUTPUT / source.name
        shutil.copyfile(source, target)
        downloads.append(target)
    downloads.append(verification)
    distribution = {"schema": 1, "version": VERSION, "artifacts": [describe(path) for path in downloads],
                    "runtime": {"minecraft": "1.21.1", "neoforge": "21.1.231", "java": 21,
                                "create": "6.0.10-280", "cee": "1.21.1-1.1.3", "powergrid": "0.6.2", "architectury": "13.0.8"},
                    "solver": {"primary": "PG native v7", "fallback": "PG Java", "substeps": 16},
                    "support": "134 stationary behavior groups plus two handheld meters; see docs/SUPPORT.md"}
    write_json(RECORD / "distribution.json", distribution)
    entries = {path.name: path for path in downloads}
    for path in [ROOT / "LICENSE", ROOT / "README.md", ROOT / "CHANGELOG.md", *sorted((ROOT / "docs").glob("*.md")),
                 ROOT / "release/artifacts.json", ROOT / "release/content-matrix.json", *sorted(RECORD.glob("*"))]:
        if path.is_file():
            entries[path.relative_to(ROOT).as_posix()] = path
    bundle = OUTPUT / f"wwpg-{VERSION}-release.zip"
    archive(bundle, entries)
    downloads.append(bundle)
    checksums = "".join(f"{sha(path)}  {path.name}\n" for path in downloads)
    (OUTPUT / "SHA256SUMS.txt").write_text(checksums, encoding="utf-8", newline="\n")
    print(checksums)


if __name__ == "__main__":
    main()
