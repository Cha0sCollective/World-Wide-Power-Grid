"""Gate tests use explicit synthetic CI inputs; they never claim runtime acceptance."""
import json
from pathlib import Path
import tempfile
import unittest

from collect_api_ci_evidence import API_NAMESPACES, COMBINATIONS, EvidenceError, cold_replacement, cycles, log, marker, metadata, nonconvergence, packaged

ROOT = Path(__file__).resolve().parents[1]


class EvidenceChecks(unittest.TestCase):
    def setUp(self):
        scratch = ROOT / 'build/api-ci-tool-tests'; scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch); self.root = Path(self.temp.name)
        self.hashes = {name: str(index) * 64 for index, name in enumerate(['runtime', 'api', 'cee_api', 'example', 'upstream_example'], 1)}

    def tearDown(self): self.temp.cleanup()

    def metadata_fixture(self):
        run = dict(id=42, head_sha='a' * 40, run_attempt=2, path='.github/workflows/build.yml',
                   html_url='https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/42', status='completed', conclusion='success')
        steps = ['Build runtime and publish both SDK APIs to an isolated local Maven repository',
                 'Build the general example independently against published Maven API artifacts',
                 'Build the CEE panel and PG board example independently against published APIs',
                 'Verify global freeze and one-tick stepping in an isolated API fixture']
        jobs, artifacts = [], []
        for index, (platform, backend, profile) in enumerate(sorted(COMBINATIONS)):
            jobs.append(dict(id=index + 100, run_id=42, run_attempt=2, name=f'build ({platform}, {backend}, {profile})',
                             status='completed', conclusion='success', steps=[dict(name=name, conclusion='success') for name in steps]))
            artifacts.append(dict(id=index + 200, name=f'wwpg-{platform}-{backend}-{profile}', expired=False,
                                  workflow_run=dict(id=42, head_sha='a' * 40)))
        return run, dict(jobs=jobs), dict(artifacts=artifacts)

    def proof(self, number, phase='SETUP', completed=True, **changes):
        value = dict(schema=1, artifact='wwpg-0.2.0.jar', sha256=self.hashes['runtime'], profile='BASE', backend='NATIVE', phase=phase,
                     namespaces=','.join(sorted(API_NAMESPACES)), required_tests_passed=28,
                     addon_artifacts={role: dict(sha256=self.hashes[role]) for role in ['example', 'upstream_example']})
        if completed: value['completed'] = 'TEST_ONLY_COMPLETION'
        value.update(changes); path = self.root / f'packaged-{number:02d}.json'; path.write_text(json.dumps(value), encoding='utf-8')

    def test_actual_eight_job_latest_attempt_and_independent_steps_are_required(self):
        values = self.metadata_fixture(); self.assertEqual(len(metadata(*values)), 8)
        values[1]['jobs'][0]['run_attempt'] = 1
        with self.assertRaises(EvidenceError): metadata(*values)
        values = self.metadata_fixture(); values[1]['jobs'][0]['steps'][1]['conclusion'] = 'skipped'
        with self.assertRaises(EvidenceError): metadata(*values)

    def test_artifact_expiry_wrong_source_duplicate_names_and_failed_run_block(self):
        for change in ['expired', 'source', 'duplicate', 'failed']:
            values = self.metadata_fixture()
            if change == 'expired': values[2]['artifacts'][0]['expired'] = True
            elif change == 'source': values[2]['artifacts'][0]['workflow_run']['head_sha'] = 'b' * 40
            elif change == 'duplicate': values[2]['artifacts'].append(values[2]['artifacts'][0])
            else: values[0]['conclusion'] = 'failure'
            with self.subTest(change=change), self.assertRaises(EvidenceError): metadata(*values)

    def test_cold_sequence_is_derived_and_later_retry_does_not_hide_failure(self):
        self.proof(0)
        for number in range(1, 4): self.proof(number, 'VERIFY')
        evidence = []
        records = packaged(self.root, API_NAMESPACES, ['SETUP', 'VERIFY', 'VERIFY', 'VERIFY'], self.hashes, 'BASE', 'NATIVE', evidence)
        self.assertEqual(len(evidence), 4); self.assertEqual(records[-1]['required_tests_passed'], 28)
        self.proof(0, completed=False)
        with self.assertRaises(EvidenceError): packaged(self.root, API_NAMESPACES, ['SETUP', 'VERIFY', 'VERIFY', 'VERIFY'], self.hashes, 'BASE', 'NATIVE', [])

    def test_changed_addon_wrong_profile_missing_namespace_and_reduced_count_block(self):
        self.proof(0)
        variants = [dict(profile='PINOUT'), dict(namespaces='wwpg_api'), dict(required_tests_passed=0),
                    dict(addon_artifacts=dict(example=dict(sha256='f' * 64), upstream_example=dict(sha256=self.hashes['upstream_example'])))]
        for changes in variants:
            self.proof(0, **changes)
            with self.subTest(changes=changes), self.assertRaises(EvidenceError):
                packaged(self.root, API_NAMESPACES, ['SETUP'], self.hashes, 'BASE', 'NATIVE', [])

    def test_actual_unload_counts_and_pre_solve_checks_are_required(self):
        value = '\n'.join(f'API_UPSTREAM_CHUNK_CYCLE: cycle={number}; actualUnloads={number}; preSolveStateVerified=true' for number in range(1, 6))
        self.assertEqual(cycles(value, 'API_UPSTREAM_CHUNK_CYCLE'), 5)
        for bad in [value.replace('actualUnloads=5', 'actualUnloads=4'), value.replace('preSolveStateVerified=true', 'preSolveStateVerified=false'), value + '\n' + value.splitlines()[0]]:
            with self.assertRaises(EvidenceError): cycles(bad, 'API_UPSTREAM_CHUNK_CYCLE')

    def test_log_cannot_mask_incomplete_or_failed_final_attempt(self):
        path = self.root / 'latest.log'
        path.write_text('TEST ONLY\nGAME TESTS COMPLETE\nAll 28 required tests passed\n', encoding='utf-8')
        self.assertIn('All 28', log(path, []))
        path.write_text('GAME TESTS COMPLETE\nAll 28 required tests passed\n1 required tests failed\n', encoding='utf-8')
        with self.assertRaises(EvidenceError): log(path, [])
        path.write_text('Server stopped without result\n', encoding='utf-8')
        with self.assertRaises(EvidenceError): log(path, [])

    def test_cold_marker_requires_both_wire_systems_and_saved_ownership_version(self):
        value = 'API_COLD_REPLACEMENT: diskStateVerified=true, delayedEntities=true, oldWiresRetired=true, newWiresPowered=true, oldCeeWiresRetired=true, newCeeWiresPowered=true, ownershipVersion=2'
        cold_replacement(value)
        for bad in [value.replace(', oldCeeWiresRetired=true, newCeeWiresPowered=true', ''),
                    value.replace('ownershipVersion=2', 'ownershipVersion=1'), value.replace('newCeeWiresPowered=true', 'newCeeWiresPowered=false'),
                    value + '\n' + value]:
            with self.assertRaises(EvidenceError): cold_replacement(bad)

    def test_actual_rejected_backend_and_paused_meter_markers_are_required(self):
        value = 'API_NONCONVERGENCE_PASSED: rollback=true; healthyCircuit=true; recovered=true'
        nonconvergence(value)
        with self.assertRaises(EvidenceError): nonconvergence(value.replace('rollback=true', 'rollback=false'))
        pause = 'API_PAUSE_PASSED: frozenCallbacks=0; steppedCommits=1; resumed=true; pausedMeasurementsUnavailable=true'
        expected = dict(frozenCallbacks='0', steppedCommits='1', resumed='true', pausedMeasurementsUnavailable='true')
        marker(pause, 'API_PAUSE_PASSED', expected)
        with self.assertRaises(EvidenceError): marker(pause.replace('; pausedMeasurementsUnavailable=true', ''), 'API_PAUSE_PASSED', expected)


if __name__ == '__main__': unittest.main()
