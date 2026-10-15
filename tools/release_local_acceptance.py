"""Recheck retained Windows executions; never represent them as fresh GitHub CI.

Only the version-equivalent reuse path can combine these four local combinations
with four fresh Linux CI jobs. Logs, input identities, cold oracles and unit XML
remain locked inputs. Failed campaigns are retained separately, never accepted.
"""
import json
import gzip
import hashlib
from pathlib import PureWindowsPath
import re
import xml.etree.ElementTree as ET

import collect_api_ci_evidence as checks


def validate(descriptor, root, gate, reuse):
    record = gate.read(gate.locked_file(descriptor, root))
    gate.require(record.get('schema') == 1 and record.get('kind') == 'retained_local_windows'
                 and record.get('source_sha') == reuse['source_sha'], 'Wrong retained Windows source/scope')
    hashes = {k: reuse[k]['sha256'] for k in ('runtime', 'api', 'cee_api', 'example', 'upstream_example')}
    gate.require(record.get('artifact_hashes') == hashes, 'Retained Windows artifacts differ')
    original = json.loads(gzip.decompress(gate.locked_file(record['original_state_gzip'], root).read_bytes()))
    gate.require(original.get('passed') is True and original.get('running') is False
                 and original.get('source_sha') == reuse['source_sha']
                 and original.get('artifact', {}).get('sha256') == hashes['runtime']
                 and {k: v['sha256'] for k, v in original['artifacts'].items()} == hashes, 'Original Windows campaign was not accepted')
    ledger = json.loads(gzip.decompress(gate.locked_file(record['original_ledger_gzip'], root).read_bytes()))
    pair_hashes = {item['path']: item['sha256'] for item in ledger['files']}
    accepted_pairs = {PureWindowsPath(item['evidence']).name for item in original['server_pairs']}
    units = 0
    unit_names = set()
    for item in record['unit_xml']:
        path = gate.locked_file(item, root)
        gate.require(path.name not in unit_names and item['sha256'] == pair_hashes.get('unit-results/' + path.name), 'Retained unit evidence differs/duplicates')
        unit_names.add(path.name)
        suite = ET.parse(path).getroot()
        gate.require(all(int(suite.get(k, 0)) == 0 for k in ('failures', 'errors', 'skipped')), 'Retained unit failure/skip')
        units += int(suite.get('tests', 0))
    gate.require(units >= 95, 'Retained units are incomplete')
    expected = {(b, p) for b in gate.BACKENDS for p in gate.PROFILES}
    seen = set()
    for combination in record['combinations']:
        key = (combination['backend'], combination['profile'])
        gate.require(key in expected and key not in seen, 'Duplicate/missing local Windows combination')
        backend, profile = key
        seen.add(key)
        gate.require(len(combination['runs']) == 35, 'Retained Windows invocation set is incomplete')
        logs = {}
        identities = {}
        for run in combination['runs']:
            label = run['label']
            pair_bytes = gzip.decompress(gate.locked_file(run['original_pair_gzip'], root).read_bytes())
            gate.require(run['original_pair_name'] in accepted_pairs and hashlib.sha256(pair_bytes).hexdigest()
                         == pair_hashes.get(run['original_pair_name']), 'Original Windows process record changed')
            pair = json.loads(pair_bytes)
            original_run = next((r for r in pair['runs'] if r['label'] == run['original_label']), {})
            gate.require(pair.get('passed') is True and pair.get('running') is False
                         and original_run.get('exit_code') == run['exit_code'] == 0
                         and original_run.get('passed') is True
                         and original_run.get('saved_all_dimensions') is True
                         and original_run.get('log_sha256') == run['log']['sha256']
                         and original_run.get('required_tests_passed') == run['requirements'], 'Retained process summary differs from its original outcome')
            gate.require(label not in logs and run['exit_code'] == 0 and run['saved_all_dimensions'] is True,
                         'Retained process was failed/interrupted or duplicated')
            identity = gate.read(gate.locked_file(run['identity'], root))
            gate.require(identity == original_run.get('packaged_identity'), 'Retained input identity differs from original process')
            reference = label == 'reference-SETUP' and identity.get('upstream') is True
            # The upstream-only reference intentionally excludes the WWPG runtime.
            # Preserve its original input identity, as the CI reference collector does.
            runtime_hash = original_run['packaged_identity']['sha256'] if reference else hashes['runtime']
            runtime_name = 'wwpg-' + reuse['runtime_version'] + ('-upstream-reference-tests.jar' if reference else '.jar')
            gate.require(identity.get('artifact') == runtime_name
                         and identity.get('sha256') == runtime_hash
                         and identity.get('profile') == profile and identity.get('backend') == backend,
                         'Retained process used different runtime/profile/backend')
            namespaces = identity.get('namespaces', '').split(',')
            if any(n.startswith('wwpg_api') for n in namespaces):
                gate.require(all(identity.get('addon_artifacts', {}).get(k, {}).get('sha256') == hashes[k]
                                 for k in ('example', 'upstream_example')), 'Retained API dependencies differ')
            path = gate.locked_file(run['log'], root)
            text = checks.log(path, [])
            counts = re.findall(r'All (\d+) required tests passed', text)
            gate.require('All dimensions are saved' in text and int(counts[-1]) == run['requirements']
                         and run['requirements'] > 0, 'Retained process count/save differs')
            logs[label], identities[label] = text, identity

        def sequence(prefix, phases, namespaces, minimum=1):
            names = [prefix + '-setup'] + [prefix + '-cold-' + str(i) for i in range(1, len(phases))]
            for name, phase in zip(names, phases):
                gate.require(name in logs and identities[name]['phase'] == phase
                             and set(identities[name]['namespaces'].split(',')) == set(namespaces),
                             'Retained namespace/cold sequence differs: ' + name)
                count = int(re.findall(r'All (\d+) required tests passed', logs[name])[-1])
                gate.require(count >= minimum, 'Retained coverage shrank: ' + name)
            return names

        default = {'wwpg', 'wwpg_stationary', 'wwpg_design', 'wwpg_distribution', 'wwpg_wires', 'wwpg_bundle', 'wwpg_coupled'}
        if profile == 'PINOUT': default.add('wwpg_pinout')
        sequence('regression', ['SETUP'] + ['VERIFY'] * 3, default, 210 if profile == 'PINOUT' else 206)
        api = sequence('api', ['SETUP'] + ['VERIFY'] * 3, checks.API_NAMESPACES, 32)
        for label in api:
            text = logs[label]
            checks.cycles(text, 'API_CHUNK_CYCLE')
            checks.cycles(text, 'API_UPSTREAM_CHUNK_CYCLE')
            checks.nonconvergence(text)
            if label != 'api-setup': checks.cold_replacement(text)
            audits = re.findall(r'WWPG_TEST_SOLVER_AUDIT: independentCeeSolves=(\d+)[,;]\s*backend=(NATIVE|JAVA)[,;]\s*observedSubsteps=(\d+)', text)
            gate.require(audits and all(c == '0' and b == backend and int(s) > 0 for c, b, s in audits), 'Retained solver audit differs')
        sequence('api-pause', ['SETUP'], {'wwpg_api_pause'})
        checks.marker(logs['api-pause-setup'], 'API_PAUSE_PASSED', dict(frozenCallbacks='0', steppedCommits='1', resumed='true', pausedMeasurementsUnavailable='true'))
        for phase in ('SETUP', 'ARM', 'VERIFY'):
            name = 'api-pending-' + phase
            gate.require(name in logs and identities[name]['phase'] == phase
                         and identities[name]['namespaces'] == 'wwpg_api_pending_restart', 'Retained pending sequence differs')
        checks.pending_retirement(logs['api-pending-ARM'], logs['api-pending-VERIFY'])
        for name in ('yard', 'api-world'):
            namespace = 'wwpg_yard' if name == 'yard' else 'wwpg_api_world'
            sequence(name, ['SETUP'] + ['VERIFY'] * 3, {namespace})
            for i in (1, 2, 3):
                label = 'exported-' + ('yard' if name == 'yard' else 'api') + '-cold-' + str(i)
                gate.require(label in logs and identities[label]['phase'] == 'VERIFY'
                             and identities[label]['namespaces'] == namespace, 'Retained exported world reopen missing')
        for item in combination['cold_oracles']:
            gate.require(gate.locked_file(item, root).stat().st_size > 0, 'Retained cold-state oracle absent')
        gate.require({i['path'].rsplit('/', 1)[-1] for i in combination['cold_oracles']} >= {
            'extension-api-before-restart.nbt', 'upstream-api-before-restart.nbt',
            'extension-api-replacement-before-restart.nbt', 'extension-api-pending-before-restart.nbt'},
            'Retained cold-state coverage incomplete')
    gate.require(seen == expected, 'Retained Windows matrix incomplete')
