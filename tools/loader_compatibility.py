"""Opt-in parallel loader compatibility; never full or graphical acceptance."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import urllib.request

import test_suites as suites
from suite_evidence import locked, require, validate_job

ROOT = suites.ROOT
POLICY = 'loader-compatibility-v1'


def versions(text):
    values = re.split(r'[\s,]+', text.strip())
    if not 1 <= len(values) <= 4 or len(set(values)) != len(values):
        raise ValueError('Select one to four distinct NeoForge versions')
    minimum = int(suites.catalog()['loaders'][0].split('.')[-1])
    if any(not re.fullmatch(r'21\.1\.[0-9]{1,5}', v) or int(v.split('.')[-1]) < minimum for v in values):
        raise ValueError('Select NeoForge 21.1 versions at or above the declared minimum')
    return values


def matrix(loaders, installations=('BASE', 'ADDONS')):
    spec = suites.catalog()
    require(bool(installations) and len(set(installations)) == len(installations)
            and set(installations) <= {'BASE', 'ADDONS'}, 'Select BASE, ADDONS or both')
    return [dict(os=os_name, loader=loader, backend=backend, installation=installation)
            for loader in loaders for os_name in spec['platforms']
            for backend in spec['backends'] for installation in installations]


def plan(loader, backend, installation, platform):
    if len(versions(loader)) != 1: raise ValueError('One loader per environment')
    spec = suites.catalog(); cfg = spec['loader_compatibility']
    if installation not in cfg['installations']: raise ValueError('Unknown installation')
    groups = cfg['groups'] + (cfg['addon_groups'] if installation == 'ADDONS' else [])
    profile = cfg['installations'][installation]
    # Reuse catalog namespace merging, then apply only this probe's explicitly
    # supplied loader. Unverified versions never enter release acceptance.
    base = spec['loaders'][0]
    selected = suites.plan('focused', groups, [backend], [profile], [base], [platform])
    jobs = [dict(id=f'{platform}/{loader}/{backend}/{installation}/installation/0-SETUP',
                 lane='headless', platform=platform, loader=loader, backend=backend,
                 installation=installation, profile=profile, group='installation', phase='SETUP',
                 namespaces=[cfg['installation_namespace']] + ([cfg['addon_namespace']] if installation=='ADDONS' else []),
                 addon=installation == 'ADDONS')]
    for original in selected:
        group = original['group']
        for index, phase in enumerate(spec['groups'][group]['phases']):
            item = dict(original, loader=loader, installation=installation, phase=phase,
                        addon=installation == 'ADDONS',
                        id=f'{platform}/{loader}/{backend}/{installation}/{group}/{index}-{phase}')
            if phase != 'SETUP':
                setup = spec['groups'][group].get('setup_namespaces', [])
                item['namespaces'] = [n for n in item['namespaces'] if n not in setup]
            if installation == 'BASE':
                item['namespaces'] = [n for n in item['namespaces'] if n not in cfg['addon_only_namespaces']]
            jobs.append(item)
    return jobs


def prepare(output):
    from ci_prepare import checked
    output = Path(output).resolve(); output.mkdir(parents=True, exist_ok=False)
    wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    # Build at the normal minimum loader, once. No tests or Minecraft launches.
    checked([sys.executable, ROOT/'tools/run_checked.py', '--timeout', '1200', '--', wrapper,
             'assemble', 'referenceJar', 'publishAllPublicationsToSdkRepository', '--no-daemon'], output/'build.log')
    for name in ('extension-addon', 'cee-addon'):
        checked([sys.executable, ROOT/'tools/run_checked.py', '--timeout', '1200', '--', wrapper,
                 '-p', ROOT/'examples'/name, 'jar', '--no-daemon'], output/(name+'.log'))
    frozen = suites.freeze(output/'frozen')
    inputs = suites.read(frozen)
    cfg = suites.catalog()['loader_compatibility']
    for item in cfg['artifacts']:
        target = frozen.parent/item['filename']
        with urllib.request.urlopen(item['url'], timeout=120) as response, target.open('xb') as stream:
            shutil.copyfileobj(response, stream)
        require(suites.sha(target) == item['sha256'], 'Optional download checksum differs: '+item['role'])
        inputs['artifacts'][item['role']] = dict(suites.descriptor(target), path=target.name,
                                               mod_id=item['mod_id'], version=item['version'])
    inputs['loader_compatibility'] = dict(policy=POLICY, artifacts=[i['role'] for i in cfg['artifacts']])
    # freeze's original manifest remains immutable.
    result = frozen.parent/'loader-inputs.json'; suites.write(result, inputs)
    return result


def validate(path, inputs_path, jobs):
    inputs = suites.frozen_inputs(inputs_path)
    cfg=suites.catalog()['loader_compatibility']
    require(inputs.get('loader_compatibility')==dict(policy=POLICY,artifacts=[i['role'] for i in cfg['artifacts']]),
            'Wrong frozen optional inventory')
    for item in cfg['artifacts']:
        actual=inputs['artifacts'].get(item['role'],{})
        require(actual.get('sha256')==item['sha256'] and actual.get('mod_id')==item['mod_id'],
                'Optional input differs from pinned inventory: '+item['role'])
    report = suites.read(path); base = Path(path).resolve().parent
    require(report.get('suite') == 'focused' and report.get('complete_suite') is False,
            'Loader probe must not claim full acceptance')
    require(report.get('status') == 'passed' and not report.get('not_run'), 'Incomplete loader probe')
    require(report.get('source_sha') == inputs['source_sha'], 'Wrong frozen source')
    require(report.get('launcher_source_sha') == inputs['source_sha'], 'Runner source differs from frozen source')
    require(inputs.get('catalog_sha256') == suites.sha(suites.CATALOG), 'Frozen catalog differs')
    require(report.get('artifact_hashes') == {r:d['sha256'] for r,d in inputs['artifacts'].items()}, 'Wrong frozen artifacts')
    require(suites.read(locked(report['catalog'], base)) == suites.catalog(), 'Wrong test catalog')
    require([j['id'] for j in report['jobs']] == [j['id'] for j in jobs], 'Missing/duplicate/reordered loader checks')
    for actual, expected in zip(report['jobs'], jobs): validate_job(actual, expected, report, base)
    inventory = next(j for j in report['jobs'] if j['group'] == 'installation')
    text = locked(inventory['log'], base).read_text(errors='replace')
    expected_ids = [i['mod_id'] for i in suites.catalog()['loader_compatibility']['artifacts']]
    for mod_id in expected_ids + ['wwpg_api_example', 'wwpg_api_upstream']:
        require(f'LOADER_OPTIONAL_VERIFIED: {mod_id} present={str(jobs[0]["installation"]=="ADDONS").lower()}' in text,
                'Missing actual optional mod observation: '+mod_id)
    return report


def run(inputs, output, loader, backend, installation):
    jobs = plan(loader, backend, installation, suites.host_platform())
    # Extend commands only within this opt-in tool. Shared suite selection and
    # its historical validators retain their existing semantics.
    original = suites.command
    def command(job, frozen, folder):
        args, directory = original(job, frozen, folder)
        args += ['-I', str(ROOT/'tools/loader_compatibility.gradle'),
                 '-PloaderInputs='+str(Path(inputs).resolve()), '-PloaderInstallation='+installation]
        return args, directory
    suites.execute(jobs, inputs, output, 'focused', command_factory=command)
    validate(Path(output)/'result.json', inputs, jobs)


def collect(plan_path, inputs_path, reports, output):
    campaign = suites.read(plan_path); inputs = suites.frozen_inputs(inputs_path)
    require(campaign.get('policy') == POLICY, 'Wrong compatibility campaign')
    expected = {(r['os'], r['loader'], r['backend'], r['installation']) for r in campaign['matrix']['include']}
    require(len(expected) == len(campaign['matrix']['include']) and expected, 'Invalid campaign matrix')
    loaders=list(dict.fromkeys(r['loader'] for r in campaign['matrix']['include']))
    installations=campaign.get('installations', ['BASE', 'ADDONS'])
    require(campaign['matrix']['include']==matrix(versions(','.join(loaders)), installations), 'Incomplete campaign matrix')
    seen = set(); entries = []; errors=[]
    for path in sorted(Path(reports).glob('*/result.json')):
        label=path.parent.name
        try:
            report = suites.read(path)
            require(bool(report.get('jobs')), 'Empty environment report')
            j = report['jobs'][0]; identity=(j['platform'],j['loader'],j['backend'],j['installation'])
            label='/'.join(identity)
            require(identity in expected and identity not in seen, 'Unexpected/duplicate environment')
            validate(path, inputs_path, plan(identity[1],identity[2],identity[3],identity[0]))
            seen.add(identity); entries.append(dict(environment=label, status='passed', report=suites.descriptor(path)))
        except (ValueError,KeyError,OSError) as error:
            errors.append(label+': '+str(error));entries.append(dict(environment=label,status='failed',reason=str(error)))
    result=dict(policy=POLICY, source_sha=inputs['source_sha'], full_acceptance=False,
                installations=installations, complete_matrix=set(installations)=={'BASE','ADDONS'},
                status='passed' if seen==expected and not errors else 'failed', environments=entries, errors=errors,
                missing=['/'.join(i) for i in sorted(expected-seen)])
    suites.write(output,result)
    summary=os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary,'a',encoding='utf-8') as f:
            f.write('## Loader compatibility checks\n\nFocused headless compatibility only; no graphical or full release acceptance.\n\n')
            for entry in entries:f.write('- '+entry['environment']+': '+entry['status']+'\n')
            for missing in result['missing']:f.write('- '+missing+': **not run / no validated report**\n')
    require(result['status']=='passed','Failed/incomplete loader compatibility: '+', '.join(errors+result['missing']))
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='action',required=True)
    p=sub.add_parser('matrix');p.add_argument('--versions',required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--installations',nargs='+',choices=['BASE','ADDONS'],default=['BASE','ADDONS'])
    p=sub.add_parser('prepare');p.add_argument('--output',type=Path,required=True)
    for action in ('plan','run'):
        p=sub.add_parser(action);p.add_argument('--loader',required=True);p.add_argument('--backend',choices=['NATIVE','JAVA'],required=True)
        p.add_argument('--installation',choices=['BASE','ADDONS'],required=True)
        if action=='run':p.add_argument('--inputs',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p=sub.add_parser('collect');p.add_argument('--plan',type=Path,required=True);p.add_argument('--inputs',type=Path,required=True)
    p.add_argument('--reports',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=parser.parse_args()
    if a.action=='matrix':
        campaign=dict(policy=POLICY,installations=a.installations,matrix={'include':matrix(versions(a.versions),a.installations)})
        a.output.parent.mkdir(parents=True,exist_ok=True);suites.write(a.output,campaign)
        print(json.dumps(campaign['matrix'],separators=(',',':')))
    elif a.action=='prepare':print(prepare(a.output))
    elif a.action=='plan':print(json.dumps(plan(a.loader,a.backend,a.installation,suites.host_platform()),indent=2))
    elif a.action=='run':run(a.inputs,a.output,a.loader,a.backend,a.installation)
    else:collect(a.plan,a.inputs,a.reports,a.output)


if __name__=='__main__':main()
