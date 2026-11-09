"""Build and audit a diagnostic-only helper overlay; preserve the gameplay jar."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

from freeze_tree import ROOT, sha
from freeze_affinity_helper import replace_once
from cpu_profile import write_new


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--base-helper-inputs', required=True)
    args = parser.parse_args()
    runtime = json.loads((ROOT / args.inputs).read_text())
    parent = json.loads((ROOT / args.base_helper_inputs).read_text())
    original = Path(parent['helper']['path'])
    assert sha(original) == parent['helper']['sha256']
    assert all(sha(ROOT / s['path']) == s['sha256'] for s in parent['sources'])
    assert all(sha(ROOT / s['path']) == s['sha256'] for s in runtime['sources'])
    folder = (ROOT / 'local/frozen' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local/frozen')
    folder.mkdir(parents=True, exist_ok=False)
    package = 'org/cha0scollective/wwpg/benchmark'
    source = folder / 'src' / package
    source.mkdir(parents=True)
    (source / 'mixin').mkdir()
    for path in (ROOT / 'tools/experimental/preparation-helper').glob('*.java'):
        shutil.copy2(path, source / ('mixin/' if path.name == 'StablePreparationMixin.java' else '') / path.name)
    plugin = (ROOT / 'local/inputs/helper-src' / package / 'BenchmarkMixinPlugin.java').read_text()
    plugin = replace_once(plugin, 'if(mixinClassName.contains(".Substep"))',
        'if(mixinClassName.endsWith(".StablePreparationMixin"))return Boolean.getBoolean("wwpg.benchmark.preparationSections");\n        if(mixinClassName.contains(".Substep"))')
    plugin = replace_once(plugin, 'public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }',
        'public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {\n'
        '        if (!Boolean.getBoolean("wwpg.benchmark.preparationSections")) return;\n'
        '        if (mixin.endsWith(".BridgeTimingMixin")) PreparationInstrumenter.install(node, false);\n'
        '        if (mixin.endsWith(".StablePreparationMixin")) PreparationInstrumenter.install(node, true);\n    }')
    (source / 'BenchmarkMixinPlugin.java').write_text(plugin, encoding='utf-8', newline='\n')
    env = dict(os.environ, JAVA_HOME=runtime['java_home'])
    env['PATH'] = str(Path(runtime['java_home']) / 'bin') + os.pathsep + env['PATH']
    command = ['gradlew.bat', 'checkPreparationInstrumentation', '--offline', '--no-daemon',
        '-I', 'tools/experimental/budget-helper.gradle', '-I', 'tools/experimental/preparation-helper.gradle',
        '-PbudgetHelperDirectory=' + str(folder), '-PbudgetRuntime=' + runtime['artifacts']['runtime']['path']]
    with (folder / 'compile.log').open('x', encoding='utf-8') as stream:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        print((folder / 'compile.log').read_text(errors='replace')[-12000:])
        raise SystemExit(result.returncode)
    checks = json.loads((folder / 'instrumentation-checks.json').read_text())
    assert checks['passed']
    classes = {p.relative_to(folder / 'classes').as_posix():p.read_bytes() for p in (folder / 'classes').rglob('*.class')
               if not p.name.startswith('PreparationInstrumentationChecks')}
    output = folder / 'wwpg-benchmark-0.1.0-preparation.jar'
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(output, 'w') as new:
        config = json.loads(old.read('wwpg-benchmark.mixins.json'))
        config['mixins'].append('StablePreparationMixin')
        for item in old.infolist():
            data = classes.pop(item.filename) if item.filename in classes else old.read(item)
            if item.filename == 'wwpg-benchmark.mixins.json':
                data = (json.dumps(config, indent=2) + '\n').encode()
            new.writestr(item, data)
        for name, data in sorted(classes.items()):
            new.writestr(name, data)
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(output) as new:
        changed = [n for n in old.namelist() if old.read(n) != new.read(n)]
        assert set(changed) == {'wwpg-benchmark.mixins.json', package + '/BenchmarkMixinPlugin.class'}
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert all(n.startswith(package + '/Preparation') or n == package + '/mixin/StablePreparationMixin.class' for n in added)
        assert added
    files = list((folder / 'src').rglob('*.java')) + list((ROOT / 'tools/experimental/preparation-helper').glob('*.java'))
    files += [ROOT / 'tools/experimental' / n for n in ('freeze_preparation_helper.py','preparation-helper.gradle','budget-helper.gradle')]
    sources = parent['sources'] + [dict(path=p.relative_to(ROOT).as_posix(), sha256=sha(p)) for p in sorted(files)]
    write_new(folder / 'inputs.json', dict(schema=1, helper=dict(path=str(output), sha256=sha(output)),
        base_helper=parent['helper'], parent_inputs=args.base_helper_inputs, runtime=runtime['artifacts']['runtime'], sources=sources,
        process_affinity_supported=parent['process_affinity_supported'], server_subset_supported=parent.get('server_subset_supported', False),
        preparation_sections_supported=True, changed_entries=changed, added_entries=added, command=command,
        instrumentation_checks=dict(path=str(folder / 'instrumentation-checks.json'), sha256=sha(folder / 'instrumentation-checks.json'))))
    print(json.dumps(dict(inputs=str(folder / 'inputs.json'), helper_sha256=sha(output), checks=checks)))


if __name__ == '__main__':
    main()
