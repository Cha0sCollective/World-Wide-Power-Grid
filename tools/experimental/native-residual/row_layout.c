/* Offline prototype only. Refresh/rebuild are explicit caller obligations. */
#include "row_layout.h"
#include <immintrin.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

static int finite_values(const double *values, int size) {
    const __m256i exponent = _mm256_set1_epi64x(INT64_C(0x7ff0000000000000));
    __m256i bad = _mm256_setzero_si256();
    int i=0;
    for(; i<=size-4; i+=4) {
        __m256i bits = _mm256_loadu_si256((const __m256i *)(values+i));
        bad = _mm256_or_si256(bad, _mm256_cmpeq_epi64(_mm256_and_si256(bits,exponent),exponent));
    }
    if(_mm256_movemask_epi8(bad)) return 0;
    for(; i<size; ++i) {
        uint64_t bits; memcpy(&bits,values+i,8);
        if((bits & UINT64_C(0x7ff0000000000000)) == UINT64_C(0x7ff0000000000000)) return 0;
    }
    return 1;
}

void wwpg_row_free(wwpg_row_plan *plan) {
    free(plan->offsets); free(plan->columns); free(plan->entries);
    free(plan->packed); free(plan->scaled); free(plan->active);
    memset(plan,0,sizeof(*plan));
}

void wwpg_row_refresh(wwpg_row_plan *plan, SuperMatrix *matrix) {
    NCformat *store = matrix->Store;
    const double *values = store->nzval;
    for(int i=0; i<plan->nnz; ++i) plan->packed[i] = values[plan->entries[i]];
    plan->nonfinite = !finite_values(values,plan->nnz);
}

int wwpg_row_build(wwpg_row_plan *plan, SuperMatrix *matrix) {
    memset(plan,0,sizeof(*plan));
    NCformat *store = matrix->Store;
    plan->n=matrix->nrow; plan->nnz=store->nnz;
    int count=plan->nnz ? plan->nnz : 1;
    plan->offsets=calloc(plan->n+1,sizeof(int));
    plan->columns=malloc(count*sizeof(int)); plan->entries=malloc(count*sizeof(int));
    plan->packed=malloc(count*sizeof(double)); plan->scaled=malloc((plan->n+1)*sizeof(double));
    plan->active=malloc(plan->n+1);
    int *cursor=malloc((plan->n+1)*sizeof(int));
    if(!plan->offsets || !plan->columns || !plan->entries || !plan->packed || !plan->scaled || !plan->active || !cursor) {
        free(cursor); wwpg_row_free(plan); return 0;
    }
    for(int i=0;i<plan->nnz;++i) ++plan->offsets[store->rowind[i]+1];
    for(int i=0;i<plan->n;++i) plan->offsets[i+1]+=plan->offsets[i];
    memcpy(cursor,plan->offsets,(plan->n+1)*sizeof(int));
    for(int col=0;col<plan->n;++col) for(int i=store->colptr[col];i<store->colptr[col+1];++i) {
        int target=cursor[store->rowind[i]]++;
        plan->columns[target]=col; plan->entries[target]=i;
    }
    free(cursor);
    wwpg_row_refresh(plan,matrix);
    return 1;
}

static int apply(wwpg_row_plan *plan, double alpha, SuperMatrix *matrix, double *x, double beta, double *y, int packed) {
    NCformat *store=matrix->Store;
    uintptr_t xb=(uintptr_t)x, yb=(uintptr_t)y, bytes=(uintptr_t)plan->n*sizeof(double);
    if((_mm_getcsr() & 0x1f80u) != 0x1f80u || (xb < yb+bytes && yb < xb+bytes)
       || (packed ? plan->nonfinite : !finite_values(store->nzval,plan->nnz))
       || !finite_values(x,plan->n) || !finite_values(y,plan->n)) {
        char trans='N'; return sp_dgemv(&trans,alpha,matrix,x,1,beta,y,1);
    }
    if(beta != 1.) {
        if(beta == 0.) for(int i=0;i<plan->n;++i) y[i]=0.;
        else for(int i=0;i<plan->n;++i) y[i]=beta*y[i];
    }
    if(alpha == 0.) return 0;
    /* Keep the original comparison and scaling even for empty columns. */
    for(int col=0;col<plan->n;++col) {
        plan->active[col] = x[col] != 0.;
        if(plan->active[col]) plan->scaled[col]=alpha*x[col];
    }
    const double *values=store->nzval;
    for(int row=0;row<plan->n;++row) {
        double value=y[row];
        for(int i=plan->offsets[row];i<plan->offsets[row+1];++i) {
            int col=plan->columns[i];
            if(plan->active[col]) value += plan->scaled[col] * (packed ? plan->packed[i] : values[plan->entries[i]]);
        }
        y[row]=value;
    }
    return 0;
}

__attribute__((noinline,noipa)) int wwpg_row_indexed(wwpg_row_plan *p,double a,SuperMatrix *m,double *x,double b,double *y) {
    return apply(p,a,m,x,b,y,0);
}
__attribute__((noinline,noipa)) int wwpg_row_packed(wwpg_row_plan *p,double a,SuperMatrix *m,double *x,double b,double *y) {
    return apply(p,a,m,x,b,y,1);
}
