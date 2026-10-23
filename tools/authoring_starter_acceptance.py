"""Build the downloadable starter independently and verify a frozen runtime.

This captures real build/gameplay evidence; it does not publish or relax gates.
"""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

from package_api_release import package_example
from reopen_stationary_yard import extract as extract_world

ROOT = Path(__file__).resolve().parents[1]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def descriptor(path):
    path = Path(path).resolve()
    return dict(path=path.relative_to(ROOT).as_posix(), sha256=sha(path), bytes=path.stat().st_size)


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def extract(archive, target):
    if target.exists():
        raise ValueError('Preserve earlier extraction: ' + str(target))
    with zipfile.ZipFile(archive) as zipped:
        for item in zipped.infolist():
            if not (target / item.filename).resolve().is_relative_to(target.resolve()):
                raise ValueError('Unsafe archive member')
        if os.name == 'nt':
            zipped.extractall(target)
        else:
            subprocess.run(['unzip', '-q', str(archive), '-d', str(target)], check=True)


def run(command, log, cwd=ROOT):
    if log.exists():
        raise ValueError('Preserve earlier log: ' + str(log))
    with log.open('w', encoding='utf-8') as stream:
        subprocess.run([str(v) for v in command], cwd=cwd, stdout=stream,
                       stderr=subprocess.STDOUT, check=True)


def prepare(output, runtime, repository_zip, runtime_source_sha=None, api_artifact=None):
    if runtime_source_sha is not None and (not isinstance(runtime_source_sha, str)
            or re.fullmatch(r'[0-9a-f]{40}', runtime_source_sha) is None
            or runtime_source_sha == '0' * 40):
        raise ValueError('Supply an immutable forty-character runtime source SHA')
    if output.exists() or not runtime.is_file():
        raise ValueError('Supply a frozen runtime and fresh output directory')
    output.mkdir(parents=True)
    source = output / 'tutorial-load-0.1.0-source.zip'
    package_example(ROOT / 'examples/adjustable-load', ROOT, source)
    repository = output / 'maven repository'
    project = output / 'starter project'
    extract(repository_zip, repository)
    extract(source, project)
    if os.name != 'nt' and not os.access(project / 'gradlew', os.X_OK):
        raise ValueError('Ordinary extraction lost executable wrapper metadata')
    wrapper = project / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    log = output / 'independent-build.log'
    run([sys.executable, ROOT / 'tools/run_checked.py', '--timeout', '1200', '--',
         wrapper, 'build', 'sdkProvenance', '-PapiRepository=' + str(repository),
         '--no-daemon'], log, project)
    provenance_path = project / 'build/sdk-provenance.json'
    provenance = json.loads(provenance_path.read_text(encoding='utf-8'))
    compiled = project / 'build/libs/tutorial-load-0.1.0.jar'
    api = Path(api_artifact) if api_artifact is not None else ROOT / 'build/libs/wwpg-api-1.0.0.jar'
    if provenance['api_sha256'] != sha(api) or provenance['jar_sha256'] != sha(compiled) \
            or provenance['wwpg_project_dependencies']:
        raise ValueError('Starter resolved another API or project dependency')
    project_source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    proof = dict(passed=True, source_sha=runtime_source_sha if runtime_source_sha is not None else project_source,
        project_source_sha=project_source, documentation_source_sha=project_source,
        source_archive_sha256=sha(source), compiled_against_api_sha256=sha(api),
        uses_wwpg_project_classes=False, source=descriptor(source),
        compiled_output=descriptor(compiled), build_log=descriptor(log),
        dependency_proof=descriptor(provenance_path), runtime_sha256=sha(runtime))
    write(output / 'build-proof.json', proof)
    print(json.dumps(dict(passed=True, source=sha(source), starter=sha(compiled))))


def server(output, runtime, backend, test_fixtures=None):
    proof = json.loads((output / 'build-proof.json').read_text(encoding='utf-8'))
    starter = ROOT / proof['compiled_output']['path']
    if sha(runtime) != proof['runtime_sha256'] or sha(starter) != proof['compiled_output']['sha256']:
        raise ValueError('Frozen authoring inputs changed')
    directory = output / ('server-' + backend)
    wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    logs = []
    for phase in ('SETUP', 'VERIFY'):
        log = output / (backend + '-' + phase + '.log')
        run([sys.executable, ROOT / 'tools/run_checked.py', '--timeout', '600', '--',
             wrapper, 'runDocumentationServer', '-I', 'tools/documentation_acceptance/init.gradle',
             '-PdocStarterJar=' + str(starter), '-PdocRuntimeJar=' + str(runtime),
             '-PdocDirectory=' + str(directory), '-PtestNamespaces=wwpg_doc',
             '-PtestBackend=' + backend, '-PdocPhase=' + phase,
             '-Pneo_version=21.1.248', '--no-daemon'] + (['-PtestFixtureArtifact='+str(test_fixtures)] if test_fixtures else []), log)
        text = log.read_text(encoding='utf-8', errors='replace')
        if 'All 4 required tests passed' not in text or 'BUILD FAILED' in text:
            raise ValueError('Starter gameplay did not pass all four fixtures')
        logs.append(log)
    observations = sorted((directory / 'doc-evidence').glob('*.json'))
    reports = [json.loads(p.read_text(encoding='utf-8')) for p in observations]
    if not reports or any(not r['passed'] or r['backend'] != backend
                          or r['runtime_sha256'] != sha(runtime)
                          or r['starter_sha256'] != sha(starter) for r in reports):
        raise ValueError('Starter observations do not match frozen inputs')
    if not any(r.get('pg_wires') and r.get('cee_wires') and r.get('replacement')
               and r.get('unavailable_cleared') for r in reports):
        raise ValueError('Controls, wiring, replacement or unavailable state missing')
    if not any(r.get('phase') == 'VERIFY' and r.get('checked_before_first_solve') for r in reports):
        raise ValueError('Cold persistence was not checked before solving')
    if not any(r.get('actual_unloads', 0) > 0 and r.get('restored_wires_power_load') for r in reports):
        raise ValueError('No actual connected chunk unload/reload')
    report = dict(passed=True, platform='windows-latest' if os.name == 'nt' else 'ubuntu-24.04',
        backend=backend, runtime_sha256=sha(runtime), starter_sha256=sha(starter),
        checks=['controls', 'native_tools', 'unavailable', 'cold_restart_before_solve',
                'actual_chunk_reload', 'replacement', 'dedicated_server'],
        evidence=[descriptor(p) for p in logs + observations])
    if test_fixtures: report['test_fixture_artifact'] = descriptor(test_fixtures)
    write(output / (backend + '-gameplay.json'), report)
    print(json.dumps(dict(passed=True, backend=backend, observations=len(reports))))


def client_world_name(directory):
    """Select the single extracted save without moving its native files."""
    saves = directory / 'saves'
    if not saves.is_dir():
        raise ValueError('Supply one playable world')
    entries = list(saves.iterdir())
    if len(entries) != 1 or not entries[0].is_dir() or entries[0].is_symlink() \
            or not (entries[0] / 'level.dat').is_file():
        raise ValueError('Supply one playable world')
    name = entries[0].name
    if name in ('', '.', '..') or '/' in name or '\\' in name \
            or any(ord(character) < 32 or ord(character) == 127 for character in name):
        raise ValueError('Use a safe save folder basename')
    return name


def client(output, runtime, backend, world, example, test_fixtures=None):
    proof = json.loads((output / 'build-proof.json').read_text(encoding='utf-8'))
    starter = ROOT / proof['compiled_output']['path']
    if sha(runtime) != proof['runtime_sha256'] or sha(starter) != proof['compiled_output']['sha256']:
        raise ValueError('Frozen authoring inputs changed')
    directory = output / ('client-' + backend)
    if directory.exists():
        raise ValueError('Preserve earlier graphical attempt')
    extract_world(world, directory, client=True)
    save_name = client_world_name(directory)
    launcher_source = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if re.fullmatch(r'[0-9a-f]{40}', launcher_source) is None:
        raise ValueError('Capture the immutable launcher source commit')
    launcher_inputs = [Path(__file__).resolve(), ROOT / 'tools/documentation_acceptance/init.gradle']
    launcher_hashes = [sha(path) for path in launcher_inputs]
    launcher_directory = directory / 'launcher-inputs'
    launcher_directory.mkdir()
    launcher_tools = []
    for path in launcher_inputs:
        retained = launcher_directory / path.name
        shutil.copyfile(path, retained)
        launcher_tools.append(descriptor(retained))
    if launcher_hashes != [item['sha256'] for item in launcher_tools]:
        raise ValueError('Launcher snapshots differ from their captured inputs')
    wrapper = ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    log = output / (backend + '-client.log')
    run([sys.executable, ROOT / 'tools/run_checked.py', '--timeout', '600', '--',
         wrapper, 'runDocumentationClient', '-I', 'tools/documentation_acceptance/init.gradle',
         '-PdocStarterJar=' + str(starter), '-PdocRuntimeJar=' + str(runtime),
         '-PdocExampleJar=' + str(example), '-PdocDirectory=' + str(directory),
         '-PdocWorldName=' + save_name,
         '-PtestBackend=' + backend, '-Pneo_version=21.1.248', '--no-daemon'] + (['-PtestFixtureArtifact='+str(test_fixtures)] if test_fixtures else []), log)
    if launcher_hashes != [sha(path) for path in launcher_inputs]:
        raise ValueError('Launcher tools changed during the graphical check')
    text = log.read_text(encoding='utf-8', errors='replace')
    observation = directory / 'doc-client-result.json'
    result = json.loads(observation.read_text(encoding='utf-8'))
    helper = ROOT / 'build/doc-acceptance/wwpg-documentation-acceptance.jar'
    if 'DOC_CLIENT_PASSED' not in text or 'DOC_CLIENT_FAILED' in text \
            or 'BUILD SUCCESSFUL' not in text or not result['passed'] \
            or result['backend'] != backend or result['runtime_sha256'] != sha(runtime) \
            or result['starter_sha256'] != sha(starter) or result['helper_sha256'] != sha(helper) \
            or not result['controls'] or not result['native_tools'] \
            or abs(result['client_meter_volts'] - 20) >= .1:
        raise ValueError('Graphical result differs from actual frozen inputs or required interactions')
    saved_helper = directory / 'acceptance-helper.jar'
    shutil.copyfile(helper, saved_helper)
    program = directory / 'documentation-client-program.txt'
    if not program.is_file():
        raise ValueError('Retain the actual single-player launcher argument file')
    vm_arguments = directory / 'documentation-client-vm-args.txt'
    if not vm_arguments.is_file():
        raise ValueError('Retain the expanded single-player launcher VM arguments')
    multiplayer = [line.strip() for line in vm_arguments.read_text(encoding='utf-8').splitlines()
                   if line.strip().startswith('-Dwwpg.test.multiplayer=')]
    if not multiplayer or multiplayer[-1] != '-Dwwpg.test.multiplayer=false':
        raise ValueError('The documentation client must disable multiplayer automation')
    retained = [log, observation, saved_helper, program, vm_arguments]
    for name in ('load-100-on.png', 'load-400-off.png'):
        screenshot = directory / 'screenshots' / name
        if not screenshot.is_file() or screenshot.stat().st_size < 1000:
            raise ValueError('Missing actual graphical screenshot')
        retained.append(screenshot)
    report = dict(passed=True, backend=backend, runtime_sha256=sha(runtime),
        starter_sha256=sha(starter), controls=True, native_tools=True,
        source_sha=proof['source_sha'], evidence=[descriptor(p) for p in retained],
        input_save_name=save_name, launcher_program=descriptor(program),
        launcher_vm_arguments=descriptor(vm_arguments),
        launcher_source_sha=launcher_source, launcher_tools=launcher_tools,
        input_world=descriptor(world), source_example=descriptor(example))
    if test_fixtures: report['test_fixture_artifact'] = descriptor(test_fixtures)
    write(output / (backend + '-graphical.json'), report)
    print(json.dumps(dict(passed=True, backend=backend, meter_volts=result['client_meter_volts'])))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['prepare', 'server', 'client'])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--runtime', type=Path, required=True)
    parser.add_argument('--maven', type=Path,
                        default=ROOT / 'build/distributions/wwpg-api-1.0.0-maven.zip')
    parser.add_argument('--runtime-source-sha',
                        help='Prepare only: bind unchanged compiled artifacts to their accepted runtime source; record this checkout separately')
    parser.add_argument('--backend', choices=['NATIVE', 'JAVA'])
    parser.add_argument('--world', type=Path)
    parser.add_argument('--example', type=Path)
    parser.add_argument('--test-fixtures', type=Path)
    args = parser.parse_args()
    output, runtime = args.output.resolve(), args.runtime.resolve()
    if not output.is_relative_to(ROOT) or not runtime.is_relative_to(ROOT):
        parser.error('Inputs and evidence must be inside the workspace')
    if args.runtime_source_sha is not None and args.mode != 'prepare':
        parser.error('--runtime-source-sha applies only when preparing a new independent build')
    if args.mode == 'prepare':
        prepare(output, runtime, args.maven.resolve(), args.runtime_source_sha)
    elif args.mode == 'server':
        if not args.backend:
            parser.error('server needs --backend')
        server(output, runtime, args.backend, args.test_fixtures)
    else:
        if not args.backend or not args.world or not args.example:
            parser.error('client needs --backend, --world and --example')
        client(output, runtime, args.backend, args.world.resolve(), args.example.resolve(), args.test_fixtures)
