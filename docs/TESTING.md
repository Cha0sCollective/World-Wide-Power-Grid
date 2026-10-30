# Choosing and recording tests

This is the `separated-v2` testing policy for future development and releases.
Published release records retain their original policy and evidence.

For the actions, assertions and limitations of each current fixture, see the
[GameTest coverage reference](GAMETEST_REFERENCE.md). Its source inventory is
separate from the passing execution records for any release.

| Lane | What it proves | Authorization |
| --- | --- | --- |
| Focused | The affected code, tooling or selected electrical regressions work | Normal development checks |
| Full headless | Runtime, lifecycle, API, SDK, world export and loader coverage across Windows/Linux, Native/Java and BASE/PINOUT | Explicit request to run the full suite |
| Graphical | Selected real client visuals, controls, targeting, synchronization and Save and Quit | Separate explicit graphical request |

“Run the full suite” means **full headless**. It does not open clients. There is
no scheduled end-of-day run. A push, PR, version bump or release never authorizes
a full run. Publishing requires applicable passing evidence from both acceptance
lanes, and the publisher validates it without launching tests.

## Inspect a selection first

Use Java 21 and Python from the repository root. All `plan` commands are read-only:

```text
python tools/test_suites.py audit
python tools/test_suites.py plan focused --group safety --backend JAVA --profile BASE
python tools/test_suites.py plan full-headless
python tools/test_suites.py plan graphical
```

The versioned [catalog](../tools/test-suites.json) owns namespace membership,
environment selection, isolated restart sequences and graphical requirements.
Every GameTest namespace in runtime, example and acceptance-helper sources must
be classified. Special
real-player and reference fixtures are explicitly classified outside ordinary
electrical regression execution. External example namespaces are listed with
their API groups.

## Freeze once and run focused checks

`tools/ci_prepare.py` builds one canonical runtime/API set, independently builds
the examples, checks units and prepares the SDK. It starts no Minecraft process.
The canonical producer does this once; the Windows preparation job consumes its
frozen inputs and checks Windows compilation and extracted SDK builds once.

```text
python tools/ci_prepare.py --output build/my-campaign
```

For existing built jars, `freeze` copies the five runtime/API/example artifacts
and the separate upstream reference artifact into a fresh directory:

```text
python tools/test_suites.py freeze --output local/my-campaign/frozen
python tools/test_suites.py run focused --group safety --backend JAVA --profile BASE --inputs local/my-campaign/frozen/inputs.json --output local/my-campaign/safety-java
```

Never rebuild a frozen jar in place. The runner verifies all hashes before and
after execution. It passes explicit frozen example paths to Gradle and excludes
runtime rebuild tasks. Focused groups run their initial phase only; full
acceptance preserves their complete cold sequences. Headless gameplay launches
retain the 600-second deadline and ordinary Minecraft watchdog. Client runs
retain the existing 900-second TCP / 1200-second GUI launcher bounds; their
scenario assertions remain independently bounded.

Fresh-only fixtures use the catalog's `setup_namespaces`. API ownership's
fixed-coordinate unload fixture runs during setup; dedicated API restart
fixtures still run through all three cold verifications. Recreating that fresh
fixture in a saved world would attempt to add wires that already exist.

The `wwpg_trains` focused safety fixture assembles and moves a native CEE train
fed through a PG source and CEE transformer. It checks overhead joints,
neighboring contacts, actual PG multimeter readings, power loss/recovery and
foreign-dimension result/storage ownership. Client sound playback, multiplayer
and train dimension travel require separate coverage; the headless fixture does
not certify them.

The `wwpg_api` startup binding regression observes native terminal resolution
before the first electrical tick. It requires terminal identity to persist
without initializing CEE infrastructure or preparing models, then verifies the
same bound node supplies an accepted 20 V after normal preparation. It guards
against recursive CEE/chunk loading while PG restores a saved network.
The ground-rod reload fixture uses Minecraft's blocking chunk/entity save
boundary after requesting the reload, so uncapped GameTest ticks do not race
unfinished disk work. Its actual unload/reload, identity and voltage assertions,
80-tick readiness bound, process deadline and watchdog remain unchanged.

## Full headless acceptance

The manual **Full headless acceptance** GitHub workflow prepares artifacts once,
then uses the catalog for the platform/backend/profile/loader selections. The
second loader retains the compatibility groups rather than duplicating world
exports, SDK work and reference comparisons. The historical 0.2.1 SDK workflow
is separately named and is not current-runtime acceptance.

Local execution uses the same interface. Select the current machine's platform;
a Windows-only result cannot claim Linux coverage:

```text
python tools/test_suites.py run full-headless --platform windows-latest --inputs build/my-campaign/frozen/inputs.json --preparation build/my-campaign/preparation.json --output local/my-campaign/headless-windows
```

The combined PINOUT yard covers the ordinary yard. BASE and PINOUT runtime/API
installation checks remain separate. Source-world cold restarts and exact ZIP
reopens remain distinct checks. References, example and showroom checks execute
once per operating-system/backend combination.

For an investigated Workshop spawn-metadata correction, the manual workflow's
`workshop_spawn_run` input selects only the focused export/reopen continuation.
Supply the preserved canonical runtime run as `frozen_run` and the passing world
run as `workshop_spawn_run`. This mode skips preparation and every full-suite
job. It freezes the corrected archives once and runs their three exact reopens
on Windows/Linux and both solvers/profiles. Original source-world cold checks
remain separate. Compare every native payload and all uncompressed `level.dat`
NBT outside `spawnRadius`; a changed electrical save requires broader testing.
The equivalent focused tools are `recheck_workshop_spawn.py prepare` and `run`.

## Manual parallel loader compatibility

The **Loader compatibility checks** GitHub Action accepts one to four newer
NeoForge 21.1 versions, separated by commas. Start it manually and choose the
branch containing the code to test. It builds and freezes one gameplay jar,
matching test companion, APIs and SDK examples at the normal minimum loader.
Every worker uses those same bytes against its selected loader.

Each loader has eight independent jobs: Windows/Linux, Native/Java, and
**BASE/ADDONS**. Up to eight jobs run concurrently, subject to GitHub runner
availability. A failure does not cancel other environments. Each job retains
its own directories, logs and proof; cold restart phases run in order.
The installation selector defaults to BOTH. Choose BASE or ADDONS for a targeted
retry; its summary explicitly covers only that selected installation. Reuse
passing evidence separately with its original source, artifacts and execution
identity, after checking artifact equivalence. A partial retry is not a fresh
pass of both installations.

BASE excludes optional integrations and SDK example mods. ADDONS loads pinned
CC:Tweaked, Pinout, Gauged Wires and Power Components, plus both SDK examples.
The report records the actual existing `PINOUT` installation profile for ADDONS;
this does not rename historical profiles or mean Pinout is the only addon.
The loaded jars are checked against the frozen hashes. Small powered probes
exercise an actual Gauged wire feeding a CEE resistor and a Power Components
fuse on a PG circuit board. Existing Pinout/CC and API tests retain their own
checks and cold sequences. This is targeted integration coverage, not exhaustive
coverage of every optional mod feature.
The three safety tests that need SDK storage devices have explicit addon-only
namespaces. BASE retains the remaining solver, wire recovery and native guard
tests; ADDONS and ordinary safety acceptance retain all three storage tests.

```text
python tools/loader_compatibility.py plan --loader 21.1.262 --backend NATIVE --installation ADDONS
```

The plan launches nothing. The workflow runs regression, loading, isolated board
startup and safety groups; ADDONS adds Pinout and API lifecycle checks. It does
not run world export campaigns, SDK acceptance, graphical clients or performance
benchmarks. New loader inputs do not enter the verified release catalog merely
because they were requested. A separate collector requires every planned
environment, exact loaded artifacts, completed tests and clean saves before
reporting a passing compatibility campaign. Missing reports remain failures.
Full release acceptance still requires its independent existing evidence.

## Separate local graphical acceptance

Graphical sessions run serially on the local Windows desktop. Performance
benchmarks remain separate. Bind the actual accepted archives and independent
starter build proof to a new input manifest:

```text
python tools/test_suites.py bind --inputs local/my-campaign/frozen/inputs.json --world yard/PINOUT=path/to/combined-yard.zip --world workshop/BASE=path/to/base-workshop.zip --world workshop/PINOUT=path/to/pinout-workshop.zip --starter build/my-campaign/starter/build-proof.json --output local/my-campaign/client-inputs.json
python tools/test_suites.py run graphical --inputs local/my-campaign/client-inputs.json --output local/my-campaign/clients
```

`graphical` selects twelve scenarios: a Native representative yard tour,
integrated Workshop checks, Native two-player synchronization, short Java
checks, BASE Workshop smoke checks and the separately compiled SDK starter.
The Native yard visits eleven representative views and retains the distinct
editor, Pinout, portal and terminal-highlight interactions. TCP Workshop clients
exercise synchronization without repeating the integrated visual gallery.

For a focused driver validation or an investigated rerun, select an exact ID from
`plan graphical`, use a fresh output path and record the reason:

```text
python tools/test_suites.py run graphical --case base-workshop/NATIVE/BASE --inputs local/my-campaign/client-inputs.json --output local/my-campaign/client-rerun --rerun-reason "Validate corrected scenario driver"
```

This is partial graphical coverage. Only a complete validated cohort qualifies
for the graphical release gate. `graphical-gallery` additionally enables the
exhaustive yard/Workshop view audit; it is never implied by another lane.

Electrical correctness belongs in GameTests. Client drivers perform bounded
readiness checks and prove the selected visuals, native input and synchronized
results. Each integrated run must use Save and Quit, reach the title screen,
save all dimensions and exit successfully. TCP runs require two real clients,
both disconnects and a clean server save/stop. Media supports named assertions;
screenshot counts cannot substitute for them.

Electrical warnings are retained in `electrical_diagnostics` as well as in the
original logs. Passing named client assertions does not certify a warning-free
runtime. Investigate those diagnostics before making that claim.

The dev.102 targeting change has focused headless player-ray coverage for
exposed regulator and capacitor pins while holding a PG wire. Highlight-only
evidence from earlier builds does not certify this path. Real-client rendering
and input acceptance must still be requested separately.

## Results and release gates

Every attempt has a fresh directory, actual source/artifact/world/helper
identities, logs, process outcomes, durations and a `result.json`. Statuses are
`passed`, `failed`, `not run` or explicitly `reused`. Preserve interrupted and
failed attempts. Corrections produce new attempts; do not clear old errors or
rename an interrupted run as a pass.

Collector-only changes can reuse immutable observations without relaunching a
client. `collect-client --case-directory PATH --output NEW_VERIFICATION.json`
validates the original processes, logs, loaded artifacts and captured helper
sources again. It preserves the old proof and identifies it in `recollected_from`.

`tools/bundle_suite_evidence.py` produces portable views and retains original
JSON bytes separately. It changes filesystem references, never executed source
identities, observed values or runtime hashes. Download CI artifacts into the
same workspace-relative `build/ci-v2/portable/ENVIRONMENT/` directories named by
their retained descriptors.

```text
python tools/suite_evidence.py --lane full-headless --report path/to/windows-result.json --report path/to/linux-result.json --output local/my-campaign/headless-cohort.json
python tools/suite_evidence.py --lane graphical --report path/to/client-result.json --output local/my-campaign/client-cohort.json
```

Supply every selected matrix report to the headless collector; it rejects
missing, duplicate, wrong-artifact and incomplete coverage. Future release
manifests use `acceptance_policy: separated-v2` and locked
`headless_acceptance` / `graphical_acceptance` cohort descriptors. Existing SDK
independent-build and packaging requirements remain in force. The graphical
gate binds observations to the exact published archives and SDK starter.

Version-only reuse requires `status: reused`, the original source/runtime
descriptor and version, and `reuse.scope: runtime_version_only`. Validators
compare runtime entries and native binaries and require identical API/example
hashes. Keep the original reports; their source and status do not change.

Historical policies, counts and continuation records remain readable through
their original validators. The old workflow definitions are retained under
`tools/legacy/` for reference, not active execution.

An investigated runner or catalog repair may continue a frozen campaign through
the manual workflow's `frozen_run` input. It reuses the original canonical jars
and per-OS preparation, requires unchanged runtime/build inputs, and retains
complete passing lifecycle groups as `reused` views of the original report.
Failed attempts remain failed. Incomplete groups start again in fresh
directories, including their setup and required cold phases; a successful
prefix cannot stand in for an unfinished lifecycle. The continuation index
identifies both retained and newly executed reports for the ordinary coverage
validator. It never launches automatically.
If a failed bundler left no usable execution report, continuation cannot reuse
that environment's claimed prefix. Its explicit `--fresh-if-unavailable` option
reruns the named environment and records that absence; other environments still
retain verified passing groups. Original partial uploads remain preserved.

## Separate gameplay and test jars (development candidates dev.99 onward)

`assemble` builds the player mod `wwpg-<version>.jar` and the separate
`wwpg-<version>-gametests.jar`. Install only the player mod for normal play.
The companion contains GameTests, test commands, world-building fixtures and
empty test structures; it requires the matching gameplay version. Gameplay Lua
programs, native libraries, APIs and production mixins stay in the player mod.

The catalog runner freezes both jars once. Each packaged headless launch loads
the exact gameplay jar plus its companion, with both hashes in its process proof.
Direct launches can supply `-PtestArtifact=<gameplay.jar>` and
`-PtestFixtureArtifact=<gametests.jar>` to `runPackagedGameTestServer`.
Normal development client/server runs exclude the companion; `runGameTestServer`
includes it. SDK/client acceptance helpers also consume the frozen companion.
Historical records without a companion retain their original validation rules.
No test namespace or lifecycle phase was removed by this separation.

For a future separated release, the accepted manifest also includes the frozen
`gametest` artifact. Its jar stays inside the verification bundle; it is not a
sixth download or a player dependency. The headless/client reports must bind the
same companion, and publication still requires their applicable complete coverage.
Native provenance manifests remain in the player jar as referenced by its notice.
