# API documentation checks — October 12, 2026

This record covers documentation and the teaching starter against **published WWPG 0.2.1 / API 1.0.0**. It is not a new runtime release or final certification of the next SDK. The published files, tags, manifests and acceptance records are unchanged.

The standalone starter resolved the actual API jar `c68a9f5824ebc80810ad697f1132170b2cf070ba5f2b8a892a1695ededf91d21`, with no WWPG project dependency. Public source edits are comments only; generated Javadoc compiles and both binary contract comparisons report zero changes (37 general and 7 CEE types). The website checks 33 dated correction targets, frozen input hashes and all new rendered routes.

Dedicated-server checks on Windows / NeoForge 21.1.248 pass with both actual NATIVE and JAVA backends: 100/200/400 Ω, open state, unavailable indication/configuration, native wires, meters, replacement, diagnostics and recovery, actual chunk unload/restored power, and OFF/400 Ω inspected before solving after a separate process restart. Reports bind loaded runtime and starter jar hashes. The new CI workflow adds Linux extraction through ordinary `unzip`, builds both original SDK examples after the documented permission step, builds the future ZIP without a permission repair, and repeats the server checks on both platforms/backends. CI results belong to the documentation PR, not the earlier release.

## Graphical testing deferred

The user deferred graphical testing while benchmarks were running. Three preliminary native-client logs are retained. The first driver failed terminal targeting; the next two produced no placed wires and failed the powered indication assertion. Those are failed checks, not passing product demonstrations. The driver now uses a full native wire stack, but that change has **not** been graphically rerun. Real-client controls, meters and screenshots on both backends remain required before including the starter in a future accepted SDK. The released API Workshop evidence remains separate.

## Automated reader review

A fresh-context reviewer used only public APIs, guides and the starter, without reading private WWPG implementation. This was automated source-level usability review, not human certification. Findings were fixed: absent file listings/LICENSE; mutating readout and stale measurements; ambiguous unload/pre-solve checks; missing example extraction; renamed jar paths; nonreproducible failure exercise; misleading maintainer links; first-instance/wiring assumptions; IDE import/provenance instructions; and north-face polarity wording. The tutorial now supplies a read-only interaction, explicit top-face enable, pending readings, exact extraction/tool steps and debugger observations. A later human author trial is still useful.

## Reproduce

From the repository root with Java 21 and Python, run `python tools/documentation_acceptance/check.py prepare`, then `python tools/documentation_acceptance/check.py server --backend NATIVE` and repeat with `JAVA`. Use a clean workspace: the preparation preserves existing extraction directories. The workflow does not add game-run tasks to the starter. Detailed runs use the test-only `tools/documentation_acceptance/init.gradle` against the released runtime; its server task requires all four cases to pass.
