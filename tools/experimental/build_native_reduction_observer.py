"""Add isolated atomic path counts to an existing native reduction candidate."""
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
    parser.add_argument('--name',required=True)
    parser.add_argument('--candidate-build',required=True)
    args = parser.parse_args()
    candidate_path = ROOT / args.candidate_build
    candidate = json.loads(candidate_path.read_text())
    for item in candidate['inputs'] + list(candidate['artifacts'].values()):
        assert sha(Path(item['path'])) == item['sha256'],item['path']
    folder = (ROOT / 'local' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local')
    folder.mkdir(exist_ok=False)
    source = folder / 'src'
    shutil.copytree(candidate_path.parent / 'src',source)
    header = (source / 'reduction.h').read_text()
    additions = '''
#include <stdatomic.h>
#include <stdio.h>
static _Atomic uint64_t wwpg_path_counts[4];
static void wwpg_count_path(int size, int path) {
    if(size == 14925) atomic_fetch_add_explicit(&wwpg_path_counts[path],1,memory_order_relaxed);
}
static void wwpg_report_reduction_paths(void) {
    printf("WWPG_NATIVE_REDUCTION_PATHS rows=14925 finiteFast=%llu finiteFallback=%llu normFast=%llu normFallback=%llu\\n",
        (unsigned long long)atomic_load_explicit(&wwpg_path_counts[0],memory_order_relaxed),
        (unsigned long long)atomic_load_explicit(&wwpg_path_counts[1],memory_order_relaxed),
        (unsigned long long)atomic_load_explicit(&wwpg_path_counts[2],memory_order_relaxed),
        (unsigned long long)atomic_load_explicit(&wwpg_path_counts[3],memory_order_relaxed));
    fflush(stdout);
}
'''
    changes = []
    def replace(old,new,count):
        nonlocal header
        assert header.count(old) == count,(old,header.count(old))
        header = header.replace(old,new)
        changes.append((old,new,count))
    replace('#include <string.h>','#include <string.h>\n' + additions,1)
    # Return expressions still invoke the exact same original scalar function.
    # Small vectors are excluded by the size filter; the main yard has 14925 rows.
    replace('return wwpg_scalar_finite(values, size);','return (wwpg_count_path(size, 1), wwpg_scalar_finite(values, size));',3)
    replace('    return 1;','    wwpg_count_path(size, 0);\n    return 1;',1)
    replace('return wwpg_scalar_norm(solver);','return (wwpg_count_path(size, 3), wwpg_scalar_norm(solver));',3)
    replace('    return value;','    wwpg_count_path(size, 2);\n    return value;',1)
    restored = header
    for old,new,count in reversed(changes): restored = restored.replace(new,old)
    assert restored == (candidate_path.parent / 'src/reduction.h').read_text()
    (source / 'reduction.h').write_text(header)
    solver = (source / 'solver.c').read_text()
    original = solver
    anchor = '#include "blas.h"'
    declaration = anchor + '\nstatic void wwpg_report_reduction_paths(void);'
    assert solver.count(anchor) == 1
    solver = solver.replace(anchor,declaration)
    anchor = 'void solver_destroy(solver_t *solver) {'
    insertion = anchor + '\n    wwpg_report_reduction_paths();'
    assert solver.count(anchor) == 1
    solver = solver.replace(anchor,insertion)
    assert solver.replace(insertion,anchor).replace(declaration,'#include "blas.h"') == original
    (source / 'solver.c').write_text(solver)
    old_folder = candidate_path.parent
    commands = []
    for command in candidate['commands']:
        if any(str(old_folder / 'numeric_checks.exe') == v or v == '-S' for v in command): continue
        commands.append([v.replace(str(old_folder),str(folder)) if str(old_folder) in v else v for v in command])
    assert len(commands) == 3
    compiler = Path(commands[0][0])
    env = dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ['PATH'])
    baseline_build = Path(candidate['baseline_build']['path']).parent / 'build'
    inputs = [candidate_path,Path(__file__)] + sorted(source.iterdir())
    hashes = {str(p):sha(p) for p in inputs}
    for index,command in enumerate(commands):
        with (folder / ('build-' + str(index) + '.log')).open('x') as log:
            result = subprocess.run(command,cwd=baseline_build,env=env,stdout=log,stderr=subprocess.STDOUT)
        if result.returncode:
            print((folder / ('build-' + str(index) + '.log')).read_text(errors='replace'))
            raise SystemExit(result.returncode)
    assert all(sha(Path(p)) == value for p,value in hashes.items())
    dll = folder / 'libpowergridNativeReduction.dll'
    record = dict(schema=1,diagnostic=True,diagnostic_kind='native-reduction-counts',
        candidate_build=dict(path=str(candidate_path),sha256=sha(candidate_path)),
        original_candidate_source_restored=True,tracked_rows=14925,
        artifact=dict(path=str(dll),sha256=sha(dll)),commands=commands,
        inputs=[dict(path=p,sha256=h) for p,h in hashes.items()],
        logs=[dict(path=str(p),sha256=sha(p)) for p in sorted(folder.glob('*.log'))],
        note='Atomic path counts on the candidate; printed when a native solver is destroyed. Whole-process lifetime observations, not stable-only timing.')
    (folder / 'build.json').write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps(dict(built=str(folder / 'build.json'),native=record['artifact'])))


if __name__ == '__main__':
    main()
