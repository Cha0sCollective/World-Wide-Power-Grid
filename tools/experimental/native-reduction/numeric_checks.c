/* Compare the actual extracted guarded scalar routines with the candidate. */
#include <windows.h>
#include <immintrin.h>
#include <math.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int reference_finite(const double *, int);
int candidate_finite(const double *, int);
double reference_norm(const double *, int);
double candidate_norm(const double *, int);
static uint64_t random_state = UINT64_C(0xd7311e805a6932b7), checks;
static unsigned original_control;

static uint64_t random_bits(void) {
    random_state ^= random_state >> 12;
    random_state ^= random_state << 25;
    random_state ^= random_state >> 27;
    return random_state * UINT64_C(2685821657736338717);
}
static uint64_t bits(double value) { uint64_t result; memcpy(&result,&value,8); return result; }
static void put(double *value, uint64_t raw) { memcpy(value,&raw,8); }
static void fail(const char *kind, int size, unsigned control, uint64_t expected, uint64_t actual, unsigned a, unsigned b) {
    fprintf(stderr,"FAIL %s size=%d csr=%x expected=%llx actual=%llx flags=%x/%x check=%llu\n",
        kind,size,control,(unsigned long long)expected,(unsigned long long)actual,a,b,(unsigned long long)checks);
    _mm_setcsr(original_control);
    exit(1);
}
static void compare(const double *values, int size, unsigned control) {
    _mm_setcsr(control);
    int af = reference_finite(values,size);
    unsigned ac = _mm_getcsr();
    _mm_setcsr(control);
    int bf = candidate_finite(values,size);
    unsigned bc = _mm_getcsr();
    if(af != bf || ac != bc) fail("finite",size,control,af,bf,ac,bc);
    _mm_setcsr(control);
    double an = reference_norm(values,size);
    ac = _mm_getcsr();
    _mm_setcsr(control);
    double bn = candidate_norm(values,size);
    bc = _mm_getcsr();
    if(bits(an) != bits(bn) || ac != bc) fail("norm",size,control,bits(an),bits(bn),ac,bc);
    checks++;
}
static void fill(double *values, int size, int shape) {
    for(int i=0;i<size;i++) {
        uint64_t raw = random_bits();
        if(shape == 0) raw = (raw & UINT64_C(0x800fffffffffffff)) | ((uint64_t)(1 + (raw >> 32) % 2046) << 52);
        if(shape == 1) raw = i % 5 ? 0 : UINT64_C(0x3ff0000000000000) + (raw & UINT64_C(0x000fffffffffffff));
        if(shape == 2) raw = (uint64_t)(i+1) << 40;
        if(shape == 3) raw = UINT64_C(0x0010000000000000) + (raw & UINT64_C(0x000fffffffffffff));
        if(shape == 4) raw = (i & 1 ? UINT64_C(0x8000000000000000) : 0);
        put(values+i,raw);
    }
}

static void benchmark(void) {
    const int size = 14925, iterations = 512, blocks = 8;
    double *values = malloc((size_t)size * sizeof(double));
    LARGE_INTEGER frequency, start, end;
    QueryPerformanceFrequency(&frequency);
    puts("kind,shape,block,role,calls,elapsed_ms,checksum");
    for(int shape=0;shape<3;shape++) {
        fill(values,size,shape);
        for(int block=0;block<blocks;block++) for(int order=0;order<2;order++) {
            int candidate = (block + order) & 1;
            int (*finite)(const double *,int) = candidate ? candidate_finite : reference_finite;
            double (*norm)(const double *,int) = candidate ? candidate_norm : reference_norm;
            volatile uint64_t checksum = 0;
            QueryPerformanceCounter(&start);
            for(int i=0;i<iterations;i++) { put(values,UINT64_C(0x3ff0000000000000) + i); checksum ^= bits(norm(values,size)); }
            QueryPerformanceCounter(&end);
            printf("norm,%d,%d,%s,%d,%.9f,%llu\n",shape,block,candidate ? "candidate" : "reference",iterations,
                (end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart,(unsigned long long)checksum);
            checksum = 0;
            QueryPerformanceCounter(&start);
            for(int i=0;i<iterations;i++) { put(values,UINT64_C(0x3ff0000000000000) + i); checksum += finite(values,size); }
            QueryPerformanceCounter(&end);
            printf("finite,%d,%d,%s,%d,%.9f,%llu\n",shape,block,candidate ? "candidate" : "reference",iterations,
                (end.QuadPart-start.QuadPart)*1000.0/frequency.QuadPart,(unsigned long long)checksum);
        }
    }
    free(values);
}

int main(int argc, char **argv) {
    original_control = _mm_getcsr();
    if(!SetProcessAffinityMask(GetCurrentProcess(),(DWORD_PTR)0xfff000)) return 2;
    if(argc == 2 && !strcmp(argv[1],"--benchmark")) { benchmark(); return 0; }
    const int sizes[] = {0,1,2,3,4,7,8,15,16,17,31,32,33,63,64,65,127,255,256,257,14925};
    const uint64_t special[] = {0,UINT64_C(0x8000000000000000),1,UINT64_C(0x8000000000000001),
        UINT64_C(0x000fffffffffffff),UINT64_C(0x800fffffffffffff),
        UINT64_C(0x0010000000000000),UINT64_C(0x8010000000000000),
        UINT64_C(0x7fefffffffffffff),UINT64_C(0xffefffffffffffff),
        UINT64_C(0x7ff0000000000000),UINT64_C(0xfff0000000000000),
        UINT64_C(0x7ff8000000000000),UINT64_C(0xfff8000000000017),
        UINT64_C(0x7ff0000000000001),UINT64_C(0xfff0000000000017)};
    double *storage = malloc((14925+8)*sizeof(double));
    double *saved = malloc((14925+8)*sizeof(double));
    if(!storage || !saved) return 3;
    for(int rounding=0;rounding<4;rounding++) for(int flushing=0;flushing<4;flushing++) {
        unsigned control = (original_control & ~(0x6000u | 0x8040u | 0x3fu)) | 0x1f80u
            | (rounding << 13) | (flushing & 1 ? 0x40u : 0) | (flushing & 2 ? 0x8000u : 0);
        for(unsigned s=0;s<sizeof(sizes)/sizeof(*sizes);s++) for(int offset=0;offset<4;offset++) for(int shape=0;shape<5;shape++) {
            int size = sizes[s]; double *values = storage+offset;
            fill(values,size,shape); memcpy(saved,values,size*sizeof(double));
            compare(values,size,control);
            compare(values,size,control | 0x3fu);
            if(memcmp(saved,values,size*sizeof(double))) fail("input mutation",size,control,0,1,0,0);
        }
        for(unsigned s=0;s<sizeof(special)/sizeof(*special);s++) for(int position=0;position<67;position++) {
            fill(storage,67,1); put(storage+position,special[s]); compare(storage,67,control);
        }
        for(int trial=0;trial<600;trial++) {
            int size = random_bits() % 512;
            for(int i=0;i<size;i++) put(storage+i,random_bits());
            compare(storage,size,control);
        }
        compare(NULL,0,control);
        compare(NULL,-1,control);
    }
    _mm_setcsr(original_control);
    SYSTEM_INFO info; GetSystemInfo(&info);
    unsigned char *pages = VirtualAlloc(NULL,info.dwPageSize*3,MEM_COMMIT|MEM_RESERVE,PAGE_READWRITE);
    DWORD ignored;
    if(!pages || !VirtualProtect(pages,info.dwPageSize,PAGE_NOACCESS,&ignored)
        || !VirtualProtect(pages+2*info.dwPageSize,info.dwPageSize,PAGE_NOACCESS,&ignored)) return 4;
    for(int size=0;size<=257;size++) {
        double *first = (double *)(pages+info.dwPageSize);
        double *last = (double *)(pages+2*info.dwPageSize)-size;
        fill(first,size,0); compare(first,size,original_control);
        fill(last,size,0); compare(last,size,original_control);
    }
    VirtualFree(pages,0,MEM_RELEASE);
    _mm_setcsr(original_control);
    free(saved); free(storage);
    printf("PASS %llu exact scalar/candidate comparisons across 16 FP modes, exceptional values and guarded boundaries\n",
        (unsigned long long)checks);
    return 0;
}
