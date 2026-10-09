"""Declare behavior fixtures without turning registry presence into certification."""
import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SUPPORT = {}

def group(namespace, names, *fixtures, registry="block"):
    for name in names.split():
        SUPPORT[f"{registry}/{namespace}:{name}"] = list(fixtures)

group("electroenergetics", "connector double_connector triple_connector quad_connector duplex_wire_termination", "stationaryConnectorFamiliesRetainMixedTerminalWiring")
group("powergrid", "wire_connector heavy_wire_connector ceiling_tile_connector", "stationaryConnectorFamiliesRetainMixedTerminalWiring")
group("powergrid", "cord_junction", "ceePowerCrossesPgCordJunction")
group("powergrid", "ceiling_tile_junction", "ceePowerCrossesPgCeilingCordJunction")
group("powergrid", "device_connector", "ceeExcitesPgWindingAndGeneratorPowersCeeLoad", "pgContactorUsesCeeCoilPowerAndBothContacts")
group("powergrid", "socket", "ceePoweredDesignerSavesAndPlacesViaLabelBoard")
group("electroenergetics", "ground_rod", "bothNativeGroundingSystemsSharePhysicalReference")
group("powergrid", "grounding_rod", "bothNativeGroundingSystemsSharePhysicalReference")
group("electroenergetics", "creative_battery", "ceeSourceAndLoadUsePg", "acRmsIntoPgLoad", "restartPreservesBothWireSystemsAndCapacitor")
group("powergrid", "creative_voltage_source", "pgToCeeWithPgWire", "pgToCeeWithCeeSpool")
group("powergrid", "creative_current_source", "pgCurrentSourceDrivesAndReversesCeeResistor")
group("electroenergetics", "alternator_rotor alternator_brushes stator", "assembledCeeAlternatorPowersPgLoad")
group("electroenergetics", "three_phase_alternator_brushes", "assembledThreePhaseGenerationPreservesPhaseAndDirection")
group("powergrid", "generator_induction_rotor generator_commutator generator_clutch generator_housing winding", "ceeExcitesPgWindingAndGeneratorPowersCeeLoad")
group("powergrid", "solar_panel", "nativePgSolarPowersCeeAndRespondsToShade")
group("electroenergetics", "bulb resistive_heater", "pgPowersCeeLightHeatAndPump")
group("electroenergetics", "electric_pump", "pgPowerPumpsWaterThroughCee")
group("powergrid", "electric_pump", "ceePowerPumpsWaterThroughPg")
group("electroenergetics", "resistor", "pgCurrentSourceDrivesAndReversesCeeResistor")
group("electroenergetics", "creative_resistor", "pgToCeeWithPgWire", "pgToCeeWithCeeSpool")
group("powergrid", "creative_resistor", "ceeToPgWithPgWire", "ceeToPgWithCeeSpool")
group("powergrid", "power_resistor", "ceeDrivesConfiguredPgPowerResistor")
group("electroenergetics", "indicator_bulb", "pgPowersBothCeeIndicatorChannels")
group("electroenergetics", "white_electric_motor", "mixedMotorsRespondToPowerAndPolarity", "ceeMotorRmsHistoryUsesPgSubsteps")
group("powergrid", "light_fixture factory_light factory_light_light ceiling_tile_lamp", "ceePowersPgNativeLightFixtures")
group("powergrid", "heating_coil basin_heater", "ceePowersPgNativeHeatingAndBasinState")
group("powergrid", "electric_motor", "mixedMotorsRespondToPowerAndPolarity")
group("powergrid", "constant_speed_motor", "ceePowersPgConstantSpeedMotorConfiguration")
group("powergrid", "servo", "ceeControlsPgServoAngleAndSavedState")
group("electroenergetics", "fuse broken_fuse", "ceeFuseTripsAndRepairRestoresPgLoad")
group("electroenergetics", "fuse_holder", "ceeHeldFuseNativeInsertionTripsMixedLoad")
group("electroenergetics", "cut_off_switch", "ceeCutoffSwitchControlsPgLoad")
group("electroenergetics", "double_switch", "ceeDoubleSwitchControlsBothPgLines")
group("electroenergetics", "redstone_relay", "ceeRedstoneRelayUsesRealWorldInput")
group("electroenergetics", "relay", "ceeRelayCoilControlsPgFactoryLoad")
group("electroenergetics", "momentary_switch", "ceeStandaloneMomentaryPulsesPgLoad")
group("electroenergetics", "emergency_stop_button", "ceeEmergencyStopAndPlayerReset")
group("electroenergetics", "potentiometer", "ceePotentiometerShaftAdjustsPgLoad")
group("electroenergetics", "redstone_potentiometer", "ceeRedstonePotentiometerAdjustsPgLoad")
group("electroenergetics", "high_voltage_switch", "ceeHvSwitchConnectorAndPlayerControl")
group("electroenergetics", "sulfur_hexafluoride_breaker", "ceeSf6BreakerUsesBothBlocksAndRedstone")
for block, fixture in {
    "lv_switch":"pgLvSwitchControlsCeeLoad", "mv_switch":"pgMvSwitchControlsCeeLoad",
    "lv_button":"pgButtonReleasesCeeLoad", "hv_switch":"pgHvSwitchMechanicalAssemblyControlsCeeLoad",
    "hv_breaker":"pgHvBreakerChargesClosesTripsAndRecovers", "contactor":"pgContactorUsesCeeCoilPowerAndBothContacts",
    "fuse_holder":"pgFuseTripsAndAcceptsSurvivalRepair", "rheostat":"pgRheostatShaftDividesCeePower",
}.items(): group("powergrid", block, fixture)
group("electroenergetics", "accumulator", "accumulatorChargesFromPgAndPowersPgLoad")
group("electroenergetics", "capacitor", "capacitorChargesOnceAndDischarges", "restartPreservesBothWireSystemsAndCapacitor")
group("electroenergetics", "high_voltage_capacitor", "highVoltageCapacitorChargesOnceAndDischarges")
group("electroenergetics", "inductor", "inductorAdvancesOnceIntoPgResistor")
group("powergrid", "portable_battery", "ceeChargesPgPortableBattery")
group("electroenergetics", "transformer", "transformerRatioChanges", "reactiveAcTransformerKeepsWindingCurrent")
group("electroenergetics", "variac", "ceeVariacControlsPgLoad")
group("electroenergetics", "redstone_variac", "ceeRedstoneVariacControlsMixedFactory")
group("powergrid", "transformer_core transformer_small", "assembledSmallTransformerStepUp", "assembledSmallTransformerStepDown")
group("powergrid", "transformer_medium", "assembledMediumTransformerStepUp", "assembledMediumTransformerStepDown")
group("powergrid", "variac", "pgVariacMechanicallyAdjustsCeeLoad")
group("electroenergetics", "ammeter voltmeter energy_meter", "standaloneCeeMetersReadPgLoadAndDisconnect")
group("electroenergetics", "tri_polar_energy_meter", "ceeStandaloneThreePoleMeterReadsPgLoads")
group("electroenergetics", "current_transformer", "ceeCurrentTransformerMapsInternalWindingNodes")
group("electroenergetics", "frequency_meter", "frequencyMeterUsesPgSubstepClock")
group("electroenergetics", "synchroscope", "ceeSynchroscopeReceivesMixedThreePhaseHistory")
group("powergrid", "voltage_gauge current_gauge power_gauge energy_meter", "pgGaugesAndEnergyMeterReadCeeSupply")
group("electroenergetics", "electrical_panel", "panelControlsAndMonitorsPgFactory", "restartRetainsPanelTerminalsBoardIdentityAndCharge")
group("electroenergetics", "diode", "cee113DiodeRectifiesNativePgPower")
group("powergrid", "circuit_board", "ceePowersNonlinearBoardAndReconfiguration", "restartRetainsPanelTerminalsBoardIdentityAndCharge")
group("powergrid", "circuit_design_table", "ceePoweredDesignerSavesAndPlacesViaLabelBoard")
group("powergrid", "modular_display", "ceePulsesPgModularDisplayWithNativeModules")

for names, fixture in [
    ("cut_off_switch voltmeter indicator_bulb", "panelControlsAndMonitorsPgFactory"),
    ("ammeter energy_meter", "panelEnergyMeterAndAmmeterReadMixedLoad"),
    ("tri_polar_energy_meter", "panelTriPolarMeterUsesAssignedTerminals"),
    ("emergency_stop_button momentary_switch", "panelPlayerStopAndMomentaryControl"),
    ("miniature_circuit_breaker", "panelBreakerTripsAndPlayerResets"),
    ("analog_lever steering_wheel", "analogPanelControlsRetainNativeInteractionAndLinkState"),
]: group("electroenergetics", names, fixture, "restartRetainsPanelTerminalsBoardIdentityAndCharge", registry="panel_attachment")

for names, fixture in [
    ("connector resistor diode", "ceePowersNonlinearBoardAndReconfiguration"),
    ("via label", "ceePoweredDesignerSavesAndPlacesViaLabelBoard"),
    ("voltage_gauge", "boardVoltageGaugeReadsCeePower"), ("current_gauge", "boardCurrentGaugeReadsCeePower"),
    ("switch", "boardSwitchControlsCeePower"), ("button", "boardButtonReleasesCeePower"),
    ("redstone_relay", "boardRedstoneRelaySwitchesCeePower"), ("relay", "boardRelayTransfersMixedLoad"),
    ("relay_dpdt", "boardDoubleRelayTransfersMixedLoads"), ("potentiometer", "boardPotentiometerDividesCeePower"),
    ("capacitor", "boardCapacitorChargesAndDischargesFromCee"), ("inductor", "boardInductorBuildsAndReleasesCeeCurrent"),
    ("display_module", "boardDisplayCountsCeePulses"), ("barretter_tube", "boardBarretterRegulatesCeeCurrent"),
    ("varistor", "boardVaristorClampsCeeSupply"), ("neon_bulb", "boardNeonStrikesAndExtinguishesFromCee"),
    ("regulator_tube", "boardRegulatorTubeStrikesAndExtinguishesFromCee"), ("light_bulb", "boardLightBulbHeatsAndCoolsFromCee"),
    ("fuse_holder", "boardFuseTripsAndPlayerRepairsCeePower"), ("bjt_npn", "boardNpnAmplifiesCeeBaseCurrent"),
    ("bjt_pnp", "boardPnpAmplifiesCeeBaseCurrent"), ("vfet", "boardVfetRespondsToCeeGateBias"),
    ("triode", "boardTriodeHeaterAndGridUseCeePower"), ("pentode", "boardPentodeHeaterAndGridUseCeePower"),
    ("thyratron", "boardThyratronHeaterTriggerAndExtinctionUseCee"),
]: group("powergrid", names, fixture, "everyBuiltinBoardComponentAssemblesFromNativeItems", registry="board_component")

LIFECYCLE = ["mixedBranchesLoopCutReconnectAndRemoval", "chunkReloadRebindsMixedEndpoints",
             "restartPreservesBothWireSystemsAndCapacitor", "restartRetainsPanelTerminalsBoardIdentityAndCharge",
             "identicalCoordinatesKeepSeparateDimensionTopology", "ceeWireOverloadBreaksAndRepairRejoinsPgCircuit"]

def apply(matrix, verified=False):
    methods = {}
    for file in (ROOT / "src/main/java/org/cha0scollective/wwpg/gametest").glob("*.java"):
        for method in re.findall(r'public static void (\w+)\(GameTestHelper', file.read_text()):
            methods[method] = file.relative_to(ROOT).as_posix()
    for row in matrix["content"]:
        fixtures = SUPPORT.get(f"{row['registry']}/{row['id']}")
        if row["status"] == "unsupported": continue
        row["release_scope"] = bool(fixtures)
        row["fixtures"] = fixtures or []
        row["status"] = "verified" if fixtures and verified else "unverified"
        row["note"] = ("Declared behavior certified by the named electrical/gameplay fixtures and shared lifecycle suite. "
                       "Cosmetic variants share the native model; other configurations are not separately certified."
                       if fixtures and verified else "Declared support awaiting the final release checks." if fixtures
                       else "Additional upstream content outside the declared beta support set; functionality is not certified.")
        if fixtures:
            row["lifecycle_fixtures"] = LIFECYCLE
            row["fixture_sources"] = {fixture: methods[fixture] for fixture in fixtures + LIFECYCLE}
    matrix["schema"] = 2
    matrix["status_definition"]["unverified"] = "Additional unclaimed content, or declared behavior awaiting release acceptance; see release_scope."
    matrix["lifecycle_policy"] = "Shared topology, persistence and synchronization fixtures cover mappings; behavior fixtures additionally check native edits/NBT/removal. No scale or moving-system certification."
    matrix["verification_record"] = "release/verification.json"
    return matrix

if __name__ == "__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--freeze",action="store_true");args=parser.parse_args()
    if args.freeze:
        report=json.loads((ROOT/"release/verification.json").read_text())
        if not report.get("all_release_gates_passed"):
            raise SystemExit("Release gates must pass before freezing support.")
    path=ROOT/"release/content-matrix.json"
    matrix=apply(json.loads(path.read_text()),args.freeze)
    path.write_text(json.dumps(matrix,indent=2)+"\n",newline="\n")
    print(f"Declared {sum(bool(r.get('release_scope')) for r in matrix['content'])} of {len(matrix['content'])} inventoried behaviors.")
