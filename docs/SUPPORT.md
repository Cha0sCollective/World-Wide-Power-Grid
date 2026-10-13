# Supported stationary content

WWPG 0.2.2-alpha.1 lets **Create: Electro Energetics (CEE)** and **Power Grid (PG)** work
together in stationary factories: equipment built in the world that stays in
place. Either mod can supply power to the other's supported equipment, and you
can connect and measure circuits with either mod's tools.

## What you can build and operate

| Use | Supported examples |
| --- | --- |
| Make and store power | Generators, alternators, solar panels, batteries, accumulators, capacitors and inductors, with each device's normal charging or depletion behavior. |
| Run a factory | Motors, pumps, lights, heaters, fans, electromagnets, growth lamps, buzzers and alarm bells. |
| Distribute and transform power | Stationary connectors, poles and insulators; transformer assemblies, variacs and stationary Nether-transformer links. |
| Control and protect circuits | Switches, relays, potentiometers, fuses and breakers, plus the built-in CEE panel attachments and PG board components. |
| See what is happening | Both handheld meters, panel and board gauges, energy meters, plotters, CRTs, and thermometer readings on CEE devices that define a temperature. |
| Convert power | CEE's converter and PG's Forge Energy (FE) inverter, following their normal directions and limits. |
| Make and repair equipment | Save, edit, copy, assemble and power PG board designs; use normal wiring tools and replace broken bulbs and fuses. |
| Add computer controls | With optional Pinout and CC:Tweaked, switch eight pins and read their voltages with Lua programs. |

All **11 built-in CEE panel attachments** and **28 built-in PG board components**
are included. Assembly parts retain their normal jobs; poles, cores and other
structural parts do not gain new electrical terminals. The [Stationary Test Yard](STATIONARY_YARD.md)
provides working examples and assembly or repair instructions for the supported
behaviors. Spares in a cabinet are not a working demonstration.

## Limits to keep in mind

Use the [exact versions in the installation guide](INSTALL.md#required-versions).
Support covers the listed stationary behaviors and tested conditions, not every
possible configuration or another version of either mod.

PG's portable battery charges an energy item; its placed block does not supply a
wired circuit. Use CEE's accumulator for rechargeable circuit storage. The pinned
CEE creative battery's AC mode produces 20 Hz despite its frequency selector.
Glass-insulated CEE wire carries essentially no power in the pinned release.
These existing behaviors are preserved; see the [wiring guide](STATIONARY_WIRING.md)
and [installation notes](INSTALL.md#pinned-upstream-behavior).

Moving trains and contraptions, altitude/velocity sensors, Sable sublevels, other
optional integrations, broad version support and large-server performance testing
remain later work. Unsupported electrical constructs are reported and isolated
from the circuit. [Current status](STATUS.md) lists release limitations; [the
long-term specification](../SPEC.md) describes the wider project goals.

## Add-ons in 0.2.1

Extension API **1.0.0** lets developers add stationary electrical equipment to
WWPG. The optional [API Workshop](EXTENSION_API_WORKSHOP.md) shows nine new machine
models, a custom CEE panel indicator and a PG board component. Its two example
mods are required for that world only. Ordinary WWPG factories do not need them.

Developers can follow the [API guide](EXTENSION_API.md) for the full contract.

<details>
<summary>Developer details: electrical models and ownership</summary>

The public API covers a stationary machine or assembly's stable terminals, internal nodes and distinct elements, including parallel branches. Supported models include loads, switches, DC and AC sources, capacitors, inductors, diodes, transformers and controlled sources. Add-ons receive explicit valid or unavailable readings and own their native gameplay, rendering, synchronization and saved state.

Custom CEE properties use the separate CEE API; existing built-in properties are not replaced. PG add-ons use PG's native component and terminal integration, with no second WWPG electrical owner. The examples are counted separately from the inherited native behavior groups.

</details>

## Native items and workflows

PG wires, hanging wires and cords, all 57 ordinary CEE wire variants and native duplex cables keep their native lengths, costs, orientation, rendering and cutting. Native PG banners/dampers and lineman-stick interactions are covered. PG's multimeter reads voltage at either mod's accessible terminals and current on either wire system; CEE's clamp meter reads either wire system. [Tool instructions](STATIONARY_WIRING.md) explain the native limits, including glass insulation and creative-only duplex placement in the pinned CEE release.

PG board designs can be edited, saved, reopened after a restart, copied, assembled,
placed and powered. Copying a design creates separate components; editing or
reloading an installed design keeps its existing connections and component
identities.

With **CC;CPG: Pinout 0.2.1 and CC:Tweaked 1.120.2**, computers can switch all eight
pins and read signed voltages through Pinout's normal Lua API. Lua common pin 9
maps to PG terminal 0; pins 1–8 retain their numbering. Use either wire system to
control supported mixed equipment. The [Pinout yard instructions](STATIONARY_YARD.md#computer-controls-in-the-pinout-edition)
explain the included programs.

## Detailed coverage records

The release retains **164 base behavior groups, or 165 with optional Pinout**.
The [beta.5 support matrix](../release/0.1.0-beta.5/supported-content.json) and
[registry inventory](../release/0.1.0-beta.5/registry-inventory.json) provide the
item-by-item baseline, including cosmetic variants, ingredients, assembly parts
and deferred moving content. The [0.2.2-alpha.1 verification record](../release/0.2.2-alpha.1/verification.json)
checks that baseline with the new jar and worlds, alongside the add-on examples.
Older published artifacts, support claims and check records remain unchanged.

<details>
<summary>Registry IDs for maintainers and bug reports</summary>

These native registry IDs are useful to maintainers; the matrix distinguishes registry types and groups cosmetic variants. Structural parts appear with their checked assembly workflow.

| Category | Native behavior IDs |
| --- | --- |
| computer_control | `pinout:pinout` |
| controls | `electroenergetics:broken_fuse`, `electroenergetics:cut_off_switch`, `electroenergetics:double_switch`, `electroenergetics:emergency_stop_button`, `electroenergetics:fuse`, `electroenergetics:fuse_holder`, `electroenergetics:high_voltage_switch`, `electroenergetics:momentary_switch`, `electroenergetics:potentiometer`, `electroenergetics:redstone_potentiometer`, `electroenergetics:redstone_relay`, `electroenergetics:relay`, `electroenergetics:sulfur_hexafluoride_breaker`, `powergrid:carbon_pile`, `powergrid:carbon_pile_coil`, `powergrid:contactor`, `powergrid:fuse_holder`, `powergrid:hv_breaker`, `powergrid:hv_switch`, `powergrid:lv_button`, `powergrid:lv_switch`, `powergrid:mv_switch`, `powergrid:punch_card_reader`, `powergrid:redstone_converter`, `powergrid:rheostat` |
| conversion | `electroenergetics:converter`, `powergrid:fe_inverter` |
| electronics | `electroenergetics:ammeter`, `electroenergetics:analog_lever`, `electroenergetics:cut_off_switch`, `electroenergetics:diode`, `electroenergetics:electrical_panel`, `electroenergetics:emergency_stop_button`, `electroenergetics:energy_meter`, `electroenergetics:indicator_bulb`, `electroenergetics:miniature_circuit_breaker`, `electroenergetics:momentary_switch`, `electroenergetics:steering_wheel`, `electroenergetics:tri_polar_energy_meter`, `electroenergetics:voltmeter`, `powergrid:barretter_tube`, `powergrid:bjt_npn`, `powergrid:bjt_pnp`, `powergrid:button`, `powergrid:capacitor`, `powergrid:circuit_board`, `powergrid:circuit_design_table`, `powergrid:connector`, `powergrid:current_gauge`, `powergrid:diode`, `powergrid:display_module`, `powergrid:fuse_holder`, `powergrid:inductor`, `powergrid:label`, `powergrid:light_bulb`, `powergrid:modular_display`, `powergrid:neon_bulb`, `powergrid:pentode`, `powergrid:potentiometer`, `powergrid:redstone_relay`, `powergrid:regulator_tube`, `powergrid:relay`, `powergrid:relay_dpdt`, `powergrid:resistor`, `powergrid:switch`, `powergrid:thyratron`, `powergrid:triode`, `powergrid:varistor`, `powergrid:vfet`, `powergrid:via`, `powergrid:voltage_gauge` |
| instruments | `powergrid:crt`, `powergrid:thermometer` |
| loads | `electroenergetics:broken_bulb`, `electroenergetics:bulb`, `electroenergetics:buzzer`, `electroenergetics:creative_resistor`, `electroenergetics:electric_pump`, `electroenergetics:indicator_bulb`, `electroenergetics:resistive_heater`, `electroenergetics:resistor`, `electroenergetics:white_electric_motor`, `powergrid:alarm_bell`, `powergrid:basin_heater`, `powergrid:ceiling_tile_lamp`, `powergrid:constant_speed_motor`, `powergrid:creative_resistor`, `powergrid:electric_fan`, `powergrid:electric_motor`, `powergrid:electric_pump`, `powergrid:electromagnet`, `powergrid:factory_light`, `powergrid:factory_light_light`, `powergrid:heating_coil`, `powergrid:light_fixture`, `powergrid:power_resistor`, `powergrid:servo`, `powergrid:string_light_block` |
| meters | `electroenergetics:ammeter`, `electroenergetics:current_transformer`, `electroenergetics:energy_meter`, `electroenergetics:frequency_meter`, `electroenergetics:synchroscope`, `electroenergetics:tri_polar_energy_meter`, `electroenergetics:voltmeter`, `powergrid:current_gauge`, `powergrid:energy_meter`, `powergrid:plotter`, `powergrid:power_gauge`, `powergrid:voltage_gauge` |
| protection | `powergrid:spark_gap` |
| sources | `electroenergetics:alternator_brushes`, `electroenergetics:alternator_rotor`, `electroenergetics:creative_battery`, `electroenergetics:radiator_panel`, `electroenergetics:stator`, `electroenergetics:three_phase_alternator_brushes`, `electroenergetics:voltage_regulator`, `powergrid:ceiling_tile_solar`, `powergrid:creative_current_source`, `powergrid:creative_voltage_source`, `powergrid:generator_clutch`, `powergrid:generator_commutator`, `powergrid:generator_housing`, `powergrid:generator_induction_rotor`, `powergrid:generator_large_induction_rotor`, `powergrid:generator_vertical_commutator`, `powergrid:solar_panel`, `powergrid:vertical_generator_housing`, `powergrid:winding` |
| storage | `electroenergetics:accumulator`, `electroenergetics:capacitor`, `electroenergetics:high_voltage_capacitor`, `electroenergetics:inductor`, `powergrid:battery`, `powergrid:portable_battery`, `powergrid:potato_battery` |
| transformers | `electroenergetics:redstone_variac`, `electroenergetics:transformer`, `electroenergetics:transformer_core`, `electroenergetics:variac`, `powergrid:nether_transformer`, `powergrid:transformer_core`, `powergrid:transformer_medium`, `powergrid:transformer_small`, `powergrid:variac` |
| wiring | `electroenergetics:catenary_holder`, `electroenergetics:concrete_pole`, `electroenergetics:connector`, `electroenergetics:double_connector`, `electroenergetics:duplex_wire_termination`, `electroenergetics:ground_rod`, `electroenergetics:insulator`, `electroenergetics:pole_mount`, `electroenergetics:quad_connector`, `electroenergetics:triple_connector`, `powergrid:ceiling_tile_connector`, `powergrid:ceiling_tile_junction`, `powergrid:cord_junction`, `powergrid:device_connector`, `powergrid:grounding_rod`, `powergrid:heavy_wire_connector`, `powergrid:socket`, `powergrid:wire_connector` |

</details>
