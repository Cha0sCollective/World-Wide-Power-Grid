import unittest,tempfile,zipfile,subprocess,os
from pathlib import Path
from sdk_documentation import bundle_markdown
from package_api_release import package_example

class Navigation(unittest.TestCase):
    SHA='a'*40
    def test_existing_files_remain_relative_and_missing_files_are_pinned(self):
        text='[limits](API_LIMITS.md#constructor-inputs) [source](../examples/adjustable-load/README.md) [records](../release/0.2.2/verification.json)'
        output=bundle_markdown(text,'docs/CREATE_ADDON.md',{'docs/API_LIMITS.md'},'0.2.2',self.SHA)
        self.assertIn('(API_LIMITS.md#constructor-inputs)',output)
        self.assertIn('/blob/'+self.SHA+'/examples/adjustable-load/README.md',output)
        self.assertIn('/releases/download/0.2.2/wwpg-0.2.2-verification.zip',output)
        self.assertNotIn('/blob/main/',output)
    def test_nested_project_readme_does_not_assume_sdk_layout(self):
        output=bundle_markdown('[API](../../docs/EXTENSION_API.md#exposing-a-device)','examples/adjustable-load/README.md',set(),'0.2.2',self.SHA)
        self.assertIn('/blob/'+self.SHA+'/docs/EXTENSION_API.md#exposing-a-device',output)
    def test_main_links_are_pinned_but_external_and_fragment_links_are_preserved(self):
        output=bundle_markdown('[a](https://github.com/Cha0sCollective/World-Wide-Power-Grid/blob/main/docs/INSTALL.md) [b](#here) [c](https://example.org/)','README.md',set(),'0.2.2',self.SHA)
        self.assertIn('/blob/'+self.SHA+'/docs/INSTALL.md',output)
        self.assertIn('(#here)',output);self.assertIn('(https://example.org/)',output)
    def test_unpinned_or_escaping_source_is_rejected(self):
        with self.assertRaises(ValueError):bundle_markdown('[x](x.md)','README.md',set(),'0.2.2','main')
        with self.assertRaises(ValueError):bundle_markdown('[x](../../private.md)','README.md',set(),'0.2.2',self.SHA)
    def test_real_source_archive_preserves_unix_wrapper_mode(self):
        root=Path(__file__).resolve().parents[1]
        (root/'build').mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=root/'build') as directory:
            out=Path(directory)/'starter.zip';package_example(root/'examples/adjustable-load',root,out)
            with zipfile.ZipFile(out) as z:
                self.assertEqual(z.getinfo('gradlew').create_system,3)
                self.assertEqual(z.getinfo('gradlew').external_attr>>16,0o100755)
                self.assertEqual(z.getinfo('build.gradle').external_attr>>16,0o100644)
                self.assertNotIn('/blob/main/',z.read('README.md').decode())
            if os.name!='nt':
                extracted=Path(directory)/'with spaces';extracted.mkdir()
                subprocess.run(['unzip','-q',str(out),'-d',str(extracted)],check=True)
                self.assertTrue(os.access(extracted/'gradlew',os.X_OK))

if __name__=='__main__':unittest.main()
