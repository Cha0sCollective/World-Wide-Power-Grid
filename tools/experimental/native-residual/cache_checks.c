#include <windows.h>
#include <immintrin.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "residual_cache.h"
#include "row_layout.h"

static unsigned original;
static int checks,cycles,failed_allocations;
static int tracking,fail_after=-1,allocation_calls,live;
static void *owned[64];
void *__real_malloc(size_t);
void *__real_calloc(size_t,size_t);
void __real_free(void *);
static void require(int okay,const char *message) {
    if(!okay) { _mm_setcsr(original); fprintf(stderr,"FAIL %s\n",message); exit(1); }
}
static int reject_allocation(void) {
    if(!tracking) return 0;
    ++allocation_calls;
    if(fail_after<0) return 0;
    if(fail_after--==0) { fail_after=-1; ++failed_allocations; return 1; }
    return 0;
}
static void *track(void *pointer) {
    if(tracking && pointer) {
        for(int i=0;i<64;++i) if(!owned[i]) { owned[i]=pointer; ++live; return pointer; }
        require(0,"allocation tracker capacity");
    }
    return pointer;
}
void *__wrap_malloc(size_t n) { return reject_allocation() ? NULL : track(__real_malloc(n)); }
void *__wrap_calloc(size_t n,size_t size) { return reject_allocation() ? NULL : track(__real_calloc(n,size)); }
void __wrap_free(void *pointer) {
    if(pointer) for(int i=0;i<64;++i) if(owned[i]==pointer) { owned[i]=NULL; --live; break; }
    __real_free(pointer);
}
static jobject JNICALL direct(JNIEnv *env,void *address,jlong size) { return (jobject)address; }
static jobject JNICALL global(JNIEnv *env,jobject object) { return object; }
static void JNICALL discard(JNIEnv *env,jobject object) { }
static jclass JNICALL object_class(JNIEnv *env,jobject object) { return (jclass)(uintptr_t)1; }
static jmethodID JNICALL method(JNIEnv *env,jclass type,const char *name,const char *sig) { return (jmethodID)(uintptr_t)1; }
static const struct JNINativeInterface_ table={.NewDirectByteBuffer=direct,.NewGlobalRef=global,.DeleteGlobalRef=discard,
    .GetObjectClass=object_class,.GetMethodID=method};
static JNIEnv environment=&table;
static jacobianop_t operations[4096];
static rhsop_t rhs_operations[4096];
static auxbuf_t aux;

static void initialize(solver_t *solver,int n) {
    solver_init(solver,rhs_operations,operations,4096,&aux,&environment,(jobject)(uintptr_t)1);
    solver_resize(solver,n);
}
static void commands(solver_t *solver,int kind,double coefficient) {
    int count=0,n=solver->m_size;
    for(int row=0;row<n;++row) {
        int col=kind==0 ? row : (row+kind)%n;
        operations[count++]=(jacobianop_t){row,col,coefficient};
    }
    solver_process_jacobian_buffer(solver,count);
}
static unsigned mode_flags(int mode,int sticky) {
    return (original & ~(0x6000u|0x8040u|0x3fu)) | 0x1f80u | ((mode/4)<<13)
        | (mode&1 ? 0x40u : 0) | (mode&2 ? 0x8000u : 0) | (sticky ? 0x3fu : 0);
}
static void comparison(solver_t *solver,int mode,int sticky,int expect_failure) {
    int n=solver->m_size;
    double input[257],x[257],expected[257],actual[257];
    require(n<=257,"test vector capacity");
    for(int i=0;i<n;++i) { x[i]=1.+i*.125; input[i]=.5+i*.25; }
    memcpy(expected,input,n*sizeof(double)); memcpy(actual,input,n*sizeof(double));
    /* Deliberately consume the native structure flag before consulting the cache. */
    SuperMatrix *matrix=sparsematrix_supermatrix(&solver->m_A);
    char trans='N'; _mm_setcsr(mode_flags(mode,sticky));
    int wanted=sp_dgemv(&trans,1.,matrix,x,1,-1.,expected,1); unsigned want=_mm_getcsr();
    _mm_setcsr(mode_flags(mode,sticky));
    int got=wwpg_residual_apply(solver,x,actual); unsigned have=_mm_getcsr();
    require(wanted==got && want==have && !memcmp(expected,actual,n*sizeof(double)),"live matrix output/status/MXCSR");
    if(expect_failure) require(!solver->m_residualCache,"failed cache allocation was retained");
    else if(WWPG_RESIDUAL_ENABLE && n>=64) require(solver->m_residualCache!=NULL,"candidate cache missing");
    else require(!solver->m_residualCache,"control/small matrix allocated cache");
    ++checks;
}
static void compare_modes(solver_t *solver) {
    for(int mode=0;mode<16;++mode) for(int sticky=0;sticky<2;++sticky) comparison(solver,mode,sticky,0);
}

int main(void) {
    original=_mm_getcsr();
    require(SetProcessAffinityMask(GetCurrentProcess(),(DWORD_PTR)0xfff000),"process affinity");
    int sizes[]={63,64,65,67,129,257};
    solver_t solver; initialize(&solver,0);
    for(unsigned s=0;s<sizeof(sizes)/sizeof(sizes[0]);++s) {
        solver_resize(&solver,sizes[s]); require(!solver.m_residualCache,"resize retained cache");
        commands(&solver,0,4.); compare_modes(&solver);
        /* Existing-entry writes, followed by factorization consuming REFACTORIZE. */
        commands(&solver,0,1.);
        _mm_setcsr(original); require(!sparsematrix_factorize(&solver.m_A),"factor updated diagonal");
        require(!(solver.m_A.m_flags & SPARSE_MATRIX_REFACTORIZE),"factor flag was not consumed");
        compare_modes(&solver);
        for(int offset=1;offset<=5;++offset) { commands(&solver,offset,.125); compare_modes(&solver); }
        require(solver.m_A.m_elementCount==sizes[s]*6,"insert count");
        _mm_setcsr(original); require(!sparsematrix_factorize(&solver.m_A),"factor inserted structure");
        compare_modes(&solver);
        solver_zero_jacobian(&solver); require(!solver.m_residualCache,"zero retained cache"); compare_modes(&solver);
        commands(&solver,0,8.); compare_modes(&solver);
        solver_resize(&solver,0); require(!solver.m_residualCache,"zero resize retained cache");
        ++cycles;
    }
    solver_resize(&solver,67); commands(&solver,0,4.); compare_modes(&solver);
    solver_t second; initialize(&second,67); commands(&second,0,8.); compare_modes(&second);
    if(WWPG_RESIDUAL_ENABLE) require(solver.m_residualCache!=second.m_residualCache,"solver cache ownership");
    commands(&solver,0,2.); compare_modes(&solver); compare_modes(&second);
    solver_destroy(&second); require(!second.m_residualCache,"destroy retained cache"); ++cycles;
    if(WWPG_RESIDUAL_ENABLE) {
        wwpg_residual_release(&solver);
        allocation_calls=0; tracking=1; comparison(&solver,0,0,0); tracking=0;
        int total=allocation_calls; require(total>0 && live>0,"cache allocations not observed");
        wwpg_residual_release(&solver); require(!live,"cache cleanup leak");
        for(int fail=0;fail<total;++fail) {
            fail_after=fail; tracking=1; comparison(&solver,0,0,1); tracking=0; fail_after=-1;
            require(!live,"partial allocation cleanup leak");
            comparison(&solver,0,0,0); wwpg_residual_release(&solver);
        }
        require(failed_allocations==total,"not every allocation failure was exercised");
        for(int repeat=0;repeat<100;++repeat) {
            tracking=1; comparison(&solver,0,0,0); tracking=0;
            solver_zero_jacobian(&solver); require(!live,"clear cleanup leak");
            commands(&solver,0,4.); ++cycles;
        }
        tracking=1; comparison(&solver,0,0,0); tracking=0;
    }
    solver_destroy(&solver); require(!solver.m_residualCache && !live,"destroy cleanup leak"); ++cycles;
    _mm_setcsr(original);
    printf("PASS %d live matrix comparisons; %d lifecycle cycles; %d allocation failures; cache_enabled=%d\n",checks,cycles,failed_allocations,WWPG_RESIDUAL_ENABLE);
    return 0;
}
