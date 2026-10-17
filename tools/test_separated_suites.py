"""Focused selection/evidence controls. Never starts Gradle or Minecraft."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import test_suites as suites
import suite_evidence as evidence
from graphical_suite import observed, validate_observation


class SelectionChecks(unittest.TestCase):
    def test_every_namespace_is_classified(self):
        self.assertGreaterEqual(suites.audit()['namespaces'],35)

    def test_new_namespace_cannot_silently_disappear(self):
        with tempfile.TemporaryDirectory(dir=suites.ROOT/'build') as d:
            root=Path(d);source=root/'src/main/java/Unknown.java';source.parent.mkdir(parents=True)
            source.write_text('@GameTestHolder("wwpg_new_regression")')
            with self.assertRaisesRegex(ValueError,'Unclassified'):suites.audit(root)

    def test_full_headless_has_no_client_launches(self):
        jobs=suites.plan('full-headless')
        self.assertTrue(jobs);self.assertEqual({j['lane'] for j in jobs},{'headless'})
        self.assertEqual(len(jobs),len({j['id'] for j in jobs}))
        self.assertEqual(len([j for j in jobs if j.get('operation')=='prepare']),2)
        self.assertEqual(len([j for j in jobs if j.get('operation')=='reference']),8)
        self.assertEqual(len([j for j in jobs if j.get('operation')=='starter']),4)

    def test_new_regressions_are_selected_on_both_solvers(self):
        for backend in ('NATIVE','JAVA'):
            jobs=suites.plan('full-headless',backends=[backend])
            names={n for job in jobs for n in job.get('namespaces',[])}
            self.assertTrue({'wwpg_contactors','wwpg_api_ownership','wwpg_rhs','wwpg_wire_recovery',
                'wwpg_pause_epoch','wwpg_wire_power','wwpg_display_pulse','wwpg_cee_preparation'}<=names)
            self.assertEqual('wwpg_native_guard' in names,backend=='NATIVE')

    def test_pinout_regressions_do_not_launch_twice(self):
        jobs=suites.plan('full-headless',backends=['NATIVE'],profiles=['PINOUT'],platforms=['windows-latest'],loaders=['21.1.231'])
        self.assertEqual(len([j for j in jobs if j.get('group')=='regression']),4)
        self.assertFalse(any(j.get('group')=='pinout' for j in jobs))
        self.assertIn('wwpg_pinout',next(j for j in jobs if j.get('group')=='regression')['namespaces'])

    def test_restart_sequences_and_exact_reopens_remain(self):
        jobs=suites.plan('full-headless',backends=['NATIVE'],profiles=['PINOUT'],platforms=['windows-latest'],loaders=['21.1.231'])
        for group in ('yard','workshop'):
            source=[j for j in jobs if j.get('group')==group and 'phase' in j]
            self.assertEqual([j['phase'] for j in source],['SETUP','VERIFY','VERIFY','VERIFY'])
            self.assertEqual(len([j for j in jobs if j.get('group')==group+'-extracted']),3)
        self.assertEqual([j['phase'] for j in jobs if j.get('group')=='pending'],['SETUP','ARM','VERIFY'])

    def test_no_base_yard_and_no_repeated_reference_per_profile(self):
        jobs=suites.plan('full-headless')
        self.assertTrue(all(j['profile']=='PINOUT' for j in jobs if j.get('group','').startswith('yard')))
        self.assertTrue(all(j['profile']=='BASE' for j in jobs if j.get('operation')=='reference'))

    def test_focused_requires_scope_and_does_not_expand(self):
        with self.assertRaisesRegex(ValueError,'--group'):suites.plan('focused')
        jobs=suites.plan('focused',['safety'],['JAVA'],['BASE'],platforms=['windows-latest'])
        self.assertEqual(len(jobs),1);self.assertEqual(jobs[0]['group'],'safety')
        with self.assertRaisesRegex(ValueError,'complete catalog'):suites.plan('full-headless',['safety'])
        with self.assertRaisesRegex(ValueError,'local Windows'):suites.plan('graphical',platforms=['ubuntu-24.04'])

    def test_graphics_are_local_explicit_and_have_smoke_coverage(self):
        jobs=suites.plan('graphical')
        self.assertEqual(len(jobs),12)
        self.assertEqual({j['platform'] for j in jobs},{'windows-latest'})
        self.assertTrue(all(j['lane']=='graphical' for j in jobs))
        self.assertFalse(any('representative-visuals' in j['checks'] for j in jobs if j['backend']=='JAVA'))
        self.assertTrue(any(j['scenario']=='base-workshop' for j in jobs))

    def test_manual_workflow_cannot_start_on_push_or_release(self):
        text=(suites.ROOT/'.github/workflows/build.yml').read_text()
        self.assertIn('workflow_dispatch:',text)
        self.assertNotIn('  push:',text);self.assertNotIn('  pull_request:',text)
        self.assertNotIn('  schedule:',text);self.assertNotIn('  release:',text)
        self.assertNotIn('run full-headless',(suites.ROOT/'.github/workflows/packaging-checks.yml').read_text())

    def test_commands_use_frozen_inputs_and_preserve_watchdog(self):
        inputs={'version':'0.2.2-test','artifacts':{r:{'path':'frozen/'+r+'.jar'} for r in suites.ROLES+('reference',)}}
        job=suites.plan('focused',['safety'],['JAVA'],['BASE'],platforms=['windows-latest'])[0]
        args,_=suites.command(job,inputs,Path('scratch'))
        self.assertIn('-PtestArtifact=frozen/runtime.jar',args)
        self.assertEqual(args[args.index('--timeout')+1],'600')
        self.assertIn('-PtestExampleArtifact=frozen/example.jar',args)


class EvidenceChecks(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(dir=suites.ROOT/'build');self.addCleanup(self.temp.cleanup)
        self.base=Path(self.temp.name)

    def file(self,name,text):
        path=self.base/name;path.write_text(text,encoding='utf-8');return suites.descriptor(path)

    def test_missing_or_failed_headless_reports_do_not_pass(self):
        with self.assertRaisesRegex(ValueError,'Missing'):evidence.validate_reports([], 'full-headless','a'*40,{})
        report=dict(schema=2,policy='separated-v2',suite='full-headless',status='failed')
        path=self.base/'failed.json';suites.write(path,report)
        with self.assertRaisesRegex(ValueError,'Incomplete'):evidence.validate_reports([path],'full-headless','a'*40,{})

    def test_process_exit_zero_without_completion_is_rejected(self):
        expected=suites.plan('focused',['safety'],['JAVA'],['BASE'],platforms=['windows-latest'])[0]
        proof=dict(completed=False,required_tests_passed=0)
        actual=dict(expected,status='passed',exit_code=0,log=self.file('log','exit zero'),proof=self.file('proof.json',json.dumps(proof)))
        with self.assertRaisesRegex(ValueError,'complete'):evidence.validate_job(actual,expected,{},self.base)

    def passing_headless_fragment(self):
        job=suites.plan('focused',['safety'],['JAVA'],['BASE'],['21.1.231'],['windows-latest'])[0]
        artifacts={role:self.file(role+'.jar','TEST_ONLY '+role) for role in suites.ROLES}
        hashes={role:item['sha256'] for role,item in artifacts.items()}
        proof=dict(completed='actual completion',required_tests_passed=50,profile='BASE',backend='JAVA',
            sha256=hashes['runtime'],phase='SETUP',neoforge='21.1.231',namespaces=','.join(job['namespaces']),
            addon_artifacts={r:{'sha256':hashes[r]} for r in ('example','upstream_example')})
        log='GAME TESTS COMPLETE\nAll 50 required tests passed\nAll dimensions are saved\nWWPG_TEST_BACKEND_CONFIG: requested=JAVA, configured=JAVA\n'
        actual=dict(job,status='passed',exit_code=0,log=self.file('log',log),proof=self.file('proof.json',json.dumps(proof)))
        inputs=dict(schema=2,policy='separated-v2',source_sha='a'*40,artifacts=artifacts)
        report=dict(schema=2,policy='separated-v2',source_sha='a'*40,suite='full-headless',status='passed',not_run=[],
            artifact_hashes=hashes,catalog=suites.descriptor(suites.CATALOG),inputs=self.file('inputs.json',json.dumps(inputs)),jobs=[actual])
        path=self.base/'result.json';suites.write(path,report)
        return path,report,proof

    def test_passing_fragment_cannot_claim_complete_suite(self):
        path,report,_=self.passing_headless_fragment()
        self.assertEqual(evidence.validate_reports([path],'full-headless','a'*40,report['artifact_hashes'],complete=False),{report['jobs'][0]['id']})
        with self.assertRaisesRegex(ValueError,'Missing full-headless'):evidence.validate_reports([path],'full-headless','a'*40,report['artifact_hashes'])

    def test_actual_restart_phase_and_artifacts_are_checked(self):
        path,report,proof=self.passing_headless_fragment();proof['phase']='VERIFY'
        report['jobs'][0]['proof']=self.file('wrong-phase.json',json.dumps(proof));path.write_text(json.dumps(report))
        with self.assertRaisesRegex(ValueError,'Lifecycle'):evidence.validate_reports([path],'full-headless','a'*40,report['artifact_hashes'],complete=False)

    def test_loaded_artifact_mismatch_cannot_pass(self):
        path,report,proof=self.passing_headless_fragment();proof['sha256']='0'*64
        report['jobs'][0]['proof']=self.file('wrong-runtime.json',json.dumps(proof));path.write_text(json.dumps(report))
        with self.assertRaisesRegex(ValueError,'loaded artifact'):evidence.validate_reports([path],'full-headless','a'*40,report['artifact_hashes'],complete=False)

    def test_portable_fragment_can_be_validated_without_original_paths(self):
        from bundle_suite_evidence import bundle
        path,report,_=self.passing_headless_fragment();portable=bundle(path,self.base/'portable')
        # Portable descriptors use workspace-relative object paths even when
        # their containing JSON lives deeper in the object directory.
        self.assertTrue(evidence.validate_reports([portable],'full-headless','a'*40,report['artifact_hashes'],complete=False))

    def test_release_cannot_substitute_headless_for_graphical(self):
        manifest=dict(acceptance_policy='separated-v2',artifacts={r:{'sha256':'a'*64} for r in suites.ROLES},headless_acceptance={})
        with self.assertRaisesRegex(ValueError,'Independent headless and graphical'):evidence.validate_release(manifest,self.base,None)

    def test_forged_client_pass_flags_are_not_coverage(self):
        job=suites.plan('graphical',backends=['JAVA'],profiles=['BASE'])[0]
        self.assertEqual(observed(job,{'client':'passed=true\nCI_SMOKE_CLIENT_COMPLETE'}),set())

    def test_shutdown_requires_click_title_and_saved_server(self):
        job=suites.plan('graphical',backends=['JAVA'],profiles=['BASE'])[0]
        hashes={r:r for r in suites.ROLES}
        helper=self.file('helper.jar','binary')
        launch=self.file('launch.json',json.dumps(dict(helper=helper,helper_sources=[self.file('source.java','source')])))
        logs={'client':self.file('client.log','CI_SMOKE_CLIENT_COMPLETE')}
        proof=dict(schema=2,scenario_id=job['id'],backend=job['backend'],profile=job['profile'],loader=job['loader'],
            source_sha='a'*40,artifact_hashes=hashes,passed=True,world=self.file('world.zip','world'),launch=launch,
            processes={'client':{'exit_code':0}},logs=logs,screenshots=[])
        with self.assertRaisesRegex(ValueError,'Save and Quit'):validate_observation(proof,job,dict(source_sha='a'*40,artifact_hashes=hashes),self.base)
        wrong=copy.deepcopy(proof);wrong['backend']='NATIVE'
        with self.assertRaisesRegex(ValueError,'environment'):validate_observation(wrong,job,dict(source_sha='a'*40,artifact_hashes=hashes),self.base)

    def test_portable_copy_retains_original_json_and_hashes(self):
        from bundle_suite_evidence import bundle
        raw=self.base/'nested.json';raw.write_text(json.dumps({'source_sha':'a'*40,'log':self.file('raw.log','actual log')}))
        report=self.base/'report.json';report.write_text(json.dumps({'proof':suites.descriptor(raw)}));before=raw.read_bytes()
        result=bundle(report,self.base/'portable');copied=suites.read(result)
        view=suites.read(suites.ROOT/copied['proof']['path'])
        original=suites.ROOT/view['_original_record']['path']
        self.assertEqual(original.read_bytes(),before);self.assertEqual(raw.read_bytes(),before)
        self.assertEqual(view['source_sha'],'a'*40)

    def test_preparation_snapshots_external_junit_before_worker_download(self):
        from ci_prepare import retain_preparation
        original = self.file('outside-campaign.xml','<testsuite tests="1" failures="0" errors="0"/>')
        output=self.base/'campaign';output.mkdir()
        path=retain_preparation(output,dict(junit=[original],platform='windows-latest'))
        Path(original['path']).unlink()
        proof=suites.read(path)
        self.assertTrue(evidence.locked(proof['junit'][0],output).is_file())
        self.assertTrue((output/'preparation-original.json').is_file())

    def test_catalog_is_retained_independently_of_later_edits(self):
        source=self.base/'mutable-catalog.json';source.write_text('{"original":true}')
        output=self.base/'attempt';output.mkdir()
        with patch.object(suites,'CATALOG',source):record=suites.snapshot_catalog(output)
        source.write_text('{"changed":true}')
        self.assertEqual(suites.read(evidence.locked(record,output)),{'original':True})


if __name__=='__main__':unittest.main()
