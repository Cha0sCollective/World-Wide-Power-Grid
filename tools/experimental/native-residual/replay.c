#include <windows.h>
#include <immintrin.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "row_layout.h"

static unsigned original;
static void require(int okay,const char *message) {
    if(!okay) { fprintf(stderr,"FAIL %s\n",message); _mm_setcsr(original); exit(1); }
}
static void bytes(FILE *file,void *target,size_t size) { require(fread(target,1,size,file)==size,"truncated capture"); }
static int reference(wwpg_row_plan *p,double a,SuperMatrix *m,double *x,double b,double *y) {
    char trans='N'; return sp_dgemv(&trans,a,m,x,1,b,y,1);
}
typedef int (*kernel)(wwpg_row_plan *,double,SuperMatrix *,double *,double,double *);

int main(int argc,char **argv) {
    original=_mm_getcsr();
    require(argc==2,"supply one capture");
    require(SetProcessAffinityMask(GetCurrentProcess(),(DWORD_PTR)0xfff000),"process affinity");
    FILE *file=fopen(argv[1],"rb"); require(file!=NULL,"open capture");
    char magic[8]; uint32_t meta[4],flags[2],status[2]; uint64_t ordinal;
    bytes(file,magic,8); bytes(file,meta,16); bytes(file,&ordinal,8); bytes(file,flags,8);
    require(!memcmp(magic,"WWPGRS01",8) && meta[0]==1 && meta[1]==14925 && meta[2]<=1000000,"capture header");
    int n=meta[1], nnz=meta[2]; double alpha,beta;
    bytes(file,&alpha,8); bytes(file,&beta,8);
    int *columns=malloc((n+1)*4),*rows=malloc((nnz+1)*4);
    double *values=malloc((nnz+1)*8),*x=malloc(n*8),*input=malloc(n*8),*captured=malloc(n*8);
    double *expected=malloc(n*8),*actual=malloc(n*8);
    require(columns && rows && values && x && input && captured && expected && actual,"allocation");
    bytes(file,columns,(n+1)*4); bytes(file,rows,nnz*4); bytes(file,values,nnz*8);
    bytes(file,x,n*8); bytes(file,input,n*8); bytes(file,status,8); bytes(file,captured,n*8);
    bytes(file,magic,8); require(!memcmp(magic,"ENDRS001",8) && fgetc(file)==EOF,"capture footer"); fclose(file);
    require(columns[0]==0 && columns[n]==nnz,"column range");
    for(int c=0;c<n;++c) require(columns[c]>=0 && columns[c]<=columns[c+1],"column order");
    for(int i=0;i<nnz;++i) require(rows[i]>=0 && rows[i]<n,"row range");
    NCformat store={.nnz=nnz,.nzval=values,.rowind=rows,.colptr=columns};
    SuperMatrix matrix={.Stype=SLU_NC,.Dtype=SLU_D,.Mtype=SLU_GE,.nrow=n,.ncol=n,.Store=&store};
    wwpg_row_plan plan;
    require(wwpg_row_build(&plan,&matrix),"row plan");
    require(wwpg_tiles_build(&plan,&matrix),"tile plan");
    memcpy(expected,input,n*8); _mm_setcsr(flags[0]);
    int ret=reference(&plan,alpha,&matrix,x,beta,expected); unsigned csr=_mm_getcsr();
    require(ret==(int)status[0] && csr==status[1] && !memcmp(expected,captured,n*8),"captured reference replay");
    kernel functions[4]={reference,wwpg_row_indexed,wwpg_row_packed,wwpg_row_tiled};
    const char *names[4]={"reference","row-indexed","row-packed","row-tiled"};
    int checks=0;
    for(int rounding=0;rounding<4;++rounding) for(int flushing=0;flushing<4;++flushing) for(int sticky=0;sticky<2;++sticky) {
        unsigned control=(original & ~(0x6000u|0x8040u|0x3fu)) | 0x1f80u
            | (rounding<<13) | (flushing&1 ? 0x40u : 0) | (flushing&2 ? 0x8000u : 0) | (sticky ? 0x3fu : 0);
        memcpy(expected,input,n*8); _mm_setcsr(control); reference(&plan,alpha,&matrix,x,beta,expected); csr=_mm_getcsr();
        for(int role=1;role<4;++role) {
            memcpy(actual,input,n*8); _mm_setcsr(control); functions[role](&plan,alpha,&matrix,x,beta,actual);
            unsigned got=_mm_getcsr();
            if(got!=csr || memcmp(actual,expected,n*8)) {
                fprintf(stderr,"role=%s rounding=%d flushing=%d sticky=%d csr=%x/%x\n",names[role],rounding,flushing,sticky,csr,got);
                require(0,"row layout differs");
            }
            ++checks;
        }
    }
    _mm_setcsr(flags[0]);
    printf("PASS %d full-vector and MXCSR comparisons; rows=%d nnz=%d\n",checks,n,nnz);
    LARGE_INTEGER freq,start,end; QueryPerformanceFrequency(&freq);
    puts("role,block,calls,elapsed_ms");
    for(int block=0;block<12;++block) for(int order=0;order<4;++order) {
        int role=(block+order)%4;
        QueryPerformanceCounter(&start);
        for(int call=0;call<512;++call) { memcpy(actual,input,n*8); functions[role](&plan,alpha,&matrix,x,beta,actual); }
        QueryPerformanceCounter(&end);
        require(!memcmp(actual,captured,n*8),"benchmark output changed");
        printf("%s,%d,512,%.9f\n",names[role],block,(end.QuadPart-start.QuadPart)*1000.0/freq.QuadPart);
    }
    _mm_setcsr(original);
    wwpg_tiles_free(&plan); wwpg_row_free(&plan); free(columns); free(rows); free(values); free(x); free(input); free(captured); free(expected); free(actual);
    return 0;
}
