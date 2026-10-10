"""Reconcile actual pinned registries; never certify content from registry presence."""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("registry", type=Path)
args = parser.parse_args()
snapshot = json.loads(args.registry.read_text())
baseline = json.loads((ROOT / "release/content-matrix.json").read_text())

DEFERRED = {
    "electroenergetics:pantograph": "Moving train current collection",
    "electroenergetics:rail_contact_shoe": "Moving train current collection",
    "powergrid:solar_panel_bearing": "Moving solar contraption",
    "electroenergetics:altitude_sensor": "Optional moving-system panel attachment",
    "electroenergetics:velocity_sensor": "Optional moving-system panel attachment",
    "electroenergetics:bunting": "Optional Supplementaries wire decoration; outside BASE and PINOUT profiles",
    "powergrid:electrozapper": "Specialized handheld equipment; native battery energy remains supported",
    "powergrid:electrobaton": "Specialized handheld equipment; native battery energy remains supported",
    "powergrid:portable_drill": "Specialized handheld equipment; native battery energy remains supported",
    "powergrid:portable_saw": "Specialized handheld equipment; native battery energy remains supported",
    "powergrid:integrated_circuit": "Handheld equipment upgrade",
    "powergrid:debug": "Upstream developer utility",
}
DECORATIVE = set("electroenergetics:electric_shock_sign electroenergetics:grounding_sign electroenergetics:high_voltage_sign electroenergetics:magnet powergrid:ceiling_tile powergrid:conductive_casing powergrid:copper_plating powergrid:copper_plating_slab powergrid:copper_plating_stairs".split())
MATERIAL_BLOCKS = set("electroenergetics:plant_oil electroenergetics:transformer_oil powergrid:acid".split())
ADDITIONS = {
    "electroenergetics:buzzer": ("loads", "pgPowerSoundsCeeBuzzer"),
    "electroenergetics:converter": ("conversion", "nativeFeConvertersExchangeMixedPower"),
    "electroenergetics:catenary_holder": ("wiring", "nativeCatenarySpoolStylesAndRecoveryCarryMixedPower"),
    "powergrid:battery": ("storage", "stationaryBatteriesChargeAndDischargeWithCee"),
    "powergrid:potato_battery": ("storage", "potatoBatteryPreservesNativeNonRechargeableBehavior"),
    "powergrid:alarm_bell": ("loads", "ceePowerRunsPgFanMagnetAndBell"),
    "powergrid:electric_fan": ("loads", "ceePowerRunsPgFanMagnetAndBell"),
    "powergrid:electromagnet": ("loads", "ceePowerRunsPgFanMagnetAndBell"),
    "powergrid:carbon_pile": ("controls", "ceeControlsNativeCarbonPileResistance"),
    "powergrid:carbon_pile_coil": ("controls", "ceeControlsNativeCarbonPileResistance"),
    "powergrid:crt": ("instruments", "mixedPowerControlsNativeCrt"),
    "powergrid:punch_card_reader": ("controls", "nativePunchCardScansEveryRowAndControlsEightMixedLoads"),
    "powergrid:redstone_converter": ("controls", "nativeRedstoneConverterControlsCeeLoad"),
    "powergrid:fe_inverter": ("conversion", "nativeFeConvertersExchangeMixedPower"),
    "powergrid:spark_gap": ("protection", "mixedVoltageTriggersAndExtinguishesNativeSparkGap"),
    "powergrid:thermometer": ("instruments", "pgThermometerReadsCeePoweredPgHeat"),
    "pinout:pinout": ("computer_control", "actualLuaControlsAllEightMixedLoads"),
}
REMAINING = {
    "electroenergetics:insulator": "nativePoleMountAndInsulatorControlMountedMixedBreaker",
    "electroenergetics:concrete_pole": "nativeThreeSegmentPoleCarriesMixedPower",
    "electroenergetics:pole_mount": "nativePoleMountAndInsulatorControlMountedMixedBreaker",
    "electroenergetics:radiator_panel": "nativeCeeCoreAssemblyTransformsMixedPowerAndUsesRadiators",
    "electroenergetics:transformer_core": "nativeCeeCoreAssemblyTransformsMixedPowerAndUsesRadiators",
    "electroenergetics:voltage_regulator": "nativeRegulatorStartsAtNeutralAndRegulatesMixedLoad",
    "electroenergetics:broken_bulb": "nativeBrokenBulbRepairRestoresMixedPowerWithoutRewiring",
    "powergrid:generator_large_induction_rotor": "nativeLargeGeneratorUsesInstalledCoilAndMixedExcitation",
    "powergrid:generator_vertical_commutator": "nativeVerticalGeneratorUsesInstalledCoilAndMixedExcitation",
    "powergrid:vertical_generator_housing": "nativeVerticalHousingJoinsAndSeparatesExcitedWindings",
    "powergrid:ceiling_tile_solar": "nativeCeilingSolarAssemblyPowersCeeAndRespondsToShade",
    "powergrid:string_light_block": "nativeColoredStringCordLightsMixedCircuitAndReturnsCuttingCost",
    "powergrid:nether_transformer": "linkedNetherEndsSurviveColdRestartAndFiveIndependentActualChunkCycles",
    "powergrid:plotter": "nativePlotterRecordsMixedWaveform",
}
TOOLS = {
    "electroenergetics:clamp_meter": "handheldMetersReadBothWireSystems",
    "powergrid:multimeter": "handheldMetersReadBothWireSystems",
    "electroenergetics:linemans_stick": "nativeWireAttachmentsInstallRemoveAndPersist",
    "electroenergetics:wire_damper": "nativeWireAttachmentsInstallRemoveAndPersist",
    "powergrid:wire_cutter": "pgWireTypesRetainCostResistanceAndPlayerCut",
    "powergrid:circuit_schematic": "savedDesignerLoadsBeforeWorldAssignment",
    "powergrid:punch_card": "nativePunchCardScansEveryRowAndControlsEightMixedLoads",
    "powergrid:growth_lamp": "ceePowerRunsNativeGrowthLampAndCropEffect",
    "powergrid:light_bulb": "nativePgBulbItemsRetainMixedLightAndReplacement",
    "powergrid:lv_light_bulb": "nativePgBulbItemsRetainMixedLightAndReplacement",
    "powergrid:portable_battery": "ceeChargesPgPortableBattery",
    "powergrid:display_module": "ceePulsesPgModularDisplayWithNativeModules",
    "powergrid:copper_coil": "nativeLargeGeneratorUsesInstalledCoilAndMixedExcitation",
    "powergrid:incomplete_circuit": "everyBuiltinBoardComponentAssemblesFromNativeItems",
    "powergrid:string_light_cord": "nativeColoredStringCordLightsMixedCircuitAndReturnsCuttingCost",
}

matrix = json.loads(json.dumps(baseline))
matrix["target"] = "0.1.0-beta.5"
matrix["verification_record"] = "release/0.1.0-beta.5/verification.json"
matrix["baseline"] = {"version": "0.1.0-beta.4", "commit": "28da7ffca1f6972dc0db2378aa425c89753cab85"}
known = {}
for row in matrix["content"]:
    known[(row["registry"], row["id"])] = row
    for variant in row.get("variants", []): known[(row["registry"], variant)] = row
    if row["status"] != "unsupported":
        row["baseline_status"] = row["status"]
        row["status"] = "unverified"
        row["release_scope"] = True
        row["note"] = "Awaiting stationary completion acceptance; prior-release evidence is retained separately."
        if row["id"] in REMAINING: row["fixtures"] = [REMAINING[row["id"]]]
        if row["id"] == "powergrid:circuit_design_table":
            row["fixtures"].append("savedDesignerLoadsBeforeWorldAssignment")
            row["fixtures"].append("designerInventoryChangesScheduleAWorldSave")
            row["fixtures"].append("savedDesignReopensCopiesAssemblesAndPowersAfterRestart")

for entry in snapshot["entries"]:
    key = (entry["registry"], entry["id"])
    if entry["registry"] == "block" and entry["id"] in ADDITIONS:
        category, fixture = ADDITIONS[entry["id"]]
        row = dict(entry, category=category, status="unverified", release_scope=True,
                   fixtures=[fixture], acceptance_circuit="Mixed native workflow, solved output, removal and restoration.",
                   profile="PINOUT" if entry["id"].startswith("pinout:") else "BASE")
        if entry["id"] == "powergrid:crt":
            row["variants"] = ["powergrid:crt", "powergrid:andesite_encased_crt", "powergrid:brass_encased_crt"]
            for variant in row["variants"]: known[("block", variant)] = row
            row["fixtures"] += ["mixedPowerControlsAndesiteCrt", "mixedPowerControlsBrassCrt"]
        matrix["content"].append(row); known[key] = row

audit = []
for entry in snapshot["entries"]:
    row = dict(entry)
    key = (entry["registry"], entry["id"])
    ident = entry["id"]
    if ident in DEFERRED:
        row.update(disposition="deferred", reason=DEFERRED[ident])
    elif key in known:
        row.update(disposition="functional_behavior", behavior=known[key]["id"])
    elif ident in DECORATIVE:
        row.update(disposition="native_placement", reason="Decoration/casing; no electrical terminals added")
    elif ident in MATERIAL_BLOCKS or entry["implementation"].endswith("BucketItem"):
        row.update(disposition="assembly_material", reason="Native fluid/material and container workflow")
    elif entry["registry"] == "item" and "block" in entry:
        parent = known.get(("block", entry["block"]))
        if parent: row.update(disposition="functional_item", behavior=parent["id"])
        elif entry["block"] in DEFERRED: row.update(disposition="deferred", reason=DEFERRED[entry["block"]])
        elif entry["block"] in DECORATIVE: row.update(disposition="native_placement", reason="Native decorative block item")
        else: raise SystemExit(f"Unaccounted block item: {entry}")
    elif entry["registry"] == "item" and ident in TOOLS:
        row.update(disposition="native_workflow", fixture=TOOLS[ident])
    elif entry["registry"] == "wire_type" or entry["implementation"].endswith(("WireItem", "CordItem", "WireSpoolItem", "BundledWireItem", "EmptySpoolItem")):
        row.update(disposition="stationary_wiring", fixture="allStationaryWireFamiliesRetainNativeRules")
    elif entry["registry"] == "wire_attachment" and ident.endswith(":empty"):
        row.update(disposition="empty_registry_sentinel", reason="Empty wire attachment; no native installation item")
    elif entry["registry"] == "wire_attachment":
        row.update(disposition="native_workflow", fixture="nativeWireAttachmentsInstallRemoveAndPersist")
    elif entry["registry"] == "panel_attachment" and ident.endswith(":empty"):
        row.update(disposition="empty_registry_sentinel", reason="Empty panel slot")
    elif entry["registry"] == "item" and entry["implementation"] in ("net.minecraft.world.item.Item", "com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem"):
        row.update(disposition="crafting_or_assembly_item", reason="Native recipe/assembly item; no standalone electrical model")
    elif entry["registry"] == "item" and entry["implementation"].endswith("MCBItem"):
        row.update(disposition="functional_item", behavior="electroenergetics:miniature_circuit_breaker")
    else:
        raise SystemExit(f"Unaccounted registry entry: {entry}")
    audit.append(row)

destination = ROOT / "release/0.1.0-beta.5"
destination.mkdir(parents=True, exist_ok=True)
(destination / "registry-inventory.json").write_text(json.dumps(dict(schema=1, profile=snapshot["profile"],
    entries=audit, note="Accounting only. Registry presence does not establish functional acceptance."), indent=2) + "\n", newline="\n")
(destination / "content-matrix.json").write_text(json.dumps(matrix, indent=2) + "\n", newline="\n")
print(f"Accounted for {len(audit)} actual registry entries; {sum(r.get('release_scope', False) for r in matrix['content'])} planned behavior groups.")
