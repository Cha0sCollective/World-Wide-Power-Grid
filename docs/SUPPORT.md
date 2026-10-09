# Declared beta support

WWPG 0.1.0-beta.1 declares 134 content behaviors: 95 block/assembly groups, all 11 built-in CEE panel attachments, and all 28 built-in PG board components. Colored panels/motors share native behavior groups. Certification covers the documented behavior and common placement, editing, removal, reconnect, split/merge, chunk reload, restart and multiplayer paths; it does not certify every upstream configuration.

The [machine-readable matrix](../release/content-matrix.json) links each claim to electrical/gameplay fixtures and the shared lifecycle suite. The [verification record](../release/verification.json) records packaged native/Java runs on Windows and Linux, saved upstream reference circuits, a restarted example world, and two real TCP clients.

## electroenergetics block

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

## powergrid block

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

## electroenergetics panel attachment

| Category | Native registry names |
| --- | --- |
| electronics | `ammeter`, `voltmeter`, `emergency_stop_button`, `cut_off_switch`, `analog_lever`, `steering_wheel`, `indicator_bulb`, `momentary_switch`, `energy_meter`, `tri_polar_energy_meter`, `miniature_circuit_breaker` |

## powergrid board component

| Category | Native registry names |
| --- | --- |
| electronics | `via`, `label`, `triode`, `pentode`, `thyratron`, `vfet`, `bjt_npn`, `bjt_pnp`, `regulator_tube`, `barretter_tube`, `neon_bulb`, `light_bulb`, `connector`, `switch`, `relay`, `relay_dpdt`, `resistor`, `redstone_relay`, `voltage_gauge`, `current_gauge`, `diode`, `capacitor`, `inductor`, `button`, `potentiometer`, `varistor`, `fuse_holder`, `display_module` |

## Additional unclaimed content

These inventory rows retain explicit unverified/unsupported status. Native content may work, but this beta makes no compatibility claim for it. Structural blocks do not acquire new terminals. Optional attachments and moving systems are outside scope.

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

See [INSTALL.md](INSTALL.md) for the pinned upstream AC-source behavior, portable-battery semantics and native platform requirements. Large-network benchmarks, asynchronous solving, trains, contraptions, Sable sublevels, optional integrations and a public extension API remain deferred.
