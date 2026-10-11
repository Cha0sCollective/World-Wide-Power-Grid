"""Exercise inert child commands, including Windows BAT paths with spaces."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from run_checked import launch_command

ROOT = Path(__file__).resolve().parents[1]


class CheckedRunnerTests(unittest.TestCase):
    def setUp(self):
        scratch = ROOT / 'build/checked-runner-tool-tests'; scratch.mkdir(parents=True, exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix='space directory ', dir=scratch)
        self.directory = Path(self.temporary.name).resolve()
        self.assertTrue(self.directory.is_relative_to(ROOT))

    def tearDown(self): self.temporary.cleanup()

    def test_non_batch_process_preserves_argument_boundaries(self):
        args = [sys.executable, '-c', 'import sys,json; print(json.dumps(sys.argv[1:]))', 'space value', 'a&b']
        result = subprocess.run(launch_command(args), capture_output=True, text=True, check=True)
        self.assertEqual(json.loads(result.stdout), args[3:])

    def test_unix_command_remains_an_argument_list(self):
        with patch('run_checked.os.name', 'posix'):
            self.assertEqual(launch_command(['./gradlew', '-Pvalue=a b']), ['./gradlew', '-Pvalue=a b'])

    @unittest.skipUnless(os.name == 'nt', 'Windows batch command execution')
    def test_real_inert_batch_handles_absolute_relative_and_default_wrapper_paths(self):
        wrapper = self.directory / 'gradlew.bat'
        wrapper.write_text('@echo off\r\n"' + sys.executable + '" -c "import sys,json; print(json.dumps(sys.argv[1:]))" %*\r\nexit /b %errorlevel%\r\n', encoding='utf-8')
        other = self.directory / 'different working directory'; other.mkdir()
        for name, cwd in [(str(wrapper), self.directory), ('gradlew.bat', self.directory),
                          ('./gradlew', self.directory), (str(wrapper), other)]:
            args = [name, '-Ppath=with spaces', 'literal & operator', 'literal^caret', '(parentheses)', 'a!b']
            with self.subTest(wrapper=name, cwd=cwd):
                result = subprocess.run(launch_command(args, cwd), cwd=cwd, capture_output=True, text=True, timeout=10)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads(result.stdout), args[1:])

    @unittest.skipUnless(os.name == 'nt', 'Windows batch argument validation')
    def test_batch_shell_expansions_and_quote_breakouts_are_rejected(self):
        for value in ['%PATH%', 'break" & echo injected', 'line\nbreak', 'line\rbreak']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                launch_command(['gradlew.bat', value], self.directory)


if __name__ == '__main__': unittest.main()
