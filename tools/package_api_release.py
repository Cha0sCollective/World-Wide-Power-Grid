"""Build a deterministic SDK candidate; public release additionally requires all acceptance gates.

This does not upload files, manufacture test results, or mark progress complete.
The manifest describes immutable inputs and points to SHA256-locked evidence.
See docs/API_RELEASE_CHECKS.md for the manifest contract.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import xml.etree.ElementTree as ET
import zipfile
import subprocess
import sys
from sdk_documentation import bundle_markdown

from verify_api_compatibility import DEFAULT_PREFIX, PUBLIC, STATIC, compare, snapshot

def declared_versions():
    """Keep packaging aligned with the versions used to build this checkout."""
    properties = (Path(__file__).resolve().parents[1] / 'gradle.properties').read_text(encoding='utf-8')
    values = dict(re.findall(r'^([a-z_]+)\s*=\s*([^\r\n]+)', properties, re.MULTILINE))
    return values['mod_version'].strip(), values['api_version'].strip()


VERSION, API_VERSION = declared_versions()
PLATFORMS = {'ubuntu-24.04', 'windows-latest'}
BACKENDS, PROFILES = {'NATIVE', 'JAVA'}, {'BASE', 'PINOUT'}
COMBINATIONS = {(p, b, r) for p in PLATFORMS for b in BACKENDS for r in PROFILES}
ROLES = {'runtime', 'api', 'api_sources', 'api_javadoc', 'cee_api', 'cee_api_sources',
         'cee_api_javadoc', 'example', 'example_sources', 'upstream_example',
         'upstream_example_sources', 'maven_repository'}
BEHAVIORS = {'machine', 'storage', 'instrument', 'panel_attachment', 'board_component'}
HASH = re.compile(r'^[0-9a-f]{64}$')
SDK_DOCUMENTS = frozenset({'docs/EXTENSION_API.md', 'docs/EXTENSION_API_PLAN.md', 'docs/EXTENSION_API_WORKSHOP.md',
    'docs/API_RELEASE_CHECKS.md', 'docs/INSTALL.md', 'docs/STATIONARY_YARD.md',
    'docs/API_LIMITS.md', 'docs/CREATE_ADDON.md', 'docs/ADDON_WORKFLOW.md', 'docs/ADDON_TESTING.md', 'LICENSE'})


class PublicationError(ValueError): pass


def require(condition, message):
    if not condition: raise PublicationError(message)


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read(path): return json.loads(Path(path).read_text(encoding='utf-8'))


def locked_file(record, root):
    require(isinstance(record, dict), 'File descriptor must be an object')
    require(HASH.fullmatch(record.get('sha256', '')) is not None, 'File descriptor lacks SHA256')
    relative = PurePosixPath(record.get('path', ''))
    require(bool(str(relative)) and not relative.is_absolute() and '..' not in relative.parts
            and not re.match(r'^[A-Za-z]:', str(relative)), 'Use a safe root-relative input path')
    path = (root / str(relative)).resolve()
    require(path.is_relative_to(root.resolve()) and path.is_file(), f'Input absent/outside root: {relative}')
    require(sha(path) == record['sha256'], f'Input changed after evidence capture: {relative}')
    require(path.stat().st_size == record.get('bytes'), f'Input size mismatch: {relative}')
    return path


def documentation_inputs(manifest, root):
    """Bind separately frozen guides without changing the runtime acceptance source."""
    if 'documentation_snapshot' not in manifest:
        return manifest['source_sha'], {name: root / name for name in SDK_DOCUMENTS}
    record = read(locked_file(manifest['documentation_snapshot'], root))
    require(isinstance(record, dict) and record.get('schema') == 1,
            'Unknown documentation snapshot schema')
    source = record.get('source_sha')
    require(isinstance(source, str) and re.fullmatch(r'[0-9a-f]{40}', source) is not None
            and source != '0' * 40,
            'Documentation snapshot requires an immutable source commit')
    require(record.get('runtime_source_sha') == manifest['source_sha'],
            'Documentation snapshot belongs to a different runtime acceptance source')
    files = record.get('files')
    require(isinstance(files, dict) and set(files) == SDK_DOCUMENTS,
            'Documentation snapshot must lock the exact required SDK document set')
    return source, {name: locked_file(files[name], root) for name in sorted(SDK_DOCUMENTS)}


def checked_zip(path):
    archive = zipfile.ZipFile(path)
    try:
        require(archive.testzip() is None, f'ZIP CRC failed: {path.name}')
        names = archive.namelist()
        require(len(names) == len(set(names)), f'Duplicate ZIP entries: {path.name}')
        for name in names:
            item = PurePosixPath(name)
            require(not item.is_absolute() and '..' not in item.parts and '\\' not in name
                    and not re.match(r'^[A-Za-z]:', name), f'Unsafe archive entry: {name}')
        return archive
    except Exception:
        archive.close()
        raise


def validate_version_accessor(api_snapshot):
    """Version metadata must remain observable by already compiled consumers."""
    owner = api_snapshot['types'].get(DEFAULT_PREFIX + 'WWPGApi', {})
    field = owner.get('fields', {}).get('API_VERSIONLjava/lang/String;', {})
    method = owner.get('methods', {}).get('version()Ljava/lang/String;', {})
    require(field.get('access', 0) & PUBLIC and field.get('flags', 0) & STATIC
            and 'constant' not in field, 'API_VERSION must be a public runtime field without ConstantValue')
    require(method.get('access', 0) & PUBLIC and method.get('flags', 0) & STATIC,
            'Published API lacks public static WWPGApi.version():String')


def validate_api_metadata(archive, label):
    """Read the main JAR manifest, including the standard folded-line format."""
    require('META-INF/MANIFEST.MF' in archive.namelist(), f'{label} lacks API version metadata')
    raw = archive.read('META-INF/MANIFEST.MF').decode('utf-8')
    lines = []
    for line in raw.splitlines():
        if not line: break  # Named entry sections are not main artifact metadata.
        if line.startswith(' '):
            require(bool(lines), f'{label} has an invalid manifest continuation')
            lines[-1] += line[1:]
        else:
            lines.append(line)
    attributes = {}
    for line in lines:
        key, separator, value = line.partition(': ')
        require(separator and key.lower() not in attributes, f'{label} has malformed/duplicate manifest metadata')
        attributes[key.lower()] = value
    require(attributes.get('wwpg-api-version') == API_VERSION,
            f'{label} WWPG-API-Version disagrees with declared API {API_VERSION}')


def package_maven(source, root, output):
    """Archive only immutable explicit versions; omit floating timestamp metadata."""
    source, root, output = Path(source).resolve(), Path(root).resolve(), Path(output).resolve()
    require(source.is_relative_to(root) and source.is_dir(), 'Maven source must be a workspace directory')
    require(output.is_relative_to(root) and output.suffix == '.zip' and not output.exists(),
            'Maven output must be a new workspace ZIP')
    entries = {}
    for artifact in ('wwpg-api', 'wwpg-cee-api'):
        relative = Path('org/cha0scollective/wwpg') / artifact / API_VERSION
        version_dir = source / relative
        require(version_dir.is_dir(), f'Unpublished local Maven version: {artifact}:{API_VERSION}')
        for suffix in ('.jar', '-sources.jar', '-javadoc.jar', '.pom'):
            expected = version_dir / (artifact + '-' + API_VERSION + suffix)
            require(expected.is_file(), f'Incomplete Maven publication: {expected.name}')
            if suffix.endswith('.jar'):
                with checked_zip(expected): pass
        pom = ET.parse(version_dir / (artifact + '-' + API_VERSION + '.pom')).getroot()
        ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
        for key, expected in (('groupId', 'org.cha0scollective.wwpg'), ('artifactId', artifact), ('version', API_VERSION)):
            node = pom.find('m:' + key, ns)
            require(node is not None and node.text == expected, f'Unexpected Maven POM {key}: {artifact}')
        for path in sorted(version_dir.rglob('*')):
            if not path.is_file(): continue
            require(path.resolve().is_relative_to(source), 'Maven publication contains an external symlink')
            require('maven-metadata' not in path.name and not path.name.endswith('.lastUpdated'),
                    'Explicit API version unexpectedly contains mutable Maven cache metadata')
            entries[path.relative_to(source).as_posix()] = path.read_bytes()
    require(entries, 'No Maven artifacts selected')
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 11, 0, 0, 0))
            info.compress_type, info.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
            archive.writestr(info, data)
    return dict(file=output.name, sha256=sha(output), bytes=output.stat().st_size,
                mode='explicit_version_maven_repository', publishable=False,
                note='Maven publication archive only; public API freeze/release gates remain separate.')


def package_example(source, root, output):
    """Export one independently buildable project, excluding local caches and outputs."""
    source, root, output = Path(source).resolve(), Path(root).resolve(), Path(output).resolve()
    require(source.is_relative_to(root) and source.is_dir(), 'Example source must be a workspace directory')
    require(output.is_relative_to(root) and output.suffix == '.zip' and not output.exists(),
            'Example output must be a new workspace ZIP')
    required = {'build.gradle', 'settings.gradle', 'gradlew', 'gradlew.bat', 'LICENSE', 'README.md',
                'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties'}
    require(all((source / name).is_file() for name in required), 'Example lacks its own build, wrapper, license or instructions')
    entries = {}
    for path in sorted(source.rglob('*')):
        if not path.is_file(): continue
        relative = path.relative_to(source).as_posix()
        if relative not in required | {'gradle.properties'} and not relative.startswith('src/'):
            continue
        require(path.resolve().is_relative_to(source), 'Example project contains an external symlink')
        data = path.read_bytes()
        if relative == 'README.md' and '](' in data.decode('utf-8'):
            source_sha = subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()
            data = bundle_markdown(data.decode('utf-8'),path.relative_to(root).as_posix(),set(),VERSION,source_sha).encode('utf-8')
        if relative.endswith('.java'):
            imports = re.findall(r'^\s*import\s+(?:static\s+)?(org\.cha0scollective\.wwpg\.[\w.*]+)', data.decode('utf-8'), re.MULTILINE)
            require(all(name.startswith(('org.cha0scollective.wwpg.api.', 'org.cha0scollective.wwpg.example.',
                                         'org.cha0scollective.wwpg.examples.')) for name in imports),
                    f'Example imports private WWPG implementation: {relative}')
        entries[relative] = data
    require(any(name.endswith('.java') for name in entries), 'Example has no implementation source')
    require(not any(name.startswith('src/') and name.endswith('.class') for name in entries),
            'Example source includes compiled classes')
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 11, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.create_system = 3  # Standard Unix extractors must honor the mode bits.
            info.external_attr = (0o100755 if name == 'gradlew' else 0o100644) << 16
            archive.writestr(info, data)
    return dict(file=output.name, sha256=sha(output), bytes=output.stat().st_size,
                mode='independent_example_source', publishable=False,
                note='Source project archive only; independent build and release acceptance remain separate.')


def validate_artifacts(manifest, root):
    require(manifest.get('schema') == 1 and manifest.get('wwpg_version') == VERSION
            and manifest.get('api_version') == API_VERSION, 'Wrong manifest/version')
    coordinate = manifest.get('api_coordinate', '')
    require(coordinate == f'org.cha0scollective.wwpg:wwpg-api:{API_VERSION}', 'Unexpected API Maven coordinate')
    cee_coordinate = manifest.get('cee_api_coordinate', '')
    require(cee_coordinate == f'org.cha0scollective.wwpg:wwpg-cee-api:{API_VERSION}', 'Unexpected CEE API Maven coordinate')
    records = manifest.get('artifacts', {})
    require(ROLES <= records.keys(), 'Missing SDK/runtime/example/Maven artifact')
    paths = {role: locked_file(record, root) for role, record in records.items()}
    api_snapshot = snapshot(paths['api'])
    require(all(t['major_version'] <= 65 for t in api_snapshot['types'].values()), 'API requires newer than Java21')
    validate_version_accessor(api_snapshot)
    with checked_zip(paths['api']) as api, checked_zip(paths['runtime']) as runtime:
        validate_api_metadata(api, 'General API SDK')
        validate_api_metadata(runtime, 'WWPG runtime')
        classes = {name: api.read(name) for name in api.namelist() if name.endswith('.class')}
        require(classes and all(name.startswith(DEFAULT_PREFIX) for name in classes),
                'Compile-time API artifact includes implementation classes')
        for name, data in classes.items():
            require(name in runtime.namelist() and runtime.read(name) == data, f'Runtime API differs from SDK: {name}')
        metadata = runtime.read('META-INF/neoforge.mods.toml').decode('utf-8')
        require(re.search(r'\bversion\s*=\s*"' + re.escape(VERSION) + r'"', metadata), 'Wrong runtime mod version')
        require(not any('/acceptance/' in name for name in runtime.namelist()), 'Private acceptance helper in runtime')
    with checked_zip(paths['api_sources']) as sources:
        required = {name.split('$', 1)[0] + '.java' for name in api_snapshot['types']}
        require(required <= set(sources.namelist()), 'Published source JAR omits an exported API type')
    with checked_zip(paths['api_javadoc']) as docs:
        require('index.html' in docs.namelist() and ('element-list' in docs.namelist() or 'package-list' in docs.namelist()),
                'Javadoc artifact is not a generated Java API reference')
    cee_prefix = DEFAULT_PREFIX + 'cee/'
    cee_snapshot = snapshot(paths['cee_api'], (cee_prefix,))
    require(all(t['major_version'] <= 65 for t in cee_snapshot['types'].values()), 'CEE API requires newer than Java21')
    with checked_zip(paths['cee_api']) as cee_api, checked_zip(paths['runtime']) as runtime:
        validate_api_metadata(cee_api, 'CEE API SDK')
        cee_classes = {name: cee_api.read(name) for name in cee_api.namelist() if name.endswith('.class')}
        require(cee_classes and all(name.startswith(cee_prefix) for name in cee_classes),
                'CEE API artifact includes implementation/general API classes')
        for name, data in cee_classes.items():
            require(name in runtime.namelist() and runtime.read(name) == data, f'Runtime CEE API differs from SDK: {name}')
    with checked_zip(paths['cee_api_sources']) as sources:
        required = {name.split('$', 1)[0] + '.java' for name in cee_snapshot['types']}
        require(required <= set(sources.namelist()), 'CEE source artifact omits an exported type')
    with checked_zip(paths['cee_api_javadoc']) as docs:
        require('index.html' in docs.namelist() and ('element-list' in docs.namelist() or 'package-list' in docs.namelist()),
                'CEE Javadoc is not generated API documentation')
    with checked_zip(paths['example']) as example:
        require(any(n.endswith('.class') for n in example.namelist()), 'Independent example contains no compiled code')
        require(not any(n.startswith(DEFAULT_PREFIX) and n.endswith('.class') for n in example.namelist()),
                'Example shades the API; it must use the single WWPG runtime copy')
        require('META-INF/neoforge.mods.toml' in example.namelist(), 'Example is not a packaged NeoForge add-on')
        example_classes = {n for n in example.namelist() if n.endswith('.class')}
    with checked_zip(paths['upstream_example']) as example:
        upstream_classes = {n for n in example.namelist() if n.endswith('.class')}
        require(upstream_classes, 'Upstream example contains no compiled code')
        require(not any(n.startswith(DEFAULT_PREFIX) for n in upstream_classes), 'Upstream example shades public API classes')
        require(not example_classes & upstream_classes, 'Independent examples contain conflicting class definitions')
        require('META-INF/neoforge.mods.toml' in example.namelist(), 'Upstream example is not a packaged NeoForge add-on')
    with checked_zip(paths['example_sources']) as example:
        require(any(n.endswith('/build.gradle') or n == 'build.gradle' for n in example.namelist()),
                'Example sources lack an independent Gradle project')
        require(any(n.endswith('gradlew') for n in example.namelist()) and
                any(n.endswith('gradle-wrapper.jar') for n in example.namelist()), 'Example sources lack a Gradle wrapper')
        require(any(n.endswith('.java') for n in example.namelist()), 'Example sources contain no implementation')
    with checked_zip(paths['upstream_example_sources']) as example:
        require(any(n.endswith('/build.gradle') or n == 'build.gradle' for n in example.namelist()) and
                any(n.endswith('gradlew') for n in example.namelist()) and
                any(n.endswith('gradle-wrapper.jar') for n in example.namelist()) and
                any(n.endswith('.java') for n in example.namelist()), 'Upstream example sources lack an independent wrapped project')
    group, artifact, version = coordinate.split(':')
    maven_prefix = group.replace('.', '/') + f'/{artifact}/{version}/{artifact}-{version}'
    with checked_zip(paths['maven_repository']) as maven:
        for suffix, role in (('.jar', 'api'), ('-sources.jar', 'api_sources'), ('-javadoc.jar', 'api_javadoc')):
            require(maven_prefix + suffix in maven.namelist(), f'Local Maven repository lacks {suffix}')
            require(hashlib.sha256(maven.read(maven_prefix + suffix)).hexdigest() == records[role]['sha256'],
                    f'Local Maven {role} differs from accepted SDK artifact')
        pom = ET.fromstring(maven.read(maven_prefix + '.pom'))
        namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
        for key, expected in (('groupId', group), ('artifactId', artifact), ('version', version)):
            node = pom.find('m:' + key, namespace)
            require(node is not None and node.text == expected, f'API POM {key} mismatch')
        cee_group, cee_artifact, cee_version = cee_coordinate.split(':')
        cee_maven_prefix = cee_group.replace('.', '/') + f'/{cee_artifact}/{cee_version}/{cee_artifact}-{cee_version}'
        for suffix, role in (('.jar', 'cee_api'), ('-sources.jar', 'cee_api_sources'), ('-javadoc.jar', 'cee_api_javadoc')):
            require(cee_maven_prefix + suffix in maven.namelist(), f'Maven repository lacks CEE {suffix}')
            require(hashlib.sha256(maven.read(cee_maven_prefix + suffix)).hexdigest() == records[role]['sha256'],
                    f'Maven {role} differs from accepted artifact')
        cee_pom = ET.fromstring(maven.read(cee_maven_prefix + '.pom'))
        for key, expected in (('groupId', cee_group), ('artifactId', cee_artifact), ('version', cee_version)):
            node = cee_pom.find('m:' + key, namespace)
            require(node is not None and node.text == expected, f'CEE API POM {key} mismatch')
    abi = read(locked_file(manifest.get('abi', {}), root))
    require(abi.get('api_version') == API_VERSION and abi.get('current', {}).get('artifact', {}).get('sha256') == records['api']['sha256'],
            'ABI record describes a different artifact/version')
    require(abi.get('current', {}).get('types') == api_snapshot['types'], 'ABI snapshot does not describe actual classfile contracts')
    require(abi.get('changes') == [], 'ABI record contains unreviewed breaking changes')
    if abi.get('mode') == 'first_release_snapshot':
        require(abi.get('previous') is None and abi.get('compatible') is None,
                'First release must not claim comparison with a fictional earlier API')
    else:
        previous = locked_file(manifest.get('previous_api', {}), root)
        require(abi.get('mode') == 'comparison' and abi.get('compatible') is True
                and abi.get('previous', {}).get('sha256') == sha(previous), 'Previous API comparison missing')
        require(compare(snapshot(previous), api_snapshot) == [], 'Actual previous API contains breaking contract differences')
    cee_abi = read(locked_file(manifest.get('cee_abi', {}), root))
    require(cee_abi.get('api_version') == API_VERSION and
            cee_abi.get('current', {}).get('artifact', {}).get('sha256') == records['cee_api']['sha256'] and
            cee_abi.get('current', {}).get('types') == cee_snapshot['types'] and cee_abi.get('changes') == [],
            'CEE API contract snapshot describes different/unreviewed classfiles')
    if cee_abi.get('mode') == 'first_release_snapshot':
        require(cee_abi.get('previous') is None and cee_abi.get('compatible') is None, 'CEE first contract has no prior public artifact')
    else:
        previous = locked_file(manifest.get('previous_cee_api', {}), root)
        require(cee_abi.get('mode') == 'comparison' and cee_abi.get('compatible') is True and
                cee_abi.get('previous', {}).get('sha256') == sha(previous) and
                compare(snapshot(previous, (cee_prefix,)), cee_snapshot) == [], 'CEE previous-contract comparison failed')
    return paths


def accepted_record(descriptor, root, hashes, source_sha):
    record = read(locked_file(descriptor, root))
    require(record.get('completed') is True and record.get('passed') is True, 'Acceptance proof is not passing/complete')
    require(record.get('source_sha') == source_sha, 'Acceptance belongs to different source commit')
    require(record.get('artifact_hashes') == hashes, 'Acceptance proof belongs to different frozen artifacts')
    require(record.get('unresolved_failures') == [], 'Acceptance proof retains unresolved failures')
    return record


def validate_extracted_build(proof, role, manifest, root, hashes):
    """Checkout compilation is preliminary; the exact downloadable SDK is a gate."""
    source_role = 'example_sources' if role == 'example' else 'upstream_example_sources'
    require(proof.get('build_from_extracted_sdk') is True, 'Independent example was not built from the extracted downloadable SDK')
    require(proof.get('extracted_source_sha256') == manifest['artifacts'][source_role]['sha256']
            and proof.get('maven_repository_sha256') == manifest['artifacts']['maven_repository']['sha256'],
            'Extracted example/Maven archives differ from the release artifacts')
    compiled = locked_file(proof.get('compiled_output', {}), root)
    require(sha(compiled) == hashes[role], 'Extracted SDK build differs from the accepted packaged example')
    build_log = locked_file(proof.get('build_log', {}), root).read_text(encoding='utf-8', errors='replace')
    require('BUILD SUCCESSFUL' in build_log and 'BUILD FAILED' not in build_log, 'Extracted SDK compilation did not complete successfully')
    dependency_proof = read(locked_file(proof.get('dependency_proof', {}), root))
    required = {'api': hashes['api']}
    if role == 'upstream_example': required['cee_api'] = hashes['cee_api']
    require(dependency_proof.get('api_dependencies') == required
            and dependency_proof.get('wwpg_project_dependencies') == []
            and dependency_proof.get('compiled_source_count', 0) > 0,
            'Extracted example compile classpath omitted the frozen SDK or included a WWPG source project')


def validate_pending_restart(proof, root, hashes, version=None):
    version = version or VERSION
    """Retain the real three-process evidence, including assertions during shutdown."""
    retained = proof.get('pending_entity_restart', {})
    require(isinstance(retained, dict), 'Pending restart proof is not an object')
    oracle = locked_file(retained.get('oracle', {}), root)
    require(oracle.name == 'extension-api-pending-before-restart.nbt' and oracle.stat().st_size > 0,
            'Stopped-before-ready restart lacks the saved-state oracle')
    phases = retained.get('phases', [])
    require(isinstance(phases, list) and [item.get('phase') for item in phases] == ['SETUP', 'ARM', 'VERIFY'],
            'Pending restart requires three retained ordered JVM phases')
    logs = {}
    for item in phases:
        record = read(locked_file(item.get('manifest', {}), root))
        require(record.get('completed') and record.get('required_tests_passed', 0) > 0
                and record.get('phase') == item['phase'] and record.get('namespaces') == 'wwpg_api_pending_restart'
                and record.get('artifact') == f'wwpg-{version}.jar' and record.get('sha256') == hashes['runtime']
                and record.get('profile') == proof['profile'] and record.get('backend') == proof['backend']
                and set(record.get('addon_artifacts', {})) == {'example', 'upstream_example'}
                and all(record['addon_artifacts'][role].get('sha256') == hashes[role]
                        for role in ('example', 'upstream_example')),
                'Pending restart process differs from the accepted artifacts/profile/backend')
        text = locked_file(item.get('log', {}), root).read_text(encoding='utf-8', errors='replace')
        require('GAME TESTS COMPLETE' in text and re.search(r'All [1-9][0-9]* required tests passed', text)
                and not re.search(r'[1-9][0-9]* required tests failed|Tests? .* failed at', text),
                'Pending restart process log is incomplete or failed')
        logs[item['phase']] = text
    for phase, name, expected in [
        ('ARM', 'API_PENDING_RESTART_ARMED', dict(actualInboxHeld='true', savedBeforeReady='true',
            replacementZeroCharge='true', nativePG='true', nativeCEE='true')),
        ('VERIFY', 'API_PENDING_RESTART_PASSED', dict(actualInboxHeld='true', savedBeforeReady='true',
            diskTombstonesVerified='true', nativePG='true', nativeCEE='true', replacementIsolated='true',
            replacementZeroCharge='true', newWiresPowered='true', ownershipVersion='2'))]:
        matches = re.findall(r'\b' + re.escape(name) + r': ([^\r\n]+)', logs[phase])
        require(len(matches) == 1, 'Missing/duplicate real pending-restart assertion: ' + name)
        fields = {}
        for entry in re.split(r'[,;]\s*', matches[0]):
            key, separator, value = entry.partition('=')
            require(separator and key not in fields, 'Malformed pending-restart assertion: ' + name)
            fields[key] = value
        require(all(fields.get(key) == value for key, value in expected.items()),
                'Pending restart omitted a required actual assertion: ' + name)


def validate_ci_acceptance(manifest, root, ci, source, hashes, version=None, selection=None):
    if ci.get('kind') == 'retained_ci_cohort':
        require(selection is None, 'Nested CI cohorts are not supported')
        import release_ci_cohort
        return release_ci_cohort.validate(ci, manifest, root, source, hashes, sys.modules[__name__], version)
    require(selection in {None, 'NATIVE', 'JAVA'}, 'Invalid CI selection')
    require(ci.get('source_sha') == source and ci.get('status') == 'completed' and (ci.get('conclusion') == 'success'
            or selection == 'JAVA' and ci.get('conclusion') == 'failure'),
            'Fresh full CI has not passed on frozen source')
    require(re.fullmatch(r'https://github\.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/[0-9]+', ci.get('url', '')),
            'Missing repository CI provenance')
    jobs = ci.get('jobs', [])
    combinations = {(j.get('platform'), j.get('backend'), j.get('profile')) for j in jobs}
    local = manifest.get('retained_windows_acceptance')
    expected = {c for c in COMBINATIONS if c[0] == 'ubuntu-24.04'} if local else COMBINATIONS
    if selection is not None:
        require(not local and ci.get('selection_backend') == selection, 'Partial CI requires an explicit complete cohort')
        expected = {c for c in expected if c[1] == selection}
    require(len(jobs) == len(expected) and combinations == expected, 'Missing/duplicate required CI combination')
    if local:
        require(isinstance(manifest.get('acceptance_reuse'), dict), 'Local Windows acceptance requires version-only runtime reuse')
        import release_local_acceptance
        release_local_acceptance.validate(local, root, sys.modules[__name__], manifest['acceptance_reuse'])
    for job in jobs:
        require(job.get('status') == 'completed' and job.get('conclusion') == 'success', 'A required CI job did not pass')
        proof = accepted_record(job.get('proof', {}), root, hashes, source)
        if proof.get('retained_prefix'):
            import ci_reference_continuation
            ci_reference_continuation.validate(proof['retained_prefix'], root)
        require((proof.get('platform'), proof.get('backend'), proof.get('profile')) ==
                (job['platform'], job['backend'], job['profile']), 'Proof profile/backend/platform mismatch')
        checkout = read(locked_file(proof.get('checked_out_source', {}), root))
        if checkout.get('stationary_yard_policy') == 'combined-pinout-v1':
            require(manifest.get('stationary_yard_policy') == 'combined-pinout-v1',
                    'CI deferred yard coverage requires the separately validated combined yard')
        require(proof.get('checked_out_source_verified') is True and checkout.get('schema') == 1
                and checkout.get('source_sha') == source and checkout.get('source_tree') == proof.get('source_tree') == ci.get('source_tree')
                and re.fullmatch(r'[0-9a-f]{40}', checkout.get('source_tree', ''))
                and checkout.get('run_id') == ci.get('run_id') and checkout.get('run_attempt') == ci.get('run_attempt')
                and checkout.get('event') in {'pull_request', 'push', 'workflow_dispatch'}, 'Actual checked-out CI source/attempt is not proven')
        required = manifest.get('required_regression_checks', {}).get(job['profile'])
        require(isinstance(required, int) and required >= (208 if job['profile'] == 'PINOUT' else 204),
                'Release cannot lower existing beta.5 regression requirements')
        require(proof.get('regression_checks_passed', 0) >= required and proof.get('extension_checks_passed', 0) > 0,
                'Missing packaged regressions or extension checks')
        require(proof.get('cold_restart_passes', 0) >= 3 and proof.get('actual_chunk_reload_cycles', 0) >= 5,
                'Insufficient actual lifecycle acceptance')
        require(BEHAVIORS <= set(proof.get('api_fixture_behaviors', [])), 'An extension category lacks a passing fixture')
        require(proof.get('independent_cee_solves') == 0 and proof.get('exactly_once_history') is True,
                'Solver ownership/state advancement check failed')
        require(proof.get('cee_property_adapter_verified') is True, 'Public CEE property adapter lacks a passing runtime fixture')
        require({'wwpg_api', 'wwpg_api_faults', 'wwpg_api_restart', 'wwpg_api_upstream', 'wwpg_api_upstream_restart', 'wwpg_api_pause', 'wwpg_api_pending_restart'}
                <= set(proof.get('api_namespaces_passed', [])), 'A required packaged API fixture suite was omitted')
        require(proof.get('global_pause_verified') is True, 'Global pause and one-tick stepping lack passing acceptance')
        require(proof.get('nonconverging_backend_verified') is True and proof.get('paused_measurements_unavailable') is True,
                'Actual rejected solve and frozen-meter acceptance are incomplete')
        require(set(proof.get('cold_wire_systems_verified', [])) == {'CEE', 'POWER_GRID'}
                and proof.get('cold_ownership_format_version') == 2,
                'Cold restart must verify both native wire systems and v2 persisted ownership')
        require(proof.get('pending_wire_retirement_verified') is True
                and proof.get('pending_entity_restart_phases') == ['SETUP', 'ARM', 'VERIFY'],
                'Stopped-before-ready removal lacks the real isolated restart sequence')
        validate_pending_restart(proof, root, hashes, version)


def artifact_acceptance_source(manifest, root, field, roles):
    """Bind retained evidence to its actual source after exact artifact checks."""
    reuse = manifest.get(field)
    if reuse is None:
        return manifest['source_sha'], {role: manifest['artifacts'][role]['sha256'] for role in roles}
    label = {'sdk_acceptance_reuse': 'SDK', 'client_acceptance_reuse': 'Client'}.get(field, 'CI')
    require(reuse.get('schema') == 1 and reuse.get('scope') == 'runtime_version_only'
            and re.fullmatch(r'[0-9a-f]{40}', reuse.get('source_sha', '')),
            label + ' reuse needs its original immutable source and explicit scope')
    require(set(reuse.get('artifacts', {})) == set(roles), label + ' reuse must bind every original artifact')
    from release_acceptance_reuse import runtime_equivalent
    for role in sorted(roles):
        old = locked_file(reuse['artifacts'][role], root)
        current = locked_file(manifest['artifacts'][role], root)
        if role == 'runtime':
            runtime_equivalent(old, current, reuse['runtime_version'], manifest['wwpg_version'])
        else:
            require(sha(old) == sha(current), label + ' reuse changed input: ' + role)
    return reuse['source_sha'], {role: reuse['artifacts'][role]['sha256'] for role in roles}


def sdk_acceptance_source(manifest, root):
    if manifest.get('sdk_acceptance_reuse') is None:
        return manifest['source_sha']
    return artifact_acceptance_source(manifest, root, 'sdk_acceptance_reuse', ROLES)[0]


def validate_release(manifest, root):
    source = manifest.get('source_sha', '')
    require(re.fullmatch(r'[0-9a-f]{40}', source) is not None, 'Missing immutable source commit')
    if 'documentation_snapshot' in manifest: documentation_inputs(manifest, root)
    require(manifest.get('unresolved_failures') == [], 'Unresolved failures block API freeze/release')
    require(manifest.get('stages_complete') == list(range(1, 7)), 'All six extension API stages must pass')
    hashes = {key: manifest['artifacts'][key]['sha256'] for key in ('runtime', 'api', 'cee_api', 'example', 'upstream_example')}
    ci = read(locked_file(manifest.get('ci', {}), root))
    if manifest.get('acceptance_reuse'):
        import release_acceptance_reuse
        old_source, old_hashes, old_version = release_acceptance_reuse.validate(manifest, root, sys.modules[__name__])
        validate_ci_acceptance(manifest, root, ci, manifest['acceptance_reuse'].get('ci_source_sha', old_source), old_hashes, old_version)
    else:
        ci_source, ci_hashes = artifact_acceptance_source(manifest, root, 'ci_acceptance_reuse', hashes)
        validate_ci_acceptance(manifest, root, ci, ci_source, ci_hashes)
    sdk_source, sdk_hashes = source, hashes
    if manifest.get('sdk_acceptance_reuse'):
        sdk_source, original_sdk_hashes = artifact_acceptance_source(manifest, root, 'sdk_acceptance_reuse', ROLES)
        sdk_hashes = {key: original_sdk_hashes[key] for key in hashes}
    independent = accepted_record(manifest.get('independent_example_build', {}), root, sdk_hashes, sdk_source)
    require(independent.get('uses_published_api_only') is True and independent.get('uses_wwpg_project_classes') is False,
            'Example was not built independently against the published SDK')
    require(independent.get('compiled_against_api_sha256') == hashes['api'], 'Example used another API artifact')
    validate_extracted_build(independent, 'example', manifest, root, hashes)
    require(set(independent.get('functional_behaviors', [])) >= {'machine', 'storage', 'instrument'},
            'Independent general example lacks required extension demonstrations')
    upstream = accepted_record(manifest.get('independent_upstream_example_build', {}), root, sdk_hashes, sdk_source)
    require(upstream.get('uses_published_api_only') is True and upstream.get('uses_wwpg_project_classes') is False,
            'Upstream example was not built independently against published APIs')
    require(upstream.get('compiled_against_api_sha256') == hashes['api'] and
            upstream.get('compiled_against_cee_api_sha256') == hashes['cee_api'], 'Upstream example used different API artifacts')
    validate_extracted_build(upstream, 'upstream_example', manifest, root, hashes)
    require({'panel_attachment', 'board_component'} <= set(upstream.get('functional_behaviors', [])),
            'Independent upstream example lacks panel/board demonstrations')
    starter = manifest.get('authoring_starter')
    if starter is not None:
        archive = locked_file(starter.get('source', {}), root)
        built = read(locked_file(starter.get('proof', {}), root))
        starter_source = manifest['acceptance_reuse']['source_sha'] if manifest.get('acceptance_reuse') else sdk_source
        starter_runtime = manifest['acceptance_reuse']['runtime']['sha256'] if manifest.get('acceptance_reuse') else sdk_hashes['runtime']
        require(built.get('passed') is True and built.get('source_sha') == starter_source
                and built.get('source_archive_sha256') == sha(archive)
                and built.get('compiled_against_api_sha256') == hashes['api']
                and built.get('uses_wwpg_project_classes') is False,
                'Tutorial starter requires an exact independent build')
        output = locked_file(built.get('compiled_output', {}), root)
        provenance = read(locked_file(built.get('dependency_proof', {}), root))
        require(provenance.get('coordinate') == 'org.cha0scollective.wwpg:wwpg-api:' + API_VERSION
                and provenance.get('api_sha256') == hashes['api']
                and provenance.get('jar_sha256') == sha(output)
                and provenance.get('wwpg_project_dependencies') == [], 'Starter dependency provenance differs')
        build_log = locked_file(built.get('build_log', {}), root).read_text(encoding='utf-8', errors='replace')
        require('BUILD SUCCESSFUL' in build_log and 'BUILD FAILED' not in build_log,
                'Starter extracted-source build did not pass')
        covered = set()
        for item in built.get('gameplay', []):
            report = read(locked_file(item, root))
            key = (report.get('platform'), report.get('backend'))
            require(key in {(p, b) for p in PLATFORMS for b in BACKENDS} and key not in covered,
                    'Starter must have unique Windows/Linux reports on both backends')
            tested_runtime = (manifest['acceptance_reuse']['ci_runtime']['sha256']
                              if manifest.get('retained_windows_acceptance') and key[0] == 'ubuntu-24.04'
                              else starter_runtime)
            require(report.get('passed') is True and report.get('runtime_sha256') == tested_runtime
                    and report.get('starter_sha256') == sha(output), 'Starter gameplay used different jars')
            require({'controls', 'native_tools', 'unavailable', 'cold_restart_before_solve',
                     'actual_chunk_reload', 'replacement', 'dedicated_server'} <= set(report.get('checks', [])),
                    'Starter gameplay omits a required workflow')
            for descriptor in report.get('evidence', []): locked_file(descriptor, root)
            require(bool(report.get('evidence')), 'Starter gameplay lacks captured evidence')
            covered.add(key)
        require(covered == {(p, b) for p in PLATFORMS for b in BACKENDS}, 'Incomplete starter gameplay matrix')
        graphical = set()
        for descriptor in built.get('graphical', []):
            report = read(locked_file(descriptor, root))
            backend = report.get('backend')
            require(backend in BACKENDS and backend not in graphical and report.get('passed') is True
                    and report.get('runtime_sha256') == starter_runtime
                    and report.get('starter_sha256') == sha(output)
                    and report.get('controls') is True and report.get('native_tools') is True,
                    'Starter needs real graphical controls and native tools on both backends')
            require(bool(report.get('evidence')), 'Starter graphical proof lacks logs/screenshots')
            for item in report['evidence']: locked_file(item, root)
            graphical.add(backend)
        require(graphical == BACKENDS, 'Starter graphical acceptance is incomplete')
        with checked_zip(archive) as project:
            wrapper = project.getinfo('gradlew')
            require(wrapper.create_system == 3 and (wrapper.external_attr >> 16) & 0o111,
                    'Tutorial starter wrapper must survive ordinary Unix extraction')
    clients = [] if manifest.get('acceptance_reuse') else manifest.get('client_acceptance', [])
    client_source, client_hashes = artifact_acceptance_source(manifest, root, 'client_acceptance_reuse', hashes)
    expected = {(backend, profile, mode) for backend in BACKENDS for profile in PROFILES for mode in ('graphical', 'multiplayer')}
    found = set()
    for descriptor in clients:
        proof = accepted_record(descriptor, root, client_hashes, client_source)
        combination = (proof.get('backend'), proof.get('profile'), proof.get('mode'))
        require(combination in expected and combination not in found, 'Unexpected/duplicate real-client proof')
        found.add(combination)
        require(proof.get('real_graphical_client') is True, 'Client proof was headless/simulated')
        require(BEHAVIORS <= set(proof.get('api_fixture_behaviors', [])), 'Client missed a required extension demonstration')
        if combination[2] == 'multiplayer':
            require(len(set(proof.get('real_clients', []))) >= 2 and proof.get('real_tcp_clients') is True,
                    'Missing two actual multiplayer clients')
    require(manifest.get('acceptance_reuse') or found == expected, 'Both backends/profiles need graphical and real two-client checks')
    world_hashes = {}
    combined_yard = manifest.get('stationary_yard_policy') is not None
    if combined_yard:
        import release_stationary_acceptance
        release_stationary_acceptance.validate(manifest, root, sys.modules[__name__])
    worlds = [('base_workshop_world', 'BASE'), ('pinout_workshop_world', 'PINOUT')]
    if not combined_yard:
        worlds = [('base_world', 'BASE'), ('pinout_world', 'PINOUT')] + worlds
    for key, profile in worlds:
        descriptor = manifest.get(key, {})
        archive = locked_file(descriptor, root)
        with checked_zip(archive) as world: require(any(n.endswith('/level.dat') for n in world.namelist()), 'Playable example archive lacks level.dat')
        if 'workshop' in key:
            world_hashes[profile] = descriptor['sha256']
    for descriptor in clients:
        proof = read(locked_file(descriptor, root))
        require(proof.get('world_sha256') == world_hashes[proof['profile']], 'A client used another exact world/profile archive')


def collect_locked_evidence(value, root):
    """Follow retained JSON proof descriptors once, including relative client media."""
    entries, processed = {}, set()
    pending = [(value, root, False)]
    while pending:
        item, relative_base, relative_media = pending.pop()
        if isinstance(item, dict):
            descriptor = None
            if {'path', 'sha256', 'bytes'} <= item.keys():
                descriptor = item
            elif relative_media and {'file', 'sha256', 'bytes'} <= item.keys():
                name = item['file']
                require(isinstance(name, str) and PurePosixPath(name).name == name and '\\' not in name,
                        'Client log/screenshot filename must be relative to its retained proof')
                path = (relative_base / name).resolve()
                require(path.is_relative_to(root), 'Client media escaped the evidence workspace')
                descriptor = dict(path=path.relative_to(root).as_posix(), sha256=item['sha256'], bytes=item['bytes'])
            if descriptor is not None:
                path = locked_file(descriptor, root)
                key = path.relative_to(root).as_posix()
                entries['evidence/' + key] = path.read_bytes()
                if path.suffix.lower() == '.json' and key not in processed:
                    processed.add(key)  # Duplicate/cyclic proof references do not recurse.
                    pending.append((read(path), path.parent, False))
            else:
                pending.extend((child, relative_base, relative_media or key in {'logs', 'screenshots'})
                               for key, child in item.items())
        elif isinstance(item, list):
            pending.extend((child, relative_base, relative_media) for child in item)
    return entries


def package(manifest_path, root, output, release=False):
    root, output = Path(root).resolve(), Path(output).resolve()
    manifest_path = Path(manifest_path).resolve()
    require(output.is_relative_to(root) and output.suffix == '.zip', 'Output must be a workspace ZIP')
    require(not output.exists(), 'Preserve existing artifacts: output already exists')
    manifest = read(manifest_path)
    paths = validate_artifacts(manifest, root)
    if release: validate_release(manifest, root)
    entries = {f'artifacts/{path.name}': path.read_bytes() for path in paths.values()}
    require(len(entries) == len(paths), 'Artifact filenames collide')
    entries['manifest.json'] = manifest_path.read_bytes()
    entries['PUBLICATION_STATUS.json'] = json.dumps(dict(schema=1, wwpg_version=VERSION, api_version=API_VERSION,
        publishable=release, state='accepted_release' if release else 'sdk_candidate',
        message='All declared acceptance gates passed.' if release else 'Candidate only. API freeze and release acceptance have not been certified.'),
        indent=2).encode('utf-8') + b'\n'
    documentation_source, documents = documentation_inputs(manifest, root)
    for name, path in sorted(documents.items()):
        require(path.is_file(), f'Missing SDK documentation/license: {name}')
        text = path.read_text(encoding='utf-8')
        entries[name] = bundle_markdown(text,name,set(documents),VERSION,documentation_source).encode('utf-8')
    starter = manifest.get('authoring_starter')
    if starter is not None:
        entries['documentation-starter/'+Path(starter['source']['path']).name] = locked_file(starter['source'],root).read_bytes()
    # Evidence must remain inspectable inside the package, not just on the build host.
    entries.update(collect_locked_evidence({k: v for k, v in manifest.items() if k != 'artifacts'}, root))
    sums = ''.join(hashlib.sha256(data).hexdigest() + '  ' + name + '\n' for name, data in sorted(entries.items()))
    entries['SHA256SUMS.txt'] = sums.encode('utf-8')
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 11, 0, 0, 0))
            info.compress_type, info.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
            archive.writestr(info, data)
    return dict(file=output.name, sha256=sha(output), bytes=output.stat().st_size, publishable=release)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--manifest', type=Path)
    mode.add_argument('--maven-source', type=Path, help='Archive both declared API versions without asserting release acceptance')
    mode.add_argument('--example-source', type=Path, help='Export an independent wrapped example project without build outputs/caches')
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--release', action='store_true', help='Enforce every freeze and public-release acceptance gate')
    args = parser.parse_args()
    try:
        require(not ((args.maven_source or args.example_source) and args.release), '--release requires the complete frozen manifest')
        if args.maven_source:
            result = package_maven(args.maven_source, args.root, args.output)
        elif args.example_source:
            result = package_example(args.example_source, args.root, args.output)
        else:
            result = package(args.manifest, args.root, args.output, args.release)
        print(json.dumps(result))
    except (PublicationError, OSError, KeyError, ValueError, zipfile.BadZipFile, ET.ParseError) as error:
        parser.exit(2, f'SDK packaging blocked: {error}\n')


if __name__ == '__main__': main()
