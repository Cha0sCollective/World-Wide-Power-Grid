# CEE 1.2 development readiness

The preparation branch `dev/cee-120-readiness` now integrates WWPG **0.2.10**,
main commit `5072e412639db71d346ff54c2b133f54f0b13934`. The initial candidate was
`0.2.10-dev.cee120.1`; the current repair candidate is `0.2.10-dev.cee120.2`.
This is a development preparation, not a published release
or a full acceptance claim.

## Current repair candidate

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
