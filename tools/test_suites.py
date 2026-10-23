"""Explicit WWPG test lanes. Planning is read-only; execution is always opt-in.

Examples: test_suites.py audit; test_suites.py plan full-headless;
test_suites.py run focused --group safety --backend JAVA --inputs frozen.json --output fresh
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
CATALOG = Path(__file__).with_name('test-suites.json')
ROLES = ('runtime', 'api', 'cee_api', 'example', 'upstream_example')


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write(path, value):
    path = Path(path)
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2); stream.write('\n')


def descriptor(path):
    path = Path(path).resolve()
    return dict(path=str(path), sha256=sha(path), bytes=path.stat().st_size)


def snapshot_catalog(output):
    target = Path(output)/'catalog.json'
    if target.exists(): raise ValueError('Preserve the original catalog snapshot')
    shutil.copyfile(CATALOG, target)
    return descriptor(target)


def catalog():
    value = read(CATALOG)
    if value['schema'] != 2 or value['policy'] != 'separated-v2':
        raise ValueError('Unknown test catalog policy')
    return value


def audit(root=ROOT):
    spec = catalog()
    membership = {}
    for group, value in spec['groups'].items():
        for name in value['namespaces']+value.get('setup_namespaces',[]):
            if name in membership: raise ValueError('Duplicate namespace: ' + name)
            membership[name] = group
    if set(membership)&spec['special_namespaces'].keys():raise ValueError('Namespace classified as both ordinary and special')
    membership.update(spec['special_namespaces'])
    roots=['src/main/java','src/gameTest/java','examples/extension-addon/src','examples/cee-addon/src',
           'tools/client_acceptance/src','tools/documentation_acceptance/src']
    discovered=set()
    for directory in roots:
        for path in (root/directory).rglob('*.java'):
            text=path.read_text(encoding='utf-8')
            names=re.findall(r'@GameTestHolder\s*\(\s*(?:value\s*=\s*)?"([^"]+)"\s*\)',text)
            if '@GameTestHolder' in text and not names:raise ValueError('Use a literal cataloged GameTest namespace: '+str(path))
            discovered.update(names)
    missing = discovered - membership.keys()
    if missing: raise ValueError('Unclassified GameTest namespaces: ' + ', '.join(sorted(missing)))
    return dict(policy=spec['policy'], namespaces=len(discovered), classified=sorted(discovered))


def plan(lane, groups=None, backends=None, profiles=None, loaders=None, platforms=None):
    spec = catalog()
    backends = backends or spec['backends']; profiles = profiles or spec['profiles']
    loaders = loaders or (spec['loaders'] if lane == 'full-headless' else [spec['loaders'][-1]])
    platforms = platforms or (['windows-latest'] if lane.startswith('graphical') else [host_platform()] if lane != 'full-headless' else spec['platforms'])
    if not set(backends) <= set(spec['backends']) or not set(profiles) <= set(spec['profiles']) \
            or not set(loaders) <= set(spec['loaders']) or not set(platforms) <= set(spec['platforms']):
        raise ValueError('Unknown test environment')
    jobs = []
    if lane in ('graphical', 'graphical-gallery'):
        if groups or platforms!=['windows-latest'] or loaders!=[spec['loaders'][-1]]:
            raise ValueError('Graphical scenarios require local Windows and the catalog client loader; use --case for a subset')
        for name, case in spec['graphical'].items():
            for backend in backends:
                for profile in profiles:
                    if profile not in case['profiles']: continue
                    checks = case['checks'] + case.get('native_checks' if backend == 'NATIVE' else 'java_checks', [])
                    jobs.append(dict(id=f'{name}/{backend}/{profile}', scenario=name, lane='graphical',
                        backend=backend, profile=profile, platform='windows-latest', loader=spec['loaders'][-1],
                        world=case['world'], tcp=case.get('tcp', False), checks=checks,
                        gallery=lane == 'graphical-gallery'))
        return jobs
    if lane not in ('focused', 'full-headless'): raise ValueError('Unknown lane')
    if lane=='full-headless' and groups:raise ValueError('Full headless uses the complete catalog; use focused --group or --case')
    if lane == 'focused' and not groups: raise ValueError('Focused execution requires --group')
    selected = groups or list(spec['groups'])
    if not set(selected) <= set(spec['groups']): raise ValueError('Unknown headless group')
    for target in platforms:
        if lane == 'full-headless' and 'NATIVE' in backends and 'BASE' in profiles and spec['loaders'][0] in loaders:
            jobs.append(dict(id=f'{target}/prepare', lane='headless', operation='prepare', platform=target))
        for loader in loaders:
            # The second loader retains its existing compatibility matrix, not a
            # second copy of every world export/reference/SDK check.
            compatibility = lane == 'full-headless' and loader != spec['loaders'][0]
            for backend in backends:
                for profile in profiles:
                    for group in selected:
                        cfg = spec['groups'][group]
                        if compatibility and group not in ('regression', 'pinout', 'platform', 'api', 'pause', 'pending'): continue
                        if profile not in cfg.get('profiles', profiles) or backend not in cfg.get('backends', backends): continue
                        if cfg.get('merge_into') in selected:continue
                        names = list(cfg['namespaces'])
                        for part in selected:
                            merged=spec['groups'][part]
                            if merged.get('merge_into')==group and profile in merged.get('profiles',spec['profiles']) and backend in merged.get('backends',spec['backends']):
                                names+=merged['namespaces']
                        phases = cfg['phases'] if lane == 'full-headless' else [cfg['phases'][0]]
                        for number, phase in enumerate(phases):
                            jobs.append(dict(id=f'{target}/{loader}/{backend}/{profile}/{group}/{number}-{phase}',
                                lane='headless', platform=target, loader=loader, backend=backend, profile=profile,
                                group=group, phase=phase, namespaces=names+cfg.get('setup_namespaces',[]) if phase=='SETUP' else names, addon=cfg.get('addon', False)
                                    or group in ('safety', 'native-guard')))
                        if lane == 'full-headless' and cfg.get('export'):
                            jobs.append(dict(id=f'{target}/{loader}/{backend}/{profile}/{group}/export',
                                lane='headless', operation='export', platform=target, loader=loader,
                                backend=backend, profile=profile, group=group, world=cfg['export']))
                            for number in range(cfg['reopens']):
                                jobs.append(dict(id=f'{target}/{loader}/{backend}/{profile}/{group}/reopen-{number}',
                                    lane='headless', platform=target, loader=loader, backend=backend, profile=profile,
                                    group=group+'-extracted', phase='VERIFY', namespaces=names, addon=cfg.get('addon', False)))
                    if lane == 'full-headless' and not compatibility and profile == 'BASE':
                        jobs.append(dict(id=f'{target}/{backend}/starter', lane='headless', operation='starter',
                            platform=target, loader=spec['loaders'][-1], backend=backend, profile=profile))
                        for reference in ('upstream', 'compatibility'):
                            jobs.append(dict(id=f'{target}/{loader}/{backend}/reference-{reference}', lane='headless',
                                operation='reference', reference=reference, platform=target, loader=loader,
                                backend=backend, profile=profile))
    return jobs


def host_platform():
    return 'windows-latest' if os.name == 'nt' else 'ubuntu-24.04'


def frozen_inputs(path):
    path = Path(path).resolve()
    record = read(path)
    if record.get('schema') != 2 or record.get('policy') != 'separated-v2' \
            or not re.fullmatch('[0-9a-f]{40}', record.get('source_sha', '')):
        raise ValueError('Missing frozen source/policy')
    if not set(ROLES) <= record['artifacts'].keys(): raise ValueError('Incomplete frozen artifacts')
    for item in record['artifacts'].values():
        item['path'] = str((path.parent / item['path']).resolve())
        if sha(item['path']) != item['sha256'] or Path(item['path']).stat().st_size != item['bytes']:
            raise ValueError('Frozen artifact changed: ' + item['path'])
    for item in record.get('worlds', {}).values():
        item['path'] = str((path.parent/item['path']).resolve())
        if sha(item['path']) != item['sha256']:raise ValueError('Frozen world changed')
    if 'starter' in record:record['starter']['path']=str((path.parent/record['starter']['path']).resolve())
    return record


def freeze(output, source=None):
    """Freeze already built inputs. This never builds or runs acceptance."""
    output = Path(output).resolve(); output.mkdir(parents=True, exist_ok=False)
    props = dict(line.split('=', 1) for line in (ROOT/'gradle.properties').read_text().splitlines() if '=' in line)
    version, api = props['mod_version'], props['api_version']
    paths = dict(runtime=f'build/libs/wwpg-{version}.jar', api=f'build/libs/wwpg-api-{api}.jar',
        cee_api=f'build/libs/wwpg-cee-api-{api}.jar',
        example=f'examples/extension-addon/build/libs/wwpg-api-example-{api}.jar',
        upstream_example=f'examples/cee-addon/build/libs/wwpg-upstream-api-example-{api}.jar',
        reference=f'build/libs/wwpg-{version}-upstream-reference-tests.jar',
        gametest=f'build/libs/wwpg-{version}-gametests.jar')
    artifacts = {}
    for role, name in paths.items():
        source_path = ROOT / name
        target = output / source_path.name
        shutil.copyfile(source_path, target); artifacts[role] = descriptor(target)
        artifacts[role]['path'] = target.name
    source = source or subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    write(output/'inputs.json', dict(schema=2, policy='separated-v2', source_sha=source,
        version=version, api_version=api, artifacts=artifacts, catalog_sha256=sha(CATALOG)))
    return output/'inputs.json'


def command(job, inputs, output):
    wrapper = str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
    directory = output / job['backend'] / job['profile'] / job.get('group', 'reference')
    if job.get('operation') == 'export':
        archive = output / f'{job["backend"]}-{job["profile"]}-{job["group"]}.zip'
        tool = 'export_stationary_yard.py' if job['world'] == 'yard' else 'export_api_workshop.py'
        args = [sys.executable, str(ROOT/'tools'/tool), '--run', str(directory), '--jar', inputs['artifacts']['runtime']['path'],
                '--profile', job['profile'], '--output', str(archive)]
        if job['world'] == 'yard': args += ['--target', inputs['version']]
        else: args += ['--backend', job['backend'], '--example', inputs['artifacts']['example']['path'],
                       '--upstream-example', inputs['artifacts']['upstream_example']['path']]
        return args, directory
    task = ('runUpstreamReferenceServer' if job['reference'] == 'upstream' else 'runReferenceCompatibilityServer') \
        if job.get('operation') == 'reference' else 'runPackagedGameTestServer'
    args = [sys.executable, str(ROOT/'tools/run_checked.py'), '--timeout', '600', '--', wrapper, task,
        '-PtestArtifact='+inputs['artifacts']['runtime']['path'], '-PtestBackend='+job['backend'],
        '-PtestProfile='+job['profile'], '-Pneo_version='+job['loader'],
        '-PtestDirectory='+str(directory), '-x', 'build', '-x', 'jar', '-x', 'test', '--no-daemon']
    if 'gametest' in inputs['artifacts']:
        args += ['-PtestFixtureArtifact='+inputs['artifacts']['gametest']['path']]
    if job.get('operation') == 'reference':
        args += ['-PreferenceArtifact='+inputs['artifacts']['reference' if job['reference'] == 'upstream' else 'runtime']['path'],
                 '-PreferenceDirectory='+str(directory)]
    else:
        args += ['-PtestNamespaces='+','.join(job['namespaces']), '-PrestartPhase='+job['phase'],
                 '-PtestApiAddon='+str(job['addon']).lower()]
    args += ['-PtestExampleArtifact='+inputs['artifacts']['example']['path'],
             '-PtestUpstreamExampleArtifact='+inputs['artifacts']['upstream_example']['path']]
    return args, directory


def archive_for_reopen(job, inputs, folder):
    world=job['group'].removesuffix('-extracted')
    bound=inputs.get('worlds',{}).get(world+'/'+job['profile'])
    if not bound:return folder/f'{job["backend"]}-{job["profile"]}-{world}.zip'
    archive=Path(bound['path'])
    if sha(archive)!=bound['sha256'] or archive.stat().st_size!=bound['bytes']:
        raise ValueError('Changed bound world archive')
    return archive


def execute(jobs, inputs_path, output, lane, reason=None, preparation=None):
    inputs = frozen_inputs(inputs_path)
    if any(j['platform'] != host_platform() for j in jobs): raise ValueError('Select this machine platform')
    output = Path(output).resolve(); output.mkdir(parents=True, exist_ok=False)
    write(output/'inputs.json', inputs)
    report = dict(schema=2, policy='separated-v2', suite=lane, source_sha=inputs['source_sha'],
        catalog=snapshot_catalog(output), inputs=descriptor(output/'inputs.json'), rerun_reason=reason,
        artifact_hashes={r: d['sha256'] for r,d in inputs['artifacts'].items()}, jobs=[])
    report['launcher_source_sha']=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    report['ci']={name:os.environ.get(name) for name in ('GITHUB_RUN_ID','GITHUB_RUN_ATTEMPT','GITHUB_SHA','GITHUB_EVENT_NAME')}
    report['complete_suite']=lane!='focused' and {j['id'] for j in jobs}=={j['id'] for j in plan(lane)}
    try:
        for job in jobs:
            frozen_inputs(inputs_path)
            record = dict(job, status='not run'); report['jobs'].append(record)
            started = time.monotonic()
            folder = output / job.get('loader', 'preparation'); folder.mkdir(exist_ok=True)
            if job.get('operation') == 'prepare':
                if preparation is None: raise ValueError('Supply the once-per-OS --preparation record')
                prepared = read(preparation)
                if prepared.get('passed') is not True or prepared['source_sha'] != inputs['source_sha']:
                    raise ValueError('Platform preparation did not pass on this source')
                record.update(status='passed', exit_code=0, proof=descriptor(preparation), log=prepared['check_log'])
            elif job.get('operation') == 'starter':
                if preparation is None: raise ValueError('Supply --preparation for independently compiled starter')
                parent = Path(preparation).resolve().parent
                log = output/(job['id'].replace('/', '__')+'.log')
                args = [sys.executable, str(ROOT/'tools/authoring_starter_acceptance.py'), 'server',
                    '--output', str(parent/'starter'), '--runtime', inputs['artifacts']['runtime']['path'], '--backend', job['backend']]
                if 'gametest' in inputs['artifacts']: args += ['--test-fixtures', inputs['artifacts']['gametest']['path']]
                with log.open('xb') as stream:
                    result = subprocess.run(args, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
                record.update(status='passed' if result.returncode == 0 else 'failed', exit_code=result.returncode,
                    proof=descriptor(parent/'starter'/(job['backend']+'-gameplay.json')) if result.returncode == 0 else None,
                    build=descriptor(parent/'starter/build-proof.json'), log=descriptor(log), command=args)
            elif job['lane'] == 'graphical':
                from graphical_suite import execute_case
                record.update(execute_case(job, inputs, output))
            else:
                args, directory = command(job, inputs, folder)
                if job.get('operation') == 'reference': directory.mkdir(parents=True, exist_ok=True)
                if job.get('group', '').endswith('-extracted') and not directory.exists():
                    archive=archive_for_reopen(job,inputs,folder)
                    from reopen_stationary_yard import extract
                    extract(archive, directory, False)
                log = output / (job['id'].replace('/', '__')+'.log')
                with log.open('xb') as stream:
                    result = subprocess.run(args, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
                record.update(status='passed' if result.returncode == 0 else 'failed', exit_code=result.returncode,
                              log=descriptor(log), command=args)
                if job.get('operation') == 'export' and result.returncode == 0:
                    record['archive'] = descriptor(Path(args[args.index('--output')+1]))
                elif result.returncode == 0:
                    proofs = sorted(directory.glob('*packaged-*.json'), key=lambda p: p.stat().st_mtime_ns)
                    if not proofs: raise ValueError('No actual process proof: '+job['id'])
                    record['proof'] = descriptor(proofs[-1])
                    if job.get('group', '').endswith('-extracted'):
                        record['extraction'] = descriptor(directory/'extracted-yard.json')
            record['seconds'] = round(time.monotonic()-started, 3)
            if record['status'] != 'passed': raise RuntimeError('Failed '+job['id'])
        frozen_inputs(inputs_path)
        report['status'] = 'passed'
    except BaseException as error:
        report.update(status='failed', failure=f'{type(error).__name__}: {error}')
        if report['jobs'] and report['jobs'][-1]['status'] == 'not run':
            report['jobs'][-1].update(status='failed',seconds=round(time.monotonic()-started,3))
        raise
    finally:
        report['not_run'] = [j['id'] for j in jobs[len(report['jobs']):]]
        write(output/'result.json', report)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    sub.add_parser('audit')
    continuation=sub.add_parser('continue-headless')
    for name in ('report','inputs','output','preparation'):continuation.add_argument('--'+name,type=Path,required=True)
    continuation.add_argument('--rerun-reason',required=True)
    continuation.add_argument('--environment')
    continuation.add_argument('--fresh-if-unavailable',action='store_true')
    continuation.add_argument('--plan',action='store_true')
    frozen = sub.add_parser('freeze'); frozen.add_argument('--output', type=Path, required=True); frozen.add_argument('--source-sha')
    binding=sub.add_parser('bind');binding.add_argument('--inputs',type=Path,required=True);binding.add_argument('--output',type=Path,required=True)
    binding.add_argument('--world',action='append',default=[]);binding.add_argument('--starter',type=Path)
    collect=sub.add_parser('collect-client');collect.add_argument('--case-directory',type=Path,required=True);collect.add_argument('--output',type=Path,required=True)
    for action in ('plan', 'run'):
        child = sub.add_parser(action)
        child.add_argument('lane', choices=['focused', 'full-headless', 'graphical', 'graphical-gallery'])
        for name in ('group', 'backend', 'profile', 'loader', 'platform', 'case'): child.add_argument('--'+name, action='append')
        if action == 'run':
            child.add_argument('--inputs', type=Path, required=True); child.add_argument('--output', type=Path, required=True)
            child.add_argument('--rerun-reason')
            child.add_argument('--preparation', type=Path)
    args = parser.parse_args()
    if args.action == 'audit': print(json.dumps(audit(), indent=2)); return
    if args.action == 'continue-headless':
        from suite_evidence import continue_headless
        result=continue_headless(args.report,args.inputs,args.output,args.rerun_reason,args.preparation,args.environment,args.fresh_if_unavailable,args.plan)
        if args.plan:print(json.dumps(result,indent=2))
        return
    if args.action == 'freeze': print(freeze(args.output, args.source_sha)); return
    if args.action == 'collect-client':
        from graphical_suite import collect_case
        collect_case(args.case_directory,args.output);return
    if args.action == 'bind':
        inputs=frozen_inputs(args.inputs)
        for pair in args.world:
            name,path=pair.split('=',1)
            if name not in {'yard/PINOUT','workshop/BASE','workshop/PINOUT'}:raise ValueError('Unknown world binding')
            inputs.setdefault('worlds',{})[name]=descriptor(path)
        if args.starter:inputs['starter']=descriptor(args.starter)
        write(args.output,inputs);return
    audit()
    jobs = plan(args.lane, args.group, args.backend, args.profile, args.loader, args.platform)
    if args.case:
        unknown = set(args.case) - {j['id'] for j in jobs}
        if unknown: raise ValueError('Unknown selected case: '+str(unknown))
        jobs = [j for j in jobs if j['id'] in args.case]
    if args.action == 'plan':
        print(json.dumps(dict(lane=args.lane, jobs=jobs, count=len(jobs)), indent=2)); return
    if not jobs: raise ValueError('Empty selection')
    execute(jobs, args.inputs, args.output, args.lane, args.rerun_reason, args.preparation)


if __name__ == '__main__': main()
