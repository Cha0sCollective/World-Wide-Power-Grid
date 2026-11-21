# CEE 1.2 development readiness

The preparation branch `dev/cee-120-readiness` integrates WWPG **0.2.11**,
main commit `ae25c2a42bf416f7d2899fb5b6a8e3263c611a8f` (release source
`96e475d2ec476a90c14a2e1ed48a001ef3307e8d`). The current development candidate
is `0.2.11-dev.cee120.3`, targeting CEE **1.2.0-132** and PG **0.6.2**.

## Transformer node references: 0.2.11-dev.cee120.3

The reporter's transformer video shows a single-node hover reading alongside
the separate winding-voltage display. Both are native CEE behavior, but the
floating node values were different under WWPG: stock CEE 1.1.4 and 1.2.0-132
gave approximately 0 / 5 kV in the 23 kV, ratio-4.6 fixture, while the published
`.8` alpha gave 9 / 14 kV on Native and Java. Every case retained the correct
5 kV winding difference. The previous ratio-only comparison did not establish
stock-equivalent node references. Fresh stock controls are preserved under
`local/node-hover-132/stock-132-01` and `stock-114-01`.

CEE 1.2 transformer networks now select a numerical zero separately for each
conductively connected region. The transformer coupling keeps both windings
in one solver network without combining their voltage references. Ordinary
CEE and native PG wires still join those regions. Physical earth takes
precedence within its own region; it no longer suppresses an isolated winding's
reference. Existing numerical anchors are excluded from physical-earth detection.
CEE's native priority and node-hash tie break choose each zero, while the existing
dynamic-history retention remains in place. Legacy CEE and networks without
CEE transformer stamps retain their prior selection. Native PG transformer
couplings retain their existing treatment; this repair targets CEE windings.

On the frozen `.3` player and companion, Native and Java each pass four new
transformer checks plus all four electrocution checks. Each solver also passes
32 readiness, lifecycle and solver-safety checks, including signed ratio changes,
regulators, physical grounds, reference changes, chunk reloads and rejected-solve
history recovery. The new fixtures cover floating 0 / 5 kV, grounded -5 / 0 kV,
ground removal, primary-only grounding, native PG bond/removal and cascades.
All 158 JVM checks pass. Exact artifacts, sources, loaded runtime audits and logs
are preserved under `local/node-hover-132/fix-02-*`. These 80 focused GameTests
are new `.3` evidence; they are not full headless or graphical acceptance.
All 66 focused runner/packaging/catalog checks pass; the catalog classifies 86
namespaces. A diagnostic companion with only its version requirement adapted
to `.2` fails all four new transformer checks against the preserved pre-fix
player, each exposing the former approximately 9 kV reference. Original jars
and prior evidence remain unchanged. See `fix-verification.json` locally.

Player SHA256:
`8536f2efcb5b2af88b0b6757c08653ec9b7bb940e15442b5405bf80c550147d4`.
Companion SHA256:
`3882a9aeec583375f2fbf707d5bc65e1933aba888d3fc6509be0c86c90b7e4ae`.

## Electrocution and contact readings: 0.2.11-dev.cee120.2

CEE's contact builder allocated a new central body node on each build. With
WWPG's persistent PG networks this repeatedly restarted warm-up. Unavailable
wire results also cleared native hazard detection, dropping and rediscovering
the contact before it could settle. Fresh-world probes reproduced an unharmed
mob and unavailable wire readings on both solvers; stock CEE damaged the mob.

The modern-only contact mixin now reuses the previous contact's central node,
matching CEE's existing ground and cut-node reuse. Each native connection
retains its last accepted hazard voltage for at most four game ticks. This
history is used only during the current bridge commit when every unavailable
endpoint/cut is known to be initializing. It preserves contact detection, not
measurement validity or damage. Real failures, isolation and restoration holds
still clear the hazard; nonfinite results remain unavailable. The history is
not serialized and has the connection's lifetime. Legacy CEE lacks these new
mixins and retains its prior hazard policy.

The four `wwpg_cee_electrocution` regressions cover real bare-wire damage,
stable body identity and settled readings, contact removal, dielectric immunity,
insulation below its rating, and rejection/no-damage/recovery beside a healthy
PG circuit. Native and Java both pass. The first parallel test attempt checked
the whole level's contact map instead of each fixture's mob; that helper failure
is preserved. It required a companion-only correction, with identical player bytes.

Player SHA256:
`f5bfc5a9d6301a8d18d9c0f14fa75fc675b4d7915eec08ffd6ad6ade817fdc5a`.
Companion SHA256:
`ba88be6a43b19bfcbbef4ffcfaef49cee60884a4c51156a22cc5276d44f5a036`.
Local evidence and immutable inputs are under `local/electrocution-fix-0211/`.
The new group passed 4/4 on Native and 4/4 on Java. The surrounding CEE 132
wiring, coordinate, readiness and solver-safety checks passed 52/52 on Native
and 39/39 on Java. CEE 1.1.4 passed 28/28 on each solver, and CEE 1.1.3 passed
28/28 on Java: 183 focused GameTests total. All 156 JVM tests and 66 Python
runner/packaging/catalog tests passed. The catalog now audits the modern fixture
source directory as well and classifies 85 namespaces.

The first Native regression wrapper counted two addon-only template namespaces
as selected tests. Minecraft actually passed all 52 selected checks. The original
wrapper failure is retained; a separate validation record checks the corrected
namespace inventory and identical runtime/companion/log hashes without describing
that execution as a fresh rerun. Subsequent Java and legacy runs are recorded
separately. See `attempt-02-validated-results.json` for the complete selection.
These are focused server checks; no graphical or full headless acceptance was run.

## 0.2.11 baseline integration: 0.2.11-dev.cee120.1

The factory-light fix rejects new copper spans between distinct PG connectors
that resolve to the same native terminal. Existing saved passive spans remain
stored without acquiring a same-node solver stamp. Topology is checked again
so a light-row split can make the saved wire electrically active. The shared
passive-property predicate uses the selected CEE integration rather than a
legacy-only `ElectricalProperties` class equality. The version-neutral graph
view is retained. The `.8` moving-wire endpoint and CEE 1.2 client preview fixes
remain present, along with reactive history migration and numerical safeguards.

Focused validation on Windows, Java 21.0.12+8, Minecraft 1.21.1, NeoForge
21.1.262, PG 0.6.2, Create 6.0.10 and Architectury 13.0.12:

| CEE runtime | Native | Java | Scope |
| --- | --- | --- | --- |
| 1.2.0-132 | 35/35 | 35/35 | Wiring, factory-light self-links, projected endpoint coordinates and eleven CEE readiness checks |
| 1.1.4 | 24/24 | 24/24 | Wiring, factory-light self-links and projected endpoint coordinates |
| 1.1.3 | 24/24 | 24/24 | Same legacy compatibility selection |

All six runs used PG at sixteen substeps, with frozen player and companion
jars and actual loaded-artifact hash checks. The source inventory and exact
commands are retained under `local/baseline-0211/`; `results.json` contains the
per-run outcomes. All 156 JVM tests and 66 readiness-runner, packaging and
catalog Python tests passed. The catalog audit classified all 84 namespaces.

Copies of the stable 0.2.11 factory-light save also upgraded to CEE 132,
saved and reopened in fresh dedicated processes with Native and Java selected
at sixteen substeps. The redundant copper span stayed saved and had no solver
branch. All four server processes saved and exited successfully, and the source
world inventory remained unchanged. The cold specimen is electrically inert;
the powered neighboring-circuit and split/reconnection assertions belong to the
separate GameTests above. `local/baseline-0211/cold-proof-v2.json` retains this
proof. The first probe's incorrect nonempty-solver assertion is preserved as a
failed helper attempt, not a passing run or a player-runtime crash.

Player SHA256:
`ba3129f1ba68102c3a2d796cb8fb06cb362b512344e2660c031339a3af0b635a`.
Companion SHA256:
`e2e383ca2b45a717959b05589e489de844c787271e4a8c828658a90848596002`.
The CEE 132 jar retains SHA256
`71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7`.

The previous review's outline-rejection, schedule-boundary, cache and protocol
proposals are separate work; this merge integrates the released baseline only.
Full headless and graphical acceptance have not been requested for this merge.
Stable 0.2.11 acceptance targets CEE 1.1.4 and does not establish CEE 1.2 acceptance.

## Historical development snapshots

The initial 0.2.10-based candidate was `0.2.10-dev.cee120.1`. Published `.8`
remains an out-of-band alpha under the owner's exact-artifact focused-check
exception. Full headless acceptance belongs to `.5`, not `.8` or this new
0.2.11-based build. All named evidence below retains its original scope and
artifact hashes. `.6` was rejected by client loading.

## Sublevel wire endpoint repair: .8

`0.2.10-dev.cee120.8` has player SHA256
`2dfe78d0b18690753ec68dd1ed4de2791cd4925fa72789b741fff4fdfd4394d0`.
The player confirmed `.7` fixes loading and placement, but placed CEE wires with
PG or Power Dock endpoints on Sable sublevels remain invisible and untargetable.
Native CEE-to-CEE wires work. The same adapter defect is present in the 0.2.10
baseline: WWPG's `InWorldNode.getPosition` override returns terminal coordinates
in the sublevel's storage frame, bypassing CEE's Sable conversion. Its render
overload also discards the supplied partial tick. CEE's wire renderer and wire
interaction handler consume those two methods, respectively.

`.8` delegates each overload to its corresponding native `toGlobalPos` conversion.
Logical positions now use CEE's sublevel projection, while rendered positions
also retain CEE's movement interpolation. Local geometry and outline coordinates
keep their existing storage frame. The shared repair covers PG and Power Dock
terminals and applies with legacy CEE as well as 1.2; native CEE geometry retains
its existing path. No electrical equations or save formats change.

The new opt-in `wwpg_wire_positions` group exercises the actual applied node mixin
with controlled logical/render transforms: translated, rotated, scaled and moving
coordinates; partial ticks; untransformed outline geometry; stationary PG; and
native CEE controls. Its coordinate regression rejects the unchanged `.7` player
with a storage-position mismatch. Both checks pass on `.8` with CEE **1.2.0-132**,
**1.1.4** and **1.1.3**, using NeoForge **21.1.262**. The negative control's companion
differs only in its WWPG version metadata. These fixtures verify the native
conversion contract; they do not assemble or visually inspect a Sable sublevel.
All thirteen preview/terminal-ray unit checks also pass on the 1.2 runtime.
A real-client title-screen loading check passes with `.8`, CEE 1.2.0-132,
Power Dock 0.3.0-preview and Sable 2.0.5. It applies both preview handlers and
executes the transformed no-world tick. It does not verify in-world wire visuals.
Compared with `.7`, only `InWorldNodeMixin.class` and mod version metadata change
in the player jar; the electrical simulation classes and native entries are identical.

Artifacts, runtime audits, focused logs and the candidate jar are retained under
`local/cee-120/client-preview-8/`. The first `positions120` setup was rejected by
the runtime audit before tests because no 1.2 checksum override was supplied;
`positions120b` supplies the expected hash and is the passing run. Full suites
were not rerun and the `.5` acceptance remains under its original identity.

## Client loading repair: .7

`0.2.10-dev.cee120.7` has player SHA256
`5a2d9f9e6e350cbf48ebe413e805e6e937824d0ea144a2eeb2dd62ae5618c0b3`.
The `.6` preview mixin declared its two static handlers with package access so
unit tests could call them directly. Mixin requires these handlers to be private
and rejected the patch before it could execute in the player's `dev128` client.
`.7` makes both handlers private; tests now invoke them reflectively and include
a regression check for the required access. The preview class differs from `.6`
by exactly the two private access flags, with identical method code. Version
metadata is the only other jar entry that changes.

All **13** focused checks pass against CEE **1.21.1-1.2.0-132**: eight preview
checks and five existing terminal-ray checks. A separate real-client title-screen
smoke reproduces `.6`'s exact non-private-static failure, then passes on `.7` with
both handlers applied and CEE's transformed `tick()` invoked on its no-world path.
The same `.7` smoke passes again with copies of the player's installed Power Dock
**0.3.0-preview** and Sable **2.0.5**. All three launches use NeoForge **21.1.262**.
No world was opened, including `My Test World` in `dev128`; in-world preview and
sublevel operation still need player verification.

The candidate jar, checksums, unit reports, negative and positive client logs,
transformed-class exports, actual input hashes and artifact comparisons are
retained under `local/cee-120/client-preview-7/`. All 307 classes from the frozen
`.5` player remain byte-identical. No full headless or full graphical suite was
rerun; `.5` acceptance retains its original identity.

The focused loading helper is documented at
[`tools/client_preview_smoke/README.md`](../../tools/client_preview_smoke/README.md).

## Previous client preview repair: .6 (loading rejected)

`0.2.10-dev.cee120.6` has player SHA256
`e6f597860ab7cc3478a15220d9d2fab1e4e9cb87bf4fbce2c44358ec1dc3315b`.
It repairs a CEE 1.2 client crash while inspecting Power Grid terminals, including
Power Dock connectors. CEE's hovered-node preview directly asks native CEE blocks
for geometry, while WWPG also makes PG terminals selectable. That leaves a null
position, which CEE 1.2 passes to Sable's range check before checking for null.
The reported crash identifies this path but does not identify the hovered block.

A client-only, CEE-1.2-only mixin fills missing hovered geometry through the
existing `InWorldNode.getLocalPosition` compatibility hook. The original CEE
conversion still runs once and preserves Sable plot coordinates. Native CEE
geometry and detached-node cache resolution retain their existing paths. If a
position remains unavailable, the range check skips Sable and lets CEE clear the
overlay normally.

Seven focused preview checks pass, covering ordinary and plot coordinates,
native geometry, unavailable positions, detached-node lookup, range delegation,
the exact build-132 injection sites and client-only registration. They pass with
the legacy unit-test runtime and again with CEE **1.21.1-1.2.0-132**; the latter
also passes five existing terminal-ray checks and verifies the loaded node class
against the requested runtime jar. The CEE jar hash remains the `.5` frozen hash
below. These checks did not apply the mixin through a client loader and missed
the static-handler access defect corrected in `.7`. They do not establish
graphical Power Dock acceptance.

The jar comparison with the frozen `.5` player finds exactly one added preview
class and changes only the client mixin registration and version metadata. All
307 existing classes and eight native entries are byte-identical. The candidate player jar,
checksum, focused reports and comparison are retained under
`local/cee-120/client-preview-6/`. Full headless and graphical suites were not
rerun for `.6`. The campaign tooling and retained `.5` acceptance keep their
original identities.

## Headless-accepted repair: .5

`0.2.10-dev.cee120.5` has player SHA256
`860a150384ed60ad8a51143afe4d84ac24323f9e55ec22d22a2f9d90c0e71308`.
Full headless run [`44649764392`](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/44649764392)
passed **398/398 jobs**, with zero failures and zero missing jobs, across Windows
and Linux, Native and Java solvers, and BASE/PINOUT profiles. The tested source is
`d8f2dd6b633b9bec1bee7e5dd9048d2ff1b6f456`.

The frozen runtime is NeoForge **21.1.262**, CEE **1.21.1-1.2.0-132**, PG **0.6.2**,
Create **6.0.10** and Architectury **13.0.12**. The actual loaded CEE SHA256 is
`71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7`.
Loaded versions and hashes for all runtime mods remain in the retained audits.

The official coverage/evidence gate passes, including the approved reference
control and all required measurement traces. All **1,204** retained objects were
hash-verified. A self-contained accepted cohort is preserved at
`local/cee-120/headless-repair-0210-1/run-44649764392-accepted-cohort/result.json`;
its relocated reports pass the same gate again. Original workflow records,
portable reports, logs, traces, frozen jars and world archives remain unchanged.

An intermittent `.3` accumulator reference failure exposed another player issue.
CEE 1.2's `BridgeCollector.Builder.connect` chooses its preferred numerical zero
from the source sign. After the test seeds charge, the accumulator nominates its
other terminal. Depending on the fixture's coordinate ordering, WWPG removed and
replaced that numerical anchor. PG then performed a warm-up solve and WWPG held
one otherwise healthy charge-history frame. The captured failing trace shows the
same circuit network, no solver error or restoration hold, an anchor move from
terminal 1 to 0, and exactly one held frame.

`.5` retains the existing numerical anchor for CEE 1.2 dynamic models while the
same device still has CEE's native default priority (100). Physical grounds,
explicit references on ordinary circuits, other priority tiers and removed devices
still trigger reselection. Merged networks retain one deterministic eligible
anchor. The repair does not change component equations, solve precision or
accepted-history assertions. New focused checks exercise uninterrupted capacitor
history after a preferred-terminal change and replacement by a higher-priority
reference, as well as explicit same-priority changes on ordinary circuits.
The previous `.3` runtime fails the new continuity check at tick 21; the matching
test companions differ only in their required-version metadata.

The first anchor repair, `.4` (SHA256
`f7e61fc385c9d329f8b2c181cbe2d037267da2320a58a45247cf8794426bfda5`), passed
the ten-case readiness groups and both upstream/compatibility reference pairs,
but broader regression caught a failure in
`preferredReferenceChangesAndPhysicalGroundTakeover`: the repair also retained
an explicitly requested priority-1000 reference. Its campaign `44647987390` was
cancelled after this finding and is not acceptance evidence. `.5` narrows the
retention rule to the CEE 1.2 dynamic-model default; legacy behavior and explicit
reference selection remain intact. The full acceptance recorded here used `.5`.

The narrowed `.5` repair passes the 218-case regression group and all eleven
readiness cases on each solver, including the existing preferred-reference /
physical-ground takeover test that rejected `.4`. Both ten-case upstream /
ten-case compatibility reference pairs also pass and satisfy the retained-trace
evidence gate (498 focused cases total). The accumulator comparison preserves
all 110 samples, fifty charging frames and fifty-nine discharging frames.
These focused observations preceded the full campaign recorded above.

The `.5` player also passes the 218-case regression group and applicable
readiness checks on CEE 1.1.4 and the actual CEE 1.1.3 jar, using each solver.
The combined focused reports contain 1,414 reported cases in sixteen processes;
four are the explicitly inapplicable modern-only continuity check on legacy
CEE. Actual loaded versions and hashes are retained in the runtime audits.
The Linux-produced `.5` player, test companion, API jars, examples and CEE jar
match the local focused inputs byte-for-byte. The reference jar differs only
by unused local launch-argument text files; all shared entries are identical.

### Repair snapshots and acceptance

| Snapshot | Change | Observed result |
| --- | --- | --- |
| `.3` | Finite diode trial merit, with exact accepted-current validation | Full run [`44643930077`](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/44643930077) passed all 398 jobs across eight environments. All 1,204 retained objects were hash-verified. Separate focused repeats exposed the coordinate-dependent accumulator pause. |
| `.4` | First numerical-reference retention repair | Focused reference pairs passed, but broader regression rejected its treatment of explicit reference changes. Run [`44647987390`](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/44647987390) was cancelled and does not establish acceptance. |
| `.5` | Retention limited to CEE 1.2 dynamic-model defaults | Focused and legacy checks pass. Full run [`44649764392`](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/44649764392), source `d8f2dd6b633b9bec1bee7e5dd9048d2ff1b6f456`, passed all 398 jobs across eight environments and the official evidence gate. |

The `.3` full result belongs to player SHA256
`0c800e29da66728a239d4fa2407efa2f9052670c744dc6614f4c2b5597a9cab8`
and source `59e8d8980086646861a787ec56459fd2cf33ca65`. It is preserved under
that identity and is not relabelled as `.5` evidence.

The expanded reference checks found a real high-bias diode failure in `.2`:
an extreme Newton trial overflowed the Shockley exponential before PG could
backtrack. `.3` supplies a large finite rejection merit for those trial currents.
Accepted measurements require the exact model current within the finite
evaluation range; clipped trial values cannot be published. Three focused JVM
checks cover finite squared merit, exact physical currents and rejected invalid
measurements. Earlier `.3` runs passed the ten reference comparison cases on both
solvers against the explicitly labelled precision control described below,
including the 70 V diode.
Core/migration, showroom and combined Pinout Yard checks also pass on both
solvers. All six broader `.3` regression invocations pass: 218 cases each on CEE
1.2, 1.1.4 and 1.1.3 with both solvers (1,308 cases).

Further fixture repairs use the version-safe indexed-node lookup, the actual
native diode model (legacy clamp or modern Shockley equation) and the steering
wheel's positive channel in the showroom's real receiver wiring. Existing
physical tolerances and restart/receiver assertions remain intact.

The public SDK example now initializes its custom storage resistance before
the first adapter capture and uses the selected CEE current-source convention.
It compiles against the public minimum SDK and passes all 37 API setup cases on
CEE 1.2, 1.1.4 and 1.1.3 with both solvers on `.2` (222 cases). Its repaired jar
has SHA256 `4a412dcc1bdc887b267588aa1f75b397403f9ce72aa1afa8076dec7b52aaa9b6`.

### Reference precision boundary

Raw CEE 1.2's default squared residual threshold (`1e-2`) fails the reference
capacitor/diode transient assertion. That failed observation is retained. The
existing test-only `CeeReferencePrecisionMixin`, explicitly enabled at `1e-16`,
makes all ten upstream reference checks pass with the same CEE jar and physical
assertions. This extends the earlier causal diagnosis recorded in the historical
report. The diagnostic is absent from the player jar. Controlled comparisons
keep their diagnostic identities and do not certify stock-default parity.

The user approved the more accurate, test-only reference baseline for the CEE
1.2 beta. Its catalog jobs explicitly select `cee120-residual-1e-16`; the evidence
gate requires the matching CEE hash, launch argument and actual control marker.
Compatibility traces must identify the same reference baseline. Raw-stock
failures remain separate observations. All eight measurement traces per
comparison are retained and checked against that identity. The reference
experiment freezes its configuration inputs and drains already queued reloads
before timing starts, preserving the exact charge/discharge frame assertions.
Further `.3` repeats exposed the anchor-change defect described above. Run
`44643930077` retains the full `.3` attempt and cannot qualify the repaired `.5`.
The default stable 342-job plan and CEE 1.1.4 recommendation remain unchanged.

## Earlier `.2` repair candidate

`0.2.10-dev.cee120.2` has player SHA256
`21ac72bf950388f7e57dde94497cd3e48de1ddda88e1caeaa72f536b93cfdc06`.
It retains CEE `1.21.1-1.2.0-132` as the development runtime. Validation is in
progress; the initial candidate's failed campaign below remains a historical
result for that exact input.

The repair uses version-matched panel links and slots in the test companion.
Analytical source fixtures retain the same physical polarity with either CEE
contract, and the independent RC/transformer references use CEE's configured
theta-method capacitor integration (`alpha=1` for legacy backward Euler).
Tolerances, phase/current checks and power-balance checks are retained.
CEE 1.2 has a separate complete expected registry inventory; the legacy record
and the PG/Pinout entries are preserved.

Expanded testing also found the new throttle-wheel control consuming PG wire
and probe interactions. A CEE-1.2-only mixin now passes those interactions to
the tool, matching existing panel controls. Tests cover all fourteen stationary
CEE 1.2 attachment types, both hands, normal throttle interaction and analog
state/link persistence. Legacy checks retain their eleven attachment types.

Comparing player ZIP entries with `.1` shows only the added throttle guard,
its mixin registration and the version metadata changed. Public APIs, native
binaries and all pre-existing player classes are byte-identical. The mixin
resource now uses frozen LF bytes; original `.1` artifacts remain unchanged.

Local repair attempts and frozen inputs are retained under
`local/cee-120/headless-repair-0210-1/`. The original two failures were test
contract assumptions; the expanded throttle finding required the new player.

The isolated worktree remains `G:/WWPG/local/cee-120-readiness`. The earlier tip
`d9a07b2` is preserved by `dev/cee-120-before-0210-20261119`; its jars, worlds,
logs and original result identities remain intact. The
[historical 0.2.8 preparation report](CEE_120_READINESS_028.md) records that snapshot.

## Initial candidate inputs

- CEE development source: `4828c3487a9fc0703717b415eb07d005c7a48653`.
- CEE development jar: `electroenergetics-1.21.1-1.2.0-132.jar`, SHA256
  `71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7`.
- Legacy CEE 1.1.4: SHA256
  `2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f`.
- Minimum CEE 1.1.3: SHA256
  `ae7ea74b7b00f175daa5a60a953a6d57f7f775aed0b642a1e9f80c3865cf8be7`.
- Primary environment: Windows, Java 21, NeoForge 21.1.262, Create 6.0.10,
  PG 0.6.2, Architectury 13.0.12, BASE profile and sixteen electrical substeps.
  Minimum compile dependencies remain separate from runtime selections.

The candidate player jar has SHA256
`da8c362356a4c3bc68db3073633d22aac7d9aa0da65b808d4d26ec72117fed9c`.
The active input manifest and frozen source copies are under
`local/cee-120/integration-0210/frozen-04/`.

## Integration behavior

The merge retains the current baseline's native PG generator sampling, torque
accumulation and rotor tick. The former WWPG generator timing overrides remain
removed. It also retains the baseline's motor restoration, attached-fan and
external-shaft behavior, live PG wire reattachment, train gauge recovery, and
saved-state alternator supply while the source chunks are unloaded.

CEE-specific behavior remains behind the existing 1.1/1.2 binary boundary:
version-matched graph/property/result access, source polarity, nonlinear diode
iteration, capacitor numerical rollback, measured current publication and
unavailable readings. Ordinary world upgrades use saved CEE metadata and owned
markers automatically. Genuinely ambiguous charged inductor histories remain
intact and unavailable until their source convention is established. New CEE
capacitor saves retain voltage and current history. This integration keeps both
legacy and development CEE support.

Comparisons against the exact published 0.2.10 jar verify that all 47 public API
class files, four native binaries and fourteen checked restoration/train class
files are unchanged. The baseline scheduler, restoration coordinator and wire
restoration source files also match main exactly. Test classes are excluded from
the player artifact; reference controls remain in their separate artifact.

## Focused verification

All 22 focused gameplay invocations passed: 158 GameTest cases on Windows BASE.
The integration's execution records are stored separately under
`local/cee-120/integration-0210/`, indexed by `evidence-index.json` with original
input/report hashes. Earlier acceptance is not relabelled as a pass for this
new runtime. The first Native core result retains its `frozen-01` identity;
`fixture-equivalence-01-04.json` verifies unchanged player and core fixture bytes.

| Selection | Cases per invocation | Environments |
| --- | ---: | --- |
| Electrical and reactive save migration | 10 | CEE 1.2, 1.1.4 and 1.1.3; Native and Java |
| Baseline integration | 7 | CEE 1.2 and 1.1.4; Native and Java |
| Lifecycle, meters, grounding and shared-state rollback | 17 | CEE 1.2; Native and Java |
| Custom adapters, callback rejection and rollback | 7 | CEE 1.2 and 1.1.4; Native and Java |
| Unmapped descriptor ownership | 1 | CEE 1.2; Native and Java |
| Standalone and panel fans | 2 | CEE 1.2; Native and Java |
| Cold migration and second reopen | 1 each | Preserved legacy save to new Native runtime, then Java reopen |

The baseline selection delegates to the real moving-train, gauge recovery,
unloaded single/three-phase alternator, motor reload, externally driven motor,
direct/custom PG rotor, and native wire-result selector assertions. The train
keeps its original 96 x 24 x 96 structure and isolated batch. Test-only graph
construction uses version-safe node insertion and prepared node views because
CEE changed `CircuitBuilder.addNode`'s return descriptor and indexed-node class.

The cold serialization specimen retains its original 6 J state. Each verification
copies its source world to a fresh run directory and inventories the source before
and after. Physical time is held for this specimen; it is not a transient or
performance test.

The 31 passing focused JVM tests cover reactive state conventions, adapter
registration, result availability and numerical history rollback. The 66 passing
Python tests comprise fourteen readiness-runner, six packaging and forty-six
catalog/evidence checks. The development
groups are explicitly focused: they do not add duplicate fixtures to the
baseline's 342-job release acceptance plan. Planning tests launch no Minecraft.

Failed attempts remain failed. The first train wrapper used an undersized
structure; the next attempted selection omitted the train and was rejected by
the strict expected-count check. The subsequent train run exposed a legacy
return descriptor in the companion's foreign-result setup; that helper was
adapted to both CEE versions. The player jar remained unchanged through these
companion corrections. Retained passing results keep their original companion
identities, with separate runtime and fixture comparisons.

## Full headless attempt: 19 November 2026

The explicitly requested full development campaign **failed and is incomplete**.
It selected 398 jobs on the exact candidate player and CEE 1.2.0-132 above,
across Windows/Linux, Native/Java and BASE/PINOUT. Both platform preparation
stages passed, including units and independently extracted SDK builds. The
generator audit (four tests) and wider audit (fourteen tests) passed in all
eight environments: 144 completed GameTest cases in total.

Every environment then stopped in regression SETUP with the same two findings:

- The reactive-transformer reference check expected voltage sample zero
  `4.952854208939122`, but observed approximately `-4.98247818310351` on both
  solvers. The cause of that reference mismatch remains to be investigated.
- The test companion's `PanelGameTests` calls the removed
  `ElectricalPanelLink.getTransmittedStrength()` method and crashes the test
  server with `NoSuchMethodError`. This call is in test code; it does not by
  itself demonstrate a player interaction crash.

Coverage is **18 passed jobs, eight failed jobs and 372 unexecuted jobs**.
The two preparation jobs retain their original execution identities; the
continuation ran gameplay afresh. The loaded runtime audits confirm the exact
player and CEE hashes in each environment. The candidate has no full headless
acceptance pass.

The [canonical run](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/44622129193)
retains the frozen inputs and preparation. The
[diagnostic continuation](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/44623975683)
retains complete portable failure logs after a producer-manifest path repair.
The local record is `local/cee-120/headless-0210-1/RESULTS.json`; its 290 portable
evidence objects were checked against their recorded hashes and sizes. Original
failed attempts are retained separately. Launcher repairs did not change the
frozen player jar.

### Subsequent `.2` campaigns

[Run 44629869533](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/44629869533)
passed 82 jobs before all eight environments stopped at API setup: eight failed
jobs and 308 unexecuted jobs. The cause was the SDK example's initial resistance
and source sign, repaired above. All 402 reachable portable evidence objects
were checked against their recorded sizes and hashes.

[Run 44633630674](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/44633630674)
uses the corrected example. The canonical comparison confirms that only
`upstream_example` changed; player, companion, APIs, reference control, CEE jar
and catalog retained their original hashes. It finished with 186 passed jobs,
eight failed jobs and 204 unexecuted jobs. BASE environments stopped at showroom
setup; PINOUT environments stopped at Yard setup, both on the old steering
channel assumption. All 614 reachable evidence objects were verified. The `.3`
focused checks correct that wiring and the Yard's old clamped-diode expectation.
No `.2` campaign establishes a passing full suite for `.3`.

## Repeat a focused check

```text
python tools/cee_readiness.py run --inputs local/cee-120/integration-0210/frozen-04/inputs.json --runtime cee120 --backend JAVA --group baseline --output local/cee-120/new-baseline-attempt
```

Other explicit groups are `core`, `lifecycle`, `ownership`, `adapters`, `fans`,
`cold-migration` and `stock`. Output directories must be fresh. The runner checks
embedded CEE versions and exact artifact hashes before launch, validates the
loaded jars and expected positive test count, requires clean shutdown and keeps
the 600-second bound. Frozen jars are not rebuilt during gameplay checks.

`freeze --from-inputs ... --cee113 ... --runtime ... --gametest ... --reference ...
--current-sources --baseline ... --output ...` records a new built runtime.
Replacing only a companion/control still requires identical player sources.
The frozen sources include the selected runtime dependency manifest.

## Remaining boundaries

Optional external addons and real Sable runtime integration remain uncertified.
Old addons that directly link removed CEE constructors or registry descriptors
need compatible builds. A future CEE artifact needs its own comparison and
applicable checks; this report concerns the exact development build above.

The historical raw-stock capacitor/rectifier failures and separate residual
threshold diagnosis remain recorded in the older report. They are not described
as a fresh stock comparison or as raw-stock parity for this integration.

The full headless attempt above failed; graphical acceptance has not been run
for this candidate. Published 0.2.10 acceptance remains the evidence for its
named release, not for the CEE development port. The preparation branch was
pushed to execute the requested manual campaign. No release was published.
