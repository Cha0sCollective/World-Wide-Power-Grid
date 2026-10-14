"""Synthetic packaging checks only; no JVM, Git call, network or acceptance claim."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import package_api_release as gate


class DocumentationSnapshotChecks(unittest.TestCase):
    RUNTIME_SOURCE = 'a' * 40
    DOC_SOURCE = 'b' * 40

    def setUp(self):
        scratch = Path(__file__).resolve().parents[1] / 'build/documentation-snapshot-tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix='TEST_ONLY-documentation-', dir=scratch)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.manifest = dict(schema=1, wwpg_version=gate.VERSION, api_version=gate.API_VERSION,
                             source_sha=self.RUNTIME_SOURCE, artifacts={})
        self.files = {}
        for name in sorted(gate.SDK_DOCUMENTS):
            live = self.root / name
            live.parent.mkdir(parents=True, exist_ok=True)
            live.write_text('TEST_ONLY_LIVE_ALPHA: ' + name + '\n', encoding='utf-8')
            raw = self.root / 'locked-docs/raw' / name
            raw.parent.mkdir(parents=True, exist_ok=True)
            text = 'TEST_ONLY_SNAPSHOT_BETA: ' + name + '\n'
            if name != 'LICENSE':
                text += ('[included](EXTENSION_API.md) [outside](../tools/verify_api_compatibility.py)\n'
                         '[record](../release/' + gate.VERSION + '/verification.json)\n')
            raw.write_text(text, encoding='utf-8')
            self.files[name] = self.descriptor(raw)
        self.snapshot = dict(schema=1, source_sha=self.DOC_SOURCE,
                             runtime_source_sha=self.RUNTIME_SOURCE, files=self.files)
        self.freeze()

    def descriptor(self, path):
        return dict(path=path.relative_to(self.root).as_posix(), bytes=path.stat().st_size,
                    sha256=hashlib.sha256(path.read_bytes()).hexdigest())

    def freeze(self):
        path = self.root / 'locked-docs/snapshot.json'
        path.write_text(json.dumps(self.snapshot, indent=2) + '\n', encoding='utf-8')
        self.manifest['documentation_snapshot'] = self.descriptor(path)

    def package_candidate(self):
        manifest = self.root / 'manifest.json'
        manifest.write_text(json.dumps(self.manifest), encoding='utf-8')
        output = self.root / 'TEST_ONLY-candidate.zip'
        # Artifact compilation/acceptance has its own tests. This fake package is
        # explicitly a nonpublishable candidate and mocks only its artifact input.
        with patch.object(gate, 'validate_artifacts', return_value={}):
            result = gate.package(manifest, self.root, output)
        self.assertFalse(result['publishable'])
        return output

    def test_legacy_manifests_keep_live_documents_and_runtime_link_source(self):
        del self.manifest['documentation_snapshot']
        source, paths = gate.documentation_inputs(self.manifest, self.root)
        self.assertEqual(source, self.RUNTIME_SOURCE)
        self.assertEqual(paths, {name: self.root / name for name in gate.SDK_DOCUMENTS})
        with zipfile.ZipFile(self.package_candidate()) as archive:
            self.assertIn(b'TEST_ONLY_LIVE_ALPHA', archive.read('docs/INSTALL.md'))

    def test_snapshot_overrides_live_tree_and_pins_only_documentation_links(self):
        source, paths = gate.documentation_inputs(self.manifest, self.root)
        self.assertEqual(source, self.DOC_SOURCE)
        self.assertEqual(set(paths), gate.SDK_DOCUMENTS)
        with zipfile.ZipFile(self.package_candidate()) as archive:
            install = archive.read('docs/INSTALL.md').decode('utf-8')
            self.assertIn('TEST_ONLY_SNAPSHOT_BETA', install)
            self.assertNotIn('TEST_ONLY_LIVE_ALPHA', install)
            self.assertIn('(EXTENSION_API.md)', install)
            self.assertIn('/blob/' + self.DOC_SOURCE + '/tools/verify_api_compatibility.py', install)
            self.assertIn('/releases/download/' + gate.VERSION + '/', install)
            self.assertNotIn('/blob/' + self.RUNTIME_SOURCE + '/', install)
            retained = json.loads(archive.read('manifest.json'))
            self.assertEqual(retained['source_sha'], self.RUNTIME_SOURCE)
            self.assertEqual(retained, self.manifest)
            self.assertIn('evidence/locked-docs/snapshot.json', archive.namelist())
            for name in gate.SDK_DOCUMENTS:
                self.assertEqual(archive.read('evidence/' + self.files[name]['path']), paths[name].read_bytes())

    def test_snapshot_reconstructs_without_git_checkout_or_live_guides(self):
        output = self.package_candidate()
        with tempfile.TemporaryDirectory(prefix='TEST_ONLY-reconstructed-', dir=self.root) as directory:
            root = Path(directory)
            with zipfile.ZipFile(output) as archive:
                for name in archive.namelist():
                    if not name.startswith('evidence/'):
                        continue
                    target = root / name.removeprefix('evidence/')
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(archive.read(name))
            self.assertFalse((root / '.git').exists())
            self.assertFalse((root / 'docs').exists())
            source, paths = gate.documentation_inputs(self.manifest, root)
            self.assertEqual(source, self.DOC_SOURCE)
            self.assertEqual(set(paths), gate.SDK_DOCUMENTS)
            paths['docs/INSTALL.md'].write_text('TEST_ONLY_TAMPERING', encoding='utf-8')
            with self.assertRaises(gate.PublicationError):
                gate.documentation_inputs(self.manifest, root)

    def test_invalid_documentation_source_is_rejected(self):
        for source in [None, 'main', 'g' * 40, 'b' * 39, '0' * 40]:
            with self.subTest(source=source):
                self.snapshot['source_sha'] = source
                self.freeze()
                with self.assertRaises(gate.PublicationError):
                    gate.documentation_inputs(self.manifest, self.root)

    def test_documentation_may_share_the_runtime_commit_when_content_is_locked(self):
        self.snapshot['source_sha'] = self.RUNTIME_SOURCE
        self.freeze()
        source, paths = gate.documentation_inputs(self.manifest, self.root)
        self.assertEqual(source, self.RUNTIME_SOURCE)
        self.assertEqual(set(paths), gate.SDK_DOCUMENTS)
        self.assertTrue(all(path.is_file() for path in paths.values()))

    def test_runtime_binding_and_schema_are_required(self):
        for field, bad in [('schema', 2), ('runtime_source_sha', None), ('runtime_source_sha', 'c' * 40)]:
            with self.subTest(field=field, value=bad):
                previous = self.snapshot[field]
                self.snapshot[field] = bad
                self.freeze()
                with self.assertRaises(gate.PublicationError):
                    gate.documentation_inputs(self.manifest, self.root)
                self.snapshot[field] = previous

    def test_exact_document_set_rejects_missing_and_extra_roles(self):
        for mutation in ['missing', 'extra']:
            with self.subTest(mutation=mutation):
                self.snapshot['files'] = dict(self.files)
                if mutation == 'missing':
                    del self.snapshot['files']['docs/INSTALL.md']
                else:
                    self.snapshot['files']['docs/UNREVIEWED.md'] = self.files['docs/INSTALL.md']
                self.freeze()
                with self.assertRaises(gate.PublicationError):
                    gate.documentation_inputs(self.manifest, self.root)

    def test_present_but_empty_snapshot_does_not_fall_back_to_live_tree(self):
        self.manifest['documentation_snapshot'] = {}
        with self.assertRaises(gate.PublicationError):
            gate.documentation_inputs(self.manifest, self.root)

    def test_changed_snapshot_and_changed_raw_document_are_rejected(self):
        path = self.root / self.manifest['documentation_snapshot']['path']
        original = path.read_bytes()
        path.write_bytes(original + b' ')
        with self.assertRaises(gate.PublicationError):
            gate.documentation_inputs(self.manifest, self.root)
        path.write_bytes(original)
        path = self.root / self.files['docs/INSTALL.md']['path']
        path.write_bytes(path.read_bytes() + b' ')
        with self.assertRaises(gate.PublicationError):
            gate.documentation_inputs(self.manifest, self.root)

    def test_bad_hash_size_and_escaping_input_are_rejected(self):
        for field, value in [('sha256', 'f' * 64), ('bytes', 0), ('path', '../outside.md'),
                             ('path', '/outside.md'), ('path', 'C:/outside.md')]:
            with self.subTest(field=field, value=value):
                previous = self.files['docs/INSTALL.md'][field]
                self.files['docs/INSTALL.md'][field] = value
                self.freeze()
                with self.assertRaises(gate.PublicationError):
                    gate.documentation_inputs(self.manifest, self.root)
                self.files['docs/INSTALL.md'][field] = previous

    def test_separate_documentation_does_not_replace_any_runtime_release_gate(self):
        # Reuse the existing pure acceptance-validator fixture, never Java's
        # separately compiled contract tests or any real release evidence.
        from test_api_compatibility import PublicationGateChecks
        release = PublicationGateChecks()
        release.setUp()
        self.addCleanup(release.tearDown)
        files = {}
        for name in gate.SDK_DOCUMENTS:
            path = release.root / 'doc-snapshot' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('TEST_ONLY_SNAPSHOT: ' + name, encoding='utf-8')
            files[name] = release.describe(path)
        release.manifest['documentation_snapshot'] = release.lock('documentation.json',
            dict(schema=1, source_sha=self.DOC_SOURCE, runtime_source_sha=release.source, files=files))
        gate.validate_release(release.manifest, release.root)
        release.manifest['source_sha'] = self.DOC_SOURCE
        with self.assertRaises(gate.PublicationError):
            gate.validate_release(release.manifest, release.root)
        release.manifest['source_sha'] = release.source
        release.change_job('actual_chunk_reload_cycles', 0)
        with self.assertRaises(gate.PublicationError):
            gate.validate_release(release.manifest, release.root)
        release.change_job('actual_chunk_reload_cycles', 5)
        gate.validate_release(release.manifest, release.root)
        self.assertEqual(release.manifest['source_sha'], self.RUNTIME_SOURCE)
        self.assertEqual(len(release.manifest['client_acceptance']), 8)


if __name__ == '__main__':
    unittest.main()
