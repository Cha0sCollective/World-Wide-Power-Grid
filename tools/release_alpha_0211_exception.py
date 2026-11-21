"""Owner-approved exception for one immutable CEE 132 alpha, with real focused evidence."""
import re
import xml.etree.ElementTree as ET

VERSION = '0.2.11-dev.cee120.3'
RUNTIME = '8536f2efcb5b2af88b0b6757c08653ec9b7bb940e15442b5405bf80c550147d4'
COMPANION = '3882a9aeec583375f2fbf707d5bc65e1933aba888d3fc6509be0c86c90b7e4ae'
RUNTIME_SOURCE = 'ac59a3880ca5d165950f326668e5d4cb7dc8b871'
AUTHORIZATION = 'those notes look good, i approve the focused-check-only exception for this alpha, go ahead and publish it'


def record(manifest, root, api):
    api.require(manifest.get('wwpg_version') == VERSION and manifest.get('api_version') == '1.0.0'
                and manifest['artifacts']['runtime']['sha256'] == RUNTIME
                and manifest['artifacts']['gametest']['sha256'] == COMPANION,
                'Alpha exception requires the exact tested .3 player and companion')
    value = api.read(api.locked_file(manifest['alpha_release_exception'], root))
    api.require(value.get('schema') == 1 and value.get('scope') == 'cee120-0211-focused-alpha-3'
                and value.get('wwpg_version') == VERSION and value.get('runtime_sha256') == RUNTIME
                and value.get('gametest_sha256') == COMPANION and value.get('runtime_source_sha') == RUNTIME_SOURCE
                and value.get('source_sha') == manifest['source_sha'] and manifest['source_sha'] != '0' * 40,
                'Focused alpha source/artifact identity differs')
    api.require(value.get('owner_authorization') == AUTHORIZATION, 'Exact .3 owner authorization is required')
    api.require(value.get('full_headless_acceptance') is False and value.get('full_graphical_acceptance') is False
                and value.get('new_graphical_client_run') is False, 'Alpha must retain its focused-only limitations')
    return value


def validate_presentation(manifest, root, config, api):
    value = record(manifest, root, api)
    expected = api.read(api.locked_file(value['presentation'], root))
    api.require(config == expected and 'alpha' in config['summary'].lower()
                and 'full headless and graphical acceptance have not been completed' in config['summary']
                and 'no new graphical acceptance was run' in config['installation']
                and 'unchanged historical exports' in config['installation'],
                'Approved .3 notes and acceptance disclosure must remain intact')


def validate(manifest, root, api):
    value = record(manifest, root, api)
    api.require(manifest.get('acceptance_policy') == 'separated-v2' and manifest.get('unresolved_failures') == [],
                'Unexpected focused alpha policy/failures')
    seen = set()
    for run in value['runs']:
        key = (run['backend'], run['scope'])
        api.require(key not in seen and run['backend'] in {'NATIVE', 'JAVA'}
                    and run['scope'] in {'contact-transformer', 'surrounding'}, 'Unexpected or duplicate focused run')
        seen.add(key)
        count = 8 if run['scope'] == 'contact-transformer' else 32
        audit = api.read(api.locked_file(run['audit'], root))
        expected = {'wwpg': (VERSION, RUNTIME), 'wwpg_tests': (VERSION, COMPANION),
                    'electroenergetics': ('1.21.1-1.2.0-132', '71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7'),
                    'powergrid': ('0.6.2', 'e40d67a874431925d502af88e85064883c67936cfd689033d7df560cffcf19e7')}
        for mod, (version, digest) in expected.items():
            api.require(audit[mod]['version'] == version and audit[mod]['sha256'] == digest,
                        'Focused alpha loaded a different ' + mod)
        for mod, version in {'neoforge': '21.1.262', 'create': '6.0.10', 'architectury': '13.0.12'}.items():
            api.require(audit[mod]['version'] == version and api.HASH.fullmatch(audit[mod]['sha256']),
                        'Focused alpha dependency differs: ' + mod)
        text = api.locked_file(run['log'], root).read_text(encoding='utf-8', errors='replace')
        api.require(f'All {count} required tests passed' in text and 'All dimensions are saved' in text
                    and f'requested={run["backend"]}, configured={run["backend"]}' in text
                    and not re.search(r'[1-9][0-9]* required tests failed|Tests? .* failed at', text),
                    'Focused alpha process did not complete the expected passing selection')
        if run['scope'] == 'contact-transformer':
            for marker in ('WWPG_TRANSFORMER_REFERENCE', 'CEE_ELECTROCUTION contact-settled',
                           'CEE_ELECTROCUTION dielectric', 'CEE_ELECTROCUTION insulated', 'CEE_ELECTROCUTION reject-recover'):
                api.require(marker in text, 'Focused gameplay observation missing: ' + marker)
    api.require(seen == {(b, s) for b in ('NATIVE', 'JAVA') for s in ('contact-transformer', 'surrounding')},
                'Both solvers and all four focused selections are required')
    total, names = 0, set()
    for descriptor in value['unit_reports']:
        suite = ET.parse(api.locked_file(descriptor, root)).getroot()
        name = suite.attrib.get('name')
        api.require(name not in names and all(int(suite.attrib.get(k, '-1')) == 0 for k in ('failures', 'errors', 'skipped')),
                    'Unit evidence is duplicated or not fully passing')
        names.add(name); total += int(suite.attrib.get('tests', '0'))
    api.require(total == 158 and 'org.cha0scollective.wwpg.bridge.ReferenceIslandsTest' in names
                and 'org.cha0scollective.wwpg.compat.cee12.mixin.WirePreviewClientMixinTest' in names,
                'Exact .3 JVM evidence is required')
    python = api.locked_file(value['python_log'], root).read_text(encoding='utf-8')
    api.require('Ran 66 tests' in python and python.rstrip().endswith('OK'), 'Focused tooling checks did not pass')
    api.require(api.read(api.locked_file(value['catalog'], root)).get('namespaces') == 86,
                'Focused namespace inventory differs')
    api.require(manifest.get('stationary_yard_policy') == 'combined-pinout-v1'
                and value.get('worlds_origin') == '0.2.10-dev.cee120.5; historical headless exports, not retested on .3',
                'Historical world origins must remain explicit')
    for role in ('stationary_yard', 'base_workshop_world', 'pinout_workshop_world'):
        api.require(manifest[role] == value['original_worlds'][role], 'Original world descriptor changed')
        original = {'stationary_yard': 'cd6379fc99d0165f68e414c14d04992774dbb409a14fcfd84493e179b4ae0bf4',
                    'base_workshop_world': 'b65e427481d332da3c37090cc8a50a086545ab69e69ce61cccb2e79d6de414e8',
                    'pinout_workshop_world': '1f6f5558d9a27a18c64f4f09617996826d58b6b7fcca262861720943b1af97c4'}
        api.require(manifest[role]['sha256'] == original[role], 'Historical world bytes differ from the approved exports')
        with api.checked_zip(api.locked_file(manifest[role], root)) as archive:
            records = [n for n in archive.namelist() if n.endswith('/WWPG_MANIFEST.json')]
            import json
            api.require(len(records) == 1 and json.loads(archive.read(records[0])).get('target') == '0.2.10-dev.cee120.5',
                        'Historical world target changed')
    validate_presentation(manifest, root, api.read(api.locked_file(value['presentation'], root)), api)
