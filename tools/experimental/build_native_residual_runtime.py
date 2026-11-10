"""Build matched residual-cache control/candidate DLLs without changing retained sources."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name',required=True)
    parser.add_argument('--candidate-build',required=True)
    parser.add_argument('--offline-build',required=True)
    args=parser.parse_args()
    base_path,offline_path=(ROOT/p for p in (args.candidate_build,args.offline_build))
    base,offline=(json.loads(p.read_text()) for p in (base_path,offline_path))
    for item in base['inputs']+list(base['artifacts'].values())+offline['inputs']+[offline['artifact'],offline['edge_checks']]:
        assert sha(Path(item['path']))==item['sha256'],item['path']
    folder=(ROOT/'local'/args.name).resolve()
    assert folder.is_relative_to(ROOT/'local')
    source=folder/'src'
    source.mkdir(parents=True,exist_ok=False)
    baseline_path=Path(base['baseline_build']['path'])
    native=baseline_path.parent/'sources/PowerGrid/native/src/powergridNative'
    for path in (native/'include').iterdir(): shutil.copy2(path,source/path.name)
    shutil.copy2(native/'c/mna.c',source/'mna.c')
    shutil.copy2(base_path.parent/'src/reduction.h',source/'reduction.h')
    definitions=['row_layout.h','row_layout.c','tile_layout.c','residual_cache.h','residual_cache.c','cache_checks.c']
    for name in definitions: shutil.copy2(ROOT/'tools/experimental/native-residual'/name,source/name)
    # Numerical kernels are the exact ones checked in the named offline build.
    for name in ('row_layout.h','row_layout.c','tile_layout.c'):
        assert sha(source/name)==sha(offline_path.parent/'src'/name)
    old_header=(source/'solver.h').read_text()
    fields='    void *m_residualCache;\n    int m_residualDirty;\n'
    assert old_header.count('} solver_t;')==1
    (source/'solver.h').write_text(old_header.replace('} solver_t;',fields+'} solver_t;'))
    original=(base_path.parent/'src/solver.c').read_text()
    changes=[
        ('#include "blas.h"','#include "blas.h"\n#include "residual_cache.h"'),
        ('static void solver_free_bufs(solver_t *solver) {','static void solver_free_bufs(solver_t *solver) {\n    wwpg_residual_release(solver);'),
        ('    sparsematrix_zero(&solver->m_A);','    wwpg_residual_release(solver);\n    sparsematrix_zero(&solver->m_A);'),
        ('    PG_TRACE("[Solver::processJacobianBuffer] entering");','    if(cmdCount > 0) solver->m_residualDirty = 1;\n    PG_TRACE("[Solver::processJacobianBuffer] entering");'),
        ('        SuperMatrix* A = sparsematrix_supermatrix(&solver->m_A);\n        sp_dgemv(&trans, 1.0, A, solver->m_state, 1, -1.0, solver->m_residual, 1);',
         '        wwpg_residual_apply(solver, solver->m_state, solver->m_residual); /* outer */'),
        ('                sp_dgemv(&trans, 1.0, A, solver->m_state, 1, -1.0, solver->m_residual, 1);',
         '                wwpg_residual_apply(solver, solver->m_state, solver->m_residual); /* fitting */'),
        ('        sparsematrix_same_pattern(&solver->m_A, 1);',
         '        sparsematrix_same_pattern(&solver->m_A, solver->m_A.m_L.Store != NULL && solver->m_A.m_U.Store != NULL);')]
    changed=original
    for old,new in changes:
        assert changed.count(old)==1,old
        changed=changed.replace(old,new)
    restored=changed
    for old,new in reversed(changes): restored=restored.replace(new,old)
    assert restored==original and (source/'solver.h').read_text().replace(fields,'')==old_header
    (source/'solver.c').write_text(changed)
    shutil.copy2(__file__,folder/'build-script.py')
    old_build=baseline_path.parent/'build'
    target=old_build/'CMakeFiles/powergridNative.dir'
    compiler=Path(base['commands'][0][0])
    include='@'+str(target/'includes_C.rsp')
    options=['-O3','-DNDEBUG','-mavx2','-std=gnu11','-ffp-contract=off']
    env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ['PATH'])
    old_objects=[old_build/p for p in shlex.split((target/'objects1.rsp').read_text())]
    libraries=[str(old_build/p) if not p.startswith('-') else p for p in shlex.split((target/'linklibs.rsp').read_text())]
    inputs=[base_path,offline_path,baseline_path,folder/'build-script.py',*sorted(source.iterdir()),*old_objects,
        *[Path(p) for p in libraries if not p.startswith('-')],target/'includes_C.rsp',target/'link.txt']
    hashes={str(p):sha(p) for p in inputs}
    commands=[]
    def run(command,label):
        commands.append(command)
        with (folder/(label+'.log')).open('x') as log:
            result=subprocess.run(command,cwd=old_build,env=env,stdout=log,stderr=subprocess.STDOUT)
        if result.returncode:
            print((folder/(label+'.log')).read_text(errors='replace')); raise SystemExit(result.returncode)
    objects={}
    for name in ('solver','mna','row_layout','tile_layout'):
        obj=folder/(name+'.obj'); objects[name]=obj
        run([str(compiler),include,'-DpowergridNative_EXPORTS',*options,'-c',str(source/(name+'.c')),'-o',str(obj)],'compile-'+name)
    artifacts={}
    for role,enabled in (('control',0),('candidate',1)):
        cache=folder/(role+'-cache.obj')
        run([str(compiler),include,*options,'-DWWPG_RESIDUAL_ENABLE='+str(enabled),'-c',str(source/'residual_cache.c'),'-o',str(cache)],'compile-'+role)
        archive=folder/(role+'.a')
        combined=[objects['solver'] if p.name=='solver.c.obj' else objects['mna'] if p.name=='mna.c.obj' else p for p in old_objects]
        combined += [objects['row_layout'],objects['tile_layout'],cache]
        run([str(compiler.with_name('x86_64-w64-mingw32-ar')),'qc',str(archive),*map(str,combined)],'archive-'+role)
        dll=folder/('libpowergridNativeResidual-'+role+'.dll')
        link=shlex.split((target/'link.txt').read_text().splitlines()[-1])
        link[link.index('-o')+1]=str(dll)
        adjusted=[]
        for value in link:
            if value.startswith('-Wl,--out-implib,'): adjusted.append('-Wl,--out-implib,'+str(folder/(role+'.dll.a')))
            elif value=='CMakeFiles/powergridNative.dir/objects.a': adjusted.append(str(archive))
            elif value=='@CMakeFiles/powergridNative.dir/linklibs.rsp': adjusted.extend(libraries)
            else: adjusted.append(value)
        run(adjusted,'link-'+role)
        executable=folder/('cache-checks-'+role+'.exe')
        sparse=next(p for p in old_objects if p.name=='sparse.c.obj')
        run([str(compiler),include,*options,'-DWWPG_RESIDUAL_ENABLE='+str(enabled),'-static-libgcc','-static',
            '-Wl,--wrap=malloc','-Wl,--wrap=calloc','-Wl,--wrap=free','-o',str(executable),str(source/'cache_checks.c'),
            str(objects['solver']),str(objects['row_layout']),str(objects['tile_layout']),str(cache),str(sparse),*libraries],
            'checks-'+role)
        artifacts[role]=dict(dll=dict(path=str(dll),sha256=sha(dll)),checks=dict(path=str(executable),sha256=sha(executable)))
    assert all(sha(Path(p))==h for p,h in hashes.items())
    proof=dict(schema=1,diagnostic=False,experiment='native-residual-cache',baseline_build=dict(path=str(baseline_path),sha256=sha(baseline_path)),
        reduction_build=dict(path=str(base_path),sha256=sha(base_path)),offline_build=dict(path=str(offline_path),sha256=sha(offline_path)),
        original_solver_restored=True,original_header_restored=True,unchanged_native_objects=3,unchanged_sparse_layout=True,
        identical_checked_row_kernels=True,artifacts=artifacts,commands=commands,
        inputs=[dict(path=p,sha256=h) for p,h in hashes.items()],logs=[dict(path=str(p),sha256=sha(p)) for p in sorted(folder.glob('*.log'))],
        note='Both variants refresh logical A at each residual call; only the candidate enables the per-solver row cache. Original callbacks, guards and stopping criteria remain.')
    (folder/'build.json').write_text(json.dumps(proof,indent=2)+'\n')
    print(json.dumps(dict(built=str(folder/'build.json'),artifacts=artifacts)))


if __name__=='__main__': main()
