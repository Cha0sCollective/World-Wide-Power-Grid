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

    def test_replacing_runtime_requires_current_sources_before_creating_output(self):
        manifest=self.root/'inputs.json'
        runner.write(manifest,{'artifacts': {}})
        args=argparse.Namespace(from_inputs=str(manifest),cee113='unused.jar',runtime='new.jar',current_sources=False)
        with self.assertRaisesRegex(ValueError,'new runtime requires --current-sources'):
            runner.freeze(args)

    def test_changed_player_sources_require_explicit_new_runtime(self):
        player=self.root/'src/main/java/Fixture.java'
        player.parent.mkdir(parents=True)
        player.write_text('old player')
        old_hash=runner.sha(player)
        artifact=self.root/'artifact.jar';artifact.write_bytes(b'fixture artifact')
        roles=['runtime','gametest','reference',*runner.CEE]
        inputs={'artifacts':{role:{'path':str(artifact),'sha256':runner.sha(artifact)} for role in roles},
                'sources':[{'path':'src/main/java/Fixture.java','sha256':old_hash}],
                'loader':'21.1.262','substeps':16,'cee_source_commit':'a'*40,'java_home':'fixture-jdk'}
        manifest=self.root/'inputs.json';runner.write(manifest,inputs)
        player.write_text('new player')
        args=argparse.Namespace(from_inputs=str(manifest),cee113=str(artifact),runtime=None,current_sources=True,
                                gametest=None,reference=None,baseline=None,output='local/frozen')
        def git(command,**kwargs):return 'src/main/java/Fixture.java\n' if command[1]=='ls-files' else 'b'*40
        with patch.object(runner,'ROOT',self.root),patch.object(runner,'validate_cee',return_value='fixture'), \
             patch.object(runner,'metadata',return_value='fixture'),patch.object(runner.subprocess,'check_output',side_effect=git):
            with self.assertRaisesRegex(ValueError,'Player source changed'):
                runner.freeze(args)
            self.assertFalse((self.root/'local/frozen').exists())
            args.runtime=str(artifact)
            runner.freeze(args)
        frozen=runner.json.loads((self.root/'local/frozen/inputs.json').read_text())
        self.assertIn('newly built runtime',frozen['source_origin'])
        self.assertEqual(frozen['sources'][0]['sha256'],runner.sha(player))

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
