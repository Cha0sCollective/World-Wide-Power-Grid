"""Pure archive/classpath gate tests; they do not execute or simulate Gradle builds."""
from pathlib import Path
import tempfile
import unittest
import zipfile

from package_api_release import PublicationError
from verify_extracted_api_sdk import SdkBuildError, audit_project, extract

ROOT = Path(__file__).resolve().parents[1]


class ExtractedSdkChecks(unittest.TestCase):
    def setUp(self):
        scratch = ROOT / 'build/extracted-sdk-tool-tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch)
        self.root = Path(self.temp.name)

    def tearDown(self): self.temp.cleanup()

    def project_zip(self):
        path = self.root / 'TEST_ONLY_source.zip'
        names = {'build.gradle': 'plugins { id "java-library" }', 'settings.gradle': 'rootProject.name="test"',
                 'gradlew': '#!/bin/sh\n', 'gradlew.bat': '@echo off\r\n', 'LICENSE': 'TEST ONLY', 'README.md': 'TEST ONLY',
                 'gradle/wrapper/gradle-wrapper.jar': 'TEST ONLY WRAPPER', 'gradle/wrapper/gradle-wrapper.properties': 'TEST ONLY',
                 'src/main/java/example/Test.java': 'package example; import org.cha0scollective.wwpg.api.ElectricalDevice;'}
        with zipfile.ZipFile(path, 'w') as output:
            for name, data in names.items(): output.writestr(name, data)
        return path

    def test_fresh_extracted_project_has_own_wrapper_and_no_checkout_link(self):
        output = self.root / 'extracted'
        extract(self.project_zip(), output)
        audit_project(output)
        with self.assertRaises(SdkBuildError): extract(self.root / 'TEST_ONLY_source.zip', output)

    def test_traversal_and_symlink_archives_are_rejected_before_extraction(self):
        for label, name, mode in [('traversal', '../outside', 0o100644), ('symlink', 'gradlew', 0o120777)]:
            source = self.root / (label + '.zip')
            with zipfile.ZipFile(source, 'w') as output:
                entry = zipfile.ZipInfo(name); entry.external_attr = mode << 16
                output.writestr(entry, 'TEST ONLY')
            destination = self.root / label
            with self.subTest(label=label), self.assertRaises((PublicationError, SdkBuildError)):
                extract(source, destination)
            self.assertFalse(destination.exists())

    def test_project_imports_cached_outputs_and_source_dependencies_are_rejected(self):
        output = self.root / 'extracted'; extract(self.project_zip(), output)
        (output / 'build').mkdir()
        with self.assertRaises(SdkBuildError): audit_project(output)
        (output / 'build').rmdir()
        java = output / 'src/main/java/example/Test.java'
        java.write_text('package example; import org.cha0scollective.wwpg.bridge.WorldBridge;', encoding='utf-8')
        with self.assertRaises(SdkBuildError): audit_project(output)
        java.write_text('package example;', encoding='utf-8')
        (output / 'build.gradle').write_text('dependencies { compileOnly project(":wwpg") }', encoding='utf-8')
        with self.assertRaises(SdkBuildError): audit_project(output)


if __name__ == '__main__': unittest.main()
