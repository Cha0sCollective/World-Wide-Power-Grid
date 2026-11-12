#pragma once
#include <immintrin.h>
#include <stdint.h>
#include <string.h>

/* The original scalar functions precede this header as wwpg_scalar_*.
 * For finite normal numbers and zero, positive binary64 encodings have exactly
 * the same order as their values. Integer operations avoid reassociation and
 * rounding. Any exceptional/subnormal input uses the untouched scalar path,
 * preserving its NaN/Inf rejection, DAZ behavior and floating exception flags.
 */
static inline __m256i wwpg_exceptional_magnitudes(__m256i magnitude) {
    const __m256i zero = _mm256_setzero_si256();
    const __m256i exponent = _mm256_set1_epi64x(INT64_C(0x7ff0000000000000));
    __m256i field = _mm256_and_si256(magnitude, exponent);
    __m256i nonfinite = _mm256_cmpeq_epi64(field, exponent);
    __m256i subnormal = _mm256_andnot_si256(_mm256_cmpeq_epi64(magnitude, zero),
                                          _mm256_cmpeq_epi64(field, zero));
    return _mm256_or_si256(nonfinite, subnormal);
}

static inline int wwpg_exceptional_magnitude(uint64_t magnitude) {
    uint64_t field = magnitude & UINT64_C(0x7ff0000000000000);
    return field == UINT64_C(0x7ff0000000000000) || (!field && magnitude);
}

static int solver_finite(const double *values, int size) {
    if(size < 16) return wwpg_scalar_finite(values, size);
    const __m256i absolute = _mm256_set1_epi64x(INT64_C(0x7fffffffffffffff));
    __m256i exceptional = _mm256_setzero_si256();
    int row = 0;
    for(; row <= size - 4; row += 4) {
        __m256i bits = _mm256_loadu_si256((const __m256i *)(values + row));
        __m256i magnitude = _mm256_and_si256(bits, absolute);
        exceptional = _mm256_or_si256(exceptional, wwpg_exceptional_magnitudes(magnitude));
    }
    if(_mm256_movemask_epi8(exceptional)) return wwpg_scalar_finite(values, size);
    for(; row < size; ++row) {
        uint64_t bits;
        memcpy(&bits, values + row, sizeof(bits));
        if(wwpg_exceptional_magnitude(bits & UINT64_C(0x7fffffffffffffff)))
            return wwpg_scalar_finite(values, size);
    }
    return 1;
}

static double solver_residual_norm(solver_t *solver) {
    int size = solver->m_size;
    const double *values = solver->m_residual;
    if(size < 16) return wwpg_scalar_norm(solver);
    const __m256i absolute = _mm256_set1_epi64x(INT64_C(0x7fffffffffffffff));
    __m256i exceptional = _mm256_setzero_si256();
    __m256i maximum = _mm256_setzero_si256();
    int row = 0;
    for(; row <= size - 4; row += 4) {
        __m256i bits = _mm256_loadu_si256((const __m256i *)(values + row));
        __m256i magnitude = _mm256_and_si256(bits, absolute);
        exceptional = _mm256_or_si256(exceptional, wwpg_exceptional_magnitudes(magnitude));
        maximum = _mm256_blendv_epi8(maximum, magnitude, _mm256_cmpgt_epi64(magnitude, maximum));
    }
    if(_mm256_movemask_epi8(exceptional)) return wwpg_scalar_norm(solver);
    uint64_t lanes[4], result = 0;
    _mm256_storeu_si256((__m256i *)lanes, maximum);
    for(int lane = 0; lane < 4; ++lane) if(lanes[lane] > result) result = lanes[lane];
    for(; row < size; ++row) {
        uint64_t bits;
        memcpy(&bits, values + row, sizeof(bits));
        uint64_t magnitude = bits & UINT64_C(0x7fffffffffffffff);
        if(wwpg_exceptional_magnitude(magnitude)) return wwpg_scalar_norm(solver);
        if(magnitude > result) result = magnitude;
    }
    double value;
    memcpy(&value, &result, sizeof(value));
    return value;
}
