# Developing WWPG

Start with [beta scope and acceptance](FIRST_RELEASE.md), [current status](STATUS.md), and the [contribution guide](../CONTRIBUTING.md). [SPEC.md](../SPEC.md) defines the long-term product; the beta supports the declared stationary content in the [matrix](../release/content-matrix.json).

## Build and launch

Use a Java 21 JDK. On Windows, use `gradlew.bat` in place of `./gradlew`.

```sh
./gradlew build --no-daemon
./gradlew runClient
./gradlew runServer
```

The normal jar is `build/libs/wwpg-0.1.0-beta.2.jar`. The build runs four equation tests and verifies locked upstream artifacts, native binary hashes, and the JNI interface.

The default runtime uses the full published Create 6.0.10-280 jar. `-PpublishedRuntime=false` selects the optional slim development artifact; its Create classes are byte-identical, with fixed Ponder, Flywheel, and Registrate versions matching the full jar's bundled libraries.

Published checksums and matching source references are in [artifacts.json](../release/artifacts.json). CEE's development branch has different APIs, and PG's generic `v0.6.2` tag targets Minecraft 1.20.1. Use the pinned Minecraft 1.21.1 sources when reviewing this implementation.

## Packaged regression suite

The 117 GameTests use real upstream devices, item interactions, and server hooks. Electrical checks cover polarity, grounding, currents, transient state, RMS/phase, transformer relationships, and nonlinear electronics. Gameplay checks cover outputs, assembly, controls, protection, repair, and failures. Three handheld-meter fixtures were added after beta.1; the real-client fixture additionally checks targeting, item packets, and displayed readings.

Run SETUP and VERIFY in separate processes using the same isolated test directory:

```sh
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PrestartPhase=SETUP -PtestDirectory=run/release-validation --no-daemon
./gradlew runPackagedGameTestServer -PtestBackend=JAVA -PrestartPhase=VERIFY -PtestDirectory=run/release-validation --no-daemon
```

Use either `NATIVE` or `JAVA` for both phases to reproduce a CI backend job. The commands above also check reopening a native-created world with Java. Tests configure 16 PG substeps, assert the actual backend, and reject independent CEE solves.

Packaged tasks load WWPG exclusively from its jar. A successful process exit is insufficient: each task requires a completed, nonempty passing suite. This catches NeoForge loading failures that can exit with code zero.

Reserved chunks `(64,64)` and `(96,96)` check restart persistence for wires, settings, capacitor history, panel IDs, and board UUID/charge. Chunk `(128,128)` unloads and reloads twice, with unload events checked. Another test isolates matching coordinates in the Overworld and Nether. A configuration test queues 100 background-thread reload requests.

**Known issue:** `chunkReloadRebindsMixedEndpoints` has failed once in Linux/native CI, reading 0 V instead of 10 V after reload. It uses fixed tick deadlines while entity loading is asynchronous. That makes timing a candidate cause; instrumentation is still needed to establish whether the issue is in the fixture or compatibility lifecycle. See [the failure record](STATUS.md#unresolved-chunk-reload-failure).

## Upstream reference worlds

Reference tests first use the upstream solvers with only the missing-native-resource repair, then reopen the saved circuits with full compatibility:

```sh
./gradlew runUpstreamReferenceServer -PtestBackend=NATIVE --no-daemon
./gradlew runReferenceCompatibilityServer -PtestBackend=NATIVE --no-daemon
```

Both use `run/reference`. The `*-upstream-reference-tests.jar` is test-only; exclude it from installations and release downloads.

## Example world

Build, restart, and export the example separately from the regression world:

```sh
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestNamespaces=wwpg_example -PtestDirectory=run/example --no-daemon
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestNamespaces=wwpg_example -PtestDirectory=run/example -PrestartPhase=VERIFY --no-daemon
python tools/export_example.py --output build/distributions/wwpg-0.1.0-beta.2-example.zip
```

The ZIP is written to `build/distributions`. It contains the small demonstration described in [INSTALL.md](INSTALL.md#example-world). The exporter creates a deterministic archive of the supplied saved world; generating a fresh Minecraft world can produce a different seed and artifact hash.

## Two real clients

Prepare once, then run the last three commands in separate terminals. Keep the jar unchanged while the processes use it.

```sh
./gradlew build prepareClientARun prepareClientBRun --no-daemon
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestNamespaces=wwpg_multiplayer -Pmultiplayer=true -PtestDirectory=run/multiplayer -x build -x compileJava -x processResources -x createMinecraftArtifacts --no-daemon
./gradlew runPackagedClientA -x build -x compileJava -x processResources -x createMinecraftArtifacts --no-daemon
./gradlew runPackagedClientB -x build -x compileJava -x processResources -x createMinecraftArtifacts --no-daemon
```

The opt-in GameTest server binds to `127.0.0.1:25575`, uses offline authentication for two test players, and runs with wall-clock pacing. These settings apply to that test instance. Keep it on loopback.

The real clients use native wire/settings packets, check synchronized readings and rendered wire data, then close. Graphics support is required. Local Linux validation used Ubuntu 24.04/WSLg with Mesa software rendering. This check is separate from CI's headless server tests.

## Native resource handling

The pinned PG 0.6.2 artifact omitted native v7 resources. WWPG bundles checksum-locked binaries from official PG 0.6.1 after verifying byte-identical `NativeMNA.class` interfaces. The binary-source mod jar is excluded from the runtime.

`verifyUpstreamArtifacts` checks jars, JNI compatibility, and both binaries. `processResources` packages the DLL/SO and their license/notice. WWPG installs a verified missing binary before PG's normal loader and preserves existing files. Native acceptance fails if native did not load. See [platform requirements](INSTALL.md#native-solver-requirements).

## Electrical lifecycle and persistence

CEE preparation → topology/parameter updates → PG substeps → CEE-compatible results → native gameplay commit runs synchronously. Stable endpoint and branch identities retain PG objects on unchanged topology. CEE dynamic state advances once per PG substep. The result bridge retains voltage history and directed currents; meter and motor sampling use PG's clock.

Unknown or invalid CEE models are diagnosed and isolated with zero results. Devices awaiting chunk entity loading are paused to preserve stored energy. Native mods retain their persistence; WWPG reconstructs electrical elements instead of saving temporary graph ordinals. Versioned `wwpg_removed_wires` data records removed PG wire UUIDs so unloaded wire entities cannot restore cut connections. Configuration-watcher updates are queued onto the server thread.

Use `/wwpg`, `/wwpg errors`, and `/wwpg at x y z` for diagnostics. Upstream integration uses targeted Mixins and accessors; see [the one-page overview](UPSTREAM_OVERVIEW.md) for reviewer context and [SPEC.md](../SPEC.md) for the intended ownership boundaries.

## Release records and packaging

[release_acceptance.py](../tools/release_acceptance.py) maps declared behaviors to electrical and shared lifecycle tests. [inventory_release.py](../tools/inventory_release.py) regenerates the pinned inventory without marking it verified. Registry presence alone is insufficient evidence.

[verify_release.py](../tools/verify_release.py) requires the named completed acceptance logs and equation results before `release_acceptance.py --freeze` marks the matrix verified. Rebuild after freezing. [package_release.py](../tools/package_release.py) checks evidence hashes, frozen claims, and byte-identical Windows/Linux jars, then assembles the delivery bundle, verification archive, and `SHA256SUMS.txt`. It excludes the test-only reference jar.

These scripts encode the original beta-preparation workflow and local log paths. [release/README.md](../release/README.md) explains the historical records. Update the workflow deliberately for future releases; preserve published artifact hashes and evidence. Current publication and CI status belong in [STATUS.md](STATUS.md).
