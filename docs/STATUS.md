# Current status and known issues

Status checked **9 October 2026**. These facts describe the implementation and published beta at that date; linked GitHub runs retain their own commit and outcome.

## Published beta

**0.1.0-beta.2** is the handheld-meter and example-world hotfix. Its release checks are in progress on this branch. It adds three packaged fixtures (117 total) and actual-client checks for CEE terminal voltage, both meters on PG wires, and both meters on CEE wires. The example heater now has a separate 600 V feed, a basin, and tools at spawn. The original 300 V feed only warmed the heater and did not reach working burner heat.

The original acceptance covered installed meters; it did not validate the handheld interactions reported by a player. Beta.2 fixes wire targeting taking precedence over terminal probes and synchronizes solved server measurements instead of using a client estimate that cannot reconstruct the CEE source. [PR #2](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/2) contains the change. The original beta.1 downloads and evidence remain unchanged.

**0.1.0-beta.1** is [published as a GitHub prerelease](https://github.com/Cha0sCollective/World-Wide-Power-Grid/releases/tag/0.1.0-beta.1). The downloads include the mod jar, example world, verification archive, complete documentation bundle, and checksums. [PR #1](https://github.com/Cha0sCollective/World-Wide-Power-Grid/pull/1) merged the implementation into `main` as `8e533df` on 9 October.

The declared scope is stationary interoperability: 95 block/assembly behaviors, 11 built-in CEE panel attachments, and 28 built-in PG board components. See [SUPPORT.md](SUPPORT.md) for the inventory and exclusions, and [INSTALL.md](INSTALL.md) for the exact dependencies and solver settings.

## Test evidence

| Record | Outcome |
| --- | --- |
| [Local release acceptance](../release/verification.json), 6 October | Passing native/Java runs on Windows/Linux: 114 packaged tests, four equation tests, reference-world comparisons, example restart, and two real TCP clients. |
| [Initial push CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/37547855327), commit `3cd55dc` | One Linux/native chunk-reload test failed; the other three backend/platform jobs passed. |
| [PR CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/37547915948), same commit `3cd55dc` | All four backend/platform jobs passed. |
| [Main merge CI](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/37990481752), commit `8e533df` | All four backend/platform jobs passed. |

CI runs the packaged suite's SETUP and VERIFY phases, upstream reference comparison, and example creation/restart on Windows and Ubuntu 24.04 with native and Java backends. Two-client multiplayer and cross-platform byte-identical jar comparison are recorded local checks, not CI jobs.

## Unresolved chunk-reload failure

The initial Linux/native run failed `chunkReloadRebindsMixedEndpoints`: the restored circuit read **0 V**, where the fixture expected **10 V**. Subsequent runs passed without implementation changes.

The test samples at a fixed tick after forcing the chunk to load, while entity loading is asynchronous. This is a plausible timing cause, not a confirmed diagnosis. It remains unclear whether the failure is limited to the test's deadline or exposes a compatibility lifecycle defect. A successful later run does not resolve that question.

The next diagnostic step is to record chunk/entity readiness, restored wire connections, and simulation progress before the assertion. Any change must retain an actual unload/reload and check that power returns within a bounded interval. Beta.2 does not claim to fix this earlier failure.

## Startup failure under investigation

Beta.2 publication is held while an intermittent mod-registration failure is investigated. NeoForge reports an “uncaught parallel processing error”; the earlier underlying error is Create's Registrate reporting unused registration callbacks. This happens before a world or electrical simulation starts and has occurred with both solver selections.

The final local 117-test native/Java runs, example restart, and two real clients passed, but the [Linux/native PR job](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38005893894) and [Linux/Java push job](https://github.com/Cha0sCollective/World-Wide-Power-Grid/actions/runs/38005891186) reproduced the startup failure on commit `cdb94cb`. Reusing CEE's existing meter handler did not fully resolve it. Failure-path diagnostics are being added; neither an upstream cause nor a WWPG cause has been established. Successful later starts alone will not be presented as a fix.

## Deferred work

Moving trains/contraptions, Sable sublevels, optional integrations, broad version ranges, specialized unclaimed devices, large-network benchmarks, asynchronous execution, advanced topology reduction, and a public extension API are outside this beta's scope. The [long-term specification](../SPEC.md) retains the full product requirements.

## Open-source license

WWPG's own code and documentation are licensed under [MIT](../LICENSE), including the WWPG portions of the published beta. Current builds declare MIT and include the license. The original `0.1.0-beta.1` download still contains its older “All Rights Reserved” metadata label; its published files and checksums are preserved. Bundled PG native resources retain their Apache 2.0 license and attribution; other dependencies retain their own licenses.
