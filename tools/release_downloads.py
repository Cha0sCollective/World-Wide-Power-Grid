"""Stage five public downloads from an accepted bundle; publish a verified GitHub draft.

Use stage, check or publish --help. Candidates never qualify for publication.
The full acceptance checker remains tools/package_api_release.py.
"""
import argparse
from contextlib import contextmanager
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import tempfile
import zipfile

import package_api_release as acceptance
from package_api_release import PublicationError, require, sha, read

ROOT = Path(__file__).resolve().parents[1]
VERSION_PATTERN = re.compile(r'^[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?$')
WORLD_KEYS = {'base_world': 'stationary-yard', 'pinout_world': 'stationary-yard-pinout',
              'base_workshop_world': 'api-workshop', 'pinout_workshop_world': 'api-workshop-pinout'}
SDK_ROLES = ('api', 'api_sources', 'api_javadoc', 'cee_api', 'cee_api_sources', 'cee_api_javadoc')
MAX_NOTE_WORDS = 250


def safe_path(name):
    require(isinstance(name, str), 'File path must be text')
    path = PurePosixPath(name)
    require(isinstance(name, str) and name and not path.is_absolute() and
            all(part not in {'', '.', '..'} for part in name.split('/')) and
            not any(c in name for c in '\\:') and str(path) == name,
            f'Unsafe file path: {name}')
    return path


def names(version):
    require(VERSION_PATTERN.fullmatch(version) is not None, 'Invalid release version')
    stem = 'wwpg-' + version
    return [stem + '.jar', stem + '-example-worlds.zip', stem + '-developer-sdk.zip',
            stem + '-verification.zip', stem + '-checksums.txt']


def presentation(data):
    require(data.get('schema') == 1, 'Unknown presentation schema')
    require(re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', data.get('repository', '')),
            'Repository must be owner/name')
    changes = data.get('changes')
    require(isinstance(changes, list) and len(changes) <= 3, 'Use at most three release highlights')
    for field in [data.get('summary'), data.get('installation'), data.get('optional_installation'), *changes]:
        require(isinstance(field, str) and field.strip() == field and field and
                len(field) <= 600 and not any(c in field for c in '\r\n'),
                'Presentation fields must be short, single-line text')
    return data


def notes(version, config):
    config = presentation(config)
    runtime, worlds, sdk, verification, checksums = names(version)
    base = 'https://github.com/' + config['repository']
    download = base + '/releases/download/' + version + '/'
    highlights = ''.join('- ' + change + '\n' for change in config['changes'])
    body = config['summary'] + ('\n\n' + highlights.rstrip() if highlights else '') + '\n\n'
    body += '''## Download

**For ordinary play, download the mod jar and install the required upstream mods.**

| I want to… | Download |
| --- | --- |
'''
    for label, filename, description in (
        ('Install WWPG', runtime, 'Mod jar'),
        ('Try ready-built circuits', worlds, 'Example worlds'),
        ('Develop an add-on', sdk, 'Developer SDK'),
        ('Inspect test records', verification, 'Verification archive — optional; not needed to play')):
        body += f'| {label} | [{description}]({download}{filename}) |\n'
    body += '\n## Install\n\n' + config['installation'] + '\n\n'
    body += f'[Installation guide]({base}/blob/main/docs/INSTALL.md) · '
    body += f'[Supported equipment and limits]({base}/blob/main/docs/SUPPORT.md) · '
    body += f'[Checksums]({download}{checksums})\n'
    require(len(body.split()) <= MAX_NOTE_WORDS, 'Release notes exceed 250 words; shorten the presentation')
    return body


@contextmanager
def accepted_bundle(path, expected_sha, temp_parent):
    """Reconstruct locked inputs and rerun acceptance; flags alone are insufficient."""
    path = Path(path)
    require(acceptance.HASH.fullmatch(expected_sha or '') is not None and sha(path) == expected_sha,
            'Accepted bundle SHA256 mismatch')
    with tempfile.TemporaryDirectory(prefix='release-verify-', dir=temp_parent) as temporary:
        root = Path(temporary).resolve()
        with zipfile.ZipFile(path) as archive:
            entries = archive.infolist()
            require(len(entries) <= 20000 and sum(e.file_size for e in entries) <= 4 * 1024**3,
                    'Acceptance archive exceeds extraction limits')
            filenames = [e.filename for e in entries]
            require(len(filenames) == len(set(filenames)), 'Duplicate acceptance archive entries')
            require(len(filenames) == len({name.casefold() for name in filenames}), 'Case-colliding archive entries')
            for entry in entries:
                safe_path(entry.filename)
                require(not entry.is_dir() and not stat.S_ISLNK(entry.external_attr >> 16),
                        'Acceptance archive must contain regular files')
            sums = {}
            for line in archive.read('SHA256SUMS.txt').decode('utf-8').splitlines():
                digest, name = line.split('  ', 1)
                require(acceptance.HASH.fullmatch(digest) and name not in sums, 'Invalid archive checksum list')
                sums[name] = digest
            require(set(sums) == set(filenames) - {'SHA256SUMS.txt'}, 'Archive checksum list is incomplete')
            for name, digest in sums.items():
                require(hashlib.sha256(archive.read(name)).hexdigest() == digest, 'Changed archive member: ' + name)
            manifest = json.loads(archive.read('manifest.json'))
            status = json.loads(archive.read('PUBLICATION_STATUS.json'))
            require(status.get('schema') == 1 and status.get('publishable') is True and
                    status.get('state') == 'accepted_release', 'SDK candidates cannot become public downloads')
            require(all(status.get(key) == manifest.get(key) for key in ('wwpg_version', 'api_version')),
                    'Acceptance status version mismatch')
            names(manifest['wwpg_version'])
            require(set(manifest['artifacts']) == acceptance.ROLES, 'Unexpected accepted artifact roles')
            destinations = {}
            for record in manifest['artifacts'].values():
                target = str(safe_path(record['path']))
                require(target not in destinations, 'Duplicate artifact input path')
                destinations[target] = 'artifacts/' + PurePosixPath(target).name
            for name in filenames:
                if name.startswith('evidence/'):
                    target = str(safe_path(name.removeprefix('evidence/')))
                    if target in destinations:
                        require(archive.read(destinations[target]) == archive.read(name),
                                'Evidence/artifact destination collision')
                    destinations[target] = name
            require(len(destinations) == len({name.casefold() for name in destinations}),
                    'Case-colliding extracted inputs')
            for relative, member in destinations.items():
                target = root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(member))
            paths = acceptance.validate_artifacts(manifest, root)
            acceptance.validate_release(manifest, root)
            # Check the entire proof chain, including media not consumed by gate summaries.
            acceptance.collect_locked_evidence({k: v for k, v in manifest.items() if k != 'artifacts'}, root)
            yield manifest, paths, root, archive


def write_json(path, data):
    path.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8', newline='\n')


def bundle(path, files):
    files = dict(files)
    files['SHA256SUMS.txt'] = ''.join(hashlib.sha256(data).hexdigest() + '  ' + name + '\n'
                                     for name, data in sorted(files.items())).encode('utf-8')
    with zipfile.ZipFile(path, 'x', zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in sorted(files.items()):
            safe_path(name)
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type, info.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
            archive.writestr(info, data)


def licenses_from(accepted, runtime):
    with zipfile.ZipFile(runtime) as jar:
        return {'licenses/WWPG-LICENSE.txt': accepted.read('LICENSE'),
                'licenses/POWER-GRID-LICENSE.txt': jar.read('META-INF/POWER-GRID-LICENSE.txt'),
                'licenses/WWPG-NATIVE-NOTICE.txt': jar.read('META-INF/WWPG-NATIVE-NOTICE.txt')}


def verify_group(path, expected):
    """Grouped downloads must retain all accepted inner files, with no extra payload."""
    with acceptance.checked_zip(path) as archive:
        require(set(archive.namelist()) == set(expected) | {'START-HERE.md', 'SHA256SUMS.txt'},
                'Grouped download contents changed: ' + path.name)
        for name, data in expected.items():
            require(archive.read(name) == data, 'Grouped file differs from acceptance: ' + name)
        files = {name: archive.read(name) for name in archive.namelist() if name != 'SHA256SUMS.txt'}
        sums = ''.join(hashlib.sha256(data).hexdigest() + '  ' + name + '\n' for name, data in sorted(files.items()))
        require(archive.read('SHA256SUMS.txt') == sums.encode('utf-8'), 'Grouped checksum list changed')


def world_payload(manifest, root, version):
    files = {f'worlds/wwpg-{version}-{label}.zip': acceptance.locked_file(manifest[key], root).read_bytes()
             for key, label in WORLD_KEYS.items()}
    if 'benchmark_world' in manifest:
        path = acceptance.locked_file(manifest['benchmark_world'], root)
        with acceptance.checked_zip(path) as archive:
            records = [n for n in archive.namelist() if n.endswith('/WWPG_MANIFEST.json')]
            require(len(records) == 1, 'Benchmark world needs one inventory')
            prefix = records[0].removesuffix('WWPG_MANIFEST.json')
            record = json.loads(archive.read(records[0]))
            require(record.get('kind') == 'mixed_capacity_benchmark' and record.get('target') == version
                    and record.get('electrical_nodes') == 14650 and record.get('connections') == 20637
                    and record.get('stateful_devices') == 1050 and record.get('chunks') == 953
                    and record.get('certified_20_tps') is False and '14650' in prefix,
                    'Benchmark identity/count/limitation differs')
            require(record.get('jar', {}).get('sha256') == manifest['artifacts']['runtime']['sha256'], 'Benchmark runtime differs')
            inventory = record.get('files', [])
            names = {prefix + item['path'] for item in inventory}
            require(names == set(archive.namelist()) - {records[0]}, 'Benchmark inventory is incomplete')
            require(prefix + 'level.dat' in names and prefix + 'WWPG_README.txt' in names, 'Benchmark save/instructions absent')
            for item in inventory:
                safe_path(item['path'])
                data = archive.read(prefix + item['path'])
                require(len(data) == item['bytes'] and hashlib.sha256(data).hexdigest() == item['sha256'], 'Benchmark entry changed')
        files[f'worlds/wwpg-{version}-benchmark-14650-nodes.zip'] = path.read_bytes()
    return files


def stage(source, expected_sha, config_path, output):
    source, output = Path(source).resolve(), Path(output).resolve()
    require(output.is_relative_to(ROOT) and not output.exists(), 'Use a fresh output directory inside the workspace')
    config = presentation(read(config_path))
    output.parent.mkdir(parents=True, exist_ok=True)
    with accepted_bundle(source, expected_sha, output.parent) as (manifest, paths, root, accepted):
        version = manifest['wwpg_version']
        body = notes(version, config)
        with tempfile.TemporaryDirectory(prefix='release-stage-', dir=output.parent) as temporary:
            staged = Path(temporary) / 'ready'
            downloads = staged / 'downloads'
            downloads.mkdir(parents=True)
            runtime, worlds, sdk, verification, checksums = names(version)
            shutil.copyfile(paths['runtime'], downloads / runtime)
            licenses = licenses_from(accepted, paths['runtime'])
            base = 'https://github.com/' + config['repository'] + '/blob/main/docs/'
            world_files = dict(licenses)
            world_files.update(world_payload(manifest, root, version))
            for role in ('example', 'upstream_example'):
                world_files['workshop-mods/' + paths[role].name] = paths[role].read_bytes()
            benchmark_note = ('The benchmark-14650-nodes world is a heavy capacity workload, separate\n'
                              'from the playable Stationary Yard. Read its WWPG_README.txt before opening it.\n'
                              'It has 14,650 electrical nodes; it is not certified to run at 20 TPS.\n\n'
                              if 'benchmark_world' in manifest else '')
            world_files['START-HERE.md'] = f'''# Example worlds for WWPG {version}

Install WWPG and the required mods first: {base}INSTALL.md

1. Extract this bundle.
2. Choose a ZIP in worlds/ and extract its named save folder into your instance's saves/ directory.
3. Open that save. Tools, goggles and instructions are at spawn.

Start with stationary-yard. The api-workshop worlds also require both jars in
workshop-mods/ on the server and every client. Those add-ons are optional for ordinary play.
{config['optional_installation']}

Station instructions: {base}STATIONARY_YARD.md
Workshop instructions: {base}EXTENSION_API_WORKSHOP.md

{benchmark_note}The inner world ZIPs and example jars retain their recorded hashes.
No upstream dependency jars are included.
'''.encode('utf-8')
            bundle(downloads / worlds, world_files)
            sdk_files = dict(licenses)
            for role in SDK_ROLES:
                sdk_files['api/' + paths[role].name] = paths[role].read_bytes()
            for role, folder in (('maven_repository', 'maven'), ('example_sources', 'examples'),
                                 ('upstream_example_sources', 'examples')):
                sdk_files[folder + '/' + paths[role].name] = paths[role].read_bytes()
            for name in accepted.namelist():
                if name.startswith('docs/'):
                    sdk_files[name] = accepted.read(name)
                elif name.startswith('documentation-starter/'):
                    sdk_files['examples/'+PurePosixPath(name).name] = accepted.read(name)
            starter_instructions = (
                '2. Start with docs/CREATE_ADDON.md and extract examples/tutorial-load-0.1.0-source.zip.\n'
                '   The other example ZIPs demonstrate the larger general and CEE/PG integrations.'
                if manifest.get('authoring_starter') else
                '2. Extract either ZIP in examples/ into its own project directory.')
            authoring_links = (
                'Build your first add-on: docs/CREATE_ADDON.md\n'
                'Build and debug: docs/ADDON_WORKFLOW.md\n'
                'Validation and repair: docs/API_LIMITS.md and docs/ADDON_TESTING.md\n'
                if manifest.get('authoring_starter') else '')
            sdk_files['START-HERE.md'] = f'''# Extension API {manifest['api_version']} for WWPG {version}

Players install {runtime}. Do not put API, source or Javadoc jars in mods/.

1. Extract the ZIP in maven/ into a directory containing org/cha0scollective/wwpg/.
{starter_instructions}
3. Use Java 21 and that project's wrapper to build against the extracted repository:

```text
chmod +x gradlew
./gradlew build "-PapiRepository=/absolute/path/to/maven-repository" --no-daemon
```

Windows: use gradlew.bat with the same option and an absolute Windows path.
api/ contains both APIs, sources and Javadoc. docs/ contains the accepted guides.
The Maven and source ZIPs are unchanged independently tested files.

Developer guide: {base}EXTENSION_API.md
{authoring_links}
'''.encode('utf-8')
            bundle(downloads / sdk, sdk_files)
            shutil.copyfile(source, downloads / verification)
            assets = [dict(filename=name, sha256=sha(downloads / name), bytes=(downloads / name).stat().st_size)
                      for name in names(version)[:-1]]
            (downloads / checksums).write_text(''.join(a['sha256'] + '  ' + a['filename'] + '\n' for a in assets),
                                              encoding='utf-8', newline='\n')
            assets.append(dict(filename=checksums, sha256=sha(downloads / checksums), bytes=(downloads / checksums).stat().st_size))
            (staged / 'release-notes.md').write_text(body, encoding='utf-8', newline='\n')
            write_json(staged / 'presentation.json', config)
            layout = dict(schema=1, state='ready', version=version, api_version=manifest['api_version'],
                          repository=config['repository'], source_sha=manifest['source_sha'],
                          accepted_bundle_sha256=expected_sha, notes_sha256=sha(staged / 'release-notes.md'), assets=assets)
            write_json(staged / 'download-layout.json', layout)
            require(not output.exists(), 'Output appeared during staging; refusing overwrite')
            staged.rename(output)
    return layout


def check(staged):
    staged = Path(staged).resolve()
    layout, config = read(staged / 'download-layout.json'), read(staged / 'presentation.json')
    require(layout.get('schema') == 1 and layout.get('state') == 'ready', 'Unknown staged layout')
    expected = names(layout['version'])
    require([a['filename'] for a in layout['assets']] == expected, 'Release must have exactly five ordered downloads')
    require({p.name for p in (staged / 'downloads').iterdir()} == set(expected), 'Unexpected public download files')
    require(layout['repository'] == config['repository'], 'Staged repository changed')
    body = (staged / 'release-notes.md').read_text(encoding='utf-8')
    require(body == notes(layout['version'], config) and sha(staged / 'release-notes.md') == layout['notes_sha256'],
            'Staged notes changed; stage again with the updated presentation')
    for asset in layout['assets']:
        acceptance.locked_file(dict(path='downloads/' + asset['filename'], sha256=asset['sha256'], bytes=asset['bytes']), staged)
    checksums = ''.join(a['sha256'] + '  ' + a['filename'] + '\n' for a in layout['assets'][:-1])
    require((staged / 'downloads' / expected[-1]).read_text(encoding='utf-8') == checksums, 'Public checksum list changed')
    with accepted_bundle(staged / 'downloads' / expected[3], layout['accepted_bundle_sha256'], staged.parent) as (manifest, paths, root, accepted):
        require(layout['source_sha'] == manifest['source_sha'] and layout['api_version'] == manifest['api_version'] and
                layout['version'] == manifest['wwpg_version'], 'Staged identity differs from acceptance')
        require(layout['assets'][0]['sha256'] == sha(paths['runtime']), 'Staged runtime differs from acceptance')
        world_files = licenses_from(accepted, paths['runtime'])
        world_files.update(world_payload(manifest, root, layout['version']))
        for role in ('example', 'upstream_example'):
            world_files['workshop-mods/' + paths[role].name] = paths[role].read_bytes()
        verify_group(staged / 'downloads' / expected[1], world_files)
        sdk_files = licenses_from(accepted, paths['runtime'])
        for role in SDK_ROLES:
            sdk_files['api/' + paths[role].name] = paths[role].read_bytes()
        for role, folder in (('maven_repository', 'maven'), ('example_sources', 'examples'),
                             ('upstream_example_sources', 'examples')):
            sdk_files[folder + '/' + paths[role].name] = paths[role].read_bytes()
        sdk_files.update({name: accepted.read(name) for name in accepted.namelist() if name.startswith('docs/')})
        sdk_files.update({'examples/'+PurePosixPath(name).name: accepted.read(name) for name in accepted.namelist() if name.startswith('documentation-starter/')})
        verify_group(staged / 'downloads' / expected[2], sdk_files)
    return layout


def gh(*args):
    timeout = 600 if args[:2] in {('release', 'create'), ('release', 'upload')} else 60
    result = subprocess.run(['gh', *args], capture_output=True, text=True, encoding='utf-8', timeout=timeout)
    require(result.returncode == 0, 'GitHub command failed: ' + result.stderr.strip())
    return result.stdout


def api_or_absent(endpoint):
    # --include supplies a machine-readable status. Auth/network failures are not absence.
    result = subprocess.run(['gh', 'api', '--include', endpoint], capture_output=True, text=True, encoding='utf-8', timeout=60)
    status = re.search(r'^HTTP/\S+ (\d+)', result.stdout, re.MULTILINE)
    if status and status.group(1) == '404':
        return None
    require(result.returncode == 0 and status and status.group(1) == '200',
            'Cannot inspect GitHub state: ' + result.stderr.strip())
    return json.loads(re.split(r'\r?\n\r?\n', result.stdout, maxsplit=1)[1])


def verify_tag(layout):
    repo, version = layout['repository'], layout['version']
    ref = api_or_absent(f'repos/{repo}/git/ref/tags/{version}')
    if ref is None:
        return False
    obj = ref['object']
    for _ in range(8):
        if obj['type'] != 'tag':
            break
        obj = json.loads(gh('api', f'repos/{repo}/git/tags/{obj["sha"]}'))['object']
    require(obj['type'] == 'commit' and obj['sha'] == layout['source_sha'],
            'Existing tag points to another commit; published identities are immutable')
    return True


def release_by_tag(repo, version):
    # The by-tag REST endpoint returns published releases only. Listing includes
    # authenticated drafts and supports recovery after a partial upload.
    pages = json.loads(gh('api', f'repos/{repo}/releases', '--paginate', '--slurp'))
    matches = [release for page in pages for release in page if release['tag_name'] == version]
    require(len(matches) <= 1, 'Duplicate releases for the requested tag')
    return matches[0] if matches else None


def publish(staged, make_public=False, plan=False):
    staged = Path(staged).resolve()
    layout = check(staged)
    repo, version = layout['repository'], layout['version']
    title = 'WWPG ' + version
    files = [str(staged / 'downloads' / asset['filename']) for asset in layout['assets']]
    command = ['release', 'create', version, *files, '--repo', repo, '--target', layout['source_sha'],
               '--title', title, '--notes-file', str(staged / 'release-notes.md'), '--draft']
    if '-' in version:
        command.append('--prerelease')
    if plan:
        return dict(command=['gh', *command], publish=make_public, layout=layout)
    existing = release_by_tag(repo, version)
    tagged = verify_tag(layout)
    if existing is None:
        gh(*command)
        existing = release_by_tag(repo, version)
    require(existing is not None and existing.get('draft') is True, 'Refusing to alter an existing public release')
    require(existing['name'] == title and existing['body'].rstrip() == (staged / 'release-notes.md').read_text(encoding='utf-8').rstrip(),
            'Existing draft belongs to another presentation')
    require(existing.get('prerelease') == ('-' in version), 'Draft prerelease status mismatch')
    require(tagged or existing.get('target_commitish') == layout['source_sha'], 'Draft target differs from accepted source')
    # An interrupted upload is recoverable without deleting or replacing any asset.
    assets = {asset['name']: asset for asset in existing['assets']}
    expected = {asset['filename']: asset for asset in layout['assets']}
    require(len(assets) == len(existing['assets']) and assets.keys() <= expected.keys(), 'Unexpected draft assets')
    for name, asset in assets.items():
        require(asset.get('digest') == 'sha256:' + expected[name]['sha256'] and asset['size'] == expected[name]['bytes'],
                'Remote draft asset differs from staging: ' + name)
    missing = [str(staged / 'downloads' / name) for name in expected if name not in assets]
    if missing:
        gh('release', 'upload', version, *missing, '--repo', repo)
    existing = release_by_tag(repo, version)
    require(existing is not None and existing.get('draft') is True, 'Draft became unavailable/public during verification')
    require(existing['name'] == title and existing['body'].rstrip() == (staged / 'release-notes.md').read_text(encoding='utf-8').rstrip()
            and existing.get('prerelease') == ('-' in version), 'Draft presentation changed during verification')
    require(len(existing['assets']) == 5 and {a['name'] for a in existing['assets']} == expected.keys(), 'Remote download count mismatch')
    for asset in existing['assets']:
        record = expected[asset['name']]
        require(asset.get('digest') == 'sha256:' + record['sha256'] and asset['size'] == record['bytes'], 'Uploaded bytes mismatch')
    # GitHub may defer creating a draft tag until publication. Its target must still be exact.
    tagged = verify_tag(layout)
    require(tagged or existing.get('target_commitish') == layout['source_sha'], 'Draft target differs from accepted source')
    if make_public:
        gh('release', 'edit', version, '--repo', repo, '--draft=false')
    return dict(url=existing['html_url'], published=make_public, assets=5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    staging = commands.add_parser('stage', help='Create the five downloads and short notes')
    staging.add_argument('--bundle', type=Path, required=True)
    staging.add_argument('--sha256', required=True)
    staging.add_argument('--presentation', type=Path, default=ROOT / 'release/presentation.json')
    staging.add_argument('--output', type=Path, required=True)
    checking = commands.add_parser('check', help='Recheck staged inputs and acceptance without GitHub')
    checking.add_argument('--staged', type=Path, required=True)
    publishing = commands.add_parser('publish', help='Upload a draft; publish only with --publish')
    publishing.add_argument('--staged', type=Path, required=True)
    publishing.add_argument('--publish', action='store_true')
    publishing.add_argument('--plan', action='store_true', help='Validate and print the upload plan without GitHub access')
    args = parser.parse_args()
    try:
        if args.command == 'stage':
            result = stage(args.bundle, args.sha256, args.presentation, args.output)
        elif args.command == 'check':
            result = check(args.staged)
        else:
            result = publish(args.staged, args.publish, args.plan)
        print(json.dumps(result, indent=2))
    except (PublicationError, OSError, KeyError, ValueError, TypeError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        parser.exit(2, f'Release blocked: {error}\n')


if __name__ == '__main__':
    main()
