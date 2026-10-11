"""Compile the exact exported example projects against an extracted Maven ZIP.

This executes each example's own Gradle wrapper in a fresh isolated directory.
It never compiles the WWPG project, publishes artifacts, or freezes the API.
Every attempt retains its inputs, build logs and resolved compile dependencies.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import zipfile

from package_api_release import API_VERSION, checked_zip, validate_api_metadata, validate_version_accessor
from verify_api_compatibility import snapshot

ROOT = Path(__file__).resolve().parents[1]
ROLES = ('runtime', 'api', 'cee_api', 'example', 'upstream_example')


class SdkBuildError(ValueError): pass


def require(value, message):
    if not value: raise SdkBuildError(message)


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def descriptor(path, root):
    path, root = Path(path).resolve(), Path(root).resolve()
    require(path.is_file() and path.is_relative_to(root), 'SDK evidence must remain in the workspace')
    return dict(path=path.relative_to(root).as_posix(), sha256=sha(path), bytes=path.stat().st_size)


def write(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def extract(archive, destination):
    """Extract only a fresh, bounded, regular-file archive; retain Unix modes."""
    require(not destination.exists(), 'Preserve previous SDK attempts: extraction destination exists')
    with checked_zip(archive) as zipped:
        members = zipped.infolist()
        require(len(members) < 100000 and sum(item.file_size for item in members) < 512 * 1024 * 1024,
                'SDK archive exceeds the bounded extraction size')
        for item in members:
            mode = item.external_attr >> 16
            require((mode & 0o170000) in (0, 0o100000, 0o040000), 'SDK archive contains a symlink or special file')
        destination.mkdir(parents=True)
        zipped.extractall(destination)
        if os.name != 'nt':
            for item in members:
                if item.filename.endswith('gradlew') and not item.is_dir():
                    (destination / item.filename).chmod(0o755)


def audit_project(directory):
    required = {'build.gradle', 'settings.gradle', 'gradlew', 'gradlew.bat', 'LICENSE', 'README.md',
                'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties'}
    require(all((directory / name).is_file() for name in required), 'Extracted example lacks its own wrapped project')
    require(not any((directory / name).exists() for name in ('build', '.gradle')), 'Source ZIP includes prebuilt output or caches')
    for name in ('build.gradle', 'settings.gradle'):
        text = (directory / name).read_text(encoding='utf-8')
        require(not re.search(r'\b(?:project|includeBuild)\s*\(', text), 'Example depends on a WWPG source project')
    sources = list((directory / 'src').rglob('*.java'))
    require(sources, 'Extracted example contains no Java sources')
    for path in sources:
        imports = re.findall(r'\bimport\s+(?:static\s+)?(org\.cha0scollective\.wwpg\.[\w.*]+)', path.read_text(encoding='utf-8'))
        require(all(value.startswith(('org.cha0scollective.wwpg.api.', 'org.cha0scollective.wwpg.example.',
                                     'org.cha0scollective.wwpg.examples.')) for value in imports), 'Extracted example imports private WWPG implementation')


INIT = r'''import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.compile.JavaCompile
gradle.projectsEvaluated {
    def project = gradle.rootProject
    def sha256 = { File file -> java.security.MessageDigest.getInstance('SHA-256').digest(file.bytes).encodeHex().toString() }
    project.tasks.named('compileJava', JavaCompile).configure {
        doFirst {
            def artifacts = project.configurations.compileClasspath.resolvedConfiguration.resolvedArtifacts
            def api = [:]
            artifacts.each { artifact ->
                if (artifact.moduleVersion.id.group == 'org.cha0scollective.wwpg') {
                    def key = ['wwpg-api':'api', 'wwpg-cee-api':'cee_api'][artifact.name]
                    if (key == null || artifact.moduleVersion.id.version != '1.0.0')
                        throw new GradleException('Unexpected WWPG implementation/version dependency: ' + artifact)
                    api[key] = sha256(artifact.file)
                }
            }
            def expected = new groovy.json.JsonSlurper().parse(new File(project.findProperty('sdkExpectedHashes')))
            if (api != expected) throw new GradleException('Compile dependencies differ from extracted frozen SDK: ' + api)
            def projects = artifacts.findAll { it.id.componentIdentifier instanceof ProjectComponentIdentifier }.collect { it.id.componentIdentifier.toString() }
            if (!projects.empty) throw new GradleException('Example compile classpath includes source projects: ' + projects)
            def proof = [schema:1, api_dependencies:api, wwpg_project_dependencies:projects,
                compiled_source_count:source.files.size(),
                compile_classpath:classpath.files.collect { [path:it.absolutePath, sha256:it.isFile() ? sha256(it) : null] }]
            new File(project.findProperty('sdkDependencyProof')).text = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(proof)) + '\n'
        }
    }
}
'''


def verify(inputs, source_sha, output, timeout=1200, root=ROOT):
    root, output = Path(root).resolve(), Path(output).resolve()
    paths = {key: Path(path).resolve() for key, path in inputs.items()}
    require(re.fullmatch(r'[0-9a-f]{40}', source_sha), 'Provide the frozen source commit SHA')
    require(output.is_relative_to(root) and not output.exists(), 'Use a fresh workspace SDK evidence directory')
    require(set(paths) == set(ROLES) | {'maven', 'example_source', 'upstream_source'}, 'Missing a frozen SDK input')
    require(all(path.is_file() and path.is_relative_to(root) for path in paths.values()), 'Frozen SDK inputs must exist in the workspace')
    hashes = {role: sha(paths[role]) for role in ROLES}
    validate_version_accessor(snapshot(paths['api']))
    for role in ('runtime', 'api', 'cee_api'):
        with checked_zip(paths[role]) as archive: validate_api_metadata(archive, role)
    output.mkdir(parents=True)
    write(output / 'attempt.json', dict(schema=1, completed=False, passed=False, source_sha=source_sha,
        artifact_hashes=hashes, inputs={key: descriptor(path, root) for key, path in paths.items()}))
    maven = output / 'maven-repository'
    extract(paths['maven'], maven)
    for artifact, role in [('wwpg-api', 'api'), ('wwpg-cee-api', 'cee_api')]:
        sdk = maven / f'org/cha0scollective/wwpg/{artifact}/{API_VERSION}/{artifact}-{API_VERSION}.jar'
        require(sdk.is_file() and sha(sdk) == hashes[role], 'Extracted Maven API differs from frozen artifact')
    init = output / 'compile-sdk-evidence.gradle'
    init.write_text(INIT, encoding='utf-8', newline='\n')
    result = {}
    for role, source_role, directory in [('example', 'example_source', 'general-example'),
                                         ('upstream_example', 'upstream_source', 'upstream-example')]:
        project = output / directory
        extract(paths[source_role], project)
        audit_project(project)
        expected = {'api': hashes['api']}
        if role == 'upstream_example': expected['cee_api'] = hashes['cee_api']
        expected_path, dependencies = output / (role + '-expected.json'), output / (role + '-dependencies.json')
        write(expected_path, expected)
        log = output / (role + '-build.log')
        wrapper = project / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
        command = [sys.executable, str(root / 'tools/run_checked.py'), '--timeout', str(timeout), '--', str(wrapper),
                   'clean', 'build', '--no-daemon', '-I', str(init), '-PapiRepository=' + maven.as_uri(),
                   '-PsdkExpectedHashes=' + str(expected_path), '-PsdkDependencyProof=' + str(dependencies)]
        with log.open('w', encoding='utf-8') as stream:
            process = subprocess.run(command, cwd=project, stdout=stream, stderr=subprocess.STDOUT, check=False)
        text = log.read_text(encoding='utf-8', errors='replace')
        require(process.returncode == 0 and 'BUILD SUCCESSFUL' in text and 'BUILD FAILED' not in text,
                f'Extracted {role} build failed; preserve {log}')
        built = project / 'build/libs' / paths[role].name
        require(built.is_file() and sha(built) == hashes[role], 'Extracted SDK build differs from frozen packaged example')
        dependency_proof = json.loads(dependencies.read_text(encoding='utf-8'))
        require(dependency_proof['api_dependencies'] == expected and dependency_proof['wwpg_project_dependencies'] == []
                and dependency_proof['compiled_source_count'] > 0, 'Actual SDK compile proof is incomplete')
        proof = dict(schema=1, completed=True, passed=True, unresolved_failures=[], source_sha=source_sha,
            artifact_hashes=hashes, uses_published_api_only=True, uses_wwpg_project_classes=False,
            build_from_extracted_sdk=True, extracted_source_sha256=sha(paths[source_role]),
            maven_repository_sha256=sha(paths['maven']), compiled_against_api_sha256=hashes['api'],
            compiled_output=descriptor(built, root), build_log=descriptor(log, root), dependency_proof=descriptor(dependencies, root),
            functional_behaviors=['machine', 'storage', 'instrument'] if role == 'example' else ['panel_attachment', 'board_component'],
            evidence_files=[descriptor(paths[source_role], root), descriptor(paths['maven'], root), descriptor(init, root)])
        if role == 'upstream_example': proof['compiled_against_cee_api_sha256'] = hashes['cee_api']
        path = output / (role + '-build-proof.json'); write(path, proof); result[role] = descriptor(path, root)
    summary = dict(schema=1, completed=True, passed=True, source_sha=source_sha, artifact_hashes=hashes,
        publishable=False, api_frozen=False, scope='Exact downloadable SDK archives extracted and independently compiled', proofs=result)
    write(output / 'attempt.json', summary)
    return summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for role in ROLES: parser.add_argument('--' + role.replace('_', '-'), type=Path, required=True)
    parser.add_argument('--maven', type=Path, required=True)
    parser.add_argument('--example-source', type=Path, required=True)
    parser.add_argument('--upstream-source', type=Path, required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--timeout', type=int, default=1200)
    args = parser.parse_args()
    try:
        require(args.timeout > 0, 'Build timeout must be positive')
        inputs = {key: getattr(args, key) for key in ROLES + ('maven', 'example_source', 'upstream_source')}
        print(json.dumps(verify(inputs, args.source_sha, args.output, args.timeout)))
    except (ValueError, OSError, KeyError, zipfile.BadZipFile) as error:
        parser.exit(2, f'Extracted SDK acceptance failed: {error}\n')


if __name__ == '__main__': main()
