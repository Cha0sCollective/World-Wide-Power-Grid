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
        self.assertEqual(len(jobs),286)
        self.assertEqual({(j['platform'],j['backend'],j['profile']) for j in jobs if 'backend' in j},
                         {(p,b,f) for p in spec['platforms'] for b in spec['backends'] for f in spec['headless_profiles']})
        self.assertEqual({j['loader'] for j in jobs if 'loader' in j},{'21.1.262'})
        self.assertTrue(all(j['lane']=='headless' for j in jobs))

    def test_development_keeps_every_baseline_job_and_all_cee_groups(self):
        baseline={j['id']:j for j in suites.plan('full-headless')}
        jobs={j['id']:j for j in suites.plan('full-headless',spec=campaign.campaign_catalog())}
        self.assertTrue(baseline.keys()<=jobs.keys())
        self.assertTrue(all({k:v for k,v in jobs[key].items() if k!='reference_control'}==job for key,job in baseline.items()))
        controlled=[j for j in jobs.values() if 'reference_control' in j]
        self.assertEqual(len(controlled),8)
        self.assertTrue(all(j['operation']=='reference' and j['reference_control']['id']=='cee120-residual-1e-16' for j in controlled))
        names={name for job in jobs.values() for name in job.get('namespaces',[])}
        self.assertTrue({'wwpg_cee_readiness','wwpg_cee_migration','wwpg_cee_lifecycle','wwpg_cee_ownership',
                         'wwpg_cee_adapters','wwpg_cee_baseline','wwpg_cee_baseline_train','wwpg_cee_fans',
                         'wwpg_cee_electrocution','wwpg_cee_transformer_reference','wwpg_transformer_logging'}<=names)
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

    def test_current_frozen_pair_and_source_are_required(self):
        inputs={'version':campaign.VERSION,'source_sha':'a'*40,
                'artifacts':{'runtime':{'sha256':'b'*64},'gametest':{'sha256':'c'*64}}}
        spec=campaign.campaign_catalog(inputs)
        self.assertEqual(spec['campaign']['player_sha256'],'b'*64)
        self.assertEqual(spec['campaign']['gametest_sha256'],'c'*64)
        for field in ('version','source','companion','digest'):
            bad=copy.deepcopy(inputs)
            if field=='version':bad['version']='0.2.10-dev.cee120.5'
            if field=='source':bad['source_sha']='unknown'
            if field=='companion':del bad['artifacts']['gametest']
            if field=='digest':bad['artifacts']['runtime']['sha256']='unknown'
            with self.subTest(field=field),self.assertRaisesRegex(ValueError,'Campaign requires'):
                campaign.campaign_catalog(bad)

    def test_no_duplicate_coverage_and_no_optional_jars_in_core_cases(self):
        spec=campaign.campaign_catalog();jobs=suites.plan('full-headless',spec=spec)
        coverage=[]
        for job in jobs:
            edition=suites.world_edition(job) if job.get('world') else None
            for namespace in job.get('namespaces',[]):
                coverage.append((job['platform'],job['backend'],namespace,job['id'].rsplit('/',1)[-1],edition))
            self.assertEqual(bool(job['optional_mods']),job['profile']=='ADDONS')
            if job.get('group','').startswith('cee-'):
                self.assertEqual(job['profile'],'BASE')
                self.assertEqual(job['optional_mods'],[])
        self.assertEqual(len(coverage),len(set(coverage)))
        self.assertEqual(sum(j.get('operation')=='prepare' for j in jobs),2)
        self.assertEqual(sum(j.get('operation')=='reference' for j in jobs),8)
        self.assertEqual(sum(j.get('operation')=='export' for j in jobs),12)
        for name in ('wwpg_cee_electrocution','wwpg_cee_transformer_reference','wwpg_transformer_logging'):
            self.assertEqual(sum(name in j.get('namespaces',[]) for j in jobs),4)

    def test_historical_electrical_coverage_survives_installation_deduplication(self):
        old=suites.plan('full-headless',spec=campaign.historical_campaign_catalog())
        new=suites.plan('full-headless',spec=campaign.campaign_catalog())
        def coverage(job,namespace):
            world=job.get('group','').removesuffix('-extracted') in ('yard','workshop','workshop-pinout')
            return (job['platform'],job['backend'],namespace,job['id'].rsplit('/',1)[-1],
                    suites.world_edition(job) if world else None)
        required={coverage(j,n) for j in old for n in j.get('namespaces',[])}
        observed={coverage(j,n) for j in new for n in j.get('namespaces',[])}
        self.assertTrue(required <= observed,required-observed)

    def test_historical_selection_is_preserved_and_not_activated_by_default(self):
        historical=campaign.historical_campaign_catalog()
        jobs=suites.plan('full-headless',spec=historical)
        self.assertEqual(len(jobs),398)
        self.assertEqual(historical['campaign']['version'],'0.2.10-dev.cee120.5')
        self.assertEqual(historical['campaign']['player_sha256'],
                         '860a150384ed60ad8a51143afe4d84ac24323f9e55ec22d22a2f9d90c0e71308')
        self.assertEqual({j['profile'] for j in jobs if 'profile' in j},{'BASE','PINOUT'})
        self.assertEqual(campaign.campaign_catalog()['campaign']['version'],campaign.VERSION)

    def test_frozen_campaign_round_trip_and_tamper_rejection(self):
        # Exercise the real decorate/activate path without starting Java or fetching jars.
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);frozen=root/'canonical/frozen';frozen.mkdir(parents=True)
            (root/'tools/legacy').mkdir(parents=True)
            for name in ('test-suites.json','legacy/test-suites-cee120-pre-addons.json'):
                (root/'tools'/name).write_bytes((campaign.ROOT/'tools'/name).read_bytes())
            contract=root/'build/cee12-contract/electroenergetics-1.21.1-1.2.0-132.jar'
            contract.parent.mkdir(parents=True);contract.write_bytes(b'fixture CEE')
            artifact=frozen/'fixture.jar';artifact.write_bytes(b'frozen fixture')
            artifacts={role:dict(suites.descriptor(artifact),path=artifact.name) for role in (*suites.ROLES,'gametest')}
            original=dict(schema=2,policy='separated-v2',source_sha='a'*40,version=campaign.VERSION,artifacts=artifacts)
            producer=frozen/'inputs.json';suites.write(producer,original);before=producer.read_bytes()
            with patch.object(campaign,'ROOT',root),patch.object(campaign,'CEE_SHA256',suites.sha(contract)):
                path=campaign.decorate_canonical(root/'canonical')
                manifest=suites.read(path);spec=suites.read(frozen/'catalog-cee120.json')
                self.assertEqual(spec['campaign']['player_sha256'],suites.sha(artifact))
                self.assertEqual(spec['campaign']['gametest_sha256'],suites.sha(artifact))
                self.assertEqual(producer.read_bytes(),before)
                environment=root/'environment.txt'
                values=campaign.activate(frozen,environment)
                self.assertEqual(Path(values['WWPG_CAMPAIGN_INPUTS']),path)
                self.assertIn('ORG_GRADLE_PROJECT_testExpectedCeeSha256='+suites.sha(contract),environment.read_text())
                with self.assertRaisesRegex(ValueError,'Preserve the existing'):
                    campaign.decorate_canonical(root/'canonical')
                for kind in ('catalog','player','companion','cee','producer','identity','recommendation'):
                    with self.subTest(kind=kind):
                        if kind=='catalog':
                            changed=copy.deepcopy(spec);changed['groups']['cee-fans']['namespaces']=[]
                            (frozen/'catalog-cee120.json').write_text(json.dumps(changed))
                        elif kind in ('player','companion'):
                            changed=copy.deepcopy(manifest)
                            # A self-consistent manifest with a substituted pair must still
                            # disagree with the separately frozen campaign catalog.
                            alternate=frozen/'alternate.jar';alternate.write_bytes(b'another fixture')
                            changed['artifacts']['runtime' if kind=='player' else 'gametest']=dict(suites.descriptor(alternate),path=alternate.name)
                            path.write_text(json.dumps(changed))
                        elif kind in ('identity','recommendation'):
                            changed=copy.deepcopy(manifest)
                            if kind=='identity':changed['campaign']['revision']='another revision'
                            else:changed['recommended_runtime']['electroenergetics']='1.21.1-1.1.4'
                            path.write_text(json.dumps(changed))
                        elif kind=='cee':(frozen/contract.name).write_bytes(b'changed CEE')
                        else:producer.write_bytes(b'changed producer')
                        with self.assertRaises(ValueError):campaign.activate(frozen)
                        path.write_text(json.dumps(manifest));(frozen/'catalog-cee120.json').write_text(json.dumps(spec))
                        (frozen/contract.name).write_bytes(contract.read_bytes());producer.write_bytes(before)

    def test_consumer_validates_campaign_before_preparation_work(self):
        with patch.object(campaign,'activate',side_effect=ValueError('changed campaign')), \
             patch('ci_prepare.prepare') as prepare:
            with self.assertRaisesRegex(ValueError,'changed campaign'):
                campaign.prepare(Path('unused'),Path('wrong/cee120-inputs.json'))
            prepare.assert_not_called()
        with patch.object(campaign,'activate',return_value={'WWPG_CAMPAIGN_INPUTS':str(Path('wrong/cee120-inputs.json').resolve())}), \
             patch('ci_prepare.prepare') as prepare:
            with self.assertRaisesRegex(ValueError,'activated cee120-inputs.json'):
                campaign.prepare(Path('unused'),Path('wrong/inputs.json'))
            prepare.assert_not_called()


if __name__=='__main__':unittest.main()
