"""Focused preflight/evidence checks; starts no Minecraft process."""
import argparse
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile

import cee_readiness as runner


class ReadinessRunnerTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=runner.ROOT / 'local')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.args = argparse.Namespace(group='core', runtime='cee113', backend='JAVA', phase='SETUP', tight_cee_residual=False)
        self.inputs = {'loader': '21.1.262', 'version': 'candidate', 'artifacts': {
            role: {'path': role + '.jar', 'sha256': role} for role in ['runtime', 'reference', 'gametest', *runner.CEE]}}
        self.loaded = {mod: {'sha256': role, 'version': runner.CEE['cee113'][0] if mod == 'electroenergetics' else 'candidate'}
                       for mod, role in [('electroenergetics', 'cee113'), ('wwpg', 'runtime'), ('wwpg_tests', 'gametest')]}
        self.loaded['neoforge'] = {'version': '21.1.262'}
        self.log = 'All 10 required tests passed\nGAME TESTS COMPLETE\nAll dimensions are saved\nGame test server shutting down'

    def test_rejects_wrong_embedded_version_even_with_matching_hash(self):
        jar = self.root / 'wrong.jar'
        with ZipFile(jar, 'w') as out:
            out.writestr('META-INF/neoforge.mods.toml', '[[mods]]\nmodId="electroenergetics"\nversion="1.21.1-1.1.4"\n')
        with patch.dict(runner.CEE, cee113=('1.21.1-1.1.3', runner.sha(jar))):
            with self.assertRaisesRegex(ValueError, 'cee113: expected'):
                runner.validate_cee('cee113', jar)

    def test_rejects_source_path_escapes(self):
        for name in ('../secret', '/absolute', 'C:/absolute', 'src/../../secret'):
            with self.subTest(name=name), self.assertRaises(ValueError):
                runner.relative(name)

    def test_output_is_new_and_local(self):
        with self.assertRaisesRegex(ValueError, 'existing attempt'):
            runner.output_path(self.root)
        with self.assertRaisesRegex(ValueError, 'under local/ or build/'):
            runner.output_path(runner.ROOT / 'src/new-output')

    def test_source_snapshot_is_checked_independently_of_working_tree(self):
        frozen = self.root / 'sources/src/fixture.java'
        frozen.parent.mkdir(parents=True)
        frozen.write_text('frozen')
        inputs = {'artifacts': {}, 'sources': [{'path': 'src/fixture.java', 'sha256': runner.sha(frozen)}]}
        runner.verify(inputs, self.root / 'inputs.json')
        frozen.write_text('modified')
        with self.assertRaisesRegex(ValueError, 'Frozen source changed'):
            runner.verify(inputs, self.root / 'inputs.json')

    def test_rejects_artifact_mutation(self):
        jar = self.root / 'player.jar'
        jar.write_bytes(b'original')
        inputs = {'sources': [], 'artifacts': {'runtime': {'path': jar, 'sha256': runner.sha(jar)}}}
        jar.write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'Frozen artifact changed'):
            runner.verify(inputs, self.root / 'inputs.json')

    def test_clean_execution_has_no_failures(self):
        self.assertEqual(runner.collect(self.log, 0, self.loaded, self.inputs, self.args), ([], 10))

    def test_zero_exit_without_tests_is_not_a_pass(self):
        failures, count = runner.collect('BUILD SUCCESSFUL', 0, self.loaded, self.inputs, self.args)
        self.assertTrue(failures)
        self.assertIsNone(count)

    def test_loaded_identity_must_match(self):
        for mod, field in [('electroenergetics', 'version'), ('electroenergetics', 'sha256'),
                           ('wwpg', 'sha256'), ('wwpg_tests', 'sha256'), ('neoforge', 'version')]:
            loaded = copy.deepcopy(self.loaded)
            loaded[mod][field] = 'wrong'
            with self.subTest(mod=mod, field=field):
                self.assertTrue(runner.collect(self.log, 0, loaded, self.inputs, self.args)[0])

    def test_incomplete_or_wrong_selection_is_not_a_pass(self):
        for text in (self.log.replace('All dimensions are saved', ''), self.log.replace('10 required', '1 required')):
            self.assertTrue(runner.collect(text, 0, self.loaded, self.inputs, self.args)[0])

    def test_launch_excludes_rebuilds_and_selects_only_requested_namespaces(self):
        cmd = runner.command(self.inputs, self.args, self.root / 'game')
        self.assertIn('-PceeRuntimeJar=cee113.jar', cmd)
        self.assertIn('-PtestNamespaces=wwpg_cee_readiness,wwpg_cee_migration', cmd)
        self.assertIn('-PtestFixtureArtifact=gametest.jar', cmd)
        self.assertIn('compileCee12GameTestJava', cmd)
        self.assertNotIn('runUpstreamReferenceServer', cmd)

    def test_diagnostic_is_restricted_to_modern_stock(self):
        self.args.tight_cee_residual = True
        with self.assertRaisesRegex(ValueError, 'only for the CEE 1.2 stock'):
            runner.command(self.inputs, self.args, self.root / 'game')

    def test_diagnostic_requires_observed_application_and_raw_execution_rejects_it(self):
        marker = '\nCEE_REFERENCE_RESIDUAL threshold=1.0E-16'
        self.assertTrue(runner.collect(self.log + marker, 0, self.loaded, self.inputs, self.args)[0])
        self.args.tight_cee_residual = True
        self.assertTrue(runner.collect(self.log, 0, self.loaded, self.inputs, self.args)[0])
        self.assertEqual(runner.collect(self.log + marker, 0, self.loaded, self.inputs, self.args), ([], 10))


if __name__ == '__main__':
    unittest.main()
