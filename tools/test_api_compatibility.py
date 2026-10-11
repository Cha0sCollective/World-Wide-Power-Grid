"""Exercise artifact gates with independently compiled Java consumers and JARs."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

from verify_api_compatibility import ContractError, compare, snapshot
from package_api_release import BEHAVIORS, COMBINATIONS, PublicationError, locked_file, package_example, package_maven, validate_release, validate_api_metadata, validate_version_accessor

PREFIX = 'example/api/'
ROOT = Path(__file__).resolve().parents[1]


def executable(name):
    supplied = os.environ.get('JAVA_HOME')
    if supplied and (Path(supplied) / 'bin' / (name + '.exe')).exists():
        return str(Path(supplied) / 'bin' / (name + '.exe'))
    found = shutil.which(name)
    if found: return found
    candidates = sorted(Path('C:/Program Files/Eclipse Adoptium').glob(f'jdk-21*/bin/{name}.exe'))
    return str(candidates[-1]) if candidates else None


class JavaContractChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.javac, cls.java = executable('javac'), executable('java')
        if not cls.javac or not cls.java:
            raise RuntimeError('A JDK is required: ABI tests must not silently skip compiled consumers')

    def setUp(self):
        scratch = ROOT / 'build/api-tool-tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch)
        self.root = Path(self.temp.name)

    def tearDown(self): self.temp.cleanup()

    def compile(self, name, source):
        directory = self.root / name
        src = directory / 'Api.java'
        directory.mkdir()
        src.write_text('package example.api;\n' + source, encoding='utf-8')
        subprocess.run([self.javac, '--release', '21', '-d', str(directory), str(src)],
                       check=True, capture_output=True, text=True, encoding='utf-8')
        jar = self.root / (name + '.jar')
        with zipfile.ZipFile(jar, 'w') as output:
            for file in directory.rglob('*.class'): output.write(file, file.relative_to(directory).as_posix())
        return jar

    def changes(self, old, new): return compare(snapshot(old, (PREFIX,)), snapshot(new, (PREFIX,)))

    def compile_version(self, name, version, *, constant=False, accessor=True):
        directory = self.root / name
        directory.mkdir()
        source = directory / 'WWPGApi.java'
        value = '"' + version + '"' if constant else 'readVersion()'
        source.write_text('package org.cha0scollective.wwpg.api; public final class WWPGApi { '
            'public static final String API_VERSION = ' + value + '; '
            'private static String readVersion() { return "' + version + '"; } '
            + ('public static String version() { return API_VERSION; } ' if accessor else '') + '}', encoding='utf-8')
        subprocess.run([self.javac, '--release', '21', '-d', str(directory), str(source)],
                       check=True, capture_output=True)
        jar = self.root / (name + '.jar')
        with zipfile.ZipFile(jar, 'w') as output:
            for file in directory.rglob('*.class'): output.write(file, file.relative_to(directory).as_posix())
        return jar

    def test_runtime_version_is_not_inlined_into_an_already_compiled_addon(self):
        old = self.compile_version('old-version', '1.0.0')
        new = self.compile_version('new-version', '1.1.0')
        validate_version_accessor(snapshot(old))
        validate_version_accessor(snapshot(new))
        self.assertEqual(compare(snapshot(old), snapshot(new)), [])
        consumer = self.root / 'VersionConsumer.java'
        consumer.write_text('import org.cha0scollective.wwpg.api.WWPGApi; '
            'public class VersionConsumer { public static void main(String[] args) { '
            'if (!WWPGApi.API_VERSION.equals("1.1.0") || !WWPGApi.version().equals("1.1.0")) '
            'throw new AssertionError("inlined version metadata"); } }', encoding='utf-8')
        subprocess.run([self.javac, '--release', '21', '-cp', str(old), '-d', str(self.root), str(consumer)],
                       check=True, capture_output=True)
        subprocess.run([self.java, '-cp', str(new) + os.pathsep + str(self.root), 'VersionConsumer'],
                       check=True, capture_output=True)

    def test_inlined_version_field_and_missing_public_accessor_block_publication(self):
        for name, options in [('inlined', dict(constant=True)), ('missing', dict(accessor=False))]:
            with self.subTest(name=name), self.assertRaises(PublicationError):
                validate_version_accessor(snapshot(self.compile_version(name, '1.0.0', **options)))

    def test_first_release_is_snapshot_without_fictional_compatibility(self):
        jar = self.compile('first', 'public interface Api { double voltage(); }')
        output = self.root / 'contract.json'
        subprocess.run([sys.executable, str(ROOT / 'tools/verify_api_compatibility.py'),
                        str(jar), '--prefix', 'example.api', '--api-version', '1.0.0', '--output', str(output)],
                       check=True, capture_output=True, text=True)
        report = json.loads(output.read_text(encoding='utf-8'))
        self.assertEqual(report['mode'], 'first_release_snapshot')
        self.assertIsNone(report['compatible'])
        self.assertIsNone(report['previous'])

    def test_previous_compiled_consumer_runs_against_compatible_addition(self):
        previous = self.compile('old', 'public class Api { public double voltage() { return 12; } }')
        current = self.compile('new', 'public class Api { public double voltage() { return 12; } public double current() { return 1; } }')
        self.assertEqual(self.changes(previous, current), [])
        consumer = self.root / 'Consumer.java'
        consumer.write_text('import example.api.Api; public class Consumer { public static void main(String[] args) '
                            '{ if (new Api().voltage() != 12) throw new AssertionError(); } }', encoding='utf-8')
        subprocess.run([self.javac, '--release', '21', '-cp', str(previous), '-d', str(self.root), str(consumer)],
                       check=True, capture_output=True)
        subprocess.run([self.java, '-cp', str(current) + os.pathsep + str(self.root), 'Consumer'],
                       check=True, capture_output=True)
        broken = self.compile('broken', 'public class Api { public float voltage() { return 12; } }')
        failure = subprocess.run([self.java, '-cp', str(broken) + os.pathsep + str(self.root), 'Consumer'],
                                 capture_output=True, text=True)
        self.assertNotEqual(failure.returncode, 0)
        self.assertIn('NoSuchMethodError', failure.stderr)
        self.assertIn('removed_member', {c['kind'] for c in self.changes(previous, broken)})

    def test_inlined_constants_and_new_abstract_obligations_fail(self):
        old = self.compile('old', 'public interface Api { int VERSION = 1; double voltage(); }')
        new = self.compile('new', 'public interface Api { int VERSION = 2; double voltage(); double current(); }')
        self.assertEqual({c['kind'] for c in self.changes(old, new)}, {'constant', 'new_abstract_method'})

    def test_source_generics_static_dispatch_and_visibility_fail(self):
        old = self.compile('old', 'public class Api { public static int read() { return 1; } public java.util.List<String> names() { return null; } public double voltage() { return 1; } }')
        new = self.compile('new', 'public class Api { public int read() { return 1; } public java.util.List<Integer> names() { return null; } protected double voltage() { return 1; } }')
        self.assertEqual({c['kind'] for c in self.changes(old, new)}, {'static', 'signature', 'visibility'})

    def test_protected_nested_api_and_private_enclosing_are_distinguished(self):
        jar = self.compile('nested', 'public class Api { protected static class Extension { public int x; } private static class Hidden { public static class Child {} } }')
        types = snapshot(jar, (PREFIX,))['types']
        self.assertIn('example/api/Api$Extension', types)
        self.assertEqual(types['example/api/Api$Extension']['flags'] & 8, 8)
        self.assertNotIn('example/api/Api$Hidden', types)
        self.assertNotIn('example/api/Api$Hidden$Child', types)

    def test_truncated_and_duplicate_classfiles_fail(self):
        jar = self.compile('valid', 'public class Api {}')
        data = zipfile.ZipFile(jar).read('example/api/Api.class')
        bad = self.root / 'truncated.jar'
        with zipfile.ZipFile(bad, 'w') as output: output.writestr('example/api/Api.class', data[:40])
        with self.assertRaises(ContractError): snapshot(bad, (PREFIX,))
        missing = self.root / 'empty.jar'
        with zipfile.ZipFile(missing, 'w'): pass
        with self.assertRaises(ContractError): snapshot(missing, (PREFIX,))


class PublicationGateChecks(unittest.TestCase):
    """Controlled summaries live only in temporary test directories, never release evidence."""
    def setUp(self):
        scratch = ROOT / 'build/api-tool-tests'
        scratch.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=scratch)
        self.root = Path(self.temp.name)
        self.source = 'a' * 40
        self.hashes = dict(runtime='1' * 64, api='2' * 64, example='3' * 64, cee_api='4' * 64, upstream_example='5' * 64)
        self.compiled_outputs = {}
        for role in ('example', 'upstream_example'):
            path = self.root / (role + '-compiled.jar')
            path.write_bytes(('TEST_ONLY_COMPILED_OUTPUT_' + role).encode('utf-8'))
            self.compiled_outputs[role] = self.describe(path)
            self.hashes[role] = self.compiled_outputs[role]['sha256']
        self.manifest = dict(source_sha=self.source, unresolved_failures=[], stages_complete=list(range(1, 7)),
                             required_regression_checks=dict(BASE=204, PINOUT=208),
                             artifacts={key: dict(sha256=value) for key, value in self.hashes.items()})
        self.manifest['artifacts'].update(example_sources=dict(sha256='6' * 64),
            upstream_example_sources=dict(sha256='7' * 64), maven_repository=dict(sha256='8' * 64))
        self.jobs, self.records = [], {}
        for i, (platform, backend, profile) in enumerate(sorted(COMBINATIONS)):
            record = self.proof(platform=platform, backend=backend, profile=profile,
                regression_checks_passed=208 if profile == 'PINOUT' else 204, extension_checks_passed=10,
                cold_restart_passes=3, actual_chunk_reload_cycles=5, api_fixture_behaviors=sorted(BEHAVIORS),
                independent_cee_solves=0, exactly_once_history=True, cee_property_adapter_verified=True,
                api_namespaces_passed=['wwpg_api', 'wwpg_api_faults', 'wwpg_api_restart', 'wwpg_api_upstream', 'wwpg_api_upstream_restart', 'wwpg_api_pause'],
                global_pause_verified=True, nonconverging_backend_verified=True, paused_measurements_unavailable=True,
                cold_wire_systems_verified=['CEE', 'POWER_GRID'], cold_ownership_format_version=2)
            self.records[f'job-{i}'] = record
            self.jobs.append(dict(platform=platform, backend=backend, profile=profile, status='completed',
                                  conclusion='success', proof=self.lock(f'job-{i}.json', record)))
        self.ci = dict(source_sha=self.source, status='completed', conclusion='success',
                       url='https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/123', jobs=self.jobs)
        self.manifest['ci'] = self.lock('ci.json', self.ci)
        independent = self.proof(uses_published_api_only=True, uses_wwpg_project_classes=False,
            compiled_against_api_sha256=self.hashes['api'], functional_behaviors=sorted(BEHAVIORS),
            **self.extracted('example'))
        self.manifest['independent_example_build'] = self.lock('independent.json', independent)
        upstream = self.proof(uses_published_api_only=True, uses_wwpg_project_classes=False,
            compiled_against_api_sha256=self.hashes['api'], compiled_against_cee_api_sha256=self.hashes['cee_api'],
            functional_behaviors=['panel_attachment', 'board_component'], **self.extracted('upstream_example'))
        self.manifest['independent_upstream_example_build'] = self.lock('independent-upstream.json', upstream)
        self.clients = []
        for profile in ('BASE', 'PINOUT'):
            world = self.root / (profile + '.zip')
            with zipfile.ZipFile(world, 'w') as zipped: zipped.writestr(profile + '/level.dat', b'TEST_ONLY')
            descriptor = self.describe(world)
            self.manifest['base_world' if profile == 'BASE' else 'pinout_world'] = descriptor
            workshop = self.root / (profile + '-workshop.zip')
            with zipfile.ZipFile(workshop, 'w') as zipped:
                zipped.writestr('Workshop-' + profile + '/level.dat', b'API_WORKSHOP_TEST_ONLY')
            descriptor = self.describe(workshop)
            self.manifest['base_workshop_world' if profile == 'BASE' else 'pinout_workshop_world'] = descriptor
            for backend in ('NATIVE', 'JAVA'):
                for mode in ('graphical', 'multiplayer'):
                    proof = self.proof(profile=profile, backend=backend, mode=mode,
                        real_graphical_client=True, api_fixture_behaviors=sorted(BEHAVIORS),
                        real_clients=['A', 'B'], real_tcp_clients=True, world_sha256=descriptor['sha256'])
                    self.clients.append(self.lock(f'{profile}-{backend}-{mode}.json', proof))
        self.manifest['client_acceptance'] = self.clients

    def tearDown(self): self.temp.cleanup()

    def proof(self, **extra):
        return dict(completed=True, passed=True, source_sha=self.source,
                    artifact_hashes=self.hashes, unresolved_failures=[], **extra)

    def extracted(self, role):
        deps = dict(api=self.hashes['api'])
        if role == 'upstream_example': deps['cee_api'] = self.hashes['cee_api']
        path = self.root / (role + '-build.log')
        path.write_text('TEST ONLY EXTRACTED SDK BUILD LOG\nBUILD SUCCESSFUL\n', encoding='utf-8')
        return dict(build_from_extracted_sdk=True,
            extracted_source_sha256=self.manifest['artifacts']['example_sources' if role == 'example' else 'upstream_example_sources']['sha256'],
            maven_repository_sha256=self.manifest['artifacts']['maven_repository']['sha256'],
            compiled_output=self.compiled_outputs[role], build_log=self.describe(path),
            dependency_proof=self.lock(role + '-dependencies.json', dict(api_dependencies=deps,
                wwpg_project_dependencies=[], compiled_source_count=1)))

    def describe(self, path):
        import hashlib
        return dict(path=path.relative_to(self.root).as_posix(), bytes=path.stat().st_size,
                    sha256=hashlib.sha256(path.read_bytes()).hexdigest())

    def lock(self, name, value):
        path = self.root / name
        path.write_text(json.dumps(value), encoding='utf-8')
        return self.describe(path)

    def change_job(self, key, value):
        record = dict(self.records['job-0'])
        record[key] = value
        self.jobs[0]['proof'] = self.lock('job-0.json', record)
        self.manifest['ci'] = self.lock('ci.json', self.ci)

    def test_complete_fixture_is_accepted_but_missing_or_duplicate_job_is_rejected(self):
        validate_release(self.manifest, self.root)
        self.jobs[-1] = self.jobs[0]
        self.manifest['ci'] = self.lock('ci.json', self.ci)
        with self.assertRaises(PublicationError): validate_release(self.manifest, self.root)

    def test_retry_missing_lifecycle_stale_source_and_second_solver_are_rejected(self):
        for key, value in [('cold_restart_passes', 2), ('actual_chunk_reload_cycles', 4),
                           ('passed', False), ('source_sha', 'b' * 40), ('independent_cee_solves', 1),
                           ('exactly_once_history', False), ('global_pause_verified', False), ('nonconverging_backend_verified', False),
                           ('paused_measurements_unavailable', False), ('cold_wire_systems_verified', ['POWER_GRID']),
                           ('cold_ownership_format_version', 1), ('api_namespaces_passed', ['wwpg_api']), ('regression_checks_passed', 0),
                           ('unresolved_failures', ['unexplained failure']), ('api_fixture_behaviors', ['machine'])]:
            self.change_job(key, value)
            with self.subTest(key=key), self.assertRaises(PublicationError): validate_release(self.manifest, self.root)
            self.change_job(key, self.records['job-0'][key])

    def test_archive_client_profile_mismatch_is_rejected(self):
        descriptor = self.clients[0]
        record = json.loads((self.root / descriptor['path']).read_text(encoding='utf-8'))
        record['world_sha256'] = self.manifest['pinout_workshop_world']['sha256']
        self.clients[0] = self.lock(descriptor['path'], record)
        with self.assertRaises(PublicationError): validate_release(self.manifest, self.root)

    def test_ci_checkout_build_and_changed_downloadable_sdk_do_not_pass_final_gate(self):
        descriptor = self.manifest['independent_example_build']
        original = json.loads((self.root / descriptor['path']).read_text(encoding='utf-8'))
        for changes in [dict(build_from_extracted_sdk=False), dict(extracted_source_sha256='a' * 64),
                        dict(maven_repository_sha256='b' * 64), dict(compiled_output=self.compiled_outputs['upstream_example'])]:
            record = dict(original, **changes)
            self.manifest['independent_example_build'] = self.lock(descriptor['path'], record)
            with self.subTest(changes=changes), self.assertRaises(PublicationError): validate_release(self.manifest, self.root)
        self.manifest['independent_example_build'] = self.lock(descriptor['path'], original)
        dependency = json.loads((self.root / original['dependency_proof']['path']).read_text(encoding='utf-8'))
        dependency['wwpg_project_dependencies'] = [':wwpg']
        original['dependency_proof'] = self.lock(original['dependency_proof']['path'], dependency)
        self.manifest['independent_example_build'] = self.lock(descriptor['path'], original)
        with self.assertRaises(PublicationError): validate_release(self.manifest, self.root)

    def test_changed_evidence_and_traversal_are_rejected(self):
        descriptor = self.manifest['ci']
        (self.root / descriptor['path']).write_text('{}', encoding='utf-8')
        with self.assertRaises(PublicationError): validate_release(self.manifest, self.root)
        with self.assertRaises(PublicationError): locked_file(dict(path='../ci.json', sha256='1' * 64, bytes=0), self.root)

    def test_incomplete_stages_and_unresolved_failures_block_freeze(self):
        self.manifest['stages_complete'] = [1, 2, 3]
        with self.assertRaises(PublicationError): validate_release(self.manifest, self.root)
        self.manifest['stages_complete'] = list(range(1, 7))
        self.manifest['unresolved_failures'] = ['panel workflow unimplemented']
        with self.assertRaises(PublicationError): validate_release(self.manifest, self.root)

    def test_jar_version_metadata_rejects_missing_wrong_duplicate_and_entry_only_values(self):
        manifests = [None, 'Manifest-Version: 1.0\r\nWWPG-API-Version: 0.2.0\r\n\r\n',
            'Manifest-Version: 1.0\r\nWWPG-API-Version: 1.0.0\r\nwwpg-api-version: 1.0.0\r\n\r\n',
            'Manifest-Version: 1.0\r\n\r\nName: example.class\r\nWWPG-API-Version: 1.0.0\r\n\r\n']
        for i, metadata in enumerate(manifests):
            path = self.root / f'bad-metadata-{i}.jar'
            with zipfile.ZipFile(path, 'w') as output:
                if metadata is not None: output.writestr('META-INF/MANIFEST.MF', metadata)
            with zipfile.ZipFile(path) as archive, self.subTest(metadata=metadata), self.assertRaises(PublicationError):
                validate_api_metadata(archive, 'TEST_ONLY SDK')
        path = self.root / 'folded.jar'
        with zipfile.ZipFile(path, 'w') as output:
            output.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\nWWPG-API-Version: 1.\r\n 0.0\r\n\r\n')
        with zipfile.ZipFile(path) as archive: validate_api_metadata(archive, 'TEST_ONLY SDK')

    def maven_fixture(self):
        repository = self.root / 'repository'
        for artifact in ('wwpg-api', 'wwpg-cee-api'):
            directory = repository / 'org/cha0scollective/wwpg' / artifact / '1.0.0'
            directory.mkdir(parents=True)
            for suffix in ('.jar', '-sources.jar', '-javadoc.jar'):
                with zipfile.ZipFile(directory / (artifact + '-1.0.0' + suffix), 'w') as jar:
                    jar.writestr('TEST_ONLY', 'Artifact gate unit test fixture')
            (directory / (artifact + '-1.0.0.pom')).write_text(
                '<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>'
                '<groupId>org.cha0scollective.wwpg</groupId><artifactId>' + artifact + '</artifactId>'
                '<version>1.0.0</version></project>', encoding='utf-8')
            (directory.parent / 'maven-metadata.xml').write_text('Mutable publication timestamp', encoding='utf-8')
        return repository

    def test_maven_archive_is_reproducible_and_omits_floating_metadata(self):
        repository = self.maven_fixture()
        first = self.root / 'first.zip'
        second = self.root / 'second.zip'
        one = package_maven(repository, self.root, first)
        (repository / 'org/cha0scollective/wwpg/wwpg-api/maven-metadata.xml').write_text('Later timestamp', encoding='utf-8')
        two = package_maven(repository, self.root, second)
        self.assertEqual(one['sha256'], two['sha256'])
        self.assertFalse(one['publishable'])
        with zipfile.ZipFile(first) as archive:
            self.assertEqual(len(archive.namelist()), 8)
            self.assertFalse(any('maven-metadata' in n for n in archive.namelist()))
        with self.assertRaises(PublicationError): package_maven(repository, self.root, first)

    def test_incomplete_maven_publication_is_rejected(self):
        repository = self.maven_fixture()
        missing = repository / 'org/cha0scollective/wwpg/wwpg-cee-api/1.0.0/wwpg-cee-api-1.0.0-javadoc.jar'
        missing.unlink()
        with self.assertRaises(PublicationError): package_maven(repository, self.root, self.root / 'bad.zip')

    def example_fixture(self):
        source = self.root / 'example'
        files = {'build.gradle': 'plugins { id "java-library" }', 'settings.gradle': 'rootProject.name="example"',
                 'gradlew': '#!/bin/sh\n', 'gradlew.bat': '@echo off\r\n', 'LICENSE': 'TEST ONLY LICENSE',
                 'README.md': 'TEST ONLY INSTRUCTIONS', 'gradle/wrapper/gradle-wrapper.jar': 'TEST ONLY WRAPPER',
                 'gradle/wrapper/gradle-wrapper.properties': 'TEST ONLY WRAPPER CONFIG',
                 'src/main/java/example/Addon.java': 'package example; import org.cha0scollective.wwpg.api.ElectricalDevice;'}
        for name, contents in files.items():
            path = source / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(contents, encoding='utf-8')
        return source

    def test_independent_source_export_excludes_cache_and_preserves_unix_wrapper_mode(self):
        source = self.example_fixture()
        (source / 'build').mkdir(); (source / 'build/private.txt').write_text('EXCLUDED')
        (source / '.gradle').mkdir(); (source / '.gradle/cache.bin').write_bytes(b'EXCLUDED')
        first, second = self.root / 'first-source.zip', self.root / 'second-source.zip'
        one, two = package_example(source, self.root, first), package_example(source, self.root, second)
        self.assertEqual(one['sha256'], two['sha256']); self.assertFalse(one['publishable'])
        with zipfile.ZipFile(first) as archive:
            self.assertNotIn('build/private.txt', archive.namelist()); self.assertNotIn('.gradle/cache.bin', archive.namelist())
            self.assertEqual(archive.getinfo('gradlew').external_attr >> 16, 0o100755)
            self.assertIn('gradle/wrapper/gradle-wrapper.jar', archive.namelist())
        with self.assertRaises(PublicationError): package_example(source, self.root, first)

    def test_private_runtime_import_and_missing_wrapper_block_independent_source_export(self):
        source = self.example_fixture()
        path = source / 'src/main/java/example/Addon.java'
        path.write_text('package example;\nimport org.cha0scollective.wwpg.bridge.WorldBridge;\n', encoding='utf-8')
        with self.assertRaises(PublicationError): package_example(source, self.root, self.root / 'bad-source.zip')
        path.write_text('package example;\n', encoding='utf-8')
        (source / 'gradle/wrapper/gradle-wrapper.jar').unlink()
        with self.assertRaises(PublicationError): package_example(source, self.root, self.root / 'bad-source.zip')


if __name__ == '__main__': unittest.main()
