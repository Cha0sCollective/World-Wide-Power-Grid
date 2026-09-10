# World-Wide Power Grid
## Product Specification

> **North-star specification:** CEE content, Power Grid electrical backend, direct interoperability, no player-visible translation layer.

**Document status:** Normative target specification  
**Specification version:** 1.1  
**Project phase:** Pre-implementation / pursuing specification  
**Initial compatibility target:** Minecraft 1.21.1-era releases of Create: Electro Energetics and Power Grid

---

## 0. How to read this document

This document defines the **finished release product** that World-Wide Power Grid is pursuing. It is deliberately not an MVP definition and must not be weakened merely because an early implementation supports a smaller subset.

Implementation milestones may deliver only portions of this specification. Such milestones are progress toward completion, not alternative definitions of completion.

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**, **SHOULD**, **SHOULD NOT**, **RECOMMENDED**, **MAY**, and **OPTIONAL** are normative.

When implementation convenience conflicts with this specification, the implementation should change unless the specification is intentionally amended.

---

# 1. Product vision

World-Wide Power Grid exists to make **Create: Electro Energetics (CEE)** and **Power Grid (PG)** behave as one electrical ecosystem.

When both supported mods are installed with World-Wide Power Grid, **Power Grid SHALL become the authoritative electrical simulation backend for supported CEE electrical content**.

CEE SHALL continue to provide and control its own machines, blocks, models, items, interfaces, wire visuals, controls, thermal behavior, component damage, failure effects, Create integration, and other gameplay mechanics. CEE's native electrical network solver, however, SHALL NOT remain an independently authoritative solver while compatibility mode is active.

The finished player experience should allow circuits such as:

```text
CEE generator
      │
   CEE wire
      │
PG transformer
      │
    PG wire
      │
CEE capacitor bank
      │
   CEE wire
      │
PG machine
```

The entire circuit SHALL participate in one Power Grid electrical topology and be solved through Power Grid's electrical backend.

No player-visible adapter block, FE bridge, one-tick equivalent-source interface, or manual compatibility component is required.

---

# 2. Definition of the product

World-Wide Power Grid is an **electrical backend compatibility layer**.

It is not a bridge between two independently running electrical simulations.

The intended ownership model is:

```text
                         PLAYER WORLD
                              │
             ┌────────────────┴────────────────┐
             │                                 │
        CEE CONTENT                        PG CONTENT
   machines / wires / UI              machines / wires / UI
             │                                 │
             └────────────────┬────────────────┘
                              │
                              ▼
                   WORLD-WIDE POWER GRID
                              │
             terminals / models / mappings
                              │
                              ▼
                  PERSISTENT PG TOPOLOGY
                              │
                              ▼
                    POWER GRID SOLVER
                              │
                 ┌────────────┴────────────┐
                 │                         │
                 ▼                         ▼
          native PG state          CEE result bridge
                                           │
                                           ▼
                                  CEE gameplay updates
```

A network containing CEE and PG content SHALL NOT enter a special solver-to-solver “mixed network” mode. Compatible CEE content is represented in PG from the beginning, so a network containing both mods is simply a Power Grid network containing native and compatibility-provided elements.

---

# 3. Guiding principle

All architecture and implementation decisions SHOULD follow this rule:

> **CEE defines what its content is and how that content behaves as a game system. Power Grid defines the electrical network in which that content participates and solves that electrical network exactly once.**

The compatibility layer translates between those responsibilities without exposing the translation to the player.

---

# 4. Single authoritative solver

Power Grid MUST be the sole authoritative electrical solver while World-Wide Power Grid compatibility mode is active.

CEE MUST NOT independently solve any electrical state that is also represented in Power Grid.

The implementation MUST NOT perform any of the following as its normal compatibility architecture:

- CEE solve followed by PG solve;
- PG solve followed by CEE solve;
- repeated iteration between both independent solvers;
- FE conversion between the two systems;
- a one-tick delayed Thevenin/Norton boundary between CEE and PG networks;
- duplicate advancement of stateful electrical components.

There SHALL be one authoritative solved electrical state per simulation step.

---

# 5. CEE solver replacement boundary

World-Wide Power Grid SHOULD preserve the useful portions of CEE's electrical device lifecycle while replacing the actual CEE network solve.

The target lifecycle is:

```text
CEE
  collect devices
  build electrical description
  device.preTick(...)
  AddToElectricGraphEvent

            │
            ▼

World-Wide Power Grid
  inspect CEE electrical description
  synchronize changes into persistent PG representation

            │
            ▼

Power Grid
  prepare electrical network
  execute authoritative solve

            │
            ▼

World-Wide Power Grid
  collect solved values for CEE-visible nodes
  construct CEE-compatible SimulationResults

            │
            ▼

CEE
  device.postTick(...)
  wire lifetime / thermal logic
  component damage
  FinishElectricSimulationEvent
  voltage synchronization
  normal gameplay state updates
```

When compatibility mode is active, the CEE operations that exist specifically to create and solve independent CEE `Network` objects SHOULD be bypassed.

In particular, compatibility mode MUST prevent duplicate execution of CEE's independent:

- electrical network solve;
- Newton/MNA loop;
- solver microtick loop;
- solver-side advancement of dynamic property state;
- asynchronous electrical solve followed by a required `Future.get()` barrier.

CEE MAY continue creating a lightweight `CircuitBuilder` or equivalent electrical description where useful for device compatibility and result queries.

---

# 6. CEE remains the gameplay owner

Replacing CEE's electrical solver MUST NOT unnecessarily replace CEE gameplay systems.

CEE SHALL remain authoritative for its own applicable:

- block and machine state;
- visuals and models;
- animations;
- GUIs;
- controls;
- redstone behavior;
- recipes and progression;
- mechanical/Create integration;
- sounds and particles;
- thermal state;
- electrical damage rules;
- explosions and fire behavior;
- wire rendering and sag;
- wire placement UX where CEE wires are used;
- CEE-specific persistence;
- other non-solver gameplay behavior.

World-Wide Power Grid is an electrical interoperability project, not a redesign of either upstream mod.

---

# 7. Compatibility nodes are invisible software objects

A **CompatNode** is an internal software representation used to make a terminal or internal CEE electrical node participate in Power Grid.

A CompatNode is NOT:

- a block;
- an item;
- a placeable connector;
- an entity the player must create;
- a visible converter;
- a progression requirement;
- a cable adapter.

CompatNodes SHALL be created, updated, loaded, unloaded, and destroyed automatically.

A CEE terminal should conceptually map as:

```text
CEE terminal
     │
     ▼
hidden persistent PG-side node
     │
     ▼
Power Grid ElectricalNetwork
```

The implementation SHOULD reuse normal Power Grid node and endpoint mechanisms wherever practical.

The implementation SHOULD NOT introduce a new persistent PG endpoint serialization type when an existing block endpoint can safely identify the terminal.

---

# 8. Sidecar device model

CEE machines do not need to literally become Power Grid block entities.

World-Wide Power Grid SHOULD represent CEE electrical devices through automatically managed **sidecar electrical objects**.

Conceptually:

```text
CEE machine/device
       │
       ├──── CEE terminal 0 ─── hidden PG node
       ├──── CEE terminal 1 ─── hidden PG node
       └──── internal model ─── PG wires/nodes/couplings/hooks
```

The CEE object remains physically and logically a CEE machine. Its sidecar representation is electrically native to Power Grid.

This design SHOULD avoid forcing CEE's object inheritance model into PG's block-entity/behaviour hierarchy.

---

# 9. Direct wire interoperability

Power Grid wires MUST be able to connect directly to compatible CEE terminals.

CEE wires MUST be able to connect directly to compatible Power Grid terminals.

The player MUST NOT need to place a compatibility-specific object between the two mods.

The following are required normal configurations:

```text
[CEE machine] -------- PG wire -------- [PG machine]

[PG machine] -------- CEE wire -------- [CEE machine]

[CEE machine] -------- PG wire -------- [CEE machine]

[PG machine] -------- CEE wire -------- [PG machine]
```

Cross-mod wiring MUST participate in the same authoritative PG electrical topology immediately after successful placement.

---

# 10. Terminal interaction requirements

Cross-mod terminal selection SHOULD feel native to the wire system the player is currently using.

When a player holds a PG wire and targets a compatible CEE terminal:

- the visible CEE terminal SHOULD be selectable at its intended physical location;
- normal PG placement feedback SHOULD be preserved where practical;
- PG range, item-cost, and wire-type restrictions SHOULD remain applicable;
- the resulting PG wire SHALL electrically join the mapped CEE terminal's PG node.

When a player holds a CEE wire spool and targets a compatible PG terminal:

- the visible PG terminal SHOULD be selectable;
- normal CEE spool workflow SHOULD be preserved where practical;
- an invisible CEE-side endpoint MAY be created automatically if the CEE wire implementation requires one;
- the resulting CEE physical wire SHALL electrically join the PG terminal's electrical node.

Any invisible endpoint must correspond to the terminal the player actually selected.

---

# 11. Persistent electrical topology

CEE electrical content MUST be represented by persistent PG-side topology.

The complete translated CEE network MUST NOT be destroyed and recreated every game tick during steady-state operation.

CEE may continue rebuilding a lightweight electrical description every tick. World-Wide Power Grid SHALL treat that description as **desired electrical state**, not as an instruction to rebuild the PG network wholesale.

The target steady-state model is:

```text
CEE desired-state snapshot
          │
          ▼
compare with existing compatibility state
          │
   ┌──────┴──────┐
   │             │
unchanged      changed
   │             │
 no-op      incremental update
```

Stable CEE node identities SHALL map to stable PG-side identities wherever possible.

---

# 12. Incremental synchronization

World-Wide Power Grid MUST synchronize CEE electrical changes incrementally.

When topology does not change, numerical changes SHOULD update existing PG elements in place whenever the PG model permits it.

Examples include changes to:

- resistance;
- source voltage;
- source current;
- switch state where representable without structural change;
- capacitance;
- inductance;
- transformer ratio;
- machine operating point;
- dynamic companion-model coefficients;
- nonlinear linearization values.

Actual PG topology changes SHOULD occur only when the electrical structure genuinely changes, for example:

- machine placement/removal;
- terminal creation/removal;
- wire placement/removal;
- a machine changing circuit structure;
- connection reconfiguration;
- chunk/sublevel lifecycle changes that require structural migration.

An unchanged CEE network SHOULD cause zero compatibility-originated PG topology additions/removals during a normal tick.

---

# 13. Stable sparse structure is a product requirement

World-Wide Power Grid MUST avoid unnecessary changes to Power Grid's matrix sparsity structure.

Power Grid's native backend is designed around sparse solving and can reuse structural ordering information when the matrix pattern remains stable. World-Wide Power Grid SHALL preserve the conditions that make such reuse possible wherever practical.

The desired long-running behavior is:

```text
topology established
       │
       ▼
stable sparse structure
       │
       ├─ source value changes
       ├─ motor/load changes
       ├─ capacitor state changes
       ├─ nonlinear operating point changes
       └─ no structural edits
       │
       ▼
same PG topology
```

The project SHALL NOT intentionally throw away PG's persistent-network advantages by rebuilding translated topology on every game tick.

---

# 14. Electrical primitive translation

The compatibility layer MUST support every electrical primitive required by the declared supported CEE version.

Translation SHOULD occur primarily at the electrical-property/model level rather than through one compatibility class per individual machine.

A CEE machine composed entirely from already-supported electrical primitives SHOULD ideally become compatible without requiring a machine-specific adapter.

The finished compatibility layer MUST support, where used by the supported CEE version:

## 14.1 Linear elements

- resistors;
- conductances;
- current sources;
- Norton-equivalent sources;
- voltage sources with internal resistance;
- ideal voltage sources;
- internal nodes;
- ground/reference behavior;
- source/reference priority behavior where it affects results.

## 14.2 Stateful/time-dependent elements

- capacitors;
- inductors;
- accumulators;
- AC sources;
- alternator phase windings;
- other CEE `MicroTickingElectricalProperties`.

## 14.3 Nonlinear elements

- diodes;
- other CEE Norton-linearized nonlinear properties;
- components using solver-iteration callbacks.

## 14.4 Coupled elements

- ideal transformer relationships;
- transformer-derived devices;
- other supported CEE coupled properties.

Unsupported normal built-in CEE electrical properties SHALL be considered compatibility defects for a release that claims full support for that CEE version.

---

# 15. Preserve CEE electrical equations where practical

Using Power Grid as the solver MUST NOT imply replacing CEE's intended electrical behavior with crude approximations when avoidable.

Where CEE owns stateful property objects or equations, World-Wide Power Grid SHOULD use those properties as the source of truth for CEE-specific state/equations while exposing their stamps/residual behavior to the PG solver.

Conceptually:

```text
CEE dynamic/nonlinear property
           │
           ▼
compatibility PG element/hook
           │
           ▼
Power Grid MNA/Newton solve
```

The project SHOULD prefer an exact or equation-equivalent PG integration over a one-tick external approximation.

---

# 16. Dynamic property ownership

Every electrical state variable MUST have exactly one advancement path while compatibility mode is active.

A capacitor charge/state, inductor current/history, accumulator state, AC phase, nonlinear iteration state, or equivalent variable MUST NOT be advanced once by CEE's solver and again by PG compatibility logic.

This is a correctness invariant.

If a CEE stateful property remains the authoritative holder of its own internal state, its `tick`/`afterTick` or equivalent lifecycle SHALL be called from the PG-controlled timestep lifecycle exactly as required and not from an additional CEE solve.

---

# 17. Nonlinear component integration

CEE nonlinear elements SHALL participate directly in Power Grid's Newton iteration or an equivalent solver-hook mechanism.

The normal architecture MUST NOT model a CEE nonlinear component as a previous-tick equivalent source detached from the current Newton iteration.

For CEE properties that express their nonlinear behavior through iterative Norton linearization, World-Wide Power Grid SHOULD update conductance/current-source contributions using PG solver hooks during the same solve.

Representative nonlinear tests MUST include forward bias, reverse bias, switching/transitional operation, and interaction with PG-native nonlinear elements where applicable.

---

# 18. Transformer and coupled-element integration

CEE transformer/coupled properties SHALL be represented as direct PG coupling constraints or equation-equivalent PG constructs.

Transformer interoperability MUST preserve, within solver tolerance:

- winding ratio;
- voltage relationship;
- current relationship;
- impedance reflection behavior;
- orientation/polarity semantics used by the CEE device.

A transformer MUST NOT be approximated through an FE bridge or a one-tick two-port equivalent unless a future specification explicitly defines such behavior as equivalent for that component.

---

# 19. Authoritative timestep and microticks

There MUST be one authoritative electrical timestep schedule.

When World-Wide Power Grid compatibility mode is active, **Power Grid's electrical timestep/multitick system SHALL be authoritative**.

CEE dynamic electrical properties SHALL be advanced according to that same PG substep schedule.

For `N` PG electrical substeps, the conceptual lifecycle is:

```text
for each electrical substep:
    prepare/update CEE dynamic models
    participate in PG solve
    commit/update CEE dynamic state
```

CEE's native microtick configuration MUST NOT result in a second independent solve.

Compatibility mode MAY ignore, override, or reinterpret CEE's native microtick setting. Any user-visible configuration behavior must be documented.

The implementation SHALL retain enough per-substep electrical state to reproduce CEE-required RMS/result behavior.

---

# 19A. Future asynchronous execution

Compatibility-provided Power Grid electrical elements SHALL be designed so that their numerical solver callbacks do **not** require access to mutable Minecraft world state.

Gameplay/world state required by an electrical calculation SHOULD be captured during a defined **prepare phase** before the numerical solve begins. The electrical solver SHALL then operate only on solver-owned state, immutable snapshots, captured scalar/model data, or other data that is safe to consume without reading mutable world objects.

Gameplay effects produced by the solved electrical state SHALL be applied during a defined **commit phase** after the solve completes. Solver callbacks MUST NOT directly mutate blocks, block entities, entities, levels, inventories, CEE gameplay SavedData, or other mutable Minecraft world state merely because the initial implementation happens to run synchronously.

The initial implementation MAY execute prepare, solve, and commit synchronously on the server thread. This does not relax the architectural separation.

The required conceptual boundary is:

```text
MUTABLE WORLD / GAMEPLAY STATE
            │
            ▼
         PREPARE
 capture immutable electrical inputs
            │
            ▼
          SOLVE
 no mutable-world reads or writes
            │
            ▼
          COMMIT
 apply resulting gameplay effects
            │
            ▼
MUTABLE WORLD / GAMEPLAY STATE
```

This requirement exists so that a future Power Grid implementation or World-Wide Power Grid execution path MAY move the numerical electrical solve off-thread without requiring a redesign of compatibility-provided electrical elements.

Asynchronous solving itself is not required for the initial release, but **asynchronous-safe solver boundaries are part of the finished architecture**.

---

# 19B. Topology mutation ownership

All compatibility-induced electrical topology mutations SHALL pass through a centralized **topology manager** or equivalent single ownership boundary.

Compatibility code outside that owner MUST NOT directly add, remove, migrate, reconnect, split, merge, or otherwise structurally mutate compatibility-provided Power Grid nodes, wires, couplings, endpoint mappings, or related network topology.

Topology mutations SHALL NOT occur while an electrical solve is in progress.

World events, device changes, wire placement/removal, chunk lifecycle changes, and similar systems MAY request topology changes at any time permitted by Minecraft's lifecycle, but those requests SHALL be queued, coalesced, or otherwise deferred until a defined topology-mutation phase in which no authoritative electrical solve is executing.

The target ownership model is:

```text
world/device/wire events
          │
          ▼
 topology change requests
          │
          ▼
 CENTRAL TOPOLOGY MANAGER
          │
   safe mutation phase
   (solver not running)
          │
          ▼
 persistent PG topology
          │
          ▼
      electrical solve
```

The topology manager SHOULD also be the authority responsible for preserving stable identities, avoiding duplicate mutations, ordering dependent mutations, and invalidating only the affected structural regions where practical.

This requirement is a correctness and concurrency invariant, not merely an organizational preference.

---

# 20. CEE result bridge

After the authoritative PG solve, World-Wide Power Grid SHALL construct results consumable by normal CEE gameplay logic.

The result bridge MUST provide valid solved values for mapped CEE nodes and internal nodes required by CEE result queries.

It MUST support the information needed by CEE's normal gameplay, including as applicable:

- node voltage;
- voltage difference;
- RMS voltage;
- branch current queries;
- heat-loss calculations;
- per-substep voltage history used by CEE behavior.

Where CEE's existing `SimulationResults` can be constructed correctly from PG-produced values, the compatibility layer SHOULD use that native class rather than replace downstream CEE logic.

CEE `postTick` methods SHOULD receive ordinary CEE-compatible results without needing to know which solver produced them.

---

# 21. CEE physical wires

CEE wires SHALL remain CEE-owned physical/gameplay objects.

CEE remains responsible for applicable:

- rendering;
- sag;
- item identity;
- placement workflow;
- maximum-length behavior;
- physical attachment state;
- CEE-specific thermal state;
- smoke;
- overheating;
- breakage/failure;
- related gameplay effects.

Electrically, each loaded/active CEE wire SHALL have an equivalent representation in the authoritative PG electrical topology using the CEE wire's actual electrical resistance/model.

There SHALL NOT be a duplicate independent CEE electrical wire solve.

After PG solves, CEE SHALL receive sufficient solved node state for its normal thermal/failure calculations.

---

# 22. Power Grid physical wires

PG wires connected to CEE terminals SHALL remain normal PG wire entities.

They SHOULD retain normal PG behavior including applicable:

- resistance;
- length and item-cost rules;
- rendering;
- wire types;
- terminal restrictions;
- transmission-line behavior;
- loading/unloading behavior;
- PG-native current/thermal behavior.

Connecting a PG wire to a CEE terminal MUST NOT create a duplicate CEE physical wire.

---

# 23. Physical topology vs. simulation topology

World-Wide Power Grid SHALL maintain a clear distinction between:

**Physical/gameplay topology:** owned by the mod that created the machine or wire.

**Electrical simulation topology:** owned by Power Grid while compatibility mode is active.

Therefore it is valid and expected that:

```text
CEE wire physical object
          │
          └──── represented electrically by PG element
```

and:

```text
CEE machine/gameplay device
          │
          └──── represented electrically by PG sidecar topology
```

This separation is fundamental to the architecture.

---

# 24. Scale is a normal use case

A server-wide connected electrical network SHALL be considered a normal supported scenario, not an edge case.

The architecture MUST remain valid when many players gradually connect local factories into one large grid containing CEE and PG content throughout.

The project MUST NOT rely on any assumption that:

- mixed networks are rare;
- CEE sections remain small;
- PG and CEE networks usually remain isolated;
- compatibility only touches boundary devices;
- only a few compatibility nodes exist at once.

A world where most or all loaded CEE electrical devices participate in PG networks is explicitly within scope.

---

# 25. Performance design requirements

Performance is part of correctness for the finished product.

The final architecture MUST satisfy all of the following:

1. CEE's independent matrix solver does not execute in compatibility mode.
2. The complete translated CEE graph is not deleted and recreated every tick.
3. Stable topology remains stable in PG.
4. Numerical changes update existing PG elements where possible.
5. Unchanged CEE content causes no unnecessary PG topology churn.
6. Compatibility bookkeeping scales primarily with changed state rather than total electrical world size.
7. Compatibility does not add a second global DFS/reoptimization pass every tick merely because CEE is installed.
8. Power Grid's sparse/native solver optimizations remain usable.
9. The server does not wait on a disabled CEE electrical worker.
10. Cross-mod interoperability does not require running two electrical solvers.
11. Solver callbacks avoid mutable-world access so numerical solving can be moved off-thread later without redesigning compatibility elements.
12. Compatibility-originated topology mutations are centralized and never race an in-progress electrical solve.

---

# 26. Topology reduction

World-Wide Power Grid MAY implement persistent electrical topology reduction to keep large CEE-heavy networks efficient.

Useful reductions may include:

- series-resistance collapse;
- dead passive-branch removal;
- parallel passive-element reduction;
- star/delta reduction where mathematically valid;
- transformer-side equivalent reduction where exact for the supported model.

Any such reduction SHOULD be cached/persistent and invalidated only when relevant topology or parameters make the reduction invalid.

The implementation SHOULD NOT rerun a global expensive reduction pass every tick when the topology is unchanged.

Topology reduction MUST preserve the electrical quantities required to reconstruct downstream CEE result queries and gameplay effects.

---

# 27. Network partitioning

Power Grid's existing ability to split large networks through transmission-line/weak-coupling behavior MAY be used where it is already part of PG's normal configured behavior.

World-Wide Power Grid MUST NOT silently introduce additional delayed boundary approximations merely to make compatibility easier.

If future compatibility-specific partitioning changes transient behavior compared with one simultaneous solve, it MUST be:

- explicitly documented;
- configurable where appropriate;
- validated for intended fidelity;
- not represented as mathematically identical when it is not.

---

# 28. Lifecycle correctness

World-Wide Power Grid MUST correctly handle the complete lifecycle of electrical content.

Required lifecycle cases include:

- world load;
- world save;
- server restart;
- dimension load/unload;
- block placement;
- block removal;
- device creation;
- device destruction;
- PG wire placement/removal;
- CEE wire placement/removal;
- thermal wire failure;
- chunk load;
- chunk unload;
- machine configuration changes;
- terminal configuration changes;
- network merge;
- network split;
- source/load state changes.

A stale compatibility node or element MUST NOT remain permanently in the PG graph after its owning CEE object no longer exists.

Lifecycle handlers that discover a required electrical topology change SHALL request that change through the centralized topology manager rather than mutating the PG graph directly.

---

# 29. Moving structures, sublevels, and coordinate transforms

Where the supported upstream versions allow electrical content to exist in Create contraptions, trains, sublevels, Sable-projected spaces, or other transformed environments, World-Wide Power Grid SHOULD preserve correct endpoint identity and electrical mapping through those systems.

The compatibility implementation MUST NOT assume that every electrical endpoint is permanently fixed to a single vanilla world-space coordinate when the supported upstream behavior says otherwise.

Cross-mod endpoint position, persistence, and migration logic MUST be validated for any transformed-world systems included in the declared compatibility scope.

---

# 30. Persistence

Compatibility mappings that cannot be safely and deterministically reconstructed from authoritative mod-owned world state MUST be persisted.

Persisted compatibility data MUST be versioned.

World reload MUST NOT cause:

- duplicate PG elements;
- duplicate hidden CEE endpoints;
- disappearing valid cross-mod wires;
- incorrect terminal reassignment;
- duplicate networks;
- stale mappings that corrupt connectivity.

Where practical, the original CEE/PG machines and physical wires SHOULD remain the authoritative persisted objects, with compatibility topology reconstructed deterministically.

---

# 31. Failure policy

World-Wide Power Grid MUST fail predictably when it encounters an unsupported electrical construct.

It MUST NOT silently fall back to:

- FE conversion;
- an arbitrary voltage/current scale;
- a previous-tick equivalent source bridge;
- duplicate solving;
- electrically disconnected behavior presented as supported.

During development, an unsupported construct SHOULD:

1. emit a clear diagnostic identifying the device/property;
2. avoid corrupting unrelated network state;
3. avoid double-advancing the affected component;
4. enter a predictable safe electrical representation or explicit unsupported state.

For a production release claiming full compatibility with a declared CEE version, encountering a normal built-in CEE electrical property as unsupported SHALL be considered a bug.

---

# 32. Debugging and observability

The finished project SHOULD provide developer/administrator diagnostics for interoperability state.

Diagnostics SHOULD be capable of exposing:

- CEE node → PG node mapping;
- PG terminal → hidden CEE endpoint mapping where used;
- number of compatibility nodes;
- number of compatibility elements/wires;
- compatibility topology additions/removals for the current tick;
- queued topology mutations and the topology-manager phase in which they are applied;
- parameter updates for the current tick;
- unsupported CEE property classes;
- PG network IDs containing compatibility content;
- synchronization/prepare time;
- numerical solve time where accessible;
- result/commit time;
- orphaned/stale mappings;
- selected-machine mapping inspection.

These tools are for development and administration and SHOULD NOT be required for ordinary gameplay.

---

# 33. Compatibility isolation and maintainability

World-Wide Power Grid SHOULD minimize invasive changes to both upstream mods.

Targeted Mixins, redirects, accessors, and event hooks are acceptable where necessary, but SHOULD intercept narrow lifecycle boundaries rather than replacing unrelated systems.

Existing CEE and PG abstractions SHOULD be reused where practical.

The project SHOULD NOT require permanent forks of either upstream project unless a future technical limitation makes that unavoidable and the decision is explicitly documented.

Compatibility code SHOULD be organized around stable concepts such as:

- endpoint mapping;
- CEE graph synchronization;
- centralized topology mutation ownership;
- prepare/solve/commit phase separation;
- property translation;
- dynamic/nonlinear solver hooks;
- result bridging;
- persistence;
- lifecycle integration.

---

# 34. Version compatibility

Every released version MUST declare which CEE and PG versions or version ranges are supported.

Support MUST be based on verified behavior, not merely assumed API similarity.

If an upstream update introduces:

- a new CEE electrical property;
- changed CEE device lifecycle semantics;
- changed PG node/network semantics;
- changed PG solver-hook behavior;
- changed endpoint serialization/placement behavior;

then full-support status MUST be revalidated before the compatibility release claims support for that upstream version.

---

# 35. Behavior when compatibility is not active

World-Wide Power Grid SHALL activate its backend replacement only when the required supported mods are present and compatibility mode is applicable.

CEE running without World-Wide Power Grid MUST retain its native behavior.

Power Grid running without World-Wide Power Grid MUST retain its native behavior.

PG-only electrical networks MUST NOT be materially altered simply because World-Wide Power Grid is installed.

---

# 36. Explicit non-goals

World-Wide Power Grid is not intended to:

- replace Power Grid's electrical solver;
- create a third electrical solver;
- convert either mod to FE;
- merge both upstream mods into one distribution;
- redesign CEE progression;
- redesign PG progression;
- require compatibility blocks or items;
- preserve CEE solver execution merely for architectural purity;
- maintain two authoritative copies of the same electrical network;
- make players understand internal solver ownership;
- promise identical behavior for unsupported future upstream versions without validation.

---

# 37. Required functional connection matrix

A release claiming full interoperability MUST pass all of the following connection classes:

```text
PG machine  -- PG wire  -- PG machine
CEE machine -- CEE wire -- CEE machine

CEE machine -- PG wire  -- PG machine
PG machine  -- PG wire  -- CEE machine

CEE machine -- CEE wire -- PG machine
PG machine  -- CEE wire -- CEE machine

CEE machine -- PG wire  -- CEE machine
PG machine  -- CEE wire -- PG machine

CEE -- CEE wire -- PG -- PG wire -- CEE
PG  -- PG wire  -- CEE -- CEE wire -- PG
```

The same classes MUST also work inside:

- branched networks;
- loops/meshes;
- multiple-source networks;
- mixed source/load networks;
- networks containing capacitors/inductors;
- nonlinear networks;
- transformer-containing networks;
- large connected networks.

A connection working visually while being electrically disconnected does not count as a pass.

---

# 38. Electrical fidelity acceptance criteria

The compatibility implementation SHALL be validated against representative reference circuits.

## 38.1 Static DC

After convergence, representative node voltages and branch currents SHOULD agree with expected/reference behavior within:

- **0.1% relative error**, or
- the applicable solver tolerance where the true value is near zero.

## 38.2 Stateful/transient circuits

Capacitor, inductor, accumulator, and equivalent dynamic behavior SHOULD remain within **1% error** over defined transient test windows, unless a difference is intentionally caused by the documented canonical PG timestep policy.

## 38.3 Nonlinear circuits

Diodes and other nonlinear CEE properties MUST converge correctly across representative:

- forward-biased operation;
- reverse-biased operation;
- turn-on/turn-off transition;
- interaction with reactive elements;
- interaction with PG-native nonlinear elements where supported.

## 38.4 Transformers/couplings

Transformer ratios, current relationships, and reflected impedance behavior MUST match the CEE-defined model within solver tolerance.

## 38.5 CEE gameplay consequences

CEE overheating, damage, failure, charging state, and similar solved-electrical consequences SHOULD occur within **one game tick** of their expected reference behavior under equivalent conditions.

Material deviations MUST be fixed or explicitly accepted/documented before a release claims full compatibility.

---

# 39. Performance acceptance benchmark

The final release SHALL NOT be considered complete until it passes a documented multiplayer-scale benchmark.

The reference benchmark MUST contain:

- one connected PG electrical system;
- both CEE and PG machines;
- both CEE and PG physical wires;
- at least **10,000 electrical nodes**;
- at least **15,000 conductive/coupled edges**;
- at least **1,000 stateful or actively changing electrical devices**;
- load/source activity representative of a large multiplayer factory server;
- a sustained stable-topology measurement phase;
- a separate controlled topology-change phase.

The test scenario SHOULD be representative of the electrical complexity expected from approximately **50 active factory-building players**, while acknowledging that player count itself is not the direct computational variable.

With Power Grid's native backend enabled on documented reference hardware:

- the benchmark SHOULD sustain **20 TPS** during the stable-topology phase;
- p95 server tick time SHOULD remain at or below **50 ms** during that phase;
- compatibility-originated topology additions/removals SHOULD be zero when no physical/electrical topology changed;
- compatibility synchronization outside the PG solver SHOULD remain a minority of total electrical simulation cost.

The benchmark publication MUST identify:

- CPU/hardware;
- Java version;
- Minecraft version;
- CEE version;
- PG version;
- World-Wide Power Grid version;
- PG solver backend;
- relevant electrical simulation configuration;
- node/edge/device counts;
- benchmark duration;
- TPS/tick-time statistics.

Functional correctness MUST also be validated with the supported non-native PG solver backend even if the large-scale performance target is measured using the native backend.

---

# 40. Stable-topology acceptance criteria

In a world where electrical topology is unchanged for a sustained test period:

- compatibility PG node creation count MUST remain zero after initial synchronization;
- compatibility PG node deletion count MUST remain zero;
- compatibility structural wire/coupling addition/removal count MUST remain zero except for upstream-required maintenance behavior;
- dynamic numerical values MAY change every substep;
- CEE's native independent solver MUST execute zero solves;
- stateful CEE properties MUST advance exactly once per authoritative PG electrical substep;
- compatibility solver callbacks MUST NOT require mutable-world access;
- no compatibility topology mutation may execute concurrently with the authoritative electrical solve.

This test specifically protects the product against accidental regression to per-tick graph rebuilding or unsafe solver/world coupling.

---

# 41. Dynamic topology acceptance criteria

The following actions MUST work without server restart or manual refresh:

- connect PG wire to CEE terminal;
- disconnect PG wire from CEE terminal;
- connect CEE wire to PG terminal;
- disconnect CEE wire from PG terminal;
- place/remove a CEE electrical machine;
- place/remove a PG electrical machine;
- add/remove a branch;
- add/remove a source;
- open/close/reconfigure a switch;
- change transformer configuration;
- destroy a CEE device through gameplay;
- break a CEE wire through its normal failure behavior;
- split a previously connected grid;
- merge previously separate grids.

Only the affected topology SHOULD be invalidated where practical.

All compatibility-induced structural changes in this suite MUST be routed through the centralized topology manager and applied only outside an active electrical solve.

---

# 42. Restart and persistence acceptance criteria

A test world containing all supported cross-mod connection directions MUST survive:

- save;
- clean shutdown;
- server restart;
- chunk unload/reload;
- player departure/return;
- network split;
- network remerge.

After reload:

- every valid physical wire MUST remain connected;
- every terminal MUST map to the intended electrical node;
- no duplicate compatibility electrical elements may exist;
- no stale compatibility node may remain from a deleted object;
- stateful CEE electrical behavior MUST continue rather than reset incorrectly;
- the PG network topology MUST represent the same physical electrical system as before shutdown.

---

# 43. Player-experience acceptance criteria

A normal player SHOULD NOT need to know World-Wide Power Grid's internal translation system exists.

The intended user rule is:

> **If the target is a compatible electrical terminal and the wire type is otherwise valid, the wire should connect normally.**

Ordinary gameplay MUST NOT require:

- compatibility commands;
- debug overlays;
- adapter recipes;
- conversion blocks;
- manual proxy-node creation;
- special network synchronization steps.

Compatibility-specific diagnostics MAY exist for administrators and developers.

---

# 44. No-adapter acceptance criterion

The following does **not** satisfy this specification:

```text
CEE network
    │
adapter block
    │
PG network
```

Neither does:

```text
CEE solver
    │
last-tick voltage/current
    │
equivalent source
    │
PG solver
```

Nor:

```text
CEE electricity
    │
convert to FE
    │
convert back to PG electricity
```

World-Wide Power Grid requires direct circuit-level participation in the authoritative PG electrical topology.

---

# 45. Definition of full interoperability

For this project, **full interoperability** means all of the following are simultaneously true for the declared supported upstream versions:

- Power Grid is the only authoritative electrical solver for compatible CEE electrical content.
- Every built-in supported CEE electrical primitive has a correct PG-side representation.
- Every built-in supported CEE electrical device behaves correctly through that representation.
- CEE physical wires have correct PG-side electrical representations.
- PG wires connect directly to CEE terminals.
- CEE wires connect directly to PG terminals.
- No player-visible adapter is required.
- No FE conversion is involved.
- No previous-tick source-equivalent bridge separates the systems.
- Stateful components advance exactly once.
- Nonlinear components participate in the authoritative current solve.
- CEE gameplay and damage behavior continue to operate.
- Cross-mod networks survive save/reload and chunk lifecycle events.
- Large server-wide connected networks are supported by the architecture.
- Persistent PG topology is synchronized incrementally rather than rebuilt each tick.
- Compatibility-provided solver elements maintain prepare/solve/commit separation and are not coupled to mutable world access during numerical callbacks.
- Compatibility-induced topology changes have centralized ownership and never execute during an active solve.
- Ordinary unsupported CEE content is treated as a defect, not an expected limitation.
- The functional, fidelity, persistence, dynamic-topology, and performance acceptance tests in this specification pass.

Until these conditions are met, the project MAY describe itself as experimental, partial, alpha, beta, or in-progress, but SHALL NOT claim complete/full interoperability for that version pair.

---

# 46. Release completion criteria

A **1.0 / complete** release may be declared only when all sections below pass.

## 46.1 Electrical backend

- [ ] CEE's independent electrical network solver executes zero solves in compatibility mode.
- [ ] PG is the sole authoritative electrical backend.
- [ ] No electrical state variable is double-advanced.

## 46.2 Device/model coverage

- [ ] Every built-in CEE electrical primitive for the declared version is supported.
- [ ] Every built-in CEE electrical device is representable through supported primitives or an intentional device-specific adapter.
- [ ] No normal supported device emits an unsupported-property diagnostic.

## 46.3 Wire interoperability

- [ ] PG wire → CEE terminal works directly.
- [ ] CEE wire → PG terminal works directly.
- [ ] Same-mod wire behavior remains intact.
- [ ] No adapter block/item is required.

## 46.4 Electrical correctness

- [ ] Static/DC acceptance suite passes.
- [ ] Reactive/transient acceptance suite passes.
- [ ] Nonlinear acceptance suite passes.
- [ ] Transformer/coupling acceptance suite passes.
- [ ] Mixed PG-native + CEE-property circuits pass.

## 46.5 Gameplay correctness

- [ ] CEE `postTick` behavior receives valid results.
- [ ] CEE thermal behavior works.
- [ ] CEE damage/failure behavior works.
- [ ] CEE wire failure behavior works.
- [ ] CEE user-facing controls remain functional.

## 46.6 Persistence/lifecycle

- [ ] Restart suite passes.
- [ ] Chunk load/unload suite passes.
- [ ] Network merge/split suite passes.
- [ ] No stale or duplicated compatibility objects remain.

## 46.7 Performance and concurrency architecture

- [ ] Persistent topology is used.
- [ ] Stable networks cause no wholesale graph recreation.
- [ ] Stable sparse structure is preserved where possible.
- [ ] Defined 10k-node/15k-edge benchmark passes.
- [ ] No CEE solver-thread synchronization barrier remains in the compatibility solve path.
- [ ] Numerical solver callbacks do not depend on mutable Minecraft world state.
- [ ] A clear prepare/solve/commit boundary exists even if execution is initially synchronous.
- [ ] All compatibility-induced topology mutations pass through the centralized topology manager.
- [ ] No compatibility-induced topology mutation occurs while an electrical solve is in progress.

## 46.8 User experience

- [ ] Cross-mod terminals can be wired using normal upstream wire tools.
- [ ] Compatibility internals are invisible in ordinary gameplay.
- [ ] Debug/admin tooling exists for diagnosing mapping failures.

## 46.9 Maintainability

- [ ] Supported upstream versions are explicitly declared.
- [ ] Compatibility persistence data is versioned.
- [ ] Upstream API assumptions are documented/tested.
- [ ] Implementation does not require an unnecessary permanent fork of either mod.

Passing only simple wire-placement tests is not sufficient.

Passing only resistive circuits is not sufficient.

Supporting only small networks is not sufficient.

Running both solvers and exchanging results is not sufficient.

The release target is a **single, persistent, scalable Power Grid electrical backend in which CEE content behaves as a first-class electrical participant**.

---

# 47. Specification-change policy

This document represents the destination the project is pursuing.

A code limitation SHOULD NOT be resolved by quietly weakening this specification.

Changes that materially alter any of the following require an explicit specification amendment:

- single-solver ownership;
- no-adapter requirement;
- direct wire interoperability;
- persistent topology requirement;
- prepare/solve/commit separation;
- centralized topology mutation ownership;
- electrical fidelity goals;
- scale expectations;
- release completion criteria.

A specification amendment SHOULD explain why the previous requirement is no longer desirable or technically appropriate.

Temporary implementation limitations belong in issues, milestones, or development documentation—not in a rewritten definition of “complete.”

---

# 48. Current expected implementation methodology

The following component model is the expected implementation direction. Exact class names may change, but architectural responsibility SHOULD remain recognizable.

```text
CEEEndpointManager
    CEE terminal/internal node ↔ persistent PG node

PGEndpointManager
    PG terminal ↔ invisible CEE endpoint when required by CEE physical wires

CEEGraphSynchronizer
    reads CEE desired-state graph
    diffs it against persistent compatibility state
    emits parameter updates and topology-change requests

CompatTopologyManager
    sole owner of compatibility-induced PG topology mutation
    queues/coalesces structural changes
    applies them only while no electrical solve is active

CEEPropertyAdapterRegistry
    maps CEE ElectricalProperties families to PG elements/hooks

CEEDynamicElementAdapters
    capture gameplay inputs during prepare
    preserve CEE capacitor/inductor/accumulator/AC state evolution
    expose solver-only state during solve
    defer gameplay effects to commit

CEENonlinearSolverHooks
    integrate CEE nonlinear properties into PG Newton iterations
    do not access mutable Minecraft world state during solver callbacks

CEECouplingAdapters
    map transformer/coupled properties into PG constraints

CEEWireSynchronizer
    mirrors CEE physical wire resistance/topology into PG
    requests structural mutations through CompatTopologyManager

MixedResultBridge
    captures PG solved node/substep state
    constructs CEE-compatible SimulationResults during commit

CompatSavedData
    persists only mappings/state that cannot be safely reconstructed

Diagnostics
    mapping counts, topology churn/queue state, unsupported properties, timing
```

The expected simulation sequence is:

```text
SERVER TICK

CEE / gameplay prepare phase
    │
    ├─ read mutable world/gameplay state
    ├─ create desired electrical description
    ├─ device.preTick(...)
    ├─ capture immutable solver inputs
    └─ publish graph event
    │
    ▼
World-Wide Power Grid synchronization phase
    │
    ├─ compare desired CEE state with persistent PG state
    ├─ update numerical parameters
    └─ submit structural changes to CompatTopologyManager
    │
    ▼
Topology mutation phase
    │
    ├─ solver is not running
    ├─ coalesce/apply queued structural changes
    └─ establish stable PG topology for this solve
    │
    ▼
Power Grid electrical solve phase
    │
    ├─ ONE authoritative PG solve / PG substeps
    └─ compatibility solver callbacks use solver-owned/captured state only
    │
    ▼
World-Wide Power Grid commit/result phase
    │
    ├─ gather mapped solved node values
    ├─ construct CEE-compatible results
    └─ translate solved outcomes into pending gameplay effects/state
    │
    ▼
CEE / gameplay commit phase
    │
    ├─ device.postTick(...)
    ├─ CEE wire thermal/lifetime behavior
    ├─ CEE component damage/failure
    └─ apply normal CEE synchronization/gameplay updates
```

The initial implementation MAY run every phase synchronously. The phase boundaries SHALL nevertheless remain explicit so that the numerical solve can later move off-thread without changing the electrical adapter contract.

This methodology is preferred because it preserves CEE's content model while avoiding dual-solver synchronization, retaining PG's persistent sparse-network architecture, and maintaining a concurrency-safe path toward future asynchronous execution.

---

# 49. Upstream technical references

These links identify the upstream projects whose behavior this specification is designed around:

- **Create: Electro Energetics** — https://github.com/george8188625/Create-Electro-Energetics
- **Power Grid** — https://github.com/patryk3211/PowerGrid

Important upstream implementation areas for maintainers include:

### CEE

- simulation `SimulationTicker`;
- `BridgeCollector`;
- `CircuitBuilder`;
- `SimulationResults`;
- `ElectricalProperties` hierarchy;
- `MicroTickingElectricalProperties`;
- nonlinear Norton property hierarchy;
- transformer/coupled properties;
- wire simulation/lifetime state;
- detached node infrastructure.

### Power Grid

- `ElectricalNetwork` / `GraphedElectricalNetwork`;
- global/world electrical network management;
- `ElectricBehaviour` / `IElectric`;
- block wire endpoints and terminal placement;
- `OwnedFloatingNode`;
- `ElectricWire` and transmission-line logic;
- solver inner/outer hooks;
- Java and native MNA backends.

These references are implementation context, not a substitute for the normative requirements above.

---

# 50. Final statement

World-Wide Power Grid is complete when the distinction between “CEE electricity” and “Power Grid electricity” no longer matters to the player or to the authoritative electrical solve.

CEE content should simply exist in the same electrical world as Power Grid content:

```text
          CEE                     PG
           │                      │
           └──────────┬───────────┘
                      │
             one persistent graph
                      │
               one PG solver
                      │
              one electrical truth
```

The numerical solve should additionally be isolated from mutable world state, and all compatibility topology mutation should have one safe owner.

**That is the product this repository is pursuing.**