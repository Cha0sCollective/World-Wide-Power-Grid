"""Extend a frozen JNI observer with read-only native stage snapshots."""
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
    inputs_path, parent_path = ROOT / args.inputs, ROOT / args.base_helper_inputs
    runtime, parent = json.loads(inputs_path.read_text()), json.loads(parent_path.read_text())
    original = Path(parent['helper']['path'])
    assert parent['native_work_supported'] and sha(original) == parent['helper']['sha256']
    assert all(sha(ROOT / s['path']) == s['sha256'] for s in parent['sources'] + runtime['sources'])
    folder = (ROOT / 'local/frozen' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local/frozen')
    folder.mkdir(parents=True, exist_ok=False)
    package = 'org/cha0scollective/wwpg/benchmark'
    source = folder / 'src' / package
    (source / 'mixin').mkdir(parents=True)
    frozen_source = parent_path.parent / 'src' / package
    work = (frozen_source / 'NativeWork.java').read_text()
    work = replace_once(work, '        long start, nanos;', '        long start, nanos;\n        long[] stages;')
    work = replace_once(work, 'end(Frame frame, boolean returned, ByteBuffer values)',
        'end(Frame frame, boolean returned, ByteBuffer values, long pointer)')
    work = replace_once(work, '        frame.owner = null;',
        '        frame.owner = null;\n        frame.stages = NativeStages.capture(pointer);')
    work = replace_once(work, 'rows,native_ns\\n");', 'rows,native_ns" + NativeStages.header() + "\\n");')
    work = replace_once(work, '+ f.nanos + "\\n");', '+ f.nanos + NativeStages.columns(f.stages) + "\\n");')
    (source / 'NativeWork.java').write_text(work, encoding='utf-8', newline='\n')
    mixin = (frozen_source / 'mixin/NativeWorkMixin.java').read_text()
    mixin = replace_once(mixin, 'NativeWork.end(frame, returned, result);', 'NativeWork.end(frame, returned, result, pointer);')
    (source / 'mixin/NativeWorkMixin.java').write_text(mixin, encoding='utf-8', newline='\n')
    helper_source = ROOT / 'tools/experimental/native-stage-helper/NativeStages.java'
    shutil.copy2(helper_source, source / helper_source.name)
    env = dict(os.environ, JAVA_HOME=runtime['java_home'])
    env['PATH'] = str(Path(runtime['java_home']) / 'bin') + os.pathsep + env['PATH']
    command = ['gradlew.bat','compileBudgetBenchmarkHelper','--offline','--no-daemon',
        '-I','tools/experimental/budget-helper.gradle','-PbudgetHelperDirectory=' + str(folder),
        '-PbudgetRuntime=' + runtime['artifacts']['runtime']['path']]
    with (folder / 'compile.log').open('x',encoding='utf-8') as stream:
        result = subprocess.run(command,cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT)
    if result.returncode:
        print((folder / 'compile.log').read_text(errors='replace')[-10000:])
        raise SystemExit(result.returncode)
    classes = {p.relative_to(folder / 'classes').as_posix():p.read_bytes() for p in (folder / 'classes').rglob('*.class')}
    output = folder / 'wwpg-benchmark-0.1.0-native-stages.jar'
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(output,'w') as new:
        for item in old.infolist():
            new.writestr(item, classes.pop(item.filename) if item.filename in classes else old.read(item))
        for name,data in sorted(classes.items()): new.writestr(name,data)
    with zipfile.ZipFile(original) as old, zipfile.ZipFile(output) as new:
        changed = sorted(n for n in old.namelist() if old.read(n) != new.read(n))
        added = sorted(set(new.namelist()) - set(old.namelist()))
        assert set(changed) == {package + '/' + n for n in ('NativeWork.class','NativeWork$Frame.class','mixin/NativeWorkMixin.class')}
        assert added == [package + '/NativeStages.class']
    files = list((folder / 'src').rglob('*.java')) + [helper_source, Path(__file__), ROOT / 'tools/experimental/budget-helper.gradle']
    sources = parent['sources'] + [dict(path=p.relative_to(ROOT).as_posix(),sha256=sha(p)) for p in sorted(files)]
    write_new(folder / 'inputs.json', dict(schema=1,helper=dict(path=str(output),sha256=sha(output)),
        base_helper=parent['helper'],parent_inputs=args.base_helper_inputs,runtime=runtime['artifacts']['runtime'],
        sources=sources,process_affinity_supported=parent['process_affinity_supported'],
        server_subset_supported=parent.get('server_subset_supported',False),native_work_supported=True,native_stages_supported=True,
        changed_entries=changed,added_entries=added,command=command))
    print(json.dumps(dict(inputs=str(folder / 'inputs.json'),helper_sha256=sha(output),changed_entries=changed,added_entries=added)))


if __name__ == '__main__':
    main()
