"""Build a reduction-only candidate using immutable objects/libraries from the native baseline."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', required=True)
    parser.add_argument('--baseline-build', required=True)
    parser.add_argument('--toolchain', required=True)
    args = parser.parse_args()
    baseline_path, toolchain_path = (ROOT / v for v in (args.baseline_build, args.toolchain))
    baseline, toolchain = (json.loads(p.read_text()) for p in (baseline_path, toolchain_path))
    compiler = Path(toolchain['compiler'])
    assert compiler.exists()
    for item in baseline['inputs'] + list(baseline['artifacts'].values()):
        assert sha(Path(item['path'])) == item['sha256'], item['path']
    folder = (ROOT / 'local' / args.name).resolve()
    assert folder.is_relative_to(ROOT / 'local')
    folder.mkdir(exist_ok=False)
    source = folder / 'src'
    source.mkdir()
    old_build = baseline_path.parent / 'build'
    original_path = baseline_path.parent / 'sources/PowerGrid/native/src/powergridNative/c/solver.c'
    original = original_path.read_text()
    start, stop = original.index('static int solver_finite('), original.index('static jobject solver_failed(')
    functions = original[start:stop]
    assert functions.count('static int solver_finite(') == functions.count('static double solver_residual_norm(') == 1
    renamed = functions.replace('static int solver_finite(', 'static int wwpg_scalar_finite(').replace(
        'static double solver_residual_norm(', 'static double wwpg_scalar_norm(')
    replacement = renamed + '#include "reduction.h"\n\n'
    candidate = original[:start] + replacement + original[stop:]
    assert candidate.replace(replacement,functions,1) == original
    (source / 'solver.c').write_text(candidate)
    definition = ROOT / 'tools/experimental/native-reduction/reduction.h'
    tests = ROOT / 'tools/experimental/native-reduction/numeric_checks.c'
    shutil.copy2(definition, source / 'reduction.h')
    shutil.copy2(tests, source / 'numeric_checks.c')
    prelude = '#include <math.h>\n#define TRUE 1\n#define FALSE 0\ntypedef struct { double *m_residual; int m_size; } solver_t;\n'
    wrappers = '''
__attribute__((noinline,noipa)) int ROLE_finite(const double *values, int size) {
    return solver_finite(values,size);
}
__attribute__((noinline,noipa)) double ROLE_norm(const double *values, int size) {
    solver_t solver = { .m_residual=(double *)values, .m_size=size };
    return solver_residual_norm(&solver);
}
'''
    for role, body in (('reference',functions),('candidate',replacement)):
        (source / (role + '.c')).write_text(prelude + body + wrappers.replace('ROLE',role))
    target = old_build / 'CMakeFiles/powergridNative.dir'
    flags = (target / 'flags.make').read_text()
    assert 'C_FLAGS = -O3 -DNDEBUG -mavx2 -std=gnu11' in flags
    old_objects = [old_build / p for p in shlex.split((target / 'objects1.rsp').read_text())]
    old_solver = [p for p in old_objects if p.name == 'solver.c.obj']
    assert len(old_solver) == 1
    libraries = shlex.split((target / 'linklibs.rsp').read_text())
    archives = [old_build / p for p in libraries if not p.startswith('-')]
    libraries = [p if p.startswith('-') else str(old_build / p) for p in libraries]
    inputs = [baseline_path,toolchain_path,compiler,original_path,definition,tests,Path(__file__)]
    inputs += old_objects + archives + [target / n for n in ('flags.make','includes_C.rsp','objects1.rsp','linklibs.rsp','link.txt')]
    inputs += sorted(source.iterdir())
    hashes = {str(p):sha(p) for p in inputs}
    commands = []
    env = dict(os.environ, PATH=str(compiler.parent) + os.pathsep + os.environ['PATH'])
    def run(command, name):
        commands.append(command)
        with (folder / (name + '.log')).open('x') as log:
            result = subprocess.run(command, cwd=old_build, env=env, stdout=log, stderr=subprocess.STDOUT)
        if result.returncode:
            print((folder / (name + '.log')).read_text(errors='replace'))
            raise SystemExit(result.returncode)
    options = ['-O3','-DNDEBUG','-mavx2','-std=gnu11']
    obj = folder / 'solver.c.obj'
    run([str(compiler),'-DpowergridNative_EXPORTS','@' + str(target / 'includes_C.rsp'),*options,
        '-o',str(obj),'-c',str(source / 'solver.c')], 'compile-solver')
    new_archive = folder / 'objects.a'
    objects = [obj if p == old_solver[0] else p for p in old_objects]
    run([str(compiler.with_name('x86_64-w64-mingw32-ar')),'qc',str(new_archive),*[str(p) for p in objects]], 'archive')
    dll = folder / 'libpowergridNativeReduction.dll'
    link = shlex.split((target / 'link.txt').read_text().splitlines()[-1])
    assert link[0] == str(compiler)
    link[link.index('-o')+1] = str(dll)
    adjusted = []
    for value in link:
        if value.startswith('-Wl,--out-implib,'):
            adjusted.append('-Wl,--out-implib,' + str(folder / 'libpowergridNativeReduction.dll.a'))
        elif value == 'CMakeFiles/powergridNative.dir/objects.a': adjusted.append(str(new_archive))
        elif value == '@CMakeFiles/powergridNative.dir/linklibs.rsp': adjusted.extend(libraries)
        else: adjusted.append(value)
    run(adjusted,'link')
    executable = folder / 'numeric_checks.exe'
    run([str(compiler),*options,'-static-libgcc','-static','-o',str(executable),
        *[str(source / (n + '.c')) for n in ('reference','candidate','numeric_checks')]],'compile-checks')
    for role in ('reference','candidate'):
        run([str(compiler),*options,'-S','-o',str(folder / (role + '.s')),str(source / (role + '.c'))],role + '-assembly')
    assert all(sha(Path(p)) == expected for p,expected in hashes.items())
    record = dict(schema=1, platform='windows', candidate='exact-native-reduction-01', diagnostic=False,
        baseline_build=dict(path=str(baseline_path),sha256=sha(baseline_path)),
        original_source_restored=True, unchanged_native_objects=4, same_compiler=True,
        reused_static_libraries=[dict(path=str(p),sha256=sha(p)) for p in archives],
        artifacts={p.name:dict(path=str(p),sha256=sha(p)) for p in (dll,executable)},
        inputs=[dict(path=p,sha256=h) for p,h in hashes.items()], commands=commands,
        logs=[dict(path=str(p),sha256=sha(p)) for p in sorted(folder.glob('*.log'))],
        assembly=[dict(path=str(p),sha256=sha(p)) for p in sorted(folder.glob('*.s'))],
        source_change='Original scalar finite/norm bodies retained; vector integer fast path for normal finite values and zero only.',
        checks_run=False, gameplay_checks_run=False)
    (folder / 'build.json').write_text(json.dumps(record,indent=2) + '\n')
    print(json.dumps(dict(built=str(folder / 'build.json'), artifacts=record['artifacts'])))


if __name__ == '__main__':
    main()
