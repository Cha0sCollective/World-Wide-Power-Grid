"""Strict, versioned coverage gates for the separated acceptance lanes.

The original release validators remain responsible for historical manifests.
No command here launches acceptance or converts incomplete evidence to a pass.
"""
import argparse
import json
from pathlib import Path
import re
from test_suites import ROOT, CATALOG, ROLES, catalog, descriptor, plan, read, sha, write


def require(value, message):
    if not value: raise ValueError(message)


def locked(item, base):
    path = (Path(base) / item['path']).resolve()
    if not path.is_file() and not Path(item['path']).is_absolute():path=(ROOT/item['path']).resolve()
    require(path.is_file() and sha(path) == item['sha256'] and path.stat().st_size == item['bytes'],
            'Missing/changed evidence: '+str(path))
    return path


def validate_job(actual, expected, inputs, base):
    for key, value in expected.items():
        require(actual.get(key) == value, 'Wrong job selection: '+expected['id']+'/'+key)
    require(actual.get('status') == 'passed' and actual.get('exit_code') == 0, 'Failed/incomplete job: '+expected['id'])
    text = locked(actual['log'], base).read_text(encoding='utf-8', errors='replace')
    if expected.get('operation') == 'prepare':
        proof = read(locked(actual['proof'], base))
        require(proof.get('passed') is True and proof.get('source_sha') == inputs['source_sha'], 'Platform preparation incomplete')
        require(proof.get('platform')==expected['platform'],'Preparation ran on another OS')
        require('BUILD SUCCESSFUL' in text and 'BUILD FAILED' not in text, 'Actual platform compilation/unit checks missing')
        require(bool(proof.get('junit')), 'Unit reports missing')
        import xml.etree.ElementTree as ET
        count=0
        for item in proof['junit']:
            suite=ET.parse(locked(item,base)).getroot()
            require(int(suite.get('failures','0'))==int(suite.get('errors','0'))==0,'Unit check failed')
            count+=int(suite.get('tests','0'))
        require(count>0,'No unit checks executed')
        require(proof.get('sdk', {}).get('passed') is True, 'Independent SDK builds missing')
        for role in ('example', 'upstream_example'):
            sdk = read(locked(proof['sdk']['proofs'][role], Path(__file__).resolve().parents[1]))
            require(sdk.get('build_from_extracted_sdk') and sdk.get('uses_wwpg_project_classes') is False
                    and sdk['compiled_output']['sha256'] == inputs['artifact_hashes'][role], 'SDK source/output differs')
            dependency = read(locked(sdk['dependency_proof'], Path(__file__).resolve().parents[1]))
            expected_dependencies={'api':inputs['artifact_hashes']['api']}
            if role=='upstream_example':expected_dependencies['cee_api']=inputs['artifact_hashes']['cee_api']
            require(dependency.get('wwpg_project_dependencies') == [] and dependency.get('api_dependencies')==expected_dependencies,
                    'SDK used project classes or another API')
            locked(sdk['compiled_output'], Path(__file__).resolve().parents[1]); locked(sdk['build_log'], Path(__file__).resolve().parents[1])
        return
    if expected.get('operation') == 'starter':
        proof = read(locked(actual['proof'], base))
        require(proof.get('passed') and proof['runtime_sha256'] == inputs['artifact_hashes']['runtime']
                and proof['backend'] == expected['backend'] and proof['platform'] == expected['platform'], 'Starter used another configuration')
        require({'controls','native_tools','unavailable','cold_restart_before_solve','actual_chunk_reload','replacement','dedicated_server'} <= set(proof['checks']), 'Missing starter lifecycle')
        build = read(locked(actual['build'], base))
        require(build.get('passed') is True and build.get('source_sha')==inputs['source_sha']
                and build.get('uses_wwpg_project_classes') is False
                and build.get('compiled_against_api_sha256')==inputs['artifact_hashes']['api']
                and build['compiled_output']['sha256']==proof.get('starter_sha256'), 'Starter build identity differs')
        locked(build['compiled_output'], ROOT); locked(build['dependency_proof'], ROOT)
        for item in proof['evidence']: locked(item, Path(__file__).resolve().parents[1])
        return
    if expected['lane'] == 'graphical':
        from graphical_suite import validate_observation
        observation = read(locked(actual['proof'], base))
        validate_observation(observation, expected, inputs, Path(actual['proof']['path']).resolve().parent)
        return
    if expected.get('operation') == 'export':
        archive = locked(actual['archive'], base)
        from zipfile import ZipFile
        with ZipFile(archive) as zipped:
            headers = [n for n in zipped.namelist() if n.endswith('/WWPG_MANIFEST.json')]
            require(len(headers) == 1, 'World inventory missing')
            header = json.loads(zipped.read(headers[0]))
            require(header['jar']['sha256'] == inputs['artifact_hashes']['runtime'] and header['profile'] == expected['profile'], 'Export artifact/profile differs')
        return
    proof = read(locked(actual['proof'], base))
    require(proof.get('completed') and proof.get('required_tests_passed', 0) > 0, 'Process did not complete its tests')
    require('GAME TESTS COMPLETE' in text and re.search(r'All [1-9][0-9]* required tests passed', text), 'Missing actual completed GameTest log')
    require('All dimensions are saved' in text, 'World did not finish saving')
    require(f'WWPG_TEST_BACKEND_CONFIG: requested={expected["backend"]}, configured={expected["backend"]}' in text,'Actual configured solver differs')
    require(proof.get('profile') == expected['profile'] and proof.get('backend') == expected['backend'], 'Actual solver/profile differs')
    role = 'reference' if expected.get('reference') == 'upstream' else 'runtime'
    require(proof.get('sha256') == inputs['artifact_hashes'][role], 'Actual loaded artifact differs')
    if expected.get('operation') != 'reference':
        require(proof.get('phase') == expected['phase'] and proof.get('neoforge') == expected['loader']
                and set(proof.get('namespaces', '').split(',')) == set(expected['namespaces']), 'Lifecycle/loader/namespace differs')
        if expected['addon']:
            require(all(proof.get('addon_artifacts', {}).get(r, {}).get('sha256') == inputs['artifact_hashes'][r]
                        for r in ('example', 'upstream_example')), 'Actual addon jar differs')
    else:
        require(proof.get('upstream')==(expected['reference']=='upstream'),'Wrong reference mode')
        require(proof.get('neoforge')==expected['loader'], 'Reference loader differs')


def validate_reports(paths, lane, source_sha, hashes, complete=True):
    expected = {j['id']: j for j in plan(lane)}
    seen = set(); exports={}; reopens=[]
    for path in paths:
        report = read(path); base = Path(path).resolve().parent
        allowed={lane}|({'graphical-gallery'} if lane=='graphical' else set())
        require(report.get('schema') == 2 and report.get('policy') == 'separated-v2'
                and report.get('suite') in allowed, 'Acceptance policy/lane differs')
        require(report.get('status') == 'passed' and not report.get('not_run'), 'Incomplete acceptance attempt')
        require(report.get('source_sha') == source_sha, 'Acceptance source differs')
        require(all(report.get('artifact_hashes', {}).get(r) == hashes[r] for r in ROLES), 'Acceptance artifacts differ')
        previous_catalog=read(locked(report['catalog'],base))
        require(previous_catalog.get('schema')==2 and previous_catalog.get('policy')=='separated-v2','Coverage policy differs')
        # Headless jobs below must exactly satisfy the current namespace,
        # environment and lifecycle plan. Unrelated catalog edits do not erase
        # otherwise identical passing client evidence.
        input_path=locked(report['inputs'],base);frozen=read(input_path)
        require(frozen.get('schema')==2 and frozen.get('policy')=='separated-v2' and frozen.get('source_sha')==source_sha,
                'Frozen campaign identity differs')
        for role in ROLES:
            require(frozen['artifacts'][role]['sha256']==hashes[role],'Frozen campaign artifact differs')
            locked(frozen['artifacts'][role],input_path.parent)
        if any(j.get('reference')=='upstream' for j in report['jobs']):
            require(frozen['artifacts']['reference']['sha256']==report['artifact_hashes'].get('reference'), 'Reference input identity differs')
            locked(frozen['artifacts']['reference'],input_path.parent)
        for job in report['jobs']:
            identity = job['id']
            require(identity in expected and identity not in seen, 'Unknown/duplicate coverage: '+identity)
            if lane=='graphical':
                current=catalog();name=job['scenario']
                require(previous_catalog['graphical'].get(name)==current['graphical'][name], 'Graphical scenario contract differs')
                if name=='yard-integrated' and job['backend']=='NATIVE':require(previous_catalog['yard_views']==current['yard_views'],'Selected yard views differ')
                if name=='workshop-integrated' and job['backend']=='NATIVE':require(previous_catalog['workshop_views']==current['workshop_views'],'Selected Workshop views differ')
            selected=expected[identity]
            if report['suite']=='graphical-gallery':selected=dict(selected,gallery=True)
            validate_job(job, selected, report, base); seen.add(identity)
            if job.get('operation') == 'export':exports[identity.removesuffix('/export')] = job['archive']['sha256']
            if job.get('group', '').endswith('-extracted'):
                extraction=read(locked(job['extraction'],base))
                prefix=identity.rsplit('/',1)[0]
                require(extraction['jar']['sha256']==hashes['runtime'],'Reopened another runtime world')
                reopens.append((prefix,extraction['sha256']))
    for prefix,archive in reopens:require(exports.get(prefix)==archive,'Exact archive reopen lacks its matching export')
    if complete: require(seen == expected.keys(), 'Missing '+lane+' coverage: '+', '.join(sorted(expected.keys()-seen)))
    return seen


def validate_release(manifest, root, gate):
    """New release contract: headless completion cannot imply client completion."""
    require(manifest.get('acceptance_policy') == 'separated-v2', 'Unknown new acceptance policy')
    hashes = {r: manifest['artifacts'][r]['sha256'] for r in ROLES}
    require(all(isinstance(manifest.get(key),dict) for key in ('headless_acceptance','graphical_acceptance')),
            'Independent headless and graphical acceptance records are required')
    for lane, key in [('full-headless', 'headless_acceptance'), ('graphical', 'graphical_acceptance')]:
        cohort = read(gate.locked_file(manifest[key], root))
        require(cohort.get('schema') == 2 and cohort.get('policy') == 'separated-v2' and cohort.get('suite') == lane,
                'Wrong independent release gate')
        paths = [gate.locked_file(d, root) for d in cohort['reports']]
        accepted_source, accepted_hashes = manifest['source_sha'], hashes
        if 'reuse' in cohort:
            reuse = cohort['reuse']
            require(reuse.get('scope') == 'runtime_version_only', 'Unsupported reuse')
            from release_acceptance_reuse import runtime_equivalent
            runtime_equivalent(gate.locked_file(reuse['runtime'], root), gate.locked_file(manifest['artifacts']['runtime'], root),
                              reuse['version'], manifest['wwpg_version'])
            accepted_source = reuse['source_sha']; accepted_hashes = dict(hashes, runtime=reuse['runtime']['sha256'])
            require(cohort.get('status') == 'reused', 'Reuse must be labelled separately')
        else: require(cohort.get('status') == 'passed', 'Incomplete release gate')
        validate_reports(paths, lane, accepted_source, accepted_hashes)
    # Bind the media to the archives that will actually ship, not merely another
    # accepted world with the same profile.
    worlds = {'yard/PINOUT': manifest['stationary_yard']['sha256'],
              'workshop/BASE': manifest['base_workshop_world']['sha256'],
              'workshop/PINOUT': manifest['pinout_workshop_world']['sha256']}
    clients = read(gate.locked_file(manifest['graphical_acceptance'], root))
    headless=read(gate.locked_file(manifest['headless_acceptance'],root))
    exports={job['archive']['sha256'] for entry in headless['reports'] for job in read(gate.locked_file(entry,root))['jobs'] if job.get('operation')=='export'}
    require(set(worlds.values())<=exports,'A published world has no headless export/reopen evidence')
    for entry in clients['reports']:
        for job in read(gate.locked_file(entry, root))['jobs']:
            observation = read(gate.locked_file(job['proof'], root))
            if job['scenario'] != 'starter':
                require(observation['world']['sha256'] == worlds[job['world']+'/'+job['profile']], 'Client did not test the published archive')
            else:
                starter = read(gate.locked_file(manifest['authoring_starter']['proof'], root))
                require(observation['starter_sha256'] == starter['compiled_output']['sha256'], 'Client used a different SDK starter')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lane', choices=['full-headless', 'graphical'], required=True)
    parser.add_argument('--report', type=Path, action='append', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    first = read(args.report[0])
    validate_reports(args.report, args.lane, first['source_sha'], first['artifact_hashes'])
    reports=[]
    for path in args.report:
        item=descriptor(path)
        item['path']=Path(path).resolve().relative_to(ROOT).as_posix()
        reports.append(item)
    write(args.output, dict(schema=2, policy='separated-v2', suite=args.lane, status='passed',reports=reports))


if __name__ == '__main__': main()
