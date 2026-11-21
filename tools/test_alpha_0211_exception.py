"""Focused validator boundaries only; synthetic records are not gameplay evidence."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import package_api_release as api
import release_alpha_0211_exception as alpha
import release_alpha_exception as dispatcher


class ExactAlphaChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.value = dict(schema=1, scope='cee120-0211-focused-alpha-3', wwpg_version=alpha.VERSION,
                          runtime_sha256=alpha.RUNTIME, gametest_sha256=alpha.COMPANION,
                          runtime_source_sha=alpha.RUNTIME_SOURCE, source_sha='a'*40,
                          owner_authorization=alpha.AUTHORIZATION, full_headless_acceptance=False,
                          full_graphical_acceptance=False, new_graphical_client_run=False)
        self.manifest = dict(wwpg_version=alpha.VERSION, api_version='1.0.0', source_sha='a'*40,
                             artifacts={'runtime':{'sha256':alpha.RUNTIME},'gametest':{'sha256':alpha.COMPANION}})
        self.save()

    def save(self):
        path=self.root/'exception.json';path.write_text(json.dumps(self.value),encoding='utf-8')
        self.manifest['alpha_release_exception']={'path':path.name,'sha256':api.sha(path),'bytes':path.stat().st_size}

    def test_exact_owner_approved_identity_is_routed(self):
        self.assertEqual(dispatcher.record(self.manifest,self.root,api),self.value)

    def test_changed_player_or_companion_cannot_inherit_exception(self):
        for role in ('runtime','gametest'):
            manifest=copy.deepcopy(self.manifest);manifest['artifacts'][role]['sha256']='b'*64
            with self.assertRaises(api.PublicationError):dispatcher.record(manifest,self.root,api)

    def test_future_version_cannot_inherit_exception(self):
        self.manifest['wwpg_version']='0.2.11-dev.cee120.4'
        with self.assertRaises(api.PublicationError):dispatcher.record(self.manifest,self.root,api)

    def test_source_authorization_and_scope_are_required(self):
        for key in ('owner_authorization','runtime_source_sha','source_sha','scope'):
            old=self.value[key];self.value[key]='TEST_ONLY_WRONG';self.save()
            with self.assertRaises(api.PublicationError):alpha.record(self.manifest,self.root,api)
            self.value[key]=old

    def test_full_acceptance_cannot_be_asserted(self):
        for key in ('full_headless_acceptance','full_graphical_acceptance','new_graphical_client_run'):
            self.value[key]=True;self.save()
            with self.assertRaises(api.PublicationError):alpha.record(self.manifest,self.root,api)
            self.value[key]=False

    def test_empty_run_summary_cannot_replace_both_solver_evidence(self):
        self.value['runs']=[];self.save()
        self.manifest.update(acceptance_policy='separated-v2',unresolved_failures=[])
        with self.assertRaisesRegex(api.PublicationError,'Both solvers'):alpha.validate(self.manifest,self.root,api)

    def test_changed_or_missing_public_disclosure_is_rejected(self):
        config={'summary':'TEST_ONLY alpha full headless and graphical acceptance have not been completed',
                'installation':'TEST_ONLY no new graphical acceptance was run; unchanged historical exports'}
        path=self.root/'presentation.json';path.write_text(json.dumps(config),encoding='utf-8')
        self.value['presentation']={'path':path.name,'sha256':api.sha(path),'bytes':path.stat().st_size};self.save()
        alpha.validate_presentation(self.manifest,self.root,config,api)
        changed=dict(config,summary='TEST_ONLY complete acceptance')
        with self.assertRaises(api.PublicationError):alpha.validate_presentation(self.manifest,self.root,changed,api)

    def test_previous_exact_alpha_still_uses_original_record(self):
        self.manifest['wwpg_version']=dispatcher.VERSION
        self.manifest['artifacts']['runtime']['sha256']=dispatcher.RUNTIME
        self.value.update(scope='cee120-focused-alpha-8',wwpg_version=dispatcher.VERSION,
                          runtime_sha256=dispatcher.RUNTIME,owner_authorization='Allow a focused-check-only alpha prerelease',
                          in_world_sublevel_visuals_confirmed=False)
        self.save()
        self.assertEqual(dispatcher.record(self.manifest,self.root,api),self.value)


if __name__=='__main__':unittest.main()
