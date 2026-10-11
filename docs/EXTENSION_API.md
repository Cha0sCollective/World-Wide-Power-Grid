# WWPG extension API

Target: **WWPG 0.2.0**, independently versioned **API 1.0.0**, Java 21 and NeoForge for Minecraft 1.21.1.

This document describes the contract being implemented. The implementation and
acceptance state are recorded in [the 0.2.0 progress record](../release/0.2.0/progress.json).
API freeze and release publication require all gates in
[the implementation plan](EXTENSION_API_PLAN.md); source files alone do not establish a supported release.

## What an add-on can provide

An add-on can expose a stationary machine's terminals, supply an immutable circuit
description, and consume solved readings to drive its own gameplay. The same
description can include an assembly's internal components or an instrument.
Native CEE panel attachments use the separate property-adapter route; native PG
board components use PG's existing component route.
Stable local element IDs permit multiple elements between the same two nodes.

The add-on owns recipes, assembly, GUI, block state, rendering, temperature,
damage, inventory, synchronization, and saved data. WWPG owns interoperability,
electrical topology updates, the shared PG simulation schedule, and result
publication. Players use the existing wiring systems and instruments.

PG-native add-ons already belong to PG's simulation. They retain their native
electrical objects and terminal integration. Registering a second WWPG circuit
for the same device would simulate it twice and is prohibited. CEE property
translation and PG terminal integration are separate upstream-specific routes.
The CEE property contract below is a separate published API; other runtime
implementation classes are not public contracts.

The two independent [example projects](../examples/) show both routes. Their
[Extension API Workshop](EXTENSION_API_WORKSHOP.md) provides visible machine,
storage, native panel and native board demonstrations with player instructions.
Its acceptance state is recorded separately from the developer contract.

## Dependency and package boundaries

The intended compile-time coordinate is:

```groovy
repositories {
    // Use the provided local Maven repository for SDK candidates.
    maven { url = uri('path/to/wwpg-api-maven') }
}
dependencies {
    compileOnly 'org.cha0scollective.wwpg:wwpg-api:1.0.0'
}
```

Use the WWPG runtime mod to supply the API at runtime. Do not shade API classes
into an add-on. The published source and Javadoc artifacts have the same
coordinate with `sources` and `javadoc` classifiers. No public Maven hosting URL
is promised until publication; the release's local Maven repository is sufficient
to build the independent example.

The public distribution target is the release asset
[wwpg-api-1.0.0-maven.zip](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/download/0.2.0/wwpg-api-1.0.0-maven.zip).
This URL becomes available when 0.2.0 is published. Download it from the release,
verify its published SHA256, and extract it into a directory such as
`wwpg-api-maven`. That directory contains `org/cha0scollective/wwpg/...` and is
the Maven repository root. Use its absolute path in `maven { url = uri(...) }`
or the examples' `-PapiRepository` option. Resolving these explicit versions
requires no GitHub account or Maven publishing credentials.

The archive includes both API coordinates and their source/Javadoc/POM artifacts.
It excludes floating version-discovery metadata, whose generation timestamp would
otherwise differ between independent CI builds. API 1.0 consumers declare the
explicit version `1.0.0`; ranges and automatic latest-version resolution are not
part of this initial distribution. A hosted Maven repository can be added later
without changing the coordinates or published JARs.

The two independent example projects are also distributed as source ZIPs with
their own wrappers and MIT license. They exclude Gradle caches and build outputs.
The Unix wrapper retains its executable permission. Generate these archives with:

```text
python tools/package_api_release.py --example-source examples/extension-addon --output build/distributions/wwpg-api-example-1.0.0-source.zip
python tools/package_api_release.py --example-source examples/cee-addon --output build/distributions/wwpg-upstream-api-example-1.0.0-source.zip
```

Building an example after extracting its source ZIP and the public Maven ZIP
requires no checkout of the WWPG runtime source.

`org.cha0scollective.wwpg.api` contains the platform-independent descriptions,
identities, units, models, and results. These signatures do not import Minecraft,
NeoForge, CEE, or PG implementation types. Platform registration and measurement
access live under `org.cha0scollective.wwpg.api.neoforge` and require the pinned
NeoForge/Minecraft development environment.

CEE property extensions additionally compile against
`org.cha0scollective.wwpg:wwpg-cee-api:1.0.0` and the pinned CEE 1.1.3 API.
The `org.cha0scollective.wwpg.api.cee` package deliberately refers to CEE types;
it is excluded from the general API artifact and has separate sources, Javadoc,
and classfile compatibility evidence. Runtime WWPG supplies both contracts once.

The supported contract is this API artifact, not every public class in the WWPG
runtime. `bridge`, `wiring`, `mixin`, and `gametest` packages are implementation
details. Network objects, matrix ordinals, solver internals, JNI pointers, and
mutable WWPG state are not extension points.

## Exposing a device

Implement `ElectricalDevice` and register it during NeoForge's
`RegisterCapabilitiesEvent` through `ElectricalProviders.registerBlock` or
`ElectricalProviders.registerBlockEntity`. These helpers publish
`ElectricalCapabilities.DEVICE` and declare one electrical owner per block.
Duplicate ownership fails with both provider IDs; a direct capability registration
that bypasses the declaration is not a supported API provider. Registration freezes
before worlds begin ticking.

Capability factories and `terminals()` can be queried on either logical side for
wire selection and rendering. Keep their code free of client-only classes and
return geometry from native state already available there. `prepare()` and
`commit()` run on the authoritative server game thread; CEE numerical callbacks
also run there. Client displays consume the owning mod's synchronized state and
never invoke these server callbacks themselves. Startup registration is not a
place to access a running world or solve a circuit.

The examples declare WWPG as a required runtime mod. An integration that makes
WWPG optional must isolate API-referencing classes and load/register them only
after confirming WWPG is installed. A `compileOnly` dependency does not make the
API classes available when the runtime is absent. Apply the same class-loading
guards to optional upstream-specific integrations; do not put optional mod types
in an always-loaded block, listener or method signature. The API source/Javadoc
JARs and Maven ZIP are developer artifacts, not extra mods to install.

Call `ElectricalDevices.changed(ServerLevel, BlockPos)` from server-side placement,
removal, loading and configuration hooks when a device may have changed. This is
especially important for plain blocks without block entities. It invalidates the
location's capability and queues loaded-device discovery without preparing a
circuit, loading a chunk or changing topology during solving. Its return value
is false when the runtime is absent or the location is unloaded. Chunk unload is
temporary loading loss; handle permanent block removal through the native removal
hook, retaining saved wires and history across unload.

The core interface is:

```java
public interface ElectricalDevice {
    String providerId();
    default String slotId() { return "main"; }
    List<Terminal> terminals();
    CircuitDescription prepare(TickContext context);
    void commit(DeviceResults results);
}
```

`providerId()` is namespaced, for example `example:variable_load`. `slotId()`
identifies the device within its block. Both remain stable across restart. The
world dimension and block position complete `DeviceIdentity`.

`terminals()` is a geometry query, available without preparing or solving a
circuit. Return an immutable snapshot. Looking at a terminal with a wiring tool
or meter must not advance electrical history, place a wire, or mutate the world.

`prepare()` captures circuit topology and current parameters once for the
authoritative game tick. Return a `CircuitDescription` containing external
terminals, internal nodes, electrical elements, and any explicit grounds. Building
new Java records does not advance a timestep or replace unchanged PG objects.

`commit()` receives an immutable snapshot once per participating server tick.
Apply electrical gameplay only when `status()` is `VALID`. Save accepted dynamic
state through the owning mod's normal block serialization. Handle an invalid
result as unavailable power and explain the reason to the player where useful;
do not treat its absence as a newly measured zero.

## Terminals, nodes, and identity

`Terminal` separates a stable local `key` from its native numerical terminal ID,
local geometry (`LocalPoint`), and selection radius. Keep both key and numerical
ID stable. A numerical terminal ID is used by upstream wire formats; it is not a
temporary solver index. Negative/native reserved IDs and overlapping terminal
geometry must follow the constructor validation and platform wiring contract.

External terminals and `InternalNode` values share one local node namespace.
`Element(id, positiveNode, negativeNode, model)` refers to those node keys. Give
each element an independent stable `id`. Two resistors in parallel have different
element IDs even when their terminal pairs match. Grounds refer to actual local
node keys; grounding does not create an invented external terminal.

The general block capability exposes one declared device description at a time.
An assembly implemented through that capability includes its components within
the same description, with distinct stable node and element keys. `slotId()` is
part of that device's identity; it is not a collection/discovery mechanism for
multiple capability owners. Native CEE panel attachments extend their native
properties through the separate CEE route, and PG board components keep PG's
native component registration and simulation.

Panel attachments or board components use stable slots owned by their assembly.
Do not derive slots from iteration order, visual position alone, list indices,
matrix ordinals, or Java object identity. Copying an upstream assembly follows
its native identity rules; preserve identities for an edit of an existing design,
and assign new ones for an actual independent copy where native serialization
requires it.

Changing a parameter with the same element ID and model family retains the
element's runtime history. Changing its endpoints or model family is structural.
Removal, block replacement, chunk unload, and temporary loading incompleteness
are different lifecycle events; do not erase saved state simply because a chunk
is not currently ready.

## Electrical models and conventions

API 1.0 supplies the following `ElectricalModel` records:

| Model | Meaning and units |
| --- | --- |
| `Resistor` | Positive resistance, ohms. |
| `Switch` | Open when disabled; positive on-resistance when closed. |
| `VoltageSource` | Ideal imposed `Vpositive − Vnegative`, volts. |
| `CurrentSource` | Current from the positive node to the negative node, amps. |
| `AcVoltageSource` | Offset plus peak sinusoid; volts, hertz, phase in radians. |
| `Capacitor` | Farads; initial voltage used only on element reconstruction. |
| `Inductor` | Henries; initial current used only on element reconstruction. |
| `Diode` | PG-native PN-junction parameters, including temperature and series resistance. |
| `Transformer` | Signed secondary-to-primary voltage ratio and named secondary nodes. |
| `ControlledVoltageSource` | Control-node voltage difference times dimensionless gain. |
| `ControlledCurrentSource` | Control-node voltage difference times transconductance, siemens. |

Constructors reject invalid keys, nonfinite values, and inappropriate zero or
negative physical parameters. Do not translate these errors into a plausible
electrical reading. An unsupported circuit needs a diagnostic and isolation.

Voltage is `Vpositive − Vnegative`. Element current is positive from the positive
node to the negative node. Positive absorbed power is voltage times that current.
Sources delivering energy therefore commonly have negative absorbed power.

For a transformer, the element endpoints form the primary winding and the named
secondary nodes form the secondary. `ratio = Vsecondary / Vprimary`; a negative
ratio reverses winding polarity. With both winding currents oriented from their
positive to negative nodes, `Iprimary = −ratio × Isecondary`. The element current
reading is the primary current. Do not infer winding direction from screen position.

Capacitors and inductors use backward Euler under the authoritative PG substep
schedule. They have no invented leakage or series resistance. Add a separate
resistor when that behavior is intended. The API diode follows PG's actual
temperature-compensated PN-junction model with series resistance; it is not an
ideal diode or a bare Shockley equation. API 1.0 does not expose its optional
avalanche model.

## Simulation and state ownership

The only electrical progression is:

**Capture inputs → update topology/parameters → PG substeps → publish results → commit gameplay.**

`TickContext` supplies game time, simulated tick duration, and the substep count.
Use simulated seconds rather than wall-clock elapsed time. Server lag does not
justify an add-on running extra electrical ticks. AC waveforms use the defined
absolute simulated time at each accepted substep.

Model records carry captured numerical inputs. They do not contain world
callbacks. Nonlinear numerical trials must not alter block state, inventory,
temperature, charge, or other committed history. WWPG advances history after
accepted electrical substeps; the gameplay callback occurs once after the tick.

`DeviceResults.electricalState()` provides committed `ElementState.Capacitor`
voltage and `ElementState.Inductor` current keyed by element ID. The add-on is the
authoritative persistent owner. Save these values with versioned native device
data and restore them into the model's initial values when the circuit is
reconstructed. Rebuilding a description each tick must not reset a live element
to its saved initial values.

Protect that saved state during invalid results: unavailable snapshots do not
contain new history. Failed nonlinear iterations must not be written into a save.
Changing a capacitance or inductance requires the native device to define its
physical editing behavior rather than WWPG inventing a charge/energy conservation
rule for it.

If gameplay discovers a tripped fuse, broken component, or removed connection in
`commit()`, expose the resulting description in the next preparation. WWPG applies
structural consequences outside solving. Do not mutate PG topology inside a result
callback or independently invoke either solver.

Rollback applies to WWPG-owned general-model numerical history and checkpointed
CEE property state when an electrical tick is rejected. Native PG devices keep
PG's native accepted-step behavior. This is not a transaction over an entire
mixed network: WWPG cannot undo arbitrary block, inventory, entity, NBT or external
side effects performed by an add-on's gameplay callback. Validate inputs before
applying effects and keep the owner's state update coherent. A throwing
`commit()` is quarantined; fix the cause and call `ElectricalDevices.changed`
to explicitly permit another preparation. Clear an unavailable display without
overwriting saved charge/current with a fabricated zero.

## Measurements and unavailable data

`DeviceResults`, `NodeResult`, `ElementResult`, and `TerminalMeasurement` are
immutable snapshots. `VoltageMeasurement` wraps an optional accepted voltage and
an explicit status. Their context identifies the tick. Consult the Javadoc for
the exact instantaneous, mean, RMS, and power fields; a display should label the
quantity it actually shows.

| Status | Interpretation |
| --- | --- |
| `VALID` | Current accepted results, including valid zero readings. |
| `NOT_READY` | Required loading or preparation is incomplete. |
| `ISOLATED` | Model/identity/parameters are unsupported or invalid. |
| `SOLVE_FAILED` | The affected electrical circuit did not produce accepted results. |

An invalid `DeviceResults` has empty node, element, and electrical-history maps.
`node()`, `element()`, and `state()` return empty optionals for missing entries.
Do not substitute last tick's successful values or manufacture `0 V` for an
unavailable reading.

Server-side measurement access is available through
`ElectricalMeasurements.read(ServerLevel, BlockPos)` in the platform package.
It reads participating extension devices; it does not invent extension identities
for unrelated native blocks. Reading a result must not force chunks to load, instantiate an adapter,
run preparation, or advance a solver. Do not call server-level measurement APIs
from client rendering; synchronize the display through the owning mod's normal
networking path.

An instrument can also call `ElectricalMeasurements.readTerminal` or
`readVoltageBetween` using block positions and stable native terminal IDs. These
support native CEE, native PG and general extension terminals. A terminal voltage
is relative to its circuit's reference; a differential voltage is signed A minus
B. Its RMS is calculated from simultaneous differential samples, so subtracting
two terminal RMS values does not produce the correct differential RMS. Separate
circuits require a shared electrical reference through their network, wiring or
explicit grounds before that comparison is valid. Unknown/unloaded terminals and
unrelated references return an unavailable result with no numerical value.

`MeasurementService` and `DeviceDiscoveryService` are runtime-supplied service
interfaces behind these facades. Add-ons consume the facades and must not register
their own implementations; multiple service owners are an explicit configuration
error.

## Persistence, loading, and failure handling

Keep a version number in extension-owned device data. Save device slots, element
IDs, component settings, and accepted dynamic state using the mod's native
serialization. WWPG preserves compatibility identities without rewriting the
upstream wire or design-table format.

WWPG also records the exact native wire identities owned by an extension. This
allows permanent replacement to retire old connections even when saved wire
entities have not loaded yet. Native PG wire UUIDs and component indices remain
native identities. CEE connections receive private compatibility tokens; making
a new connection gives it a new token, even when its endpoints match an old one.
Cleanup targets the retired connection's identity, so it cannot cut a newly
installed wire merely because the block position and terminal numbers match.
These records are internal versioned save data, not identifiers an add-on should
persist or manipulate.

Wire entities may load after a block. A partially loaded assembly must be marked
not ready instead of publishing fabricated power or deleting its saved links.
Do not use measurement queries as an excuse to request chunk tickets.
WWPG waits for a ticking owner chunk, an available block entity when required,
and loaded entities. If a device depends on further assembly parts that are not
ready, its capability factory can temporarily return `null` on that same owner.
Notify `ElectricalDevices.changed` when those requirements become available.
This preserves the distinction between an unavailable provider and permanent
replacement; do not change the block's owner identity to represent loading loss.

An exception or invalid model is reported with its provider, device, and relevant
terminal/element identity. The failing description is isolated; a solver failure
invalidates readings for the affected network. Recovery requires a new successful
preparation and solve. No second CEE solver or stale-result fallback is permitted.

If an add-on is absent, its electrical contract is unavailable. Preserve
recoverable compatibility records where possible. Removing a Minecraft content
mod can independently remove its blocks/items during world loading; WWPG cannot
promise to recover data Minecraft itself discards. Make a world backup before
removing content mods.

## Adapting a CEE electrical property

Subscribe to `RegisterCeePropertyAdaptersEvent` on your mod's **mod event bus**.
WWPG dispatches it once in mod order during the serial enqueued load-complete
phase; the game event bus is stopped at this startup point. Register a namespaced
provider ID, the **exact** native CEE property
class, and a `CeePropertyAdapter<T>`. Registration freezes after the listeners
return. Duplicate IDs, duplicate class owners, or late registration are errors;
an adapter for a superclass does not silently take over subclass behavior.
Only an add-on's own custom property classes may be registered. Replacing a
built-in CEE model is rejected, so an extension cannot silently change another
mod's electrical behavior.

`prepare(T)` captures `CeeElectricalParameters`: nonnegative conductance in
siemens, native CEE current-source amps, native CEE voltage-source volts, and
whether the ideal voltage constraint exists. These source signs follow CEE's
getter conventions; do not apply the general API's source orientation here.
WWPG performs the conversion to PG. A zero crossing must retain
`idealVoltageSource: true` for an actual ideal AC source. `inverted()` reverses
source polarity while preserving conductance and source family.

A static adapter needs no electrical timestep callback. A dynamic adapter
returns `dynamic(): true`, uses `beforeSubstep(T, CeeSubstepContext)` to prepare
numerical inputs from the previous accepted voltage, and uses
`afterSubstep(T, CeeSubstepResult)` to commit history after an accepted substep.
Neither callback may mutate the world, entities, inventory, or client state.
Native CEE device gameplay and serialization remain the owning mod's work.

Every dynamic adapter must implement `checkpoint(T)` and capture all fields its
callbacks can mutate, including saved charge, voltage history, and accepted-step
counters. Its `CeeStateCheckpoint.restore()` restores the captured values without
world access, further state advancement, or exceptions. `CeeStateCheckpoint.NONE`
is reserved for stateless adapters. WWPG retains a whole-tick checkpoint and a
checkpoint before each substep. A rejected solve or callback failure restores the
substep and then rolls back that property's entire tick, including earlier
accepted substeps, before native CEE gameplay can save the results. It prevents
that checkpointed property from retaining charge from a rejected tick. It does
not roll back native PG state or arbitrary gameplay side effects elsewhere in
the mixed network. The ideal-voltage-source family must remain unchanged
inside a tick; an adapter can request a different family at the next preparation.

This contract supports two-terminal Norton and ideal-voltage descriptions.
Custom coupled properties do not gain raw matrix access; use the general API
models or an existing native CEE transformer property. The add-on does not run
CEE's solver or independently advance history during nonlinear trials.

## Versioning and compatibility checks

`WWPGApi.version()` returns the installed API version, currently `1.0.0`; the
mod version is `0.2.0`. Mod bug fixes do not automatically change the API version.
The `API_VERSION` field also reads the version at runtime. It is deliberately
not a Java compile-time constant, so an already compiled add-on observes a later
compatible API version without recompilation.

After API 1.0 is frozen, compatible additions use API minor versions. Breaking
descriptors, removed models, changed identity meaning, changed physical
conventions, or new mandatory implementation methods require an API major version
and migration documentation. Constants are part of the contract because Java
may inline them into already compiled clients.

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
python tools/verify_extracted_api_sdk.py --runtime build/libs/wwpg-0.2.0.jar --api build/libs/wwpg-api-1.0.0.jar --cee-api build/libs/wwpg-cee-api-1.0.0.jar --example examples/extension-addon/build/libs/wwpg-api-example-1.0.0.jar --upstream-example examples/cee-addon/build/libs/wwpg-upstream-api-example-1.0.0.jar --maven build/distributions/wwpg-api-1.0.0-maven.zip --example-source build/distributions/wwpg-api-example-1.0.0-source.zip --upstream-source build/distributions/wwpg-upstream-api-example-1.0.0-source.zip --source-sha FROZEN_COMMIT_SHA --output build/extracted-sdk-FROZEN_COMMIT_SHA
```

This runs each extracted project's own wrapper, records its resolved compile-time
API hashes and build log, and requires byte-for-byte agreement with the frozen
example jars. The isolated projects do not use the main WWPG source tree.
Each attempt uses a new output directory; a failed attempt remains inspectable.

Pass `--release` only when the frozen manifest includes completed acceptance:

```text
python tools/package_api_release.py --manifest release/0.2.0/manifest.json --output build/distributions/wwpg-0.2.0-sdk-candidate.zip
python tools/package_api_release.py --manifest release/0.2.0/manifest.json --output build/distributions/wwpg-0.2.0-release.zip --release
```

The input format has `schema: 1`, `wwpg_version: "0.2.0"`,
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
- `independent_example_build`, a locked proof that the external Gradle project
  compiled against the API artifact without WWPG project implementation classes.
- `independent_upstream_example_build`, the corresponding proof for the separate
  CEE property/panel and native PG board example.
- Eight locked `client_acceptance` proofs: both profiles and backends, graphical
  and actual two-client multiplayer.
- Locked `base_world` and `pinout_world` descriptors for the exact accepted ZIPs.
- Locked `base_workshop_world` and `pinout_workshop_world` descriptors for the
  separate accepted API Workshop ZIPs. API client proofs bind to these archives.

Every acceptance proof must declare `completed: true`, `passed: true`, the frozen
`source_sha`, `artifact_hashes` for `runtime`, `api`, `cee_api`, `example`, and
`upstream_example`, and no
`unresolved_failures`. Matrix proofs identify platform/backend/profile, passing
regression/extension counts, at least three cold passes and five actual chunk
cycles, zero independent CEE solves, exactly-once electrical history, and a
passing runtime CEE-property fixture (`cee_property_adapter_verified: true`).
They list all passing `api_namespaces_passed`: `wwpg_api`, `wwpg_api_faults`,
`wwpg_api_restart`, `wwpg_api_upstream`, `wwpg_api_upstream_restart`, and `wwpg_api_pause`.
`global_pause_verified: true` binds the separate pause/step/resume fixture to
each profile/backend/platform proof; a passing subset cannot replace that gate.
`paused_measurements_unavailable: true` additionally confirms frozen meter behavior.
`nonconverging_backend_verified: true` requires actual backend rejection, scoped
history rollback, a healthy unrelated circuit, and recovery. Cold restart proofs
require `cold_wire_systems_verified: ["CEE", "POWER_GRID"]` and
`cold_ownership_format_version: 2`; a legacy PG-only result is insufficient.

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

These JSON files are summaries of retained actual logs and artifact evidence,
not substitutes for executing tests. Packaging validates their immutability and
declared gates; it does not establish trust in fabricated input. Preserve failed
runs, investigate causes, and close defects with reproducible evidence. A retry
alone does not establish API readiness.

Derive the packaged matrix records from actual GitHub metadata and uploaded
artifacts with the evidence collector, rather than writing passing flags by hand:

```text
python tools/collect_api_ci_evidence.py --collect-ci RUN_ID --download build/api-ci-RUN_ID --output release/0.2.0/evidence/ci-RUN_ID
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

## Deferred extension points

API 1.0 covers stationary integration on the pinned dependency set. Moving
contraptions, alternate solvers, raw numerical matrix access, custom wire
renderers, remote APIs, replacement Lua APIs, broad Minecraft/version ranges,
and arbitrary numerical callbacks require separate designs. Full product
specification 1.0 certification remains independent from API 1.0 compatibility.
