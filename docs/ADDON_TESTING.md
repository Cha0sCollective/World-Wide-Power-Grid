# Test and troubleshoot your add-on

Use a small known circuit before testing a factory. The [adjustable load](CREATE_ADDON.md) gives a repeatable 20 V reference and a native save/reload exercise. Check the actual backend with `/wwpg`; run the same circuit with PG's native and Java backends by stopping the instance, changing `solverBackend` to the literal value `"NATIVE"` or `"JAVA"` in `config/powergrid-server.toml`, and restarting. Keep the other tested solver settings unchanged.

## My block exists, but WWPG does not discover it

Check that WWPG is installed, the mod metadata requires it on BOTH sides, and the provider is registered on your **mod event bus** during `RegisterCapabilitiesEvent`. Confirm the registered block/entity type, returned non-null device and exact provider ID. Use `/wwpg at x y z` at the block and inspect startup logs for duplicate owners. A PG-native component does not need a second WWPG device registration.

## The device becomes isolated after a setting change

Use `/wwpg errors` and inspect `prepare()` inputs. Common causes are a constructor-bound violation, duplicate keys, an undeclared node, or mismatched terminal records. Matching only the terminal count is insufficient; key, native number, position and radius all participate. See [validation limits](API_LIMITS.md).

### A deliberate mismatch and its repair

Start with a newly placed **ON / 100 Ω** load in a throwaway test world. In a throwaway copy of the starter, add `if (!enabled) return CircuitDescription.empty();` at the start of `prepare()`. Build, stop the external instance, replace the jar and restart. Switch the load OFF using its top face, then run `/wwpg errors`: the still-declared terminals conflict with the empty prepared description. To repair it, delete that added line, rebuild and replace the jar with the instance stopped, then restart. The saved OFF state must now show zero current after a valid solve. No hot replacement is assumed. For repairs made by a live gameplay control, call `ElectricalDevices.changed` on the server after correcting the inputs.

A broken off-state implementation returns `CircuitDescription.empty()` while `terminals()` still exposes two contacts. The runtime rejects it with this exact exception message:

```text
API minecraft:overworld 11799205, -58, 2479875: IllegalArgumentException: Prepared terminals differ from read-only terminal geometry
```

This is captured output from the native-solver fixture on NeoForge 21.1.248 with the published WWPG 0.2.1 jar. Coordinates vary by test world. Its unavailable snapshot detail was `Invalid extension description: IllegalArgumentException`; `/wwpg errors` carries the fuller cause above. The documentation acceptance fixture captures the real diagnostic and checks unavailable terminal readings, then repairs the same provider and verifies recovery. Keep terminals and use an open switch, or a description with the same terminals and no conducting elements. After repairing a quarantined device, notify `ElectricalDevices.changed(serverLevel, position)` and inspect the next accepted result. Do not print an old measurement as if recovery had already succeeded.

## I see wires, but results are unavailable

Read `MeasurementStatus` before reading optional values. `NOT_READY` means the device/area is not ready; `ISOLATED` identifies an invalid provider or callback; `SOLVE_FAILED` means a calculation was rejected. Paused circuits can also be unavailable. A visible wire does not guarantee a ready endpoint or a valid solve. Clear indicators and readouts; retain previously accepted storage history. `VALID` with zero current is an ordinary measured result, for example an open switch.

## Fixing callback inputs did not resume operation

A throwing `commit()` is quarantined. Correct the cause, then call `ElectricalDevices.changed` on the authoritative server thread after repair. Do not retry by running another solver, advancing history in `prepare()`, or calling `commit()` yourself. Inspect `/wwpg errors` for the original failure and verify a fresh VALID result.

## It works in my client but fails on a dedicated server

Separate logical side from physical process. An integrated server can hide accidental client-class dependencies. Provider factories, geometry, preparation, saving and commit must load without `net.minecraft.client` classes. Check BOTH-side metadata and matching installed jars. Break on the server's callback rather than the remote client's class.

## Storage or settings disappear after reload

Use the debugger observation sequence in the [build-and-install guide](ADDON_WORKFLOW.md). Inspect saved native data **before the first solve**; recharging can hide a missing value. Settings belong in block-entity serialization. Capacitor voltage and inductor current come from accepted `electricalState()` snapshots, not an unavailable result. Supply restored initial values when reconstructing elements; changing an element ID creates a new identity. Preserve IDs during chunk unload, but retire old connections on actual replacement. The load starter saves configuration only; use the general example for electrical storage.

## Acceptance checklist

| Check | Pass condition |
| --- | --- |
| Independent build | Only the published API and declared upstream build dependencies; no WWPG implementation imports |
| Naming | Your add-on and both supplied examples load together without registry/provider collisions |
| Electrical reference | Correct polarity, current and power in a known small circuit; native and Java checked |
| Controls | Configuration changes take effect on subsequent solves; off state retains selectable terminals |
| Native tools | Both wire systems and handheld meters reach the intended handlers |
| Unavailable results | No stale displayed readings or invented zero; no loss of accepted storage history |
| Restart | Saved settings/state inspected before solving; correct output after reopening |
| Chunk lifecycle | Unload/reload preserves state and connections; replacement retires the old identity |
| Deployment | The same jar loads on client and dedicated server and shows synchronized output |
| Repair | A deliberate failure produces a useful diagnostic and resumes after correction/notification |

Record the dependency versions, actual backend, jar hash, circuit, steps and expected/observed values. A passing build alone is not gameplay acceptance. Do not claim Linux graphical testing from a server-only run. CEE numerical adapters additionally require checkpoint/rollback tests; PG components keep their native registration and assembly workflow.
