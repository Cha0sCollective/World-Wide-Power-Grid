"""Synthetic publication-gate tests only; these fixtures are not gameplay evidence."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import package_api_release as gate
import release_downloads
import release_stationary_acceptance as yard


class CombinedYardGate(unittest.TestCase):
    def setUp(self):
        scratch = Path(__file__).resolve().parents[1] / 'build/combined-yard-tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch)
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.old_version, self.version = '0.2.2-dev.94', '0.2.2-beta.4'
        self.old = self.jar('old.jar', self.old_version)
        self.current = self.jar('current.jar', self.version)
        self.world = self.world_zip('old-world.zip', self.old_version, self.old['sha256'])
        self.current_world = self.world_zip('world.zip', self.version, self.current['sha256'])
        self.row = dict(platform='windows-latest', backend='NATIVE', executed_profile='PINOUT',
            source_sha='a' * 40, runtime_version=self.old_version, tested_runtime=self.old,
            world=self.world, client_world=self.world,
            extraction=self.json('extraction.json', dict(sha256=self.world['sha256'], profile='PINOUT', jar=dict(sha256=self.old['sha256']))),
            original_campaign=self.json('campaign.json', dict(passed=True, running=False, profile='PINOUT', source_sha='a'*40, artifact=dict(sha256=self.old['sha256']))),
            server_runs=[], client_evidence=[self.file('TEST_ONLY-media', b'TEST_ONLY')])
        for number, (stage, phase) in enumerate(yard.SEQUENCE):
            manifest = self.json(f'manifest-{number}.json', dict(completed=True, required_tests_passed=1,
                sha256=self.old['sha256'], profile='PINOUT', backend='NATIVE', phase=phase, namespaces='wwpg_yard', execution=number))
            log = self.file(f'log-{number}.txt', f'TEST_ONLY {number}\nAll 1 required tests passed\nAll dimensions are saved'.encode())
            label = 'TEST_ONLY-' + str(number)
            pair = self.json(f'pair-{number}.json', dict(passed=True, running=False, runs=[dict(label=label,
                exit_code=0, passed=True, saved_all_dimensions=True, required_tests_passed=1,
                log_sha256=log['sha256'], completed_proof=dict(sha256=manifest['sha256']))]))
            self.row['server_runs'].append(dict(stage=stage, phase=phase, manifest=manifest,
                log=log, original_pair=pair, original_label=label))
        common = dict(passed=True, profile='PINOUT', backend='NATIVE', jar=dict(sha256=self.old['sha256']))
        self.row['graphical'] = self.json('graphical.json', dict(common,
            exact_archive_sha256=self.world['sha256'], views=[f'TEST_ONLY-{i}' for i in range(150)],
            view_count=150, client_server_gauge_comparisons=135))
        self.row['multiplayer'] = self.json('multiplayer.json', dict(common,
            archive_sha256=self.world['sha256'], real_clients=['TEST_ONLY-A', 'TEST_ONLY-B'],
            processes={k: dict(exit_code=0) for k in ('server', 'client-a', 'client-b')}))
        self.manifest = dict(source_sha='b'*40, wwpg_version=self.version,
            artifacts=dict(runtime=self.current), stationary_yard_policy=yard.POLICY,
            stationary_yard=self.current_world, ci=self.json('ci.json', dict(jobs=[])))

    def file(self, name, data):
        path = self.root / name
        path.write_bytes(data)
        return dict(path=name, sha256=hashlib.sha256(data).hexdigest(), bytes=len(data))

    def json(self, name, data):
        return self.file(name, json.dumps(data).encode())

    def jar(self, name, version):
        path = self.root / name
        with zipfile.ZipFile(path, 'w') as z:
            z.writestr('META-INF/neoforge.mods.toml', f'version="{version}"')
            z.writestr('TEST_ONLY', b'not a mod')
        return self.file(name, path.read_bytes())

    def world_zip(self, name, version, runtime):
        data = b'TEST_ONLY not actual level.dat'
        metadata = dict(profile='PINOUT', target=version, jar=dict(sha256=runtime),
            support_baseline='release/0.1.0-beta.5/content-matrix.json',
            files={'level.dat': dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())})
        path = self.root / name
        with zipfile.ZipFile(path, 'w') as z:
            z.writestr('TEST_ONLY/level.dat', data)
            z.writestr('TEST_ONLY/WWPG_MANIFEST.json', json.dumps(metadata))
        return self.file(name, path.read_bytes())

    def check(self, row=None, reduced_test_scope=True):
        manifest = copy.deepcopy(self.manifest)
        manifest['stationary_yard_acceptance'] = self.json('selection.json',
            dict(schema=1, policy=yard.POLICY, runs=[row or self.row]))
        if reduced_test_scope:
            # Validate one synthetic record in isolation, never full release acceptance.
            with patch.object(gate, 'PLATFORMS', {'windows-latest'}), patch.object(gate, 'BACKENDS', {'NATIVE'}):
                yard.validate(manifest, self.root, gate)
        else:
            yard.validate(manifest, self.root, gate)

    def test_retained_outcomes_validate_without_relabeling_profile(self):
        self.check()
        self.assertEqual(self.row['executed_profile'], 'PINOUT')

    def test_partial_platform_coverage_cannot_pass_real_gate(self):
        with self.assertRaisesRegex(gate.PublicationError, 'missing a platform/backend'):
            self.check(reduced_test_scope=False)

    def test_failed_exit_cannot_be_hidden_by_passing_summary(self):
        row = copy.deepcopy(self.row)
        path = self.root / row['server_runs'][-1]['original_pair']['path']
        pair = json.loads(path.read_text())
        pair['runs'][0]['exit_code'] = 1
        row['server_runs'][-1]['original_pair'] = self.json(path.name, pair)
        with self.assertRaisesRegex(gate.PublicationError, 'exit/count/save/input'):
            self.check(row)

    def test_missing_cold_reopen_is_rejected(self):
        row = copy.deepcopy(self.row)
        row['server_runs'].pop()
        with self.assertRaisesRegex(gate.PublicationError, 'three exact-archive reopens'):
            self.check(row)

    def test_retained_clients_need_media(self):
        row = copy.deepcopy(self.row)
        row['client_evidence'] = []
        with self.assertRaisesRegex(gate.PublicationError, 'media/logs'):
            self.check(row)

    def test_pinout_record_cannot_claim_a_base_execution(self):
        row = copy.deepcopy(self.row)
        row['executed_profile'] = 'BASE'
        with self.assertRaisesRegex(gate.PublicationError, 'invented BASE'):
            self.check(row)

    def test_distribution_has_one_yard_and_two_workshops(self):
        manifest = dict(self.manifest, base_workshop_world=self.file('base-api.zip', b'TEST_ONLY base'),
            pinout_workshop_world=self.file('pinout-api.zip', b'TEST_ONLY pinout'))
        files = release_downloads.world_payload(manifest, self.root, self.version)
        self.assertEqual(len(files), 3)
        self.assertEqual(sum('stationary-yard' in n for n in files), 1)

    def test_sdk_reuse_checks_each_locked_input(self):
        artifacts = {k: self.file(k + '.bin', ('TEST_ONLY ' + k).encode()) for k in gate.ROLES if k != 'runtime'}
        artifacts['runtime'] = self.current
        old = dict(artifacts, runtime=self.old)
        manifest = dict(source_sha='b'*40, wwpg_version=self.version, artifacts=artifacts,
            sdk_acceptance_reuse=dict(schema=1, scope='runtime_version_only', source_sha='a'*40,
                runtime_version=self.old_version, artifacts=old))
        self.assertEqual(gate.sdk_acceptance_source(manifest, self.root), 'a'*40)
        manifest['artifacts']['example_sources'] = self.file('changed.bin', b'TEST_ONLY changed source')
        with self.assertRaisesRegex(gate.PublicationError, 'changed input: example_sources'):
            gate.sdk_acceptance_source(manifest, self.root)

    def test_ci_reuse_cannot_carry_changed_runtime_entries(self):
        roles = {'runtime', 'api', 'cee_api', 'example', 'upstream_example'}
        artifacts = {k: self.file(k + '.bin', ('TEST_ONLY ' + k).encode()) for k in roles if k != 'runtime'}
        artifacts['runtime'] = self.current
        manifest = dict(source_sha='b'*40, wwpg_version=self.version, artifacts=artifacts,
            ci_acceptance_reuse=dict(schema=1, scope='runtime_version_only', source_sha='a'*40,
                runtime_version=self.old_version, artifacts=dict(artifacts, runtime=self.old)))
        self.assertEqual(gate.artifact_acceptance_source(manifest, self.root, 'ci_acceptance_reuse', roles)[0], 'a'*40)
        path = self.root / 'changed.jar'
        with zipfile.ZipFile(path, 'w') as z:
            z.writestr('META-INF/neoforge.mods.toml', f'version="{self.version}"')
            z.writestr('TEST_ONLY', b'changed executable/native input')
        manifest['artifacts']['runtime'] = self.file(path.name, path.read_bytes())
        with self.assertRaisesRegex(ValueError, 'changed beyond its version'):
            gate.artifact_acceptance_source(manifest, self.root, 'ci_acceptance_reuse', roles)

    def test_client_reuse_preserves_actual_source_and_rejects_changed_addon(self):
        roles = {'runtime', 'api', 'cee_api', 'example', 'upstream_example'}
        artifacts = {k: self.file(k + '.bin', ('TEST_ONLY ' + k).encode()) for k in roles if k != 'runtime'}
        artifacts['runtime'] = self.current
        manifest = dict(source_sha='b'*40, wwpg_version=self.version, artifacts=artifacts,
            client_acceptance_reuse=dict(schema=1, scope='runtime_identical_tooling_only', source_sha='a'*40,
                runtime_version=self.version, artifacts=dict(artifacts)))
        source, hashes = gate.artifact_acceptance_source(manifest, self.root, 'client_acceptance_reuse', roles)
        self.assertEqual(source, 'a'*40)
        self.assertEqual(manifest['source_sha'], 'b'*40)
        self.assertEqual(hashes['runtime'], self.current['sha256'])
        manifest['client_acceptance_reuse']['runtime_version'] = self.old_version
        with self.assertRaisesRegex(gate.PublicationError, 'cannot change runtime version'):
            gate.artifact_acceptance_source(manifest, self.root, 'client_acceptance_reuse', roles)
        manifest['client_acceptance_reuse']['runtime_version'] = self.version
        manifest['artifacts']['example'] = self.file('changed-example.bin', b'TEST_ONLY changed addon')
        with self.assertRaisesRegex(gate.PublicationError, 'Client reuse changed input: example'):
            gate.artifact_acceptance_source(manifest, self.root, 'client_acceptance_reuse', roles)


if __name__ == '__main__':
    unittest.main()
