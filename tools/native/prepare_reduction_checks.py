"""Extract the released scalar and optimized routines for exact differential checks."""
from pathlib import Path
import sys

source, output = map(Path, sys.argv[1:])
text = source.read_text()
functions = text[text.index('static int wwpg_scalar_finite('):text.index('#include "reduction.h"')]
prelude = '#include <math.h>\n#define TRUE 1\n#define FALSE 0\ntypedef struct { double *m_residual; int m_size; } solver_t;\n'
wrappers = '''
__attribute__((noinline)) int ROLE_finite(const double *values, int size) { return solver_finite(values,size); }
__attribute__((noinline)) double ROLE_norm(const double *values, int size) {
    solver_t solver={.m_residual=(double *)values,.m_size=size};return solver_residual_norm(&solver);
}
'''
output.mkdir(parents=True, exist_ok=True)
for role in ('reference', 'candidate'):
    body = functions.replace('wwpg_scalar_finite','solver_finite').replace('wwpg_scalar_norm','solver_residual_norm') if role=='reference' else functions+'#include "reduction.h"\n'
    (output/(role+'.c')).write_text(prelude+body+wrappers.replace('ROLE',role))
