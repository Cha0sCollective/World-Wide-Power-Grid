# CEE 1.2 development readiness

This development work targets CEE commit
`4828c3487a9fc0703717b415eb07d005c7a48653`, declaring `1.21.1-1.2.0`.
It is preparation for that frozen contract, not a release compatibility claim.
The comparison jar is `electroenergetics-1.21.1-1.2.0-132.jar`, SHA256
`71916b60d3eb000b2b766b410e6dc5af58dbf3f1ff7da40080f3ee8d950393d7`.
The retained 1.1.4 reference has SHA256
`2362decf60ace66037eb2ae79f34fed57b5f152e0435164f5f91912ea837052f`.
The minimum SDK compile dependency remains CEE 1.1.3. No upstream drift check
has been substituted for these exact input identities.

## Integration boundary

The shared bridge owns endpoint identity, topology, restoration, solver
acceptance and gameplay publication. Separate binary adapters implement the
CEE 1.1 and 1.2 graph/property/result contracts. Selection occurs once from the
abstract-versus-concrete electrical-property contract. Reflection is used only
to load the selected implementation, never to call numerical models in the loop.
The 1.2 classes compile separately; their CEE jar does not change SDK compilation.

The 1.2 graph adapter collects original dynamic orientations from the prepared
graph once. Both source stamping and published branch currents use the selected
CEE polarity convention. Result objects retain measured per-substep branch and
transformer current aggregates, independent of upstream's removed current-map
constructor. Availability remains per endpoint and fresh results own their data.

The old three-phase panel-meter corrections are restricted to 1.1.x. The variac
position-qualified voltage correction remains. Graph-connect validation keeps
mandatory, version-specific node descriptors. Capacitor rollback in 1.2 includes
voltage history, current history and the private integration conductance.

## Focused evidence and limits

The isolated branch starts at WWPG `d05f15c1aaabd122661cb1f79cd575cbc78a2c19`.
This preserves the retained duplicate circuit-scan correction and excludes the
rejected wire-power tally and row-cache experiments. Defaults remain 16 substeps.
No native solver change is planned.

Initial boundary compilation passed on Java 21 / NeoForge 21.1.262:
`compileJava compileCee12Java --offline --no-daemon --max-workers=2`, supplying
the exact development jar through `-PceeDevelopmentJar=...`.
Local evidence is in `local/cee-120/compile-02.log`. The earlier sandbox attempt
is retained separately as a failed environment setup attempt.

Nonlinear runtime integration and focused electrical/gameplay checks are still
in progress. Static analysis and compilation do not establish gameplay parity.
In particular, rectifier/capacitor transients, both solver backends, signed energy,
transformer and regulator topology, three-phase meters, rejection and restoration,
partial winding isolation, unload/reload, addon binary compatibility and the new
panel/fan devices require current evidence. No full headless or graphical
acceptance, benchmark, publication or release is authorized by this work.
