# WWPG 0.2.0 changelog

The accepted verification record identifies the implementation, artifacts and completed functional checks.

## Added

- Independently versioned general extension API 1.0.0 with NeoForge discovery,
  terminal geometry, stable circuit identities, electrical models, accepted
  state, and measurement facades.
- Separate CEE property API for an add-on's own exact property classes,
  startup registration, substep callbacks, and numerical checkpoints.
- Independent general-machine and native CEE-panel/PG-board example projects,
  source ZIPs, SDK source/Javadoc artifacts, and an explicit-version Maven ZIP.
- Extension API Workshop with eleven playable stations and base/Pinout editions.
- External add-on electrical, fault, pause/step, real unload, cold replacement,
  assembly, wire, meter, and saved-state fixtures.
- Graphical and real two-client workshop acceptance drivers, classfile ABI
  inspection, exact extracted SDK compilation, and locked release evidence tools.

## Changed

- Both native wire systems and handheld meters can resolve registered extension
  terminals while preserving their upstream item workflows.
- Electrical callbacks run under the shared synchronous PG schedule. Immutable
  results report unavailable or failed frames explicitly.
- Persisted API ownership distinguishes a saved owner from a later block
  replacement, including before native wire entities finish loading. Exact old
  PG and CEE wire retirement survives saving and stopping before readiness;
  cleanup preserves newly installed wire identities.
- Native tools report loading extension terminals and refuse placement until
  their owner and old-wire retirement are ready.
- Live diode parameter updates retain existing electrical objects and accepted
  dynamic history when circuit structure remains unchanged.
- Rejected solve and callback frames do not publish stale compatibility readings.
  Compatibility-owned electrical history and checkpointed CEE property state
  restore according to the documented scoped rollback contract.
- Throwing gameplay commits remain isolated until an explicit repair notification.
- Global pause and one-tick stepping preserve exactly-once compatibility updates
  and clear unavailable meter readings.
- Acceptance runners handle Windows batch paths and arguments containing spaces.
- A legacy demonstration observes the momentary lamp after the accepted CEE
  gameplay frame rather than before its commit. Native pulse duration, strict
  brightness and OFF timing remain unchanged; the solver model is unchanged.

The upstream dependency pins and stationary scope remain unchanged. Native PG
content retains its own solver models and accepted-step behavior. No public raw
matrix access, moving-system contract, replacement Lua API, or broad version
range is introduced.
