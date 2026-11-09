"""Focused checks for profile boundaries and incompatible diagnostic modes."""
import unittest
from types import SimpleNamespace

from cpu_profile import filter_events, instant_ns, steady_window, validate_options
from summarize_current_profile import signature


class CpuProfileChecks(unittest.TestCase):
    def test_fractional_timestamps_keep_nanosecond_order(self):
        self.assertEqual(instant_ns('2026-11-09T18:00:00.000000001Z') - instant_ns('2026-11-09T18:00:00Z'), 1)
        self.assertEqual(instant_ns('2026-11-09T18:00:00.1Z') - instant_ns('2026-11-09T18:00:00Z'), 100_000_000)
        self.assertLess(instant_ns('2026-11-09T18:00:00Z'), instant_ns('2026-11-09T18:00:00.2Z'))

    def test_non_utc_or_excess_precision_fails(self):
        for value in ('2026-11-09T18:00:00+01:00', '2026-11-09T18:00:00.1234567890Z', 'not a time'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                instant_ns(value)

    def test_boundary_crossing_durations_are_excluded_whole(self):
        start = instant_ns('2026-11-09T18:00:00Z')
        window = dict(start_unix_ns=start, end_unix_ns=start + 1_000_000_000)
        def event(at, duration=0):
            return dict(type='jdk.GCPhasePause', start=at, duration_ns=duration)
        before = event('2026-11-09T17:59:59.9Z', 200_000_000)
        inside = event('2026-11-09T18:00:00Z', 100_000_000)
        crossing = event('2026-11-09T18:00:00.9Z', 200_000_000)
        at_end = event('2026-11-09T18:00:01Z')
        kept, excluded = filter_events([before, inside, crossing, at_end], window)
        self.assertEqual(kept, [inside])
        self.assertEqual(excluded, {'jdk.GCPhasePause': 3})

    def test_negative_duration_fails(self):
        with self.assertRaises(ValueError):
            filter_events([dict(type='jdk.GCPhasePause', start='1970-01-01T00:00:00Z', duration_ns=-1)],
                          dict(start_unix_ns=0, end_unix_ns=10))

    @staticmethod
    def clocks():
        ticks = [dict(tick=str(i), phase='STABLE', timing_sample='0', wall_ns=str(i * 50_000_000)) for i in (1, 2, 3)]
        placed = [dict(tick=str(i), phase='STABLE', wall_unix_ms=str(1000 + i * 50)) for i in (1, 2, 3)]
        return ticks, placed

    def test_inner_window_excludes_first_tick_and_margins(self):
        window = steady_window(*self.clocks())
        self.assertEqual((window['start_unix_ns'], window['end_unix_ns']), (1_052_000_000, 1_148_000_000))
        self.assertEqual(window['wall_monotonic_offset_spread_ns'], 0)

    def test_clock_jump_is_not_silently_attributed(self):
        ticks, placed = self.clocks()
        placed[-1]['wall_unix_ms'] = '1180'
        with self.assertRaises(ValueError):
            steady_window(ticks, placed)

    def test_mismatched_tick_population_fails(self):
        ticks, placed = self.clocks()
        placed[-1]['tick'] = '4'
        with self.assertRaises(ValueError):
            steady_window(ticks, placed)

    def test_instrumented_tick_fails(self):
        ticks, placed = self.clocks()
        ticks[0]['timing_sample'] = '1'
        with self.assertRaises(ValueError):
            steady_window(ticks, placed)

    def test_only_long_warmup_control_without_other_diagnostics_is_allowed(self):
        baseline = dict(cpu_jfr=True, detailed=False, substep_trace=False, substep_jfr=False,
                        loading_trace=False, mode='control', warmup=120)
        validate_options(SimpleNamespace(**baseline))
        for key, value in [('detailed', True), ('substep_trace', True), ('substep_jfr', True),
                           ('loading_trace', True), ('mode', 'verify'), ('warmup', 59)]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate_options(SimpleNamespace(**{**baseline, key: value}))

    def test_missing_disabled_flags_match_but_runtime_and_enabled_flags_do_not(self):
        old = dict(runtime={'sha256': 'a'}, helper={'sha256': 'b'})
        self.assertEqual(signature(old), signature({**old, 'terminal_key_lookup': False}))
        self.assertNotEqual(signature(old), signature({**old, 'terminal_key_lookup': True}))
        self.assertNotEqual(signature(old), signature({**old, 'runtime': {'sha256': 'c'}}))


if __name__ == '__main__':
    unittest.main()
