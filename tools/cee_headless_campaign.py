"""Explicit CEE development campaign around the existing full headless runner."""
import argparse
import copy
import json
import os
from pathlib import Path
import shutil
import sys

import test_suites as suites

ROOT=suites.ROOT
VERSION='0.2.10-dev.cee120.5'
PLAYER_SHA256='860a150384ed60ad8a51143afe4d84ac24323f9e55ec22d22a2f9d90c0e71308'
CEE_VERSION='1.21.1-1.2.0-132'
CEE_SHA256='71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7'


def campaign_catalog():
    # Preserve the historical .5 selection independently of later runner policy.
    # The published recommendation remains unchanged. This opt-in campaign
    # promotes every CEE development regression into its own full matrix.
    spec=copy.deepcopy(suites.read(ROOT/'tools/legacy/test-suites-cee120-pre-addons.json'))
    spec['recommended_runtime']['electroenergetics']=CEE_VERSION
    for cfg in spec['groups'].values():cfg.pop('focused_only',None)
    spec['groups']['cee-fans']={'namespaces':['wwpg_cee_fans'],'phases':['SETUP']}
    spec['special_namespaces'].pop('wwpg_cee_fans')
    spec['reference_control']={'id':'cee120-residual-1e-16','cee_sha256':CEE_SHA256,
        'squared_residual_threshold':1e-16,
        'trace_cases':['cee-heating','cee-fuse','cee-accumulator','pg-acid-battery','pg-potato-battery',
                       'cee-diode-reactive','cee-diode-clamped-high-bias','pg-diode-reactive-board'],
        'scope':'Test-only independent CEE reference; stock-default failures retained separately'}
    spec['campaign']={'name':'CEE 1.2 full headless','version':VERSION,'player_sha256':PLAYER_SHA256,
                      'cee_version':CEE_VERSION,'cee_sha256':CEE_SHA256,
                      'scope':'Windows/Linux, Native/Java, BASE/PINOUT; no graphical lane'}
    return spec


def assert_player(inputs):
    if inputs.get('version')!=VERSION or inputs['artifacts']['runtime']['sha256']!=PLAYER_SHA256:
        raise ValueError('Campaign requires the exact frozen '+VERSION+' player '+PLAYER_SHA256)


def decorate_canonical(output):
    frozen=output/'frozen'
    original=frozen/'inputs.json'
    inputs=suites.frozen_inputs(original)
    assert_player(inputs)
    contract=ROOT/'build/cee12-contract/electroenergetics-1.21.1-1.2.0-132.jar'
    if suites.sha(contract)!=CEE_SHA256:raise ValueError('CEE contract differs from the reviewed build')
    target=frozen/contract.name
    if target.exists():raise ValueError('Preserve the existing CEE campaign inputs')
    shutil.copy2(contract,target)
    spec=campaign_catalog();catalog_path=frozen/'catalog-cee120.json'
    suites.write(catalog_path,spec)
    # Keep the original producer manifest unchanged beside this separate runtime campaign.
    record=suites.read(original)
    record['artifacts']['cee_runtime']=dict(suites.descriptor(target),path=target.name)
    record.update(recommended_runtime=spec['recommended_runtime'],catalog_sha256=suites.sha(catalog_path),
                  campaign=spec['campaign'],producer_inputs=dict(suites.descriptor(original),path=original.name))
    path=frozen/'cee120-inputs.json'
    suites.write(path,record)
    suites.frozen_inputs(path)
    return path


def prepare(output,inputs=None):
    # JVM/SDK compilation retains the minimum contracts. Actual game launches
    # select the development runtime only after activating its frozen campaign.
    import ci_prepare
    result=ci_prepare.prepare(output,inputs)
    if inputs is None:decorate_canonical(Path(output).resolve())
    else:assert_player(suites.frozen_inputs(inputs))
    print(result)


def activate(directory,environment_file=None):
    folder=Path(directory).resolve()
    path=folder/'cee120-inputs.json'
    inputs=suites.frozen_inputs(path);assert_player(inputs)
    cee=inputs['artifacts']['cee_runtime']
    if cee['sha256']!=CEE_SHA256:raise ValueError('CEE runtime hash differs')
    catalog_path=folder/'catalog-cee120.json'
    if suites.read(catalog_path)!=campaign_catalog() or suites.sha(catalog_path)!=inputs['catalog_sha256']:
        raise ValueError('CEE campaign catalog changed')
    values={'WWPG_TEST_CATALOG':str(catalog_path),'WWPG_CAMPAIGN_INPUTS':str(path),
            'ORG_GRADLE_PROJECT_ceeRuntimeJar':cee['path'],
            'ORG_GRADLE_PROJECT_ceeDevelopmentJar':cee['path'],
            'ORG_GRADLE_PROJECT_testExpectedCeeSha256':CEE_SHA256}
    if environment_file:
        with Path(environment_file).open('a',encoding='utf-8',newline='\n') as out:
            for key,value in values.items():
                if '\n' in value or '\r' in value:raise ValueError('Invalid environment path')
                out.write(key+'='+value+'\n')
    print(json.dumps(dict(version=VERSION,player_sha256=PLAYER_SHA256,cee_version=CEE_VERSION,
                          cee_sha256=CEE_SHA256,inputs=str(path)),indent=2))
    return values


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='action',required=True)
    sub.add_parser('plan')
    prep=sub.add_parser('prepare');prep.add_argument('--output',type=Path,required=True);prep.add_argument('--inputs',type=Path)
    active=sub.add_parser('activate');active.add_argument('--directory',type=Path,required=True)
    args=parser.parse_args()
    if args.action=='plan':
        jobs=suites.plan('full-headless',spec=campaign_catalog())
        print(json.dumps(dict(campaign=campaign_catalog()['campaign'],count=len(jobs),jobs=jobs),indent=2))
    elif args.action=='prepare':prepare(args.output,args.inputs)
    else:activate(args.directory,os.environ.get('GITHUB_ENV'))


if __name__=='__main__':main()
