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
Rendered CRT beams, remaining assembly workflows,
content-specific lifecycle checks and playable demonstrations still block completion.
Test-only barrel FE capabilities are enabled only in the acceptance harness.

The native-workflow batch adds real item interactions for horizontal/vertical
large generators, installed coils and winding housings, two through five segment
CEE poles, mounted insulator-controlled breakers, all four stationary catenary
styles, string-light dyeing/placement/cutting, fan washing, electromagnet recipes,
banner patterns and dampers, and native attachment/spool recovery. Supplementaries
bunting requires another optional mod and is explicitly deferred; the empty wire
attachment registry entry is a sentinel rather than a player item.

Three more reproduced defects are corrected in this batch. Odd-height CEE poles
checked one block beyond their top and failed to conduct. A PG-only native Nether
transformer circuit amplified stale cross-dimension samples after shutdown:
clustering substeps reached `2.581177438983389E11 V` in the isolated Java control,
while interleaving the same native equations produced approximately zero volts.
WWPG now prepares every dimension before advancing each PG substep across them.
CEE gameplay still commits once, and no replacement transformer model is added.
The deferral flag is local to the server thread so client Ponder scenes continue
using their own native simulation.

The second Nether defect left a link to an unloaded controller because Create
skips `remove()` during chunk unload. WWPG detaches that endpoint through PG's
existing link table, with a guard against detaching a replacement. Native saved
identities, history format, wire drops and assembly requirements are preserved.
The new durable mixed Nether fixture alternates actual end unloads, delays entity
loading, checks unloaded and unready source isolation, and compares restored
history with the actual serialized chunk data before its first solve. It keeps
the circuit for separate-process cold restarts, including a saved banner and
native wire identities. The final eight-combination and graphical release gates
remain outstanding.

This batch passes 169 packaged BASE/native checks and 170 PINOUT/Java checks on
one frozen development jar (SHA-256
`92ed114972153ccf25f46326ca31375786867bef6fa1e914acd027faad432c0a`).
Each locally tested profile/backend also passes three consecutive separate-process
cold restarts, with 21 distribution checks and five actual independent Nether-end
unload/reload cycles in each pass. Logs, artifact proofs, reproduced controls and
fixture corrections are retained in [`evidence/native-workflows`](evidence/native-workflows).
The first full Java run exposed a fixture collision: the prepared Nether portal
shared coordinates with the older dimension test's source block. Reserved fixture
coordinates correct that collision; both failed runs remain in the evidence.

The foundation and equipment stage CI runs
[38083631940](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38083631940)
and [38083716279](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38083716279)
pass all eight combinations. Those runs cover their respective stage commits,
not this later batch or the completed beta.5 release.

PR #11's first complete CI run failed the saved-board charge assertion in all
eight combinations. A second fixture collision was reproduced locally: the
destructive stationary portal used the exact position of the persistent
electronics source. Full VERIFY runs replaced that source before checking the
board, allowing it to discharge. The earlier distribution-only cold passes did
not exercise this interaction. The portal fixtures now use a separate reserved
area. The strict pre-solve charge assertion and saved electronics coordinates
remain unchanged. Failed CI and local controls are retained in
[`evidence/restart-fixture-isolation`](evidence/restart-fixture-isolation).
Fresh full suites pass locally; complete cold and CI verification are pending.

The electrical-accuracy batch passes 182 packaged BASE/native and 183 PINOUT/Java
checks locally, including three consecutive complete cold runs per tested profile.
The strict saved-board assertion now passes in those full restarts, after moving
the destructive portal fixtures. DC and transformer references check 0.1% errors;
270 recorded capacitor and 270 inductor points per backend check 1% windows at
1, 2 and 16 substeps. Native AC, reactive transformer phase/RMS, winding current
and reflected impedance are also checked. Evidence records each exact artifact,
including the added, unexecuted Lua test resource between SETUP and cold runs.

A graphical run of the extracted older yard exposed a separate real display bug:
an odd saved native sample count, advanced by 16 each tick, never reached CEE's
`count % 10 == 0` display update condition. Frequency and synchroscope measurements
continued internally while their displayed values froze. An elapsed-sample check
retains the native throttle and value-change rules. Both regressions pass on
native and Java. The native client also passes all 69 legacy-yard views,
synchronized readings, wire rendering, and real panel interaction packets.
That yard regression does not replace the complete beta.5 demonstrations.
Controls, fixture corrections, recorded samples and proofs are retained in
[`evidence/accuracy`](evidence/accuracy). Native timing comparisons, accumulator
and battery windows, nonlinear mixed circuits and final acceptance remain open.

A fresh extracted legacy-yard client run also passes all 69 views with the Java
backend and both optional mods installed, including synchronized gauges and
actual panel interaction packets. Both client helpers shut down after completing
the checks. This confirms optional-profile startup; playable computer-control
stations and the new yard acceptance remain pending. Its log and exact jar/ZIP
checksums are included in the accuracy evidence.
