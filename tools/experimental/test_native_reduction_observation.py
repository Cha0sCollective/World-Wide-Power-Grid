import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from native_stages import MARKER, WINDOWS_DLL, inspect_runtime as ordinary_inspector
from run_native_reduction_observation import inspect_runtime, parse_counts


class ReductionObservationChecks(unittest.TestCase):
    def test_diagnostic_identity_cannot_claim_clean_or_stage_run(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'fixture.jar'
            dll = b'counter identity fixture'
            digest = hashlib.sha256(dll).hexdigest()
            marker = dict(schema=1, diagnostic=True, diagnostic_kind='native-reduction-counts',
                          tracked_rows=14925, native_sha256=digest)
            with zipfile.ZipFile(path, 'w') as jar:
                jar.writestr(WINDOWS_DLL, dll)
                jar.writestr(MARKER, json.dumps(marker))
                jar.writestr('native/wwpg/safe/guarded.properties', 'windows.sha256=' + digest)
            self.assertTrue(inspect_runtime(path, False)['diagnostic'])
            with self.assertRaises(AssertionError): ordinary_inspector(path, False)
            with self.assertRaises(AssertionError): inspect_runtime(path, True)

    def test_clean_marker_cannot_use_counter_entry_point(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'fixture.jar'
            with zipfile.ZipFile(path, 'w') as jar:
                jar.writestr(MARKER, json.dumps(dict(schema=1, diagnostic=False)))
            with self.assertRaises(AssertionError): inspect_runtime(path, False)

    def test_no_counts_or_backwards_counts_rejected(self):
        with self.assertRaises(AssertionError): parse_counts('No counters')
        first = 'WWPG_NATIVE_REDUCTION_PATHS rows=14925 finiteFast=10 finiteFallback=2 normFast=5 normFallback=1\n'
        second = first.replace('normFast=5', 'normFast=4')
        with self.assertRaises(AssertionError): parse_counts(first + second)
        self.assertEqual(parse_counts(first + first)[-1]['finiteFast'], 10)


if __name__ == '__main__':
    unittest.main()
