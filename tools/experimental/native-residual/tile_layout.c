/* Offline only: four independent rows per vector, ordered contributions per row. */
#include "row_layout.h"
#include <immintrin.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

typedef struct { int begin, count, degree, value_begin; } row_group;
struct wwpg_tiles {
    int group_count, value_count;
    row_group *groups;
    int *rows, *columns, *entries;
    double *values;
};

void wwpg_tiles_free(wwpg_row_plan *plan) {
    struct wwpg_tiles *tiles=plan->tiles;
    if(!tiles) return;
    free(tiles->groups); free(tiles->rows); free(tiles->columns);
    free(tiles->entries); free(tiles->values); free(tiles); plan->tiles=NULL;
}

void wwpg_tiles_refresh(wwpg_row_plan *plan, SuperMatrix *matrix) {
    wwpg_row_refresh(plan,matrix);
    struct wwpg_tiles *tiles=plan->tiles;
    const double *values=((NCformat *)matrix->Store)->nzval;
    for(int i=0;i<tiles->value_count;++i) tiles->values[i]=values[tiles->entries[i]];
}

int wwpg_tiles_build(wwpg_row_plan *plan, SuperMatrix *matrix) {
    wwpg_tiles_free(plan);
    struct wwpg_tiles *tiles=calloc(1,sizeof(*tiles));
    if(!tiles) return 0;
    plan->tiles=tiles;
    int maximum=0;
    for(int row=0;row<plan->n;++row) {
        int degree=plan->offsets[row+1]-plan->offsets[row];
        if(degree>maximum) maximum=degree;
    }
    int *counts=calloc(maximum+1,sizeof(int)),*starts=calloc(maximum+2,sizeof(int));
    int *cursor=calloc(maximum+1,sizeof(int));
    int capacity=plan->nnz ? plan->nnz : 1;
    tiles->groups=calloc(maximum+1,sizeof(row_group));
    tiles->rows=malloc((plan->n+1)*sizeof(int));
    tiles->columns=malloc(capacity*sizeof(int)); tiles->entries=malloc(capacity*sizeof(int));
    tiles->values=malloc(capacity*sizeof(double));
    if(!counts || !starts || !cursor || !tiles->groups || !tiles->rows || !tiles->columns || !tiles->entries || !tiles->values) {
        free(counts); free(starts); free(cursor); wwpg_tiles_free(plan); return 0;
    }
    for(int row=0;row<plan->n;++row) ++counts[plan->offsets[row+1]-plan->offsets[row]];
    for(int degree=0;degree<=maximum;++degree) starts[degree+1]=starts[degree]+counts[degree];
    memcpy(cursor,starts,(maximum+1)*sizeof(int));
    for(int row=0;row<plan->n;++row) tiles->rows[cursor[plan->offsets[row+1]-plan->offsets[row]]++]=row;
    for(int degree=0;degree<=maximum;++degree) if(counts[degree]) {
        row_group *group=tiles->groups+tiles->group_count++;
        *group=(row_group){starts[degree],counts[degree],degree,tiles->value_count};
        for(int at=0;at+4<=group->count;at+=4) for(int term=0;term<degree;++term) for(int lane=0;lane<4;++lane) {
            int row=tiles->rows[group->begin+at+lane], source=plan->offsets[row]+term;
            int target=tiles->value_count++;
            tiles->columns[target]=plan->columns[source]; tiles->entries[target]=plan->entries[source];
        }
    }
    free(counts); free(starts); free(cursor);
    wwpg_tiles_refresh(plan,matrix);
    return 1;
}

/* Classify with integer operations so exceptional values keep the reference flags. */
static int eligible_values(const double *values,int n,int require_normal) {
    const __m256i mask=_mm256_set1_epi64x(INT64_C(0x7ff0000000000000));
    const __m256i zero=_mm256_setzero_si256();
    __m256i bad=zero;
    int i=0;
    for(;i<=n-4;i+=4) {
        __m256i exp=_mm256_and_si256(_mm256_loadu_si256((const __m256i *)(values+i)),mask);
        bad=_mm256_or_si256(bad,_mm256_cmpeq_epi64(exp,mask));
        if(require_normal) bad=_mm256_or_si256(bad,_mm256_cmpeq_epi64(exp,zero));
    }
    if(_mm256_movemask_epi8(bad)) return 0;
    for(;i<n;++i) {
        uint64_t bits; memcpy(&bits,values+i,8); bits &= UINT64_C(0x7ff0000000000000);
        if(bits==UINT64_C(0x7ff0000000000000) || (require_normal && !bits)) return 0;
    }
    return 1;
}

__attribute__((noinline,noipa)) int wwpg_row_tiled(wwpg_row_plan *plan,double alpha,SuperMatrix *matrix,double *x,double beta,double *y) {
    uint64_t ab,bb; memcpy(&ab,&alpha,8); memcpy(&bb,&beta,8);
    uintptr_t xb=(uintptr_t)x,yb=(uintptr_t)y,bytes=(uintptr_t)plan->n*sizeof(double);
    if(!plan->tiles || ab!=UINT64_C(0x3ff0000000000000) || bb!=UINT64_C(0xbff0000000000000)
       || (_mm_getcsr() & 0x1f80u)!=0x1f80u || (xb<yb+bytes && yb<xb+bytes)
       || plan->nonfinite || !eligible_values(x,plan->n,1) || !eligible_values(y,plan->n,0)) {
        char trans='N'; return sp_dgemv(&trans,alpha,matrix,x,1,beta,y,1);
    }
    /* Multiplication matters for subnormal y with FTZ; sign flipping is not equivalent. */
    for(int row=0;row<plan->n;++row) y[row]=beta*y[row];
    struct wwpg_tiles *tiles=plan->tiles;
    for(int g=0;g<tiles->group_count;++g) {
        const row_group *group=tiles->groups+g;
        int at=0, value_at=group->value_begin;
        for(;at+4<=group->count;at+=4) {
            const int *rows=tiles->rows+group->begin+at;
            __m128i indices=_mm_loadu_si128((const __m128i *)rows);
            __m256d sums=_mm256_i32gather_pd(y,indices,8);
            for(int term=0;term<group->degree;++term,value_at+=4) {
                indices=_mm_loadu_si128((const __m128i *)(tiles->columns+value_at));
                __m256d state=_mm256_i32gather_pd(x,indices,8);
                __m256d product=_mm256_mul_pd(state,_mm256_loadu_pd(tiles->values+value_at));
                sums=_mm256_add_pd(sums,product);
            }
            double result[4]; _mm256_storeu_pd(result,sums);
            for(int lane=0;lane<4;++lane) y[rows[lane]]=result[lane];
        }
        for(;at<group->count;++at) {
            int row=tiles->rows[group->begin+at]; double sum=y[row];
            for(int i=plan->offsets[row];i<plan->offsets[row+1];++i) sum += x[plan->columns[i]]*plan->packed[i];
            y[row]=sum;
        }
    }
    return 0;
}
