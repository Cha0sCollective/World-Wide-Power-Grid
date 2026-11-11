"""Focused tests for the narrow version/documentation acceptance comparison."""
import os,json,copy,hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile
import warnings
from release_acceptance_reuse import runtime_equivalent, api_equivalent, world_version_equivalent, companion_equivalent

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
    def test_duplicate_archive_entries_are_rejected_on_either_side(self):
        for kind in ('runtime', 'api', 'companion'):
            for side in ('old', 'new'):
                for differing in (False, True):
                    with self.subTest(kind=kind, side=side, differing=differing):
                        if kind == 'runtime':
                            old=self.runtime('old.jar','1');new=self.runtime('new.jar','2')
                            check=lambda:runtime_equivalent(old,new,'1','2')
                            member='native/pg.so'
                        elif kind == 'companion':
                            old=self.companion('old.jar','1');new=self.companion('new.jar','2')
                            check=lambda:companion_equivalent(old,new,'1','2')
                            member='fixture.nbt'
                        else:
                            old=self.jar('old.jar',{'resource.txt':b'original'})
                            new=self.jar('new.jar',{'resource.txt':b'original'})
                            check=lambda:api_equivalent(old,new)
                            member='resource.txt'
                        target=old if side=='old' else new
                        with zipfile.ZipFile(target) as z:payload=z.read(member)
                        # Put the expected entry last: filename reads can hide the first one.
                        with warnings.catch_warnings():
                            warnings.simplefilter('ignore',UserWarning)
                            with zipfile.ZipFile(target,'a') as z:
                                z.writestr(member,b'altered' if differing else payload)
                                z.writestr(member,payload)
                        with self.assertRaisesRegex(ValueError,'[Dd]uplicate'):
                            check()
    def companion(self,name,version,extra=None):
        files={'META-INF/neoforge.mods.toml':f'modId="wwpg_tests"\nversion="{version}"\nmodId="wwpg"\nversionRange="[{version}]"',
            'Tests.class':b'actual tests','fixture.nbt':b'actual saved fixture'}
        files.update(extra or {});return self.jar(name,files)
    def test_companion_promotion_retains_tests_and_fixtures(self):
        companion_equivalent(self.companion('old-tests.jar','dev'),self.companion('new-tests.jar','stable'),'dev','stable')
    def test_changed_test_fixture_or_dependency_is_rejected(self):
        old=self.companion('old-tests.jar','dev')
        for i,change in enumerate([{'Tests.class':b'changed tests'},{'fixture.nbt':b'changed fixture'},
                {'META-INF/neoforge.mods.toml':'modId="wwpg_tests"\nversion="stable"\nmodId="wwpg"\nversionRange="[dev]"'},
                {'new.class':b'added test'}]):
            with self.subTest(i=i),self.assertRaises(ValueError):
                companion_equivalent(old,self.companion(str(i)+'-tests.jar','stable',change),'dev','stable')
    def test_companion_requires_exact_original_dependency_and_identity(self):
        for i,metadata in enumerate(['modId="other"\nversion="dev"\nmodId="wwpg"\nversionRange="[dev]"',
                'modId="wwpg_tests"\nversion="dev"\nmodId="wwpg"\nversionRange="[0,)"']):
            with self.subTest(i=i),self.assertRaises(ValueError):
                companion_equivalent(self.companion(str(i)+'-bad.jar','dev',{'META-INF/neoforge.mods.toml':metadata}),
                    self.companion(str(i)+'-good.jar','stable'),'dev','stable')
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

class WorldVersionReuseChecks(unittest.TestCase):
    """Synthetic ZIPs test promotion guards; they are not gameplay evidence."""
    def setUp(self):
        scratch=Path(__file__).resolve().parents[1]/'build/reuse-tests';scratch.mkdir(parents=True,exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(dir=scratch);self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name)
        self.old_runtime=dict(path='old.jar',sha256='a'*64,bytes=10)
        self.new_runtime=dict(path='new.jar',sha256='b'*64,bytes=11)
        self.header='TEST_ONLY/WWPG_MANIFEST.json';self.readme='TEST_ONLY/WWPG_README.txt'
        self.native=b'TEST_ONLY native saved state'
        self.meta=dict(target='beta',profile='PINOUT',jar=dict(name='old.jar',sha256='a'*64,bytes=10),
            files={'level.dat':dict(sha256=hashlib.sha256(self.native).hexdigest(),bytes=len(self.native))},
            packaged_yard_proof=dict(runtime='original observation',passed=True))
        self.original={self.header:json.dumps(self.meta).encode(),self.readme:b'Install beta; preserve charge.', 'TEST_ONLY/level.dat':self.native}
        updated=dict(self.meta,target='stable',jar=dict(name='new.jar',sha256='b'*64,bytes=11))
        self.promoted=dict(self.original,**{self.header:json.dumps(updated).encode(),self.readme:b'Install stable; preserve charge.'})
    def archive(self,name,files):
        path=self.root/name
        with zipfile.ZipFile(path,'w') as z:
            for key,data in files.items():z.writestr(key,data)
        return path
    def check(self,files=None):
        world_version_equivalent(self.archive('old.zip',self.original),self.archive('new.zip',files or self.promoted),
            'beta','stable',self.old_runtime,self.new_runtime)
    def test_labels_only_retain_every_native_byte_and_original_proof(self):self.check()
    def test_changed_native_save_is_rejected(self):
        changed=dict(self.promoted);changed['TEST_ONLY/level.dat']=b'different stored charge'
        with self.assertRaisesRegex(ValueError,'native save'):self.check(changed)
    def test_changed_inventory_and_original_observations_are_rejected(self):
        for field in ('files','packaged_yard_proof','profile'):
            with self.subTest(field=field):
                changed=dict(self.promoted);meta=json.loads(changed[self.header]);meta[field]='changed';changed[self.header]=json.dumps(meta).encode()
                with self.assertRaisesRegex(ValueError,'metadata changed'):self.check(changed)
    def test_new_operating_instructions_are_rejected(self):
        changed=dict(self.promoted);changed[self.readme]=b'Install stable; change solver settings.'
        with self.assertRaisesRegex(ValueError,'instructions changed'):self.check(changed)
    def test_added_world_entry_is_rejected(self):
        changed=dict(self.promoted);changed['TEST_ONLY/new.dat']=b'new'
        with self.assertRaisesRegex(ValueError,'entries differ'):self.check(changed)
    def test_duplicate_world_entries_are_rejected_on_either_side(self):
        for side in ('old','new'):
            for member in (self.header,self.readme,'TEST_ONLY/level.dat'):
                with self.subTest(side=side,member=member):
                    old=self.archive('old.zip',self.original);new=self.archive('new.zip',self.promoted)
                    path=old if side=='old' else new
                    with warnings.catch_warnings():
                        warnings.simplefilter('ignore',UserWarning)
                        with zipfile.ZipFile(path,'a') as z:z.writestr(member,z.read(member))
                    with self.assertRaisesRegex(ValueError,'Duplicate'):
                        world_version_equivalent(old,new,'beta','stable',self.old_runtime,self.new_runtime)
    def test_original_runtime_must_match(self):
        self.old_runtime['sha256']='c'*64
        with self.assertRaisesRegex(ValueError,'Original world runtime'):self.check()
    def test_promotion_must_bind_both_cohorts_to_original_source(self):
        import suite_evidence as evidence
        from types import SimpleNamespace
        reuse=dict(schema=1,scope='runtime_version_only',version='beta',source_sha='a'*40,runtime=self.old_runtime,
            worlds={field:{} for field in evidence.WORLD_KEYS.values()})
        manifest=dict(world_metadata_reuse=reuse)
        with self.assertRaisesRegex(ValueError,'original runtime acceptance'):
            evidence.accepted_world_hashes(manifest,self.root,SimpleNamespace(),[dict(reuse=dict(version='beta',source_sha='b'*40,runtime=self.old_runtime))])
    def test_missing_original_archive_cannot_enable_promotion(self):
        import suite_evidence as evidence
        with self.assertRaisesRegex(ValueError,'all original archives'):
            evidence.accepted_world_hashes(dict(world_metadata_reuse=dict(schema=1,scope='runtime_version_only',worlds={})),self.root,None,[])


if __name__=='__main__':unittest.main()
