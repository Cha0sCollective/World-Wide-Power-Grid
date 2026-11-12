/* Focused allocation-failure subprocesses; never linked into the gameplay library. */
#include <assert.h>
#include <limits.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "sparse.h"
#include "allocation_faults.h"
static void fill(sparsematrix_t *a){sparsematrix_resize(a,2);sparsematrix_zero(a);sparsematrix_set(a,0,0,2);sparsematrix_set(a,1,0,1);sparsematrix_set(a,0,1,1);sparsematrix_set(a,1,1,3);}
static void solve(sparsematrix_t *a){double values[]={4,7};DNformat store={2,values};SuperMatrix b={SLU_DN,SLU_D,SLU_GE,2,1,&store};assert(!sparsematrix_solve(a,&b));assert(fabs(values[0]-1)<1e-12&&fabs(values[1]-2)<1e-12);}
int main(int argc,char **argv){
    assert(argc==3);int nth=atoi(argv[2]);sparsematrix_t a;
    if(!strcmp(argv[1],"init")){
        arm(nth);sparsematrix_init(&a);disarm();assert(a.m_factorInfo!=0);sparsematrix_destroy(&a);
    }else if(!strcmp(argv[1],"resize")){
        sparsematrix_init(&a);fill(&a);solve(&a);int *columns=a.m_columns;
        arm(nth);sparsematrix_resize(&a,4);disarm();assert(a.m_factorInfo!=0);
        assert(a.m_size==2&&a.m_columns==columns&&sparsematrix_get(&a,0,0)==2);
        fill(&a);solve(&a);sparsematrix_destroy(&a);
    }else if(!strcmp(argv[1],"growth")){
        sparsematrix_init(&a);sparsematrix_resize(&a,35);
        for(int i=0;i<1024;++i)sparsematrix_set(&a,i%35,i/35,i+1);
        int *rows=a.m_rowIndices;double *values=a.m_elements;
        arm(nth);sparsematrix_set(&a,1024%35,1024/35,77);disarm();assert(a.m_factorInfo!=0);
        assert(a.m_elementCount==1024&&a.m_rowIndices==rows&&a.m_elements==values&&sparsematrix_get(&a,0,0)==1);
        fill(&a);solve(&a);sparsematrix_destroy(&a);
    }else if(!strcmp(argv[1],"bounds")){
        sparsematrix_init(&a);fill(&a);sparsematrix_resize(&a,nth?INT_MAX:-1);assert(a.m_factorInfo!=0&&a.m_size==2);
        fill(&a);solve(&a);sparsematrix_destroy(&a);
    }else abort();
    assert(live==0);puts("PASS controlled allocation failure, cleanup and fresh solve");return 0;
}
