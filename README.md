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

**Specification / pre-implementation.**

The repository is currently pursuing the complete product described in [`SPEC.md`](SPEC.md). That document defines the release target and completion criteria. It is intentionally **not** an MVP checklist.

Implementation milestones may be narrower, but they must move toward the specification rather than redefine “complete” around what happens to be implemented first.

## Product principles

- **One electrical solver.** Power Grid is the sole authoritative electrical solver while compatibility mode is active.
- **No adapter blocks.** PG wires connect directly to CEE terminals and CEE wires connect directly to PG terminals.
- **No FE conversion.** Interoperability is circuit-level, using real electrical quantities and models.
- **No one-tick boundary model.** The project does not join the mods through delayed Thevenin/Norton bridge blocks.
- **Persistent topology.** CEE electrical content is represented by persistent PG-side nodes/elements and synchronized incrementally.
- **CEE stays CEE.** CEE keeps ownership of its gameplay, visuals, machine state, wire rendering, thermal behavior, failures, and other non-solver mechanics.
- **Scale is a requirement.** A server-wide connected network containing both mods is considered a normal supported use case.

## Primary documents

- [`SPEC.md`](SPEC.md) — normative product target, architecture, invariants, and release completion criteria.
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — project workflow and how implementation work should relate to the specification.

## Upstream projects

- Create: Electro Energetics: https://github.com/george8188625/Create-Electro-Energetics
- Power Grid: https://github.com/patryk3211/PowerGrid

World-Wide Power Grid is an interoperability project and is not a replacement distribution of either upstream mod.

## License

A project license has **not yet been selected**. Do not assume the license of either upstream project automatically applies to this repository.
