# Current status and known issues

Status checked **10 October 2026**. These facts describe the implementation and published beta at that date; linked GitHub runs retain their own commit and outcome.

## Published beta

**[0.1.0-beta.4](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.4)** publishes the restart and chunk-loading corrections from [PR #7](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/7). Saved wires are protected until chunk entities finish loading, and PG board capacitors save retained charge rather than an unsolved zero reading. It includes beta.3's grounding correction. The support set and dependencies are unchanged. Existing example worlds work with beta.4; replace the WWPG jar on the server and every client. [Release records](../release/0.1.0-beta.4/) identify the final artifact and its checks.

**[0.1.0-beta.2](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.2)** is the handheld-meter, example-world, and startup-registration hotfix. Its final packaged checks passed with native and Java solvers. It adds three meter fixtures and one concurrent-registration regression (118 total), plus actual-client checks for CEE terminal voltage, both meters on PG wires, and both meters on CEE wires. The example heater now has a separate 600 V feed, a basin, and tools at spawn. The original 300 V feed only warmed the heater and did not reach working burner heat.

The original acceptance covered installed meters; it did not validate the handheld interactions reported by a player. Beta.2 fixes wire targeting taking precedence over terminal probes and synchronizes solved server measurements instead of using a client estimate that cannot reconstruct the CEE source. [PR #2](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/2) contains the change. The original beta.1 downloads and evidence remain unchanged.

**Expanded test yard revision 1** adds live transformer, meter, board, panel, control, motor, and storage stations for the existing beta.2 runtime. All 28 built-in board components and 11 panel attachment types are represented in working circuits. [Player guide](FIXTURE_WORLD.md) and [coverage/evidence](../release/examples/expanded-yard-v1/) distinguish live circuits from parts cabinets. Loose construction wire items are cleaned before export.

**Example world revision 2** makes the panel/board circuit visibly testable with RUN/OFF lamps and a short capacitor off-delay. It uses the existing beta.2 runtime and is distributed separately from the original world and release bundle. [Instructions](INSTALL.md#example-world) explain the controls; [revision evidence](../release/examples/panel-relay-v2/) records its checks. The revision does not resolve the earlier reload/restart failures below.

**0.1.0-beta.1** is [published as a GitHub prerelease](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.1). The downloads include the mod jar, example world, verification archive, complete documentation bundle, and checksums. [PR #1](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/1) merged the implementation into `main` as `8e533df` on 9 October.

The declared scope is stationary interoperability: 95 block/assembly behaviors, 11 built-in CEE panel attachments, and 28 built-in PG board components. See [SUPPORT.md](SUPPORT.md) for the inventory and exclusions, and [INSTALL.md](INSTALL.md) for the exact dependencies and solver settings.

## Grounding correction in beta.3

The published beta.2 can retain incorrect voltages when a ground connection changes without replacing the circuit's nodes. This also affects moving the preferred voltage reference and unloading a chunk containing a ground rod. PG 0.6.2 skips its incremental equation update for ground connections, which have only one terminal.

The source correction rebuilds the affected equations when grounding changes, preserving the circuit's existing nodes and branches. Three new regressions cover adding/removing/changing grounds, reference changes and physical-ground takeover, and two actual ground-rod chunk unload/reload cycles. The packaged suite now contains 121 checks. [Grounding verification](../release/fixes/grounding/) records the results and reproduction on the old code.

The same grounding-fix jar passed [real Windows client checks of the downloaded expanded yard](../release/fixes/grounding/client/) with native and Java backends, including a native save/restart. Each client visited 69 stations, checked 54 synchronized gauges, and switched the factory and P2 panel through actual client interactions. All 282 PG wires remained connected. These checks cover the yard's 85 live behavior groups; they do not resolve the intermittent failures below.

This correction is released in beta.3; published beta.2 downloads and earlier evidence remain unchanged. It does not establish the causes of the earlier intermittent chunk-reload or board-restart failures below.

## Restoration corrections in beta.4

Beta.4 protects PG wires while saved entities are still loading and preserves PG board capacitors' committed charge when their newly restored circuit has not solved yet. The published beta.3 jar remains unchanged. Dependencies, solver selection, and the stationary support set are unchanged.

The investigation separates three behaviors: an early zero-power reading while wires are pending; PG's cleanup timer treating a pending entity as missing; and a board's persistence callback copying a cold zero terminal reading over retained charge. The test suite delays real entity-load results, requires restored power shortly after readiness, and checks saved capacitor state before recharging can conceal a loss. It contains 123 packaged checks, including cold-save and missing-wire cleanup regressions. [Restoration verification](../release/fixes/restoration/) records reproduction and backend results.

The PR #7 candidate passed all 123 checks in Windows native/Java SETUP and VERIFY runs and the complete Windows/Linux native/Java CI matrix. Disabling the two production corrections makes exactly their two focused regressions fail while the other 121 pass. Both backends also passed the downloaded expanded yard's automated server checks from pristine copies and again after saving with the corrected jar: 85 live behavior groups, all board and panel types, 282 retained PG wires, and no loose wire items. Beta.4's versioned-jar results are recorded separately in [release acceptance](../release/0.1.0-beta.4/). Graphical-client checks from beta.3 are historical evidence.

The historical failures below did not record entity readiness or capacitor history at the failing instant. The corrections address reproducible restoration paths, but cannot conclusively attribute every earlier zero reading to one of them. The PG design-table saved-design error remains a separate open issue.

## Test evidence

| Record | Outcome |
| --- | --- |
| [Local release acceptance](../release/verification.json), 6 October | Passing native/Java runs on Windows/Linux: 114 packaged tests, four equation tests, reference-world comparisons, example restart, and two real TCP clients. |
| [Initial push CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/37547855327), commit `3cd55dc` | One Linux/native chunk-reload test failed; the other three backend/platform jobs passed. |
| [PR CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/37547915948), same commit `3cd55dc` | All four backend/platform jobs passed. |
| [Main merge CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/37990481752), commit `8e533df` | All four backend/platform jobs passed. |
| [Beta.2 implementation CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38008086944), commit `cd41b24` | All four backend/platform jobs passed with the registration fix: 118 packaged checks, reference comparison, and example creation/restart. |
| [Beta.2 packaged acceptance](../release/0.1.0-beta.2/verification.json), 9 October | Final Windows native/Java SETUP and VERIFY runs passed all 118 checks. The updated example passed creation/restart; two real clients measured about 20 V and 1 A using native item controls and closed cleanly. Earlier failures are retained in the verification archive. |
| [Visible example CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38022856355), commit `16656f1` | All four Windows/Linux backend jobs passed the full regression suite, reference comparisons, and revised example creation/restart. Their fixture jars match the locally tested harness byte for byte. |
| [Example revision 2 acceptance](../release/examples/panel-relay-v2/verification.json), 9 October | Native/Java creation, restart, and reopening the exported ZIP passed. Checks cover panel interaction, actual relay-powered lamps, capacitor off-delay, panel readings, and saved charge before recharging. All 67 non-GameTest jar entries match the published beta.2 runtime. |
| [Expanded yard CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38028267367), commit `59373ab` | All four Windows/Linux native/Java jobs passed the 118-check suite, reference comparisons, and both example worlds' creation/restart. All four fixture jars match the local harness. |
| [Expanded yard acceptance](../release/examples/expanded-yard-v1/verification.json), 10 October | Six native/Java creation, restart, and exact exported-ZIP checks passed, plus four equation checks. Coverage is 85/134 groups, all 28 board components and 11 panel attachments, 282 PG wire entities retained, and no loose wire items remaining. |
| [Beta.3 versioned-jar CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38054448951), commit `08e1c49` | All four Windows/Linux native/Java jobs passed on their first attempt: 121 packaged checks in SETUP and VERIFY, upstream references, and both example worlds' creation/restart. No intermittent failure appeared in this run. |
| [Beta.3 real-client acceptance](../release/0.1.0-beta.3/verification.json), 10 October | The final versioned jar passed in pristine copies of the downloaded expanded yard with native and Java Windows clients: 69 station views, 54 synchronized gauges, actual factory/P2 panel packets, 282 retained PG wires, and no loose wire items. Both clients saved and closed normally. Only mod-version metadata differs from the earlier tested grounding-fix jar. |

CI runs the packaged suite's SETUP and VERIFY phases, upstream reference comparison, and example creation/restart on Windows and Ubuntu 24.04 with native and Java backends. Two-client multiplayer is a local check. Beta.2 and beta.3 each have four downloaded CI jars matching their respective local release jar; hashes are recorded in [beta.2 CI](../release/0.1.0-beta.2/ci.json) and [beta.3 CI](../release/0.1.0-beta.3/ci.json).

## Unresolved chunk-reload failure

The initial Linux/native run failed `chunkReloadRebindsMixedEndpoints`: the restored circuit read **0 V**, where the fixture expected **10 V**. Subsequent runs passed without implementation changes.

The published test samples at a fixed tick after forcing the chunk to load, while entity loading is asynchronous. Delaying real entity-load results now reproduces **0 V with entities still pending** at that deadline. The updated fixture retains both actual unload/reload cycles and requires 10 V within five ticks of entity readiness, under the original total timeout. It also checks retained wire identities while loading is pending.

PG's separate missing-wire cleanup timer uses block-chunk availability and can expire a part while entities are still loading. The source pauses that timer until entity readiness, retaining its normal cleanup grace period afterward. A dedicated fixture exercises this reconciliation boundary; the ordinary whole-chunk fixture alone did not reproduce premature part deletion. These results establish concrete timing and lifecycle defects without proving which occurred in the old uninstrumented CI failure.

## Unresolved board restart reading

A local Windows/native VERIFY run on `cd41b24` failed `restartRetainsPanelTerminalsBoardIdentityAndCharge`: the PG circuit-board capacitor read **0 V** at the tick-20 assertion. An instrumented rerun read about **9.869 V** in both the saved component property and the capacitor's internal voltage history, with the wire entities loaded. All four implementation CI jobs passed this check.

The failed run did not capture those diagnostics, so the rerun cannot establish whether the original failure lost stored charge or sampled a circuit before restoration completed. The failure and diagnostic logs are retained with the hotfix evidence. This is separate from the startup registration fix and remains open; later passing runs do not establish a cause.

The same assertion failed again in [PR #5's Linux/native CI run, attempt 1](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38046505940/attempts/1), on `dfbfa64`. A retry of the failed job passed without code or assertion changes; the [main merge CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38051470456) also passed. Beta.3 retains the unresolved historical failure.

The current investigation's delayed restart retained about 9.869 V; it did not reproduce saved charge loss in that circuit. A separate cold-save fixture conclusively reproduces a related defect: PG's native board capacitor tick writes **0 V** into its saved charge property although its solver history still holds **7.5 V**. Saving committed history corrects that overwrite. The revised restart fixture checks serialized charge and history before the first solve, then checks the restored live reading after entity readiness. Historical failures lacked those observations, so their exact cause remains unproven.

## PG circuit design table saved-design error

Building the expanded yard exposed `powergrid:circuit_design_table` throwing a `NullPointerException` when loading its saved schematic. PG 0.6.2's `CircuitDesignTableBlockEntity.read` accesses `level.registryAccess()` before the block entity has a level. The table could subsequently receive power, so a powered-block check alone missed the failed load. The diagnostic log is retained in the expanded-yard evidence archive.

The downloadable yard excludes a placed design table. Its item remains in the parts cabinets, and the live routing board demonstrates pins, traces, a via, and a label. The existing design/copy workflow tests do not establish saved-table persistence. This issue remains open in beta.4.

## Startup registration race

NeoForge reported an “uncaught parallel processing error”; the underlying error was Create's Registrate reporting unused registration callbacks. This happened before a world or electrical simulation started and occurred with both solver selections, including on GitHub's independent test machines.

Earlier local native/Java runs, example restart, and two real clients passed, but the [Linux/native PR job](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38005893894) and [Linux/Java push job](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38005891186) reproduced startup failures on `cdb94cb`. Reusing CEE's existing meter handler did not fully resolve them.

A subsequent [instrumented Windows/Java failure](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38006974291), on `1f8b377`, recorded a callback count of **−1** with an empty callback map. Beta.2 now synchronizes pending-callback updates and entry publication per Registrate instance, preserving the existing error checks for actual unused callbacks. A forced interleaving regression reproduces a stranded callback with the fix disabled and retains it with the fix enabled. All four implementation CI jobs and both final packaged clients passed with the fix. The initiating class-loading interleaving has not been attributed to an individual mod.

## Deferred work

Moving trains/contraptions, Sable sublevels, optional integrations, broad version ranges, specialized unclaimed devices, large-network benchmarks, asynchronous execution, advanced topology reduction, and a public extension API are outside this beta's scope. The [long-term specification](../SPEC.md) retains the full product requirements.

## Open-source license

WWPG's own code and documentation are licensed under [MIT](../LICENSE), including the WWPG portions of the published beta. Current builds declare MIT and include the license. The original `0.1.0-beta.1` download still contains its older “All Rights Reserved” metadata label; its published files and checksums are preserved. Bundled PG native resources retain their Apache 2.0 license and attribution; other dependencies retain their own licenses.
