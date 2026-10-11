# Stationary completion work

This directory records implementation and acceptance for the planned 0.1.0-beta.5.
**It is not a published release or a completed support claim.**
Work-in-progress jars use `0.1.0-beta.5-dev`; published beta.4 artifacts are unchanged.

Baseline: beta.4, commit `28da7ffca1f6972dc0db2378aa425c89753cab85`.
The dependency baseline remains fixed. The optional PINOUT profile is locked in
[`optional-artifacts.json`](../optional-artifacts.json).

Completion requires the full stationary registry audit, native player workflows,
electrical accuracy checks, both solvers, both operating systems, repeated actual
reload/restart checks, graphical and two-client acceptance, and verified exports
of the base and Pinout worlds. Old release artifacts and evidence remain unchanged.

The first implementation batch fixes cold loading and inventory persistence in the
native PG design table. Its fixtures cover saved component identities, settings,
traces, vias, labels, copying, native component assembly, board placement, mixed
power, and actual chunk cycling. Native schematic copies retain PG's intentional
creation of new component UUIDs; world saves retain the original identities.

The runtime audit accounts for 519 base registry entries and two optional Pinout
entries. The new matrix contains 164 base behavior groups and one optional group.
Every new-release row remains unverified until final acceptance. Runtime checks
compare the packaged inventory record against the complete pinned registries.

Initial powered fixtures cover the buzzer, fan, magnetic field, alarm bell, both
stationary batteries, redstone converter, spark gap, carbon pile, thermometer,
plotter, native FE conversion, and an actual CC:Tweaked Lua computer. Further
native workflows and playable stations are still required. CEE's component heat
uses abstract units; those values are not presented as Celsius readings.

[`progress.json`](progress.json) tracks the outstanding gates. Failed controls and
passing focused checks are retained under [`evidence`](evidence).

Local first-batch checks passed 139 packaged tests with BASE/native and 140 with
PINOUT/Java. After correcting a fixture that rebuilt cords before entity loading,
the four design checks also passed three consecutive cold restarts of the same
jar; each pass performed five actual chunk unload/reload cycles. This is partial
local evidence, not completion of the eight-combination release matrix. Earlier
failed runs are preserved with their explanations.

Foundation CI run [38081560620](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38081560620)
passed all eight platform/backend/profile combinations, including three cold
restarts in each. A duplicate run of the same commit,
[38081588970](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38081588970),
passed seven combinations but its Linux/native/Pinout setup was cancelled after
prolonged slowdown. Its server continued ticking and completed two actual designer
chunk cycles; it never produced a complete passing proof. Slow startup preceded
the electrical tests. The cause remains open. The retained logs and incomplete
proof are in `evidence/ci-slowdown`; CI now has a process timeout and captures JVM
thread dumps on expiration. A passing retry does not resolve this investigation.

The next equipment batch passes 151 packaged BASE/native checks and 152
PINOUT/Java checks on the same development jar (SHA-256
`15a067f4003fe410254869d91e86e1c4ad4b8bbd17d17fb1f5a4bf16d155ed78`).
It corrects three reproduced defects: a neutral CEE voltage regulator was rejected
as an invalid transformer ratio; PG Nether-transformer cold loading read the wrong
history index; and repairing or breaking a CEE bulb disconnected PG wires when its
block variant changed. Saved formats and endpoint identities are preserved.

New powered checks cover native core/radiator assemblies, regulation, bulb repair
and overload, ceiling solar installation/shading/removal, native growth-lamp crop
effects, all punch-card rows driving eight mixed loads, CRT heater/grid/deflection
electrical behavior, and external FE provider/receiver accounting. The initial CRT
fixture assumed ideal source voltages and failed; the corrected expectation uses
measured terminal voltage and PG's actual emission equation, including wire drops.
Those failed and passing logs are retained under `evidence/distribution`.
Rendered CRT beams, linked Nether operation, remaining assembly workflows,
content-specific lifecycle checks and playable demonstrations still block completion.
Test-only barrel FE capabilities are enabled only in the acceptance harness.
