import unittest

from native_work import validate_rows


class NativeWorkEvidenceChecks(unittest.TestCase):
    def setUp(self):
        self.ticks = [dict(tick='20', tick_ns='50000000', timing_sample='1')]
        self.observed = [dict(tick='20', step=str(i), tick_ns='50000000', returned='true', rows='14925',
            max_iterations='2', jacobian_commands='0', hook_indices='0|0', hook_commands='0|0',
            iteration_calls='2', residual_calls='3', native_ns='600000') for i in range(1, 17)]
        self.substeps = [dict(tick='20', step=str(i), tick_ns='50000000', native_solve_calls='1',
            native_solve_ns='610000') for i in range(1, 17)]

    def test_complete_observation(self):
        self.assertEqual(validate_rows(self.observed, self.ticks, self.substeps), 1)

    def test_missing_or_duplicate_call_rejected(self):
        for values in (self.observed[:-1], self.observed + self.observed[:1]):
            with self.assertRaises(AssertionError): validate_rows(values, self.ticks, self.substeps)

    def test_bad_callback_sequence_rejected(self):
        for key, value in (('hook_indices', '0|2'), ('hook_commands', '0'), ('residual_calls', '1')):
            values = [dict(r) for r in self.observed]
            values[0][key] = value
            with self.assertRaises(AssertionError): validate_rows(values, self.ticks, self.substeps)

    def test_unpaired_or_outside_scope_duration_rejected(self):
        for key, value in (('tick_ns', '50000001'), ('native_ns', '620000'), ('returned', 'false')):
            values = [dict(r) for r in self.observed]
            values[0][key] = value
            with self.assertRaises(AssertionError): validate_rows(values, self.ticks, self.substeps)


if __name__ == '__main__':
    unittest.main()
