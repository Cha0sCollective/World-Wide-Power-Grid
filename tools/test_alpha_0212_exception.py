"""Exact publication boundaries; synthetic records are never execution evidence."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
import package_api_release as api
import release_alpha_exception as dispatcher
import release_alpha_0212_exception as alpha


class HeadlessAlphaChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.manifest = dict(wwpg_version=alpha.VERSION, api_version='1.0.0', source_sha='a'*40,
            artifacts={role: {'sha256':digest} for role,digest in alpha.ARTIFACTS.items()})
        self.scope = dict(tested_source_sha=alpha.RUNTIME_SOURCE, publication_source_sha='a'*40,
                          protected_runtime_diff=[], runtime_payload_identical=True)
        self.value = dict(schema=1, scope='cee120-0212-headless-alpha-1', wwpg_version=alpha.VERSION,
            source_sha='a'*40, runtime_source_sha=alpha.RUNTIME_SOURCE,
            runtime_sha256=alpha.ARTIFACTS['runtime'], gametest_sha256=alpha.ARTIFACTS['gametest'],
            owner_authorization=alpha.AUTHORIZATION, full_headless_acceptance=True,
            full_graphical_acceptance=False, new_graphical_client_run=False)
        self.save()

    def file(self,name,value):
        path=self.root/name;path.write_text(json.dumps(value),encoding='utf-8')
        return dict(path=name,sha256=api.sha(path),bytes=path.stat().st_size)

    def save(self):
        self.value['source_scope']=self.file('scope.json',self.scope)
        self.manifest['alpha_release_exception']=self.file('exception.json',self.value)

    def test_only_exact_approved_identity_is_routed(self):
        self.assertEqual(dispatcher.record(self.manifest,self.root,api),self.value)
        for role in alpha.ARTIFACTS:
            changed=copy.deepcopy(self.manifest);changed['artifacts'][role]['sha256']='b'*64
            with self.subTest(role=role),self.assertRaises(api.PublicationError):
                dispatcher.record(changed,self.root,api)
        self.manifest['wwpg_version']='0.2.12-dev.cee120.2'
        with self.assertRaises(api.PublicationError):dispatcher.record(self.manifest,self.root,api)

    def test_authorization_source_and_scope_cannot_be_inherited(self):
        for key in ('owner_authorization','runtime_source_sha','source_sha','scope'):
            old=self.value[key];self.value[key]='TEST_ONLY_WRONG';self.save()
            with self.subTest(key=key),self.assertRaises(api.PublicationError):alpha.record(self.manifest,self.root,api)
            self.value[key]=old
        self.scope['protected_runtime_diff']=['src/TEST_ONLY_CHANGED.java'];self.save()
        with self.assertRaises(api.PublicationError):alpha.record(self.manifest,self.root,api)

    def test_headless_pass_is_required_and_graphical_pass_cannot_be_invented(self):
        for key in ('full_headless_acceptance','full_graphical_acceptance','new_graphical_client_run'):
            old=self.value[key];self.value[key]=not old;self.save()
            with self.subTest(key=key),self.assertRaises(api.PublicationError):alpha.record(self.manifest,self.root,api)
            self.value[key]=old

    def test_approved_notes_are_locked_including_graphical_disclosure(self):
        config=api.read(Path(__file__).resolve().parents[1]/'release'/alpha.VERSION/'presentation.json')
        self.value['presentation']=self.file('presentation.json',config);self.save()
        alpha.validate_presentation(self.manifest,self.root,config,api)
        changed=dict(config,summary='TEST_ONLY alpha with complete graphical acceptance')
        self.value['presentation']=self.file('presentation.json',changed);self.save()
        with self.assertRaises(api.PublicationError):alpha.validate_presentation(self.manifest,self.root,changed,api)

    def test_summary_cannot_replace_the_original_full_headless_catalog(self):
        self.manifest.update(acceptance_policy='separated-v2',unresolved_failures=[],stationary_yard_policy='combined-pinout-v1')
        self.value['catalog']=self.file('catalog.json',{'TEST_ONLY':'empty selection'});self.save()
        with self.assertRaisesRegex(api.PublicationError,'exact accepted 286-job'):
            alpha.validate(self.manifest,self.root,api)


if __name__=='__main__':unittest.main()
