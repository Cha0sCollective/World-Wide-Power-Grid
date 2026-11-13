# Choosing and recording tests

This is the `separated-v2` testing policy for future development and releases.
Published release records retain their original policy and evidence.

For the actions, assertions and limitations of each current fixture, see the
[GameTest coverage reference](GAMETEST_REFERENCE.md). Its source inventory is
separate from the passing execution records for any release.

| Lane | What it proves | Authorization |
| --- | --- | --- |
| Focused | The affected code, tooling or selected electrical regressions work | Normal development checks |
| Full headless | Runtime, lifecycle, API, SDK, world export on the recommended loader across Windows/Linux, Native/Java and BASE/PINOUT | Explicit request to run the full suite |
| Graphical | Selected real client visuals, controls, targeting, synchronization and Save and Quit | Separate explicit graphical request |

“Run the full suite” means **full headless**. It does not open clients. There is
no scheduled end-of-day run. A push, PR, version bump or release never authorizes
a full run. Publishing requires applicable passing evidence from both acceptance
lanes, and the publisher validates it without launching tests.

## Select release runtime dependencies

Freeze the **current recommended runtime set** rather than adding each new
loader to full acceptance. The recommendation is **NeoForge 21.1.262, PG 0.6.2
and CEE 1.1.4**, with Create 6.0.10 and Architectury 13.0.11. PINOUT uses
CC:Tweaked 1.120.2: 1.121.0 requires NeoForge 21.1.265 or newer. Minimum SDK
compile dependencies remain separate; the CEE 1.1.3 compile input does not
change the locked CEE 1.1.4 gameplay dependency. Record actual loaded versions
and hashes, not just resolver choices.

Full headless acceptance runs on this one loader across Windows/Linux,
Native/Java and BASE/PINOUT. The separately requested graphical lane uses the
same recommendation. A complete run on one dependency set cannot establish
full acceptance of another. Published beta.3 evidence remains its actual
NeoForge 21.1.266 execution; it is not relabelled or rerun automatically.

The catalog's `runtime_loader` and `recommended_runtime` own this selection.
Other loader versions use the separate focused loading workflow and never
enter full acceptance because a compatibility probe was requested. Full
planning rejects alternate loaders instead of silently expanding.

The twelve circuit-library assemblies retain every assertion at sixteen
substeps. Adder, oscillator, source-switch and regulator share one process as
four independent GameTests, with non-overlapping 32 x 8 x 32 test areas, one
pre-batch solver setup and separate traces. Missing cases or empty traces fail
validation. Individual focused groups remain available. The other eight cases
stay isolated: memory fixtures reuse trace filenames, advanced assemblies reuse
manifests, and heater/protection fixtures have observers or shared diagnostics.
See the [circuit library](CIRCUIT_LIBRARY.md) for their functional assertions.

The planned headless workload is **342 jobs across eight environments**:
328 Minecraft launches, 12 exports and two OS preparation entries. Compared
with beta.3's 654 jobs, separating three older-loader matrices saves 288 jobs
and circuit batching saves another 24. Cold restarts, unload/reload, rejection
recovery, SDK checks and exact world-archive reopens remain intact. These are
planned counts, not a claim that a fresh full suite ran.

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

`wwpg_sable_targeting` is an opt-in coordinate fixture namespace. Install the
Sable Companion 1.6.0 API explicitly for that invocation; the normal BASE and
PINOUT launches do not require it. These fixtures use real CEE geometry and PG
wire dispatch with controlled public-API poses, covering translation, rotation,
scale, render interpolation, panel covers, occlusion and live owner replacement.
They do not establish client frame rate or full moving-factory electrical
compatibility. Retain separate evidence for checks using the actual Sable mod.

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

The `wwpg_generator_timing` fixtures run in the separate `generator-timing`
group. They require a healthy world's diagnostic history, so they must not share
a process with safety fixtures that deliberately reject electrical solves.
They cover transformer-fed native
self-excitation with PG's intended overload oscillation, load changes and drive stop/restart,
plus a fully connected 72-coil carbon-pile fixture that checks original powered
operating points and both saturated and unsaturated clutch loads,
plus unchanged direct/custom-rotor execution in both directions at one and sixteen
substeps. A separate one-step batch requires shaft-driven and redstone variac re-excitation to
operate while the rotor remains at full speed. The `wwpg_audit_generator`
fixtures also record startup and load-change traces for comparison against the
native PG timing reference on both solvers. Matching upstream behavior, including
availability and oscillation, replaces the removed coupling model's settling
requirements. See [upstream behavior](UPSTREAM_BEHAVIOR.md).

The `wwpg` variac fixture checks powered PG load control and calls CEE's native
heating calculation with distinct local voltage readings and signed current.
It rejects global-index voltage reads and verifies the resulting temperature.
Run it against both CEE 1.1.3 and 1.1.4 when changing the compatibility redirect;
the grouped redirects require exactly one recognized upstream voltage call.

The `wwpg_wires` panel-tool regression checks the shared cover predicate for all
four PG wire materials, preserves native CEE tools, and excludes unrelated items.
Actual player rays and PG wire dispatch must select both sparse panel terminals
without consuming wire, across all horizontal panel orientations. The front-only
cover exception must preserve wall occlusion, reach, back/side blocking, closed
offhand-only covers, native CEE tool behavior and live attachment removal.
With Gauged installed, the same checks exercise all its
registered wire materials, including sizes outside PG's light-wire tag. These headless checks do
not certify the client's fade animation.

The `wwpg_wires` terminal-tool regressions also use actual PG multimeter rays
on exposed regulator pins and powered, recessed panel terminals. They require
native probe selection and accepted voltage without classifying the meter as
a wire. CEE switch and panel controls must ignore wiring/probe clicks, including
Shift and offhand attempts, while ordinary control clicks remain effective.
The guards target native control handlers, including installed fuse-holder
switches; bulb, fuse and fuse-holder material
repair workflows retain their native handling. PG's client highlighter shares
the main-hand probe selection, with the existing outline geometry and colors.

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
then derives its eight platform/backend/profile selections from the catalog
and uses only the recommended loader. Older-loader compatibility is a separate
manual workflow and cannot expand this matrix. The historical 0.2.1 SDK workflow
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

A headless continuation retains complete passing lifecycle groups and runs the
failed or unexecuted groups in fresh directories. The SDK starter also
uses the recommended loader; all jobs remain bound to the same
platform/backend/profile and exact catalog job IDs. Custom reference and SDK
launchers prepare the selected loader's Minecraft artifacts before their launch
preparation. The gameplay jar stays frozen. Failed SDK commands include their
underlying output in the retained launcher log even when no passing proof exists.

## Manual parallel loader compatibility

The **Loader compatibility checks** GitHub Action accepts one to four explicitly selected
NeoForge 21.1 versions at or above the declared minimum, 21.1.231, separated by commas. Start it manually and choose the
branch containing the code to test. It builds and freezes one gameplay jar,
matching test companion, APIs and SDK examples at the recommended loader.
Every worker uses those same bytes against its selected loader.

Each loader has eight independent environments: Windows/Linux, Native/Java, and
**BASE/ADDONS**. Up to eight jobs run concurrently, subject to GitHub runner
availability. A failure does not cancel other environments. Each job retains
its own directories, logs and proof. Each has three focused launches:
installation/powered optional probes, loading/restoration fixtures and isolated
board startup. That is 24 launches per selected loader.
The installation selector defaults to BOTH. Choose BASE or ADDONS for a targeted
retry; its summary explicitly covers only that selected installation. Reuse
passing evidence separately with its original source, artifacts and execution
identity, after checking artifact equivalence. A partial retry is not a fresh
pass of both installations.

BASE excludes optional integrations and SDK example mods. ADDONS loads the
recommended pinned CC:Tweaked 1.120.2, Pinout, Gauged Wires and Power Components,
plus both SDK examples. These same jars are retained when only the loader
changes; the runtime audit verifies their actual hashes, including on newer loaders.
The report records the actual existing `PINOUT` installation profile for ADDONS;
this does not rename historical profiles or mean Pinout is the only addon.
The loaded jars are checked against the frozen hashes. Small powered probes
exercise an actual Gauged wire feeding a CEE resistor and a Power Components
fuse on a PG circuit board. The full recommended-runtime suite retains the
Pinout/CC and API checks and their complete cold sequences. This is targeted integration coverage, not exhaustive
coverage of every optional mod feature.
Broader safety and API cold sequences remain in full headless acceptance,
including Native-only guards and addon-storage ownership tests. The loading
probe does not claim those assertions ran on the alternative loader.

```text
python tools/loader_compatibility.py plan --loader 21.1.262 --backend NATIVE --installation ADDONS
```

The plan launches nothing. The workflow checks actual installation and jar
identities, powered optional probes, loading/restoration and board startup.
It does not repeat broad regression/API cold sequences, world export campaigns,
SDK acceptance, graphical clients or benchmarks. Those remain in the recommended
full suite. New loader inputs do not change the recommendation. The collector
requires every planned environment, exact loaded artifacts, completed tests and
clean saves. Its `loader-loading-v2` result explicitly is not full acceptance.
Historical `loader-compatibility-v1` results retain their original larger scope.

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

## Reviewing historical headless coverage

`tools/legacy/test-suites-beta3.json` preserves beta.3's 654-job planning
contract. Read-only historical validation can use `suite_evidence.py` with
`--historical-catalog tools/legacy/test-suites-beta3.json` and the original
reports. Its output is explicitly historical-only and cannot qualify a current
release. Normal release gates use the current plan; no old record is rewritten
to claim the new batching or loader selection ran.
