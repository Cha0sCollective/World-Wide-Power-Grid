"""Ensure the release gate rejects missing, stale or mismatched packaged runs."""
import json
from pathlib import Path
import tempfile
import unittest

from package_beta5 import proofs


class PackagedProofChecks(unittest.TestCase):
    def setUp(self):
        scratch = Path(__file__).resolve().parents[1] / 'build/package-tool-tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch)
        self.root = Path(self.temp.name)
        self.phases = ['SETUP', 'VERIFY', 'VERIFY', 'VERIFY']
        self.records = [dict(completed=True, required_tests_passed=204, phase=phase,
                             sha256='frozen', profile='BASE', backend='NATIVE', namespaces='default')
                        for phase in self.phases]

    def tearDown(self):
        self.temp.cleanup()

    def check(self):
        for i, record in enumerate(self.records):
            (self.root / f'packaged-{i}.json').write_text(json.dumps(record))
        return proofs(self.root, 'frozen', 'BASE', 'NATIVE', 'default', self.phases, 204)

    def test_requires_three_actual_cold_processes(self):
        self.assertEqual(len(self.check()), 4)
        self.records[-1]['phase'] = 'SETUP'
        with self.assertRaises(SystemExit): self.check()

    def test_rejects_retry_artifact_and_wrong_backend(self):
        for key, value in [('sha256', 'another-jar'), ('backend', 'JAVA'), ('profile', 'PINOUT'),
                           ('namespaces', 'wwpg_yard'), ('required_tests_passed', 203), ('completed', False)]:
            original = self.records[2][key]
            self.records[2][key] = value
            with self.subTest(key=key), self.assertRaises(SystemExit): self.check()
            self.records[2][key] = original

    def test_missing_cold_process_is_not_a_pass(self):
        self.records.pop()
        with self.assertRaises(SystemExit): self.check()


if __name__ == '__main__': unittest.main()
