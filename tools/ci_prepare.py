"""Build once, then check the frozen SDK once per OS. No Minecraft launches."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
from test_suites import ROOT, descriptor, freeze, frozen_inputs, host_platform, write


def retain_preparation(output, record):
    """Snapshot dependencies before another worker consumes the preparation.

    In particular, Gradle's JUnit reports live outside the uploaded campaign
    directory. Portable evidence must carry those bytes, not producer paths.
    The runnable starter project remains beside preparation.json.
    """
    from bundle_suite_evidence import bundle
    original = output/'preparation-original.json'
    write(original, record)
    portable = bundle(original, output/'preparation-evidence')
    shutil.copyfile(portable, output/'preparation.json')
    return output/'preparation.json'


def checked(command, log, cwd=ROOT):
    with Path(log).open('xb') as stream:
        subprocess.run([str(v) for v in command], cwd=cwd, stdout=stream, stderr=subprocess.STDOUT, check=True)


def prepare(output, inputs=None):
    output = Path(output).resolve(); output.mkdir(parents=True, exist_ok=False)
    wrapper = ROOT/('gradlew.bat' if os.name == 'nt' else 'gradlew')
    if inputs is None:
        checked([sys.executable, ROOT/'tools/run_checked.py', '--timeout', '1200', '--', wrapper,
                 'assemble', 'referenceJar', 'publishAllPublicationsToSdkRepository', '--no-daemon'], output/'assemble.log')
        # Seed artifacts for the independent compile comparison, once in the
        # producer rather than in every solver/profile worker.
        for name in ('extension-addon', 'cee-addon'):
            checked([sys.executable, ROOT/'tools/run_checked.py', '--timeout', '1200', '--', wrapper,
                     '-p', ROOT/'examples'/name, 'build', '--no-daemon'], output/(name+'.log'))
        inputs = freeze(output/'frozen')
    frozen = frozen_inputs(inputs)
    checked([sys.executable, ROOT/'tools/run_checked.py', '--timeout', '1200', '--', wrapper,
             'check', '--no-daemon'], output/'check.log')
    from package_api_release import package_example, package_maven
    maven = output/'maven.zip'
    # A downloaded canonical Maven ZIP is used on consumers; APIs are never
    # republished from the consumer's compilation into the tested SDK.
    canonical_maven = Path(inputs).resolve().parent/'maven.zip'
    if canonical_maven.is_file(): shutil.copyfile(canonical_maven, maven)
    else: package_maven(ROOT/'build/maven-repository', ROOT, maven)
    sdk = {r: d['path'] for r,d in frozen['artifacts'].items() if r in ('runtime','api','cee_api','example','upstream_example')}
    sdk['maven'] = maven
    for role, name in [('example_source','extension-addon'), ('upstream_source','cee-addon')]:
        archive = output/(role+'.zip'); package_example(ROOT/'examples'/name, ROOT, archive); sdk[role] = archive
    from verify_extracted_api_sdk import verify
    result = verify(sdk, frozen['source_sha'], output/'extracted-sdk')
    from authoring_starter_acceptance import prepare as starter_prepare
    starter_prepare(output/'starter', Path(sdk['runtime']), maven, frozen['source_sha'], api_artifact=Path(sdk['api']))
    record = dict(schema=2, policy='separated-v2', passed=True,
        source_sha=frozen['source_sha'], platform=host_platform(), inputs=descriptor(inputs), check_log=descriptor(output/'check.log'),
        sdk=result, starter=descriptor(output/'starter/build-proof.json'),
        unit_execution='gradle-cache' if 'Task :test FROM-CACHE' in (output/'check.log').read_text(errors='replace') else 'executed-or-up-to-date',
        junit=[descriptor(p) for p in sorted((ROOT/'build/test-results/test').glob('TEST-*.xml'))])
    if (output/'frozen').is_dir():
        shutil.copyfile(maven, output/'frozen/maven.zip')
    return retain_preparation(output, record)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True); parser.add_argument('--inputs', type=Path)
    args=parser.parse_args(); print(prepare(args.output, args.inputs))
