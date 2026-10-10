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
