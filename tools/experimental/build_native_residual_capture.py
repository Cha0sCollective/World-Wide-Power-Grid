"""Build an isolated residual-input observer from the frozen reduction candidate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--candidate-build', required=True)
    args = parser.parse_args()
    base_path = ROOT / args.candidate_build
    base = json.loads(base_path.read_text())
    for item in base['inputs'] + list(base['artifacts'].values()):
        assert sha(Path(item['path'])) == item['sha256'], item['path']
    folder = (ROOT / 'local' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local')
    folder.mkdir(exist_ok=False)
    source = folder / 'src'
    shutil.copytree(base_path.parent / 'src', source)
    definitions = [ROOT / 'tools/experimental/native-residual' / name for name in ('capture.h','capture_checks.c')]
    for path in definitions: shutil.copy2(path, source / path.name)
    solver = source / 'solver.c'
    original = solver.read_text()
    old = 'sp_dgemv(&trans, 1.0, A, solver->m_state, 1, -1.0, solver->m_residual, 1);'
    assert original.count(old) == 2 and original.count('#include "reduction.h"') == 1
    changed = original.replace('#include "reduction.h"', '#include "reduction.h"\n#include "capture.h"')
    for stage in (0,1): changed = changed.replace(old, old.replace('sp_dgemv(', 'wwpg_capture_gemv(' + str(stage) + ', '), 1)
    restored = changed.replace('#include "capture.h"\n', '')
    for stage in (0,1): restored = restored.replace(old.replace('sp_dgemv(', 'wwpg_capture_gemv(' + str(stage) + ', '), old)
    assert restored == original
    solver.write_text(changed)
    commands = []
    for command in base['commands']:
        if any(value == str(base_path.parent / 'numeric_checks.exe') or value == '-S' for value in command): continue
        commands.append([value.replace(str(base_path.parent), str(folder)) for value in command])
    assert len(commands) == 3
    compiler = commands[0][0]
    working = Path(base['baseline_build']['path']).parent / 'build'
    libraries = [item['path'] for item in base['reused_static_libraries']]
    include = next(arg for arg in commands[0] if arg.startswith('@'))
    commands.append([compiler, include, '-O3','-DNDEBUG','-mavx2','-std=gnu11','-static-libgcc','-static',
                     '-o',str(folder / 'capture_checks.exe'),str(source / 'capture_checks.c'),*libraries])
    inputs = [base_path,Path(__file__),*definitions,*sorted(source.iterdir())]
    hashes = {str(p):sha(p) for p in inputs}
    env = dict(os.environ, PATH=str(Path(compiler).parent)+os.pathsep+os.environ['PATH'])
    for index, command in enumerate(commands):
        with (folder / ('build-' + str(index) + '.log')).open('x') as log:
            result = subprocess.run(command,cwd=working,env=env,stdout=log,stderr=subprocess.STDOUT)
        if result.returncode:
            print((folder / ('build-' + str(index) + '.log')).read_text(errors='replace'))
            raise SystemExit(result.returncode)
    assert all(sha(Path(p)) == value for p,value in hashes.items())
    record = dict(schema=1,diagnostic=True,diagnostic_kind='native-residual-capture',
        candidate_build=dict(path=str(base_path),sha256=sha(base_path)),original_candidate_source_restored=True,
        tracked_rows=14925,capture_slots=['outer-4096','outer-40960','fit'],
        artifacts={p.name:dict(path=str(p),sha256=sha(p)) for p in (folder / 'libpowergridNativeReduction.dll',folder / 'capture_checks.exe')},
        inputs=[dict(path=p,sha256=h) for p,h in hashes.items()],commands=commands,
        logs=[dict(path=str(p),sha256=sha(p)) for p in sorted(folder.glob('*.log'))])
    (folder / 'build.json').write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps(dict(built=str(folder / 'build.json'),artifacts=record['artifacts'])))


if __name__ == '__main__':
    main()
