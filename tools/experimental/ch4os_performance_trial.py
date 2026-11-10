"""Freeze and run the Public instance's server performance stack on copied yards."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tomllib
import zipfile

import plain_wire_benchmark as runner
from cpu_profile import write_new
from summarize_tree import ROOT,evidence,read,sha

PACK=Path('C:/Users/Parker/AppData/Roaming/PrismLauncher/instances/Create-Ch4oS-Public-Prism')
PATTERNS=('lithium-','modernfix-','ferritecore-','alltheleaks-','smoothchunk-','leaky-',
          'Clumps-','connectivity-','packetfixer-','recipeessentials-','structureessentials-','cupboard-')
CONFIGS=('lithium','modernfix','ferritecore','alltheleaks','smoothchunk','leaky',
         'clumps','connectivity','packetfixer','recipeessentials','structureessentials','cupboard')


def verify(bundle):
    for item in bundle['files']+bundle['java_inputs']:
        assert sha(Path(item['path']))==item['sha256'],item['path']


def freeze(args):
    folder=(ROOT/'local/frozen'/args.name).resolve()
    assert folder.is_relative_to(ROOT/'local/frozen')
    folder.mkdir(parents=True,exist_ok=False)
    game=PACK/'.minecraft'
    settings={}
    for line in (PACK/'instance.cfg').read_text().splitlines():
        key,sep,value=line.partition('=')
        if sep and key in ('JvmArgs','JavaPath','MinMemAlloc','MaxMemAlloc','OverrideMemory','OverrideJavaArgs','OverrideJavaLocation'):
            settings[key]=value
    assert settings['JvmArgs']=='-XX:+UseZGC -XX:+ZGenerational'
    assert settings['MinMemAlloc']=='1024' and settings['MaxMemAlloc']=='14000'
    java=Path(settings['JavaPath']).with_name('java.exe'); assert java.is_file()
    sources=[]
    for prefix in PATTERNS:
        matches=list((game/'mods').glob(prefix+'*.jar')); assert len(matches)==1,prefix
        target=folder/'mods'/matches[0].name; target.parent.mkdir(exist_ok=True)
        shutil.copy2(matches[0],target)
        sources.append(dict(source=str(matches[0]),source_sha256=sha(matches[0]),copied=evidence(target)))
    config=folder/'config'; config.mkdir()
    for source in sorted((game/'config').iterdir()):
        if source.is_file() and source.suffix in ('.json','.toml','.properties') and source.name.lower().startswith(CONFIGS):
            shutil.copy2(source,config/source.name)
            sources.append(dict(source=str(source),source_sha256=sha(source)))
    sable=next((game/'mods').glob('sable-neoforge-*.jar'))
    with zipfile.ZipFile(sable) as archive:
        exclusions=tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())['lithium:options']
    assert len(exclusions)==4 and all(value is False for value in exclusions.values())
    lithium=config/'lithium.properties'
    with lithium.open('a') as stream:
        stream.write('\n# Match Public Sable compatibility policy without loading its gameplay systems.\n')
        for key,value in sorted(exclusions.items()): stream.write(key+'=false\n')
    shutil.copy2(__file__,folder/'freezer.py')
    probe=[str(java),'-Xms1024M','-Xmx14000M','-XX:+UseZGC','-XX:+ZGenerational','-XX:+PrintCommandLineFlags','-version']
    result=subprocess.run(probe,capture_output=True,timeout=30)
    (folder/'java-probe.log').write_bytes(result.stdout+result.stderr)
    text=(result.stdout+result.stderr).decode(errors='replace')
    assert result.returncode==0 and '+UseZGC' in text and '+ZGenerational' in text and '21.0.7' in text
    java_inputs=[java,java.parent/'server/jvm.dll',java.parent.parent/'release',java.parent.parent/'lib/modules']
    proof=dict(schema=1,settings=settings,java=str(java),sources=sources,sable=dict(path=str(sable),sha256=sha(sable)),
        lithium_compatibility_exclusions=exclusions,
        files=[dict(path=str(path),sha256=sha(path)) for path in sorted(folder.rglob('*')) if path.is_file()],
        java_inputs=[dict(path=str(path),sha256=sha(path)) for path in java_inputs],
        limitations=['Headless: Sodium, Sodium Extra, ImmediatelyFast and GPU Memory Leak Fix are not exercised.',
            'Only the performance stack is copied; the other Public gameplay mods are absent.',
            'Sable Lithium exclusions are reproduced as explicit rules; Sable itself is absent.',
            'NeoForge/Create/Architectury/PG/CEE and the WWPG control remain pinned to the previous control inputs.',
            'This combined Java/GC/heap/mod/config trial cannot isolate individual contributions.'])
    write_new(folder/'inputs.json',proof)
    print(json.dumps(dict(bundle=str(folder/'inputs.json'),mods=len(PATTERNS),configs=len(list(config.iterdir())),java=str(java))))


def run(args):
    bundle_path=(ROOT/args.bundle_inputs).resolve(); bundle=read(bundle_path); verify(bundle)
    original=runner.prepare
    def prepare(options,**kwargs):
        directory,reference,source=original(options,**kwargs)
        for item in bundle['files']:
            path=Path(item['path']); relative=path.relative_to(bundle_path.parent)
            if relative.parts[0] in ('mods','config'):
                target=directory/relative; assert not target.exists()
                target.parent.mkdir(parents=True,exist_ok=True); shutil.copy2(path,target)
        launch=read(directory/'launch.json')
        old=(directory/'launch.args').read_text()
        prefix='-Xms512M\n-Xmx8G\n'; assert old.startswith(prefix)
        flags='-Xms1024M\n-Xmx14000M\n-XX:+UseZGC\n-XX:+ZGenerational\n-XX:+PrintCommandLineFlags\n-Xlog:gc=info:file=gc.log:uptime,level,tags\n'
        (directory/'launch.args').write_text(flags+old[len(prefix):],newline='\n')
        launch['command'][0]=bundle['java']
        launch['profile'].update(name='CH4OS_PUBLIC_SERVER_PERFORMANCE_STACK',heap_gib=None,heap_max_mib=14000,heap_initial_mib=1024,
            collector='Generational ZGC',bundle=evidence(bundle_path),limitations=bundle['limitations'])
        additions=[path for sub in ('mods','config') for path in (directory/sub).iterdir() if path.is_file()]
        existing={item['path'] for item in launch['profile_inputs']}
        launch['profile_inputs'] += [dict(path=str(path),sha256=sha(path)) for path in additions if str(path) not in existing]
        launch['profile_inputs'] += bundle['java_inputs']
        wrapper=directory/'runner-sources/ch4os_performance_trial.py'; shutil.copy2(__file__,wrapper)
        launch['runner_sources'].append(dict(path=str(wrapper),sha256=sha(wrapper)))
        runner.bench.write(directory/'launch.json',launch)
        props=directory/'server.properties'; settings=props.read_text().replace('view-distance=2\n','view-distance=12\n').replace('simulation-distance=2\n','simulation-distance=10\n')
        props.write_text(settings,newline='\n')
        write_new(directory/'performance-stack.json',dict(bundle=evidence(bundle_path),profile=launch['profile'],
            applied_mods=[evidence(path) for path in sorted((directory/'mods').glob('*.jar'))],
            applied_configs=[evidence(path) for path in sorted((directory/'config').iterdir())]))
        return directory,reference,source
    runner.prepare=prepare
    runner.main()
    verify(bundle)


def main():
    mode=sys.argv.pop(1)
    parser=argparse.ArgumentParser(add_help=False)
    if mode=='freeze':
        parser.add_argument('--name',required=True); freeze(parser.parse_args()); return
    assert mode=='run'
    parser.add_argument('--bundle-inputs',required=True)
    args,rest=parser.parse_known_args(); sys.argv=[sys.argv[0],*rest]
    run(args)


if __name__=='__main__': main()
