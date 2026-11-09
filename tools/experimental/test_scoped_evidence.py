"""Focused guards against accepting absent or mismatched scope execution evidence."""
import unittest

from plain_wire_benchmark import validate_scoped_restoration
from summarize_current_profile import signature


class ScopedEvidenceTest(unittest.TestCase):
    def report(self, **overrides):
        stats = dict(snapshots=4, membershipChecks=3, savedParts=24, nodeBindings=100,
            seedEndpoints=12, heldNetworks=8, fallbackWorlds=0, totalNanos=20, maxNanos=10,
            topologyPasses=2, topologyNanos=12, maxTopologyNanos=8)
        stats.update(overrides)
        return 'WWPG experimental scoped restoration: ' + ', '.join(f'{k}={v}' for k, v in stats.items())

    def test_activity_and_disabled_evidence(self):
        self.assertEqual(validate_scoped_restoration(self.report(), True, True)['snapshots'], 4)
        self.assertIsNone(validate_scoped_restoration('', False))
        with self.assertRaises(AssertionError):
            validate_scoped_restoration(self.report(), False)

    def test_absent_duplicate_and_incomplete_reports(self):
        for text in ('', self.report() + '\n' + self.report(), self.report().replace('snapshots=4, ', '')):
            with self.subTest(text=text), self.assertRaises(AssertionError):
                validate_scoped_restoration(text, True)

    def test_activity_cannot_be_inferred_from_enablement(self):
        with self.assertRaises(AssertionError):
            validate_scoped_restoration(self.report(seedEndpoints=0), True, True)

    def test_negative_and_impossible_timing(self):
        for overrides in (dict(fallbackWorlds=-1), dict(maxNanos=21), dict(maxTopologyNanos=13)):
            with self.subTest(overrides=overrides), self.assertRaises(AssertionError):
                validate_scoped_restoration(self.report(**overrides), True)

    def test_profile_matching_distinguishes_scope(self):
        old = dict(runtime=dict(sha256='runtime'), helper=dict(sha256='helper'))
        self.assertEqual(signature(old), signature(dict(old, scoped_restoration=False)))
        self.assertNotEqual(signature(old), signature(dict(old, scoped_restoration=True)))


if __name__ == '__main__':
    unittest.main()
