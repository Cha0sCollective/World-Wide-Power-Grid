"""Test presentation, byte preservation and upload safety without launching Minecraft.

Tiny TEST_ONLY bundles exercise grouping with mocked acceptance gates. They are
never acceptance evidence; an unmocked test rejects their incomplete manifest.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import release_downloads as release
from package_api_release import PublicationError, locked_file, sha


class ReleaseChecks(unittest.TestCase):
    def test_optional_benchmark_inventory_and_actual_label(self):
        prefix = 'WWPG Benchmark - MIXED - 14650 electrical nodes/'
        files = {'level.dat': b'TEST_ONLY level', 'WWPG_README.txt': b'TEST_ONLY instructions'}
        record = dict(kind='mixed_capacity_benchmark', target=self.version, electrical_nodes=14650,
                      connections=20637, stateful_devices=1050, chunks=953, certified_20_tps=False,
                      jar=dict(sha256=self.manifest['artifacts']['runtime']['sha256']),
                      files=[self.descriptor(n, d) for n, d in files.items()])
        path = self.root / 'benchmark.zip'
        with zipfile.ZipFile(path, 'w') as archive:
            for name, data in files.items(): archive.writestr(prefix + name, data)
            archive.writestr(prefix + 'WWPG_MANIFEST.json', json.dumps(record))
        self.manifest['benchmark_world'] = self.descriptor('benchmark.zip', path.read_bytes())
        for key in release.WORLD_KEYS:
            target = self.root / self.manifest[key]['path']
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(self.entries['evidence/' + self.manifest[key]['path']])
        values = release.world_payload(self.manifest, self.root, self.version)
        self.assertEqual(values[f'worlds/wwpg-{self.version}-benchmark-14650-nodes.zip'], path.read_bytes())
        for field, value in [('electrical_nodes', 10000), ('certified_20_tps', True), ('files', [])]:
            changed = dict(record, **{field: value})
            with zipfile.ZipFile(path, 'w') as archive:
                for name, data in files.items(): archive.writestr(prefix + name, data)
                archive.writestr(prefix + 'WWPG_MANIFEST.json', json.dumps(changed))
            self.manifest['benchmark_world'] = self.descriptor('benchmark.zip', path.read_bytes())
            with self.subTest(field=field), self.assertRaises(PublicationError):
                release.world_payload(self.manifest, self.root, self.version)

    def setUp(self):
        (release.ROOT / 'build').mkdir(exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix='TEST_ONLY-release-', dir=release.ROOT / 'build')
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.config = release.read(release.ROOT / 'release/presentation.json')
        self.config['changes'] = ['Makes mixed factories easier to inspect.']
        self.config_path = self.root / 'presentation.json'
        release.write_json(self.config_path, self.config)
        self.version = '0.3.1-beta.1'
        self.manifest = dict(schema=1, wwpg_version=self.version, api_version='1.1.0', source_sha='a' * 40, artifacts={})
        self.entries = {'LICENSE': b'TEST_ONLY license', 'docs/EXTENSION_API.md': b'TEST_ONLY guide'}
        runtime = self.root / 'runtime.jar'
        release.bundle(runtime, {'META-INF/POWER-GRID-LICENSE.txt': b'TEST_ONLY PG license',
                                 'META-INF/WWPG-NATIVE-NOTICE.txt': b'TEST_ONLY native notice'})
        for role in sorted(release.acceptance.ROLES):
            suffix = '.zip' if role.endswith('sources') or role == 'maven_repository' else '.jar'
            filename = 'wwpg-' + self.version + '.jar' if role == 'runtime' else role + '-1.1.0' + suffix
            data = runtime.read_bytes() if role == 'runtime' else ('TEST_ONLY ' + role).encode()
            self.manifest['artifacts'][role] = self.descriptor('inputs/' + filename, data)
            self.entries['artifacts/' + filename] = data
            # Actual accepted archives may retain a second identical copy via proof references.
            self.entries['evidence/inputs/' + filename] = data
        for key in release.WORLD_KEYS:
            data = ('TEST_ONLY ' + key).encode()
            self.manifest[key] = self.descriptor('worlds/' + key + '.zip', data)
            self.entries['evidence/worlds/' + key + '.zip'] = data
        self.source = self.root / 'TEST_ONLY-bundle.zip'
        self.make_source()
        self.output = self.root / 'stage'

    @staticmethod
    def descriptor(path, data):
        return dict(path=path, sha256=hashlib.sha256(data).hexdigest(), bytes=len(data))

    def make_source(self, candidate=False):
        self.source.unlink(missing_ok=True)
        entries = dict(self.entries)
        entries['manifest.json'] = json.dumps(self.manifest).encode()
        entries['PUBLICATION_STATUS.json'] = json.dumps(dict(schema=1, wwpg_version=self.version,
            api_version='1.1.0', publishable=not candidate,
            state='sdk_candidate' if candidate else 'accepted_release')).encode()
        release.bundle(self.source, entries)

    def mock_gates(self):
        artifact = patch.object(release.acceptance, 'validate_artifacts',
            side_effect=lambda manifest, root: {role: locked_file(record, root) for role, record in manifest['artifacts'].items()})
        gate = patch.object(release.acceptance, 'validate_release')
        return artifact, gate

    def stage(self):
        artifact, gate = self.mock_gates()
        with artifact as artifact_mock, gate as gate_mock:
            result = release.stage(self.source, sha(self.source), self.config_path, self.output)
            artifact_mock.assert_called_once()
            gate_mock.assert_called_once()
        return result

    def check(self):
        artifact, gate = self.mock_gates()
        with artifact, gate:
            return release.check(self.output)

    def refresh_assets(self):
        layout = release.read(self.output / 'download-layout.json')
        for asset in layout['assets']:
            file = self.output / 'downloads' / asset['filename']
            asset.update(sha256=sha(file), bytes=file.stat().st_size)
        sums = self.output / 'downloads' / release.names(self.version)[-1]
        sums.write_text(''.join(a['sha256'] + '  ' + a['filename'] + '\n' for a in layout['assets'][:-1]), encoding='utf-8')
        layout['assets'][-1].update(sha256=sha(sums), bytes=sums.stat().st_size)
        release.write_json(self.output / 'download-layout.json', layout)

    def test_five_files_future_versions_and_short_notes(self):
        layout = self.stage()
        self.assertEqual([a['filename'] for a in layout['assets']], release.names(self.version))
        self.assertEqual(layout['api_version'], '1.1.0')
        self.assertEqual(layout, self.check())
        body = (self.output / 'release-notes.md').read_text(encoding='utf-8')
        self.assertLessEqual(len(body.split()), 250)
        self.assertIn('/' + self.version + '/wwpg-' + self.version + '.jar', body)
        self.assertNotIn('0.2.0', body)
        self.assertEqual(self.source.read_bytes(), (self.output / 'downloads' / release.names(self.version)[3]).read_bytes())

    def test_inner_worlds_and_sdk_are_unchanged(self):
        self.stage()
        with zipfile.ZipFile(self.output / 'downloads' / release.names(self.version)[1]) as worlds:
            for key, label in release.WORLD_KEYS.items():
                self.assertEqual(worlds.read(f'worlds/wwpg-{self.version}-{label}.zip'), self.entries['evidence/worlds/' + key + '.zip'])
        with zipfile.ZipFile(self.output / 'downloads' / release.names(self.version)[2]) as sdk:
            for role in release.SDK_ROLES:
                filename = Path(self.manifest['artifacts'][role]['path']).name
                self.assertEqual(sdk.read('api/' + filename), self.entries['artifacts/' + filename])

    def test_staging_is_deterministic(self):
        first = self.stage()
        self.output = self.root / 'second-stage'
        self.assertEqual(first, self.stage())

    def test_preserves_prior_output(self):
        self.stage()
        before = (self.output / 'download-layout.json').read_bytes()
        with self.assertRaisesRegex(PublicationError, 'fresh output'):
            release.stage(self.source, sha(self.source), self.config_path, self.output)
        self.assertEqual(before, (self.output / 'download-layout.json').read_bytes())

    def test_rejects_candidate_even_with_correct_hash(self):
        self.make_source(candidate=True)
        with self.assertRaisesRegex(PublicationError, 'candidates'):
            release.stage(self.source, sha(self.source), self.config_path, self.output)
        self.assertFalse(self.output.exists())

    def test_accepted_flag_does_not_bypass_real_acceptance(self):
        with self.assertRaises(PublicationError):
            release.stage(self.source, sha(self.source), self.config_path, self.output)
        self.assertFalse(self.output.exists())

    def test_changed_bundle_hash_blocks_before_extraction(self):
        with self.assertRaisesRegex(PublicationError, 'SHA256'):
            release.stage(self.source, 'b' * 64, self.config_path, self.output)

    def test_changed_member_and_correct_outer_hash_still_blocks(self):
        with zipfile.ZipFile(self.source) as archive:
            files = {name: archive.read(name) for name in archive.namelist()}
        files['LICENSE'] = b'changed'
        self.source.unlink()
        with zipfile.ZipFile(self.source, 'w') as archive:
            for name, data in files.items():
                archive.writestr(name, data)
        with self.assertRaisesRegex(PublicationError, 'Changed archive member'):
            release.stage(self.source, sha(self.source), self.config_path, self.output)

    def test_unsafe_zip_entries_and_symlink_block(self):
        for name in ('../escape', '/absolute', 'C:/escape', 'folder\\escape', 'safe/link'):
            with self.subTest(name=name):
                self.make_source()
                with zipfile.ZipFile(self.source, 'a') as archive:
                    entry = zipfile.ZipInfo(name)
                    if name == 'safe/link':
                        entry.external_attr = 0o120777 << 16
                    archive.writestr(entry, b'target')
                with self.assertRaises(PublicationError):
                    release.stage(self.source, sha(self.source), self.config_path, self.output)

    def test_long_notes_and_many_highlights_block(self):
        for mutation in (dict(changes=['a', 'b', 'c', 'd']),
                         dict(changes=['word ' * 100 + 'end'] * 3)):
            with self.subTest(mutation=mutation):
                with self.assertRaises(PublicationError):
                    release.notes(self.version, {**self.config, **mutation})

    def test_changed_local_asset_extra_file_and_notes_block(self):
        self.stage()
        runtime = self.output / 'downloads' / release.names(self.version)[0]
        original = runtime.read_bytes()
        runtime.write_bytes(b'changed')
        with self.assertRaises(PublicationError): self.check()
        runtime.write_bytes(original)
        extra = self.output / 'downloads' / 'extra.zip'
        extra.write_bytes(b'noise')
        with self.assertRaisesRegex(PublicationError, 'Unexpected public'): self.check()
        extra.unlink()
        (self.output / 'release-notes.md').write_text('changed', encoding='utf-8')
        with self.assertRaisesRegex(PublicationError, 'notes changed'): self.check()

    def test_rewritten_layout_cannot_hide_changed_group_payload(self):
        self.stage()
        name = release.names(self.version)[2]
        file = self.output / 'downloads' / name
        with zipfile.ZipFile(file) as archive:
            files = {name: archive.read(name) for name in archive.namelist() if name != 'SHA256SUMS.txt'}
        files['api/api-1.1.0.jar'] = b'changed'
        file.unlink()
        release.bundle(file, files)
        self.refresh_assets()
        with self.assertRaisesRegex(PublicationError, 'differs from acceptance'): self.check()

    def remote(self, layout, draft=True):
        return dict(tag_name=self.version, draft=draft, name='WWPG ' + self.version,
            body=release.notes(self.version, self.config), target_commitish=layout['source_sha'],
            prerelease=True, html_url='https://github.com/TEST_ONLY/draft',
            assets=[dict(name=a['filename'], digest='sha256:' + a['sha256'], size=a['bytes']) for a in layout['assets']])

    def test_publisher_creates_draft_with_exact_files_and_frozen_target(self):
        layout = self.stage()
        artifact, gate = self.mock_gates()
        with artifact, gate, patch.object(release, 'verify_tag', return_value=False), \
                patch.object(release, 'release_by_tag', side_effect=[None, self.remote(layout), self.remote(layout)]), \
                patch.object(release, 'gh') as gh:
            self.assertFalse(release.publish(self.output)['published'])
            command = gh.call_args_list[0].args
            self.assertEqual(command[:3], ('release', 'create', self.version))
            self.assertEqual(list(command[3:8]), [str(self.output / 'downloads' / name) for name in release.names(self.version)])
            self.assertIn('--draft', command)
            self.assertIn('--prerelease', command)
            self.assertEqual(command[command.index('--target') + 1], layout['source_sha'])
            self.assertEqual(gh.call_count, 1)

    def test_plan_never_contacts_github(self):
        self.stage()
        artifact, gate = self.mock_gates()
        with artifact, gate, patch.object(release, 'gh') as gh, patch.object(release, 'api_or_absent') as api:
            self.assertEqual(len(release.publish(self.output, plan=True)['layout']['assets']), 5)
            gh.assert_not_called()
            api.assert_not_called()

    def test_publisher_refuses_public_release_wrong_assets_or_target(self):
        layout = self.stage()
        for mutation in (dict(draft=False), dict(target_commitish='b' * 40),
                         dict(assets=[dict(name='unexpected.zip', size=1, digest='sha256:' + 'a' * 64)])):
            with self.subTest(mutation=mutation):
                artifact, gate = self.mock_gates()
                with artifact, gate, patch.object(release, 'verify_tag', return_value=False), \
                        patch.object(release, 'release_by_tag', return_value={**self.remote(layout), **mutation}), \
                        patch.object(release, 'gh') as gh:
                    with self.assertRaises(PublicationError): release.publish(self.output, make_public=True)
                    gh.assert_not_called()

    def test_resume_uploads_only_missing_then_explicitly_publishes(self):
        layout = self.stage()
        partial = self.remote(layout)
        partial['assets'] = partial['assets'][:1]
        artifact, gate = self.mock_gates()
        with artifact, gate, patch.object(release, 'verify_tag', return_value=False), \
                patch.object(release, 'release_by_tag', side_effect=[partial, self.remote(layout)]), \
                patch.object(release, 'gh') as gh:
            self.assertTrue(release.publish(self.output, make_public=True)['published'])
            command = gh.call_args_list[0].args
            self.assertEqual(command[:3], ('release', 'upload', self.version))
            self.assertEqual(list(command[3:7]), [str(self.output / 'downloads' / name) for name in release.names(self.version)[1:]])
            self.assertNotIn('--clobber', command)
            self.assertEqual(gh.call_args_list[-1].args[-1], '--draft=false')

    def test_remote_digest_mismatch_never_publishes(self):
        layout = self.stage()
        remote = self.remote(layout)
        remote['assets'][0]['digest'] = 'sha256:' + 'b' * 64
        artifact, gate = self.mock_gates()
        with artifact, gate, patch.object(release, 'verify_tag', return_value=False), \
                patch.object(release, 'release_by_tag', return_value=remote), patch.object(release, 'gh') as gh:
            with self.assertRaisesRegex(PublicationError, 'differs from staging'):
                release.publish(self.output, make_public=True)
            gh.assert_not_called()

    def test_github_errors_are_not_treated_as_absent(self):
        for status in ('401', '403', '500'):
            response = subprocess.CompletedProcess([], 1, 'HTTP/2.0 ' + status + '\n\n{}', 'error')
            with patch.object(release.subprocess, 'run', return_value=response):
                with self.assertRaises(PublicationError): release.api_or_absent('TEST_ONLY')
        response = subprocess.CompletedProcess([], 1, 'HTTP/2.0 404\n\n{}', 'not found')
        with patch.object(release.subprocess, 'run', return_value=response):
            self.assertIsNone(release.api_or_absent('TEST_ONLY'))

    def test_annotated_tag_must_resolve_to_frozen_commit(self):
        layout = dict(repository=self.config['repository'], version=self.version, source_sha='a' * 40)
        with patch.object(release, 'api_or_absent', return_value=dict(object=dict(type='tag', sha='c' * 40))), \
                patch.object(release, 'gh', return_value=json.dumps(dict(object=dict(type='commit', sha='b' * 40)))):
            with self.assertRaisesRegex(PublicationError, 'another commit'): release.verify_tag(layout)

    def test_paginated_lookup_finds_drafts_without_using_published_by_tag_endpoint(self):
        draft = dict(tag_name=self.version, draft=True)
        with patch.object(release, 'gh', return_value=json.dumps([[dict(tag_name='0.1.0')], [draft]])) as gh:
            self.assertEqual(release.release_by_tag(self.config['repository'], self.version), draft)
            self.assertIn('--paginate', gh.call_args.args)
            self.assertIn('--slurp', gh.call_args.args)
            self.assertNotIn('/releases/tags/', gh.call_args.args[1])

    def test_publication_rechecks_the_uploaded_bytes_before_publishing(self):
        layout = self.stage()
        corrupted = self.remote(layout)
        corrupted['assets'][0]['digest'] = 'sha256:' + 'b' * 64
        artifact, gate = self.mock_gates()
        with artifact, gate, patch.object(release, 'verify_tag', return_value=False), \
                patch.object(release, 'release_by_tag', side_effect=[None, self.remote(layout), corrupted]), \
                patch.object(release, 'gh') as gh:
            with self.assertRaisesRegex(PublicationError, 'Uploaded bytes mismatch'):
                release.publish(self.output, make_public=True)
            self.assertEqual(gh.call_count, 1)  # Draft creation happened; publishing did not.


if __name__ == '__main__':
    unittest.main()
