"""Focused runner checks. Synthetic records are never gameplay evidence."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import loader_compatibility as loader
import test_suites as suites


class PlanningChecks(unittest.TestCase):
    def test_versions_are_bounded_literal_21_1_versions(self):
        self.assertEqual(loader.versions('21.1.252, 21.1.262'),['21.1.252','21.1.262'])
        for value in ('','21.1.230','21.2.262','21.1.262;echo bad','21.1.262\n--help',
                      '21.1.262,21.1.262',','.join('21.1.'+str(n) for n in range(260,265))):
            with self.subTest(value=value),self.assertRaises(ValueError):loader.versions(value)

    def test_eight_isolated_environments_per_version(self):
        jobs=loader.matrix(['21.1.252','21.1.262'])
        self.assertEqual(len(jobs),16)
        self.assertEqual(len({tuple(j.values()) for j in jobs}),16)
        self.assertEqual({j['installation'] for j in jobs},{'BASE','ADDONS'})

    def test_base_never_loads_optional_or_example_mods(self):
        for backend in ('NATIVE','JAVA'):
            jobs=loader.plan('21.1.262',backend,'BASE','windows-latest')
            self.assertTrue(all(j['profile']=='BASE' and not j['addon'] for j in jobs))
            names={n for j in jobs for n in j['namespaces']}
            self.assertFalse({'wwpg_api','wwpg_pinout','wwpg_loader_addons'} & names)
            self.assertIn('wwpg_trains',names)

    def test_addons_have_real_optional_fixtures_and_ordered_restarts(self):
        jobs=loader.plan('21.1.262','NATIVE','ADDONS','windows-latest')
        self.assertTrue(all(j['profile']=='PINOUT' and j['addon'] for j in jobs))
        names={n for j in jobs for n in j['namespaces']}
        self.assertTrue({'wwpg_loader_addons','wwpg_pinout','wwpg_api','wwpg_native_guard'} <= names)
        for group,phases in [('regression',['SETUP','VERIFY','VERIFY','VERIFY']),
                             ('api',['SETUP','VERIFY','VERIFY','VERIFY']),('pending',['SETUP','ARM','VERIFY'])]:
            selected=[j for j in jobs if j['group']==group]
            self.assertEqual([j['phase'] for j in selected],phases)
            if group=='api':self.assertTrue(all('wwpg_api_ownership' not in j['namespaces'] for j in selected[1:]))
        self.assertTrue(all('operation' not in j and j['lane']=='headless' for j in jobs))
        self.assertFalse(any(j['group'] in ('yard','workshop','example','showroom') for j in jobs))

    def test_unverified_loaders_do_not_enter_release_coverage(self):
        self.assertNotIn('21.1.262',suites.catalog()['loaders'])
        with self.assertRaises(ValueError):suites.plan('full-headless',loaders=['21.1.262'])
        self.assertTrue(all(j.get('loader')!='21.1.262' for j in suites.plan('full-headless')))

    def test_workflow_is_manual_parallel_and_keeps_failed_jobs(self):
        text=(suites.ROOT/'.github/workflows/loader-compatibility.yml').read_text()
        self.assertIn('workflow_dispatch:',text);self.assertIn('max-parallel: 8',text);self.assertIn('fail-fast: false',text)
        for trigger in ('push','pull_request','release','schedule','workflow_run'):
            self.assertNotIn('  '+trigger+':',text)
        self.assertNotIn('run full-headless',text);self.assertNotIn('run graphical',text)
        self.assertIn("NEO_VERSIONS: ${{ inputs.neo_versions }}",text)
        self.assertNotIn('--versions ${{ inputs.',text)

    def test_optional_inventory_matches_existing_pins(self):
        items={i['role']:i for i in suites.catalog()['loader_compatibility']['artifacts']}
        self.assertEqual(set(items),{'computercraft','pinout','gauged','power_components'})
        for i in suites.read(suites.ROOT/'release/optional-artifacts.json')['artifacts']:
            self.assertEqual(items[i['role']]['sha256'],i['sha256'])
        self.assertIn('wwpg_loader_addons',suites.audit()['classified'])


class EvidenceChecks(unittest.TestCase):
    def setUp(self):
        (suites.ROOT/'build').mkdir(exist_ok=True)
        self.tmp=tempfile.TemporaryDirectory(dir=suites.ROOT/'build');self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        self.artifacts={}
        ids={i['role']:i['mod_id'] for i in suites.catalog()['loader_compatibility']['artifacts']}
        for role in (*suites.ROLES,'gametest','reference',*ids):
            p=self.root/(role+'.jar');p.write_text('TEST_ONLY '+role)
            self.artifacts[role]=dict(suites.descriptor(p),mod_id=ids.get(role,''))
        self.inputs=self.root/'inputs.json'
        suites.write(self.inputs,dict(schema=2,policy='separated-v2',source_sha='a'*40,artifacts=self.artifacts,
                     loader_compatibility=dict(policy=loader.POLICY,artifacts=list(ids))))
        self.hashes={r:d['sha256'] for r,d in self.artifacts.items()}
        # Synthetic artifact pins stay local to this test; production validation
        # still requires the real catalog's pinned optional jars.
        spec=copy.deepcopy(suites.catalog())
        for item in spec['loader_compatibility']['artifacts']:item['sha256']=self.hashes[item['role']]
        self.catalog=self.root/'catalog.json';suites.write(self.catalog,spec)
        self.catalog_patch=patch.object(suites,'CATALOG',self.catalog);self.catalog_patch.start();self.addCleanup(self.catalog_patch.stop)
        inputs=suites.read(self.inputs);inputs['catalog_sha256']=suites.sha(self.catalog);self.inputs.write_text(json.dumps(inputs))

    def fragment(self,environment):
        jobs=loader.plan(environment['loader'],environment['backend'],environment['installation'],environment['os'])
        folder=self.root/'reports'/('-'.join(environment.values()));folder.mkdir(parents=True)
        record=dict(schema=2,policy='separated-v2',suite='focused',complete_suite=False,status='passed',not_run=[],
                    source_sha='a'*40,launcher_source_sha='a'*40,artifact_hashes=self.hashes,catalog=suites.descriptor(suites.CATALOG),
                    inputs=suites.descriptor(self.inputs),jobs=[])
        for i,job in enumerate(jobs):
            proof=dict(completed='TEST_ONLY completion',required_tests_passed=1,profile=job['profile'],backend=job['backend'],
                       sha256=self.hashes['runtime'],phase=job['phase'],neoforge=job['loader'],namespaces=','.join(job['namespaces']),
                       test_fixture_artifact={'sha256':self.hashes['gametest']},
                       addon_artifacts={r:{'sha256':self.hashes[r]} for r in ('example','upstream_example')})
            pp=folder/(str(i)+'-proof.json');suites.write(pp,proof)
            log='TEST_ONLY\nGAME TESTS COMPLETE\nAll 1 required tests passed\nAll dimensions are saved\n'
            log+=f'WWPG_TEST_BACKEND_CONFIG: requested={job["backend"]}, configured={job["backend"]}\n'
            if i==0:
                for id in ('computercraft','pinout','gauged_wires','moregrid','wwpg_api_example','wwpg_api_upstream'):
                    log+=f'LOADER_OPTIONAL_VERIFIED: {id} present={str(job["installation"]=="ADDONS").lower()}\n'
            lp=folder/(str(i)+'.log');lp.write_text(log)
            record['jobs'].append(dict(job,status='passed',exit_code=0,proof=suites.descriptor(pp),log=suites.descriptor(lp)))
        path=folder/'result.json';suites.write(path,record)
        return path,record,jobs

    def test_missing_job_wrong_loader_and_failed_shutdown_cannot_pass(self):
        path,record,jobs=self.fragment(loader.matrix(['21.1.262'])[0])
        loader.validate(path,self.inputs,jobs)
        for change in ('missing','loader','shutdown','artifact','source'):
            bad=copy.deepcopy(record)
            if change=='missing':bad['jobs'].pop()
            elif change=='loader':bad['jobs'][0]['loader']='21.1.248'
            elif change=='artifact':bad['artifact_hashes']['runtime']='0'*64
            elif change=='source':bad['source_sha']='b'*40
            else:
                log=self.root/'interrupted.log';log.write_text('TEST_ONLY interrupted before save')
                bad['jobs'][0]['log']=suites.descriptor(log)
            path.write_text(json.dumps(bad))
            with self.subTest(change=change),self.assertRaises(ValueError):loader.validate(path,self.inputs,jobs)

    def test_collector_requires_every_environment_and_retains_failure_summary(self):
        environments=loader.matrix(['21.1.262']);campaign=self.root/'plan.json'
        suites.write(campaign,dict(policy=loader.POLICY,matrix={'include':environments}))
        for e in environments[:-1]:self.fragment(e)
        output=self.root/'missing.json'
        with self.assertRaisesRegex(ValueError,'incomplete'):
            loader.collect(campaign,self.inputs,self.root/'reports',output)
        self.assertEqual(suites.read(output)['status'],'failed');self.assertEqual(len(suites.read(output)['missing']),1)
        self.fragment(environments[-1]);result=loader.collect(campaign,self.inputs,self.root/'reports',self.root/'passed.json')
        self.assertEqual(result['status'],'passed');self.assertFalse(result['full_acceptance'])

    def test_failure_and_duplicate_cannot_be_hidden_by_other_passes(self):
        environments=loader.matrix(['21.1.262']);campaign=self.root/'plan.json'
        suites.write(campaign,dict(policy=loader.POLICY,matrix={'include':environments}))
        for e in environments:self.fragment(e)
        source=next((self.root/'reports').glob('*/result.json')); duplicate=self.root/'reports/duplicate';duplicate.mkdir()
        (duplicate/'result.json').write_bytes(source.read_bytes())
        with self.assertRaisesRegex(ValueError,'duplicate'):
            loader.collect(campaign,self.inputs,self.root/'reports',self.root/'duplicate-summary.json')


if __name__=='__main__':unittest.main()
