"""Synthetic publication-gate tests. These are never gameplay evidence."""
import copy
import gzip
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

import ci_reference_continuation as continuation
from collect_api_ci_evidence import EvidenceError, metadata, opaque_original_json
import package_api_release as gate
import test_api_ci_evidence


class ContinuationChecks(unittest.TestCase):
    def test_selected_java_retains_failed_overall_outcome(self):
        run,jobs,index=test_api_ci_evidence.EvidenceChecks.metadata_fixture(self)
        run['conclusion']='failure'
        for job in jobs['jobs']:
            if ', NATIVE,' in job['name']: job['conclusion']='failure'
        self.assertEqual(len(metadata(run,jobs,index,backend='JAVA')),4)
        self.assertEqual(run['conclusion'],'failure')
        with self.assertRaises(EvidenceError): metadata(run,jobs,index)
        next(j for j in jobs['jobs'] if ', JAVA,' in j['name'])['conclusion']='failure'
        with self.assertRaises(EvidenceError): metadata(run,jobs,index,backend='JAVA')

    def setUp(self):
        scratch=Path(__file__).resolve().parents[1]/'build/reference-continuation-tests'
        scratch.mkdir(parents=True,exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(dir=scratch); self.root=Path(self.temp.name)
        run=dict(id=123,run_attempt=1,head_sha='a'*40,status='completed',conclusion='failure',
                 path='.github/workflows/build.yml',html_url=f'https://github.com/{continuation.REPO}/actions/runs/123')
        job=dict(id=456,run_id=123,run_attempt=1,name='build (ubuntu-24.04, NATIVE, PINOUT)',status='completed',conclusion='failure',
                 steps=[dict(name='Three consecutive cold world restarts',number=10,conclusion='success'),
                        dict(name=continuation.REFERENCE_STEP,number=11,conclusion='failure')])
        artifact=dict(id=789,name='wwpg-ubuntu-24.04-NATIVE-PINOUT',expired=False,workflow_run=dict(id=123,head_sha='a'*40))
        archive=self.root/'original.zip'
        members={'build/ci-source.json':json.dumps(dict(source_sha='a'*40,run_id=123,run_attempt=1)),
                 'run/reference/reference-packaged-TEST_ONLY.json':json.dumps(dict(completed=False)),
                 'run/reference/logs/latest.log':'TEST_ONLY PG native solve rejected: info=5, rows=6, nnz=11',
                 'run/logs/latest.log':'TEST_ONLY retained passing prefix'}
        members.update({path:'TEST_ONLY '+role for role,path in continuation.ROLES.items()})
        with zipfile.ZipFile(archive,'w') as z:
            for name,value in members.items(): z.writestr(name,value)
        artifact['digest']='sha256:'+continuation.sha(archive)
        for key,value in [('run',run),('job',job),('artifact',artifact)]:
            (self.root/f'{key}.json').write_text(json.dumps(value),encoding='utf-8')
        for name,value in members.items():
            if name.startswith('run/reference/') or name=='build/ci-source.json': continue
            p=self.root/name; p.parent.mkdir(parents=True,exist_ok=True); p.write_text(value,encoding='utf-8')
        self.receipt=dict(schema=1,scope='reference_teardown_only',platform='ubuntu-24.04',profile='PINOUT',
            original_archive=continuation.desc(archive,self.root),
            artifacts={role:continuation.desc(self.root/path,self.root) for role,path in continuation.ROLES.items()},
            files=[dict(original_member='run/logs/latest.log',file=continuation.desc(self.root/'run/logs/latest.log',self.root))],
            **{k:continuation.desc(self.root/f'{k}.json',self.root) for k in ('run','job','artifact')})

    def tearDown(self): self.temp.cleanup()

    def test_retains_actual_source_failure_and_prefix(self):
        v=continuation.validate(self.receipt,self.root)
        self.assertEqual((v['source_sha'],v['run_id'],v['job_id']),('a'*40,123,456))

    def test_changed_prefix_and_runtime_cannot_reuse(self):
        for member in ['run/logs/latest.log',continuation.ROLES['runtime']]:
            p=self.root/member; before=p.read_bytes(); p.write_bytes(before+b'CHANGED')
            with self.assertRaises(ValueError): continuation.validate(self.receipt,self.root)
            p.write_bytes(before)

    def test_omitted_retained_file_or_role_rejected(self):
        for key in ['files','artifacts']:
            r=copy.deepcopy(self.receipt); r[key]=[] if key=='files' else {}
            with self.assertRaises(ValueError): continuation.validate(r,self.root)

    def test_other_failure_or_failed_prefix_rejected(self):
        run=json.loads((self.root/'run.json').read_text()); job=json.loads((self.root/'job.json').read_text())
        artifact=json.loads((self.root/'artifact.json').read_text())
        for change in ['other_failure','prefix_failure','wrong_attempt']:
            j=copy.deepcopy(job)
            if change=='other_failure': j['steps'][-1]['name']='Actual runtime regression failure'
            elif change=='prefix_failure': j['steps'][0]['conclusion']='failure'
            else: j['run_attempt']=2
            with self.assertRaises(ValueError): continuation.origin(run,j,artifact,'ubuntu-24.04','PINOUT')

    def test_substituted_github_archive_rejected(self):
        p=self.root/'original.zip'; p.write_bytes(p.read_bytes()+b'CHANGED')
        r=copy.deepcopy(self.receipt); r['original_archive']=continuation.desc(p,self.root)
        with self.assertRaises(ValueError): continuation.validate(r,self.root)

    def test_original_checkout_relative_receipt_is_lossless_opaque_provenance(self):
        p=self.root/'checkout-relative-receipt.json'
        p.write_text(json.dumps(dict(TEST_ONLY=True,original_file=dict(
            path='run/only-in-the-original-ci-checkout.json',sha256='a'*64,bytes=10))),encoding='utf-8')
        with self.assertRaises(gate.PublicationError):
            gate.collect_locked_evidence(continuation.desc(p,self.root),self.root)
        record=opaque_original_json(p,self.root)
        self.assertEqual(gzip.decompress((self.root/record['path']).read_bytes()),p.read_bytes())
        self.assertEqual(len(gate.collect_locked_evidence(record,self.root)),1)


if __name__=='__main__': unittest.main()
