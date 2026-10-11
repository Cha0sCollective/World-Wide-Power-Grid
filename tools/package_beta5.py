"""Package beta.5 only after matching CI, exact-world and real-client acceptance."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.1.0-beta.5'
REPO = 'Cha0sCollective/World-Wide-Power-Grid'
RECORD = ROOT / 'release' / VERSION
OUTPUT = ROOT / 'build/distributions' / VERSION
CI = ROOT / 'build/beta5-ci'
EXPECTED = {f'build ({platform}, {backend}, {profile})'
            for platform in ('ubuntu-24.04', 'windows-latest')
            for backend in ('NATIVE', 'JAVA') for profile in ('BASE', 'PINOUT')}


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def describe(path):
    return dict(file=path.name, sha256=sha(path), bytes=path.stat().st_size)


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, record):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8', newline='\n')


def gh(*args):
    return subprocess.run(['gh', *args], check=True, capture_output=True, text=True, encoding='utf-8').stdout


def collect_ci(run_id):
    run = json.loads(gh('api', f'repos/{REPO}/actions/runs/{run_id}'))
    require(run['status'] == 'completed' and run['conclusion'] == 'success', 'Full CI has not passed')
    require(run['path'] == '.github/workflows/build.yml', 'Expected the full Build workflow')
    jobs = json.loads(gh('api', f'repos/{REPO}/actions/runs/{run_id}/jobs'))['jobs']
    require(len(jobs) == 8 and {j['name'] for j in jobs} == EXPECTED
            and all(j['conclusion'] == 'success' for j in jobs), 'Incomplete eight-way matrix')
    destination = CI / str(run_id)
    require(not destination.exists(), 'Keep the existing CI collection; do not overwrite evidence')
    destination.mkdir(parents=True)
    gh('run', 'download', str(run_id), '--repo', REPO, '--dir', str(destination))
    for job in jobs:
        log = gh('run', 'view', str(run_id), '--repo', REPO, '--job', str(job['id']), '--log')
        (destination / f'ci-{job["id"]}.log').write_text(log, encoding='utf-8', newline='\n')
    jars = sorted(destination.glob(f'wwpg-*/build/libs/wwpg-{VERSION}.jar'))
    require(len(jars) == 8, 'Missing final versioned CI jars')
    write(RECORD / 'ci.json', dict(schema=1, runId=run_id, runAttempt=run['run_attempt'],
          status=run['status'], conclusion=run['conclusion'], headSha=run['head_sha'], url=run['html_url'],
          jobs=[dict(databaseId=j['id'], name=j['name'], conclusion=j['conclusion'],
                     status=j['status'], url=j['html_url'], steps=j['steps']) for j in jobs],
          packagedArtifactHashes=[dict(job=p.relative_to(destination).parts[0], **describe(p)) for p in jars]))


def proofs(directory, jar_hash, profile, backend, namespace, phases, count):
    records = [read(p) for p in sorted(directory.glob('packaged-*.json'))]
    require(len(records) == len(phases), f'Unexpected packaged process count: {directory}')
    require([r['phase'] for r in records] == phases, f'Missing cold phases: {directory}')
    for record in records:
        require(record.get('completed') and record.get('required_tests_passed') == count,
                f'Incomplete packaged checks: {directory}')
        require(record['sha256'] == jar_hash and record['profile'] == profile
                and record['backend'] == backend and record['namespaces'] == namespace,
                f'Packaged proof does not match release inputs: {directory}')
    return records


def archive(path, entries):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        for name, source in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 10, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            output.writestr(info, source.read_bytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--collect-ci', type=int)
    parser.add_argument('--client-batch', required=True, help='Completed accept_stationary_clients batch with ColdRestarts')
    parser.add_argument('--build-log', type=Path, required=True)
    args = parser.parse_args()
    if args.collect_ci:
        collect_ci(args.collect_ci)
    jar = ROOT / 'build/libs' / f'wwpg-{VERSION}.jar'
    jar_hash = sha(jar)
    with zipfile.ZipFile(jar) as packaged:
        require(f'version="{VERSION}"'.encode() in packaged.read('META-INF/neoforge.mods.toml')
                or f'version = "{VERSION}"'.encode() in packaged.read('META-INF/neoforge.mods.toml'), 'Not the final mod version')
        require(packaged.read('META-INF/WWPG-LICENSE.txt') == (ROOT / 'LICENSE').read_bytes(), 'License mismatch')
        require(not any('/acceptance/' in name for name in packaged.namelist()), 'Private client helper leaked into jar')
    require('BUILD SUCCESSFUL' in args.build_log.read_text(encoding='utf-8', errors='replace'), 'Build did not pass')
    equation_tests = 0
    for path in (ROOT / 'build/test-results/test').glob('TEST-*.xml'):
        suite = ET.parse(path).getroot()
        require(int(suite.attrib.get('failures', 0)) == int(suite.attrib.get('errors', 0)) == 0, 'Equation test failure')
        equation_tests += int(suite.attrib['tests'])
    require(equation_tests >= 4, 'Missing analytical equation tests')
    ci = read(RECORD / 'ci.json')
    require(ci['conclusion'] == 'success' and len(ci['jobs']) == 8
            and {j['name'] for j in ci['jobs']} == EXPECTED
            and all(j['conclusion'] == 'success' for j in ci['jobs']), 'Full matrix is not passing')
    artifacts = CI / str(ci['runId'])
    jars = sorted(artifacts.glob(f'wwpg-*/build/libs/wwpg-{VERSION}.jar'))
    require(len(jars) == 8 and all(sha(p) == jar_hash for p in jars), 'All eight CI jars must match the local final jar')
    ci_records = []
    for job in ci['jobs']:
        platform, backend, profile = job['name'][7:-1].split(', ')
        root = artifacts / f'wwpg-{platform}-{backend}-{profile}' / 'run'
        count = 208 if profile == 'PINOUT' else 204
        full = proofs(root, jar_hash, profile, backend, 'default', ['SETUP', 'VERIFY', 'VERIFY', 'VERIFY'], count)
        source = proofs(root / 'yard', jar_hash, profile, backend, 'wwpg_yard', ['SETUP', 'VERIFY', 'VERIFY', 'VERIFY'], 1)
        extracted = proofs(root / 'exported-yard', jar_hash, profile, backend, 'wwpg_yard', ['VERIFY'] * 3, 1)
        proofs(root / 'example', jar_hash, 'BASE', backend, 'wwpg_example', ['SETUP', 'VERIFY'], 1)
        proofs(root / 'showroom', jar_hash, 'BASE', backend, 'wwpg_showroom', ['SETUP', 'VERIFY'], 1)
        refs = [read(p) for p in sorted((root / 'reference').glob('reference-packaged-*.json'))]
        require(len(refs) == 2 and all(r.get('completed') and r.get('required_tests_passed') == 10 and r['backend'] == backend for r in refs)
                and {r['upstream'] for r in refs} == {True, False}, 'Missing pinned upstream/compatibility references')
        require(not list((artifacts / f'wwpg-{platform}-{backend}-{profile}' / 'build/watchdog').rglob('*threads*')), 'Final CI used a watchdog timeout')
        ci_records.append(dict(platform=platform, backend=backend, profile=profile, packaged_checks=count,
                               full_processes=full, source_yard_processes=source, exact_zip_processes=extracted, references=refs))
    worlds = {}
    clients = []
    for profile in ('BASE', 'PINOUT'):
        world = ROOT / 'build/distributions' / f'{args.client_batch}-{profile}.zip'
        world_hash = sha(world)
        with zipfile.ZipFile(world) as zipped:
            require(zipped.testzip() is None, 'World ZIP CRC failed')
            names = [n for n in zipped.namelist() if n.endswith('/WWPG_MANIFEST.json')]
            require(len(names) == 1, 'Missing exact world manifest')
            manifest = json.loads(zipped.read(names[0]))
            require(manifest['profile'] == profile and manifest['jar']['sha256'] == jar_hash, 'World does not match release jar/profile')
            require(len(manifest['behavior_mapping']) == (165 if profile == 'PINOUT' else 164), 'Incomplete stationary behavior mapping')
            require('native_nether_forced_chunks' in manifest, 'World lost native Nether loading settings')
        worlds[profile] = world
        for backend in ('NATIVE', 'JAVA'):
            cold = ROOT / 'run' / f'{args.client_batch}-exported-{profile}-{backend}'
            extraction = read(cold / 'extracted-yard.json')
            require(extraction['sha256'] == world_hash and extraction['jar']['sha256'] == jar_hash, 'Cold checks used another ZIP or jar')
            records = proofs(cold, jar_hash, profile, backend, 'wwpg_yard', ['VERIFY'] * 3, 1)
            for mode in ('graphical', 'multiplayer'):
                record = read(RECORD / 'evidence' / f'{args.client_batch}-{mode}-{profile}-{backend}' / 'verification.json')
                require(record['passed'] and record['profile'] == profile and record['backend'] == backend
                        and record['jar']['sha256'] == jar_hash, 'Real-client acceptance mismatch')
                require(record.get('exact_archive_sha256', record.get('archive_sha256')) == world_hash, 'Clients used another exact ZIP')
                if mode == 'graphical':
                    require(record['view_count'] == (150 if profile == 'PINOUT' else 148), 'Incomplete real graphical yard')
                else:
                    require(record['real_clients'] == ['WWPG_A', 'WWPG_B'], 'Missing two real TCP clients')
                clients.append(dict(mode=mode, profile=profile, backend=backend, record=record))
            cold_evidence = RECORD / 'evidence' / f'{args.client_batch}-exported-{profile}-{backend}'
            cold_evidence.mkdir(parents=True, exist_ok=True)
            write(cold_evidence / 'verification.json', dict(passed=True, extraction=extraction, processes=records))
            for attempt in range(1, 4):
                log = ROOT / 'build' / f'{args.client_batch}-exported-{profile}-{backend}-{attempt}.log'
                text = log.read_text(encoding='utf-8', errors='replace')
                require('BUILD SUCCESSFUL' in text and 'STATIONARY_YARD_NATIVE_TICKETS_SAVED' in text, 'Exact ZIP cold process did not preserve loading settings')
                (cold_evidence / f'restart-{attempt}.log.gz').write_bytes(gzip.compress(log.read_bytes(), mtime=0))
    supported = read(RECORD / 'content-matrix.json')
    supported['acceptance_record'] = 'verification.json'
    supported['status_definition']['verified'] = 'Passed the final eight-way packaged matrix and corresponding exact playable-world, graphical and two-real-client gates.'
    for row in supported['content']:
        if row['release_scope']:
            row['status'] = 'verified'
            row['note'] = 'Final stationary beta.5 acceptance passed; Pinout requires its optional installation profile.'
    write(RECORD / 'supported-content.json', supported)
    report = dict(schema=1, target=VERSION, stationary_completion=True, tested_artifact=describe(jar),
                  ci=ci['url'], ci_source_commit=ci['headSha'], equation_tests=equation_tests,
                  base_behaviors=164, pinout_behaviors=165, base_packaged_checks=204, pinout_packaged_checks=208,
                  platform_backend_profile_matrix=ci_records, real_clients=clients,
                  released_worlds={p: describe(w) for p, w in worlds.items()},
                  compatibility='Existing beta.4 stationary worlds and native identities remain compatible.',
                  boundaries=['Stationary content only', 'Pinned upstream versions', 'No moving systems, scale certification or public API'],
                  historical_investigations='Preserved in evidence; passing acceptance does not retroactively identify every earlier intermittent failure.')
    write(RECORD / 'verification.json', report)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    downloads = [OUTPUT / jar.name]
    shutil.copyfile(jar, downloads[0])
    for profile, world in worlds.items():
        target = OUTPUT / f'wwpg-{VERSION}-stationary-yard{ "-pinout" if profile == "PINOUT" else ""}.zip'
        shutil.copyfile(world, target)
        downloads.append(target)
    # The distribution manifest contains this archive's hash. Including its
    # previous value would make a rerun embed a stale, self-referential checksum.
    # Ship the manifest in the outer release bundle instead.
    evidence = {p.relative_to(ROOT).as_posix(): p for p in RECORD.rglob('*')
                if p.is_file() and p != RECORD / 'distribution.json'}
    evidence['build/final-build.log'] = args.build_log
    for path in (ROOT / 'build/test-results/test').glob('TEST-*.xml'):
        evidence['equation-tests/' + path.name] = path
    for job in ci['jobs']:
        log = artifacts / f'ci-{job["databaseId"]}.log'
        evidence['ci-logs/' + log.name] = log
    verification_zip = OUTPUT / f'wwpg-{VERSION}-verification.zip'
    archive(verification_zip, evidence)
    downloads.append(verification_zip)
    write(RECORD / 'distribution.json', dict(schema=1, version=VERSION, artifacts=[describe(p) for p in downloads],
          runtime=dict(minecraft='1.21.1', neoforge='21.1.231', java=21, create='6.0.10-280', cee='1.21.1-1.1.3', powergrid='0.6.2', architectury='13.0.8'),
          optional=read(ROOT / 'release/optional-artifacts.json'), solver=dict(primary='PG native v7', fallback='PG Java', substeps=16)))
    bundle_entries = {p.name: p for p in downloads}
    for path in [ROOT / 'LICENSE', ROOT / 'README.md', ROOT / 'CHANGELOG.md', ROOT / 'SPEC.md', ROOT / 'CONTRIBUTING.md',
                 ROOT / 'src/main/resources/META-INF/POWER-GRID-LICENSE.txt',
                 ROOT / 'src/main/resources/META-INF/WWPG-NATIVE-NOTICE.txt',
                 *sorted((ROOT / 'docs').glob('*.md')),
                 ROOT / 'release/artifacts.json', ROOT / 'release/optional-artifacts.json', *sorted(RECORD.glob('*'))]:
        if path.is_file():
            bundle_entries[path.relative_to(ROOT).as_posix()] = path
    bundle = OUTPUT / f'wwpg-{VERSION}-release.zip'
    archive(bundle, bundle_entries)
    downloads.append(bundle)
    checksums = ''.join(f'{sha(p)}  {p.name}\n' for p in downloads)
    (OUTPUT / 'SHA256SUMS.txt').write_text(checksums, encoding='utf-8', newline='\n')
    print(checksums)


if __name__ == '__main__':
    main()
