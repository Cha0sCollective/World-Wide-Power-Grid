"""Freeze and run explicitly selected CEE readiness checks; never acceptance."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
import sys
import time
import tomllib
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
CEE = {
    'cee120': ('1.21.1-1.2.0-132', '71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7'),
    'cee114': ('1.21.1-1.1.4', '2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f'),
    'cee113': ('1.21.1-1.1.3', 'ae7ea74b7b00f175daa5a60a953a6d57f7f775aed0b642a1e9f80c3865cf8be7'),
}
GROUPS = {
    'core': ['wwpg_cee_readiness', 'wwpg_cee_migration'],
    'lifecycle': ['wwpg_cee_lifecycle'],
    'ownership': ['wwpg_cee_ownership'],
    'adapters': ['wwpg_audit_endpoints', 'wwpg_cee_adapters'],
    'stock': ['wwpg_cee_stock'],
    'cold-migration': ['wwpg_cee_migration_cold'],
    'fans': ['wwpg_cee_fans'],
    'baseline': ['wwpg_cee_baseline', 'wwpg_cee_baseline_train'],
}
COUNTS = {'core': 10, 'lifecycle': 17, 'ownership': 1, 'adapters': 7, 'stock': 4, 'cold-migration': 1, 'fans': 2, 'baseline': 7}
EXCLUDED = ['build', 'jar', 'gameTestJar', 'referenceJar', 'test',
            'compileJava', 'compileExtensionApiJava', 'compileCeeApiJava',
            'compileGameTestJava', 'compileCee12Java', 'compileCee12GameTestJava',
            'compileReferenceControlJava', 'processResources', 'processGameTestResources']


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def write(path, value):
    Path(path).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def metadata(path, mod_id):
    with ZipFile(path) as jar:
        data = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))
    matches = [mod for mod in data['mods'] if mod['modId'] == mod_id]
    if len(matches) != 1:
        raise ValueError(f'Expected one {mod_id} descriptor: {path}')
    return str(matches[0]['version'])


def validate_cee(role, path):
    version = metadata(path, 'electroenergetics')
    expected_version, expected_hash = CEE[role]
    if version != expected_version or sha(path) != expected_hash:
        raise ValueError(f'{role}: expected {expected_version}/{expected_hash}, found {version}/{sha(path)}')
    return version


def relative(value):
    path = PurePosixPath(value.replace('\\', '/'))
    if path.is_absolute() or '..' in path.parts or ':' in value:
        raise ValueError('Source path must remain relative to its snapshot: ' + value)
    return Path(*path.parts)


def output_path(value):
    path = (ROOT / value).resolve()
    if not any(path.is_relative_to(ROOT / base) and path != ROOT / base for base in ('local', 'build')):
        raise ValueError('Readiness output must be a fresh directory under local/ or build/')
    if path.exists():
        raise ValueError('Preserve the existing attempt; choose a fresh output: ' + str(path))
    return path


def verify(inputs, manifest):
    for role, item in inputs['artifacts'].items():
        if sha(item['path']) != item['sha256']:
            raise ValueError('Frozen artifact changed: ' + role)
        if role in CEE:
            validate_cee(role, item['path'])
    for item in inputs['sources']:
        if sha(manifest.parent / 'sources' / relative(item['path'])) != item['sha256']:
            raise ValueError('Frozen source changed: ' + item['path'])


def freeze(args):
    previous = Path(args.from_inputs).resolve()
    old = json.loads(previous.read_text())
    files = {role: Path(item['path']) for role, item in old['artifacts'].items()}
    files['cee113'] = Path(args.cee113).resolve()
    new_runtime = getattr(args, 'runtime', None)
    if new_runtime:
        if not args.current_sources:
            raise ValueError('A new runtime requires --current-sources')
        files['runtime'] = Path(new_runtime).resolve()
    if args.gametest:
        files['gametest'] = Path(args.gametest).resolve()
    if args.reference:
        files['reference'] = Path(args.reference).resolve()
    # Validate replacements before creating any output, including the corrected 1.1.3 input.
    for role, path in files.items():
        if role in CEE:
            validate_cee(role, path)
        elif path == Path(old['artifacts'][role]['path']) and sha(path) != old['artifacts'][role]['sha256']:
            raise ValueError('Previous artifact changed: ' + role)
    version = metadata(files['runtime'], 'wwpg')
    if metadata(files['gametest'], 'wwpg_tests') != version or metadata(files['reference'], 'wwpg') != version:
        raise ValueError('Gameplay, companion and reference versions must match')
    if args.current_sources:
        # A replacement companion/control does not authorize describing a different player jar as tested.
        for source in ([] if new_runtime else old['sources']):
            if source['path'].startswith(('src/main/', 'src/cee12/', 'src/api/', 'src/ceeApi/')):
                if sha(ROOT / relative(source['path'])) != source['sha256']:
                    raise ValueError('Player source changed; build and freeze a new runtime first: ' + source['path'])
        names = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard'], cwd=ROOT, text=True).splitlines()
        source_files = [(name, ROOT / name) for name in names if name.startswith(('src/', 'tools/reference_control/')) or name in ('build.gradle', 'gradle.properties', 'release/artifacts.json', 'tools/test-suites.json', 'tools/cee_readiness.py')]
    else:
        source_files = [(item['path'], previous.parent / 'sources' / relative(item['path'])) for item in old['sources']]
        for item in old['sources']:
            if sha(previous.parent / 'sources' / relative(item['path'])) != item['sha256']:
                raise ValueError('Previous source snapshot changed: ' + item['path'])
    out = output_path(args.output)
    out.mkdir(parents=True)
    inputs = {key: old[key] for key in ('loader', 'substeps', 'cee_source_commit', 'java_home')}
    inputs.update(schema=2, purpose='Focused CEE readiness; not acceptance', version=version,
                  previous_inputs=str(previous), previous_inputs_sha256=sha(previous),
                  freeze_commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                  source_origin='current working sources; newly built runtime' if new_runtime else 'current working sources; player sources compared' if args.current_sources else 'preserved previous snapshot',
                  artifacts={}, sources=[])
    if getattr(args, 'baseline', None):
        inputs['baseline_commit'] = subprocess.check_output(['git', 'rev-parse', args.baseline + '^{commit}'], cwd=ROOT, text=True).strip()
    for role, path in files.items():
        target = out / 'artifacts' / role / path.name
        target.parent.mkdir(parents=True)
        shutil.copy2(path, target)
        inputs['artifacts'][role] = dict(path=str(target), sha256=sha(target))
        if role in CEE:
            inputs['artifacts'][role]['version'] = validate_cee(role, target)
    for name, path in source_files:
        target = out / 'sources' / relative(name)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        inputs['sources'].append(dict(path=name, sha256=sha(target)))
    write(out / 'inputs.json', inputs)
    verify(inputs, out / 'inputs.json')
    print(out / 'inputs.json')


def inventory(folder):
    result = {}
    for path in sorted(folder.rglob('*')):
        if path.is_symlink():
            raise ValueError('World input must not contain symbolic links: ' + str(path))
        if path.is_file():
            result[path.relative_to(folder).as_posix()] = sha(path)
    return result


def command(inputs, args, game):
    if args.group == 'fans' and args.runtime != 'cee120':
        raise ValueError('New fan fixtures require CEE 1.2')
    stock = args.group == 'stock'
    artifact = inputs['artifacts']['reference' if stock else 'runtime']
    cee = inputs['artifacts'][args.runtime]
    names = ','.join(GROUPS[args.group])
    result = ['gradlew.bat' if os.name == 'nt' else './gradlew',
              'runUpstreamReferenceServer' if stock else 'runPackagedGameTestServer',
              '--offline', '--no-daemon', '--max-workers=2',
              '-Pneo_version=' + inputs['loader'], '-Pmod_version=' + inputs['version'],
              '-PtestBackend=' + args.backend, '-PtestProfile=BASE', '-PtestNamespaces=' + names,
              '-PrestartPhase=' + args.phase, '-PtestFixtureArtifact=' + inputs['artifacts']['gametest']['path'],
              '-PceeDevelopmentJar=' + inputs['artifacts']['cee120']['path'], '-PceeRuntimeJar=' + cee['path'],
              '-PtestExpectedCeeSha256=' + cee['sha256']]
    if stock:
        result += ['-PreferenceArtifact=' + artifact['path'], '-PreferenceDirectory=' + str(game), '-PreferenceNamespaces=' + names]
    else:
        result += ['-PtestArtifact=' + artifact['path'], '-PtestDirectory=' + str(game)]
    if args.tight_cee_residual:
        if not stock or args.runtime != 'cee120':
            raise ValueError('The diagnostic residual override is only for the CEE 1.2 stock control')
        result += ['-PtestTightCeeResidual=true']
    for task in EXCLUDED:
        result += ['-x', task]
    return result


def collect(text, code, loaded, inputs, args):
    failures = []
    diagnostic = 'CEE_REFERENCE_RESIDUAL threshold=1.0E-16'
    if args.tight_cee_residual and diagnostic not in text:
        failures.append('CEE residual diagnostic was not applied')
    if not args.tight_cee_residual and diagnostic in text:
        failures.append('Raw execution unexpectedly enabled the CEE residual diagnostic')
    passed = re.search(r'All (\d+) required tests passed', text)
    if code != 0 or not passed or int(passed[1]) <= 0 or 'GAME TESTS COMPLETE' not in text:
        failures.append('No complete passing GameTest execution')
    if passed and int(passed[1]) != COUNTS[args.group]:
        failures.append('Selected GameTest count mismatch')
    if 'All dimensions are saved' not in text or 'Game test server shutting down' not in text:
        failures.append('Clean save/shutdown not observed')
    roles = {'electroenergetics': args.runtime, 'wwpg_tests': 'gametest', 'wwpg': 'reference' if args.group == 'stock' else 'runtime'}
    for mod_id, role in roles.items():
        if loaded.get(mod_id, {}).get('sha256') != inputs['artifacts'][role]['sha256']:
            failures.append('Loaded artifact mismatch: ' + mod_id)
    if loaded.get('electroenergetics', {}).get('version') != CEE[args.runtime][0]:
        failures.append('Loaded CEE version mismatch')
    if loaded.get('neoforge', {}).get('version') != inputs['loader']:
        failures.append('Loaded loader version mismatch')
    return failures, int(passed[1]) if passed else None


def run(args):
    manifest = Path(args.inputs).resolve()
    inputs = json.loads(manifest.read_text())
    verify(inputs, manifest)
    # Gradle owns the launch classpath. Do not silently use different orchestration.
    for item in inputs['sources']:
        if item['path'] == 'build.gradle' and sha(ROOT / 'build.gradle') != item['sha256']:
            raise ValueError('Gradle orchestration changed; create a new freeze with --current-sources')
    if args.phase != 'SETUP' and not args.world:
        raise ValueError('Verification phases require an explicitly copied source world')
    out = output_path(args.output)
    game = out / 'game'
    cmd = command(inputs, args, game)
    world = Path(args.world).resolve() if args.world else None
    world_files = inventory(world) if world else None
    if world:
        if not (world / 'level.dat').is_file():
            raise ValueError('World input requires level.dat')
    game.mkdir(parents=True)
    (game / 'eula.txt').write_text('eula=true\n')
    if world:
        shutil.copytree(world, game / 'world')
        write(out / 'world-input.json', dict(path=str(world), files=world_files))
    env = dict(os.environ, JAVA_HOME=inputs['java_home'], GRADLE_USER_HOME=str(ROOT / 'local/gradle-home'),
               TEMP=str(ROOT / 'local/tmp'), TMP=str(ROOT / 'local/tmp'))
    Path(env['TEMP']).mkdir(parents=True, exist_ok=True)
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']
    report = dict(schema=2, scope='focused CEE readiness; not acceptance', status='running',
                  inputs=str(manifest), inputs_sha256=sha(manifest), group=args.group, backend=args.backend,
                  runtime=args.runtime, reference_mode='tight-residual diagnostic' if args.tight_cee_residual else 'stock' if args.group == 'stock' else 'bridge',
                  command=cmd, started=datetime.now(timezone.utc).isoformat(), runner_sha256=sha(__file__))
    report['orchestration_sha256'] = sha(ROOT / 'build.gradle')
    write(out / 'result.json', report)
    start = time.monotonic()
    with (out / 'launcher.log').open('w', encoding='utf-8') as log:
        code = subprocess.call([sys.executable, str(ROOT / 'tools/run_checked.py'), '--timeout', '600', '--'] + cmd,
                               cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    text = (out / 'launcher.log').read_text(errors='replace')
    loaded_path = game / 'loaded-runtime-inputs.json'
    loaded = json.loads(loaded_path.read_text()) if loaded_path.exists() else {}
    failures, count = collect(text, code, loaded, inputs, args)
    try:
        verify(inputs, manifest)
        if world and inventory(world) != world_files:
            failures.append('Source world changed')
    except (ValueError, OSError) as error:
        failures.append(str(error))
    report.update(status='failed' if failures else 'passed', exit_code=code, seconds=time.monotonic()-start,
                  tests=count, failures=failures, loaded=loaded,
                  observations=[line for line in text.splitlines() if 'CEE_READINESS ' in line or 'CEE_COLD_MIGRATION ' in line])
    write(out / 'result.json', report)
    print(json.dumps({key: report[key] for key in ('status', 'tests', 'seconds', 'failures')}, indent=2))
    return 1 if failures else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest='action', required=True)
    f = subs.add_parser('freeze')
    f.add_argument('--from-inputs', required=True)
    f.add_argument('--cee113', required=True)
    f.add_argument('--runtime', help='Explicit newly built player artifact; requires current sources and matching companion/control')
    f.add_argument('--baseline', help='Baseline commit/ref for the integration record')
    f.add_argument('--gametest')
    f.add_argument('--reference')
    f.add_argument('--current-sources', action='store_true')
    f.add_argument('--output', required=True)
    r = subs.add_parser('run')
    r.add_argument('--inputs', required=True)
    r.add_argument('--runtime', choices=CEE, required=True)
    r.add_argument('--backend', choices=['NATIVE', 'JAVA'], required=True)
    r.add_argument('--group', choices=GROUPS, required=True)
    r.add_argument('--phase', choices=['SETUP', 'VERIFY', 'VERIFY_AGAIN'], default='SETUP')
    r.add_argument('--world')
    r.add_argument('--tight-cee-residual', action='store_true')
    r.add_argument('--output', required=True)
    args = parser.parse_args()
    try:
        return run(args) if args.action == 'run' else freeze(args)
    except (ValueError, OSError) as error:
        parser.exit(2, str(error) + '\n')


if __name__ == '__main__':
    sys.exit(main())
