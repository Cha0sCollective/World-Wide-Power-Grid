"""Owner-approved graphical exception for one exact alpha; full headless remains mandatory."""
import hashlib
import json
import re
import xml.etree.ElementTree as ET

VERSION = '0.2.12-dev.cee120.1'
RUNTIME_SOURCE = '0cbbc7f7837eec738237a839d5aa16235f0e9561'
AUTHORIZATION = 'those notes look good, i approve the headless-only alpha exception for this exact build, go ahead and publish 0.2.12-dev.cee120.1'
ARTIFACTS = {
    "runtime": "ec4980f544120e39c89002c0ee0cb936ec62194c9685177d2e40022344cc6723",
    "gametest": "3d832f7a951ebc7e65ad9b32f12a398b4a69d29864c84a96c297697425c312d7",
    "api": "a75b5fe80b58778b8fb871031fb60c829ff7f05d78880e1b8f5ae9eac5af8892",
    "cee_api": "d5792f888ddd4b72ef127d7dcfe8948ed2dcdcf9002f196ae974b8364dd3360a",
    "example": "17ef99aa7fb06acab09efb67badd3b27aff30250167049177d952ee86b46fcef",
    "upstream_example": "4a412dcc1bdc887b267588aa1f75b397403f9ce72aa1afa8076dec7b52aaa9b6",
    "maven_repository": "5ef39059bf4bd4a73dee2d381d016091fb73bd6cc557c0f138d6a4b87862a859",
    "api_sources": "1862a8763d1687e785b3928c92c64c4cb797d9183a853f9e0f45f25f4102f55b",
    "api_javadoc": "1034ac31be14d6978729720111048efeb40d5d161b7a8b4c4b4b9df81dc8e82c",
    "cee_api_sources": "00e2e53fa240830a8d2d7e9ecb35449651a88c0da88067003e69da41f13b9f34",
    "cee_api_javadoc": "47e503361326fa243be8a7665d0aaabb4f589625b9792af9681cb54182f1527f",
    "example_sources": "f4717a0fb162ca30876982db29904d6d6dfcfd23ab3b1f0ea4b5efdc9e99ecb3",
    "upstream_example_sources": "e55d1a0ae22384d7a02c66bc3abb9a2044c85e4696bdc258f9943c9debb14f99"
}
WORLDS = {
    "stationary_yard": "9c0bd40a06add6394a155c330b28683490d4af8d6021c23b05e51d5d46e39d85",
    "base_workshop_world": "6223c4c9236ebeeddc59b510b5b08f22dd5657d2c9fc511e83dabeba802cb85a",
    "pinout_workshop_world": "17634f9e9342835cc887160c14c5be6f8b4616b647e7778435f45446a2b58191"
}
CATALOG = '60b456f93edd27058680820cdfc89b9cb4d5d501ecb702408769749d91918797'
HEADLESS_ARTIFACTS = {
    "runtime": "ec4980f544120e39c89002c0ee0cb936ec62194c9685177d2e40022344cc6723",
    "api": "a75b5fe80b58778b8fb871031fb60c829ff7f05d78880e1b8f5ae9eac5af8892",
    "cee_api": "d5792f888ddd4b72ef127d7dcfe8948ed2dcdcf9002f196ae974b8364dd3360a",
    "example": "17ef99aa7fb06acab09efb67badd3b27aff30250167049177d952ee86b46fcef",
    "upstream_example": "4a412dcc1bdc887b267588aa1f75b397403f9ce72aa1afa8076dec7b52aaa9b6",
    "reference": "d65bcc5a08766a24c809dc50c0455b681ea5842fc6e7e6a8e8a5b4b295b53eff",
    "gametest": "3d832f7a951ebc7e65ad9b32f12a398b4a69d29864c84a96c297697425c312d7",
    "computercraft": "e8651fe24b384e925e563c9ad077644744474e2e7c0adb8e6d5b4757598dce9f",
    "pinout": "d31c19c3c8aefb512922bf8086146127cad7ef9100d929a8fddcc2ab133fdd4a",
    "gauged": "3798534240dc72cc34df5783c03b3a0178c6a7361a4bd198af014982606839e3",
    "power_components": "73a130d1144f52fb94e6d7ed70c89692a2f7b7ad0476a4cfb81cde47a40261fd",
    "cee_runtime": "71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7"
}
NOTES = '44db795a480ced3f12eb2817992d7b111e0c2b34ac6c8e697cf3c1010bd49f89'


def record(manifest, root, api):
    api.require(manifest.get('wwpg_version') == VERSION and manifest.get('api_version') == '1.0.0',
                'Headless alpha exception applies only to 0.2.12-dev.cee120.1')
    api.require(set(manifest.get('artifacts', {})) == set(ARTIFACTS) and
                all(manifest['artifacts'][role]['sha256'] == digest for role, digest in ARTIFACTS.items()),
                'Headless alpha requires the exact tested player, companion and SDK payloads')
    value = api.read(api.locked_file(manifest['alpha_release_exception'], root))
    api.require(value.get('schema') == 1 and value.get('scope') == 'cee120-0212-headless-alpha-1'
                and value.get('wwpg_version') == VERSION and value.get('runtime_source_sha') == RUNTIME_SOURCE
                and value.get('source_sha') == manifest.get('source_sha')
                and re.fullmatch('[0-9a-f]{40}', manifest.get('source_sha', ''))
                and manifest['source_sha'] != '0' * 40
                and value.get('runtime_sha256') == ARTIFACTS['runtime']
                and value.get('gametest_sha256') == ARTIFACTS['gametest'], 'Headless alpha source/artifact identity differs')
    api.require(value.get('owner_authorization') == AUTHORIZATION, 'Exact headless-only alpha authorization is required')
    api.require(value.get('full_headless_acceptance') is True and value.get('full_graphical_acceptance') is False
                and value.get('new_graphical_client_run') is False,
                'Only graphical acceptance is excepted; passing full headless evidence remains required')
    scope = api.read(api.locked_file(value['source_scope'], root))
    api.require(scope.get('tested_source_sha') == RUNTIME_SOURCE and scope.get('publication_source_sha') == manifest['source_sha']
                and scope.get('protected_runtime_diff') == [] and scope.get('runtime_payload_identical') is True,
                'Publication source must retain the tested runtime')
    return value


def validate_presentation(manifest, root, config, api):
    value = record(manifest, root, api)
    expected = api.read(api.locked_file(value['presentation'], root))
    from release_downloads import notes
    api.require(config == expected and hashlib.sha256(notes(VERSION, config).encode('utf-8')).hexdigest() == NOTES,
                'The exact approved alpha notes and graphical disclosure must remain intact')


def validate(manifest, root, api):
    value = record(manifest, root, api)
    api.require(manifest.get('acceptance_policy') == 'separated-v2' and manifest.get('unresolved_failures') == []
                and manifest.get('stationary_yard_policy') == 'combined-pinout-v1', 'Unexpected alpha acceptance policy/failures')
    spec_path = api.locked_file(value['catalog'], root)
    api.require(api.sha(spec_path) == CATALOG, 'The exact accepted 286-job CEE catalog is required')
    spec = api.read(spec_path)
    cohort = api.read(api.locked_file(manifest['headless_acceptance'], root))
    api.require(cohort.get('schema') == 2 and cohort.get('policy') == 'separated-v2' and
                cohort.get('suite') == 'full-headless' and cohort.get('status') == 'passed'
                and not cohort.get('historical_review_only') and 'reuse' not in cohort
                and len(cohort.get('reports', [])) == 8, 'All eight fresh headless reports are required')
    paths = [api.locked_file(item, root) for item in cohort['reports']]
    reports = [api.read(path) for path in paths]
    hashes = reports[0]['artifact_hashes']
    api.require(all(report.get('artifact_hashes') == HEADLESS_ARTIFACTS for report in reports),
                'Headless reference, CEE and optional dependency inputs must retain their exact tested hashes')
    api.require(all(hashes.get(role) == ARTIFACTS[role] for role in
                    ('runtime', 'gametest', 'api', 'cee_api', 'example', 'upstream_example')),
                'Headless tests used different frozen artifacts')
    import suite_evidence
    previous_root = suite_evidence.ROOT
    # Portable captures keep producer-relative paths. Only their filesystem
    # anchor changes; every original descriptor, byte count and hash stays locked.
    suite_evidence.ROOT = root
    try:
        seen = suite_evidence.validate_reports(paths, 'full-headless', RUNTIME_SOURCE, hashes, catalog_contract=spec)
    finally:
        suite_evidence.ROOT = previous_root
    api.require(len(seen) == 286, 'The complete 286-job headless acceptance is required')
    exports = set()
    environments = set()
    units = {}
    for report in reports:
        for job in report['jobs']:
            if job.get('operation') == 'export': exports.add(job['archive']['sha256'])
            if job.get('operation') == 'prepare':
                prepared = api.read(api.locked_file(job['proof'], root))
                count, names = 0, set()
                for item in prepared['junit']:
                    suite = ET.parse(api.locked_file(item, root)).getroot()
                    name = suite.attrib.get('name')
                    api.require(name not in names and all(int(suite.get(k, '-1')) == 0
                                for k in ('failures', 'errors', 'skipped')), 'Unit evidence is duplicated or failing')
                    names.add(name); count += int(suite.get('tests', '0'))
                api.require(job['platform'] not in units and count == 160, 'Each OS requires all 160 passing JVM tests')
                units[job['platform']] = count
            log = api.locked_file(job['log'], root).read_text(encoding='utf-8', errors='replace')
            for row in re.findall(r'WWPG_RUNTIME_INPUTS_VERIFIED: (\{[^\r\n]+\})', log):
                loaded = json.loads(row)
                if not {'wwpg', 'powergrid', 'electroenergetics'} <= loaded.keys(): continue
                expected = {'electroenergetics': ('1.21.1-1.2.0-132', '71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7'),
                            'powergrid': ('0.6.2', 'e40d67a874431925d502af88e85064883c67936cfd689033d7df560cffcf19e7')}
                api.require(all(loaded[mod]['version'] == version and loaded[mod]['sha256'] == digest
                                for mod, (version, digest) in expected.items()), 'Actual loaded CEE/PG stack differs')
                mode = 'reference' if job.get('reference') == 'upstream' else 'runtime'
                api.require(loaded['wwpg']['sha256'] == hashes[mode], 'Actual loaded player/reference differs')
                environments.add((job['platform'], job['backend'], job['profile']))
    api.require(units == {'windows-latest': 160, 'ubuntu-24.04': 160}, 'Both operating-system preparations are required')
    api.require(environments == {(p, b, i) for p in ('windows-latest', 'ubuntu-24.04')
                                for b in ('NATIVE', 'JAVA') for i in ('BASE', 'ADDONS')}, 'All eight actual loaded environments are required')
    for role, digest in WORLDS.items():
        api.require(manifest[role]['sha256'] == digest and digest in exports, 'Alpha world is not the exact tested/reopened export')
        with api.checked_zip(api.locked_file(manifest[role], root)) as archive:
            headers = [n for n in archive.namelist() if n.endswith('/WWPG_MANIFEST.json')]
            api.require(len(headers) == 1, 'World inventory is ambiguous')
            header = json.loads(archive.read(headers[0]))
            api.require(header.get('target') == VERSION and header.get('jar', {}).get('sha256') == ARTIFACTS['runtime'],
                        'World version or runtime differs')
    validate_presentation(manifest, root, api.read(api.locked_file(value['presentation'], root)), api)
