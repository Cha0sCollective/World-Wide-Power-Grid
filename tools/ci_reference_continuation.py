"""Resume only a failed bridge-free reference and its unexecuted successors.

Earlier passing processes retain their original GitHub job, source and files.
The failed reference remains inside the original artifact, never overwritten.
This narrow continuation accepts only the known reference-teardown failure.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPO = 'Cha0sCollective/World-Wide-Power-Grid'
RESTORE_STEP = 'Restore passing prefix before the isolated reference failure'
REFERENCE_STEP = 'Run python tools/run_checked.py -- ./gradlew runUpstreamReferenceServer -PtestBackend=NATIVE --no-daemon'
PREFIX_DIRS = {'api', 'api-pause', 'api-pending-restart', 'loading', 'board-startup', 'safety', 'logs'}
ROLES = {'runtime': 'build/libs/wwpg-0.2.2-beta.4.jar', 'api': 'build/libs/wwpg-api-1.0.0.jar',
         'cee_api': 'build/libs/wwpg-cee-api-1.0.0.jar',
         'example': 'examples/extension-addon/build/libs/wwpg-api-example-1.0.0.jar',
         'upstream_example': 'examples/cee-addon/build/libs/wwpg-upstream-api-example-1.0.0.jar'}


def require(ok, message):
    if not ok: raise ValueError(message)


def read(path): return json.loads(Path(path).read_text(encoding='utf-8'))
def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def desc(path, root=ROOT):
    path = Path(path).resolve()
    return dict(path=path.relative_to(root).as_posix(), sha256=sha(path), bytes=path.stat().st_size)


def origin(run, job, artifact, platform, profile):
    require(run.get('status') == 'completed' and run.get('conclusion') == 'failure'
            and run.get('path') == '.github/workflows/build.yml', 'Origin is not the completed failed Build run')
    require(run.get('html_url') == f'https://github.com/{REPO}/actions/runs/{run["id"]}', 'Wrong origin repository')
    require(job.get('name') == f'build ({platform}, NATIVE, {profile})'
            and job.get('status') == 'completed' and job.get('conclusion') == 'failure'
            and job.get('run_id') == run['id'] and job.get('run_attempt') == run['run_attempt'], 'Wrong failed Native job')
    steps = job.get('steps', [])
    failed = [s for s in steps if s.get('conclusion') == 'failure']
    require(len(failed) == 1 and failed[0].get('name') == REFERENCE_STEP, 'Failure was not isolated to upstream reference')
    before = [s for s in steps if s.get('number', 0) < failed[0]['number']]
    require(before and all(s.get('conclusion') in {'success', 'skipped'} for s in before), 'Prefix contains a failed/incomplete step')
    require(any(s.get('name') == 'Three consecutive cold world restarts' and s.get('conclusion') == 'success' for s in before),
            'Prefix did not complete the actual cold restarts')
    require(artifact.get('name') == f'wwpg-{platform}-NATIVE-{profile}' and artifact.get('expired') is False
            and artifact.get('workflow_run', {}).get('id') == run['id']
            and artifact['workflow_run'].get('head_sha') == run['head_sha'], 'Wrong origin artifact/source')


def prefix_files(directory):
    run = directory / 'run'
    return sorted(p for p in run.rglob('*') if p.is_file()
                  and (len(p.relative_to(run).parts) == 1 or p.relative_to(run).parts[0] in PREFIX_DIRS))


def validate(receipt, root=ROOT):
    def locked(record):
        p = (root / record['path']).resolve()
        require(p.is_relative_to(root.resolve()) and p.is_file() and sha(p) == record['sha256']
                and p.stat().st_size == record['bytes'], 'Changed/missing continuation evidence')
        return p
    require(receipt.get('schema') == 1 and receipt.get('scope') == 'reference_teardown_only', 'Wrong continuation scope')
    run, job, artifact = [read(locked(receipt[k])) for k in ('run', 'job', 'artifact')]
    origin(run, job, artifact, receipt['platform'], receipt['profile'])
    archive = locked(receipt['original_archive'])
    require(artifact.get('digest') == 'sha256:' + sha(archive), 'Original GitHub artifact digest differs')
    with zipfile.ZipFile(archive) as z:
        original_source = json.loads(z.read('build/ci-source.json'))
        require(original_source['source_sha'] == run['head_sha'] and original_source['run_id'] == run['id']
                and original_source['run_attempt'] == run['run_attempt'], 'Original checkout is unproven')
        for role, record in receipt['artifacts'].items():
            require(role in ROLES and hashlib.sha256(z.read(ROLES[role])).hexdigest() == record['sha256']
                    and sha(locked(record)) == record['sha256'], 'Changed runtime/API/example cannot reuse a prefix')
        require(set(receipt['artifacts']) == set(ROLES), 'Continuation omitted an artifact comparison')
        expected = {n for n in z.namelist() if not n.endswith('/') and n.startswith('run/')
                    and (len(Path(n).parts) == 2 or Path(n).parts[1] in PREFIX_DIRS)}
        require(expected == {v['original_member'] for v in receipt['files']}, 'Incomplete original prefix inventory')
        for item in receipt['files']:
            require(hashlib.sha256(z.read(item['original_member'])).hexdigest() == item['file']['sha256'], 'Retained process record changed')
            locked(item['file'])
        failures = [json.loads(z.read(n)) for n in z.namelist() if n.startswith('run/reference/reference-packaged-') and n.endswith('.json')]
        require(len(failures) == 1 and not failures[0].get('completed'), 'Original failed reference was not preserved')
        log = z.read('run/reference/logs/latest.log').decode('utf-8', errors='replace')
        require('PG native solve rejected: info=5, rows=6, nnz=11' in log, 'Different failure requires investigation before continuation')
    return dict(source_sha=run['head_sha'], run_id=run['id'], job_id=job['id'],
                failure='reference_teardown_singular_matrix', retained_process_files=len(receipt['files']))


def prepare(run_id, platform, profile):
    destination = ROOT / 'build/retained-ci'
    require(not destination.exists(), 'Use a fresh continuation checkout')
    destination.mkdir(parents=True)
    def api(suffix):
        return json.loads(subprocess.check_output(['gh', 'api', f'repos/{REPO}/{suffix}'], text=True, encoding='utf-8'))
    run = api(f'actions/runs/{run_id}')
    jobs = api(f'actions/runs/{run_id}/jobs?per_page=100')['jobs']
    artifacts = api(f'actions/runs/{run_id}/artifacts?per_page=100')['artifacts']
    job = next(j for j in jobs if j['name'] == f'build ({platform}, NATIVE, {profile})')
    artifact = next(a for a in artifacts if a['name'] == f'wwpg-{platform}-NATIVE-{profile}')
    origin(run, job, artifact, platform, profile)
    archive = destination / 'original-failed-artifact.zip'
    with archive.open('wb') as f:
        subprocess.run(['gh', 'api', f'repos/{REPO}/actions/artifacts/{artifact["id"]}/zip'], stdout=f, check=True)
    require(artifact.get('digest') == 'sha256:' + sha(archive), 'Downloaded origin artifact failed its GitHub digest')
    extracted = destination / 'original'
    with zipfile.ZipFile(archive) as z:
        require(all((extracted / n).resolve().is_relative_to(extracted.resolve()) for n in z.namelist()), 'Unsafe origin ZIP')
        z.extractall(extracted)
    for key, value in [('run', run), ('job', job), ('artifact', artifact)]:
        (destination / f'{key}.json').write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')
    files = []
    for p in prefix_files(extracted):
        target = ROOT / p.relative_to(extracted)
        require(not target.exists(), f'Continuation would overwrite a process record: {target}')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(p, target)
        files.append(dict(original_member=p.relative_to(extracted).as_posix(), file=desc(target)))
    receipt = dict(schema=1, scope='reference_teardown_only', platform=platform, profile=profile,
                   original_archive=desc(archive), artifacts={role: desc(ROOT / path) for role, path in ROLES.items()},
                   files=files, **{key: desc(destination / f'{key}.json') for key in ('run','job','artifact')})
    validate(receipt)
    (destination / 'receipt.json').write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf-8')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run', type=int, required=True); p.add_argument('--platform', required=True)
    p.add_argument('--profile', choices=['BASE','PINOUT'], required=True)
    a = p.parse_args(); prepare(a.run, a.platform, a.profile)
