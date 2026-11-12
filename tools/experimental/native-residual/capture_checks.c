#include <slu_ddefs.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include "capture.h"

int main(void) {
    const int n = 14925;
    unsigned original = _mm_getcsr();
    int *columns = malloc((n+1)*sizeof(int)), *rows = malloc(n*sizeof(int));
    double *values = malloc(n*sizeof(double)), *x = malloc(n*sizeof(double));
    double *y = malloc(n*sizeof(double)), *reference = malloc(n*sizeof(double));
    if(!columns || !rows || !values || !x || !y || !reference) return 2;
    for(int i=0; i<n; ++i) { columns[i]=rows[i]=i; values[i]=2; x[i]=i+1; }
    columns[n]=n;
    NCformat store = {.nnz=n, .nzval=values, .rowind=rows, .colptr=columns};
    SuperMatrix matrix = {.Stype=SLU_NC, .Dtype=SLU_D, .Mtype=SLU_GE, .nrow=n, .ncol=n, .Store=&store};
    char trans='N';
    for(int slot=0; slot<3; ++slot) {
        for(int i=0; i<n; ++i) y[i]=reference[i]=(i+1)*3;
        if(slot == 1) { x[12]=0x1p-1074; y[17]=reference[17]=-0.0; }
        if(slot == 2) {
            uint64_t signaling = UINT64_C(0x7ff0000000000001);
            memcpy(values+23, &signaling, 8);
            x[27]=INFINITY;
        }
        unsigned control = (original | 0x1f80u) & ~0x8040u;
        control = (control & ~0x3fu) | (slot == 0 ? 0x3fu : 0);
        _mm_setcsr(control);
        int expected = sp_dgemv(&trans, 1.0, &matrix, x, 1, -1.0, reference, 1);
        unsigned expected_flags = _mm_getcsr();
        atomic_store(&wwpg_outer_residual_calls, slot == 0 ? 4095 : slot == 1 ? 40959 : 40960);
        _mm_setcsr(control);
        int actual = wwpg_capture_gemv(slot == 2, &trans, 1.0, &matrix, x, 1, -1.0, y, 1);
        unsigned actual_flags = _mm_getcsr();
        if(actual != expected || actual_flags != expected_flags || memcmp(y, reference, n*sizeof(double))) {
            fprintf(stderr, "Capture changed result or MXCSR in slot %d\n", slot);
            _mm_setcsr(original);
            return 3;
        }
    }
    _mm_setcsr(original);
    free(columns); free(rows); free(values); free(x); free(y); free(reference);
    puts("PASS 3 capture paths preserve exact output and MXCSR");
    return 0;
}
