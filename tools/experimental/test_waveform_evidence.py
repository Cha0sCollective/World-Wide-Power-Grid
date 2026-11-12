"""Focused guards for the indexed waveform experiment's execution evidence."""
import unittest

from plain_wire_benchmark import validate_indexed_waveform
from summarize_current_profile import signature


class WaveformEvidenceTest(unittest.TestCase):
    report = 'WWPG experimental indexed waveform: passes=16, directVisits=128, middleVisits=32'

    def test_enabled_and_disabled(self):
        self.assertEqual(validate_indexed_waveform(self.report, True)['middleVisits'], 32)
        self.assertIsNone(validate_indexed_waveform('', False))
        with self.assertRaises(AssertionError):
            validate_indexed_waveform(self.report, False)

    def test_missing_duplicate_or_incomplete(self):
        for text in ('', self.report + '\n' + self.report, self.report.replace(', middleVisits=32', '')):
            with self.subTest(text=text), self.assertRaises(AssertionError):
                validate_indexed_waveform(text, True)

    def test_zero_activity_or_negative_counts(self):
        for text in (self.report.replace('passes=16', 'passes=0'),
                     self.report.replace('directVisits=128', 'directVisits=0'),
                     self.report.replace('middleVisits=32', 'middleVisits=-1')):
            with self.subTest(text=text), self.assertRaises(AssertionError):
                validate_indexed_waveform(text, True)

    def test_matching_distinguishes_mode(self):
        old = dict(runtime=dict(sha256='runtime'), helper=dict(sha256='helper'))
        self.assertEqual(signature(old), signature(dict(old, indexed_waveform=False)))
        self.assertNotEqual(signature(old), signature(dict(old, indexed_waveform=True)))


if __name__ == '__main__':
    unittest.main()
