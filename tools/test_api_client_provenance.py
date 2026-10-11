"""Acceptance helper proofs must describe immutable bytes actually launched."""
import gzip
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from collect_api_client_evidence import retain_helper_provenance, verify_helper_provenance


class HelperProvenanceChecks(unittest.TestCase):
    def setUp(self):
        scratch = Path(__file__).resolve().parents[1] / 'build' / 'api-client-provenance-tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch)
        self.root = Path(self.temp.name).resolve()
        self.run = self.root / 'run'
        self.frozen = self.run / 'acceptance-helper'
        self.frozen.mkdir(parents=True)
        self.spec = {'acceptance_helper': self.record('helper.jar', b'actual launched jar'),
                     'acceptance_helper_sources': []}
        for kind, source in [('java', 'tools/client_acceptance/src/Driver.java'),
                             ('init', 'tools/client_acceptance/api_client.gradle'),
                             ('resource', 'tools/client_acceptance/resources/META-INF/neoforge.mods.toml')]:
            item = self.record('inputs/' + source, ('captured ' + kind).encode())
            item.update(kind=kind, source_path=source)
            self.spec['acceptance_helper_sources'].append(item)
        self.launch = self.run / 'api-client-launch.json'
        self.launch.write_text(json.dumps(self.spec), encoding='utf-8')

    def tearDown(self):
        self.temp.cleanup()

    def record(self, name, content):
        path = self.frozen / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return dict(path=str(path), sha256=hashlib.sha256(content).hexdigest(), bytes=len(content))

    def test_retains_captured_inputs_without_reading_current_checkout(self):
        current = self.root / 'tools/client_acceptance/src/Driver.java'
        current.parent.mkdir(parents=True)
        current.write_bytes(b'new source written after launch')
        result = retain_helper_provenance(self.spec, self.launch, self.run, self.root / 'proof', self.root)
        self.assertEqual(gzip.decompress((self.root / result['launch_descriptor']['path']).read_bytes()), self.launch.read_bytes())
        copied = next(item for item in result['acceptance_helper_sources'] if item['kind'] == 'java')
        self.assertEqual((self.root / copied['path']).read_bytes(), b'captured java')
        self.assertFalse(Path(result['acceptance_helper']['path']).is_absolute())

    def test_rejects_changed_helper_or_source_bytes(self):
        for item in [self.spec['acceptance_helper'], self.spec['acceptance_helper_sources'][0]]:
            path = Path(item['path']); original = path.read_bytes()
            path.write_bytes(b'x' * len(original))
            with self.assertRaisesRegex(AssertionError, 'hash changed'):
                verify_helper_provenance(self.spec, self.run)
            path.write_bytes(original)

    def test_rejects_missing_or_escape_inputs(self):
        missing = dict(self.spec); missing['acceptance_helper_sources'] = []
        with self.assertRaisesRegex(AssertionError, 'actual Java/init/resource'):
            verify_helper_provenance(missing, self.run)
        escaped = self.root / 'outside.jar'; escaped.write_bytes(b'actual launched jar')
        self.spec['acceptance_helper']['path'] = str(escaped)
        with self.assertRaisesRegex(AssertionError, 'escaped'):
            verify_helper_provenance(self.spec, self.run)

    def test_rejects_duplicate_source_and_unsafe_identity(self):
        self.spec['acceptance_helper_sources'].append(dict(self.spec['acceptance_helper_sources'][0]))
        with self.assertRaisesRegex(AssertionError, 'Duplicate'):
            verify_helper_provenance(self.spec, self.run)
        self.spec['acceptance_helper_sources'].pop()
        self.spec['acceptance_helper_sources'][0]['source_path'] = '../Driver.java'
        with self.assertRaisesRegex(AssertionError, 'Invalid captured'):
            verify_helper_provenance(self.spec, self.run)


if __name__ == '__main__':
    unittest.main()
