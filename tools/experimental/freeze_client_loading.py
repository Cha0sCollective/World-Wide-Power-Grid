"""Freeze an isolated integrated-client observer using the existing queue-aware correctness gate."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

from freeze_tree import ROOT, sha


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--inputs', required=True)
    parser.add_argument('--base-helper-inputs', required=True)
    parser.add_argument('--inspect-delivery', action='store_true')
    parser.add_argument('--inspect-task-queues', action='store_true')
    args = parser.parse_args()
    if args.inspect_task_queues and not args.inspect_delivery:
        parser.error('--inspect-task-queues requires --inspect-delivery')
    runtime_inputs = json.loads((ROOT / args.inputs).read_text())
    parent = json.loads((ROOT / args.base_helper_inputs).read_text())
    parent_jar = Path(parent['helper']['path'])
    assert sha(parent_jar) == parent['helper']['sha256']
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in parent['sources'])
    runtime = runtime_inputs['artifacts']['runtime']
    cee = runtime_inputs['artifacts']['cee']
    for artifact in (runtime, cee):
        assert sha(Path(artifact['path'])) == artifact['sha256']
    folder = (ROOT / 'local/frozen' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local/frozen')
    folder.mkdir(parents=True, exist_ok=False)
    sources = ROOT / 'tools/experimental/client_loading'
    shutil.copytree(sources / 'src', folder / 'src')
    shutil.copytree(sources / 'resources', folder / 'resources')
    if args.inspect_delivery:
        inspector = ROOT / 'tools/experimental/client_delivery'
        for sub in ('src', 'resources'):
            shutil.copytree(inspector / sub, folder / sub, dirs_exist_ok=True)
        if not args.inspect_task_queues:
            config_path = folder / 'resources/wwpg-client-delivery.mixins.json'
            config = json.loads(config_path.read_text())
            config['client'].remove('TaskQueueTimingMixin')
            config_path.write_text(json.dumps(config, indent=2) + '\n', encoding='utf-8')
    package = 'org/cha0scollective/wwpg/benchmark'
    session_source = next(item for item in parent['sources'] if item['path'].endswith('/Session.java'))
    text = (ROOT / session_source['path']).read_text()
    old = 'Path.of("world/WWPG_BENCHMARK.json")'
    assert text.count(old) == 3
    text = text.replace(old, 'server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("WWPG_BENCHMARK.json")')
    old = 'private boolean ended;'
    assert text.count(old) == 1
    text = text.replace(old, 'private boolean ended, passed;\n'
        '    // Read only from the integrated server thread by the separate client observer.\n'
        '    public static Boolean loadCompletion() { return current == null || !current.ended ? null : current.passed; }')
    old = 'if(ended)return;ended=true;'
    assert text.count(old) == 1
    text = text.replace(old, 'if(ended)return;ended=true;this.passed=passed;')
    target = folder / 'src' / package / 'Session.java'
    target.parent.mkdir(parents=True)
    target.write_text(text, encoding='utf-8', newline='\n')
    with zipfile.ZipFile(parent_jar) as archive:
        metadata = archive.read('META-INF/neoforge.mods.toml').decode()
    metadata += '\n[[mods]]\nmodId="wwpg_benchmark_client"\nversion="0.1.0"\ndisplayName="WWPG focused client loading observer"\n'
    metadata += 'description="Disposable copied-save timing helper; not graphical acceptance."\n[[mixins]]\nconfig="wwpg-client-loading.mixins.json"\n'
    if args.inspect_delivery:
        metadata += '\n[[mods]]\nmodId="wwpg_benchmark_delivery"\nversion="0.1.0"\ndisplayName="WWPG chunk delivery inspector"\n'
        metadata += 'description="Read-only focused diagnostic; not clean timing evidence."\n[[mixins]]\nconfig="wwpg-client-delivery.mixins.json"\n'
    metadata_path = folder / 'resources/META-INF/neoforge.mods.toml'
    metadata_path.parent.mkdir(parents=True)
    metadata_path.write_text(metadata, encoding='utf-8', newline='\n')
    shutil.copy2(ROOT / 'tools/experimental/client-loading.gradle', folder / 'client-loading.gradle')
    env = dict(os.environ)
    env['JAVA_HOME'] = runtime_inputs['java_home']
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']
    command = ['gradlew.bat', 'prepareClientLoadingTemplate', '--offline', '--no-daemon', '-I', str(folder / 'client-loading.gradle'),
               '-PclientLoadingFolder=' + str(folder), '-PclientLoadingRuntime=' + runtime['path'],
               '-PclientLoadingCee=' + cee['path'], '-PclientLoadingParent=' + str(parent_jar)]
    with (folder / 'compile.log').open('w') as log:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        print((folder / 'compile.log').read_text(errors='replace')[-10000:])
        raise SystemExit(result.returncode)
    helper = folder / 'wwpg-benchmark-client-loading.jar'
    with zipfile.ZipFile(parent_jar) as original, zipfile.ZipFile(helper) as revised:
        changed = [name for name in original.namelist() if not name.endswith('/')
                   and (name not in revised.namelist() or original.read(name) != revised.read(name))]
        required_changes = {package + '/Session.class', 'META-INF/neoforge.mods.toml'}
        assert required_changes <= set(changed) <= required_changes | {'META-INF/MANIFEST.MF'}, changed
    template = json.loads((folder / 'launch-template.json').read_text())
    classpath = [dict(path=name, sha256=sha(Path(name))) for name in template['classpath'] if Path(name).is_file()]
    assert all(Path(name).is_file() for name in template['classpath']), 'Unexpected mutable classpath directory'
    files = [p for sub in ('src', 'resources', 'launch') for p in (folder / sub).rglob('*') if p.is_file()]
    files += [folder / 'client-loading.gradle', folder / 'launch-template.json']
    manifest = dict(schema=1, helper=dict(path=str(helper), sha256=sha(helper)), runtime=runtime, cee=cee,
                    base_helper=parent['helper'], parent_inputs=args.base_helper_inputs, java_home=runtime_inputs['java_home'],
                    changed_parent_entries=changed, classpath=classpath,
                    sources=[dict(path=p.relative_to(ROOT).as_posix(), sha256=sha(p)) for p in sorted(files)],
                    process_affinity_supported=True, server_subset_supported=True,
                    inspection_only=args.inspect_delivery, inspect_delivery=args.inspect_delivery,
                    inspect_task_queues=args.inspect_task_queues,
                    scope='Integrated copied-save load and stationary rendering observation only; no acceptance suites')
    (folder / 'inputs.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(inputs=str(folder / 'inputs.json'), helper=manifest['helper'])))


if __name__ == '__main__':
    main()
