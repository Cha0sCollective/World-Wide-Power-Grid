# Upstream behavior and compatibility checks

This describes the audit corrections in 0.2.8-beta.2 and the beta.3 parity revert. The separate
[acceptance record](../release/0.2.8-beta.3/ACCEPTANCE.md) preserves the actual
tested source, coverage and remaining uncertainty. Historical beta1 evidence
continues to describe its original runtime.

## Preserved behavior

For supported PG and CEE devices, WWPG preserves electrical equations,
participating substeps, stored energy, accepted history and native gameplay
updates. Reference fixtures compare stock mod behavior with WWPG at the same
settings. They check waveforms, storage and transition timing using their
declared numerical tolerances; tolerances are not enlarged to accommodate a
failed comparison.

Mixed PG/CEE circuits have no directly equivalent shared stock solver. Their
checks combine upstream device references with electrical relationships and
public measurement checks. Passing those checks does not prove universal
parity for every circuit or extension.

A rejected or restoring electrical frame cannot publish an accepted reading
or advance stored state. Windings sharing an alternator rotor share that
acceptance boundary. An unsupported extension endpoint is isolated with a
diagnostic while unrelated supported circuits continue. These are WWPG's
documented failure-handling boundaries.

## Native PG generator timing

Starting with 0.2.8-beta.3, WWPG uses PG's original
generator, rotor and clutch update timing. The coupled timing model introduced
in 0.2.7 is removed, following upstream confirmation that the observed overload
oscillation is intended behavior. Self-excited generators may therefore hunt
under the same loads as stock PG. Flat output is not a parity requirement.

PG owns field sampling, its inertia companion, torque accumulation and the
ordinary rotor/clutch tick. WWPG retains the shared electrical substep schedule
and independent restoration, isolation and native allocation safeguards.
The old generatorCoupling property no longer selects an alternative model.

The [0.2.7 timing document](developer/GENERATOR_TIMING.md) is historical and
describes the removed model. Previous acceptance remains evidence for its named
runtime, not acceptance of this revert. Current focused comparisons require
matching source current, rotor speed, source resistance and availability against
the native timing reference, including overload and excitation control changes.

## Restoration and native failures

Scoped restoration is active only when bounded saved-wire batching is available.
Disabling batching or selecting a zero budget selects compatible restoration
behavior and reports the effective fallback. Negative budgets and invalid part
limits remain configuration errors.

The native allocation guards change allocation ownership and failure handling,
not numerical algorithms. Related replacement arrays are committed together;
allocation failure must not expose an incomplete matrix or stale accepted
solution. Focused subprocess fault injection checks these boundaries without
exhausting the machine's actual heap. This is not a guarantee that an application
can recover from every form of system-wide memory exhaustion.

## Evidence limits

The historical Linux Native/BASE/21.1.248 cold-start motor assertion remains a
separate investigation. Passing repeats alone do not establish a cause or fix.
Focused audit checks do not replace full headless or graphical acceptance.
See [testing policy](TESTING.md) for their separate authorization and evidence.
