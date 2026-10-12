# API documentation checks — October 12, 2026

This record covers documentation and the teaching starter against **published WWPG 0.2.1 / API 1.0.0**. It is not a new runtime release or final certification of the next SDK. The published files, tags, manifests and acceptance records are unchanged.

The standalone starter resolved the actual API jar `c68a9f5824ebc80810ad697f1132170b2cf070ba5f2b8a892a1695ededf91d21`, with no WWPG project dependency. Public source edits are comments only; generated Javadoc compiles and both binary contract comparisons report zero changes (37 general and 7 CEE types). The website checks 33 dated correction targets, frozen input hashes and all new rendered routes.

Dedicated-server checks on Windows / NeoForge 21.1.248 pass with both actual NATIVE and JAVA backends: 100/200/400 Ω, open state, unavailable indication/configuration, native wires, meters, replacement, diagnostics and recovery, actual chunk unload/restored power, and OFF/400 Ω inspected before solving after a separate process restart. Reports bind loaded runtime and starter jar hashes. The new CI workflow adds Linux extraction through ordinary `unzip`, builds both original SDK examples after the documented permission step, builds the future ZIP without a permission repair, and repeats the server checks on both platforms/backends. CI results belong to the documentation PR, not the earlier release.

The first documentation CI attempt failed before any build because the archive test assumed `build/` already existed on a clean checkout. The test now creates its own scratch parent; the failed CI log is retained.

## Initial graphical attempts (superseded by the Dev Suite follow-up)

The user deferred graphical testing while benchmarks were running. Three preliminary native-client logs are retained. The first driver failed terminal targeting; the next two produced no placed wires and failed the powered indication assertion. Those are failed checks, not passing product demonstrations. The driver now uses a full native wire stack, but that change has **not** been graphically rerun. Real-client controls, meters and screenshots on both backends remain required before including the starter in a future accepted SDK. The released API Workshop evidence remains separate.

## Automated reader review

A fresh-context reviewer used only public APIs, guides and the starter, without reading private WWPG implementation. This was automated source-level usability review, not human certification. Findings were fixed: absent file listings/LICENSE; mutating readout and stale measurements; ambiguous unload/pre-solve checks; missing example extraction; renamed jar paths; nonreproducible failure exercise; misleading maintainer links; first-instance/wiring assumptions; IDE import/provenance instructions; and north-face polarity wording. The tutorial now supplies a read-only interaction, explicit top-face enable, pending readings, exact extraction/tool steps and debugger observations. A later human author trial is still useful.

## Reproduce

From the repository root with Java 21 and Python, run `python tools/documentation_acceptance/check.py prepare`, then `python tools/documentation_acceptance/check.py server --backend NATIVE` and repeat with `JAVA`. Use a clean workspace: the preparation preserves existing extraction directories. The workflow does not add game-run tasks to the starter. Detailed runs use the test-only `tools/documentation_acceptance/init.gradle` against the released runtime; its server task requires all four cases to pass.

## Dev Suite follow-up

After the user authorized the separate Dev Suite host, Linux software-rendered Xvfb client checks passed on **both actual backends**, using the published 0.2.1 runtime and an independently built starter. They verify native PG wire placement, 100/200/400 Ω controls and readouts, read-only observation, OFF indication, resource/model availability and a native client multimeter near 20 V. The server fixtures separately cover the CEE clamp meter and both wire systems. Small result reports and exact helper hashes are retained here; full logs, failed attempts, helper captures and screenshots remain at `/workspace/artifacts/wwpg-api-docs-gui`. This is not a Steam Deck hardware/GPU certification or next-release acceptance.

The first container cold build timed out during unbuffered archive writes on the Windows-mounted v9fs workspace. A derived build copy in the ext4 Linux cache completed the SDK builds and 40 constructor checks. The first container client attempt failed to place wires: PG recasts the player's view on the server, while the driver rotated and clicked in the same tick. Waiting for the view to settle corrected placement on both backends. Clients closed automatically and music remained disabled.

The broader NeoForge compatibility run `38300946248` retained a failed Windows/NATIVE/BASE cold process at the 600-second watchdog. Its server thread was actively building CEE connections, and actual duplex unload counters advanced to three before termination. The affected cold-restarter command now has a bounded 1200-second limit, retaining the same test set, three cold processes and five unload cycles. Only a completed passing run closes that timing gate.

## Final CI watchdog follow-up

The final website preview found an absent optional-font stylesheet in the frozen Javadoc. Derived website output supplies a fallback import without modifying the frozen inputs, and CSS URL checks cover the missing destination. Repeated parsing of the tutorial appendix caused the Dev Suite default Node heap to run out; caching target anchor IDs corrected it. All 22 site tests then passed without a heap override. The original failure logs and final preview captures remain in Dev Suite.

Build run `38309298496`, job `115010842204` (Windows / NATIVE / PINOUT), was cut off by its remaining 600-second watchdog during the third populated cold restart. The first two restart logs and full failed job log are preserved here, along with its timeout record and JVM stacks. At termination, the server was RUNNABLE in `validateAllCoupledNodes` during normal conductance updates. All five Pinout unload cycles had completed and the duplex fixture had progressed through three unloads; no assertion failure caused termination. The main build's populated cold-restart step now uses the same bounded 1200 seconds as the loader compatibility workflow, preserving three separate processes, all five actual unloads and every original assertion. Other gameplay launch limits remain unchanged. A completed passing run is still required before merge. This change addresses a test-time budget; it does not claim a runtime performance improvement.

## Scope correction

The full-game watchdog and startup-audit investigations above are historical failed-run evidence. Their proposed code/workflow repairs are preserved separately on `dev/test-harness-repairs`, and are not part of this documentation PR. The documentation branch restores the original full-game workflows and contains no gameplay source changes. The captured logs remain byte-identical; they do not become passing evidence merely because a later run passes.
