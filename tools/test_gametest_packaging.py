"""Packaging boundaries for the separately installed GameTest companion."""
from pathlib import Path
import re
import unittest
import tempfile
from zipfile import ZipFile
import test_suites as suites


class GameTestPackaging(unittest.TestCase):
    def test_companion_owns_all_game_tests(self):
        root=suites.ROOT
        self.assertFalse(list((root/'src/main/java/org/cha0scollective/wwpg/gametest').rglob('*.java')))
        methods=0
        for file in (root/'src/gameTest/java').rglob('*.java'):
            methods+=len(re.findall(r'@GameTest\s*(?:\(|\n)',file.read_text(encoding='utf-8')))
        self.assertEqual(methods,344)
        self.assertEqual(suites.audit()['namespaces'],40)

    def test_player_sources_do_not_link_test_helpers(self):
        for file in (suites.ROOT/'src/main/java').rglob('*.java'):
            self.assertNotIn('org.cha0scollective.wwpg.gametest',file.read_text(encoding='utf-8'),str(file))

    def test_separated_runtime_cannot_omit_the_frozen_companion(self):
        with tempfile.TemporaryDirectory(dir=suites.ROOT/'build') as folder:
            root=Path(folder)
            runtime=root/'player.jar'
            with ZipFile(runtime,'w') as jar:jar.writestr('META-INF/neoforge.mods.toml','test-only')
            artifacts={role:suites.descriptor(runtime) for role in suites.ROLES}
            manifest=root/'inputs.json'
            suites.write(manifest,dict(schema=2,policy='separated-v2',source_sha='a'*40,artifacts=artifacts))
            with self.assertRaisesRegex(ValueError,'requires a frozen GameTest companion'):
                suites.frozen_inputs(manifest)

    def test_legacy_runtime_keeps_its_original_embedded_fixtures(self):
        with tempfile.TemporaryDirectory(dir=suites.ROOT/'build') as folder:
            root=Path(folder);runtime=root/'legacy.jar'
            with ZipFile(runtime,'w') as jar:
                jar.writestr('org/cha0scollective/wwpg/gametest/WiringGameTests.class',b'test-only')
            manifest=root/'inputs.json'
            suites.write(manifest,dict(schema=2,policy='separated-v2',source_sha='a'*40,
                artifacts={role:suites.descriptor(runtime) for role in suites.ROLES}))
            self.assertNotIn('gametest',suites.frozen_inputs(manifest)['artifacts'])

    def test_packaged_launch_explicitly_selects_frozen_companion(self):
        inputs={'artifacts':{r:{'path':r+'.jar'} for r in (*suites.ROLES,'gametest')}}
        job=suites.plan('focused',groups=['api'],backends=['JAVA'],profiles=['BASE'])[0]
        command,_=suites.command(job,inputs,Path('build/packaging-test'))
        self.assertIn('-PtestArtifact=runtime.jar',command)
        self.assertIn('-PtestFixtureArtifact=gametest.jar',command)
        self.assertIn('-x',command)

    def test_built_player_jar_excludes_tests_and_keeps_native_and_lua(self):
        props=dict(line.split('=',1) for line in (suites.ROOT/'gradle.properties').read_text().splitlines() if '=' in line)
        base=suites.ROOT/'build/libs'/('wwpg-'+props['mod_version'])
        with ZipFile(str(base)+'.jar') as player,ZipFile(str(base)+'-gametests.jar') as companion:
            self.assertFalse(any('/gametest/' in name for name in player.namelist()))
            self.assertFalse(any('/structure/empty.nbt' in name for name in player.namelist()))
            self.assertEqual(len([n for n in player.namelist() if n.endswith(('.dll','.so'))]),4)
            self.assertTrue(any(n.endswith('.lua') for n in player.namelist()))
            self.assertIn('org/cha0scollective/wwpg/gametest/WwpgGameTests.class',companion.namelist())
            self.assertIn('META-INF/neoforge.mods.toml',companion.namelist())
            self.assertTrue(any(n.endswith('/structure/empty.nbt') for n in companion.namelist()))
            sources=suites.ROOT/'src/gameTest/java'
            expected={path.relative_to(sources).with_suffix('.class').as_posix()
                      for path in sources.rglob('*.java')}
            self.assertTrue(expected <= set(companion.namelist()),
                            'Companion omitted compiled fixture classes: '+str(expected-set(companion.namelist())))


if __name__=='__main__':unittest.main()
