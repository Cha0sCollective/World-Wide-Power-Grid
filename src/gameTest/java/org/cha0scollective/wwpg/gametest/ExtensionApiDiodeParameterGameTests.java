package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.api.*;
import org.cha0scollective.wwpg.api.neoforge.ElectricalDevices;
import org.cha0scollective.wwpg.api.neoforge.ElectricalMeasurements;
import org.cha0scollective.wwpg.api.neoforge.ElectricalProviders;
import org.cha0scollective.wwpg.extension.ApiCircuits;
import org.cha0scollective.wwpg.extension.ApiTerminals;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.sim.special.PNJunctionWire;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Exercises live PN parameters through the real selected PG backend and both wire systems. */
@GameTestHolder("wwpg_api")
@PrefixGameTestTemplate(false)
public final class ExtensionApiDiodeParameterGameTests {
    private static final String PROVIDER = "wwpg:diode_parameter_fixture";
    private static final double SUPPLY = .65;
    private static final Map<ServerLevel, Map<BlockPos, State>> STATES = new WeakHashMap<>();
    private static final List<Terminal> TERMINALS = List.of(
            new Terminal("positive", 0, new LocalPoint(.25, .5, 0), .12),
            new Terminal("negative", 1, new LocalPoint(.75, .5, 0), .12));

    /** Installed only by the packaged acceptance profile; ordinary iron blocks expose no device. */
    public static void register(RegisterCapabilitiesEvent event) {
        ElectricalProviders.registerBlock(event, PROVIDER, (level, pos, block, entity, context) -> {
            var state = level instanceof ServerLevel server ? STATES.getOrDefault(server, Map.of()).get(pos) : null;
            return state == null ? null : new Probe(state);
        }, Blocks.IRON_BLOCK);
    }

    private static final class State {
        final ServerLevel level;
        final BlockPos pos;
        DeviceResults latest;
        Object diodeIdentity, storageIdentity;
        boolean monitoring, identityChanged;
        int acceptedUpdates, rejectedUpdates;
        final int[] phases = new int[4];
        double expectedStorage, maximumHistoryError, maximumCurrentError;
        State(ServerLevel level, BlockPos pos) { this.level = level; this.pos = pos.immutable(); }
    }

    private record Probe(State state) implements ElectricalDevice {
        @Override public String providerId() { return PROVIDER; }
        @Override public List<Terminal> terminals() { return TERMINALS; }
        @Override public CircuitDescription prepare(TickContext context) {
            int phase = phase(context);
            return new CircuitDescription(TERMINALS, List.of(new InternalNode("stored")), List.of(
                    new Element("supply", "positive", "negative", new ElectricalModel.VoltageSource(SUPPLY)),
                    new Element("diode", "positive", "negative", new ElectricalModel.Diode(saturation(phase), resistance(phase), 22, 1)),
                    new Element("ballast", "positive", "stored", new ElectricalModel.Resistor(100)),
                    // This creation-only value deliberately stays zero even while the retained capacitor charges.
                    new Element("storage", "stored", "negative", new ElectricalModel.Capacitor(.01, 0))), Set.of("negative"));
        }
        @Override public void commit(DeviceResults results) {
            state.latest = results;
            if (!state.monitoring) return;
            if (results.status() != MeasurementStatus.VALID) { ++state.rejectedUpdates; return; }
            ++state.acceptedUpdates;
            int phase = phase(results.context()); ++state.phases[phase];
            var circuits = ApiCircuits.get(state.level);
            state.identityChanged |= state.diodeIdentity != circuits.elementIdentity(state.pos, "diode")
                    || state.storageIdentity != circuits.elementIdentity(state.pos, "storage");
            var diode = results.element("diode").orElseThrow();
            // Pinned PG's native silicon PN model uses the Banwell/Jayakumar
            // Wright-omega approximation at 22 C, including the series resistance.
            double thermal = 1.380649e-23 * (22 + 273.15) / 1.602176634e-19;
            double is = saturation(phase), rs = resistance(phase), isRs = is * rs;
            double expectedCurrent = thermal * PNJunctionWire.WrightOmega4(Math.log(isRs / thermal)
                    + (isRs + diode.lastVoltageVolts()) / thermal) / rs - is;
            state.maximumCurrentError = Math.max(state.maximumCurrentError,
                    Math.abs(diode.lastCurrentAmps() - expectedCurrent));
            // One RC advancement per accepted substep, regardless of PN iterations or parameter edits.
            state.expectedStorage = SUPPLY + (state.expectedStorage - SUPPLY)
                    * Math.pow(1 / (1 + results.context().substepSeconds()), results.context().substeps());
            double stored = ((ElementState.Capacitor) results.state("storage").orElseThrow()).voltageVolts();
            state.maximumHistoryError = Math.max(state.maximumHistoryError, Math.abs(stored - state.expectedStorage));
        }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void liveDiodeParametersRetainObjectsAndAcceptedStorageHistory(GameTestHelper helper) {
        var relative = new BlockPos(1, 2, 1);
        var ceeRelative = new BlockPos(4, 2, 1);
        var pgRelative = new BlockPos(6, 2, 1);
        var level = helper.getLevel();
        var pos = helper.absolutePos(relative);
        var cee = helper.absolutePos(ceeRelative);
        var pg = helper.absolutePos(pgRelative);
        for (var at : new BlockPos[]{relative, ceeRelative, pgRelative}) helper.setBlock(at.below(), Blocks.STONE);
        var state = new State(level, pos);
        STATES.computeIfAbsent(level, ignored -> new HashMap<>()).put(pos, state);
        helper.setBlock(relative, Blocks.IRON_BLOCK);
        helper.setBlock(ceeRelative, CEEBlocks.RESISTOR.get());
        helper.setBlock(pgRelative, ModdedBlocks.CREATIVE_RESISTOR.get());
        helper.assertTrue(ElectricalDevices.changed(level, pos), "Test-only plain-block provider could not be nominated");
        helper.runAtTickTime(5, () -> {
            DevicesSavedData.load(level).getDevice(cee, ResistorDevice.class).properties = ElectricalProperties.resistor(100);
            ((ResistorBlockEntity) level.getBlockEntity(pg)).setValue(100);
            WiringGameTests.connect(helper, pos, 0, cee, 0, true);
            WiringGameTests.connect(helper, pos, 1, cee, 1, false);
            WiringGameTests.connect(helper, pos, 0, pg, 0, false);
            WiringGameTests.connect(helper, pos, 1, pg, 1, true);
        });
        helper.runAtTickTime(20, () -> {
            var result = accepted(helper, pos);
            state.diodeIdentity = ApiCircuits.get(level).elementIdentity(pos, "diode");
            state.storageIdentity = ApiCircuits.get(level).elementIdentity(pos, "storage");
            helper.assertTrue(state.diodeIdentity != null && state.storageIdentity != null, "Fixture elements were not created");
            state.expectedStorage = ((ElementState.Capacitor) result.state("storage").orElseThrow()).voltageVolts();
            helper.assertTrue(state.expectedStorage > .05 && state.expectedStorage < SUPPLY,
                    "RC branch lacks measurable retained history: " + state.expectedStorage);
            state.monitoring = true;
        });
        helper.runAtTickTime(50, () -> {
            accepted(helper, pos);
            helper.assertTrue(state.acceptedUpdates >= 25 && state.rejectedUpdates == 0,
                    "Changing Is/Rs restarted warmup or rejected frames: accepted=" + state.acceptedUpdates + ", rejected=" + state.rejectedUpdates);
            helper.assertTrue(!state.identityChanged, "Same-ID diode or its neighboring storage element was recreated");
            for (int phase = 0; phase < 4; ++phase)
                helper.assertTrue(state.phases[phase] >= 5, "A distinct Is/Rs parameter combination was not accepted: " + phase);
            helper.assertTrue(Double.isFinite(state.maximumCurrentError) && state.maximumCurrentError < 1e-6,
                    "Live Is/Rs were ignored or differ from PG's native PN equation: " + state.maximumCurrentError);
            helper.assertTrue(Double.isFinite(state.maximumHistoryError) && state.maximumHistoryError < 1e-7,
                    "Parameter edits or PN iterations reset/duplicated RC history: " + state.maximumHistoryError);
            for (var load : new BlockPos[]{cee, pg}) {
                var volts = ElectricalMeasurements.readVoltageBetween(level, load, 0, load, 1);
                helper.assertTrue(volts.status() == MeasurementStatus.VALID
                                && Math.abs(volts.voltage().orElseThrow().lastVoltageVolts() - SUPPLY) < .001,
                        "Native mixed load did not retain the actual source voltage: " + volts.status());
            }
            state.monitoring = false;
            // Plain blocks have no BlockEntity anchor. Physical replacement with
            // the same block/provider/slot must still invalidate the old frame immediately.
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            STATES.get(level).put(pos, new State(level, pos));
            level.setBlockAndUpdate(pos, Blocks.IRON_BLOCK.defaultBlockState());
            helper.assertTrue(ApiTerminals.pendingRemoval(level, pos), "Same plain-block owner removal was not recorded");
            var removed = ElectricalMeasurements.read(level, pos);
            var terminal = ElectricalMeasurements.readTerminal(level, pos, 0);
            helper.assertTrue(removed.status() == MeasurementStatus.NOT_READY && removed.elements().isEmpty(),
                    "Identical plain-block replacement exposed the previous device frame");
            helper.assertTrue(terminal.status() == MeasurementStatus.NOT_READY && terminal.voltage().isEmpty(),
                    "Identical plain-block replacement exposed the previous terminal voltage");
            helper.assertTrue(ElectricalDevices.changed(level, pos), "Fresh plain-block replacement could not be nominated");
        });
        helper.runAtTickTime(65, () -> {
            var result = accepted(helper, pos);
            helper.assertTrue(!ApiTerminals.pendingRemoval(level, pos), "Plain-block replacement did not retire safely");
            helper.assertTrue(ApiCircuits.get(level).elementIdentity(pos, "diode") != state.diodeIdentity,
                    "A physical replacement reused the old diode object");
            double stored = ((ElementState.Capacitor) result.state("storage").orElseThrow()).voltageVolts();
            helper.assertTrue(stored > .05 && stored < .4, "Fresh replacement inherited old RC history: " + stored);
            DynamicGameTests.audit(helper);
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info(
                    "API_DIODE_PARAMETERS_PASSED: stableObjects=true; acceptedUpdates={}; nativePNParameters=true; storageHistory=true; plainBlockReplacement=true",
                    state.acceptedUpdates);
            for (var at : new BlockPos[]{pos, cee, pg}) level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            STATES.get(level).remove(pos);
            helper.succeed();
        });
    }

    private static int phase(TickContext context) { return (int) (context.gameTime() & 3); }
    private static double saturation(int phase) { return phase == 1 || phase == 2 ? 1e-6 : 1e-9; }
    private static double resistance(int phase) { return phase >= 2 ? 20 : 1; }
    private static DeviceResults accepted(GameTestHelper helper, BlockPos pos) {
        var result = ElectricalMeasurements.read(helper.getLevel(), pos);
        helper.assertTrue(result.status() == MeasurementStatus.VALID, "Dynamic diode fixture is " + result.status() + ": " + result.detail());
        return result;
    }
}
