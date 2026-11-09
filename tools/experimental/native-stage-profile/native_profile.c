#include "solver.h"
#include "native_profile.h"
#include <windows.h>
#include <jni.h>

uint64_t wwpg_profile_clock(void) {
    LARGE_INTEGER value;
    QueryPerformanceCounter(&value);
    return (uint64_t)value.QuadPart;
}

uint64_t wwpg_profile_frequency(void) {
    LARGE_INTEGER value;
    QueryPerformanceFrequency(&value);
    return (uint64_t)value.QuadPart;
}

/* The helper copies this read-only observation immediately after the native call.
 * The original one-byte auxiliary buffer and all existing JNI methods are intact. */
JNIEXPORT jobject JNICALL Java_org_cha0scollective_wwpg_benchmark_NativeStages_snapshot
        (JNIEnv *env, jclass ignored, jlong pointer) {
    solver_t *solver = (solver_t *)(uintptr_t)pointer;
    if (!solver || solver->m_profile[0] != WWPG_PROFILE_MAGIC || solver->m_A.m_profile != NULL) {
        jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (type) (*env)->ThrowNew(env, type, "Native stage snapshot is unavailable or the solve is active");
        return NULL;
    }
    return (*env)->NewDirectByteBuffer(env, solver->m_profile, sizeof(solver->m_profile));
}
