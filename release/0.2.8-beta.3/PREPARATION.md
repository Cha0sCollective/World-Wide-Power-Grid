# Historical local beta.3 preparation

This snapshot predates the completed [release acceptance](ACCEPTANCE.md).

This candidate retains the CEE restoration scan correction from `6bce82c` and
removes the generator coupling introduced in 0.2.7. Following the user's
confirmation from upstream, PG's original rotor and clutch timing, including
overload oscillation, is the required behavior. Historical timing documents and
acceptance remain identified with their original versions.

Focused Windows checks passed on Native and Java with CEE 1.1.4 and NeoForge
21.1.262: 102 candidate GameTests, eight native-timing reference GameTests and
seven rollback/acceptance-boundary unit tests. Cases cover transformer and
carbon-pile excitation, shaft/redstone variacs, load/drive transitions, direct
rotor execution, restoration, isolation and loading. All 72 GameTest namespaces
remain classified.

The reference uses the retained mask1 runtime with generator coupling explicitly
disabled. It is not a fresh PG-only execution. Traces closely match, with zero
availability mismatches; the largest observed differences were 0.843 V, 0.037 RPM,
0.056 A and 0.001455 clutch load. These are observations from the recorded runs, not new
acceptance tolerances or proof of identical behavior in every circuit.

Package inspection verified unchanged API classes and native-library entries,
byte-identical loading-fix classes, and no GameTests inside the gameplay jar.
The final release-target label was corrected from beta.2 to beta.3 after focused
testing. ZIP entry comparison verified that this JSON label is the only changed
entry; all executable code and other resources are identical. Focused results
retain their original tested artifact identity and are explicitly reused.
Detailed traces, worlds and failed attempts remain local under
`local/beta3-parity`. The first reference attempt stopped before gameplay because
of the companion's exact-version binding; its replacement differs only in test
metadata. A later reference attempt exposed settling-specific fixture assertions;
those failures are retained and replaced with cycle-aware powered-state checks.

This is not release acceptance. No fresh full headless suite, Linux acceptance,
graphical lane or original-yard execution was performed. The previous successful
yard runs describe the retained loading fix before this timing revert. Fresh
applicable headless and graphical acceptance is required before publication;
neither lane starts automatically. Freeze the newest supported dependency inputs
for that explicitly requested campaign. Nothing has been pushed or published.
