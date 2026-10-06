# World-Wide Power Grid

> **One electrical world. Two great content ecosystems. One authoritative solver.**

World-Wide Power Grid is a compatibility project for **Create: Electro Energetics (CEE)** and **Power Grid (PG)**.

The finished product is intended to make CEE electrical content participate directly in Power Grid's electrical simulation. When the compatibility layer is active, Power Grid is the authoritative electrical backend for supported CEE devices and wires, while CEE remains responsible for its blocks, machines, visuals, controls, thermal behavior, damage systems, wire gameplay, and other native mechanics.

The goal is not to bridge two independent simulators. The goal is to make them behave like one electrical ecosystem.

```text
CEE machines ─┐
CEE wires ────┼────► compatibility layer ────► persistent Power Grid graph ────► Power Grid solver
PG machines ──┤                                                        │
PG wires ─────┘                                                        └────► CEE-compatible results
```

## Project status

**Implementation in progress; no first release has been certified.**

The long-term product is described in [`SPEC.md`](SPEC.md). The bounded first release uses [`docs/FIRST_RELEASE.md`](docs/FIRST_RELEASE.md), targeting **CEE 1.1.3 and PG 0.6.2 on Minecraft 1.21.1 / NeoForge**. Its priority is working stationary factory items across both mods.

Implementation milestones may be narrower, but they must move toward the specification rather than redefine “complete” around what happens to be implemented first.

## Product principles

- **One electrical solver.** Power Grid is the sole authoritative electrical solver while compatibility mode is active.
- **No adapter blocks.** PG wires connect directly to CEE terminals and CEE wires connect directly to PG terminals.
- **No FE conversion.** Interoperability is circuit-level, using real electrical quantities and models.
- **No one-tick boundary model.** The project does not join the mods through delayed Thevenin/Norton bridge blocks.
- **Persistent topology.** CEE electrical content is represented by persistent PG-side nodes/elements and synchronized incrementally.
- **CEE stays CEE.** CEE keeps ownership of its gameplay, visuals, machine state, wire rendering, thermal behavior, failures, and other non-solver mechanics.
- **Scale is a long-term requirement.** The first functional release checks ordinary stationary factories; server-wide scale acceptance follows later.

## Primary documents

- [`SPEC.md`](SPEC.md) — normative product target, architecture, invariants, and release completion criteria.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — project workflow and how implementation work should relate to the specification.
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — fixed dependencies, build/tests, native binary setup and simulation lifecycle.
- [`release/content-matrix.json`](release/content-matrix.json) — release inventory, acceptance circuits and verification status.

## Upstream projects

- Create: Electro Energetics: https://github.com/george8188625/Create-Electro-Energetics
- Power Grid: https://github.com/patryk3211/PowerGrid

World-Wide Power Grid is an interoperability project and is not a replacement distribution of either upstream mod.

## License

A project license has **not yet been selected**. Do not assume the license of either upstream project automatically applies to this repository.
