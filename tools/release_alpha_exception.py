"""Owner-authorized exceptions for two exact CEE 1.2 alphas; never a general bypass."""
import xml.etree.ElementTree as ET

VERSION = '0.2.10-dev.cee120.8'
RUNTIME = '2dfe78d0b18690753ec68dd1ed4de2791cd4925fa72789b741fff4fdfd4394d0'
CEE = {
    '1.21.1-1.2.0-132': '71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7',
    '1.21.1-1.1.4': '2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f',
    '1.21.1-1.1.3': 'ae7ea74b7b00f175daa5a60a953a6d57f7f775aed0b642a1e9f80c3865cf8be7',
}


def record(manifest, root, api):
    if manifest.get('wwpg_version') == '0.2.11-dev.cee120.3':
        import release_alpha_0211_exception
        return release_alpha_0211_exception.record(manifest, root, api)
    api.require(manifest.get('wwpg_version') == VERSION and manifest.get('api_version') == '1.0.0'
                and manifest['artifacts']['runtime']['sha256'] == RUNTIME,
                'Alpha exception applies only to the exact .8 runtime')
    value = api.read(api.locked_file(manifest['alpha_release_exception'], root))
    api.require(value.get('schema') == 1 and value.get('scope') == 'cee120-focused-alpha-8'
                and value.get('wwpg_version') == VERSION and value.get('runtime_sha256') == RUNTIME
                and value.get('source_sha') == manifest['source_sha'] and manifest['source_sha'] != '0' * 40,
                'Alpha exception identity differs')
    api.require(value.get('owner_authorization') == 'Allow a focused-check-only alpha prerelease',
                'Exact owner authorization is required for this alpha exception')
    api.require(value.get('full_headless_acceptance') is False and value.get('full_graphical_acceptance') is False
                and value.get('in_world_sublevel_visuals_confirmed') is False,
                'Focused alpha must retain its acceptance limitations')
    return value


def validate_presentation(manifest, root, config, api):
    if manifest.get('wwpg_version') == '0.2.11-dev.cee120.3':
        import release_alpha_0211_exception
        return release_alpha_0211_exception.validate_presentation(manifest, root, config, api)
    value = record(manifest, root, api)
    expected = api.read(api.locked_file(value['presentation'], root))
    api.require(config == expected and 'alpha' in config['summary'].lower()
                and 'Full acceptance has not been completed' in config['summary'],
                'Alpha notes must retain the locked acceptance disclosure')


def validate(manifest, root, api):
    if manifest.get('wwpg_version') == '0.2.11-dev.cee120.3':
        import release_alpha_0211_exception
        return release_alpha_0211_exception.validate(manifest, root, api)
    value = record(manifest, root, api)
    api.require(manifest.get('acceptance_policy') == 'separated-v2'
                and manifest.get('unresolved_failures') == [], 'Unexpected focused alpha policy/failures')
    units = {}
    for descriptor in value['unit_reports']:
        suite = ET.parse(api.locked_file(descriptor, root)).getroot()
        api.require(suite.attrib.get('name') not in units, 'Duplicate alpha unit report')
        api.require(all(int(suite.attrib.get(k, '-1')) == 0 for k in ('failures', 'errors', 'skipped')),
                    'Alpha unit checks did not pass completely')
        units[suite.attrib['name']] = int(suite.attrib['tests'])
    api.require(units == {
        'org.cha0scollective.wwpg.compat.cee12.mixin.WirePreviewClientMixinTest': 8,
        'org.cha0scollective.wwpg.wiring.TerminalPickingRayTest': 5,
    }, 'Exact focused alpha unit groups are required')
    versions = set()
    for run in value['position_runs']:
        audit = api.read(api.locked_file(run['audit'], root))
        version = audit['electroenergetics']['version']
        api.require(version in CEE and version not in versions and audit['electroenergetics']['sha256'] == CEE[version],
                    'Focused alpha CEE runtime differs or is duplicated')
        versions.add(version)
        api.require(audit['wwpg']['version'] == VERSION and audit['wwpg']['sha256'] == RUNTIME
                    and audit['wwpg_tests']['sha256'] == manifest['artifacts']['gametest']['sha256'],
                    'Focused coordinate evidence used another player/companion')
        for mod, expected in {'neoforge': '21.1.262', 'powergrid': '0.6.2', 'create': '6.0.10', 'architectury': '13.0.12'}.items():
            api.require(audit[mod]['version'] == expected and api.HASH.fullmatch(audit[mod]['sha256']),
                        'Focused alpha dependency identity missing: ' + mod)
        log = api.locked_file(run['log'], root).read_text(encoding='utf-8', errors='replace')
        api.require('2 GAME TESTS COMPLETE' in log and 'All 2 required tests passed' in log
                    and 'required tests failed' not in log and 'BUILD SUCCESSFUL' in log,
                    'Focused alpha coordinate process did not pass')
    api.require(versions == set(CEE), 'All three focused CEE versions are required')
    log = api.locked_file(value['client_log'], root).read_text(encoding='utf-8', errors='replace')
    api.require(f'WWPG_PREVIEW_MIXIN_SMOKE_PASSED wwpg={VERSION} cee=1.21.1-1.2.0-132 handlers=2' in log
                and 'WWPG_PREVIEW_MIXIN_SMOKE_FAILED' not in log and 'SHA256 ' + RUNTIME in log
                and 'Power Dock 0.3.0-preview (powerdock)' in log and 'Sable 2.0.5 (sable)' in log,
                'Exact alpha Power Dock/Sable client loading proof is required')
    mods = api.read(api.locked_file(value['client_mod_inputs'], root))
    api.require({item['filename']: item['sha256'] for item in mods} == {
        'power-dock-0.3.0-preview.jar': 'f79c8bb61169ba2e997d159ad4c8d0a164da3733ef27bf5714c2f5246714f363',
        'sable-neoforge-1.21.1-2.0.5.jar': 'c8710f85bc780bbf523e6726ceaaf76dd0b8c61479db497b382c6bb34317e7ab',
    }, 'Client mod input hashes differ; upstream mod jars are not bundled')
    api.require(manifest.get('stationary_yard_policy') == 'combined-pinout-v1', 'Alpha uses the combined yard')
    api.require(value.get('worlds_origin') == '0.2.10-dev.cee120.5; historical headless exports, not retested on .8',
                'Historical example worlds must not be relabelled as .8 acceptance')
    for role in ('stationary_yard', 'base_workshop_world', 'pinout_workshop_world'):
        path = api.locked_file(manifest[role], root)
        api.require(manifest[role] == value['original_worlds'][role], 'Historical world descriptor changed')
        with api.checked_zip(path) as archive:
            records = [n for n in archive.namelist() if n.endswith('/WWPG_MANIFEST.json')]
            api.require(len(records) == 1, 'Historical world inventory missing')
            import json
            api.require(json.loads(archive.read(records[0])).get('target') == '0.2.10-dev.cee120.5',
                        'Historical world target changed')
    validate_presentation(manifest, root, api.read(api.locked_file(value['presentation'], root)), api)
