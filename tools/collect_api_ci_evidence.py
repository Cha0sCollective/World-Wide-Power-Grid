"""Derive candidate API matrix evidence from actual GitHub metadata and artifacts.

No Gradle execution, API freeze, publication, or progress edits occur here. Every
final-job packaged attempt must be complete; a later retry cannot erase a failure.
Client and independently extracted SDK acceptance remain separate release gates.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from package_api_release import BEHAVIORS, COMBINATIONS, API_VERSION, VERSION, checked_zip, validate_api_metadata, validate_version_accessor
from verify_api_compatibility import snapshot

ROOT = Path(__file__).resolve().parents[1]
REPO = 'Cha0sCollective/World-Wide-Power-Grid'
API_NAMESPACES = {'wwpg_api', 'wwpg_api_faults', 'wwpg_api_restart',
                  'wwpg_api_upstream', 'wwpg_api_upstream_restart'}
HASH = re.compile(r'[0-9a-f]{64}')
ARTIFACTS = {'runtime': f'build/libs/wwpg-{VERSION}.jar', 'api': 'build/libs/wwpg-api-1.0.0.jar',
             'cee_api': 'build/libs/wwpg-cee-api-1.0.0.jar',
             'example': 'examples/extension-addon/build/libs/wwpg-api-example-1.0.0.jar',
             'upstream_example': 'examples/cee-addon/build/libs/wwpg-upstream-api-example-1.0.0.jar'}


class EvidenceError(ValueError): pass


def require(condition, message):
    if not condition: raise EvidenceError(message)


def read(path): return json.loads(Path(path).read_text(encoding='utf-8'))
def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def descriptor(path, root):
    path, root = Path(path).resolve(), Path(root).resolve()
    require(path.is_relative_to(root) and path.is_file(), f'Evidence absent/outside workspace: {path}')
    return dict(path=path.relative_to(root).as_posix(), sha256=sha(path), bytes=path.stat().st_size)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def metadata(run, jobs, index):
    require(run.get('status') == 'completed' and run.get('conclusion') == 'success', 'Full CI run is not passing/complete')
    require(run.get('path') == '.github/workflows/build.yml', 'Wrong CI workflow')
    require(re.fullmatch(r'[0-9a-f]{40}', run.get('head_sha', '')), 'CI lacks its actual immutable source SHA')
    require(run.get('html_url') == f'https://github.com/{REPO}/actions/runs/{run.get("id")}', 'Wrong repository/run provenance')
    expected = {f'build ({platform}, {backend}, {profile})': (platform, backend, profile)
                for platform, backend, profile in COMBINATIONS}
    values = jobs.get('jobs', [])
    require(len(values) == 8 and {job.get('name') for job in values} == set(expected), 'Missing/duplicate eight-way CI job')
    required_steps = {'Record the exact checked-out source and CI attempt',
                      'Build runtime and publish both SDK APIs to an isolated local Maven repository',
                      'Build the general example independently against published Maven API artifacts',
                      'Build the CEE panel and PG board example independently against published APIs',
                      'Verify global freeze and one-tick stepping in an isolated API fixture',
                      'Verify stopped-before-ready persisted wire retirement'}
    artifacts = {}
    for item in index.get('artifacts', []):
        if not item.get('name', '').startswith('wwpg-'): continue
        require(item['name'] not in artifacts, 'Duplicate uploaded artifact names; retain and resolve earlier attempts')
        require(item.get('expired') is False and item.get('workflow_run', {}).get('id') == run['id']
                and item.get('workflow_run', {}).get('head_sha') == run['head_sha'], 'Artifact provenance/expiration mismatch')
        artifacts[item['name']] = item
    result = []
    for job in values:
        require(job.get('status') == 'completed' and job.get('conclusion') == 'success', 'A required matrix job failed')
        require(job.get('run_id') == run['id'] and job.get('run_attempt') == run.get('run_attempt'), 'Job belongs to a different run/attempt')
        steps = {step.get('name'): step for step in job.get('steps', [])}
        require(required_steps <= steps.keys() and all(steps[name].get('conclusion') == 'success' for name in required_steps),
                'Actual runtime/independent SDK/pause job steps did not pass')
        platform, backend, profile = expected[job['name']]
        name = f'wwpg-{platform}-{backend}-{profile}'
        require(name in artifacts, f'Missing actual uploaded job artifact: {name}')
        result.append((job, artifacts[name], (platform, backend, profile)))
    return result


def packaged(directory, namespaces, phases, hashes, profile, backend, evidence):
    paths = sorted(directory.glob('packaged-*.json'))
    require(len(paths) == len(phases), f'Unexpected packaged process/attempt count: {directory}')
    records = [read(path) for path in paths]
    require([item.get('phase') for item in records] == phases, f'Missing consecutive cold phase sequence: {directory}')
    for path, item in zip(paths, records):
        require(item.get('completed') and isinstance(item.get('required_tests_passed'), int)
                and item['required_tests_passed'] > 0, f'Incomplete/failed packaged attempt remains unresolved: {path}')
        require(item.get('artifact') == f'wwpg-{VERSION}.jar' and item.get('sha256') == hashes['runtime']
                and item.get('profile') == profile and item.get('backend') == backend, f'Packaged artifact/profile/backend mismatch: {path}')
        actual = set(item.get('namespaces', '').split(','))
        require(actual == set(namespaces), f'Packaged namespace mismatch: {path}')
        if any(name.startswith('wwpg_api') for name in namespaces):
            addons = item.get('addon_artifacts', {})
            require(set(addons) == {'example', 'upstream_example'} and all(addons[role].get('sha256') == hashes[role] for role in addons),
                    f'Packaged example artifact mismatch: {path}')
        evidence.append(path)
    require(len({item['required_tests_passed'] for item in records}) == 1, f'Restarts silently changed the required test count: {directory}')
    return records


def log(path, evidence):
    require(path.is_file(), f'Missing retained actual log: {path}')
    value = path.read_text(encoding='utf-8', errors='replace')
    require('GAME TESTS COMPLETE' in value and re.search(r'All [1-9][0-9]* required tests passed', value), f'No completed passing GameTest summary: {path}')
    require(not re.search(r'[1-9][0-9]* required tests failed|Tests? .* failed at|API_WORKSHOP_SERVER_FAILED', value), f'Failed final-attempt log: {path}')
    evidence.append(path); return value


def cycles(text, prefix):
    values = re.findall(re.escape(prefix) + r': cycle=(\d+)[,;]\s*actualUnloads=(\d+)[,;]\s*preSolveStateVerified=true', text)
    require(len(values) == 5 and [int(pair[0]) for pair in values] == list(range(1, 6))
            and all(int(cycle) == int(unloads) for cycle, unloads in values), f'Missing five actual pre-solve unload cycles: {prefix}')
    return 5


def marker(text, name, expected):
    """Require an explicit assertion marker; old/partial logs cannot certify it."""
    matches = re.findall(r'\b' + re.escape(name) + r': ([^\r\n]+)', text)
    require(len(matches) == 1, f'Missing/duplicate actual assertion marker: {name}')
    fields = {}
    for entry in re.split(r'[,;]\s*', matches[0]):
        key, separator, value = entry.partition('=')
        require(separator and key not in fields, f'Malformed/duplicate assertion field: {name}')
        fields[key] = value
    require(all(fields.get(key) == str(value) for key, value in expected.items()), f'Incomplete actual assertions: {name}')
    return fields


def cold_replacement(text):
    marker(text, 'API_COLD_REPLACEMENT', dict(diskStateVerified='true', delayedEntities='true',
        oldWiresRetired='true', newWiresPowered='true', oldCeeWiresRetired='true', newCeeWiresPowered='true', ownershipVersion='2'))


def nonconvergence(text):
    marker(text, 'API_NONCONVERGENCE_PASSED', dict(rollback='true', healthyCircuit='true', recovered='true'))


def pending_retirement(armed, verified):
    marker(armed, 'API_PENDING_RESTART_ARMED', dict(actualInboxHeld='true', savedBeforeReady='true',
        replacementZeroCharge='true', nativePG='true', nativeCEE='true'))
    marker(verified, 'API_PENDING_RESTART_PASSED', dict(actualInboxHeld='true', savedBeforeReady='true',
        diskTombstonesVerified='true', nativePG='true', nativeCEE='true', replacementIsolated='true',
        replacementZeroCharge='true', newWiresPowered='true', ownershipVersion='2'))


def world(archive, extraction, hashes, profile, api, evidence):
    require(extraction.get('sha256') == sha(archive) and extraction.get('profile') == profile
            and extraction.get('jar', {}).get('sha256') == hashes['runtime'], 'Extracted save does not bind the exact ZIP/jar/profile')
    with checked_zip(archive) as zipped:
        candidates = [name for name in zipped.namelist() if name.endswith('/WWPG_MANIFEST.json')]
        require(len(candidates) == 1 and any(name.endswith('/level.dat') for name in zipped.namelist()), 'Playable ZIP manifest/save missing')
        manifest = json.loads(zipped.read(candidates[0]))
        require(manifest.get('target') == VERSION and manifest.get('profile') == profile
                and manifest.get('jar', {}).get('sha256') == hashes['runtime'], 'World manifest target/runtime/profile differs')
        if api:
            require(manifest.get('target') == VERSION and manifest.get('api_version') == API_VERSION, 'Wrong API Workshop target/version')
            require(all(manifest.get('example_addons', {}).get(role, {}).get('sha256') == hashes[role]
                        for role in ('example', 'upstream_example')), 'Workshop contains another example add-on identity')
        else:
            require(manifest.get('support_baseline') == 'release/0.1.0-beta.5/content-matrix.json', 'Stationary yard lacks the declared inherited content baseline')
    evidence.append(archive)


def audit_sdk(directory, hashes, evidence):
    validate_version_accessor(snapshot(directory / ARTIFACTS['api']))
    with checked_zip(directory / ARTIFACTS['runtime']) as runtime:
        validate_api_metadata(runtime, 'WWPG runtime')
        for role, prefix in [('api', 'org/cha0scollective/wwpg/api/'), ('cee_api', 'org/cha0scollective/wwpg/api/cee/')]:
            with checked_zip(directory / ARTIFACTS[role]) as api:
                validate_api_metadata(api, role + ' SDK')
                classes = [name for name in api.namelist() if name.endswith('.class')]
                require(classes and all(name.startswith(prefix) for name in classes), 'SDK includes non-API classes')
                require(all(name in runtime.namelist() and runtime.read(name) == api.read(name) for name in classes), 'Runtime API differs from published SDK')
    for role, folder in [('example', 'extension-addon'), ('upstream_example', 'cee-addon')]:
        source = directory / f'build/distributions/{Path(ARTIFACTS[role]).stem}-source.zip'
        with checked_zip(source) as archive:
            names = set(archive.namelist())
            require({'build.gradle', 'settings.gradle', 'gradlew', 'gradlew.bat', 'LICENSE', 'gradle/wrapper/gradle-wrapper.jar'} <= names,
                    'Independent example archive lacks its own wrapped project')
            build = archive.read('build.gradle').decode('utf-8')
            require('org.cha0scollective.wwpg:wwpg-api:1.0.0' in build and not re.search(r'\b(?:project|includeBuild)\s*\(', build),
                    'Example uses a WWPG source project instead of the published API')
            for name in names:
                if name.endswith('.java'):
                    imports = re.findall(r'\bimport\s+(?:static\s+)?(org\.cha0scollective\.wwpg\.[\w.*]+)', archive.read(name).decode('utf-8'))
                    require(all(value.startswith(('org.cha0scollective.wwpg.api.', 'org.cha0scollective.wwpg.example.',
                                                  'org.cha0scollective.wwpg.examples.')) for value in imports), 'Example source imports WWPG implementation')
        with checked_zip(directory / ARTIFACTS[role]) as archive:
            require(not any(name.startswith('org/cha0scollective/wwpg/api/') and name.endswith('.class') for name in archive.namelist()), 'Example shades API classes')
        evidence.append(source)


def checked_source(path, source_sha, run_id, run_attempt, evidence):
    require(path.is_file(), 'Actual checked-out source provenance is absent')
    value = read(path)
    require(value.get('schema') == 1 and value.get('source_sha') == source_sha
            and re.fullmatch(r'[0-9a-f]{40}', value.get('source_tree', ''))
            and value.get('run_id') == run_id and value.get('run_attempt') == run_attempt
            and value.get('event') in {'pull_request', 'push', 'workflow_dispatch'}, 'Actual checkout differs from frozen run source/attempt')
    evidence.append(path)
    return value


def matrix_job(directory, combination, source_sha, run_id, run_attempt, root=ROOT):
    platform, backend, profile = combination
    evidence = []
    source = checked_source(directory / 'build/ci-source.json', source_sha, run_id, run_attempt, evidence)
    paths = {role: directory / path for role, path in ARTIFACTS.items()}
    require(all(path.is_file() for path in paths.values()), 'Matrix upload omitted a frozen API/runtime/example artifact')
    hashes = {role: sha(path) for role, path in paths.items()}; evidence.extend(paths.values())
    audit_sdk(directory, hashes, evidence)
    run = directory / 'run'
    # An incomplete earlier attempt within this final job remains a blocker.
    for path in run.rglob('packaged-*.json'):
        item = read(path)
        require(item.get('completed') and item.get('required_tests_passed', 0) > 0, f'Unresolved earlier failed/incomplete attempt: {path}')
    require(not list((directory / 'build/watchdog').rglob('*threads*')), 'Final job required a watchdog timeout')
    full = packaged(run, {'default'}, ['SETUP', 'VERIFY', 'VERIFY', 'VERIFY'], hashes, profile, backend, evidence)
    count = full[0]['required_tests_passed']
    require(count >= (208 if profile == 'PINOUT' else 204), 'Inherited regression coverage was reduced')
    api = packaged(run / 'api', API_NAMESPACES, ['SETUP', 'VERIFY', 'VERIFY', 'VERIFY'], hashes, profile, backend, evidence)
    pause = packaged(run / 'api-pause', {'wwpg_api_pause'}, ['SETUP'], hashes, profile, backend, evidence)
    pending_restart = packaged(run / 'api-pending-restart', {'wwpg_api_pending_restart'},
        ['SETUP', 'ARM', 'VERIFY'], hashes, profile, backend, evidence)
    pending_logs = {phase: log(run / 'api-pending-restart' / (phase.lower() + '-phase.log'), evidence)
                    for phase in ('SETUP', 'ARM', 'VERIFY')}
    pending_retirement(pending_logs['ARM'], pending_logs['VERIFY'])
    pending_oracle = run / 'api-pending-restart/extension-api-pending-before-restart.nbt'
    require(pending_oracle.is_file() and pending_oracle.stat().st_size > 0,
            'Stopped-before-ready restart lacks its actual saved-state oracle')
    evidence.append(pending_oracle)
    pending_files = sorted((run / 'api-pending-restart').glob('packaged-*.json'))
    pending_proof = dict(oracle=descriptor(pending_oracle, root), phases=[
        dict(phase=phase, manifest=descriptor(path, root),
             log=descriptor(run / 'api-pending-restart' / (phase.lower() + '-phase.log'), root))
        for phase, path in zip(('SETUP', 'ARM', 'VERIFY'), pending_files)])
    latest = log(run / 'api/logs/latest.log', evidence)
    nonconvergence(latest)
    require(cycles(latest, 'API_CHUNK_CYCLE') == cycles(latest, 'API_UPSTREAM_CHUNK_CYCLE') == 5, 'Actual unload coverage incomplete')
    audits = re.findall(r'WWPG_TEST_SOLVER_AUDIT: independentCeeSolves=(\d+)[,;]\s*backend=(NATIVE|JAVA)[,;]\s*observedSubsteps=(\d+)', latest)
    require(audits and all(cee == '0' and selected == backend and int(steps) > 0 for cee, selected, steps in audits), 'Missing zero-CEE actual backend audit')
    paused = log(run / 'api-pause/logs/latest.log', evidence)
    marker(paused, 'API_PAUSE_PASSED', dict(frozenCallbacks='0', steppedCommits='1', resumed='true', pausedMeasurementsUnavailable='true'))
    for number in range(1, 4):
        log(run / f'restart-{number}.log', evidence)
        api_log = log(run / f'api/restart-{number}.log', evidence)
        cycles(api_log, 'API_CHUNK_CYCLE'); cycles(api_log, 'API_UPSTREAM_CHUNK_CYCLE')
        cold_replacement(api_log); nonconvergence(api_log)
    for name in ['extension-api-before-restart.nbt', 'upstream-api-before-restart.nbt', 'extension-api-replacement-before-restart.nbt']:
        oracle = run / 'api' / name
        require(oracle.is_file() and oracle.stat().st_size > 0, f'Pre-solve saved-state oracle missing: {name}')
        evidence.append(oracle)
    for namespace, folder in [('wwpg_yard', 'yard'), ('wwpg_api_world', 'api-workshop')]:
        packaged(run / folder, {namespace}, ['SETUP', 'VERIFY', 'VERIFY', 'VERIFY'], hashes, profile, backend, evidence)
        packaged(run / ('exported-' + folder), {namespace}, ['VERIFY'] * 3, hashes, profile, backend, evidence)
        for origin in [folder, 'exported-' + folder]:
            for number in range(1, 4): log(run / origin / f'restart-{number}.log', evidence)
        extraction_path = run / ('exported-' + folder) / 'extracted-yard.json'; extraction = read(extraction_path); evidence.append(extraction_path)
        archive = directory / f'build/distributions/wwpg-{ "api-workshop" if namespace == "wwpg_api_world" else "stationary" }-{profile}.zip'
        world(archive, extraction, hashes, profile, namespace == 'wwpg_api_world', evidence)
        if namespace == 'wwpg_api_world':
            for origin in [folder, 'exported-' + folder]:
                station_path = run / origin / 'api-workshop-stations.json'; stations = read(station_path); evidence.append(station_path)
                require(stations.get('completed') is True and stations.get('runtime_station_checks') == 11
                        and stations.get('native_item_workflows') is True and stations.get('phase') == 'VERIFY'
                        and stations.get('profile') == profile and stations.get('backend') == backend
                        and len(stations.get('stations', [])) == 11 and all(item.get('checked') is True for item in stations['stations']),
                        'Source/extracted Workshop did not verify all eleven native/API stations')
    for namespace, folder in [('wwpg_example', 'example'), ('wwpg_showroom', 'showroom')]:
        packaged(run / folder, {namespace}, ['SETUP', 'VERIFY'], hashes, 'BASE', backend, evidence)
        log(run / folder / 'logs/latest.log', evidence)
    references = sorted((run / 'reference').glob('reference-packaged-*.json'))
    require(len(references) == 2, 'Pinned upstream/compatibility reference processes absent')
    values = [read(path) for path in references]
    require({item.get('upstream') for item in values} == {False, True} and all(item.get('completed') and item.get('required_tests_passed', 0) > 0
            and item.get('backend') == backend for item in values), 'Pinned reference process failed/incomplete')
    evidence.extend(references)
    xmls = sorted((directory / 'build/test-results/test').glob('TEST-*.xml'))
    if not xmls: xmls = sorted((directory / 'build/reports/tests').rglob('TEST-*.xml'))
    require(xmls, 'Actual analytical/unit JUnit results missing from CI upload')
    tests = 0
    for path in xmls:
        suite = ET.parse(path).getroot()
        require(int(suite.attrib.get('failures', 0)) == int(suite.attrib.get('errors', 0)) == 0, 'JUnit equation/contract/state check failed')
        tests += int(suite.attrib.get('tests', 0)); evidence.append(path)
    require(tests > 0, 'No actual JUnit checks ran')
    return dict(schema=1, completed=True, passed=True, unresolved_failures=[], artifact_hashes=hashes,
                platform=platform, backend=backend, profile=profile, regression_checks_passed=count,
                extension_checks_passed=api[0]['required_tests_passed'] + pause[0]['required_tests_passed'] + pending_restart[0]['required_tests_passed'],
                api_namespaces_passed=sorted(API_NAMESPACES | {'wwpg_api_pause', 'wwpg_api_pending_restart'}), cold_restart_passes=3,
                actual_chunk_reload_cycles=5, independent_cee_solves=0, exactly_once_history=True,
                cee_property_adapter_verified=True, global_pause_verified=True, api_fixture_behaviors=sorted(BEHAVIORS),
                nonconverging_backend_verified=True, paused_measurements_unavailable=True,
                cold_wire_systems_verified=['CEE', 'POWER_GRID'], cold_ownership_format_version=2,
                source_sha=source['source_sha'], source_tree=source['source_tree'], checked_out_source_verified=True,
                pending_wire_retirement_verified=True, pending_entity_restart_phases=['SETUP', 'ARM', 'VERIFY'],
                pending_entity_restart=pending_proof,
                junit_checks_passed=tests, evidence_files=evidence)


def aggregate(run_path, jobs_path, index_path, artifacts, output, root=ROOT):
    root, artifacts, output = Path(root).resolve(), Path(artifacts).resolve(), Path(output).resolve()
    require(artifacts.is_relative_to(root) and output.is_relative_to(root) and not output.exists(), 'Use a fresh workspace evidence directory')
    run, jobs, index = read(run_path), read(jobs_path), read(index_path)
    rows = metadata(run, jobs, index)
    proofs, hashes = [], None
    for job, artifact, combination in sorted(rows, key=lambda row: row[2]):
        proof = matrix_job(artifacts / artifact['name'], combination, run['head_sha'], run['id'], run['run_attempt'], root)
        require(hashes is None or proof['artifact_hashes'] == hashes, 'Platform jobs built different frozen API/runtime/example artifacts')
        hashes = proof['artifact_hashes']
        proof['evidence_files'] = [descriptor(path, root) for path in sorted(set(proof['evidence_files']))]
        proof['checked_out_source'] = descriptor(artifacts / artifact['name'] / 'build/ci-source.json', root)
        proof['github_job_id'] = job['id']; proof['github_artifact_id'] = artifact['id']
        proofs.append((job, combination, proof))
    output.mkdir(parents=True)
    ci = dict(schema=1, source_sha=run['head_sha'], status=run['status'], conclusion=run['conclusion'],
              url=run['html_url'], run_id=run['id'], run_attempt=run['run_attempt'], jobs=[],
              inputs={key: descriptor(path, root) for key, path in [('run', run_path), ('jobs', jobs_path), ('artifacts', index_path)]})
    require(len({proof['source_tree'] for _, _, proof in proofs}) == 1, 'CI jobs checked out different source trees')
    ci['source_tree'] = proofs[0][2]['source_tree']
    for job, (platform, backend, profile), proof in proofs:
        path = output / f'{platform}-{backend}-{profile}.json'; write(path, proof)
        ci['jobs'].append(dict(platform=platform, backend=backend, profile=profile, status=job['status'],
                               conclusion=job['conclusion'], id=job['id'], url=job['html_url'], proof=descriptor(path, root)))
    write(output / 'ci.json', ci)
    for role, key in [('example', 'independent-example-build'), ('upstream_example', 'independent-upstream-example-build')]:
        value = dict(schema=1, completed=True, passed=True, source_sha=run['head_sha'], artifact_hashes=hashes,
                     unresolved_failures=[], uses_published_api_only=True, uses_wwpg_project_classes=False,
                     compiled_against_api_sha256=hashes['api'],
                     functional_behaviors=['machine', 'storage', 'instrument'] if role == 'example' else ['panel_attachment', 'board_component'],
                     provenance=descriptor(output / 'ci.json', root), acceptance_scope='independent CI source project build; extracted SDK/client gates remain separate')
        if role == 'upstream_example': value['compiled_against_cee_api_sha256'] = hashes['cee_api']
        write(output / (key + '.json'), value)
    summary = dict(schema=1, source_sha=run['head_sha'], artifact_hashes=hashes, matrix_evidence_complete=True,
                   api_frozen=False, publishable=False, state='packaged_matrix_candidate',
                   remaining=['Exact downloadable SDK extraction/build', 'Graphical and two real TCP clients on both profiles/backends',
                              'Review unresolved historical failures and complete six release gates', 'Frozen release manifest/package validation'])
    write(output / 'CI_EVIDENCE_STATUS.json', summary)
    return summary


def download(run_id, destination):
    require(not destination.exists() and destination.resolve().is_relative_to(ROOT), 'Use a fresh workspace CI download')
    destination.mkdir(parents=True)
    for name, endpoint in [('run', ''), ('jobs', '/jobs'), ('artifacts', '/artifacts')]:
        args = ['gh', 'api', f'repos/{REPO}/actions/runs/{run_id}{endpoint}']
        result = subprocess.run(args, check=True, capture_output=True, text=True, encoding='utf-8')
        (destination / (name + '.json')).write_text(result.stdout, encoding='utf-8')
    metadata(read(destination / 'run.json'), read(destination / 'jobs.json'), read(destination / 'artifacts.json'))
    subprocess.run(['gh', 'run', 'download', str(run_id), '--repo', REPO, '--dir', str(destination / 'artifacts')], check=True)
    return destination / 'run.json', destination / 'jobs.json', destination / 'artifacts.json', destination / 'artifacts'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--collect-ci', type=int, help='Read/download the actual complete GitHub run using gh')
    parser.add_argument('--download', type=Path, help='Fresh directory for --collect-ci inputs/artifacts')
    parser.add_argument('--run-json', type=Path); parser.add_argument('--jobs-json', type=Path)
    parser.add_argument('--artifact-index', type=Path); parser.add_argument('--artifacts', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.collect_ci:
            require(args.download is not None, '--collect-ci needs a fresh --download directory')
            inputs = download(args.collect_ci, args.download)
        else:
            require(all([args.run_json, args.jobs_json, args.artifact_index, args.artifacts]), 'Supply actual run/jobs/artifact-index JSON and downloaded artifact tree')
            inputs = args.run_json, args.jobs_json, args.artifact_index, args.artifacts
        result = aggregate(*inputs, args.output)
        print(json.dumps(result))
    except (EvidenceError, OSError, ValueError, KeyError, ET.ParseError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(2, f'API CI evidence blocked: {error}\n')


if __name__ == '__main__': main()
