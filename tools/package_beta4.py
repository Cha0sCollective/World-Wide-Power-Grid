"""Collect beta.4 CI evidence and package the restoration release.

Run after the four-job versioned CI matrix and the downloaded-yard VERIFY and
restart checks on both backends. Earlier published assets/records are read only.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.1.0-beta.4"
REPO = "Cha0sCollective/World-Wide-Power-Grid"
RECORD = ROOT / "release" / VERSION
OUTPUT = ROOT / "build/distributions" / VERSION
CI = ROOT / "build/beta4-ci"
EXPECTED_JOBS = {f"build ({os}, {backend})" for os in ("ubuntu-24.04", "windows-latest")
                 for backend in ("NATIVE", "JAVA")}


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def describe(path):
    return {"file": path.name, "sha256": sha(path), "bytes": path.stat().st_size}


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8", newline="\n")


def gh(*args):
    return subprocess.run(["gh", *args], check=True, capture_output=True,
                          text=True, encoding="utf-8").stdout


def collect_ci(run_id):
    run = json.loads(gh("api", f"repos/{REPO}/actions/runs/{run_id}"))
    require(run["status"] == "completed" and run["conclusion"] == "success", "CI has not passed")
    require(run["path"] == ".github/workflows/build.yml", "Not the full Build workflow")
    jobs = json.loads(gh("api", f"repos/{REPO}/actions/runs/{run_id}/jobs"))["jobs"]
    require(len(jobs) == 4 and {job["name"] for job in jobs} == EXPECTED_JOBS
            and all(job["conclusion"] == "success" for job in jobs), "Incomplete CI matrix")
    CI.mkdir(parents=True, exist_ok=True)
    # Dedicated run directories keep subsequent collections from reusing old artifacts.
    artifacts = CI / str(run_id)
    require(not artifacts.exists(), "CI artifacts already collected; use the retained ci.json")
    gh("run", "download", str(run_id), "--repo", REPO, "--dir", str(artifacts))
    for job in jobs:
        log = gh("run", "view", str(run_id), "--repo", REPO, "--job", str(job["id"]), "--log")
        (artifacts / f"ci-{job['id']}.log").write_text(log, encoding="utf-8", newline="\n")
    jars = sorted(artifacts.glob(f"wwpg-*/build/libs/wwpg-{VERSION}.jar"))
    require(len(jars) == 4, "Missing versioned CI jars")
    write_json(RECORD / "ci.json", {
        "status": run["status"], "conclusion": run["conclusion"], "headSha": run["head_sha"],
        "url": run["html_url"], "runId": run["id"], "runAttempt": run["run_attempt"],
        "event": run["event"], "jobs": [{"databaseId": j["id"], "name": j["name"],
            "conclusion": j["conclusion"], "status": j["status"], "url": j["html_url"],
            "steps": j["steps"]} for j in jobs],
        "packagedArtifactHashes": [{"job": p.relative_to(artifacts).parts[0], **describe(p)} for p in jars]
    })


def archive(path, entries):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name, source in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 10, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            target.writestr(info, source.read_bytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--collect-ci", type=int, help="Completed four-job beta.4 Build run")
    args = parser.parse_args()
    RECORD.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    if args.collect_ci:
        collect_ci(args.collect_ci)
    jar = ROOT / "build/libs" / f"wwpg-{VERSION}.jar"
    previous = ROOT / "build/libs/wwpg-0.1.0-beta.3.jar"
    investigation = json.loads((ROOT / "release/fixes/restoration/verification.json").read_text(encoding="utf-8"))
    require(sha(previous) == investigation["candidate"]["sha256"], "Comparison requires the tested PR #7 candidate")
    for item in investigation["source_files"]:
        require(sha(ROOT / item["path"]) == item["sha256"], f"Correction source changed: {item['path']}")
    with zipfile.ZipFile(previous) as old, zipfile.ZipFile(jar) as current:
        require(set(old.namelist()) == set(current.namelist()), "Jar entry sets differ")
        changed = [name for name in current.namelist() if old.read(name) != current.read(name)]
        metadata = "META-INF/neoforge.mods.toml"
        require(changed == [metadata], f"Changes beyond version metadata: {changed}")
        require(old.read(metadata).replace(b"0.1.0-beta.3", VERSION.encode()) == current.read(metadata),
                "Unexpected metadata change")
        require(current.read("META-INF/WWPG-LICENSE.txt") == (ROOT / "LICENSE").read_bytes(), "License mismatch")
        require(not any("wwpg/acceptance/" in name for name in current.namelist()), "Client helper leaked into jar")
    evidence = {}
    build_log = ROOT / "build/beta4-build.log"
    require("BUILD SUCCESSFUL" in build_log.read_text(encoding="utf-8", errors="replace"), "Build failed")
    evidence[build_log.relative_to(ROOT).as_posix()] = build_log
    equations = 0
    for path in sorted((ROOT / "build/test-results/test").glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        require(not int(suite.attrib.get("failures", 0)) and not int(suite.attrib.get("errors", 0)), "Equation test failed")
        equations += int(suite.attrib["tests"])
        evidence["equation-tests/" + path.name] = path
    require(equations == 4, "Expected four equation tests")
    ci = json.loads((RECORD / "ci.json").read_text(encoding="utf-8"))
    jobs = ci["jobs"]
    require(ci["conclusion"] == "success" and len(jobs) == 4
            and {job["name"] for job in jobs} == EXPECTED_JOBS
            and all(job["conclusion"] == "success" for job in jobs), "Incomplete passing matrix")
    artifacts = CI / str(ci["runId"])
    jars = sorted(artifacts.glob(f"wwpg-*/build/libs/wwpg-{VERSION}.jar"))
    require(len(jars) == 4 and all(sha(p) == sha(jar) for p in jars), "Four CI jars must match the release jar")
    require(len(ci["packagedArtifactHashes"]) == 4
            and all(p["sha256"] == sha(jar) for p in ci["packagedArtifactHashes"]), "CI record hash mismatch")
    for job in jobs:
        path = artifacts / f"ci-{job['databaseId']}.log"
        log = path.read_text(encoding="utf-8", errors="replace")
        require(log.count("All 123 required tests passed") == 2, f"Missing full SETUP/VERIFY suites: {path}")
        require(log.count("All 2 required tests passed") == 2, f"Missing upstream reference phases: {path}")
        require(log.count("All 1 required tests passed") == 4, f"Missing both example restarts: {path}")
        require("history=7.5" in log and "entitiesReady=false" in log, f"Missing restoration diagnostics: {path}")
        evidence["ci-logs/" + path.name] = path
    yard_checks = []
    for backend in ("NATIVE", "JAVA"):
        for phase in ("verify", "restart"):
            path = ROOT / f"build/beta4-yard-{backend.lower()}-{phase}.log"
            log = path.read_text(encoding="utf-8", errors="replace")
            for marker in ("BUILD SUCCESSFUL", "All 1 required tests passed", "GAME TESTS COMPLETE",
                           f"World-Wide Power Grid {VERSION} (wwpg)", "WWPG_SHOWROOM_PASSED: phase=VERIFY",
                           "85/134 declared behavior groups", "282 connected PG wires/cords retained",
                           "removed 0 loose wire/spool items", "WWPG_EXAMPLE_VISIBLE_PASSED"):
                require(marker in log, f"Missing downloaded-yard checkpoint {marker}: {path}")
            evidence[path.relative_to(ROOT).as_posix()] = path
            yard_checks.append({"backend": backend, "phase": phase, "result": "PASS",
                                "log": {"path": path.relative_to(ROOT).as_posix(), **describe(path)}})
    worlds = [(ROOT / "build/distributions/0.1.0-beta.3/wwpg-0.1.0-beta.2-fixture-world-v1.zip",
               "7463af3876daa72064b0fa226f1cd88a633af6a1b2ab7471720d0fd6f3dd128f"),
              (ROOT / "build/distributions/0.1.0-beta.3/wwpg-0.1.0-beta.2-example-v2.zip",
               "ef7547db80a9ce80246c2825008e24bca6712104f82e8e2f5701e9871947c413")]
    for path, expected in worlds:
        require(sha(path) == expected, f"Published world changed: {path}")
    for path in sorted((ROOT / "release/fixes/restoration").rglob("*")):
        if path.is_file():
            evidence[path.relative_to(ROOT).as_posix()] = path
    negative = ROOT / "build/restoration-java-controls.log"
    require("2 required tests failed" in negative.read_text(encoding="utf-8", errors="replace"), "Missing negative control")
    evidence[negative.relative_to(ROOT).as_posix()] = negative
    report = {"schema": 1, "target": VERSION, "tested_artifact": describe(jar), "ci_source_commit": ci["headSha"],
              "ci": ci["url"], "packaged_checks": 123, "electrical_gameplay_lifecycle_checks": 122,
              "startup_registration_checks": 1, "equation_tests": equations,
              "comparison": {"tested_PR7_candidate": describe(previous), "changed_entries": changed,
                             "only_mod_version_metadata_changed": True, "correction_source_hashes_unchanged": True},
              "downloaded_yard": {"checks": yard_checks, "live_behavior_groups": "85/134",
                                  "board_types": 28, "panel_types": 11, "transformer_variac_circuits": 7,
                                  "retained_PG_wires": 282, "loose_wire_items": 0,
                                  "mode": "Automated packaged servers; no new graphical-client checks"},
              "worlds_reused_unchanged": [describe(path) for path, _ in worlds],
              "negative_control": "release/fixes/restoration/verification.json; two focused failures, other 121 pass",
              "earlier_client_and_multiplayer_evidence": ["release/0.1.0-beta.2/verification.json", "release/0.1.0-beta.3/verification.json"],
              "remaining_issues": ["Exact causes of historical intermittent zero readings remain unproven.",
                                   "PG 0.6.2 circuit design-table saved-design loading error remains open."],
              "scope": "Unchanged stationary support set; no scale or moving-system certification"}
    write_json(RECORD / "verification.json", report)
    for path in (RECORD / "ci.json", RECORD / "verification.json", RECORD / "RELEASE_NOTES.md"):
        evidence[path.relative_to(ROOT).as_posix()] = path
    verification = OUTPUT / f"wwpg-{VERSION}-verification.zip"
    archive(verification, evidence)
    downloads = []
    for source in [jar, *(path for path, _ in worlds)]:
        target = OUTPUT / source.name
        shutil.copyfile(source, target)
        downloads.append(target)
    downloads.append(verification)
    write_json(RECORD / "distribution.json", {"schema": 1, "version": VERSION,
        "artifacts": [describe(path) for path in downloads],
        "runtime": {"minecraft": "1.21.1", "neoforge": "21.1.231", "java": 21, "create": "6.0.10-280",
                    "cee": "1.21.1-1.1.3", "powergrid": "0.6.2", "architectury": "13.0.8"},
        "solver": {"primary": "PG native v7", "fallback": "PG Java", "substeps": 16},
        "support": "134 stationary behavior groups plus two handheld meters; see docs/SUPPORT.md"})
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
