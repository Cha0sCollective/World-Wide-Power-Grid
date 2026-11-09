import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

from native_stages import MAGIC, MARKER, STAGES, WINDOWS_DLL, inspect_runtime, validate_row


class NativeStageEvidenceChecks(unittest.TestCase):
    def setUp(self):
        self.row = dict(profile_magic=str(MAGIC), profile_frequency='10000000', profile_stages=str(len(STAGES)),
            profile_invalid='0', residual_calls='3', iteration_calls='2', native_ns='650000')
        calls = dict(total=1,residual_multiply=3,residual_norm=3,sparse_solve=1,triangular_solve=1,permutation=2,
                     finite=4,copy=8,delta=1,java_callback=5,jacobian=1,rhs=1)
        times = dict(total=6300,residual_multiply=1800,residual_norm=700,sparse_solve=2800,
                     triangular_solve=2300,permutation=400,finite=300,copy=300,delta=100,java_callback=50,jacobian=20,rhs=100)
        self.row.update({'c_' + k + '_calls':str(calls.get(k,0)) for k in STAGES})
        self.row.update({'c_' + k + '_qpc':str(times.get(k,0)) for k in STAGES})

    def test_consistent_nested_sample(self):
        calls, times = validate_row(self.row)
        self.assertEqual(calls['triangular_solve'], 1)

    def test_wrong_layout_or_reentry_rejected(self):
        for key, value in (('profile_magic','0'),('profile_stages','12'),('profile_invalid','1')):
            with self.assertRaises(AssertionError): validate_row(dict(self.row, **{key:value}))

    def test_callback_count_mismatch_rejected(self):
        for key in ('c_residual_multiply_calls','c_java_callback_calls'):
            with self.assertRaises(AssertionError): validate_row(dict(self.row, **{key:'0'}))

    def test_outside_parent_scope_rejected(self):
        for key in ('c_triangular_solve_qpc','c_copy_qpc','c_total_qpc'):
            with self.assertRaises(AssertionError): validate_row(dict(self.row, **{key:'7000'}))

    def test_diagnostic_cannot_be_labeled_clean(self):
        with tempfile.TemporaryDirectory() as folder:
            jar = Path(folder) / 'runtime.jar'
            binary = b'observation fixture'
            marker = dict(schema=1,diagnostic=True,native_sha256=hashlib.sha256(binary).hexdigest(),
                          profile_magic=MAGIC,profile_stages=list(STAGES))
            with zipfile.ZipFile(jar,'w') as archive:
                archive.writestr(WINDOWS_DLL,binary)
                archive.writestr(MARKER,json.dumps(marker))
            self.assertTrue(inspect_runtime(jar,True)['diagnostic'])
            with self.assertRaises(AssertionError): inspect_runtime(jar,False)

    def test_ordinary_runtime_cannot_claim_native_stages(self):
        with tempfile.TemporaryDirectory() as folder:
            jar = Path(folder) / 'runtime.jar'
            with zipfile.ZipFile(jar,'w') as archive: archive.writestr('example.txt','fixture')
            self.assertIsNone(inspect_runtime(jar,False))
            with self.assertRaises(AssertionError): inspect_runtime(jar,True)


if __name__ == '__main__':
    unittest.main()
