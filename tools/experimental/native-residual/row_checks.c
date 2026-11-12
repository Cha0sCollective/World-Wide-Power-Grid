#include <windows.h>
#include <immintrin.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "row_layout.h"

typedef struct { void *base,*data; } guarded;
static unsigned original;
static int checks,refreshes,rebuilds;
static void require(int okay,const char *message) {
    if(!okay) { _mm_setcsr(original); fprintf(stderr,"FAIL %s\n",message); exit(1); }
}
static guarded allocate(size_t length,int tail) {
    SYSTEM_INFO system; GetSystemInfo(&system);
    size_t page=system.dwPageSize,used=length ? length : 1;
    size_t accessible=((used+page-1)/page)*page;
    char *base=VirtualAlloc(NULL,accessible+2*page,MEM_RESERVE,PAGE_NOACCESS);
    require(base!=NULL,"reserve guarded buffer");
    require(VirtualAlloc(base+page,accessible,MEM_COMMIT,PAGE_READWRITE)!=NULL,"commit guarded buffer");
    return (guarded){base,base+page+(tail ? accessible-length : 0)};
}
static void release(guarded allocation) { require(VirtualFree(allocation.base,0,MEM_RELEASE),"free guard"); }
static double bits(uint64_t value) { double result; memcpy(&result,&value,8); return result; }
static uint64_t random_state=UINT64_C(0xd61e9d2ea1245917);
static uint64_t random_bits(void) {
    random_state ^= random_state<<13; random_state ^= random_state>>7; random_state ^= random_state<<17;
    return random_state;
}
static double normal(void) {
    uint64_t value=random_bits();
    return bits((value & UINT64_C(0x800fffffffffffff)) | UINT64_C(0x3ff0000000000000));
}
static unsigned control(int mode,int sticky) {
    return (original & ~(0x6000u|0x8040u|0x3fu)) | 0x1f80u | ((mode/4)<<13)
        | (mode&1 ? 0x40u : 0) | (mode&2 ? 0x8000u : 0) | (sticky ? 0x3fu : 0);
}
static int reference(double a,SuperMatrix *matrix,double *x,double b,double *y) {
    char trans='N'; return sp_dgemv(&trans,a,matrix,x,1,b,y,1);
}
static void check(wwpg_row_plan *plan,SuperMatrix *matrix,double a,double b,double *x,
                  double *input,double *expected,double *actual,int mode,int sticky,int pattern,int tail) {
    size_t bytes=(size_t)plan->n*sizeof(double);
    memcpy(expected,input,bytes); _mm_setcsr(control(mode,sticky));
    int expected_status=reference(a,matrix,x,b,expected); unsigned expected_csr=_mm_getcsr();
    memcpy(actual,input,bytes); _mm_setcsr(control(mode,sticky));
    int actual_status=wwpg_row_tiled(plan,a,matrix,x,b,actual); unsigned actual_csr=_mm_getcsr();
    if(expected_status!=actual_status || expected_csr!=actual_csr || memcmp(expected,actual,bytes)) {
        _mm_setcsr(original);
        fprintf(stderr,"n=%d nnz=%d pattern=%d mode=%d sticky=%d boundary=%d csr=%x/%x status=%d/%d\n",
            plan->n,plan->nnz,pattern,mode,sticky,tail,expected_csr,actual_csr,expected_status,actual_status);
        for(int i=0;i<plan->n;++i) if(memcmp(expected+i,actual+i,8)) {
            uint64_t eb,ab; memcpy(&eb,expected+i,8); memcpy(&ab,actual+i,8);
            fprintf(stderr,"first differing row=%d bits=%llx/%llx\n",i,(unsigned long long)eb,(unsigned long long)ab); break;
        }
        require(0,"tiled kernel output or flags differ");
    }
    ++checks;
}

static void exercise(int n,int tail,int structure) {
    int maximum=n<11 ? n : 11,capacity=n*(maximum ? maximum : 1);
    guarded cg=allocate((n+1)*sizeof(int),tail),rg=allocate(capacity*sizeof(int),tail),vg=allocate(capacity*sizeof(double),tail);
    guarded xg=allocate(n*sizeof(double),tail),ig=allocate(n*sizeof(double),tail);
    guarded eg=allocate(n*sizeof(double),tail),ag=allocate(n*sizeof(double),tail);
    int *columns=cg.data,*rows=rg.data,nnz=0;
    double *values=vg.data,*x=xg.data,*input=ig.data,*expected=eg.data,*actual=ag.data;
    for(int col=0;col<n;++col) {
        columns[col]=nnz;
        for(int row=0;row<n;++row) {
            int degree=structure==2 ? 0 : (structure ? maximum : row%(maximum+1));
            if((col+row)%n < degree) rows[nnz++]=row;
        }
    }
    columns[n]=nnz;
    for(int i=0;i<nnz;++i) values[i]=normal();
    NCformat store={.nnz=nnz,.nzval=values,.rowind=rows,.colptr=columns};
    SuperMatrix matrix={.Stype=SLU_NC,.Dtype=SLU_D,.Mtype=SLU_GE,.nrow=n,.ncol=n,.Store=&store};
    wwpg_row_plan plan;
    require(wwpg_row_build(&plan,&matrix) && wwpg_tiles_build(&plan,&matrix),"build row and tile plans"); ++rebuilds;
    for(int pattern=0;pattern<28;++pattern) {
        for(int i=0;i<nnz;++i) values[i]=normal();
        for(int i=0;i<n;++i) { x[i]=normal(); input[i]=normal(); }
        double a=1.,b=-1.;
        for(int i=0;i<n;++i) {
            uint64_t sign=(i&1) ? UINT64_C(0x8000000000000000) : 0;
            if(pattern==1) x[i]=bits(sign);
            if(pattern==2) x[i]=bits(sign|1);
            if(pattern==3) x[i]=bits(sign|UINT64_C(0xfffffffffffff));
            if(pattern==4) x[i]=bits(sign|UINT64_C(0x7ff0000000000001));
            if(pattern==5) x[i]=bits(sign|UINT64_C(0x7ff800000000002a));
            if(pattern==6) x[i]=bits(sign|UINT64_C(0x7ff0000000000000));
            if(pattern==7) input[i]=bits(sign|1);
            if(pattern==12) input[i]=bits(sign|UINT64_C(0x7ff0000000000001));
            if(pattern==13) input[i]=bits(sign|UINT64_C(0x7ff0000000000000));
            if(pattern==14 || pattern==16) x[i]=2.;
            if(pattern==15) x[i]=bits(UINT64_C(0x0010000000000000));
            if(pattern==17) input[i]=bits(sign);
            if(pattern==24) x[i]=bits(sign|UINT64_C(0x0010000000000000));
            if(pattern==25) x[i]=bits(sign|UINT64_C(0x7fefffffffffffff));
            if(pattern==26) input[i]=bits(sign|UINT64_C(0xfffffffffffff));
        }
        for(int i=0;i<nnz;++i) {
            uint64_t sign=(i&1) ? UINT64_C(0x8000000000000000) : 0;
            if(pattern==8) values[i]=bits(sign|1);
            if(pattern==9) values[i]=bits(sign);
            if(pattern==10) values[i]=bits(sign|UINT64_C(0x7ff0000000000001));
            if(pattern==11) values[i]=bits(sign|UINT64_C(0x7ff0000000000000));
            if(pattern==14 || pattern==16) values[i]=bits((pattern==16 ? sign : 0)|UINT64_C(0x7fefffffffffffff));
            if(pattern==15) values[i]=bits(sign|UINT64_C(0x0010000000000000));
            if(pattern==27) values[i]=bits(sign|UINT64_C(0xfffffffffffff));
        }
        if(pattern==18) b=0.;
        if(pattern==19) { a=0.; b=1.; }
        if(pattern==20) { a=-2.; b=.25; }
        if(pattern==21) a=bits(UINT64_C(0x7ff0000000000001));
        if(pattern==22) b=bits(UINT64_C(0x7ff0000000000001));
        if(pattern==23) { a=-0.; b=-0.; }
        wwpg_tiles_refresh(&plan,&matrix); ++refreshes;
        for(int mode=0;mode<16;++mode) for(int sticky=0;sticky<2;++sticky)
            check(&plan,&matrix,a,b,x,input,expected,actual,mode,sticky,pattern,tail);
    }
    /* Same and partially overlapping vectors must preserve the original sequential semantics. */
    if(n) {
        guarded bg=allocate((n+1)*sizeof(double),tail),dg=allocate((n+1)*sizeof(double),tail);
        double *before=bg.data,*after=dg.data;
        for(int i=0;i<nnz;++i) values[i]=normal();
        wwpg_tiles_refresh(&plan,&matrix); ++refreshes;
        for(int overlap=0;overlap<3;++overlap) for(int mode=0;mode<16;++mode) {
            for(int i=0;i<=n;++i) before[i]=normal();
            memcpy(after,before,(n+1)*sizeof(double));
            int xo=overlap==1,yo=overlap==2;
            _mm_setcsr(control(mode,0)); reference(1.,&matrix,before+xo,-1.,before+yo); unsigned want=_mm_getcsr();
            _mm_setcsr(control(mode,0)); wwpg_row_tiled(&plan,1.,&matrix,after+xo,-1.,after+yo); unsigned got=_mm_getcsr();
            require(want==got && !memcmp(before,after,(n+1)*sizeof(double)),"overlapping vector fallback"); ++checks;
        }
        release(bg); release(dg);
    }
    wwpg_tiles_free(&plan); wwpg_row_free(&plan);
    release(cg); release(rg); release(vg); release(xg); release(ig); release(eg); release(ag);
}

int main(void) {
    original=_mm_getcsr();
    require(SetProcessAffinityMask(GetCurrentProcess(),(DWORD_PTR)0xfff000),"process affinity");
    int sizes[]={0,1,2,3,4,5,7,8,9,16,17,33,65,129};
    for(unsigned i=0;i<sizeof(sizes)/sizeof(sizes[0]);++i) for(int tail=0;tail<2;++tail)
        for(int structure=0;structure<3;++structure) exercise(sizes[i],tail,structure);
    int cols[]={0,1},rows[]={0}; double value=1.,x=2.,expected,actual;
    NCformat store={.nnz=1,.nzval=&value,.rowind=rows,.colptr=cols};
    SuperMatrix matrix={.Stype=SLU_NC,.Dtype=SLU_D,.Mtype=SLU_GE,.nrow=1,.ncol=1,.Store=&store};
    wwpg_row_plan plan; require(wwpg_row_build(&plan,&matrix) && wwpg_tiles_build(&plan,&matrix),"unmasked plan");
    for(int mode=0;mode<16;++mode) for(int unmask=0;unmask<7;++unmask) {
        unsigned csr=control(mode,0) & ~(unmask==6 ? 0x1f80u : 1u<<(7+unmask));
        expected=actual=1.; _mm_setcsr(csr); reference(1.,&matrix,&x,-1.,&expected); unsigned want=_mm_getcsr();
        _mm_setcsr(csr); wwpg_row_tiled(&plan,1.,&matrix,&x,-1.,&actual); unsigned got=_mm_getcsr();
        require(want==got && !memcmp(&expected,&actual,8),"unmasked exact-input fallback"); ++checks;
    }
    wwpg_tiles_free(&plan); wwpg_row_free(&plan); _mm_setcsr(original);
    printf("PASS %d tiled output/status/MXCSR comparisons; %d explicit value refreshes; %d structural rebuilds; both guard boundaries\n",checks,refreshes,rebuilds);
    return 0;
}
