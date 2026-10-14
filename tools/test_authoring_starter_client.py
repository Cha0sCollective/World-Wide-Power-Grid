"""Focused launcher controls only: no Minecraft, Java, Gradle, Git or network."""
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import authoring_starter_acceptance as launcher

LAUNCHER = Path(launcher.__file__).resolve()
INIT = LAUNCHER.parent / 'documentation_acceptance/init.gradle'
RUNTIME_SOURCE = 'c' * 40
DOCUMENTATION_SOURCE = 'd' * 40
TOOL_SOURCE = 'e' * 40


class BeforeJvm(Exception):
    pass


class StarterClientChecks(unittest.TestCase):
    def setUp(self):
        scratch = Path(__file__).resolve().parents[1] / 'build/starter-client-tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix='TEST_ONLY-starter-client-', dir=scratch)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        self.addCleanup(patch.stopall)
        patch.object(launcher, 'ROOT', self.root).start()
        local_launcher = self.root / 'tools/authoring_starter_acceptance.py'
        local_init = self.root / 'tools/documentation_acceptance/init.gradle'
        local_init.parent.mkdir(parents=True)
        shutil.copyfile(LAUNCHER, local_launcher)
        shutil.copyfile(INIT, local_init)
        patch.object(launcher, '__file__', str(local_launcher)).start()
        patch.object(subprocess, 'Popen', side_effect=AssertionError('No child processes in launcher controls')).start()
        self.git = patch.object(launcher.subprocess, 'check_output', return_value=TOOL_SOURCE + '\n').start()
        self.runtime = self.root / 'frozen-runtime.jar'
        self.starter = self.root / 'accepted-starter.jar'
        self.example = self.root / 'accepted-example.jar'
        for path in (self.runtime, self.starter, self.example):
            path.write_bytes(path.name.encode('ascii'))
        self.output = self.root / 'fresh-client-attempt'
        self.output.mkdir()
        self.build_proof = dict(passed=True, source_sha=RUNTIME_SOURCE,
                               project_source_sha=DOCUMENTATION_SOURCE,
                               documentation_source_sha=DOCUMENTATION_SOURCE,
                               runtime_sha256=launcher.sha(self.runtime),
                               compiled_output=launcher.descriptor(self.starter))
        self.original_build_proof = (json.dumps(self.build_proof, indent=2) + '\n').encode('utf-8')
        (self.output / 'build-proof.json').write_bytes(self.original_build_proof)

    def archive(self, name, level=True):
        payload = {'level.dat': b'original level bytes'} if level else {'notes.txt': b'not a save'}
        manifest = dict(save_name=name, profile='BASE', jar={'sha256': launcher.sha(self.runtime)},
                        files={key: dict(sha256=launcher.hashlib.sha256(value).hexdigest(), bytes=len(value))
                               for key, value in payload.items()})
        path = self.root / 'unchanged-world.zip'
        with zipfile.ZipFile(path, 'w') as archive:
            for key, value in {**payload, 'WWPG_MANIFEST.json': json.dumps(manifest).encode(), 'WWPG_README.txt': b'fixture'}.items():
                archive.writestr(name + '/' + key, value)
        return path

    def call(self, world, backend='JAVA'):
        launcher.client(self.output, self.runtime, backend, world, self.example)

    def assert_selected_name(self, name):
        world = self.archive(name)
        before = world.read_bytes()
        with patch.object(Path, 'rename', side_effect=AssertionError('The save must not be renamed')), \
                patch.object(launcher, 'run', side_effect=BeforeJvm) as run:
            with self.assertRaises(BeforeJvm):
                self.call(world)
        command = [str(value) for value in run.call_args.args[0]]
        self.assertIn('-PdocWorldName=' + name, command)
        self.assertEqual(command[command.index('--timeout') + 1], '600')
        self.assertIn('-Pneo_version=21.1.248', command)
        self.assertEqual((self.output / ('client-JAVA/saves/' + name) / 'level.dat').read_bytes(), b'original level bytes')
        self.assertFalse((self.output / 'client-JAVA/saves/Documentation Test').exists())
        self.assertEqual(world.read_bytes(), before)
        self.assertEqual((self.output / 'build-proof.json').read_bytes(), self.original_build_proof)

    def test_actual_extracted_basename_is_passed_without_rename(self):
        self.assert_selected_name('StationaryYard')

    def test_spaces_and_unicode_are_one_gradle_property_argument(self):
        self.assert_selected_name('Stationary Yard BASE beta.2 Ω')

    def test_missing_saves_directory_is_rejected_before_launch(self):
        world = self.archive('Yard')
        with patch.object(launcher, 'extract_world', side_effect=lambda archive, directory, client: directory.mkdir()), \
                patch.object(launcher, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'one playable world'):
                self.call(world)
        run.assert_not_called()

    def test_missing_level_dat_is_rejected_before_launch(self):
        world = self.archive('Yard', level=False)
        with patch.object(launcher, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'one playable world'):
                self.call(world)
        run.assert_not_called()

    def test_multiple_extracted_worlds_are_rejected_before_launch(self):
        world = self.archive('Yard')
        def extract(archive, directory, client):
            for name in ('First', 'Second'):
                path = directory / 'saves' / name
                path.mkdir(parents=True)
                (path / 'level.dat').write_bytes(b'world')
        with patch.object(launcher, 'extract_world', side_effect=extract), patch.object(launcher, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'one playable world'):
                self.call(world)
        run.assert_not_called()

    def test_plain_file_in_saves_is_not_a_world(self):
        saves = self.root / 'directory/saves'
        saves.mkdir(parents=True)
        (saves / 'not-a-world').write_bytes(b'file')
        with self.assertRaisesRegex(ValueError, 'one playable world'):
            launcher.client_world_name(saves.parent)

    def test_unsafe_archive_basename_is_rejected_before_launch(self):
        world = self.archive('../outside')
        with patch.object(launcher, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'save folder name'):
                self.call(world)
        run.assert_not_called()

    def test_unsafe_control_character_basename_is_rejected(self):
        directory = self.root / 'directory'
        with patch.object(Path, 'is_dir', return_value=True), patch.object(Path, 'is_file', return_value=True), \
                patch.object(Path, 'is_symlink', return_value=False), \
                patch.object(Path, 'iterdir', return_value=iter([directory / 'saves' / 'yard\n--extra'])):
            with self.assertRaisesRegex(ValueError, 'safe save folder basename'):
                launcher.client_world_name(directory)

    def test_existing_client_attempt_is_preserved(self):
        world = self.archive('Yard')
        directory = self.output / 'client-JAVA'
        directory.mkdir()
        (directory / 'failed-evidence').write_bytes(b'preserve')
        with patch.object(launcher, 'extract_world') as extract, patch.object(launcher, 'run') as run:
            with self.assertRaisesRegex(ValueError, 'Preserve earlier graphical attempt'):
                self.call(world)
        extract.assert_not_called()
        run.assert_not_called()
        self.assertEqual((directory / 'failed-evidence').read_bytes(), b'preserve')

    def passing_run(self, command, log):
        directory = self.output / 'client-JAVA'
        helper = self.root / 'build/doc-acceptance/wwpg-documentation-acceptance.jar'
        helper.parent.mkdir(parents=True)
        helper.write_bytes(b'helper')
        log.write_text('BUILD SUCCESSFUL\nDOC_CLIENT_PASSED\n', encoding='utf-8')
        (directory / 'doc-client-result.json').write_text(json.dumps(dict(
            passed=True, backend='JAVA', runtime_sha256=launcher.sha(self.runtime),
            starter_sha256=launcher.sha(self.starter), helper_sha256=launcher.sha(helper),
            controls=True, native_tools=True, client_meter_volts=20.0)), encoding='utf-8')
        (directory / 'documentation-client-program.txt').write_text('--quickPlaySingleplayer\n"Stationary Yard"\n', encoding='utf-8')
        screens = directory / 'screenshots'
        screens.mkdir()
        for name in ('load-100-on.png', 'load-400-off.png'):
            (screens / name).write_bytes(b'fixture image' * 200)

    def test_success_keeps_runtime_documentation_and_launcher_identities_separate(self):
        world = self.archive('Stationary Yard')
        with patch.object(Path, 'rename', side_effect=AssertionError('No save rename')), \
                patch.object(launcher, 'run', side_effect=self.passing_run):
            self.call(world)
        result = json.loads((self.output / 'JAVA-graphical.json').read_text(encoding='utf-8'))
        self.assertEqual(result['source_sha'], RUNTIME_SOURCE)
        self.assertEqual(result['launcher_source_sha'], TOOL_SOURCE)
        self.assertEqual(result['input_save_name'], 'Stationary Yard')
        program = self.root / result['launcher_program']['path']
        self.assertEqual(launcher.sha(program), result['launcher_program']['sha256'])
        self.assertIn(result['launcher_program'], result['evidence'])
        self.assertEqual({Path(item['path']).name for item in result['launcher_tools']},
                         {'authoring_starter_acceptance.py', 'init.gradle'})
        for item in result['launcher_tools']:
            self.assertIn('/launcher-inputs/', item['path'])
            self.assertEqual(launcher.sha(self.root / item['path']), item['sha256'])
        self.assertEqual((self.output / 'build-proof.json').read_bytes(), self.original_build_proof)

    def test_launcher_change_during_run_cannot_be_reported_as_passed(self):
        world = self.archive('Stationary Yard')
        def modified_run(command, log):
            self.passing_run(command, log)
            (self.root / 'tools/documentation_acceptance/init.gradle').write_bytes(b'changed')
        with patch.object(launcher, 'run', side_effect=modified_run):
            with self.assertRaisesRegex(ValueError, 'Launcher tools changed'):
                self.call(world)
        self.assertFalse((self.output / 'JAVA-graphical.json').exists())

    def test_missing_argument_file_cannot_be_reported_as_passed(self):
        world = self.archive('Stationary Yard')
        def missing_program(command, log):
            self.passing_run(command, log)
            (self.output / 'client-JAVA/documentation-client-program.txt').unlink()
        with patch.object(launcher, 'run', side_effect=missing_program):
            with self.assertRaisesRegex(ValueError, 'launcher argument file'):
                self.call(world)
        self.assertFalse((self.output / 'JAVA-graphical.json').exists())


if __name__ == '__main__':
    unittest.main()
