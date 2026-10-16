# Guarded native solver development

This describes the **beta.4 preparation's unload/native safety change**, not the
binaries in published 0.2.2-beta.3. Public API 1.0.0, save formats, electrical equations,
precision settings and the sixteen-substep schedule are unchanged.

When a PG device pauses or unloads, WWPG captures the owners of its internal
connections and retained external junctions before PG detaches them. It queues
PG's existing island discovery afterward. Disconnected circuits can then receive
separate solver owners. Client/Ponder behaviour stays on its existing path, and
shutdown does not create world network data merely to queue retired owners.

The guarded native build checks SuperLU factorization and solve statuses and
validates row/column permutations for range and uniqueness before use, including
before inverse-permutation reuse during refactorization. Failed factors are
discarded; partial factor allocations have a defined cleanup owner. It checks
every residual/state row for non-finite numbers. Failure restores both native
state buffers to their substep entry values, leaves acceptance false and raises
a Java exception. WWPG's existing failure boundary retains diagnostics and holds
CEE/addon storage and measurement availability while unrelated circuits run.
Both backends clear stale convergence and restore update counting when a callback
throws. PG batteries and FE inverters hold tick-side energy and filter history
while their owning circuit is unaccepted; retained finite recovery values do not
count as a fresh gameplay result. Client/Ponder ticking is unaffected.
These checks prevent the identified unsafe paths; they are not a promise that
arbitrary native corruption or every out-of-memory condition is recoverable.

## Sources and loading

[tools/native/sources.lock.json](../tools/native/sources.lock.json) pins the PG,
SuperLU and OpenBLAS revisions and archive hashes. WWPG modifications are the
two adjacent safety patches; upstream licenses are copied into the native
resource bundle. Test fault-injection wrappers are linked only into the
standalone test executable, never the mod's library.

The loader verifies and selects the guarded resource by SHA256 under
`.pg-native/wwpg-safety-1/<sha256>/`. An existing administrator-installed v7 file
is preserved. A missing, corrupt or unsupported guarded library disables native
support and reports an error; it cannot silently select the unchecked file.
Original pinned resources remain available in the jar for provenance.

## Build and focused verification

Use a POSIX build environment with CMake, a GNU-compatible C compiler and JNI
headers. Windows cross-builds require MinGW and Windows JNI platform headers.
Put the checked source ZIP archives in an archive directory. Downloads occur
only with the builder's explicit `--download` option.

```text
python tools/build_native_solver.py --platform linux --archives <archives> --work <work> --jni-include <jdk>/include
python tools/build_native_solver.py --platform windows --archives <archives> --work <work> --jni-include <jdk>/include --jni-platform-include <windows-jdk>/include/win32 --cc <mingw-gcc> --ar <mingw-ar> --ranlib <mingw-ranlib>
```

The output is `build/guarded-pg-native`, with a manifest binding each library to
its exact build inputs. Java packaging verifies both platforms against those
inputs and refuses stale output. Native compilation and full suites are never
started implicitly by these preparation tasks.

Run the generated `sparse_safety_tests` executable on its target platform. It
checks singular matrices, reported allocation/factorization failures, invalid
permutations, cached-factor reuse, safe cleanup and valid recovery. Run focused
JUnit checks for `NativeBootstrapTest` and `NativeCeeCheckpointTest` when changing
loading or rollback.

For a frozen jar, select only the affected GameTest namespaces:

```text
./gradlew runPackagedGameTestServer -x build -x jar -x test -PtestArtifact=<jar> -PtestBackend=NATIVE -PtestApiAddon=true -PtestNamespaces=wwpg_unload,wwpg_solver_safety,wwpg_native_guard
./gradlew runPackagedGameTestServer -x build -x jar -x test -PtestArtifact=<jar> -PtestBackend=JAVA -PtestApiAddon=true -PtestNamespaces=wwpg_unload,wwpg_solver_safety
```

These checks exercise powered/passive split and reload, all sixteen changing
inputs, true rejection and recovery, PG/CEE/addon storage, unavailable readings and
unrelated healthy circuits. They do not substitute for explicitly requested full
acceptance or establish large-yard performance. Preserve failed attempts and
verify the actual loaded module hash in saved-world diagnostics.
