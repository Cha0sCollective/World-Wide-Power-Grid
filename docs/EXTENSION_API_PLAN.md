# WWPG 0.2.0 / extension API 1.0 implementation and acceptance

The release delivers a documented stationary integration contract, an independent
SDK, and a separately built example add-on. API version **1.0.0** is independent
of WWPG version **0.2.0**. Completion is recorded in
[release/0.2.0/progress.json](../release/0.2.0/progress.json).

Keep the beta.5 upstream pins: Minecraft 1.21.1, NeoForge 21.1.231, Java 21, Create
6.0.10-280, CEE 1.1.3, PG 0.6.2, and Architectury 13.0.8. Optional Pinout 0.2.1
and CC:Tweaked 1.120.2 retain their separate installation profile. Native is the
primary backend; Java receives the same functionality and lifecycle acceptance.

## Stage 1 — Contract and external example

Define device discovery, terminal geometry, stable identities, captured models,
measurements, lifecycle, persistence ownership, optional loading, units, and
compatibility policy. Keep general API signatures free of upstream implementation
types. Establish API/source/Javadoc artifacts and a standalone NeoForge example
project using the API coordinate.

**Gate:** A variable load with a visible output can be implemented through the
documented public contract. Example compilation cannot access WWPG project classes.

## Stage 2 — Discovery, identities, and topology

Register through NeoForge capabilities and namespaced provider/slot identities.
Support external terminals, internal nodes, multiple components within an
assembly, and parallel elements with distinct IDs. Separate geometry queries
from preparation and solving. Retain PG objects for unchanged topology and apply
structural changes outside the solve. Preserve beta.5 identities and wire saves.

**Gate:** Two independent extension devices coexist, edit and reload without
collisions, duplicate elements, or unnecessary object replacement.

## Stage 3 — Models and the electrical lifecycle

Implement standard resistance/switch/source/storage/AC/transformer/diode/controlled
source models. Define current/power orientation, state ownership, substep timing,
RMS and averages, nonlinear trial isolation, and exactly-once accepted history.
Keep native PG content in its native simulation; separately identify any claimed
upstream-specific CEE adapter or PG integration route.

**Gate:** Analytical DC, storage, AC, transformer, and representative nonlinear
fixtures pass on both backends with no independent CEE solve.

## Stage 4 — Wires, meters, and assembly workflows

Expose extension terminals to both native wire systems and shared instruments.
Preserve selection, validation, cost, cutting, repair, and rendering. Extend the
independent example with a panel attachment and board component using upstream
assembly/editing workflows. Invalid readings remain unavailable rather than zero
or stale successful results.

**Gate:** Real players can wire, measure, edit, copy, replace, and repair example
content, including simultaneous two-client interactions.

## Stage 5 — Persistence, loading, and failures

Version extension-owned data and save authoritative accepted electrical state.
Exercise delayed wire entities, partial assemblies, unloaded/missing providers,
block replacement, changed grounds, split/merge, invalid parameters, exceptions,
and convergence failures. Preserve recoverable identity records. Report the
device/terminal/element and reason without creating graph objects from diagnostics.

**Gate:** Three cold restart passes and five actual unload/reload cycles preserve
pre-solve charge, identities, settings, and intended connections. Fault injection
produces isolation and valid recovery without ghost power or history advancement.
Numerical rollback is scoped to WWPG-owned general models and checkpointed CEE
properties. Native PG retains its native accepted-step behavior; arbitrary
gameplay effects are not an entire-network transaction.

## Stage 6 — Freeze, SDK, and release

Publish API/source/Javadoc/local Maven artifacts, independent example sources and
jar, developer documentation, a playable example, dependency/checksum manifests,
and release notes. Snapshot the first contract honestly; future releases compare
against the genuine published API and run a previously compiled example.

Preserve beta.5's 204 base / 208 Pinout packaged checks. Run Windows/Linux ×
native/Java × base/Pinout, functional example checks, lifecycle tests, extracted
world checks, graphical clients, and actual two-client dedicated-server sessions.
All evidence must refer to the same frozen runtime/API/example artifacts.

**Gate:** A developer builds and runs the example outside the repository against
the SDK. All declared extension categories and inherited regression requirements
pass. No unresolved defect in a claimed public workflow remains at API freeze.

## Completion rule

Unimplemented categories and failed gates remain blockers. Do not mark a narrower
preview as API 1.0 or remove failed acceptance requirements to obtain a release.
SDK candidate packaging is explicitly non-publishable until complete acceptance.
Stage-sized PRs preserve reviewable changes and failure evidence. Earlier beta
artifacts and their historical support records remain unchanged.

Deferred work remains moving electrical systems, broad upstream/version support,
large-server certification, asynchronous solving, solver replacement, custom wire
renderers, unrestricted numerical callbacks, and full product-specification 1.0
certification.
