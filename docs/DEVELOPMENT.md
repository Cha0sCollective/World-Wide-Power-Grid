# Developing WWPG

The bounded stationary release is in [FIRST_RELEASE.md](FIRST_RELEASE.md); [SPEC.md](../SPEC.md) remains the long-term specification. Exact versions, published artifact checksums and source references are in [artifacts.json](../release/artifacts.json). Do not substitute upstream development HEAD or PG's generic Minecraft 1.20.1 `v0.6.2` tag.

Use a Java 21 JDK. On Windows substitute `gradlew.bat` for `./gradlew`. The default build uses the full published Create 6.0.10-280 jar. `-PpublishedRuntime=false` selects its byte-identical slim artifact; fixed Ponder, Flywheel and Registrate match the full jar's bundled versions.

```sh
./gradlew build --no-daemon
./gradlew runClient
./gradlew runServer
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PrestartPhase=SETUP -PtestDirectory=run/release-validation --no-daemon
./gradlew runPackagedGameTestServer -PtestBackend=JAVA -PrestartPhase=VERIFY -PtestDirectory=run/release-validation --no-daemon
```

The jar is `build/libs/wwpg-0.1.0-beta.1.jar`. Four JUnit tests check linear/transformer equations. The 114 GameTests use real upstream devices, item interactions and server hooks. Analytical checks cover polarity/current, grounds, charge history, RMS/phase, transformer relationships and nonlinear electronics; gameplay checks cover outputs, assembly, configuration and failure/repair. Tests use 16 PG substeps, assert the requested backend actually solved, and reject any independent CEE solve. A regression dispatches 100 configuration reload requests from a background thread.

Packaged tasks load WWPG exclusively from its jar and require a completed, nonempty passing suite, even when NeoForge exits zero after a loading failure. Run SETUP then VERIFY in separate processes with the same test directory. Reserved chunks (64,64) and (96,96) check saved wires, settings, capacitor history, panel terminal IDs and board UUID/charge. Chunk (128,128) actually unloads/reloads twice, with unload events checked. A separate fixture checks identical coordinates in the Overworld and Nether. Use isolated test worlds.

Reference fixtures first use the upstream solvers with only the missing-native-resource repair, then reopen the saved circuits with full compatibility:

```sh
./gradlew runUpstreamReferenceServer -PtestBackend=NATIVE --no-daemon
./gradlew runReferenceCompatibilityServer -PtestBackend=NATIVE --no-daemon
```

They use `run/reference`. The `*-upstream-reference-tests.jar` is test-only and must not be distributed as WWPG. Build/export the example separately:

```sh
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestNamespaces=wwpg_example -PtestDirectory=run/example --no-daemon
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestNamespaces=wwpg_example -PtestDirectory=run/example -PrestartPhase=VERIFY --no-daemon
python tools/export_example.py
```

For the opt-in multiplayer check, prepare once, then run the last three commands in separate terminals. Keep the jar immutable while the processes use it.

```sh
./gradlew build prepareClientARun prepareClientBRun --no-daemon
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestNamespaces=wwpg_multiplayer -Pmultiplayer=true -x build -x compileJava -x processResources -x createMinecraftArtifacts --no-daemon
./gradlew runPackagedClientA -x build -x compileJava -x processResources -x createMinecraftArtifacts --no-daemon
./gradlew runPackagedClientB -x build -x compileJava -x processResources -x createMinecraftArtifacts --no-daemon
```

This test-only offline-auth TCP server binds to `127.0.0.1:25575`, permits two players and runs with wall-clock pacing. It changes no normal server authorization or EULA settings. Real Minecraft clients use native wire/configuration packets, verify synchronized readings/settings and rendered wire data, then close. Linux validation uses Ubuntu 24.04/WSLg with Mesa software rendering. Working graphics are required. Keep this test server on loopback.

## Native solver

PG 0.6.2 omitted native v7 resources. WWPG bundles the checksum-locked DLL/SO from official PG 0.6.1; the JNI class is byte-identical. The donor mod never enters the runtime mod path. `verifyUpstreamArtifacts` checks published jars, the JNI interface and both binaries. `processResources` packages binaries and their Apache license/notice. WWPG installs a verified missing binary before PG's normal loader and preserves existing files. Native acceptance fails if the requested backend cannot load. See [INSTALL.md](INSTALL.md) for requirements.

## Lifecycle and persistence

CEE preparation → topology/parameter updates → PG substeps → CEE-compatible results → native gameplay commit runs synchronously. Stable endpoint/branch identities retain PG objects on unchanged topology. CEE dynamic state advances once per PG substep, including three-phase windings across networks. Results preserve voltage history and directed currents; winding leakage is combined before RMS aggregation. Meter and motor history use PG's clock. Configuration watcher updates are queued onto the server thread.

Unknown/invalid CEE constructs are diagnosed and isolated with zero results; no second solver or stale history is used. Devices awaiting chunk entity load are paused to preserve stored energy. Native mods keep their own persistence; WWPG reconstructs stamps instead of saving graph ordinals. Versioned `wwpg_removed_wires` data remembers removed PG wire UUIDs so unloaded entities cannot resurrect cut connections.

Use `/wwpg`, `/wwpg errors` and `/wwpg at x y z` for bounded diagnostics. The matrix distinguishes declared behavior from additional unclaimed content. [release_acceptance.py](../tools/release_acceptance.py) maps claims to behavior and shared lifecycle fixtures; [inventory_release.py](../tools/inventory_release.py) regenerates the pinned inventory without certifying it. Registry presence alone is not support evidence.

CI checks Windows/Linux × native/Java, separate restart processes, reference worlds and the example. A workflow does not establish that its remote jobs passed. Local evidence is recorded in [verification.json](../release/verification.json). Ordinary stationary factories define this beta's scope; large-network and moving-system certification are deferred.

For release preparation, `tools/verify_release.py` requires the named completed acceptance logs and equation results before `tools/release_acceptance.py --freeze` certifies the matrix. Rebuild after freezing. `tools/package_release.py` checks current evidence hashes, the frozen claims and byte-identical Windows/Linux jars, then assembles the delivery bundle, verification archive and `SHA256SUMS.txt`. It excludes the test-only upstream reference jar. See [distribution.json](../release/distribution.json) for the prepared artifact hashes.
