"""Package the frozen, locally verified beta and its reproducible evidence."""
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
version = "0.1.0-beta.1"
output = root / "build/distributions"
output.mkdir(parents=True, exist_ok=True)


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def archive(path, entries):
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name, source in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 6, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            target.writestr(info, source.read_bytes())


report_path = root / "release/verification.json"
report = json.loads(report_path.read_text())
if not report.get("all_release_gates_passed"):
    raise SystemExit("Release acceptance has not passed.")
matrix = json.loads((root / "release/content-matrix.json").read_text())
claims = [row for row in matrix["content"] if row.get("release_scope")]
if len(claims) != 134 or any(row["status"] != "verified" for row in claims):
    raise SystemExit("The declared support matrix has not been frozen.")

evidence = {"release/verification.json": report_path}
for check in report["checks"]:
    source = root / check["log"]
    if sha256(source) != check["sha256"]:
        raise SystemExit(f"Evidence changed after verification: {check['log']}")
    evidence[check["log"]] = source
for source in (root / "build/test-results/test").glob("TEST-*.xml"):
    evidence["equation-tests/" + source.name] = source
for name in ["final-release-build.log", "final-linux-release-build.log"]:
    source = root / "build" / name
    if "BUILD SUCCESSFUL" not in source.read_text(errors="replace"):
        raise SystemExit(f"Release build did not complete: {name}")
    evidence["build/" + name] = source
evidence_zip = output / f"wwpg-{version}-verification.zip"
archive(evidence_zip, evidence)

jar = root / "build/libs" / f"wwpg-{version}.jar"
linux_jar = root / "build/linux-project/build/libs" / jar.name
if sha256(jar) != sha256(linux_jar):
    raise SystemExit("Windows and Linux release jars differ.")
release_jar = output / jar.name
shutil.copyfile(jar, release_jar)
world = output / f"wwpg-{version}-example.zip"
if not world.is_file():
    raise SystemExit("The separately validated example world has not been exported.")

distribution = {
    "schema": 1, "version": version, "local_tag": version,
    "publication": "Prepared and tagged locally; no GitHub or mod-site publication in this session.",
    "artifacts": [
        {"file": path.name, "sha256": sha256(path), "bytes": path.stat().st_size}
        for path in [release_jar, world, evidence_zip]
    ],
    "reproducible_jar": "Byte-identical Windows Java 21 and Ubuntu 24.04 Java 21 builds, with normalized text and ZIP permissions.",
    "runtime": {"minecraft": "1.21.1", "neoforge": "21.1.231", "java": 21,
                "create": "6.0.10-280", "cee": "1.21.1-1.1.3", "powergrid": "0.6.2", "architectury": "13.0.8"},
    "solver": {"primary": "PG native v7", "regression_fallback": "PG Java", "substeps": 16},
    "scope": "134 declared stationary content behaviors; 11 CEE panel attachments and 28 PG board components included.",
    "verification": "release/verification.json",
}
manifest = root / "release/distribution.json"
manifest.write_text(json.dumps(distribution, indent=2) + "\n", newline="\n")
entries = {path.name: path for path in [release_jar, world, evidence_zip]}
for name in ["README.md", "CHANGELOG.md", "docs/INSTALL.md", "docs/SUPPORT.md",
             "docs/FIRST_RELEASE.md", "docs/DEVELOPMENT.md", "release/artifacts.json",
             "release/content-matrix.json", "release/verification.json", "release/distribution.json"]:
    entries[name] = root / name
bundle = output / f"wwpg-{version}-release.zip"
archive(bundle, entries)
checksums = [f"{sha256(path)}  {path.name}" for path in [release_jar, world, evidence_zip, bundle]]
(output / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", newline="\n")
print("\n".join(checksums))
