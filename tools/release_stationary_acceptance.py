"""Validate the combined Pinout yard without inventing separate BASE executions.

Windows may retain version-equivalent local outcomes; Linux uses completed CI.
Every selected run, cold reopen and real client retains its original identity.
"""
import gzip
import hashlib
import json
import re

POLICY = 'combined-pinout-v1'
SEQUENCE = [('source', 'SETUP')] + [('source', 'VERIFY')] * 3 + [('extracted', 'VERIFY')] * 3


def raw_json(descriptor, root, gate):
    path = gate.locked_file(descriptor, root)
    data = path.read_bytes()
    return json.loads(gzip.decompress(data) if path.suffix == '.gz' else data)


def world_files(descriptor, root, gate, version, runtime_hash):
    path = gate.locked_file(descriptor, root)
    with gate.checked_zip(path) as archive:
        names = archive.namelist()
        headers = [n for n in names if n.endswith('/WWPG_MANIFEST.json')]
        gate.require(len(headers) == 1, 'Combined yard needs one inventory')
        prefix = headers[0].removesuffix('WWPG_MANIFEST.json')
        metadata = json.loads(archive.read(headers[0]))
        gate.require(metadata.get('profile') == 'PINOUT' and metadata.get('target') == version
            and metadata.get('jar', {}).get('sha256') == runtime_hash,
            'Combined yard profile/version/runtime differs')
        gate.require(metadata.get('support_baseline') == 'release/0.1.0-beta.5/content-matrix.json',
            'Combined yard omits inherited equipment coverage')
        files = {n.removeprefix(prefix): archive.read(n) for n in names}
        gate.require('level.dat' in files, 'Combined yard lacks the actual save')
        inventory = metadata.get('files', {})
        gate.require(isinstance(inventory, dict) and inventory
            and set(inventory) == set(files) - {'WWPG_MANIFEST.json', 'WWPG_README.txt'},
            'Combined yard inventory is incomplete')
        for name, item in inventory.items():
            data = files.get(name)
            gate.require(data is not None and len(data) == item['bytes']
                and hashlib.sha256(data).hexdigest() == item['sha256'], 'Combined yard inventory changed')
        return {n: data for n, data in files.items() if n not in {'WWPG_MANIFEST.json', 'WWPG_README.txt'}}


def validate(manifest, root, gate):
    from release_acceptance_reuse import runtime_equivalent
    gate.require(manifest.get('stationary_yard_policy') == POLICY,
        'Unknown combined-yard policy')
    gate.require('base_world' not in manifest and 'pinout_world' not in manifest,
        'Combined yard cannot also claim separate yard editions')
    record = raw_json(manifest.get('stationary_yard_acceptance', {}), root, gate)
    gate.require(record.get('schema') == 1 and record.get('policy') == POLICY,
        'Missing combined-yard acceptance')
    current_runtime = gate.locked_file(manifest['artifacts']['runtime'], root)
    current_hash = manifest['artifacts']['runtime']['sha256']
    published_files = world_files(manifest['stationary_yard'], root, gate,
        manifest['wwpg_version'], current_hash)
    ci = raw_json(manifest['ci'], root, gate)
    ci_proofs = {j['proof']['sha256'] for j in ci['jobs']}
    expected = {(p, b) for p in gate.PLATFORMS for b in gate.BACKENDS}
    seen = set()
    for item in record.get('runs', []):
        key = (item.get('platform'), item.get('backend'))
        gate.require(key in expected and key not in seen and item.get('executed_profile') == 'PINOUT',
            'Missing/duplicate combined-yard platform/backend or invented BASE execution')
        seen.add(key)
        gate.require(re.fullmatch(r'[0-9a-f]{40}', item.get('source_sha', '')),
            'Original combined-yard source is missing')
        tested = gate.locked_file(item['tested_runtime'], root)
        tested_hash = item['tested_runtime']['sha256']
        runtime_equivalent(tested, current_runtime, item['runtime_version'], manifest['wwpg_version'])
        world_files(item['world'], root, gate, item['runtime_version'], tested_hash)
        extraction = raw_json(item['extraction'], root, gate)
        gate.require(extraction.get('sha256') == item['world']['sha256']
            and extraction.get('profile') == 'PINOUT'
            and extraction.get('jar', {}).get('sha256') == tested_hash, 'Exact yard extraction differs')
        ci_backed = key[0] == 'ubuntu-24.04'
        if ci_backed:
            gate.require(item.get('ci_proof', {}).get('sha256') in ci_proofs,
                'Linux yard is not bound to an accepted CI job')
            job = raw_json(item['ci_proof'], root, gate)
            gate.require(job.get('passed') is True and job.get('completed') is True
                and job.get('platform') == key[0] and job.get('backend') == key[1]
                and job.get('profile') == 'PINOUT' and job.get('source_sha') == item['source_sha']
                and job.get('artifact_hashes', {}).get('runtime') == tested_hash,
                'Linux yard job identity differs')
            ci_files = {d['sha256'] for d in job['evidence_files']}
        else:
            campaign = raw_json(item['original_campaign'], root, gate)
            gate.require(campaign.get('passed') is True and campaign.get('running') is False
                and campaign.get('profile') == 'PINOUT' and campaign.get('source_sha') == item['source_sha']
                and campaign.get('artifact', {}).get('sha256') == tested_hash,
                'Retained yard campaign is incomplete or belongs to another runtime')
        rows = item.get('server_runs', [])
        gate.require([(r.get('stage'), r.get('phase')) for r in rows] == SEQUENCE,
            'Combined yard needs setup, three cold restarts and three exact-archive reopens')
        execution_ids = set()
        for row in rows:
            proof = raw_json(row['manifest'], root, gate)
            gate.require(bool(proof.get('completed')) and proof.get('required_tests_passed', 0) > 0
                and proof.get('sha256') == tested_hash and proof.get('profile') == 'PINOUT'
                and proof.get('backend') == key[1] and proof.get('phase') == row['phase']
                and proof.get('namespaces') == 'wwpg_yard', 'Yard process identity/completion differs')
            execution_ids.add(row['manifest']['sha256'])
            if ci_backed:
                gate.require(row['manifest']['sha256'] in ci_files, 'Yard process was not in the accepted CI upload')
            else:
                pair = raw_json(row['original_pair'], root, gate)
                matches = [r for r in pair.get('runs', []) if r.get('label') == row['original_label']]
                gate.require(pair.get('passed') is True and pair.get('running') is False and len(matches) == 1,
                    'Retained yard process has no unique completed original outcome')
                outcome = matches[0]
                gate.require(outcome.get('exit_code') == 0 and outcome.get('passed') is True
                    and outcome.get('saved_all_dimensions') is True
                    and outcome.get('required_tests_passed') == proof['required_tests_passed']
                    and outcome.get('log_sha256') == row['log']['sha256']
                    and outcome.get('completed_proof', {}).get('sha256') == row['manifest']['sha256'],
                    'Retained yard exit/count/save/input differs from original evidence')
            if row.get('log'):
                text = gate.locked_file(row['log'], root).read_text(encoding='utf-8', errors='replace')
                counts = re.findall(r'All (\d+) required tests passed', text)
                gate.require(counts and int(counts[-1]) == proof['required_tests_passed']
                    and 'All dimensions are saved' in text and not re.search(r'[1-9][0-9]* required tests failed', text),
                    'Yard log lacks matching test completion and saved shutdown')
                if ci_backed:
                    gate.require(row['log']['sha256'] in ci_files, 'Yard cold log is absent from CI')
            else:
                gate.require(ci_backed and row['phase'] == 'SETUP', 'Retained yard is missing its process log')
        gate.require(len(execution_ids) == 7, 'Repeated yard manifest cannot replace distinct executions')
        if not ci_backed:
            client_world = item['client_world']
            gate.require(world_files(client_world, root, gate, item['runtime_version'], tested_hash) == published_files,
                'Published combined yard differs from the actually played save')
            for mode in ('graphical', 'multiplayer'):
                client = raw_json(item[mode], root, gate)
                gate.require(client.get('passed') is True and client.get('profile') == 'PINOUT'
                    and client.get('backend') == key[1] and client.get('jar', {}).get('sha256') == tested_hash
                    and client.get('exact_archive_sha256', client.get('archive_sha256')) == client_world['sha256'],
                    'Combined yard client used another runtime/save/profile')
                if mode == 'graphical':
                    gate.require(client.get('view_count', 0) >= 150 and len(set(client.get('views', []))) >= 150
                        and client.get('client_server_gauge_comparisons', 0) >= 135,
                        'Combined yard graphical coverage is incomplete')
                else:
                    gate.require(len(set(client.get('real_clients', []))) >= 2
                        and set(client.get('processes', {})) == {'server', 'client-a', 'client-b'}
                        and all(p.get('exit_code') == 0 for p in client['processes'].values()),
                        'Combined yard needs two real clients and saved clean process exits')
            gate.require(bool(item.get('client_evidence')), 'Combined yard client media/logs were not retained')
            media_hashes = {d['sha256'] for d in item['client_evidence']}
            for descriptor in item['client_evidence']:
                gate.locked_file(descriptor, root)
            for mode in ('graphical', 'multiplayer'):
                pending = [raw_json(item[mode], root, gate)]
                while pending:
                    value = pending.pop()
                    if isinstance(value, dict):
                        if 'sha256' in value and ('file' in value or 'bytes' in value):
                            gate.require(value['sha256'] in media_hashes or value['sha256'] == tested_hash,
                                'Combined yard client proof references omitted media')
                        pending.extend(value.values())
                    elif isinstance(value, list):
                        pending.extend(value)
    gate.require(seen == expected, 'Combined yard is missing a platform/backend')
