# Developing WWPG

The first release target is **0.1.0-beta.1**, with stationary factory interoperability. `SPEC.md` remains the long-term specification. The staged release gates are in [FIRST_RELEASE.md](FIRST_RELEASE.md); partial fixtures do not certify the entire supported-content matrix.

## Fixed baseline

| Dependency | Version |
| --- | --- |
| Java | 21 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.231 |
| Create | 6.0.10-280 |
| CEE | 1.21.1-1.1.3 |
| Power Grid | 0.6.2 |
| Ponder | 1.0.82+mc1.21.1 |
| Flywheel | 1.0.6 |
| Architectury | 13.0.8 |

Published artifact URLs, SHA-256 hashes and source references are recorded in [artifacts.json](../release/artifacts.json). `verifyUpstreamArtifacts` verifies the release jars and native donor interface. Neither upstream development HEAD nor PG's generic `v0.6.2` tag is the source baseline.

## Build and run

Set `JAVA_HOME` to a Java 21 JDK. On Windows use `gradlew.bat` for these commands; on Linux use `./gradlew`.

```sh
./gradlew build
./gradlew runGameTestServer -PtestBackend=NATIVE
./gradlew runGameTestServer -PtestBackend=JAVA
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PrestartPhase=SETUP
./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PrestartPhase=VERIFY
./gradlew runClient
./gradlew runServer
```

The jar is written to `build/libs/wwpg-0.1.0-beta.1.jar`. GameTests run the actual upstream jars and Minecraft server hooks. JUnit tests check linear and transformer polarity/current equations. GameTests use sixteen PG substeps, check the actual selected backend and reject any CEE solver invocation. Ordinary gameplay uses PG's `multiTicks` setting; configure sixteen substeps when testing AC/RMS behavior. CEE's independent `microTicks` setting does not control the compatibility simulation.

`runPackagedGameTestServer` loads WWPG exclusively from the built jar and asserts that the mod file is a jar. Run `SETUP` and then `VERIFY` in separate processes using the same `run/world`; the second run checks both saved wire systems, persisted native settings, capacitor history and stable restored stamps. These fixtures reserve chunks (64,64) and (128,128) in the test world. Use the development test directory for them.

Both GameTest tasks also require the current server process to report a completed passing suite. A zero process exit after a NeoForge mod-loading failure cannot pass this check. The current suite contains thirty fixtures; Windows native `SETUP` and Java `VERIFY` have passed, alongside the four electrical equation tests.

Launch tasks install native binaries in `run/.pg-native`. The game directory and test logs are local and ignored by Git. CI runs packaged GameTests and restart verification on Windows and Linux with both backends. A checked-in CI workflow is not evidence that its remote jobs have already passed.

## PG native binary workaround

The published Minecraft 1.21.1 PG 0.6.2 jar omitted native v7 binaries. The published 0.6.1 jar contains them and has a byte-identical `NativeMNA.class`. WWPG's development tasks extract only those binaries from the checksum-locked donor. **The running PG mod remains 0.6.2.** The donor mod jar is never put on the runtime mod path, and WWPG does not bundle native binaries in its jar.

PG loads the platform binary from `.pg-native` in the game/server working directory. Existing matching v7 binaries can be reused. `installPgNative` creates the development copies; the Windows DLL and Linux SO are also available under `build/pg-native-resources/native/`. Preserve that filename when installing the appropriate binary on a test server. PG performs its own native platform/support check and retains its Java fallback. Native GameTests fail if the requested backend cannot load.

## Internal lifecycle

1. CEE prepares its normal devices and wire description on the server thread.
2. WWPG compares stable endpoint/branch identities and updates persistent PG objects. Changed native PG endpoints are rebound after device edits.
3. PG discovers islands and selects references in the combined topology. Explicit physical grounds retain their native electrical models.
4. PG advances all substeps. CEE dynamic models prepare and consume each substep once, including three-phase models spanning separate networks.
5. WWPG produces CEE voltage history and directed currents. CEE commits its normal gameplay, protection, damage and synchronization paths.

Unknown nodes/properties are diagnosed and disconnected from the compatibility graph. Their CEE result description is zeroed; no second solver or old voltage history is used. CEE and PG own their normal world/device/wire persistence. WWPG reconstructs stamps from those saved descriptions. Its versioned `wwpg_removed_wires` SavedData retains deleted PG wire UUIDs when an endpoint disappears before the wire entity loads; this prevents saved entities from resurrecting removed connections. Solver indexes are never persisted.

Use `/wwpg` for phase, topology changes, substep/backend counts and CEE solve attempts; `/wwpg errors` lists bounded recent diagnostics, and `/wwpg at x y z` shows terminal mappings, voltage, backend and isolation state.

PG's portable battery retains its native behavior: its placed electrical input charges an energy item. It does not discharge as a voltage source into wires. WWPG feeds that input with solved CEE power and adds no FE bridge. The CEE accumulator provides reversible circuit storage.

Targeted corrections are applied to the pinned APIs: CEE device removal immediately cuts attached PG wires, including remove/replacement within one tick; CEE 1.1.3's variac loss calculation resolves terminal IDs relative to its own position rather than as global graph indexes; its three-pole panel meter reads the panel's assigned terminal IDs and uses the consumed-energy sign from the single-phase meter. Transformer winding currents include simultaneous leakage current before RMS aggregation, preserving their AC phase relationship.

Panel fixtures exercise survival attachment insertion and item consumption, emergency-stop/reset and momentary player interactions, configured breaker insertion/trip/reset, ammeter readings and both panel energy-meter timesteps/disconnect. Alternator fixtures use actual rotor/stator/brush assemblies driven by a configured Create motor, including speed changes, stopping, balanced three-phase RMS and reversal of phase sequence. These checks do not certify every attachment, machine configuration or multiplayer workflow.

## Content inventory

[content-matrix.json](../release/content-matrix.json) records the bounded release inventory and acceptance circuits. Colored motor/panel variants share behavior groups. Optional CEE sensor attachments and moving solar bearings are outside this release.

The inventory generator checks block assets against the pinned jar, and the registry GameTest checks every declared block, attachment, board component and cosmetic variant against the actual loaded registries. Run the generator only for an intentional inventory update:

```sh
python tools/inventory_release.py --cee-source <pinned-cee-source> --pg-source <pinned-pg-source> --jars <published-jar-directory>
```

The matrix remains `unverified` until the full declared electrical, gameplay and lifecycle checks pass on the packaged jar. A passing source/resistor/diode fixture is evidence for that fixture, not certification of every upstream item.
