# Upstream behavior and compatibility checks

This describes the local 0.2.8 audit corrections. It is not a new acceptance
record for the published 0.2.8-beta1 release.

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

## Generator timing correction

The correction introduced in 0.2.7 remains enabled. For eligible native PG
generators it solves field current, electrical output and rotor response
together during the electrical substeps. It retains PG's clutch sampling
timing and force limits and replaces the managed path's electrical
approximation of inertia with the directly calculated rotor response.
Unsupported models retain PG's original path.

This is an intentional model difference, especially under overload. A local
comparison of 0.2.6 and 0.2.7 at sixteen substeps found closely matching settled
readings around 32 kW, while heavy overloads that cycled in 0.2.6 settled at
reduced speed in 0.2.7. Both clutches still saturated. The observations do not
establish identical load-step transients or certify real-world generator
behavior. Keep stock-reference expectations separate from correction-specific
stability and excitation-control checks.

The existing `-Dwwpg.generatorCoupling=false` switch selects the native PG
generator timing path for controlled comparisons. It is not required for normal
operation. Preserve original comparisons and identify the switch in evidence.

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
