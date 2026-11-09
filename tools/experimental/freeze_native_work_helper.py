"""Freeze a helper-only observer for JNI arguments and native callbacks."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

from cpu_profile import write_new
from freeze_affinity_helper import replace_once
from freeze_tree import ROOT, sha


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
    assert all(sha(ROOT / s['path']) == s['sha256'] for s in parent['sources'] + runtime['sources'])
    folder = (ROOT / 'local/frozen' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local/frozen')
    folder.mkdir(parents=True, exist_ok=False)
    package = 'org/cha0scollective/wwpg/benchmark'
    source = folder / 'src' / package
    (source / 'mixin').mkdir(parents=True)
    for path in (ROOT / 'tools/experimental/native-work-helper').glob('*.java'):
        shutil.copy2(path, source / ('mixin/' if path.name.endswith('Mixin.java') else '') / path.name)
    plugin = (ROOT / 'local/inputs/helper-src' / package / 'BenchmarkMixinPlugin.java').read_text()
    plugin = replace_once(plugin, 'if(mixinClassName.contains(".Substep"))',
        'if(mixinClassName.endsWith(".NativeWorkMixin"))return Boolean.getBoolean("wwpg.benchmark.nativeWork");\n        if(mixinClassName.contains(".Substep"))')
    (source / 'BenchmarkMixinPlugin.java').write_text(plugin, encoding='utf-8', newline='\n')
    tick = (ROOT / 'local/inputs/helper-src' / package / 'mixin/TickMixin.java').read_text()
    tick = replace_once(tick, '        Metrics.reset();', '        Metrics.reset();org.cha0scollective.wwpg.benchmark.NativeWork.beginTick();')
    tick = replace_once(tick, 'org.cha0scollective.wwpg.benchmark.Substeps.write(phase,tickNanos,Session.observationTick());',
        'org.cha0scollective.wwpg.benchmark.Substeps.write(phase,tickNanos,Session.observationTick());\n'
        '            org.cha0scollective.wwpg.benchmark.NativeWork.write(phase,tickNanos,Session.observationTick());')
    (source / 'mixin/TickMixin.java').write_text(tick, encoding='utf-8', newline='\n')
    env = dict(os.environ, JAVA_HOME=runtime['java_home'])
    env['PATH'] = str(Path(runtime['java_home']) / 'bin') + os.pathsep + env['PATH']
    command = ['gradlew.bat', 'compileBudgetBenchmarkHelper', '--offline', '--no-daemon',
        '-I', 'tools/experimental/budget-helper.gradle', '-PbudgetHelperDirectory=' + str(folder),
        '-PbudgetRuntime=' + runtime['artifacts']['runtime']['path']]
    with (folder / 'compile.log').open('x', encoding='utf-8') as stream:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        print((folder / 'compile.log').read_text(errors='replace')[-12000:])
        raise SystemExit(result.returncode)
    classes = {p.relative_to(folder / 'classes').as_posix(): p.read_bytes() for p in (folder / 'classes').rglob('*.class')}
    output = folder / 'wwpg-benchmark-0.1.0-native-work.jar'
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(output, 'w') as new:
        config = json.loads(old.read('wwpg-benchmark.mixins.json'))
        config['mixins'].append('NativeWorkMixin')
        for item in old.infolist():
            data = classes.pop(item.filename) if item.filename in classes else old.read(item)
            if item.filename == 'wwpg-benchmark.mixins.json': data = (json.dumps(config, indent=2) + '\n').encode()
            new.writestr(item, data)
        for name, data in sorted(classes.items()): new.writestr(name, data)
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(output) as new:
        changed = sorted(n for n in old.namelist() if old.read(n) != new.read(n))
        assert set(changed) == {'wwpg-benchmark.mixins.json', package + '/BenchmarkMixinPlugin.class', package + '/mixin/TickMixin.class'}
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert added and all(n.startswith(package + '/NativeWork') or n == package + '/mixin/NativeWorkMixin.class' for n in added)
    files = list((folder / 'src').rglob('*.java')) + list((ROOT / 'tools/experimental/native-work-helper').glob('*.java'))
    files += [ROOT / 'tools/experimental' / n for n in ('freeze_native_work_helper.py', 'budget-helper.gradle')]
    sources = parent['sources'] + [dict(path=p.relative_to(ROOT).as_posix(), sha256=sha(p)) for p in sorted(files)]
    write_new(folder / 'inputs.json', dict(schema=1, helper=dict(path=str(output), sha256=sha(output)),
        base_helper=parent['helper'], parent_inputs=args.base_helper_inputs, runtime=runtime['artifacts']['runtime'],
        sources=sources, process_affinity_supported=parent['process_affinity_supported'],
        server_subset_supported=parent.get('server_subset_supported', False), native_work_supported=True,
        changed_entries=changed, added_entries=added, command=command))
    print(json.dumps(dict(inputs=str(folder / 'inputs.json'), helper_sha256=sha(output), changed_entries=changed, added_entries=added)))


if __name__ == '__main__':
    main()
