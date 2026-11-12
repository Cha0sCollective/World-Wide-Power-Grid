import unittest

from preparation_sections import SECTIONS, validate_rows


class PreparationEvidenceChecks(unittest.TestCase):
    def setUp(self):
        self.ticks = [dict(tick=str(i)) for i in range(200, 221)]
        self.rows = [dict(tick=str(i), owner='42', forced_edit=str(i == 201).lower(), updates='1', accepted='1',
                         stage_mask='127', prepare_ns='10', stable_ns='7', **{key:'1' for key in SECTIONS}) for i in (201, 207)]

    def test_complete_balanced_population(self):
        self.assertEqual(validate_rows(self.rows, self.ticks), 2)

    def test_missing_or_duplicate_observation_rejected(self):
        for rows in (self.rows[:1], self.rows + self.rows[:1]):
            with self.assertRaises(AssertionError):
                validate_rows(rows, self.ticks)

    def test_incomplete_stages_rejected(self):
        self.rows[0]['stage_mask'] = '63'
        with self.assertRaises(AssertionError):
            validate_rows(self.rows, self.ticks)

    def test_overlapping_or_incomplete_durations_rejected(self):
        for key, value in (('stable_ns','8'), ('prepare_ns','6'), ('apply_ns','-1')):
            rows = [dict(row) for row in self.rows]
            rows[0][key] = value
            with self.assertRaises(AssertionError):
                validate_rows(rows, self.ticks)

    def test_failed_update_not_a_steady_measurement(self):
        self.rows[0]['accepted'] = '0'
        with self.assertRaises(AssertionError):
            validate_rows(self.rows, self.ticks)


if __name__ == '__main__':
    unittest.main()
