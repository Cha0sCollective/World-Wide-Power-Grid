#pragma once
#include <stdint.h>

#define WWPG_PROFILE_MAGIC UINT64_C(0x5757504750524f31)
enum wwpg_profile_stage {
    WWPG_TOTAL, WWPG_RESIDUAL_MULTIPLY, WWPG_RESIDUAL_NORM, WWPG_SPARSE_SOLVE,
    WWPG_FACTORIZATION, WWPG_TRIANGULAR_SOLVE, WWPG_PERMUTATION, WWPG_FINITE,
    WWPG_COPY, WWPG_DELTA, WWPG_JAVA_CALLBACK, WWPG_JACOBIAN, WWPG_RHS,
    WWPG_STAGE_COUNT
};
#define WWPG_PROFILE_WORDS (4 + 2 * WWPG_STAGE_COUNT)

uint64_t wwpg_profile_clock(void);
uint64_t wwpg_profile_frequency(void);
static inline void wwpg_profile_record(uint64_t *profile, int stage, uint64_t start) {
    if (!profile) return;
    uint64_t elapsed = wwpg_profile_clock() - start;
    profile[4 + 2 * stage]++;
    profile[5 + 2 * stage] += elapsed;
}

/* Expressions execute exactly once, with their original result type. */
#define WWPG_PROFILE_VALUE(profile, stage, type, expression) ({ \
    uint64_t *wwpg_target_ = (profile); \
    uint64_t wwpg_start_ = wwpg_target_ ? wwpg_profile_clock() : 0; \
    type wwpg_value_ = (expression); \
    wwpg_profile_record(wwpg_target_, (stage), wwpg_start_); \
    wwpg_value_; \
})
#define WWPG_PROFILE_VOID(profile, stage, expression) do { \
    uint64_t *wwpg_target_ = (profile); \
    uint64_t wwpg_start_ = wwpg_target_ ? wwpg_profile_clock() : 0; \
    expression; \
    wwpg_profile_record(wwpg_target_, (stage), wwpg_start_); \
} while (0)
