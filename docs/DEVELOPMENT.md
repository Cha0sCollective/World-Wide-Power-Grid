# Development and acceptance

Use Java 21 and the pinned [artifacts](../release/artifacts.json). The ordinary build produces `build/libs/wwpg-0.2.0.jar`; native is primary. The optional profile adds the [locked Pinout/CC:Tweaked artifacts](../release/optional-artifacts.json).

```sh
./gradlew build --no-daemon
./gradlew runClient
./gradlew runServer
```

On Windows use `./gradlew.bat`. The build checks analytical equations, dependency/native hashes and the donor JNI interface. Use the published Minecraft 1.21.1 sources linked in the lock record; PG's generic v0.6.2 tag and newer CEE development APIs are different baselines.

## Packaged functional suite

BASE has 204 checks; PINOUT has 208. Run SETUP in a fresh directory, then VERIFY in three separate cold processes. Repeat for each backend/profile. Actual reload tests remove tickets and observe unload events, with delayed wire loading and saved state inspected before resumed solving.

```sh
python tools/run_checked.py -- ./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestProfile=BASE -PtestDirectory=run/accept-base-native -PrestartPhase=SETUP --no-daemon
python tools/run_checked.py -- ./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestProfile=BASE -PtestDirectory=run/accept-base-native -PrestartPhase=VERIFY --no-daemon
```

Switch to JAVA/PINOUT as needed. CI runs all eight Windows/Linux × backend/profile combinations, upstream and compatibility references, legacy examples, dedicated yards and three cold restarts of the exact extracted ZIPs. `run_checked.py` retains failed-process thread dumps and terminates only its owned processes. A timeout or retry is not acceptance.

```sh
./gradlew runUpstreamReferenceServer -PtestBackend=NATIVE --no-daemon
./gradlew runReferenceCompatibilityServer -PtestBackend=NATIVE --no-daemon
```

Reference jars and the disposable graphical helper are test-only and excluded from runtime downloads.

## Playable world and real clients

Build yards in dedicated `wwpg_yard` directories, not combined regression worlds. Exports require matching packaged proofs, typed behavior coverage, native Nether loading settings and safe native files. Reopen the exact ZIP before testing it.

```sh
./gradlew runPackagedGameTestServer -PtestNamespaces=wwpg_yard -PtestProfile=BASE -PtestDirectory=run/yard-base --no-daemon
python tools/export_stationary_yard.py --target 0.2.0 --run run/yard-base --jar build/libs/wwpg-0.2.0.jar --profile BASE --output build/distributions/yard-base.zip
python tools/reopen_stationary_yard.py build/distributions/yard-base.zip --run run/exact-yard-base
```

`tools/accept_stationary_clients.ps1` freezes a supplied jar and exports one yard per profile. `-ColdRestarts` requires three actual cold processes per solver. Graphical checks visit every station, interact with native menus/panels/computers and walk the portal. Multiplayer starts one dedicated server and two real TCP clients. Collectors require exact jar/ZIP hashes, actual checkpoints, all views and both clients.

```powershell
./tools/accept_stationary_clients.ps1 -Artifact wwpg-0.2.0.jar -Target 0.2.0 -Batch acceptance-label -Mode Both -ColdRestarts -Python python
```

Keep previous failed batches; corrections use fresh directories. Run only one local Gradle build/runtime at a time. Actual multiplayer JVMs launch from one prepared specification, avoiding overlapping Gradle-generated response files.

## Extension API acceptance

Build the API/source/Javadoc artifacts and both independent example projects as described in the [SDK guide](EXTENSION_API.md). Include `-PtestApiAddon=true` when running packaged extension fixtures. `wwpg_api`, `wwpg_api_faults`, `wwpg_api_restart`, `wwpg_api_upstream` and `wwpg_api_upstream_restart` run SETUP followed by three cold VERIFY processes. Run `wwpg_api_pause` separately. The stopped-before-ready regression uses a separate `wwpg_api_pending_restart` directory and three real processes in SETUP → ARM → VERIFY order; it must not be folded into the longer combined suite.

The [API Workshop guide](EXTENSION_API_WORKSHOP.md) gives its separate builder, export/reopen and graphical/two-client commands. Match every process to the same frozen source, runtime, SDK and example jars. Independently extract and build the exact downloadable example source and Maven ZIPs; a checkout example build is preliminary evidence.

## 0.2.0 release gate

The [API release manifest contract](EXTENSION_API.md#sdk-packaging-and-release-evidence) requires twelve locked SDK/runtime/example artifacts, honest first-release ABI snapshots, all eight CI jobs, two exact extracted-SDK builds, four accepted world ZIPs, eight graphical/two-client API records and retained failure closures. Counts come from executed records. The final progress record marks every stage complete only after its gate passes.

`tools/package_api_release.py --release` validates the locked manifest and assembles the bundle. Its default candidate mode cannot establish publication or API freeze. Keep earlier artifacts and failed attempts unchanged. [Final 0.2.0 verification](../release/0.2.0/verification.json) binds the accepted release.

## Historical beta.5 packaging

`tools/package_beta5.py --collect-ci RUN_ID --client-batch acceptance-label --build-log build/final-build.log` requires eight successful jobs with byte-identical final jars, correct process counts/phases, analytical checks, native/compatibility references, all exact-world cold passes and complete graphical/two-client evidence. Only after these gates does it derive `supported-content.json`, write final verification and assemble the jar, two world ZIPs, verification archive, release bundle and checksums. Historical candidate matrices and published downloads stay unchanged.

`tools/test_stationary_export.py` and `tools/test_beta5_package.py` reject incomplete native state, unsafe extraction and mismatched/missing acceptance. [Final release records](../release/0.1.0-beta.5/) retain causal controls and known attribution limits. Original packaging scripts remain available for reproducing their named historical versions; they are not 0.2.0 publication commands.
