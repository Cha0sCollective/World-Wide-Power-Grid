"""Focused negative controls for the one-build alpha publication exception."""
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

import package_api_release as api
import release_alpha_exception as alpha


class AlphaExceptionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='TEST_ONLY-alpha-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.manifest = dict(wwpg_version=alpha.VERSION, api_version='1.0.0', source_sha='a' * 40,
                             acceptance_policy='separated-v2', unresolved_failures=[],
                             stationary_yard_policy='combined-pinout-v1',
                             artifacts={'runtime': {'sha256': alpha.RUNTIME}, 'gametest': {'sha256': 'b' * 64}})
        config = dict(summary='Experimental alpha. Full acceptance has not been completed for this build.')
        self.value = dict(schema=1, scope='cee120-focused-alpha-8', wwpg_version=alpha.VERSION,
                          runtime_sha256=alpha.RUNTIME, source_sha='a' * 40,
                          owner_authorization='Allow a focused-check-only alpha prerelease',
                          full_headless_acceptance=False, full_graphical_acceptance=False,
                          in_world_sublevel_visuals_confirmed=False,
                          presentation=self.save('presentation.json', config), unit_reports=[], position_runs=[],
                          client_mod_inputs=self.save('client-mod-inputs.json', [
                              {'filename': 'power-dock-0.3.0-preview.jar', 'sha256': 'f79c8bb61169ba2e997d159ad4c8d0a164da3733ef27bf5714c2f5246714f363'},
                              {'filename': 'sable-neoforge-1.21.1-2.0.5.jar', 'sha256': 'c8710f85bc780bbf523e6726ceaaf76dd0b8c61479db497b382c6bb34317e7ab'},
                          ]), worlds_origin='0.2.10-dev.cee120.5; historical headless exports, not retested on .8',
                          original_worlds={})
        for name, count in [('compat.cee12.mixin.WirePreviewClientMixinTest', 8), ('wiring.TerminalPickingRayTest', 5)]:
            xml = f'<testsuite name="org.cha0scollective.wwpg.{name}" tests="{count}" failures="0" errors="0" skipped="0"/>'
            self.value['unit_reports'].append(self.save(name + '.xml', xml))
        for version, digest in alpha.CEE.items():
            audit = {mod: dict(version=v, sha256='c' * 64) for mod, v in
                     {'neoforge': '21.1.262', 'powergrid': '0.6.2', 'create': '6.0.10', 'architectury': '13.0.12'}.items()}
            audit.update(electroenergetics=dict(version=version, sha256=digest),
                         wwpg=dict(version=alpha.VERSION, sha256=alpha.RUNTIME), wwpg_tests=dict(sha256='b' * 64))
            self.value['position_runs'].append(dict(audit=self.save(version + '.json', audit),
                log=self.save(version + '.log', '2 GAME TESTS COMPLETE\nAll 2 required tests passed\nBUILD SUCCESSFUL')))
        self.value['client_log'] = self.save('client.log', f'SHA256 {alpha.RUNTIME}\n'
            f'WWPG_PREVIEW_MIXIN_SMOKE_PASSED wwpg={alpha.VERSION} cee=1.21.1-1.2.0-132 handlers=2\n'
            'Power Dock 0.3.0-preview (powerdock)\nSable 2.0.5 (sable)')
        for role in ('stationary_yard', 'base_workshop_world', 'pinout_workshop_world'):
            path = self.root / (role + '.zip')
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('TEST_ONLY/WWPG_MANIFEST.json', json.dumps({'target': '0.2.10-dev.cee120.5'}))
            self.manifest[role] = self.value['original_worlds'][role] = self.descriptor(path)

    def descriptor(self, path):
        return dict(path=path.relative_to(self.root).as_posix(), sha256=api.sha(path), bytes=path.stat().st_size)

    def save(self, name, value):
        path = self.root / name
        path.write_text(value if isinstance(value, str) else json.dumps(value), encoding='utf-8')
        return self.descriptor(path)

    def validate(self):
        self.manifest['alpha_release_exception'] = self.save('exception.json', self.value)
        alpha.validate(self.manifest, self.root, api)

    def test_complete_disclosed_focus_scope_is_allowed(self):
        self.validate()

    def test_other_versions_and_changed_runtime_are_rejected(self):
        for key, value in [('wwpg_version', '0.2.10'), ('wwpg_version', '0.2.10-dev.cee120.9'), ('api_version', '2.0.0')]:
            original = self.manifest[key]
            self.manifest[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(api.PublicationError): self.validate()
            self.manifest[key] = original
        self.manifest['artifacts']['runtime']['sha256'] = 'd' * 64
        with self.assertRaises(api.PublicationError): self.validate()

    def test_owner_decision_and_honest_scope_are_required(self):
        for key, value in [('owner_authorization', ''), ('full_headless_acceptance', True),
                           ('full_graphical_acceptance', True), ('in_world_sublevel_visuals_confirmed', True)]:
            original = self.value[key]
            self.value[key] = value
            with self.subTest(key=key), self.assertRaises(api.PublicationError): self.validate()
            self.value[key] = original

    def test_missing_legacy_checks_are_rejected(self):
        self.value['position_runs'].pop()
        with self.assertRaises(api.PublicationError): self.validate()

    def test_failed_process_cannot_become_a_pass_by_relocking(self):
        self.value['position_runs'][0]['log'] = self.save('failed.log', '2 GAME TESTS COMPLETE\n1 required tests failed')
        with self.assertRaises(api.PublicationError): self.validate()

    def test_other_player_or_companion_cannot_supply_evidence(self):
        record = self.value['position_runs'][0]
        audit = json.loads(api.locked_file(record['audit'], self.root).read_text())
        audit['wwpg_tests']['sha256'] = 'd' * 64
        record['audit'] = self.save('changed-audit.json', audit)
        with self.assertRaises(api.PublicationError): self.validate()

    def test_missing_units_and_failed_client_are_rejected(self):
        original = self.value['unit_reports']
        self.value['unit_reports'] = original[:1]
        with self.assertRaises(api.PublicationError): self.validate()
        self.value['unit_reports'] = original
        self.value['client_log'] = self.save('failed-client.log', 'WWPG_PREVIEW_MIXIN_SMOKE_FAILED')
        with self.assertRaises(api.PublicationError): self.validate()

    def test_notes_cannot_hide_the_limited_scope(self):
        self.value['presentation'] = self.save('bad-notes.json', {'summary': 'Fully accepted release.'})
        with self.assertRaises(api.PublicationError): self.validate()


if __name__ == '__main__': unittest.main()
