/* JNI ABI declarations for the supported Linux x86-64 platform. */
#ifndef WWPG_JNI_MD_H
#define WWPG_JNI_MD_H
#define JNIEXPORT __attribute__((visibility("default")))
#define JNIIMPORT
#define JNICALL
typedef int jint;
typedef long jlong;
typedef signed char jbyte;
#endif
