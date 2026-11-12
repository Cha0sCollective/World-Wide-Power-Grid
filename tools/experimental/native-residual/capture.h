#pragma once
#include <windows.h>
#include <immintrin.h>
#include <stdint.h>
#include <stdatomic.h>
#include <stdio.h>

_Static_assert(sizeof(int_t) == 4 && sizeof(double) == 8, "Capture ABI requires int32 and binary64");
static _Atomic uint64_t wwpg_outer_residual_calls;
static _Atomic unsigned wwpg_residual_slots[3];

static int wwpg_capture_write(HANDLE file, const void *data, size_t bytes) {
    DWORD written = 0;
    return bytes <= UINT32_MAX && WriteFile(file, data, (DWORD)bytes, &written, NULL) && written == bytes;
}

/* Integer selection and read-only copies surround the original operation.
 * Restore its exact input/output MXCSR around diagnostic file I/O. */
static int wwpg_capture_gemv(int stage, char *trans, double alpha, SuperMatrix *matrix,
                            double *x, int incx, double beta, double *y, int incy) {
    if(matrix->nrow != 14925 || matrix->ncol != 14925)
        return sp_dgemv(trans, alpha, matrix, x, incx, beta, y, incy);
    unsigned before = _mm_getcsr();
    uint64_t ordinal = stage == 0
        ? atomic_fetch_add_explicit(&wwpg_outer_residual_calls, 1, memory_order_relaxed) + 1
        : atomic_load_explicit(&wwpg_outer_residual_calls, memory_order_relaxed);
    int slot = stage == 0 && ordinal == 4096 ? 0
             : stage == 0 && ordinal == 40960 ? 1
             : stage == 1 && ordinal >= 40960 ? 2 : -1;
    unsigned expected = 0;
    if(slot >= 0 && !atomic_compare_exchange_strong_explicit(&wwpg_residual_slots[slot], &expected, 1,
                                                           memory_order_relaxed, memory_order_relaxed)) slot = -1;
    const char *names[3] = {"benchmark/native-residual-outer-4096.bin",
                          "benchmark/native-residual-outer-40960.bin",
                          "benchmark/native-residual-fit.bin"};
    HANDLE file = INVALID_HANDLE_VALUE;
    int okay = 0;
    NCformat *store = matrix->Store;
    if(slot >= 0 && matrix->Stype == SLU_NC && matrix->Dtype == SLU_D && matrix->Mtype == SLU_GE
       && incx == 1 && incy == 1 && *trans == 'N' && store->nnz >= 0 && store->nnz <= 1000000) {
        file = CreateFileA(names[slot], GENERIC_WRITE, 0, NULL, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, NULL);
        if(file != INVALID_HANDLE_VALUE) {
            uint32_t meta[4] = {1, 14925, (uint32_t)store->nnz, (uint32_t)stage};
            uint32_t flags[2] = {before, 0};
            okay = wwpg_capture_write(file, "WWPGRS01", 8)
                && wwpg_capture_write(file, meta, sizeof(meta))
                && wwpg_capture_write(file, &ordinal, sizeof(ordinal))
                && wwpg_capture_write(file, flags, sizeof(flags))
                && wwpg_capture_write(file, &alpha, 8) && wwpg_capture_write(file, &beta, 8)
                && wwpg_capture_write(file, store->colptr, 14926 * sizeof(int_t))
                && wwpg_capture_write(file, store->rowind, store->nnz * sizeof(int_t))
                && wwpg_capture_write(file, store->nzval, store->nnz * sizeof(double))
                && wwpg_capture_write(file, x, 14925 * sizeof(double))
                && wwpg_capture_write(file, y, 14925 * sizeof(double));
        }
    }
    _mm_setcsr(before);
    int result = sp_dgemv(trans, alpha, matrix, x, incx, beta, y, incy);
    unsigned after = _mm_getcsr();
    if(file != INVALID_HANDLE_VALUE) {
        uint32_t status[2] = {(uint32_t)result, after};
        okay = okay && wwpg_capture_write(file, status, sizeof(status))
            && wwpg_capture_write(file, y, 14925 * sizeof(double))
            && wwpg_capture_write(file, "ENDRS001", 8);
        if(!CloseHandle(file)) okay = 0;
    }
    if(slot >= 0) {
        printf("WWPG_RESIDUAL_CAPTURE slot=%d stage=%d outer=%llu rows=14925 nnz=%d okay=%d\n",
               slot, stage, (unsigned long long)ordinal, (int)store->nnz, okay);
        fflush(stdout);
    }
    _mm_setcsr(after);
    return result;
}
