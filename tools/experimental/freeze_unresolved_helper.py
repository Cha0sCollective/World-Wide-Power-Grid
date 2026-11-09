"""Freeze a read-only saved-part inspector around the existing loading correctness helper."""
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
    args = parser.parse_args()
    runtime_inputs = json.loads((ROOT / args.inputs).read_text())
    parent = json.loads((ROOT / args.base_helper_inputs).read_text())
    runtime = runtime_inputs['artifacts']['runtime']
    base = Path(parent['helper']['path'])
    assert sha(base) == parent['helper']['sha256'] and sha(Path(runtime['path'])) == runtime['sha256']
    assert all(sha(ROOT / item['path']) == item['sha256'] for item in parent['sources'])
    folder = (ROOT / 'local/frozen' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local/frozen')
    folder.mkdir(parents=True, exist_ok=False)
    package = 'org/cha0scollective/wwpg/benchmark'
    source = folder / 'src' / package
    source.mkdir(parents=True)
    session = next(item for item in parent['sources'] if item['path'].endswith('/Session.java'))
    text = (ROOT / session['path']).read_text()
    old = 'if(tick%100==0){write("status.json",statusObject());'
    assert text.count(old) == 1
    text = text.replace(old, 'if(tick%100==0){UnresolvedTreeDiagnostic.capture(server,tick,phase);write("status.json",statusObject());')
    old = 'private void finish(boolean passed,String reason){if(ended)return;'
    assert text.count(old) == 1
    text = text.replace(old, old + 'UnresolvedTreeDiagnostic.capture(server,tick,"FINISH");')
    (source / 'Session.java').write_text(text, encoding='utf-8', newline='\n')
    gate = next(item for item in parent['sources'] if item['path'].endswith('/LoadingGate.java'))
    shutil.copy2(ROOT / gate['path'], source / 'LoadingGate.java')
    shutil.copy2(ROOT / 'tools/experimental/UnresolvedTreeDiagnostic.java', source / 'UnresolvedTreeDiagnostic.java')
    env = dict(os.environ)
    env['JAVA_HOME'] = runtime_inputs['java_home']
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env['PATH']
    command = ['gradlew.bat', 'compileBudgetBenchmarkHelper', '--offline', '--no-daemon', '-I', 'tools/experimental/budget-helper.gradle',
               '-PbudgetHelperDirectory=' + str(folder), '-PbudgetRuntime=' + runtime['path']]
    with (folder / 'compile.log').open('w') as log:
        completed = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    if completed.returncode:
        print((folder / 'compile.log').read_text(errors='replace')[-8000:])
        raise SystemExit(completed.returncode)
    helper = folder / 'wwpg-benchmark-unresolved.jar'
    replaced = package + '/Session.class'
    added = package + '/UnresolvedTreeDiagnostic.class'
    with zipfile.ZipFile(base) as before, zipfile.ZipFile(helper, 'w') as output:
        for item in before.infolist():
            output.writestr(item, (folder / 'classes' / replaced).read_bytes() if item.filename == replaced else before.read(item))
        output.write(folder / 'classes' / added, added)
    with zipfile.ZipFile(base) as before, zipfile.ZipFile(helper) as after:
        changed = [name for name in before.namelist() if before.read(name) != after.read(name)]
        assert changed == [replaced], changed
    sources = [dict(path=p.relative_to(ROOT).as_posix(), sha256=sha(p)) for p in sorted((folder / 'src').rglob('*.java'))]
    result = dict(schema=1, helper=dict(path=str(helper), sha256=sha(helper)), base_helper=parent['helper'], runtime=runtime,
        sources=sources, parent_inputs=args.base_helper_inputs, changed_parent_entries=changed,
        process_affinity_supported=parent['process_affinity_supported'], server_subset_supported=parent.get('server_subset_supported', False),
        inspection_only=True, scope='Read-only unresolved saved parts every 100 ticks and at completion; not a clean timing comparison')
    (folder / 'inputs.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(inputs=str(folder / 'inputs.json'), helper=result['helper'])))


if __name__ == '__main__':
    main()
