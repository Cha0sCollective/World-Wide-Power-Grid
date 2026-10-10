# Supported content

WWPG 0.1.0-beta.2 retains the original 134 checked behaviors: 95 block/assembly groups, all 11 built-in CEE panel attachments, and all 28 built-in PG board components. It also adds explicit support for two handheld meters. Colored panels and motors share native behavior groups. The counts describe inventory groups, not separate automated tests; the packaged suite contains 117 electrical/gameplay/lifecycle fixtures and one startup-registration regression, some covering multiple groups.

The [machine-readable matrix](../release/content-matrix.json) links each declared behavior to electrical/gameplay tests and a shared lifecycle suite. Coverage includes placement, editing, removal, reconnection, network split/merge, chunk reload, restart, and multiplayer interactions. It does not establish every configuration of every device.

The [local verification record](../release/verification.json) contains passing runs. An earlier GitHub run failed a chunk-reload assertion; later runs passed, and the cause remains unresolved. Read [current status](STATUS.md) alongside this support list.

Names below are native registry IDs, useful when checking upstream code or the matrix. CEE IDs use the `electroenergetics:` namespace; PG IDs use `powergrid:`.

## CEE blocks and assemblies

| Category | Native registry names |
| --- | --- |
| controls | `fuse`, `broken_fuse`, `fuse_holder`, `cut_off_switch`, `double_switch`, `redstone_relay`, `high_voltage_switch`, `sulfur_hexafluoride_breaker`, `relay`, `momentary_switch`, `emergency_stop_button`, `potentiometer`, `redstone_potentiometer` |
| electronics | `electrical_panel`, `diode` |
| loads | `bulb`, `electric_pump`, `resistor`, `creative_resistor`, `indicator_bulb`, `resistive_heater`, `white_electric_motor` |
| meters | `ammeter`, `voltmeter`, `energy_meter`, `tri_polar_energy_meter`, `current_transformer`, `frequency_meter`, `synchroscope` |
| sources | `creative_battery`, `alternator_rotor`, `alternator_brushes`, `three_phase_alternator_brushes`, `stator` |
| storage | `accumulator`, `capacitor`, `high_voltage_capacitor`, `inductor` |
| transformers | `transformer`, `variac`, `redstone_variac` |
| wiring | `connector`, `double_connector`, `triple_connector`, `quad_connector`, `ground_rod`, `duplex_wire_termination` |

## PG blocks and assemblies

| Category | Native registry names |
| --- | --- |
| controls | `lv_switch`, `lv_button`, `mv_switch`, `hv_switch`, `hv_breaker`, `contactor`, `fuse_holder`, `rheostat` |
| electronics | `circuit_board`, `circuit_design_table`, `modular_display` |
| loads | `creative_resistor`, `power_resistor`, `heating_coil`, `basin_heater`, `light_fixture`, `factory_light`, `factory_light_light`, `ceiling_tile_lamp`, `electric_motor`, `constant_speed_motor`, `servo`, `electric_pump` |
| meters | `voltage_gauge`, `current_gauge`, `power_gauge`, `energy_meter` |
| sources | `creative_voltage_source`, `creative_current_source`, `generator_induction_rotor`, `generator_commutator`, `generator_clutch`, `generator_housing`, `solar_panel`, `winding` |
| storage | `portable_battery` |
| transformers | `transformer_core`, `transformer_small`, `transformer_medium`, `variac` |
| wiring | `wire_connector`, `heavy_wire_connector`, `cord_junction`, `ceiling_tile_connector`, `ceiling_tile_junction`, `device_connector`, `socket`, `grounding_rod` |

## CEE panel attachments

| Category | Native registry names |
| --- | --- |
| electronics | `ammeter`, `voltmeter`, `emergency_stop_button`, `cut_off_switch`, `analog_lever`, `steering_wheel`, `indicator_bulb`, `momentary_switch`, `energy_meter`, `tri_polar_energy_meter`, `miniature_circuit_breaker` |

## Handheld meters added in beta.2

| Tool | Mixed-circuit behavior |
| --- | --- |
| PG `multimeter` | Voltage between two accessible terminals from either mod; current on PG or CEE wires. |
| CEE `clamp_meter` | Hold to measure current on PG or CEE wires. |

The [additional support record](../release/0.1.0-beta.2/content-additions.json) links these tools to server fixtures and the real-client test. The original beta.1 checks covered installed meters, not these handheld interactions. See [meter instructions](INSTALL.md#handheld-meters); optional lineman-stick extension interactions have no compatibility claim.

## PG circuit-board components

| Function | Native registry names |
| --- | --- |
| Routing and labels | `via`, `label`, `connector` |
| Passive and protection | `resistor`, `capacitor`, `inductor`, `potentiometer`, `varistor`, `fuse_holder` |
| Switching and control | `switch`, `button`, `relay`, `relay_dpdt`, `redstone_relay` |
| Semiconductor devices | `diode`, `bjt_npn`, `bjt_pnp`, `vfet` |
| Tubes and lamps | `triode`, `pentode`, `thyratron`, `regulator_tube`, `barretter_tube`, `neon_bulb`, `light_bulb` |
| Readings and display | `voltage_gauge`, `current_gauge`, `display_module` |

## Additional content outside the declared set

`Unverified` means this beta has no completed compatibility claim for that group. `Unsupported` means it is outside the release scope. Structural blocks retain their native role and do not acquire new terminals. Optional attachments and moving systems are excluded.

- `electroenergetics:insulator` (unverified)
- `electroenergetics:concrete_pole` (unverified)
- `electroenergetics:pole_mount` (unverified)
- `electroenergetics:radiator_panel` (unverified)
- `electroenergetics:voltage_regulator` (unverified)
- `electroenergetics:broken_bulb` (unverified)
- `electroenergetics:transformer_core` (unverified)
- `powergrid:generator_large_induction_rotor` (unverified)
- `powergrid:generator_vertical_commutator` (unverified)
- `powergrid:vertical_generator_housing` (unverified)
- `powergrid:ceiling_tile_solar` (unverified)
- `powergrid:string_light_block` (unverified)
- `powergrid:nether_transformer` (unverified)
- `powergrid:plotter` (unverified)
- `electroenergetics:altitude_sensor` (unsupported)
- `electroenergetics:velocity_sensor` (unsupported)

See [INSTALL.md](INSTALL.md#pinned-upstream-behavior) for the tested upstream AC-source and portable-battery behavior. Large-network benchmarks, asynchronous solving, trains, contraptions, Sable sublevels, optional integrations, and a public extension API remain deferred.
