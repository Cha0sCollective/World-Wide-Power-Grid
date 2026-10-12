# Extension API release checks

This guide is for maintainers building and verifying a WWPG release. Add-on
developers should start with the [public API contract](EXTENSION_API.md) and the
two [example projects](../examples/extension-addon/README.md) and
[upstream example](../examples/cee-addon/README.md).

WWPG 0.2.1 and extension API 1.0.0 are published. The
[final verification record](../release/0.2.1/verification.json) identifies the
accepted artifacts and evidence; the [original acceptance plan](EXTENSION_API_PLAN.md)
preserves the release requirements. The commands below reproduce those checks.
Use fresh output paths for a new attempt and keep earlier records unchanged.

## Release sequence

1. Build the runtime, both API artifacts, and both independent examples.
2. Snapshot the public classfile contracts and export the Maven and example source ZIPs.
3. Freeze the source commit and artifact hashes. Build the exact SDK ZIPs after extraction.
4. Verify the complete CI matrix, exported worlds, graphical clients, and real multiplayer sessions against those artifacts.
5. Review failed attempts and lock the completed progress and verification records.
6. Run the strict release packager, then use the [release process](RELEASING.md)
   to stage the five downloads, review the short notes and publish a verified draft.
   Publish only after every required gate passes.

The [development guide](DEVELOPMENT.md) describes local builds and test runs.
This document keeps the detailed manifest requirements used by the release tools.

## Export the independent examples

The two independent example projects are also distributed as source ZIPs with
their own wrappers and MIT license. They exclude Gradle caches and build outputs.
The Unix wrapper retains its executable permission. Generate these archives with:

```text
python tools/package_api_release.py --example-source examples/extension-addon --output build/distributions/wwpg-api-example-1.0.0-source.zip
python tools/package_api_release.py --example-source examples/cee-addon --output build/distributions/wwpg-upstream-api-example-1.0.0-source.zip
```

Building an example after extracting its source ZIP and the public Maven ZIP
requires no checkout of the WWPG runtime source.

## Snapshot and compare API contracts

The first API release has no previous public API artifact. Establish an honest
contract snapshot:

```text
python tools/verify_api_compatibility.py build/libs/wwpg-api-1.0.0.jar --api-version 1.0.0 --output build/api-contract.json
python tools/verify_api_compatibility.py build/libs/wwpg-cee-api-1.0.0.jar --prefix org.cha0scollective.wwpg.api.cee --api-version 1.0.0 --output build/cee-api-contract.json
```

For a later compatible release, compare with the actual earlier published JAR:

```text
python tools/verify_api_compatibility.py CURRENT.jar --previous PREVIOUS-PUBLISHED.jar --api-version NEXT_VERSION --output build/api-compatibility.json
```

The tool checks exported type/member descriptors, visibility, static dispatch,
final/abstract constraints, generic signatures, declared exceptions, inheritance,
sealed type contracts, Java target, and constants. Private/package-private
implementation changes are ignored. This conservative classfile check does not
prove electrical semantics, interface default-method conflict resolution, or
gameplay behavior. Run the previous independently compiled add-on as well.

## SDK packaging and release evidence

`tools/package_api_release.py` assembles an SDK candidate from immutable artifact
descriptors. It checks the runtime's API bytes against the compile-time artifact,
source/Javadoc completeness, a packaged independent example, and exact Maven
repository contents. Its default output explicitly says `sdk_candidate` and
`publishable: false`.

To prepare the separately downloadable Maven asset from the Gradle publication,
without asserting release readiness:

```text
./gradlew publishAllPublicationsToSdkRepository --no-daemon
python tools/package_api_release.py --maven-source build/maven-repository --output build/distributions/wwpg-api-1.0.0-maven.zip
```

CI builds both standalone example projects against that local publication before
running their packaged fixtures. The archive command alone does not freeze the
API or permit a public release.

The final SDK gate builds the exact downloadable source ZIPs after extraction,
using the exact extracted Maven ZIP. Run it on the frozen artifacts:

```text
python tools/verify_extracted_api_sdk.py --runtime build/libs/wwpg-0.2.1.jar --api build/libs/wwpg-api-1.0.0.jar --cee-api build/libs/wwpg-cee-api-1.0.0.jar --example examples/extension-addon/build/libs/wwpg-api-example-1.0.0.jar --upstream-example examples/cee-addon/build/libs/wwpg-upstream-api-example-1.0.0.jar --maven build/distributions/wwpg-api-1.0.0-maven.zip --example-source build/distributions/wwpg-api-example-1.0.0-source.zip --upstream-source build/distributions/wwpg-upstream-api-example-1.0.0-source.zip --source-sha FROZEN_COMMIT_SHA --output build/extracted-sdk-FROZEN_COMMIT_SHA
```

This runs each extracted project's own wrapper, records its resolved compile-time
API hashes and build log, and requires byte-for-byte agreement with the frozen
example jars. The isolated projects do not use the main WWPG source tree.
Each attempt uses a new output directory; a failed attempt remains inspectable.

Pass `--release` only when the frozen manifest includes completed acceptance:

```text
python tools/package_api_release.py --manifest release/0.2.1/verification.json --output build/distributions/wwpg-0.2.1-sdk-candidate.zip
python tools/package_api_release.py --manifest release/0.2.1/verification.json --output build/distributions/wwpg-0.2.1-release.zip --release
```

### Manifest fields and artifact roles

The input format has `schema: 1`, `wwpg_version: "0.2.1"`,
`api_version: "1.0.0"`, and
`api_coordinate: "org.cha0scollective.wwpg:wwpg-api:1.0.0"`.
It also supplies
`cee_api_coordinate: "org.cha0scollective.wwpg:wwpg-cee-api:1.0.0"`.
Each file descriptor is `{ "path": "workspace/relative/file", "sha256": "...", "bytes": 123 }`.
Paths must remain within the selected workspace root. Existing output ZIPs are
never overwritten.

Required `artifacts` roles are `runtime`, `api`, `api_sources`, `api_javadoc`,
`cee_api`, `cee_api_sources`, `cee_api_javadoc`, `example`, `example_sources`,
`upstream_example`, `upstream_example_sources`, and `maven_repository`.
The upstream example supplies the panel/board integration in a separate
standalone project. `abi` and `cee_abi` point to the separate classfile
snapshot/comparison JSONs. A comparison also requires `previous_api` or
`previous_cee_api`, respectively, pointing to the genuine previous artifact.

A release additionally supplies:

- `source_sha`, the frozen 40-character Git commit; `stages_complete: [1,2,3,4,5,6]`;
  `unresolved_failures: []`.
- `required_regression_checks` for `BASE` and `PINOUT`, preserving at least the
  previous release's 204 and 208 packaged requirements.
- A locked `ci` JSON with the successful repository run URL, source SHA, and
  exactly eight platform/backend/profile jobs. Every job has a locked `proof`.
  The run and job proofs share the actual `source_tree`. Each job locks its
  `checked_out_source` record containing actual Git HEAD/tree, run ID, attempt,
  and event; pull-request merge commits cannot stand in for the frozen source.
- `independent_example_build`, a locked proof that the external Gradle project
  compiled against the API artifact without WWPG project implementation classes.
- `independent_upstream_example_build`, the corresponding proof for the separate
  CEE property/panel and native PG board example.
- Eight locked `client_acceptance` proofs: both profiles and backends, graphical
  and actual two-client multiplayer.
- Locked `base_world` and `pinout_world` descriptors for the exact accepted ZIPs.
- Locked `base_workshop_world` and `pinout_workshop_world` descriptors for the
  separate accepted API Workshop ZIPs. API client proofs bind to these archives.

### Required simulation and lifecycle evidence

Every acceptance proof must declare `completed: true`, `passed: true`, the frozen
`source_sha`, `artifact_hashes` for `runtime`, `api`, `cee_api`, `example`, and
`upstream_example`, and no
`unresolved_failures`. Matrix proofs identify platform/backend/profile, passing
regression/extension counts, at least three cold passes and five actual chunk
cycles, zero independent CEE solves, exactly-once electrical history, and a
passing runtime CEE-property fixture (`cee_property_adapter_verified: true`).
They list all passing `api_namespaces_passed`: `wwpg_api`, `wwpg_api_faults`,
`wwpg_api_restart`, `wwpg_api_upstream`, `wwpg_api_upstream_restart`,
`wwpg_api_pause`, and `wwpg_api_pending_restart`.
`global_pause_verified: true` binds the separate pause/step/resume fixture to
each profile/backend/platform proof; a passing subset cannot replace that gate.
`paused_measurements_unavailable: true` additionally confirms frozen meter behavior.
`nonconverging_backend_verified: true` requires actual backend rejection, scoped
history rollback, a healthy unrelated circuit, and recovery. Cold restart proofs
require `cold_wire_systems_verified: ["CEE", "POWER_GRID"]` and
`cold_ownership_format_version: 2`; a legacy PG-only result is insufficient.
`pending_wire_retirement_verified: true` covers a separate three-process
`pending_entity_restart_phases: ["SETUP", "ARM", "VERIFY"]` sequence. Its locked
`pending_entity_restart` retains the saved-state `oracle` and each ordered phase's
`manifest` and `log` descriptors. Final packaging reparses the ARM shutdown and
VERIFY markers for actual held entity loading, saving before readiness, disk
tombstones, both wire systems, zero-charge replacement, and fresh functioning
connections. A repeated ordinary VERIFY pass cannot substitute for this sequence.

### Independent builds and real clients

Required `api_fixture_behaviors` are `machine`, `storage`, `instrument`,
`panel_attachment`, and `board_component`. Independent build proofs name their
categories as `functional_behaviors`: the general example covers machine,
storage, and instrument; the upstream example covers panel and board.
Both set `uses_published_api_only: true`, `uses_wwpg_project_classes: false`,
and lock `compiled_against_api_sha256`. The upstream example additionally locks
`compiled_against_cee_api_sha256`.
Both require `build_from_extracted_sdk: true`, `extracted_source_sha256` matching
their source ZIP artifact, and `maven_repository_sha256` matching the Maven ZIP.
Locked `compiled_output`, `build_log`, and `dependency_proof` descriptors preserve
the independently built jar, successful build log, and actual resolved API hashes,
source count, and empty `wwpg_project_dependencies`. A CI build from the checkout
is preliminary evidence and cannot substitute for this exact archive gate.
Client proofs identify backend/profile/mode, the exact `world_sha256`, and a real
graphical client. Multiplayer additionally records at least two unique
`real_clients` and `real_tcp_clients: true`.

### Retained inputs and failed attempts

These JSON files are summaries of retained actual logs and artifact evidence,
not substitutes for executing tests. Packaging validates their immutability and
declared gates; it does not establish trust in fabricated input. Preserve failed
runs, investigate causes, and close defects with reproducible evidence. A retry
alone does not establish API readiness.
The package includes every transitively referenced locked proof and its logs,
dependency records, saved-state oracles, and client screenshots; changed evidence
blocks packaging.

### Collect the completed CI matrix

Derive the packaged matrix records from actual GitHub metadata and uploaded
artifacts with the evidence collector, rather than writing passing flags by hand:

```text
python tools/collect_api_ci_evidence.py --collect-ci RUN_ID --download build/api-ci-RUN_ID --output release/0.2.1/evidence/ci-RUN_ID
```

This read-only GitHub collection uses `gh`; it does not build, freeze, publish, or
change the progress record. It requires all eight jobs, independent example build
steps, matching uploaded jars, complete packaged attempts, actual unload counters,
pause/step counters, solver audits, JUnit XML, and both source/extracted world
restart chains. An incomplete earlier attempt in a final job blocks collection.
The output remains a matrix candidate: independent builds from the extracted SDK,
real-client acceptance, failed-run review, and final package approval remain
separate. For an already downloaded run, use `--run-json`, `--jobs-json`,
`--artifact-index`, and `--artifacts` instead of `--collect-ci`.

### Adjustable-load starter in the next SDK

Do not rebuild existing SDK assets. A future release includes `authoring_starter` with locked `source` ZIP and `proof` descriptors in the accepted manifest. The proof names the frozen `source_sha`, `source_archive_sha256`, `compiled_against_api_sha256`, `uses_wwpg_project_classes: false`, and locked `compiled_output`, `build_log` and `dependency_proof`. The latter is the actual `sdkProvenance` output (`coordinate`, `api_sha256`, `jar_sha256`, empty `wwpg_project_dependencies`).

`gameplay` contains four locked reports: Windows/Linux × NATIVE/JAVA, each binding the exact runtime and starter jar hashes. Required `checks` are `controls`, `native_tools`, `unavailable`, `cold_restart_before_solve`, `actual_chunk_reload`, `replacement`, and `dedicated_server`; `evidence` holds actual locked logs and observations. `graphical` holds separate real-client reports for each backend, binding both jar hashes, `controls: true`, `native_tools: true`, and locked logs/screenshots. Summary booleans cannot replace the actual observations. The ZIP must retain Unix origin with executable `gradlew` and ordinary files non-executable.

Documentation publication can proceed before those future release gates. The October 12 starter's graphical testing was deferred while benchmarks were running; this does not certify the starter for the next SDK release. Finish the remaining client checks against the next release's frozen artifacts.
