# Development and testing

Use this guide to build WWPG and choose checks for a change. To write an add-on,
start with the [public API guide](EXTENSION_API.md). The detailed SDK manifest and
publication requirements are in [API release checks](API_RELEASE_CHECKS.md).

Run commands from the repository root with Java 21. On Windows, use
`./gradlew.bat` in place of `./gradlew`; `tools/run_checked.py` also handles the
Windows wrapper. Use a Python interpreter for the `python` commands.

## Build and launch locally

The [dependency lock](../release/artifacts.json) records the pinned upstream
artifacts. The optional `PINOUT` profile adds the
[locked Pinout and CC:Tweaked versions](../release/optional-artifacts.json).
The ordinary build produces `build/libs/wwpg-0.2.0.jar`.

The compile baseline remains NeoForge 21.1.231. Use `-Pneo_version=21.1.248`
to build or launch with that loader, including the independent examples.
[Version compatibility](VERSION_COMPATIBILITY.md) describes the runtime
declarations and the additional matrix checking a frozen jar on 21.1.248.

```sh
./gradlew build --no-daemon
./gradlew runClient
./gradlew runServer
```

Native is the primary solver backend; Java receives the same functional checks.
The build checks analytical equations, dependency and native-library hashes, and
the donor JNI interface. Use the published Minecraft 1.21.1 sources linked in the
lock record. PG's generic `v0.6.2` tag and newer CEE development APIs are different
baselines.

## Electrical lifecycle and persistence

`BASE` has 204 inherited packaged checks. `PINOUT` has 208, including the optional
integration. The API fixtures below add their own checks.

Choose a new run directory, run `SETUP` once, then run `VERIFY` three times. Each
command starts a separate server process; all three restart passes must succeed.
The example below uses the native backend and base installation:

```sh
python tools/run_checked.py -- ./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestProfile=BASE -PtestDirectory=run/accept-base-native -PrestartPhase=SETUP --no-daemon
python tools/run_checked.py -- ./gradlew runPackagedGameTestServer -PtestBackend=NATIVE -PtestProfile=BASE -PtestDirectory=run/accept-base-native -PrestartPhase=VERIFY --no-daemon
```

Repeat the second command twice more. Use separate directories for `JAVA` and
`PINOUT` runs. Actual chunk tests remove tickets and observe unload events; saved
state is checked before solving resumes, including delayed wire loading.

Keep failed attempts. After a correction, choose a fresh directory rather than
overwriting the failure. `run_checked.py` captures thread dumps on a timeout and
terminates its owned processes. A timeout or successful retry alone does not
establish a fix.

The [CI workflow](../.github/workflows/build.yml) runs all eight combinations of
Windows/Linux, native/Java, and base/Pinout. It also runs the reference circuits,
legacy example worlds, dedicated yards, API fixtures, and extracted-world restarts.

## Compare with upstream reference circuits

```sh
./gradlew runUpstreamReferenceServer -PtestBackend=NATIVE --no-daemon
./gradlew runReferenceCompatibilityServer -PtestBackend=NATIVE --no-daemon
```

Use `-PtestBackend=JAVA` for the other backend. Reference jars and the disposable
graphical helper are test-only and excluded from runtime downloads.

## Build and reopen a Stationary Test Yard

Use a dedicated `wwpg_yard` directory, separate from the regression worlds:

```sh
./gradlew runPackagedGameTestServer -PtestNamespaces=wwpg_yard -PtestProfile=BASE -PtestDirectory=run/yard-base --no-daemon
python tools/export_stationary_yard.py --target 0.2.0 --run run/yard-base --jar build/libs/wwpg-0.2.0.jar --profile BASE --output build/distributions/yard-base.zip
python tools/reopen_stationary_yard.py build/distributions/yard-base.zip --run run/exact-yard-base
```

The exporter requires matching packaged proofs, coverage records for the claimed
behaviors, native Nether chunk-loading settings, and safe native files. Verify
the extracted save with three cold `wwpg_yard` `VERIFY` runs. An acceptance pass
on the construction save does not prove the downloadable ZIP survived export.
Use the same profile, backend, and frozen jar throughout.

The [Stationary Yard guide](STATIONARY_YARD.md) explains player operation. Current
graphical and two-client API checks use the runner described below.

## Build both extension examples

Publish the SDK to the local build repository, then build each standalone project:

```sh
./gradlew build publishAllPublicationsToSdkRepository --no-daemon
./gradlew -p examples/extension-addon build --no-daemon
./gradlew -p examples/cee-addon build --no-daemon
```

The [general example](../examples/extension-addon/README.md) uses the general API.
The [upstream example](../examples/cee-addon/README.md) uses the CEE property API
and PG's native board system. Neither compiles against WWPG implementation classes.
For an independent build from downloads, use each example's own wrapper and the
extracted Maven ZIP. A build from this checkout is preliminary evidence for the
SDK archive gate.

## Run the extension fixtures

Add `-PtestApiAddon=true` so the packaged server loads both built example jars.
Run the five main namespaces together, then repeat `VERIFY` three times as for the
regression suite:

```sh
python tools/run_checked.py -- ./gradlew runPackagedGameTestServer -PtestProfile=BASE -PtestBackend=NATIVE -PtestApiAddon=true -PtestNamespaces=wwpg_api,wwpg_api_faults,wwpg_api_restart,wwpg_api_upstream,wwpg_api_upstream_restart -PtestDirectory=run/api-base-native -PrestartPhase=SETUP --no-daemon
python tools/run_checked.py -- ./gradlew runPackagedGameTestServer -PtestProfile=BASE -PtestBackend=NATIVE -PtestApiAddon=true -PtestNamespaces=wwpg_api,wwpg_api_faults,wwpg_api_restart,wwpg_api_upstream,wwpg_api_upstream_restart -PtestDirectory=run/api-base-native -PrestartPhase=VERIFY --no-daemon
```

Run `wwpg_api_pause` separately, with its own directory. The stopped-before-ready
regression also needs a separate `wwpg_api_pending_restart` directory and three
real processes in **SETUP → ARM → VERIFY** order. Keep each phase's log before
the next launch. An ordinary restart test cannot replace that sequence.

## Check the playable API Workshop with real clients

The [API Workshop guide](EXTENSION_API_WORKSHOP.md) covers building, exporting,
reopening, and operating the example save. Its Windows runner starts graphical
and real two-client sessions against the five frozen runtime/API/example jars:

```powershell
./tools/accept_api_clients.ps1 -Batch api-acceptance -SourceSha <frozen-commit> -Mode Both -ColdRestarts -Python python
```

Replace `<frozen-commit>` with the full 40-character source SHA. Build and resolve
the dependencies first; the runner uses offline launches. Use a new batch name
after a failed attempt. It records the exact world ZIP and artifact hashes for
each profile and backend. Graphical checks exercise native tools and menus;
multiplayer checks use a dedicated server and two real TCP clients.

Run one local Gradle build or runtime preparation at a time. The multiplayer JVMs
launch from one prepared specification, avoiding overlapping Gradle-generated
response files. Match every process to the same frozen source and jars.

## Verify and package a release

[API release checks](API_RELEASE_CHECKS.md) defines the complete gate: twelve
locked SDK/runtime/example artifacts, first-release classfile snapshots, eight
CI jobs, two exact extracted-SDK builds, four accepted world ZIPs, eight
graphical/two-client API records, and retained failure closures. Counts come from
executed records. The progress record marks a stage complete only after its gate
passes.

`tools/package_api_release.py --release` validates the locked manifest and
assembles the bundle. Its default candidate mode does not establish publication
or API freeze. Keep earlier artifacts and failed attempts unchanged.
[Final 0.2.0 verification](../release/0.2.0/verification.json) binds the accepted release.

Follow [Publishing a release](RELEASING.md) to turn that accepted bundle into
the five public downloads, generate short notes, and upload a verified GitHub draft.
Use the release tools for future publications so SDK files and test records
stay inside their bundles.

## Historical beta.5 tooling

`tools/accept_stationary_clients.ps1` is the earlier stationary-client runner.
It refers to the local `build/pinout-native-controls.init.gradle` helper and
writes evidence under the beta.5 directory. It is not a self-contained current
release command from a clean checkout. The current API runner is documented above.

`tools/package_beta5.py --collect-ci RUN_ID --client-batch acceptance-label --build-log build/final-build.log`
requires eight successful jobs with byte-identical final jars, correct process
counts/phases, analytical checks, native/compatibility references, all exact-world
cold passes, and complete graphical/two-client evidence. After those gates, it
derives `supported-content.json`, writes final verification, and assembles the
jar, two world ZIPs, verification archive, release bundle, and checksums.

`tools/test_stationary_export.py` and `tools/test_beta5_package.py` reject
incomplete native state, unsafe extraction, and mismatched or missing acceptance.
[Beta.5 release records](../release/0.1.0-beta.5/) retain causal controls and known
attribution limits. Historical packaging scripts reproduce their named versions;
they are not 0.2.0 publication commands.
