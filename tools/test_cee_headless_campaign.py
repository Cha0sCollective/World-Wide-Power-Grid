"""Campaign selection and identity checks; starts no Gradle or Minecraft."""
import copy
import json
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

import cee_headless_campaign as campaign
import test_suites as suites
import suite_evidence as evidence


class CeeCampaignChecks(unittest.TestCase):
    def test_reference_evidence_requires_the_actual_control_and_frozen_cee(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            control=campaign.campaign_catalog()['reference_control']
            base=dict(id='test-reference',lane='headless',operation='reference',reference='upstream',
                      backend='NATIVE',profile='BASE',platform='windows-latest',loader='21.1.262',reference_control=control)
            log='GAME TESTS COMPLETE\nAll 1 required tests passed\nAll dimensions are saved\nWWPG_TEST_BACKEND_CONFIG: requested=NATIVE, configured=NATIVE\n'
            log+='WWPG_RUNTIME_INPUTS_VERIFIED: '+json.dumps({'electroenergetics':{'sha256':campaign.CEE_SHA256}})+'\n'
            for number,case in enumerate(('valid','missing-marker','missing-argument','wrong-cee','wrong-mode','compatibility')):
                with self.subTest(case=case):
                    job=copy.deepcopy(base)
                    if case=='compatibility':job['reference']='compatibility'
                    text=log+('CEE_REFERENCE_RESIDUAL threshold=1.0E-16\n' if case not in ('missing-marker','compatibility') else '')
                    log_path=root/(str(number)+'.log');log_path.write_text(text)
                    proof_path=root/(str(number)+'.json')
                    suites.write(proof_path,dict(completed=True,required_tests_passed=1,backend='NATIVE',profile='BASE',neoforge='21.1.262',
                        upstream=case not in ('wrong-mode','compatibility'),sha256='runtime' if case=='compatibility' else 'reference'))
                    actual=dict(job,status='passed',exit_code=0,log=suites.descriptor(log_path),proof=suites.descriptor(proof_path),
                                command=[] if case=='missing-argument' else ['-PtestTightCeeResidual=true'])
                    actual['reference_traces']={}
                    for name in control['trace_cases']:
                        path=root/(str(number)+'-'+name+'.json')
                        suites.write(path,dict(reference_baseline=control['id'],backend='NATIVE',upstream=case!='compatibility'))
                        actual['reference_traces'][name]=suites.descriptor(path)
                    inputs=dict(artifact_hashes=dict(runtime='runtime',reference='reference',cee_runtime='wrong' if case=='wrong-cee' else campaign.CEE_SHA256))
                    if case in ('valid','compatibility'):evidence.validate_job(actual,job,inputs,root)
                    else:
                        with self.assertRaises(ValueError):evidence.validate_job(actual,job,inputs,root)

    def test_explicit_runtime_arguments_do_not_depend_on_environment_key_case(self):
        inputs={'version':campaign.VERSION,'artifacts':{role:{'path':role+'.jar'} for role in (*suites.ROLES,'gametest')}}
        inputs['artifacts']['cee_runtime']={'path':'cee120.jar','sha256':campaign.CEE_SHA256}
        job=suites.plan('focused',['regression'],['NATIVE'],['BASE'])[0]
        args,_=suites.command(job,inputs,Path('scratch'))
        for argument in ('-PceeRuntimeJar=cee120.jar','-PceeDevelopmentJar=cee120.jar',
                         '-PtestExpectedCeeSha256='+campaign.CEE_SHA256):
            self.assertIn(argument,args)
        del inputs['artifacts']['cee_runtime']
        args,_=suites.command(job,inputs,Path('scratch'))
        self.assertFalse(any(arg.startswith('-PceeRuntimeJar=') for arg in args))

    def test_producer_reference_survives_execution_manifest_relocation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary); frozen=root/'frozen'; frozen.mkdir()
            artifact=frozen/'artifact.jar';artifact.write_bytes(b'frozen artifact')
            artifacts={role:dict(suites.descriptor(artifact),path=artifact.name) for role in (*suites.ROLES,'gametest')}
            producer=frozen/'inputs.json'
            original=dict(schema=2,policy='separated-v2',source_sha='a'*40,artifacts=artifacts)
            suites.write(producer,original)
            manifest=frozen/'cee120-inputs.json'
            suites.write(manifest,dict(original,producer_inputs=dict(suites.descriptor(producer),path=producer.name)))
            loaded=suites.frozen_inputs(manifest)
            execution=root/'execution';execution.mkdir()
            suites.write(execution/'inputs.json',loaded)
            relocated=suites.frozen_inputs(execution/'inputs.json')
            self.assertEqual(relocated['producer_inputs']['path'],str(producer.resolve()))
            self.assertEqual(suites.read(producer),original)
            producer.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'producer manifest changed'):
                suites.frozen_inputs(manifest)

    def test_default_recommendation_and_full_suite_remain_unchanged(self):
        original=suites.catalog()
        self.assertEqual(original['recommended_runtime']['electroenergetics'],'1.21.1-1.1.4')
        self.assertEqual(len(suites.plan('full-headless')),258)
        campaign.campaign_catalog()
        self.assertEqual(suites.catalog(),original)

    def test_development_matrix_has_both_platforms_solvers_and_profiles(self):
        spec=campaign.campaign_catalog();jobs=suites.plan('full-headless',spec=spec)
        self.assertEqual(len(jobs),398)
        self.assertEqual({(j['platform'],j['backend'],j['profile']) for j in jobs if 'backend' in j},
                         {(p,b,f) for p in spec['platforms'] for b in spec['backends'] for f in spec['profiles']})
        self.assertEqual({j['loader'] for j in jobs if 'loader' in j},{'21.1.262'})
        self.assertTrue(all(j['lane']=='headless' for j in jobs))

    def test_development_keeps_every_baseline_job_and_all_cee_groups(self):
        historical=suites.read(campaign.ROOT/'tools/legacy/test-suites-cee120-pre-addons.json')
        baseline={j['id']:j for j in suites.plan('full-headless',spec=historical)}
        jobs={j['id']:j for j in suites.plan('full-headless',spec=campaign.campaign_catalog())}
        self.assertTrue(baseline.keys()<=jobs.keys())
        self.assertTrue(all({k:v for k,v in jobs[key].items() if k!='reference_control'}==job for key,job in baseline.items()))
        controlled=[j for j in jobs.values() if 'reference_control' in j]
        self.assertEqual(len(controlled),8)
        self.assertTrue(all(j['operation']=='reference' and j['reference_control']['id']=='cee120-residual-1e-16' for j in controlled))
        names={name for job in jobs.values() for name in job.get('namespaces',[])}
        self.assertTrue({'wwpg_cee_readiness','wwpg_cee_migration','wwpg_cee_lifecycle','wwpg_cee_ownership',
                         'wwpg_cee_adapters','wwpg_cee_baseline','wwpg_cee_baseline_train','wwpg_cee_fans'}<=names)
        self.assertNotIn('wwpg_cee_migration_cold',names)

    def test_reference_precision_is_explicit_and_does_not_change_the_stable_plan(self):
        spec=campaign.campaign_catalog()
        selected=[j for j in suites.plan('full-headless',spec=spec) if j.get('operation')=='reference']
        inputs={'artifacts':{role:{'path':role+'.jar'} for role in (*suites.ROLES,'gametest','reference')}}
        for job in selected:
            args,_=suites.command(job,inputs,Path('scratch'))
            self.assertIn('-PtestTightCeeResidual=true',args)
        self.assertTrue(all('reference_control' not in j for j in suites.plan('full-headless')))

    def test_development_catalog_is_fully_classified(self):
        spec=campaign.campaign_catalog()
        classified={n for cfg in spec['groups'].values() for n in cfg['namespaces']+cfg.get('setup_namespaces',[])}
        classified.update(spec['special_namespaces'])
        current=suites.audit()['classified']
        self.assertTrue(classified <= set(current))
        self.assertIn('wwpg_cee_fans',spec['groups']['cee-fans']['namespaces'])

    def test_another_player_or_version_is_rejected(self):
        inputs={'version':campaign.VERSION,'artifacts':{'runtime':{'sha256':campaign.PLAYER_SHA256}}}
        campaign.assert_player(inputs)
        for field in ('version','hash'):
            bad=copy.deepcopy(inputs)
            if field=='version':bad['version']='another version'
            else:bad['artifacts']['runtime']['sha256']='a'*64
            with self.assertRaisesRegex(ValueError,'exact frozen'):
                campaign.assert_player(bad)


if __name__=='__main__':unittest.main()
