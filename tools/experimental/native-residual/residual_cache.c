#include "residual_cache.h"
#include "row_layout.h"
#include <immintrin.h>
#include <stdlib.h>

#ifndef WWPG_RESIDUAL_ENABLE
#define WWPG_RESIDUAL_ENABLE 1
#endif

void wwpg_residual_release(solver_t *solver) {
    wwpg_row_plan *plan=solver->m_residualCache;
    if(plan) {
        wwpg_tiles_free(plan);
        wwpg_row_free(plan);
        free(plan);
    }
    solver->m_residualCache=NULL;
    solver->m_residualDirty=0;
}

/* Only the solver's command-buffer, clear and resize paths write its private A.
 * Value dirtiness survives logical-matrix preparation and factorization.
 * Insertions increase nnz; clear/resize release the plan even if nnz is reused. */
int wwpg_residual_apply(solver_t *solver,double *x,double *y) {
    SuperMatrix *matrix=sparsematrix_supermatrix(&solver->m_A);
#if WWPG_RESIDUAL_ENABLE
    if(solver->m_size>=64) {
        wwpg_row_plan *plan=solver->m_residualCache;
        NCformat *store=matrix->Store;
        if(!plan || plan->n!=matrix->nrow || plan->nnz!=store->nnz || solver->m_residualDirty) {
            unsigned csr=_mm_getcsr();
            if(!plan || plan->n!=matrix->nrow || plan->nnz!=store->nnz) {
                wwpg_residual_release(solver);
                plan=calloc(1,sizeof(*plan));
                solver->m_residualCache=plan;
                if(plan && (!wwpg_row_build(plan,matrix) || !wwpg_tiles_build(plan,matrix))) {
                    wwpg_residual_release(solver); plan=NULL;
                }
            } else {
                wwpg_tiles_refresh(plan,matrix);
            }
            solver->m_residualDirty=0;
            _mm_setcsr(csr);
        }
        if(plan) return wwpg_row_tiled(plan,1.,matrix,x,-1.,y);
    }
#endif
    char trans='N';
    return sp_dgemv(&trans,1.,matrix,x,1,-1.,y,1);
}
