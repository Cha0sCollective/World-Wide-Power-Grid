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
        if actual.get('producer'):
            producer=read(locked(actual['producer'],base))
            require(producer.get('policy')=='fixed-platform-v1' and producer['archive']['sha256']==actual['archive']['sha256'],
                    'Corrected export producer identity differs')
            from recheck_workshop_spawn import validate_correction
            validate_correction(locked(producer['source'],base),archive)
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
        require(report.get('status') in ('passed','reused') and not report.get('not_run'), 'Incomplete acceptance attempt')
        if report['status']=='reused':
            original=read(locked(report['execution_report'],base))
            require(report.get('reuse_reason') and report.get('original_status')==original.get('status'), 'Missing retained execution identity')
            for key in ('schema','policy','suite','source_sha','artifact_hashes','catalog','inputs'):
                require(report.get(key)==original.get(key),'Retained execution changed: '+key)
            require(bool(report['jobs']),'Empty retained coverage')
            for job in report['jobs']:
                require(job.get('status')=='passed' and job in original['jobs'],'Retained job did not actually pass')
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


def continue_headless(previous, inputs_path, output, reason, preparation, environment=None, fresh_if_unavailable=False, plan_only=False):
    """Reuse complete passing lifecycle groups; run incomplete groups afresh.

    Failed attempts stay failed. A retained view points to their immutable
    observations and cannot manufacture a passing process or split a cold
    sequence across incompatible world directories.
    """
    from test_suites import execute, frozen_inputs
    frozen=frozen_inputs(inputs_path)
    previous=Path(previous).resolve();base=previous.parent
    available=previous.is_file()
    if available:old=read(previous)
    else:
        require(fresh_if_unavailable and environment,'Original report unavailable; explicit selected-environment rerun required')
        old=dict(suite='full-headless',policy='separated-v2',source_sha=frozen['source_sha'],
                 artifact_hashes={r:d['sha256'] for r,d in frozen['artifacts'].items()},jobs=[])
    require(old.get('suite')=='full-headless' and old.get('policy')=='separated-v2','Not a versioned headless execution')
    require(old['source_sha']==frozen['source_sha'] and all(old['artifact_hashes'][r]==frozen['artifacts'][r]['sha256'] for r in ROLES),'Continuation changed tested artifacts/source')
    import subprocess
    protected=['src','gradle.properties','build.gradle','tools/native','release/artifacts.json','release/optional-artifacts.json']
    changed=subprocess.check_output(['git','diff','--name-only',frozen['source_sha'],'HEAD','--',*protected],cwd=ROOT,text=True)
    dirty=subprocess.check_output(['git','diff','--name-only','--',*protected],cwd=ROOT,text=True)
    require(not changed.strip() and not dirty.strip(),'Frozen continuation requires unchanged runtime/build inputs')
    environments={(j['platform'],j['loader'],j['backend'],j['profile']) for j in old['jobs'] if 'backend' in j}
    if not available:environments={tuple(environment.split('/'))}
    require(len(environments)==1,'Continue one isolated environment at a time')
    platform,loader,backend,profile=next(iter(environments))
    if environment:require(tuple(environment.split('/'))==(platform,loader,backend,profile),'Continuation environment differs')
    expected=plan('full-headless',backends=[backend],profiles=[profile],loaders=[loader],platforms=[platform])
    groups={}
    for job in expected:groups.setdefault(job.get('group',job.get('operation')),[]).append(job)
    actual={j['id']:j for j in old['jobs']};retained=[];remaining=[]
    for members in groups.values():
        try:
            for selected in members:
                require(selected['id'] in actual,'Unexecuted lifecycle')
                validate_job(actual[selected['id']],selected,old,base)
        except (ValueError,KeyError,TypeError):remaining.extend(members)
        else:retained.extend(actual[j['id']] for j in members)
    selected_ids={j['id'] for j in remaining};selected=[j for j in expected if j['id'] in selected_ids]
    if plan_only:return dict(retained_jobs=[j['id'] for j in retained],jobs=selected,original_report_available=available)
    output=Path(output).resolve();output.mkdir(parents=True,exist_ok=False)
    def relative(path):
        item=descriptor(path);item['path']=Path(path).relative_to(ROOT).as_posix();return item
    reports=[]
    if retained:
        view=dict(old,status='reused',original_status=old['status'],execution_report=relative(previous),
                  reuse_reason=reason,jobs=retained,not_run=[],complete_suite=False)
        write(output/'retained.json',view);reports.append(relative(output/'retained.json'))
    # Keep the catalog's original ordering and lifecycle dependencies.
    status='failed'
    try:
        if selected:execute(selected,inputs_path,output/'execution','full-headless',reason,preparation)
        status='passed'
    finally:
        if (output/'execution/result.json').is_file():reports.append(relative(output/'execution/result.json'))
        write(output/'index.json',dict(schema=2,policy='separated-v2',kind='headless-continuation',status=status,
            reports=reports,retained_jobs=len(retained),executed_jobs=len(selected),reason=reason,
            original_report_available=available))


WORLD_KEYS={'yard/PINOUT':'stationary_yard','workshop/BASE':'base_workshop_world','workshop/PINOUT':'pinout_workshop_world'}


def accepted_world_hashes(manifest, root, gate, cohorts):
    """Version promotions retain observed archives; only their labels may differ."""
    reuse=manifest.get('world_metadata_reuse')
    if reuse is None:return {key:manifest[field]['sha256'] for key,field in WORLD_KEYS.items()}
    require(reuse.get('schema')==1 and reuse.get('scope')=='runtime_version_only'
            and set(reuse.get('worlds',{}))==set(WORLD_KEYS.values()),'World promotion needs all original archives and explicit scope')
    for cohort in cohorts:
        accepted=cohort.get('reuse',{})
        require(accepted.get('version')==reuse.get('version') and accepted.get('source_sha')==reuse.get('source_sha')
                and accepted.get('runtime',{}).get('sha256')==reuse.get('runtime',{}).get('sha256'),
                'World promotion differs from original runtime acceptance')
    from release_acceptance_reuse import world_version_equivalent
    old_runtime=reuse['runtime'];gate.locked_file(old_runtime,root)
    for field in WORLD_KEYS.values():
        world_version_equivalent(gate.locked_file(reuse['worlds'][field],root),gate.locked_file(manifest[field],root),
            reuse['version'],manifest['wwpg_version'],old_runtime,manifest['artifacts']['runtime'])
    return {key:reuse['worlds'][field]['sha256'] for key,field in WORLD_KEYS.items()}


def validate_release(manifest, root, gate):
    """New release contract: headless completion cannot imply client completion."""
    require(manifest.get('acceptance_policy') == 'separated-v2', 'Unknown new acceptance policy')
    hashes = {r: manifest['artifacts'][r]['sha256'] for r in ROLES}
    require(all(isinstance(manifest.get(key),dict) for key in ('headless_acceptance','graphical_acceptance')),
            'Independent headless and graphical acceptance records are required')
    cohorts=[]
    for lane, key in [('full-headless', 'headless_acceptance'), ('graphical', 'graphical_acceptance')]:
        cohort = read(gate.locked_file(manifest[key], root))
        cohorts.append(cohort)
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
    # Bind media to exact archives or proven label-only derivatives, never an
    # unrelated accepted world with the same profile.
    worlds = accepted_world_hashes(manifest, root, gate, cohorts)
    import gzip
    import zipfile
    from export_api_workshop import fixed_spawn_level
    for key in ('base_workshop_world','pinout_workshop_world'):
        with zipfile.ZipFile(gate.locked_file(manifest[key],root)) as archive:
            headers=[n for n in archive.namelist() if n.endswith('/WWPG_MANIFEST.json')]
            require(len(headers)==1,'Workshop inventory is ambiguous')
            header=json.loads(archive.read(headers[0]))
            require(header.get('spawn_policy')=='fixed-platform-v1','Workshop lacks fixed platform spawn policy')
            raw=archive.read(headers[0].removesuffix('WWPG_MANIFEST.json')+'level.dat')
            require(gzip.decompress(raw)==gzip.decompress(fixed_spawn_level(raw)),'Workshop still randomizes spawn beyond its platform')
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
