"""Check native Workshop preservation and reject mismatched or incomplete export evidence."""
import gzip
import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

from export_api_workshop import VERSION, STATION_IDS, TICKETS, export, fixed_spawn_level
from reopen_stationary_yard import extract
from recheck_workshop_spawn import correct, validate_correction


class WorkshopExportChecks(unittest.TestCase):
    def setUp(self):
        scratch = Path(__file__).resolve().parents[1] / "build" / "api-workshop-export-tests"
        scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch)
        self.root = Path(self.temp.name).resolve()
        self.run = self.root / "run"
        self.world = self.run / "world"
        self.output = self.root / "workshop.zip"
        self.jars = {}
        for role in ("runtime", "example", "upstream_example"):
            path = self.root / (role + ".jar")
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("META-INF/neoforge.mods.toml", "test-only metadata")
                archive.writestr("test-only.txt", role)
            self.jars[role] = path
        level = b"\x0a\x00\x00\x08\x00\x09LevelName\x00\x08Original\x0a\x00\x09GameRules\x08\x00\x0bspawnRadius\x00\x0210\x00\x00"
        tickets = b"\x0a\x00\x00\x0a\x00\x04data\x0c\x00\x06Forced" + struct.pack(">i", len(TICKETS))
        tickets += b"".join(struct.pack(">q", (z << 32) | x) for x, z in TICKETS) + b"\x00\x00"
        self.native = {
            "level.dat": gzip.compress(level, mtime=0),
            "data/chunks.dat": gzip.compress(tickets, mtime=0),
            "data/electroenergetics.dat": b"native CEE connections",
            "data/wwpg.dat": b"stable extension identities",
            "region/r.17.17.mca": b"native accepted storage and panel/board contents",
            "entities/r.17.17.mca": b"native PG wire entity bytes",
            "serverconfig/powergrid-server.toml": b"fixed backend setting",
        }
        for relative, data in self.native.items():
            path = self.world / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        (self.root / "release").mkdir()
        (self.root / "release/artifacts.json").write_text('{"artifacts": []}', encoding="utf-8")
        (self.root / "release/optional-artifacts.json").write_text('{"artifacts": []}', encoding="utf-8")
        self.configure()

    def tearDown(self):
        self.temp.cleanup()

    def configure(self, profile="BASE", phase="VERIFY", jar_hash=None, addons=True):
        self.profile = profile
        hashes = {role: hashlib.sha256(path.read_bytes()).hexdigest() for role, path in self.jars.items()}
        for index in range(4):
            proof = dict(profile=profile, backend="NATIVE", namespaces="wwpg_api_world",
                         phase="SETUP" if index == 0 else phase, completed="test-only recorded completion",
                         required_tests_passed=1, sha256=jar_hash or hashes["runtime"])
            if addons:
                proof["addon_artifacts"] = {
                    role: dict(artifact=self.jars[role].name, sha256=hashes[role])
                    for role in ("example", "upstream_example")}
            (self.run / f"packaged-{index}.json").write_text(json.dumps(proof), encoding="utf-8")
        stations = dict(target=VERSION, api_version="1.0.0", profile=profile, backend="NATIVE",
                        phase="VERIFY", completed=True, runtime_station_checks=11,
                        native_item_workflows=True, loose_construction_wire_items=0,
                        native_forced_chunks=[list(pair) for pair in TICKETS],
                        stations=[dict(code=code, registry_id=registry_id, checked=True)
                                  for code, registry_id in STATION_IDS.items()])
        (self.run / "api-workshop-stations.json").write_text(json.dumps(stations), encoding="utf-8")

    def export(self, output=None):
        return export(self.run, self.jars["runtime"], self.jars["example"],
                      self.jars["upstream_example"], self.profile, "NATIVE",
                      output or self.output, self.root)

    def test_exact_native_wires_history_and_both_addon_hashes_survive_zip_extraction(self):
        evidence = self.export()
        self.assertFalse(evidence["release_acceptance"])
        extract(self.output, self.root / "reopened")
        for relative, data in self.native.items():
            if relative != "level.dat":
                self.assertEqual((self.root / "reopened/world" / relative).read_bytes(), data)
        raw = gzip.decompress((self.root / "reopened/world/level.dat").read_bytes())
        self.assertIn(b"Extension API Workshop", raw)
        self.assertIn(b"spawnRadius\x00\x010", raw)
        manifest = json.loads((self.root / "reopened/world/WWPG_MANIFEST.json").read_text(encoding="utf-8"))
        self.assertEqual(set(manifest["example_addons"]), {"example", "upstream_example"})
        self.assertEqual(len(manifest["cold_restart_proofs"]), 3)
        self.assertEqual(len(manifest["stations"]["stations"]), 11)
        self.assertEqual(manifest['spawn_policy'],'fixed-platform-v1')

    def test_fixed_spawn_changes_only_the_radius_string(self):
        before=gzip.decompress(self.native['level.dat'])
        after=gzip.decompress(fixed_spawn_level(self.native['level.dat']))
        self.assertEqual(after,before.replace(b'spawnRadius\x00\x0210',b'spawnRadius\x00\x010'))
        self.assertEqual(fixed_spawn_level(gzip.compress(after)),fixed_spawn_level(self.native['level.dat']))
        for raw in (b'missing',before+before,before.replace(b'\x00\x0210',b'\x00\x02xx')):
            with self.assertRaises(ValueError):fixed_spawn_level(gzip.compress(raw))

    def test_preserved_archive_correction_rejects_other_native_changes(self):
        source=self.root/'old.zip';header='Workshop/WWPG_MANIFEST.json'
        manifest=dict(profile='BASE',backend='NATIVE',files={name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for name,raw in self.native.items()})
        with zipfile.ZipFile(source,'x') as zipped:
            for name,raw in self.native.items():zipped.writestr('Workshop/'+name,raw)
            zipped.writestr(header,json.dumps(manifest))
        corrected=self.root/'corrected.zip';correct(source,corrected);validate_correction(source,corrected)
        corrupt=self.root/'bad.zip'
        with zipfile.ZipFile(corrected) as before,zipfile.ZipFile(corrupt,'x') as after:
            for name in before.namelist():after.writestr(name,b'changed wire' if name.endswith('/entities/r.17.17.mca') else before.read(name))
        with self.assertRaisesRegex(ValueError,'native payload'):validate_correction(source,corrupt)

    def test_missing_runtime_addon_or_cold_restart_evidence_prevents_any_export(self):
        for values in (dict(jar_hash="0" * 64), dict(addons=False), dict(phase="SETUP")):
            with self.subTest(values=values):
                self.configure(**values)
                with self.assertRaises(ValueError):
                    self.export()
                self.assertFalse(self.output.exists())
        self.configure()
        proof = json.loads((self.run / "packaged-2.json").read_text())
        proof["completed"] = None
        (self.run / "packaged-2.json").write_text(json.dumps(proof), encoding="utf-8")
        with self.assertRaises(ValueError):
            self.export()
        self.assertFalse(self.output.exists())

    def test_missing_station_or_lost_native_chunk_tickets_prevents_export(self):
        record = self.run / "api-workshop-stations.json"
        stations = json.loads(record.read_text())
        stations["stations"].pop()
        record.write_text(json.dumps(stations), encoding="utf-8")
        with self.assertRaises(ValueError):
            self.export()
        self.configure()
        (self.world / "data/chunks.dat").write_bytes(gzip.compress(
            b"\x0a\x00\x00\x0c\x00\x06Forced\x00\x00\x00\x00\x00", mtime=0))
        with self.assertRaises(ValueError):
            self.export()
        self.assertFalse(self.output.exists())

    def test_existing_artifacts_and_source_save_are_preserved_and_optional_profile_is_explicit(self):
        original = (self.world / "level.dat").read_bytes()
        with self.assertRaises(ValueError):
            self.export(self.world / "level.dat")
        self.assertEqual((self.world / "level.dat").read_bytes(), original)
        self.configure("PINOUT")
        self.export()
        data = self.output.read_bytes()
        with self.assertRaises(ValueError):
            self.export()
        self.assertEqual(self.output.read_bytes(), data)
        extract(self.output, self.root / "client", client=True)
        manifest = json.loads((self.root / "client/saves/Extension API Workshop — Pinout/WWPG_MANIFEST.json").read_text(encoding="utf-8"))
        self.assertEqual(manifest["profile"], "PINOUT")
        self.assertIn("optional_dependencies", manifest)


if __name__ == "__main__":
    unittest.main()
