# CEE 1.2 development readiness

The local preparation candidate is `0.2.8-dev.cee120.3`. Focused work resumed
after the saved checkpoint; this report records its verified inputs and limits.
It is not a release or full acceptance claim.

The isolated worktree is `G:/WWPG/local/cee-120-readiness`, branch
`dev/cee-120-readiness`, based on
`d05f15c1aaabd122661cb1f79cd575cbc78a2c19`.
The original pause checkpoint remains at `local/cee-120/PAUSE_CHECKPOINT.md`.
Resumed results are recorded separately; earlier attempts are preserved.

## Frozen inputs and implementation

The reviewed CEE source is commit
`4828c3487a9fc0703717b415eb07d005c7a48653`.
The development artifact is `electroenergetics-1.21.1-1.2.0-132.jar`, SHA256
`71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7`.
The 1.1.4 reference hash is
`2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f`.
The verified 1.1.3 compatibility jar is `qYJdIoAx-KWxEmmON.jar`, SHA256
`ae7ea74b7b00f175daa5a60a953a6d57f7f775aed0b642a1e9f80c3865cf8be7`.
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
| CEE readiness runner | 12 preflight/evidence tests |
| Packaging boundaries | 6 tests |
| CEE 1.2 electrical/migration | 10 GameTests on each backend |
| CEE 1.2 lifecycle | 17 GameTests on each backend |
| CEE 1.2 ownership | One isolated GameTest on each backend |
| CEE 1.1.4 compatibility | 10 GameTests on each backend |
| Actual CEE 1.1.3 compatibility | 10 GameTests on each backend, `cee113-core-*-11` |
| Custom adapter and rejection checks | 7 GameTests on each backend on CEE 1.2 and 1.1.3, `*-adapters-*-15` |
| CEE 1.2 standalone/panel fans | 2 GameTests on each backend, `cee120-fans-*-15` |
| Cold migration | Legacy setup, development upgrade and development reopen |

Exact jars, source snapshots, hashes, logs and original result identities are in
`local/cee-120/`. The original candidate jar is in `frozen-10`, SHA256
`c9f02c92858701897d0f9247c029d17077af77cfce0cb0c04e87cb9247cc185a`.
The player jar contains no tests. Artifact comparisons preserve all 47 public API
class files, four native binaries and seventeen protected generator-timing class
files byte for byte against the retained baseline. The Native lifecycle and
ownership results retain their original frozen-09 identities; explicit runtime
and fixture comparisons are recorded separately.

The corrected compatibility freeze is `frozen-11`; the final added companion
fixtures are in `frozen-15`. The player bytes are identical throughout.
`fixture-equivalence-10-15.json` verifies all 312 existing companion class files
are unchanged and identifies five added class files. It also verifies that all
323 player classes shared by the reference artifact are byte-identical. Original
results keep their original input, log and companion identities.

## Stock CEE control and residual diagnosis

`cee120-stock-raw-14` ran the four bridge-free controls on the final player
candidate's matching source. Signed Norton and diode controls passed; capacitor
and rectifier controls failed. Stock capacitor voltage stalled at 9.462279 V
and the rectifier reached 5.465890 V. These are retained failures.

`cee120-stock-tight-14` used the same frozen CEE, reference and companion jars
with one explicitly enabled test-only mixin: the squared residual threshold in
`Network.runSolver` changed from `1e-2` to `1e-16`. Its application is recorded
in the log. All four controls passed; capacitor voltage reached 9.99999994 V
and rectifier voltage reached 8.570958 V. The preserved PG result was
8.570858 V. This establishes the threshold's causal role in these two fixture
failures; it does not certify every upstream nonlinear model.

The diagnostic is absent from the player jar and disabled in the raw reference
run. No production assertion or PG solver was weakened. Native CEE's dynamic
current publication still uses its final property stamp, whereas WWPG retains
measured substep current maps; these readings are not claimed to be identical.

## Repeat a focused preparation

The checked-in `tools/cee_readiness.py` provides only explicit focused groups.
It checks embedded versions as well as hashes, uses fresh output directories,
verifies frozen source copies, excludes runtime rebuilds, records actual loaded
inputs and requires the expected positive test count and clean shutdown. Runs
retain the 600-second bound. A copied input world is inventoried before and after;
the source world is never used as the run directory.

For example, from this worktree after preparing Java 21:

```text
python tools/cee_readiness.py run --inputs local/cee-120/frozen-15/inputs.json --runtime cee120 --backend JAVA --group adapters --output local/cee-120/new-adapter-attempt
```

Other groups are `core`, `lifecycle`, `ownership`, `fans`, `stock` and
`cold-migration`. Fans require the modern runtime. `--tight-cee-residual` is
restricted to modern stock controls and labels the result as a diagnostic.
`freeze --from-inputs ... --cee113 ... --output ...` creates a new preserved
copy and requires the verified minimum-version jar explicitly. Replacing a test
companion or reference artifact uses `--gametest` or `--reference` and
`--current-sources`; player sources must still match the preserved runtime.
Changed Gradle orchestration also requires a new freeze.

## Remaining readiness work

An earlier cached jar assigned the 1.1.3 role was actually 1.1.4. Original
reports preserve their true loaded version/hash and separate correction records.
The new verified 1.1.3 runs close that gameplay gap without rewriting old evidence.

Unmodified stock transient parity remains failed as described above. Optional
external addons and real Sable runtime integration are not certified by these
focused controls. Old addons that link CEE's removed constructors or registry
field descriptors need their own compatible builds.

Integration with the separate newer WWPG beta.3 baseline remains pending. This
branch preserves its delegated starting baseline and protected generator-timing
bytes; it does not silently import changes from the other worktree. Any chosen
integration needs its own artifact comparison and affected focused checks.

No full headless acceptance, graphical acceptance, large benchmark, GitHub
publication or release was run or authorized. Published artifacts, historical
worlds and earlier acceptance records remain unchanged.
