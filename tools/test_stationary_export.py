"""Checks for omitted Nether/computer state and unsafe or unverified exports."""
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import struct
import sys
import tempfile
import unittest
import zipfile

from reopen_stationary_yard import extract

EXPORT = Path(__file__).with_name('export_stationary_yard.py')


class ExportChecks(unittest.TestCase):
    def setUp(self):
        scratch_root = Path(__file__).resolve().parents[1] / 'build' / 'export-tool-tests'
        scratch_root.mkdir(parents=True, exist_ok=True)
        self.scratch = tempfile.TemporaryDirectory(dir=scratch_root)
        self.root = Path(self.scratch.name).resolve()
        assert self.root.is_relative_to(scratch_root.resolve())
        self.run = self.root / 'run'
        self.world = self.run / 'world'
        self.jar = self.root / 'wwpg-candidate.jar'
        self.jar_version('0.1.0-beta.5')
        self.archive = self.root / 'yard.zip'
        level = b'\x0a\x00\x00\x08\x00\x09LevelName\x00\x08Original\x08\x00\x0aRetainThis\x00\x0dnative-state!\x00'
        self.files = {'level.dat': gzip.compress(level), 'data/wwpg.dat': b'endpoint identities',
                      'region/r.0.0.mca': b'overworld chunks', 'entities/r.0.0.mca': b'native PG wires',
                      'DIM-1/region/r.0.0.mca': b'nether chunks', 'DIM-1/entities/r.0.0.mca': b'remote wires',
                      'DIM-1/data/electroenergetics.dat': b'remote CEE connections',
                      'serverconfig/powergrid-server.toml': b'native backend'}
        for dimension, cx, cz in (('', 16, 14), ('DIM-1/', 2, 1)):
            forced = [(z << 32) | x for x in range(cx - 1, cx + 2) for z in range(cz - 1, cz + 2)]
            raw = b'\x0a\x00\x00\x0a\x00\x04data\x0c\x00\x06Forced' + struct.pack('>i', len(forced))
            raw += b''.join(struct.pack('>q', entry) for entry in forced) + b'\x00\x00'
            self.files[dimension + 'data/chunks.dat'] = gzip.compress(raw, mtime=0)
        for name, data in self.files.items():
            path = self.world / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        (self.root / 'release/0.1.0-beta.5').mkdir(parents=True)
        for name in ('artifacts.json', 'optional-artifacts.json'):
            (self.root / 'release' / name).write_text('{"artifacts": []}')
        behavior = dict(id='powergrid:nether_transformer', registry='block', release_scope=True,
                        acceptance_circuit='40 V at the remote load', fixtures=['native_fixture'])
        (self.root / 'release/0.1.0-beta.5/content-matrix.json').write_text(json.dumps(dict(content=[behavior])))
        self.configure('BASE')

    def tearDown(self):
        self.scratch.cleanup()

    def test_native_nether_tickets_cannot_be_lost_at_batch_completion(self):
        result = self.export()
        self.assertEqual(result.returncode, 0, result.stderr)
        (self.world / 'data/chunks.dat').write_bytes(gzip.compress(b'\x0a\x00\x00\x0a\x00\x04data\x0c\x00\x06Forced\x00\x00\x00\x00\x00\x00'))
        self.assertNotEqual(self.export().returncode, 0, 'An unpowered Nether route was accepted')

    def configure(self, profile, completed=True, namespace='wwpg_yard'):
        self.profile = profile
        proof = dict(profile=profile, namespaces=namespace, sha256=hashlib.sha256(self.jar.read_bytes()).hexdigest(),
                     required_tests_passed=1 if completed else 0, completed='recorded' if completed else None)
        (self.run / 'packaged-001.json').write_text(json.dumps(proof))
        station = dict(code='S65', checked=True, observed_content=[dict(id='powergrid:nether_transformer', registry='block')], checked_workflow_content=[])
        record = dict(profile=profile, stations=[station])
        for name in ('stationary-yard-stations.json', 'stationary-yard-coverage.json'):
            (self.run / name).write_text(json.dumps(record))

    def jar_version(self, version):
        with zipfile.ZipFile(self.jar, 'w') as jar:
            jar.writestr('META-INF/neoforge.mods.toml', f'[[mods]]\nmodId="wwpg"\nversion="{version}"\n')

    def export(self, output=None, target=None):
        return subprocess.run([sys.executable, str(EXPORT), '--run', str(self.run), '--jar', str(self.jar),
                               '--profile', self.profile, '--output', str(output or self.archive), '--root', str(self.root)]
                              + (['--target', target] if target else []),
                              capture_output=True, text=True)

    def test_new_release_target_cannot_claim_historical_artifact_or_acceptance(self):
        self.assertNotEqual(self.export(target='0.2.0').returncode, 0)
        self.assertFalse(self.archive.exists())
        self.jar_version('0.2.0')
        self.configure('BASE')
        self.assertNotEqual(self.export().returncode, 0)
        result = self.export(target='0.2.0')
        self.assertEqual(result.returncode, 0, result.stderr)
        with zipfile.ZipFile(self.archive) as archive:
            manifest = json.loads(archive.read('Stationary Test Yard/WWPG_MANIFEST.json'))
        self.assertEqual(manifest['target'], '0.2.0')
        self.assertEqual(manifest['support_baseline'], 'release/0.1.0-beta.5/content-matrix.json')
        self.assertIn('only when its exact ZIP SHA-256 is included', manifest['acceptance_record'])
        self.assertNotIn('Published beta.5', manifest['acceptance_record'])

    def test_native_nether_connections_and_wire_entities_survive_actual_zip_and_extraction(self):
        result = self.export()
        self.assertEqual(result.returncode, 0, result.stderr)
        target = self.root / 'reopened'
        extract(self.archive, target)
        for name, data in self.files.items():
            if name != 'level.dat':
                self.assertEqual((target / 'world' / name).read_bytes(), data, name)
        raw = gzip.decompress((target / 'world/level.dat').read_bytes())
        self.assertIn(b'Stationary Test Yard', raw)
        self.assertIn(b'native-state!', raw)
        client = self.root / 'client'
        evidence = extract(self.archive, client, client=True)
        self.assertEqual(evidence['destination'], 'client')
        for name, data in self.files.items():
            if name != 'level.dat':
                self.assertEqual((client / 'saves/Stationary Test Yard' / name).read_bytes(), data, name)

    def test_optional_computer_files_are_preserved_but_rejected_in_the_base_profile(self):
        programs = {'startup.lua': b'print("startup")', 'wwpg.lua': b'print("native commands")', '.yard-built': b'saved switches', 'state.txt': b'computer identity'}
        for name, data in programs.items():
            path = self.world / 'computercraft/computer/0' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
        self.assertNotEqual(self.export().returncode, 0)
        self.configure('PINOUT')
        result = self.export()
        self.assertEqual(result.returncode, 0, result.stderr)
        extract(self.archive, self.root / 'optional')
        for name, data in programs.items():
            self.assertEqual((self.root / 'optional/world/computercraft/computer/0' / name).read_bytes(), data)

    def test_failed_and_regression_runs_cannot_be_exported(self):
        self.configure('BASE', completed=False)
        self.assertNotEqual(self.export().returncode, 0)
        self.configure('BASE', namespace='default')
        self.assertNotEqual(self.export().returncode, 0)
        self.assertFalse(self.archive.exists())

    def test_same_named_panel_attachment_does_not_cover_a_missing_block(self):
        record = json.loads((self.run / 'stationary-yard-coverage.json').read_text())
        record['stations'][0]['observed_content'][0]['registry'] = 'panel_attachment'
        (self.run / 'stationary-yard-coverage.json').write_text(json.dumps(record))
        self.assertNotEqual(self.export().returncode, 0)
        self.assertFalse(self.archive.exists())

    def test_no_source_save_overwrite_or_existing_destination_replacement(self):
        original = (self.world / 'level.dat').read_bytes()
        self.assertNotEqual(self.export(self.world / 'level.dat').returncode, 0)
        self.assertEqual((self.world / 'level.dat').read_bytes(), original)
        self.assertEqual(self.export().returncode, 0)
        with self.assertRaises(ValueError):
            extract(self.archive, self.run)

    def test_modified_native_payload_is_rejected_before_extraction(self):
        self.assertEqual(self.export().returncode, 0)
        corrupted = self.root / 'changed.zip'
        with zipfile.ZipFile(self.archive) as source, zipfile.ZipFile(corrupted, 'w') as target:
            for info in source.infolist():
                target.writestr(info, b'changed wires' if info.filename.endswith('/entities/r.0.0.mca') else source.read(info))
        destination = self.root / 'rejected'
        with self.assertRaises(ValueError):
            extract(corrupted, destination)
        self.assertFalse(destination.exists())


if __name__ == '__main__':
    unittest.main()
