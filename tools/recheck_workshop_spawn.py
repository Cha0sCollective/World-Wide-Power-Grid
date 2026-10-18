"""Correct only Workshop spawn metadata, then reopen the exact fresh archives.

This manual focused continuation never builds a runtime or repeats a full suite.
The completed electrical source-world/cold checks keep their original identity.
"""
import argparse
import copy
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import zipfile
import test_suites as s
import suite_evidence as evidence
from export_api_workshop import fixed_spawn_level
from bundle_suite_evidence import bundle


def relative(path):
    item=s.descriptor(path);item['path']=Path(path).resolve().relative_to(s.ROOT).as_posix();return item


def correct(source,output):
    source,output=Path(source),Path(output)
    if output.exists():raise ValueError('Preserve the earlier world archive')
    with zipfile.ZipFile(source) as original:
        if original.testzip() is not None:raise ValueError('Corrupt source world')
        headers=[n for n in original.namelist() if n.endswith('/WWPG_MANIFEST.json')]
        if len(headers)!=1:raise ValueError('Ambiguous world manifest')
        header=headers[0];manifest=json.loads(original.read(header));prefix=header.removesuffix('WWPG_MANIFEST.json')
        payload={info.filename:original.read(info) for info in original.infolist()}
        for name,item in manifest['files'].items():
            data=payload[prefix+name]
            if len(data)!=item['bytes'] or hashlib.sha256(data).hexdigest()!=item['sha256']:
                raise ValueError('Source inventory differs: '+name)
        before=payload[prefix+'level.dat'];after=fixed_spawn_level(before)
        payload[prefix+'level.dat']=after
        manifest.update(spawn_policy='fixed-platform-v1',spawn_metadata_source_sha256=s.sha(source))
        manifest['files']['level.dat']=dict(bytes=len(after),sha256=hashlib.sha256(after).hexdigest())
        payload[header]=(json.dumps(manifest,indent=2,ensure_ascii=False)+'\n').encode('utf-8')
        output.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(output,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as zipped:
            for info in original.infolist():zipped.writestr(copy.copy(info),payload[info.filename])
        changed=[name for name,data in payload.items() if original.read(name)!=data]
    if set(changed)!={header,prefix+'level.dat'}:raise ValueError('Unexpected native world change')
    return dict(policy='fixed-platform-v1',source=relative(source),archive=relative(output),changed_entries=changed,
        correction='Only spawnRadius becomes 0 in native level.dat; all other uncompressed NBT and native world payloads are preserved.',
        profile=manifest['profile'],source_backend=manifest['backend'])


def validate_correction(source,archive):
    """Compare native bytes without depending on platform-specific gzip headers."""
    with zipfile.ZipFile(source) as before,zipfile.ZipFile(archive) as after:
        if set(before.namelist())!=set(after.namelist()) or after.testzip() is not None:
            raise ValueError('Corrected world inventory differs')
        header=next(n for n in before.namelist() if n.endswith('/WWPG_MANIFEST.json'))
        level=header.removesuffix('WWPG_MANIFEST.json')+'level.dat'
        for name in before.namelist():
            if name not in (header,level) and before.read(name)!=after.read(name):
                raise ValueError('Changed electrical/native payload: '+name)
        if gzip.decompress(after.read(level))!=gzip.decompress(fixed_spawn_level(before.read(level))):
            raise ValueError('Changed native NBT outside spawnRadius')
        expected=json.loads(before.read(header))
        expected.update(spawn_policy='fixed-platform-v1',spawn_metadata_source_sha256=s.sha(source))
        raw=after.read(level);expected['files']['level.dat']=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
        if json.loads(after.read(header))!=expected:raise ValueError('Changed world metadata outside spawn correction')


def prepare(inputs,base_reports,output):
    output=Path(output).resolve();output.mkdir(parents=True,exist_ok=False)
    frozen=s.frozen_inputs(inputs);frozen['worlds']={};proofs={}
    for profile,index_path in base_reports.items():
        index=s.read(index_path)
        if index['status']!='passed':raise ValueError('Original environment has not passed')
        paths=[evidence.locked(item,s.ROOT) for item in index['reports']]
        evidence.validate_reports(paths,'full-headless',frozen['source_sha'],{r:frozen['artifacts'][r]['sha256'] for r in s.ROLES},complete=False)
        rows=[job for path in paths for job in s.read(path)['jobs'] if job.get('operation')=='export' and job['world']=='workshop' and job['profile']==profile]
        if len(rows)!=1:raise ValueError('No unique completed Workshop export')
        source=evidence.locked(rows[0]['archive'],s.ROOT)
        target=output/'sources'/(profile+'.zip');target.parent.mkdir(exist_ok=True);shutil.copyfile(source,target)
        archive=output/'worlds'/(profile+'.zip')
        proof=correct(target,archive);proof['source_environment']=Path(index_path).parent.name
        proof_path=output/(profile+'-spawn-correction.json');s.write(proof_path,proof)
        frozen['worlds']['workshop/'+profile]=dict(relative(archive),path=archive.relative_to(output).as_posix());proofs[profile]=relative(proof_path)
    # Normalize canonical descriptors for a different OS/workspace checkout.
    for item in frozen['artifacts'].values():item['path']=Path(os.path.relpath(Path(item['path']).resolve(),output)).as_posix()
    s.write(output/'inputs.json',frozen)
    s.write(output/'spawn-corrections.json',dict(schema=1,passed=True,source_sha=frozen['source_sha'],proofs=proofs))
    print(json.dumps(dict(passed=True,worlds=frozen['worlds'])))


def run(inputs,corrections,output,backend,profile):
    frozen=s.frozen_inputs(inputs);metadata=s.read(corrections)
    jobs=s.plan('full-headless',backends=[backend],profiles=[profile],loaders=['21.1.231'],platforms=[s.host_platform()])
    exported=next(j for j in jobs if j.get('operation')=='export' and j['world']=='workshop')
    reopens=[j for j in jobs if j.get('group')=='workshop-extracted']
    # Validate the producer's metadata-only export before any reopen, on the
    # same host/environment that will consume it. This is not a second export.
    correction_path=evidence.locked(metadata['proofs'][profile],s.ROOT)
    correction=s.read(correction_path)
    archive=evidence.locked(correction['archive'],s.ROOT)
    if correction['policy']!='fixed-platform-v1' or s.sha(archive)!=frozen['worlds']['workshop/'+profile]['sha256']:
        raise ValueError('Wrong corrected world')
    validate_correction(evidence.locked(correction['source'],s.ROOT),archive)
    output=Path(output).resolve()
    s.execute(reopens,inputs,output,'full-headless',reason='Authorized Workshop spawn correction; three exact archive reopens only; retain the passing full runtime suite')
    raw=output/'result.json';report=s.read(raw)
    export_record=dict(exported,status='passed',exit_code=0,archive=frozen['worlds']['workshop/'+profile],
        log=relative(correction_path),producer=relative(correction_path),execution='validated shared metadata export; produced once',
        source_backend=correction['source_backend'],source_environment=correction['source_environment'])
    report.update(jobs=[export_record]+report['jobs'],execution_report=relative(raw),
        note='Original reopen process report is retained; only a validated shared export record is added.')
    combined=output/'world-result.json';s.write(combined,report)
    evidence.validate_reports([combined],'full-headless',report['source_sha'],report['artifact_hashes'],complete=False)
    portable=bundle(combined,output/'portable');print(portable)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='action',required=True)
    p=sub.add_parser('prepare');p.add_argument('--inputs',type=Path,required=True);p.add_argument('--base-report',type=Path,required=True);p.add_argument('--pinout-report',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p=sub.add_parser('run');p.add_argument('--inputs',type=Path,required=True);p.add_argument('--corrections',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--backend',choices=['NATIVE','JAVA'],required=True);p.add_argument('--profile',choices=['BASE','PINOUT'],required=True)
    a=parser.parse_args()
    if a.action=='prepare':prepare(a.inputs,dict(BASE=a.base_report,PINOUT=a.pinout_report),a.output)
    else:run(a.inputs,a.corrections,a.output,a.backend,a.profile)
