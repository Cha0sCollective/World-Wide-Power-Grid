# Narrowing the restoration pause

Pre-implementation investigation snapshot, November 9, 2026, for the retained
`wiretree.8` runtime. The subsequent opt-in prototype and its separately named
evidence are described in [scoped restoration](SCOPED_RESTORATION.md). This
review records the requirements that led to that prototype.

## Retained runtime behavior confirmed in source

`WireTreeBatch.holding(level)` is true whenever bounded work remains in that
dimension. `RestorationCoordinator.defer` consequently pauses every native
network, `pausedAt` returns true for every position, and
`WorldBridge.finishSolving` adds every CEE device to its paused set. Quiet-window
expiry cannot release this hold. Continuous incoming work can therefore keep
otherwise independent circuits paused.

The existing `queueHoldsStorageBeyondQuietDeadlineAndResumes` fixture explicitly
expects this behavior. Its battery/load and source/capacitor circuits are
separate from the wire ring being reloaded. It proves conservative storage
holding and eventual recovery after a finite queue; it does not prove that an
unrelated circuit continues operating during restoration.

PG 0.6.2's inspected `WorldNetworks` has a private `lineParts` map containing
saved parts, a `partNodeMap` keyed by node identity, and public external-node,
subnetwork and transmission-line collections. Pending queue entries already
contain the original endpoints and saved part/owner identities. Reading those
identities need not resolve endpoints or load chunks.

## Required implementation boundaries

The candidate pause set must follow the entire affected electrical component,
including unresolved saved segments. Looking only at a queued endpoint's
current `getNetwork()` would miss paths through an unloaded owner or a
mid-line node. The native tree walk's visited set is also insufficient: its
leaf handling can resolve a neighboring endpoint without visiting it. The
retired completed-region cache must not become an ownership authority.

Start with an independent, ephemeral connectivity calculation while work is
pending. It needs saved endpoint-to-endpoint edges, current endpoint-to-network
ownership, internal owner positions, transmission-line couplers and native
split-transformer coupling across worlds. Expand endpoint and network
membership to a fixed point. Resolve metadata only; do not call endpoint
`getNode`, initialize block entities, request chunks or execute geometry
callbacks to discover the set.

The saved `lineParts` authority avoids assuming that `partNodeMap` still uses
the same node object as the current external-node map. Validate actual map
mutation and endpoint-movement paths before retaining an index. Unknown
endpoint mappings require an explicit conservative fallback, not a claim that
the graph is complete.

Compute and check the pause set at the actual preparation boundaries. Native
preparation can merge/split networks after CEE and API preparation. Recheck
final network ownership before any solve, preserve holds already used to
defer native preparation, and keep coupled circuits synchronized. Replace the
dimension-wide checks in native deferral, public readings and CEE commit as a
single coherent change. CEE/API storage and heat must not advance on one side
of a paused coupling.

Measure the connectivity work separately. A full saved-graph scan on every
loading tick could erase the loading gain. Any work bound must specify what
remains paused while discovery is incomplete and must not turn continuous
incoming work into permanent starvation. Steady ticks with an empty queue
should take the existing fast path.

## Focused evidence required

- Keep a saved-wire queue nonempty beyond the quiet deadline, while an
  independent powered circuit continues taking all 16 substeps. Exercise
  repeated incoming work, not only one finite reload.
- Put native and CEE storage in the affected component; verify their energy,
  history, heating and measurement availability remain held, then recover.
- Cover unresolved chains, missing middle owners, branches, closed loops,
  merge/split, removed queued wires and changed endpoint owners. Verify actual
  unload/reload and saved identities.
- Cover transmission-line and split-transformer coupling, public API internal
  terminals, and conservative handling of unsupported endpoints.
- Compare the same 14,650-node copied-save client load at 1920×1080 / 12 chunks,
  worst loading tick, and steady tick distribution. Retain the current
  under-one-minute loading evidence only if the new artifact demonstrates it.

These are focused lanes, not authorization for full acceptance suites. The
current runtime's successful finite reloads cannot substitute for this evidence.
