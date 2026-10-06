"""Inventory the pinned upstream sources and jars. Run only when deliberately updating the matrix."""
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--cee-source", type=Path, required=True)
parser.add_argument("--pg-source", type=Path, required=True)
parser.add_argument("--jars", type=Path, required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
lock = json.loads((root / "release/artifacts.json").read_text())
sources = {"electroenergetics": args.cee_source, "powergrid": args.pg_source}
packages = {"electroenergetics": "com/george_vi/electroenergetics", "powergrid": "org/patryk3211/powergrid"}
registries = {"electroenergetics": "CEEBlocks.java", "powergrid": "collections/ModdedBlocks.java"}

# Scope follows the user's stationary-factory release categories. Decorative structural
# parts are recorded with their electrical assemblies; moving equipment is excluded.
scope = {
    "electroenergetics": {
        "wiring": "connector insulator concrete_pole pole_mount double_connector triple_connector quad_connector ground_rod duplex_wire_termination",
        "sources": "creative_battery alternator_rotor alternator_brushes three_phase_alternator_brushes stator radiator_panel voltage_regulator",
        "loads": "bulb broken_bulb electric_pump resistor creative_resistor indicator_bulb resistive_heater",
        "controls": "fuse broken_fuse fuse_holder cut_off_switch double_switch redstone_relay high_voltage_switch sulfur_hexafluoride_breaker relay momentary_switch emergency_stop_button potentiometer redstone_potentiometer",
        "storage": "accumulator capacitor high_voltage_capacitor inductor",
        "transformers": "transformer transformer_core variac redstone_variac",
        "meters": "ammeter voltmeter energy_meter tri_polar_energy_meter current_transformer frequency_meter synchroscope",
        "electronics": "electrical_panel diode",
    },
    "powergrid": {
        "wiring": "wire_connector heavy_wire_connector cord_junction ceiling_tile_connector ceiling_tile_junction device_connector socket grounding_rod",
        "sources": "creative_voltage_source creative_current_source generator_induction_rotor generator_large_induction_rotor generator_commutator generator_vertical_commutator generator_clutch generator_housing vertical_generator_housing solar_panel ceiling_tile_solar winding",
        "loads": "creative_resistor power_resistor heating_coil basin_heater light_fixture factory_light factory_light_light ceiling_tile_lamp electric_motor constant_speed_motor servo electric_pump string_light_block",
        "controls": "lv_switch lv_button mv_switch hv_switch hv_breaker contactor fuse_holder rheostat",
        "storage": "portable_battery",
        "transformers": "transformer_core transformer_small transformer_medium nether_transformer variac",
        "meters": "voltage_gauge current_gauge power_gauge energy_meter plotter",
        "electronics": "circuit_board circuit_design_table modular_display",
    },
}
circuits = {
    "wiring": "Mixed source/load, branch and loop, ground reference, cut and reconnect, both wire tools.",
    "sources": "Drive an opposite-mod resistive load; vary rotation or voltage; check current and gameplay feedback.",
    "loads": "Opposite-mod source, control in series; verify real light, heat, pumping or mechanical output and loss of power.",
    "controls": "Mixed source/load; operate control, vary resistance, or trip protection; repair and reconnect.",
    "storage": "Charge and discharge into an opposite-mod load; compare substep evolution, then save/restart.",
    "transformers": "Mixed source/load on both windings; step-up/down, reverse polarity, edit ratio under load.",
    "meters": "Mixed circuit with known DC and AC conditions; compare display and energy accumulation with solved values.",
    "electronics": "Mixed source with panel/board controls and meters; nonlinear operation, edit, replacement and reload.",
}
models = {
    "wiring": "Native endpoint geometry, wire resistance, and physical grounding.",
    "sources": "Upstream prepared source model and mechanical feedback; PG content keeps its native model.",
    "loads": "Upstream prepared resistive/source model; solved current and voltage drive native gameplay.",
    "controls": "Upstream resistance and switching state; protection failures commit after the solve.",
    "storage": "CEE backward-Euler history properties or PG native dynamic model, under the PG substep schedule.",
    "transformers": "Four-terminal coupling with winding resistance; CEE ratio is reciprocal to PG's ratio.",
    "meters": "Native sensing impedance; DC/RMS voltage, signed current and energy from solved history.",
    "electronics": "Native component models; CEE panel attachments become stable terminal and internal-node mappings.",
}
fixtures = {
    "electroenergetics:creative_battery": ["ceeSourceAndLoadUsePg", "acRmsIntoPgLoad", "ceePowersNonlinearBoardAndReconfiguration", "restartPreservesBothWireSystemsAndCapacitor", "mixedMotorsRespondToPowerAndPolarity", "ceeChargesPgPortableBattery"],
    "electroenergetics:creative_resistor": ["ceeSourceAndLoadUsePg", "pgToCeeWithPgWire", "pgToCeeWithCeeSpool"],
    "electroenergetics:capacitor": ["capacitorChargesOnceAndDischarges", "restartPreservesBothWireSystemsAndCapacitor"],
    "electroenergetics:inductor": ["inductorAdvancesOnceIntoPgResistor"],
    "electroenergetics:accumulator": ["accumulatorChargesFromPgAndPowersPgLoad"],
    "electroenergetics:bulb": ["pgPowersCeeLightHeatAndPump"],
    "electroenergetics:resistive_heater": ["pgPowersCeeLightHeatAndPump"],
    "electroenergetics:electric_pump": ["pgPowersCeeLightHeatAndPump"],
    "electroenergetics:fuse": ["ceeFuseTripsAndRepairRestoresPgLoad"],
    "electroenergetics:broken_fuse": ["ceeFuseTripsAndRepairRestoresPgLoad"],
    "electroenergetics:variac": ["ceeVariacControlsPgLoad"],
    "electroenergetics:transformer": ["transformerRatioChanges"],
    "electroenergetics:electrical_panel": ["panelControlsAndMonitorsPgFactory"],
    "powergrid:creative_voltage_source": ["pgToCeeWithPgWire", "pgToCeeWithCeeSpool", "mixedBranchesLoopCutReconnectAndRemoval", "pgPowersCeeLightHeatAndPump"],
    "powergrid:creative_resistor": ["ceeToPgWithPgWire", "ceeToPgWithCeeSpool", "mixedBranchesLoopCutReconnectAndRemoval", "restartPreservesBothWireSystemsAndCapacitor", "chunkReloadRebindsMixedEndpoints"],
    "powergrid:electric_motor": ["mixedMotorsRespondToPowerAndPolarity"],
    "powergrid:portable_battery": ["ceeChargesPgPortableBattery"],
    "powergrid:circuit_board": ["ceePowersNonlinearBoardAndReconfiguration"],
}
rows = []
for namespace, source in sources.items():
    package = source / "src/main/java" / packages[namespace]
    registry = (package / registries[namespace]).read_text(encoding="utf-8")
    registered = dict((ident, cls) for cls, ident in re.findall(r'BlockEntry<([^>]+)>\s+\w+\s*=\s*REGISTRATE\.block\("([^"]+)"', registry))
    jar_name = "electroenergetics-1.21.1-1.1.3.jar" if namespace == "electroenergetics" else "powergrid-mc1.21.1-0.6.2.jar"
    jar = args.jars / jar_name
    expected = next(a["sha256"] for a in lock["artifacts"] if a["role"] == ("cee" if namespace == "electroenergetics" else "pg"))
    if hashlib.sha256(jar.read_bytes()).hexdigest() != expected:
        raise SystemExit(f"Artifact checksum mismatch: {jar}")
    with zipfile.ZipFile(jar) as archive:
        state_ids = {Path(n).stem for n in archive.namelist() if n.startswith(f"assets/{namespace}/blockstates/") and n.endswith(".json")}
    for category, names in scope[namespace].items():
        for ident in names.split():
            if ident not in state_ids:
                raise SystemExit(f"Scoped block absent from pinned jar: {namespace}:{ident}")
            cls = registered.get(ident)
            candidates = list(package.rglob(f"{cls}.java")) if cls else []
            class_source = candidates[0].relative_to(source).as_posix() if candidates else registries[namespace]
            rows.append({"id": f"{namespace}:{ident}", "registry": "block", "category": category,
                         "model": models[category], "model_source": class_source,
                         "terminals": "Upstream named terminal IDs and geometry; assembly/configuration can change accessible terminals.",
                         "gameplay": circuits[category], "acceptance_circuit": circuits[category],
                         "status": "unverified", "fixtures": fixtures.get(f"{namespace}:{ident}", []),
                         "note": "Fixtures cover the named behaviors; complete gameplay and lifecycle acceptance is still required."})
    if namespace == "electroenergetics":
        # CEE's dyed/cased motors use a helper registration rather than a single BlockEntry.
        variants = sorted(f"{namespace}:{ident}" for ident in state_ids if "motor" in ident)
        if variants:
                rows.append({"id": f"{namespace}:white_electric_motor", "variants": variants, "registry": "block", "category": "loads", "behavior_group": "CEE motor",
                             "model": models["loads"], "terminals": "CEE motor terminal configuration.", "gameplay": "Motor shaft rotation and stress output.",
                             "acceptance_circuit": "PG source -> CEE motor, each native operating mode and casing behavior; stop and restart.",
                             "status": "unverified", "fixtures": ["mixedMotorsRespondToPowerAndPolarity"]})
        next(r for r in rows if r["id"] == "electroenergetics:electrical_panel")["variants"] = sorted(
                f"{namespace}:{ident}" for ident in state_ids if ident.endswith("electrical_panel"))

portable = next(r for r in rows if r["id"] == "powergrid:portable_battery")
portable["model"] = "PG native switched charging resistor; native item charge advances once per gameplay tick."
portable["gameplay"] = "Solved electrical input charges PG's portable energy item; its native placed block is not a circuit voltage source."
portable["acceptance_circuit"] = "CEE source -> PG portable battery; verify charging, saturation, power removal, saved charge and native item use."

panel_source = args.cee_source / "src/main/java/com/george_vi/electroenergetics/content/electrical_panel/attachments/CEEPanelAttachmentTypes.java"
for ident, factory, mode in re.findall(r'\.register\("([^"]+)".*?PanelAttachmentType\(([^,]+),.*?PanelAttachmentMode\.(\w+)\)', panel_source.read_text(), re.S):
    optional = ident in {"altitude_sensor", "velocity_sensor"}
    count = 0 if mode.endswith("NONE") else 6 if mode == "FULL_TRIPLE" else 4 if mode == "FULL_DOUBLE" else 2
    rows.append({"id": f"electroenergetics:{ident}", "registry": "panel_attachment", "category": "electronics",
                 "model": factory, "terminals": {"count": count, "identity": "Panel position, slot, and upstream stable terminal ID"},
                 "gameplay": "Native panel attachment interaction, sensing, indication or control.",
                 "acceptance_circuit": "Mixed source/load with attachment; operate, configure, remove, replace and reload alongside another attachment.",
                 "status": "unsupported" if optional else "unverified", "release_scope": not optional,
                 "fixtures": ["panelControlsAndMonitorsPgFactory"] if ident in {"cut_off_switch", "voltmeter", "indicator_bulb"} else []})

component_source = args.pg_source / "src/main/java/org/patryk3211/powergrid/circuits/components/Components.java"
for segment in component_source.read_text().split("public static final RegistryEntry")[1:]:
    match = re.search(r'REGISTRATE\.component\("([^"]+)"', segment)
    if not match:
        continue
    ident = match.group(1)
    pads = [int(n) for n in re.findall(r'\.addPad\w*\([^,]+,[^,]+,\s*(-?\d+)', segment)]
    # Single-pad overload omits a node index and uses pad zero.
    if ident == "via": pads = [0]
    rows.append({"id": f"powergrid:{ident}", "registry": "board_component", "category": "electronics",
                 "model": "PG native " + re.search(r'component\("[^"]+",\s*(\w+)::new', segment).group(1),
                 "terminals": {"pads": sorted(set(n for n in pads if n >= 0)), "identity": "Native placed component UUID and pad index"},
                 "gameplay": "Native circuit board component behavior, configuration and failure.",
                 "acceptance_circuit": "CEE source -> assembled PG board; verify component response and mixed factory control, then edit and reload.",
                 "status": "unverified", "release_scope": True,
                 "fixtures": ["ceePowersNonlinearBoardAndReconfiguration"] if ident in {"connector", "resistor", "diode"} else []})

matrix = {"schema": 1, "target": lock["target"], "upstream_versions": {"cee": "1.21.1-1.1.3", "pg": "0.6.2"},
          "status_definition": {"verified": "All declared electrical, gameplay and lifecycle acceptance checks passed on the packaged jar.",
                                "unverified": "In release scope; may have passing partial fixtures but is not yet certified for release.",
                                "unsupported": "Outside release scope; unknown electrical constructs are isolated with diagnostics."},
          "content": rows}
(root / "release/content-matrix.json").write_text(json.dumps(matrix, indent=2) + "\n", encoding="utf-8")
print(f"Inventoried {len(rows)} content entries. No partial fixture is promoted to verified release support.")
