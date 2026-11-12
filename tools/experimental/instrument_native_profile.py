"""Insert timed observation scopes and prove removal restores the guarded source."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[2]


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    args = parser.parse_args()
    folder = (ROOT / args.directory).resolve()
    assert folder.is_relative_to(ROOT / 'local')
    prepared = json.loads((folder / 'prepared.json').read_text())
    baseline = folder / 'sources/PowerGrid/native/src/powergridNative'
    profile = folder / 'sources/PowerGridProfile/native/src/powergridNative'
    output = folder / 'instrumentation.json'
    assert not output.exists()
    patches = {}

    def transform(relative, replacements):
        original = (baseline / relative).read_text()
        target = profile / relative
        assert target.read_text() == original
        text = original
        edits = []
        for old, new, count in replacements:
            assert text.count(old) == count, (relative, old, text.count(old), count)
            text = text.replace(old, new)
            edits.append((old, new, count))
        restored = text
        for old, new, count in reversed(edits):
            assert restored.count(new) == count
            restored = restored.replace(new, old)
        assert restored == original
        target.write_text(text, encoding='utf-8', newline='\n')
        patches[relative] = dict(before_normalized_sha256=digest(original), after_normalized_sha256=digest(text),
            original_source_restored=True, replaced_sites=sum(e[2] for e in edits), replacements=edits)

    transform('include/sparse.h', [('#include <slu_ddefs.h>', '#include <slu_ddefs.h>\n#include "native_profile.h"', 1),
        ('    char m_flags;', '    char m_flags;\n    uint64_t *m_profile;', 1)])
    transform('include/solver.h', [('    char m_converged;',
        '    char m_converged;\n    uint64_t m_profile[WWPG_PROFILE_WORDS];', 1)])
    source = (baseline / 'c/solver.c').read_text()
    start = source.index('jobject solver_single_tick(')
    stop = source.index('\nvoid solver_set_precision', start)
    original_body = source[start:stop]
    body = original_body
    body_edits = []
    def replace(old, new, count):
        nonlocal body
        assert body.count(old) == count, (old, body.count(old), count)
        body = body.replace(old, new)
        body_edits.append((old,new,count))
    def value(expression, stage, kind, count):
        replace(expression, f'WWPG_PROFILE_VALUE(solver->m_profile, WWPG_{stage}, {kind}, {expression})', count)
    def void(expression, stage, count):
        replace(expression, f'WWPG_PROFILE_VOID(solver->m_profile, WWPG_{stage}, {expression})', count)
    replace('jobject solver_single_tick(', 'static jobject solver_single_tick_body(', 1)
    for name, count in (('m_state',2),('m_rhs',1),('m_b',1)):
        value(f'solver_finite(solver->{name}, solver->m_size)', 'FINITE', 'int', count)
    value('solver_residual_norm(solver)', 'RESIDUAL_NORM', 'double', 2)
    value('sparsematrix_solve(&solver->m_A, &solver->m_B)', 'SPARSE_SOLVE', 'int', 1)
    value('(*solver->m_env)->CallIntMethod(solver->m_env, mnaObj, solver->m_iterHookMethod, i, solver->m_stateBuffer)', 'JAVA_CALLBACK', 'jint', 2)
    void('(*solver->m_env)->CallVoidMethod(solver->m_env, mnaObj, solver->m_residualAddMethod, solver->m_bBuffer)', 'JAVA_CALLBACK', 2)
    void('sp_dgemv(&trans, 1.0, A, solver->m_state, 1, -1.0, solver->m_residual, 1)', 'RESIDUAL_MULTIPLY', 2)
    void('solver_process_jacobian_buffer(solver, cmdCount)', 'JACOBIAN', 3)
    void('solver_process_rhs_buffer(solver)', 'RHS', 1)
    copies = re.findall(r'(?m)^\s*(memcpy\([^;]+\));', original_body)
    assert len(copies) == 6
    for call in dict.fromkeys(copies): void(call, 'COPY', copies.count(call))
    deltas = re.findall(r'(?m)^\s*(daxpy_\([^;]+\));', original_body)
    assert len(deltas) == 2
    for call in deltas: void(call, 'DELTA', 1)
    restored = body
    for old,new,count in reversed(body_edits):
        assert restored.count(new) == count
        restored = restored.replace(new,old)
    assert restored == original_body
    wrapper = (ROOT / 'tools/experimental/native-stage-profile/solver_wrapper.c').read_text()
    transform('c/solver.c', [(original_body, body + '\n' + wrapper, 1)])
    patches['c/solver.c']['body_replacements'] = body_edits
    sparse = (baseline / 'c/sparse.c').read_text()
    replacements = []
    for call, stage, kind in (
        ('permutation_valid(matrix, matrix->m_permR)', 'PERMUTATION', 'int'),
        ('permutation_valid(matrix, matrix->m_permC)', 'PERMUTATION', 'int'),
        ('sparsematrix_factorize(matrix)', 'FACTORIZATION', 'int')):
        replacements.append((call, f'WWPG_PROFILE_VALUE(matrix->m_profile, WWPG_{stage}, {kind}, {call})', sparse.count(call)))
    call = 'dgstrs(NOTRANS, &matrix->m_L, &matrix->m_U, matrix->m_permC, matrix->m_permR, B, &matrix->m_stats, &info)'
    replacements.append((call, f'WWPG_PROFILE_VOID(matrix->m_profile, WWPG_TRIANGULAR_SOLVE, {call})', 1))
    transform('c/sparse.c', replacements)
    for name, subdir in (('native_profile.h','include'),('native_profile.c','c')):
        shutil.copy2(ROOT / 'tools/experimental/native-stage-profile' / name, profile / subdir / name)
    data = dict(schema=1, all_original_source_restored=True, changes=patches,
        auxiliary_buffer_unchanged=True, snapshot_api='org.cha0scollective.wwpg.benchmark.NativeStages.snapshot(long)',
        source_lock=prepared['source_lock'],
        sources=[dict(path=str(p), sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in
            [Path(__file__)] + sorted((ROOT / 'tools/experimental/native-stage-profile').glob('*'))])
    output.write_text(json.dumps(data, indent=2) + '\n')
    print(json.dumps(dict(instrumented=str(output), source_files=len(patches), original_source_restored=True)))


if __name__ == '__main__':
    main()
