"""Synthetic provenance controls; compilation and Git are mocked, never executed."""
import contextlib
import io
import json
import os
from pathlib import Path
import runpy
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import authoring_starter_acceptance as starter


class StarterSourceChecks(unittest.TestCase):
    RUNTIME_SOURCE = 'a' * 40
    DOCUMENTATION_SOURCE = 'b' * 40

    def setUp(self):
        scratch = Path(__file__).resolve().parents[1] / 'build/starter-source-tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix='TEST_ONLY-starter-', dir=scratch)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.runtime = self.root / 'runtime.jar'
        self.runtime.write_bytes(b'TEST_ONLY_FROZEN_RUNTIME')
        self.api = self.root / 'build/libs/wwpg-api-1.0.0.jar'
        self.api.parent.mkdir(parents=True)
        self.api.write_bytes(b'TEST_ONLY_FROZEN_PUBLIC_API')
        self.repository = self.root / 'repository.zip'
        with zipfile.ZipFile(self.repository, 'w') as archive:
            archive.writestr('TEST_ONLY_REPOSITORY', b'No real Maven resolution')

    def fake_package(self, source, root, output):
        with zipfile.ZipFile(output, 'w') as archive:
            for name, data in [('gradlew', b'#!/bin/sh\n'), ('gradlew.bat', b'@echo off\r\n'),
                               ('README.md', ('TEST_ONLY_DOCUMENTATION_SOURCE=' + self.DOCUMENTATION_SOURCE).encode())]:
                info = zipfile.ZipInfo(name)
                info.create_system = 3
                info.external_attr = (0o100755 if name == 'gradlew' else 0o100644) << 16
                archive.writestr(info, data)

    def fake_extract(self, archive, target):
        target.mkdir()
        with zipfile.ZipFile(archive) as zipped:
            zipped.extractall(target)
            if os.name != 'nt':
                for item in zipped.infolist():
                    (target / item.filename).chmod(item.external_attr >> 16 & 0o777)

    def prepare(self, output, runtime_source=None, changed_dependency=None, api_artifact=None):
        def fake_run(command, log, project):
            compiled = project / 'build/libs/tutorial-load-0.1.0.jar'
            compiled.parent.mkdir(parents=True)
            compiled.write_bytes(b'TEST_ONLY_IDENTICAL_COMPILED_STARTER')
            log.write_text('TEST_ONLY_MOCK_BUILD\nBUILD SUCCESSFUL\n', encoding='utf-8')
            provenance = dict(coordinate='org.cha0scollective.wwpg:wwpg-api:1.0.0',
                api_sha256=starter.sha(self.api), jar_sha256=starter.sha(compiled), wwpg_project_dependencies=[])
            if changed_dependency:
                provenance.update(changed_dependency)
            (project / 'build/sdk-provenance.json').write_text(json.dumps(provenance), encoding='utf-8')
        with patch.object(starter, 'ROOT', self.root), \
                patch.object(starter, 'package_example', side_effect=self.fake_package), \
                patch.object(starter, 'extract', side_effect=self.fake_extract), \
                patch.object(starter, 'run', side_effect=fake_run), \
                patch.object(starter.subprocess, 'check_output', return_value=self.DOCUMENTATION_SOURCE + '\n'), \
                contextlib.redirect_stdout(io.StringIO()):
            starter.prepare(output, self.runtime, self.repository, runtime_source, api_artifact=api_artifact)
        return json.loads((output / 'build-proof.json').read_text(encoding='utf-8'))

    def test_default_keeps_checkout_source_as_acceptance_source(self):
        proof = self.prepare(self.root / 'default')
        self.assertEqual(proof['source_sha'], self.DOCUMENTATION_SOURCE)
        self.assertEqual(proof['project_source_sha'], self.DOCUMENTATION_SOURCE)
        self.assertEqual(proof['documentation_source_sha'], self.DOCUMENTATION_SOURCE)

    def test_frozen_api_works_on_a_fresh_platform_without_build_libs(self):
        frozen=self.root/'downloaded-api.jar';frozen.write_bytes(self.api.read_bytes());self.api.unlink();self.api=frozen
        proof=self.prepare(self.root/'fresh-platform',self.RUNTIME_SOURCE,api_artifact=frozen)
        self.assertEqual(proof['compiled_against_api_sha256'],starter.sha(frozen))

    def test_explicit_runtime_source_preserves_separate_project_and_jar_identities(self):
        proof = self.prepare(self.root / 'explicit', self.RUNTIME_SOURCE)
        self.assertEqual(proof['source_sha'], self.RUNTIME_SOURCE)
        self.assertEqual(proof['project_source_sha'], self.DOCUMENTATION_SOURCE)
        self.assertEqual(proof['documentation_source_sha'], self.DOCUMENTATION_SOURCE)
        self.assertEqual(proof['runtime_sha256'], starter.sha(self.runtime))
        self.assertEqual(proof['compiled_against_api_sha256'], starter.sha(self.api))
        source = self.root / proof['source']['path']
        self.assertEqual(proof['source_archive_sha256'], starter.sha(source))
        compiled = self.root / proof['compiled_output']['path']
        self.assertEqual(proof['compiled_output']['sha256'], starter.sha(compiled))
        self.assertFalse(proof['uses_wwpg_project_classes'])

    def test_readme_source_binding_does_not_change_compiled_output(self):
        default = self.prepare(self.root / 'default')
        explicit = self.prepare(self.root / 'explicit', self.RUNTIME_SOURCE)
        self.assertEqual(default['compiled_output']['sha256'], explicit['compiled_output']['sha256'])
        self.assertEqual(default['runtime_sha256'], explicit['runtime_sha256'])
        self.assertEqual(default['source_archive_sha256'], explicit['source_archive_sha256'])

    def test_bad_explicit_runtime_source_is_rejected_before_any_output_is_created(self):
        for index, source in enumerate(['', 'main', 'g' * 40, 'a' * 39, '0' * 40, 123]):
            with self.subTest(source=source):
                output = self.root / ('bad-' + str(index))
                with self.assertRaises(ValueError):
                    self.prepare(output, source)
                self.assertFalse(output.exists())

    def test_source_override_does_not_bypass_compiled_api_or_project_dependency_checks(self):
        for index, change in enumerate([dict(api_sha256='f' * 64), dict(jar_sha256='f' * 64),
                                        dict(wwpg_project_dependencies=['TEST_ONLY_PRIVATE_RUNTIME'])]):
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.prepare(self.root / ('dependency-' + str(index)), self.RUNTIME_SOURCE, change)

    def test_prepare_source_flag_is_rejected_for_later_server_modes(self):
        argv = [starter.__file__, 'server', '--output', str(self.root / 'output'),
                '--runtime', str(self.runtime), '--backend', 'NATIVE',
                '--runtime-source-sha', self.RUNTIME_SOURCE]
        with patch.object(sys, 'argv', argv), contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
            runpy.run_path(starter.__file__, run_name='__main__')
        self.assertEqual(error.exception.code, 2)


if __name__ == '__main__':
    unittest.main()
