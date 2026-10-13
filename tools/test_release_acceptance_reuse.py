"""Focused tests for the narrow version/documentation acceptance comparison."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile
from release_acceptance_reuse import runtime_equivalent, api_equivalent

class ReuseChecks(unittest.TestCase):
    def setUp(self):
        root=Path(__file__).resolve().parents[1]/'build/reuse-tests';root.mkdir(parents=True,exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(dir=root);self.root=Path(self.temp.name)
    def tearDown(self):self.temp.cleanup()
    def jar(self,name,files):
        path=self.root/name
        with zipfile.ZipFile(path,'w') as z:
            for n,data in files.items():z.writestr(n,data)
        return path
    def runtime(self,name,version,extra=None):
        files={'META-INF/neoforge.mods.toml':'version="'+version+'"\ndependencies="unchanged"','Main.class':b'actual code','native/pg.so':b'actual native binary'}
        files.update(extra or {});return self.jar(name,files)
    def test_only_mod_version_may_change(self):
        runtime_equivalent(self.runtime('old.jar','1'),self.runtime('new.jar','2'),'1','2')
    def test_changed_code_native_binary_or_other_metadata_is_rejected(self):
        old=self.runtime('old.jar','1')
        for i,change in enumerate([{'Main.class':b'new code'},{'native/pg.so':b'new binary'},
            {'META-INF/neoforge.mods.toml':'version="2"\ndependencies="changed"'}]):
            with self.subTest(i=i),self.assertRaises(ValueError):runtime_equivalent(old,self.runtime(str(i)+'.jar','2',change),'1','2')
    def test_added_runtime_entry_is_rejected(self):
        with self.assertRaises(ValueError):runtime_equivalent(self.runtime('old.jar','1'),self.runtime('new.jar','2',{'extra.class':b'code'}),'1','2')
    def compile(self,name,source):
        directory=self.root/name;directory.mkdir();p=directory/'Api.java';p.write_text(source,encoding='utf-8')
        home=os.environ.get('JAVA_HOME');javac=str(Path(home)/'bin/javac') if home else 'javac'
        if os.name=='nt' and home:javac+='.exe'
        subprocess.run([javac,'--release','21',str(p)],check=True,capture_output=True)
        return self.jar(name+'.jar',{'Api.class':(directory/'Api.class').read_bytes()})
    def test_documentation_line_shift_is_allowed(self):
        source='public class Api { public int value(){ return helper(); } private int helper(){ return 7; } }'
        api_equivalent(self.compile('old',source),self.compile('new','\n\n\n'+source))
    def test_private_implementation_change_is_rejected(self):
        source='public class Api { public int value(){ return helper(); } private int helper(){ return 7; } }'
        with self.assertRaises(ValueError):api_equivalent(self.compile('old',source),self.compile('new',source.replace('return 7','return 8')))

if __name__=='__main__':unittest.main()
