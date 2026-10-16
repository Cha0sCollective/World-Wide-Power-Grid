Guarded PG native solver (development)
======================================

This directory holds focused error-handling patches against the exact upstream
sources in sources.lock.json, plus a standalone fault-injection executable.
PG's equations, Newton iteration limits, precision and BLAS targets are kept.
The SuperLU patch releases local work on early allocation-error returns and
makes partially allocated factor buffers visible to the PG wrapper's owner.
The PG patch checks factorization status and both permutations before using
factors or inverse permutations. Failure throws through JNI, restores the
substep entry state in both direct buffers, and leaves acceptance false.
No fault-injection functions are linked into the shipped library.
Every residual row and returned state row is checked for finite values. The
finite infinity norm, equations and convergence tolerances are unchanged.
The original binary resources remain in the jar for historical provenance;
the loader selects only the guarded build and never an unchecked v7 fallback.

Build with tools/build_native_solver.py. Sources are pinned by commit and
archive checksum. Builds require CMake, a C compiler, make, and JDK JNI headers;
Windows cross-builds also require a MinGW toolchain. Native resources are copied
to build/guarded-pg-native with a checksummed manifest. Gradle verifies the
manifest before packaging. Never reuse native output after changing a patch.

prebuilt/ contains the exact guarded libraries accepted with dev.94, their
input manifests and upstream licenses. Clean checkouts use these frozen inputs;
an existing build/guarded-pg-native directory takes precedence and is still
validated. Select an explicit workspace directory with
-PguardedNativeDirectory=tools/native/prebuilt (or build/guarded-pg-native).
Patch/source changes invalidate the pinned input hashes and require an explicit
native rebuild. Packaging never silently falls back after a validation failure.

NativeBootstrap selects only the verified guarded resource, in a directory
named for its checksum. Existing administrator-installed v7 files are retained
but cannot silently bypass these checks. A guarded load failure disables the
native backend; it never falls back to an unchecked v7 library.

Upstream PowerGrid: Apache-2.0, copyright 2025 patryk3211.
SuperLU: BSD-3-Clause, University of California/Lawrence Berkeley National Lab.
OpenBLAS: BSD-3-Clause. Original licenses are retained with source archives and
included in the generated resource directory. Patches are WWPG modifications.
