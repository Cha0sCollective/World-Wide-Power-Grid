# CEE 1.2 development readiness

Work is paused at the user's request. The local preparation candidate is
`0.2.8-dev.cee120.3`; it is not a release or full acceptance claim.

The isolated worktree is `G:/WWPG/local/cee-120-readiness`, branch
`dev/cee-120-readiness`, based on
`d05f15c1aaabd122661cb1f79cd575cbc78a2c19`.
The detailed resume checkpoint is
`local/cee-120/PAUSE_CHECKPOINT.md`.

## Frozen inputs and implementation

The reviewed CEE source is commit
`4828c3487a9fc0703717b415eb07d005c7a48653`.
The development artifact is `electroenergetics-1.21.1-1.2.0-132.jar`, SHA256
`71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7`.
The 1.1.4 reference hash is
`2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f`.
The minimum SDK compile dependency remains CEE 1.1.3.
No upstream drift was substituted for these frozen inputs.

Separate binary adapters handle graph descriptors, property construction,
source polarity and result construction. Shared code retains ownership,
restoration, rejection and gameplay publication. The 1.2 diode uses PG's existing
Native/Java iteration hooks and exact nonlinear residual evaluation. Trial solves
do not advance physical history. Unsupported nonlinear addon models are rejected
explicitly. Capacitor rollback includes voltage, current and integration
conductance. Result publication retains measured branch/transformer currents and
unavailable readings despite CEE's removed current-map constructor.

The old three-phase panel-meter corrections apply only to 1.1.x. The variac
position-qualified voltage correction remains. Default substeps remain sixteen;
there is no native solver change.

## Save compatibility

Inductor history migrates automatically using the world's saved CEE version.
Owned device and world-origin markers prevent repeated conversion and retain
provenance for later unmarked data. A genuinely ambiguous charged history stays
intact and unavailable; an explicit source override can resolve that exceptional
case. Ordinary upgrades require no player-facing choice. New 1.2 capacitor saves
retain current history as well as voltage. In-flight results cannot overwrite
newly read reactive state before its model has been prepared.

A focused cold 1.1.4-to-1.2 transition and a second reopen preserved a held 6 J
specimen with the correct current direction. The source world was retained;
only owned markers were removed from a disposable copy to exercise unmarked
legacy migration. This serialization control holds physical time and does not
certify a transient or a performance result.

## Focused evidence

Primary runtime: Java 21 and NeoForge 21.1.262.

| Check | Passing evidence |
| --- | --- |
| JVM units | 31 targeted tests |
| Catalog/evidence tooling | 38 tests |
| Packaging boundaries | 6 tests |
| CEE 1.2 electrical/migration | 10 GameTests on each backend |
| CEE 1.2 lifecycle | 17 GameTests on each backend |
| CEE 1.2 ownership | One isolated GameTest on each backend |
| CEE 1.1.4 compatibility | 10 GameTests on each backend |
| Cold migration | Legacy setup, development upgrade and development reopen |

Exact jars, source snapshots, hashes, logs and original result identities are in
`local/cee-120/`. The candidate jar is in `frozen-10`, SHA256
`c9f02c92858701897d0f9247c029d17077af77cfce0cb0c04e87cb9247cc185a`.
The player jar contains no tests. Artifact comparisons preserve all 47 public API
class files, four native binaries and seventeen protected generator-timing class
files byte for byte against the retained baseline. The Native lifecycle and
ownership results retain their original frozen-09 identities; explicit runtime
and fixture comparisons are recorded separately.

## Remaining readiness work

Actual CEE 1.1.3 gameplay remains unverified. A cached runtime jar initially
assigned that role was actually 1.1.4. Original reports preserve the loaded
version/hash and have separate correction records. A new freeze must use the
verified 1.1.3 jar, SHA256
`ae7ea74b7b00f175daa5a60a953a6d57f7f775aed0b642a1e9f80c3865cf8be7`.

Stock CEE transient parity is unresolved. An initial four-case control retained
two capacitor/rectifier failures. Its first capacitor waveform matches PG, then
stock stalls near 9.47 V while PG reaches 10 V. The frozen upstream jar accepts a
squared residual norm below 0.01; this is consistent with the discrepancy and
needs further investigation. Failed controls remain failed. PG's acceptance must
not be weakened to imitate an unconverged upstream result. A final-candidate raw
stock comparison is still pending.

Further work includes explicit modern addon-adapter source-polarity coverage,
new fan/panel gameplay fixtures, optional-addon compatibility, a maintained
checked-in readiness runner, and reconciliation with the newer parallel WWPG
beta.3 baseline. This branch preserves its delegated starting baseline.

No full headless acceptance, graphical acceptance, large benchmark, GitHub
publication or release was run or authorized. Published artifacts, historical
worlds and earlier acceptance records remain unchanged.
