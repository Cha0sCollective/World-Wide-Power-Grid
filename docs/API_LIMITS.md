# API validation and limits

These are the existing **API 1.0.0** rules, not new restrictions. Numeric bounds are inclusive unless shown with parentheses. NaN and infinities are rejected. These are validation limits, not recommended component sizes.

## Identity and circuit structure

Local keys contain **1–128** characters matching `[a-z0-9_.:/-]+`. Dimension/provider IDs contain at most **256** characters matching `[a-z0-9_.-]+:[a-z0-9_./-]+`. Null references and null collection entries throw `NullPointerException`; invalid values or relationships throw `IllegalArgumentException`.

A device permits **4096** terminals plus internal nodes and **8192** elements. Terminal native numbers are **0–4095**. Node keys are unique across terminals and internal nodes; native terminal numbers and element IDs must each be unique. Every endpoint, control node, transformer secondary and ground must refer to a declared local node. Element endpoints and transformer secondary endpoints must differ; controlled-source control endpoints may be equal.

`prepare()` must return the same complete terminal records as `terminals()`, compared as a set. List order may differ. Keep those records when switching off. `CircuitDescription.empty()` is only appropriate when the geometry query is also empty.

## Constructor inputs

### CircuitDescription

| Argument | Units and accepted values |
| --- | --- |
| `terminals` | non-null immutable-copy input; unique local keys and native IDs; must match the provider geometry |
| `internalNodes` | non-null nodes with keys unique across terminals and internal nodes |
| `elements` | non-null elements with unique IDs and declared endpoint/control/winding nodes |
| `grounds` | non-null set of declared local node keys |

There may be at most 4096 terminals plus internal nodes and 8192 elements. All endpoint, control, winding and ground keys must be declared. Collections reject null entries and are defensively copied.

### DeviceIdentity

| Argument | Units and accepted values |
| --- | --- |
| `dimensionId` | namespaced lowercase ID, at most 256 characters, matching [a-z0-9_.-]+:[a-z0-9_./-]+ |
| `x` | block X coordinate; any int |
| `y` | block Y coordinate; any int |
| `z` | block Z coordinate; any int |
| `providerId` | namespaced lowercase ID with the same grammar and limit as dimensionId |
| `slotId` | stable local key, 1..128 characters matching [a-z0-9_.:/-]+ |

### EndpointIdentity

| Argument | Units and accepted values |
| --- | --- |
| `device` | non-null complete stable device address |
| `nodeKey` | stable local key, 1..128 characters matching [a-z0-9_.:/-]+ |

### InternalNode

| Argument | Units and accepted values |
| --- | --- |
| `key` | stable local key, 1..128 characters matching [a-z0-9_.:/-]+ |

### Terminal

| Argument | Units and accepted values |
| --- | --- |
| `key` | stable local key, 1..128 characters matching [a-z0-9_.:/-]+ |
| `nativeId` | stable upstream terminal number in [0, 4095] inclusive; unique within a description |
| `position` | non-null block-local position, already transformed for orientation |
| `hitRadius` | selection radius in blocks, finite in [1e-12, 2] inclusive |

### LocalPoint

| Argument | Units and accepted values |
| --- | --- |
| `x` | block-local X coordinate, finite in [-64, 64] inclusive |
| `y` | block-local Y coordinate, finite in [-64, 64] inclusive |
| `z` | block-local Z coordinate, finite in [-64, 64] inclusive |

### Element

| Argument | Units and accepted values |
| --- | --- |
| `id` | stable local key, 1..128 characters matching [a-z0-9_.:/-]+ |
| `positiveNode` | declared local key; different from negativeNode |
| `negativeNode` | declared local key; different from positiveNode |
| `model` | non-null captured electrical model |

### TickContext

| Argument | Units and accepted values |
| --- | --- |
| `gameTime` | nonnegative dimension game tick |
| `tickSeconds` | simulated seconds in [1e-12, 1] inclusive |
| `substeps` | electrical steps per tick in [1, 4096] inclusive |

### NodeResult

| Argument | Units and accepted values |
| --- | --- |
| `lastVoltageVolts` | signed last voltage in volts; finite, with magnitude at most 1e15 (inclusive) |
| `meanVoltageVolts` | signed arithmetic mean voltage in volts; finite, with magnitude at most 1e15 (inclusive) |
| `rmsVoltageVolts` | RMS voltage in volts, finite in [0, 1e15] inclusive |

### ElementResult

| Argument | Units and accepted values |
| --- | --- |
| `lastVoltageVolts` | signed last voltage in volts; finite, with magnitude at most 1e15 (inclusive) |
| `meanVoltageVolts` | signed mean voltage in volts; finite, with magnitude at most 1e15 (inclusive) |
| `rmsVoltageVolts` | RMS voltage in volts in [0, 1e15] inclusive |
| `lastCurrentAmps` | signed last current in amperes; finite, with magnitude at most 1e15 (inclusive) |
| `meanCurrentAmps` | signed mean current in amperes; finite, with magnitude at most 1e15 (inclusive) |
| `rmsCurrentAmps` | RMS current in amperes in [0, 1e15] inclusive |
| `meanPowerWatts` | mean signed V*I in watts; finite, with magnitude at most 1e15 (inclusive) |
| `energyJoules` | signed absorbed energy in joules; finite, with magnitude at most 1e15 (inclusive) |

### DeviceResults

| Argument | Units and accepted values |
| --- | --- |
| `context` | non-null authoritative timestep |
| `status` | non-null availability status |
| `nodes` | non-null map of valid local keys to non-null node readings; at most 4096 entries |
| `elements` | non-null map of valid local IDs to non-null element readings; at most 8192 entries |
| `electricalState` | non-null history map; at most 8192 entries, with IDs present in elements |
| `detail` | non-null explanation of at most 1024 characters |

Non-VALID snapshots require all three maps to be empty. Every history ID must have an element reading. Collections reject null entries and are defensively copied.

### TerminalMeasurement

| Argument | Units and accepted values |
| --- | --- |
| `status` | non-null status; VALID exactly when voltageVolts is present |
| `voltageVolts` | non-null optional signed volts; present value must be finite, with magnitude at most 1e15 (inclusive) |
| `detail` | non-null explanation of at most 1024 characters |

### VoltageMeasurement

| Argument | Units and accepted values |
| --- | --- |
| `context` | non-null authoritative timestep |
| `status` | non-null status; VALID exactly when voltage is present |
| `voltage` | non-null optional accepted voltage summary |
| `detail` | non-null explanation of at most 1024 characters |

### ElectricalModel.Resistor

| Argument | Units and accepted values |
| --- | --- |
| `resistanceOhms` | resistance in ohms; finite, in [1e-12, 1e15] (inclusive) |

### ElectricalModel.Switch

| Argument | Units and accepted values |
| --- | --- |
| `closed` | true for the conducting state; false for an open circuit |
| `onResistanceOhms` | closed-state resistance in ohms; finite, in [1e-12, 1e15] (inclusive) |

### ElectricalModel.VoltageSource

| Argument | Units and accepted values |
| --- | --- |
| `voltageVolts` | Vpositive minus Vnegative, in volts; finite, with magnitude at most 1e15 (inclusive) |

### ElectricalModel.CurrentSource

| Argument | Units and accepted values |
| --- | --- |
| `currentAmps` | current from positive to negative node, in amperes; finite, with magnitude at most 1e15 (inclusive) |

### ElectricalModel.AcVoltageSource

| Argument | Units and accepted values |
| --- | --- |
| `peakVoltageVolts` | peak amplitude in volts, finite in [0, 1e15] inclusive |
| `frequencyHertz` | frequency in hertz, finite in [0, 1e15] inclusive |
| `phaseRadians` | phase in radians; finite, with magnitude at most 1e15 (inclusive) |
| `offsetVoltageVolts` | DC offset in volts; finite, with magnitude at most 1e15 (inclusive) |

### ElectricalModel.Capacitor

| Argument | Units and accepted values |
| --- | --- |
| `capacitanceFarads` | capacitance in farads; finite, in [1e-12, 1e15] (inclusive) |
| `initialVoltageVolts` | restored/initial oriented voltage in volts; finite, with magnitude at most 1e15 (inclusive) |

### ElectricalModel.Inductor

| Argument | Units and accepted values |
| --- | --- |
| `inductanceHenries` | inductance in henries; finite, in [1e-12, 1e15] (inclusive) |
| `initialCurrentAmps` | restored/initial oriented current in amperes; finite, with magnitude at most 1e15 (inclusive) |

### ElectricalModel.Diode

| Argument | Units and accepted values |
| --- | --- |
| `saturationCurrentAmps` | saturation current in amperes, finite in [1e-30, 1e15] inclusive |
| `seriesResistanceOhms` | series resistance in ohms; finite, in [1e-12, 1e15] (inclusive) |
| `temperatureCelsius` | temperature in Celsius, finite in (-273.15, 1e15] |
| `idealityFactor` | dimensionless ideality factor; finite, in [1e-12, 1e15] (inclusive) |

### ElectricalModel.Transformer

| Argument | Units and accepted values |
| --- | --- |
| `secondaryPositiveNode` | declared local key different from secondaryNegativeNode |
| `secondaryNegativeNode` | declared local key different from secondaryPositiveNode |
| `ratio` | signed Vsecondary/Vprimary, with finite magnitude in [1e-12, 1e12] inclusive; negative reverses polarity |

### ElectricalModel.ControlledVoltageSource

| Argument | Units and accepted values |
| --- | --- |
| `controlPositiveNode` | declared local control-node key |
| `controlNegativeNode` | declared local control-node key; may equal controlPositiveNode |
| `gain` | dimensionless voltage gain; finite, with magnitude at most 1e15 (inclusive) |

### ElectricalModel.ControlledCurrentSource

| Argument | Units and accepted values |
| --- | --- |
| `controlPositiveNode` | declared local control-node key |
| `controlNegativeNode` | declared local control-node key; may equal controlPositiveNode |
| `transconductanceSiemens` | current/voltage gain in siemens; finite, with magnitude at most 1e15 (inclusive) |

### ElementState.Capacitor

| Argument | Units and accepted values |
| --- | --- |
| `voltageVolts` | accepted oriented capacitor voltage in volts; finite, with magnitude at most 1e15 (inclusive) |

### ElementState.Inductor

| Argument | Units and accepted values |
| --- | --- |
| `currentAmps` | accepted oriented inductor current in amperes; finite, with magnitude at most 1e15 (inclusive) |

### CeeElectricalParameters

| Argument | Units and accepted values |
| --- | --- |
| `conductanceSiemens` | conductance in siemens, finite in [0, 1e15] inclusive |
| `currentSourceAmps` | CEE-native signed source current in amperes; finite, with magnitude at most 1e15 (inclusive) |
| `voltageSourceVolts` | CEE-native signed source voltage in volts; finite, with magnitude at most 1e15 (inclusive) |
| `idealVoltageSource` | must be true for nonzero voltageSourceVolts; retain true for ideal-source zero crossings |

### CeeSubstepContext

| Argument | Units and accepted values |
| --- | --- |
| `previousVoltageVolts` | first-node minus second-node voltage in volts; finite, with magnitude at most 1e15 (inclusive) |
| `substep` | zero-based index in [0, totalSubsteps-1] |
| `totalSubsteps` | step count in [1, 4096] inclusive |
| `substepSeconds` | finite simulated seconds in (0, 1] |

### CeeSubstepResult

| Argument | Units and accepted values |
| --- | --- |
| `voltageVolts` | first-node minus second-node voltage in volts; finite, with magnitude at most 1e15 (inclusive) |
| `currentAmps` | first-node to second-node current in amperes; finite, with magnitude at most 1e15 (inclusive) |
| `substep` | zero-based index in [0, totalSubsteps-1] |
| `totalSubsteps` | step count in [1, 4096] inclusive |
| `substepSeconds` | finite simulated seconds in (0, 1] |

## Values used by the beginner example

`new ElectricalModel.Resistor(100)` means 100 Ω. `new ElectricalModel.Capacitor(1e-13, 0)` is rejected even though its capacitance is positive: the minimum is 1e-12 F. A terminal radius of `.12` means 0.12 blocks, not pixels. Convert model coordinates from pixels by dividing by 16.

CEE parameters use **CEE source polarity**. The adapter converts that to PG; do not invert the parameters a second time. CEE substep seconds permit any finite value greater than zero up to one, whereas general `TickContext.tickSeconds` has a 1e-12 minimum.

[Back to the author guide](EXTENSION_API.md) · [Build your own load](CREATE_ADDON.md)
