/* WWPG focused fault-injection checks. Never linked into the shipped library. */
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include "sparse.h"

static int fault, factor_calls, solve_calls;
int_t __real_dsnode_dfs(const int, const int, const int_t *, const int_t *, const int_t *, int_t *, int *, GlobalLU_t *);
int_t __wrap_dsnode_dfs(const int first, const int last, const int_t *rows,
    const int_t *begin, const int_t *end, int_t *prune, int *marker, GlobalLU_t *glu) {
    int_t result = __real_dsnode_dfs(first,last,rows,begin,end,prune,marker,glu);
    return fault == 4 ? glu->n + 700 : result;
}
void __real_dgstrf(superlu_options_t *, SuperMatrix *, int, int, int *, void *, int_t,
    int *, int *, SuperMatrix *, SuperMatrix *, GlobalLU_t *, SuperLUStat_t *, int_t *);
void __real_dgstrs(trans_t, SuperMatrix *, SuperMatrix *, const int *, const int *, SuperMatrix *, SuperLUStat_t *, int *);

void __wrap_dgstrf(superlu_options_t *o, SuperMatrix *a, int relax, int panel, int *tree,
    void *work, int_t length, int *pc, int *pr, SuperMatrix *l, SuperMatrix *u,
    GlobalLU_t *glu, SuperLUStat_t *stats, int_t *info) {
    factor_calls++;
    if(fault == 1) { *info = a->ncol + 100; return; }
    if(fault == 5) { *info = -7; return; }
    __real_dgstrf(o,a,relax,panel,tree,work,length,pc,pr,l,u,glu,stats,info);
    if(!*info && fault == 2) pr[0] = -1;
    if(!*info && fault == 3) pc[0] = pc[1];
}

void __wrap_dgstrs(trans_t t, SuperMatrix *l, SuperMatrix *u, const int *pc,
    const int *pr, SuperMatrix *b, SuperLUStat_t *s, int *info) {
    solve_calls++;
    if(fault == 6) { *info = -4; return; }
    __real_dgstrs(t,l,u,pc,pr,b,s,info);
}

static void fill(sparsematrix_t *a, int singular) {
    sparsematrix_resize(a,2); sparsematrix_zero(a);
    sparsematrix_set(a,0,0,2); sparsematrix_set(a,1,0,1);
    sparsematrix_set(a,0,1,singular ? 4 : 1); sparsematrix_set(a,1,1,singular ? 2 : 3);
}

static int solve(sparsematrix_t *a) {
    double rhs[] = {4,7};
    DNformat store = {2,rhs};
    SuperMatrix b = {SLU_DN,SLU_D,SLU_GE,2,1,&store};
    int status = sparsematrix_solve(a,&b);
    if(!status) { assert(fabs(rhs[0]-1)<1e-12); assert(fabs(rhs[1]-2)<1e-12); }
    else { assert(rhs[0]==4 && rhs[1]==7); }
    return status;
}

int main(void) {
    sparsematrix_t a; sparsematrix_init(&a);
    fill(&a,0); assert(!solve(&a)); assert(solve_calls==1);
    assert(!solve(&a)); assert(factor_calls==1 && solve_calls==2);
    int checks=2, before;
    for(int f=1;f<=5;f++) {
        fill(&a,0); fault=f; int before=solve_calls;
        assert(solve(&a)); assert(solve_calls==before);
        int factors=factor_calls; assert(solve(&a)); assert(factor_calls==factors);
        fault=0; fill(&a,0); assert(!solve(&a)); checks+=3;
    }
    fill(&a,0); assert(!solve(&a)); sparsematrix_same_pattern(&a,1);
    sparsematrix_add(&a,0,0,1); fault=4;
    before=solve_calls;
    assert(solve(&a)>0); assert(solve_calls==before); fault=0; checks++;
    fill(&a,1); before=solve_calls;
    assert(solve(&a)>0); assert(solve_calls==before); checks++;
    fill(&a,0); assert(!solve(&a)); checks++;
    // A solve routine's own error is latched, not silently accepted.
    fill(&a,0); fault=6; before=solve_calls;
    assert(solve(&a)==-4 && solve_calls==before+1);
    before=solve_calls; assert(solve(&a)==-4 && solve_calls==before);
    fault=0; fill(&a,0); assert(!solve(&a)); checks+=3;
    // Missing factors cannot reach SuperLU even if a stale flag says reusable.
    fill(&a,0); a.m_flags &= ~SPARSE_MATRIX_REFACTORIZE;
    before=solve_calls; assert(solve(&a)==-1003 && solve_calls==before); checks++;
    // Corruption between solves and before inverse-permutation reuse.
    for(int which=0;which<2;which++) for(int invalid=0;invalid<3;invalid++) {
        fill(&a,0); assert(!solve(&a));
        int *p=which ? a.m_permC : a.m_permR;
        p[0]=invalid==0 ? -1 : invalid==1 ? 2 : p[1];
        before=solve_calls;
        assert(solve(&a)); assert(solve_calls==before); checks++;
        fill(&a,0); assert(!solve(&a));
        sparsematrix_same_pattern(&a,1);
        sparsematrix_add(&a,0,0,1);
        p=which ? a.m_permC : a.m_permR;
        p[0]=invalid==0 ? -1 : invalid==1 ? 2 : p[1];
        int factors=factor_calls;
        assert(sparsematrix_factorize(&a)); assert(factor_calls==factors); checks++;
    }
    // Ordinary same-pattern refactorization still works, repeatedly.
    for(int i=0;i<100;i++) {
        fill(&a,0); assert(!solve(&a)); sparsematrix_same_pattern(&a,1);
        sparsematrix_add(&a,0,0,0); assert(!solve(&a));
    }
    sparsematrix_zero(&a); sparsematrix_resize(&a,0); sparsematrix_destroy(&a);
    printf("PASS %d native safety checks plus 100 valid refactor/cleanup cycles\n",checks+1);
    return 0;
}
