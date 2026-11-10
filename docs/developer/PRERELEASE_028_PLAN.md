# 0.2.8-rc.1 prerelease campaign

Started November 10, 2026 at the user's explicit request to package the current
control, add twelve circuits from the **Powergrid Circuits** chat, and run full
release-required testing. This is a new task; the earlier performance goal was
cleared by the user. No goal or automatic performance campaign is active.

## Workspace and source

- Release worktree: `G:/WWPG/local/r028`, branch `dev/0.2.8-rc.1`, initially
  based on experimental commit `4549eb5`.
- Preserve the normal main checkout, experimental worktree, all published
  releases and evidence. The main checkout has historical untracked circuit
  and evidence directories; do not remove or add them indiscriminately.
- Tested Windows control: `G:/WWPG/local/x27w/local/frozen/native-residual-control-01/inputs.json`;
  runtime SHA `f8e0bf53deb7549fbe18e1507686084123fab2965922e5f56bfc5c8f6dc08f98`;
  native DLL SHA `e9476995415ac0a1466188c237dddf741349cf879ec4d24cd4a9247b5e29357d`.
- Its Java baseline is `wiretree.11` / `prototype-12g`, with plain/property
  batching, bounded/prioritized restoration, chunk-task service, reviewed CEE
  geometry reuse and scoped restoration enabled. Normal prerelease use must
  enable the intended behavior without benchmark-only JVM arguments.
- The control includes exact native finite/max reductions and fresh matrix /
  factor handling after fitting callbacks. The rejected residual-layout cache
  and packed worker/result-processing prototypes must remain disabled/absent.
  Implement corresponding intended control behavior on Linux and Windows;
  do not silently ship asymmetric native optimizations.

## Circuit sources located

Chat: `01a1b37b-56f3-7332-9667-1f4cb9c33318` on local host, title
**Powergrid Circuits**. Read using app thread tools; do not send messages or
resume that chat. The final circuit list confirms all twelve below, tested
previously at sixteen substeps on stock PG 0.6.2, with community attribution.
Earlier checks are historical source/oracle evidence, not acceptance of this
new prerelease.

The source checkout `G:/WWPG` contains untracked `circuits/` and `research/`
`research/` files; these are not automatically present in a new worktree.
Copy only reviewed, required inputs and retain hashes/provenance.

| Requested circuit | Located source / harness |
| --- | --- |
| Three-bit full adder | `research/community-schematics/adders/fa3_ordered.nbt`, stock circuit checks |
| LC oscillator | `research/community-schematics/oscillator/LC_Oscillator.nbt`, stock circuit checks |
| Generator/battery selector | `research/community-schematics/source-switch/Gen_Battery_autoSwitchV3.nbt`, stock checks |
| Converted linear regulator | Native conversion used by stock checks; locate exact distributed bytes rather than original Power Chip input |
| Eight-bit parallel register | Native-memory-cell assembly from circuit baseline harness; not the unverified original community register |
| Direct-row 4x4 memory | Working native-memory-cell assembly from circuit baseline harness; not the broken community RAM |
| Four-bit clocked shift register | `circuits/advanced-v1`, `research/advanced-circuits` |
| Addressed 4x4 memory | Same advanced library, `ram4x4` assembly |
| Fused latching output | Same advanced library, `fused-latch`; Power Components required |
| 240 V relay basin heater | `circuits/basin-heater-v1`, `research/basin-heater` |
| Quiet 240 V heater | `circuits/basin-heater-quiet-v1`, `research/basin-heater-quiet` |
| Two-source loss alarm | `circuits/source-loss-alarm-v1`, `research/source-loss-alarm` |

Useful preserved campaigns in main checkout's `local/`:
`stock-pg-circuits-20261107T153207001530Z`,
`advanced-library-20261107T050408722877Z`,
`basin-controller-20261107T163247590818Z`,
`source-loss-alarm-20261108T153945299975Z`.
Quiet heater requires **16 electrical substeps**: the chat reproduced actual
heater light strobing at one substep and steady heat/light at sixteen on both
backends. The controller's neon bulb deliberately blinks.

The user confirmed community redistribution permission on November 10, 2026.
Preserve community attribution. All circuit fixtures must assert real
operations and expected outputs, not force states or substitute easier designs.

## Required work and gates

1. Inspect existing sources, design/oracle evidence, release catalogs and native
   generation. Select newest currently supported runtime dependency inputs,
   verifying upstream versions before freezing. Preserve minimum SDK compile
   and older compatibility lanes separately.
2. Promote control features into a normal prerelease configuration, implement
   intended native control behavior reproducibly, add twelve circuit fixtures
   and register their full cold/reload coverage in the suite catalog.
3. Run appropriate focused checks while developing. Update installation,
   circuit requirements, API/example/SDK versions and player documentation.
4. Freeze final source and exact runtime/API/example/native/dependency bytes.
   Run the explicitly requested full headless cohorts on Windows/Linux,
   Native/Java, BASE/PINOUT and required loader coverage. Run the separate
   release-required graphical cohort against the accepted archives/SDK starter.
   No older passing runtime evidence substitutes for changed code or jars.
5. Preserve failures, repair causes and use fresh attempts; reuse passing
   unchanged groups only with the repository's explicit identity validators.
6. Complete strict release packaging and stage the five downloads with the mod
   jar first. Notes: <=3 highlights, <=250 words. Preserve acceptance gates.
   The user's wording requests packaging a prerelease; public publication is
   not yet a separate requested action. Finish reviewable packaging before any
   final publication question.

Read `docs/RELEASING.md`, `docs/TESTING.md`, `docs/API_RELEASE_CHECKS.md`,
`tools/test-suites.json`, `tools/test_suites.py`, `tools/ci_prepare.py` and the
manual `.github/workflows/build.yml`. No suite has started at this checkpoint.

## Progress

- Read current release/test rules; explicit full-release testing authorization recorded.
- Located circuit chat, schematics and existing stock/WWPG harnesses.
- Created the isolated release worktree/branch before implementation.
- Version `0.2.8-rc.1` is provisional, communicated to user.
- Promoted intended Java defaults; preparing symmetric native binaries with exact
  reduction and fitting matrix/factor lifetime fixes, without the rejected cache.
- Adapted twelve preserved circuit sequences and thirteen immutable schematic
  resources into the GameTest companion; author/permission provenance retained.
- Selected current runtime jars and NeoForge 21.1.265. Minimum SDK inputs and
  older loader compatibility remain separate. Complete release suite not started.
- Java and fixture compilation pass; 150 Java unit checks and 38 suite/evidence
  tooling checks pass.
- Both native builds pass 40,740 exact comparisons across 16 FP modes and
  37 sparse safety checks plus 100 valid refactor/cleanup cycles each. Exact
  records are in `local/native-{linux,windows}-checks-01`.
- Native libraries are staged in `tools/native/prebuilt`; their source manifests
  validate during the successful candidate assembly. API jars are byte-identical
  to the published 1.0.0 jars.
- All twelve circuits pass on both backends (24/24), in `local/circuit-checks-01`.
  A later companion update removed obsolete diagnostic-flag requirements from
  two batching helper classes; circuit classes/resources are unchanged. These
  development runs are not carried forward as release acceptance.
- Control preflight passes 37 Native and 24 Java checks, including the existing
  batch power assertions, restored rings and geometry comparisons. The first
  preflight failed at seven old diagnostic-flag guards; its logs are retained.
  The repaired fixtures retain their independent bitwise assertions and run
  with the real default `verify=false` setting. Final full acceptance remains fresh.
- Both independently built example jars match the published 1.0.0 outputs.
- Ready to freeze source and run the explicitly requested full release matrix.
