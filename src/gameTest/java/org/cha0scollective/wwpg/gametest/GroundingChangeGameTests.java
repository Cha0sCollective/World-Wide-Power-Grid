package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.WorldWidePowerGrid;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.sim.ElectricalNetwork;
import org.patryk3211.powergrid.electricity.sim.node.OwnedFloatingNode;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Change only grounding: unrelated node replacement must not conceal a stale matrix. */
@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class GroundingChangeGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void physicalGroundChangesKeepExistingTopology(GameTestHelper h) {
        var fixture = new GroundFixture(h);
        double[] conductance = {1, 1};
        Consumer<AddToElectricGraphEvent> listener = event -> {
            if (event.level != h.getLevel()) return;
            event.builder.ground(new InWorldNode(0, fixture.source), conductance[0]);
            event.builder.ground(new InWorldNode(1, fixture.source), conductance[1]);
        };
        fixture.listen(listener);
        fixture.at(5, fixture::wire);
        fixture.at(20, () -> { fixture.expect(5, -5); fixture.capture(); conductance[0] = 0; });
        fixture.at(30, () -> { fixture.expect(10, 0); fixture.unchanged(); conductance[0] = 1; });
        fixture.at(40, () -> { fixture.expect(5, -5); fixture.unchanged(); conductance[0] = 3; });
        fixture.at(50, () -> { fixture.expect(2.5, -7.5); fixture.unchanged(); conductance[0] = 0; });
        fixture.at(60, () -> { fixture.expect(10, 0); fixture.unchanged(); fixture.finish(); });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void preferredReferenceChangesAndPhysicalGroundTakeover(GameTestHelper h) {
        var fixture = new GroundFixture(h);
        int[] preferred = {1};
        boolean[] physical = {false};
        Consumer<AddToElectricGraphEvent> listener = event -> {
            if (event.level != h.getLevel()) return;
            event.builder.defaultZeroPotential(new InWorldNode(preferred[0], fixture.source), 1000);
            if (physical[0]) event.builder.ground(new InWorldNode(1, fixture.source), 1);
        };
        fixture.listen(listener);
        fixture.at(5, fixture::wire);
        fixture.at(20, () -> { fixture.expect(10, 0); fixture.capture(); preferred[0] = 0; });
        fixture.at(30, () -> { fixture.expect(0, -10); fixture.unchanged(); physical[0] = true; });
        fixture.at(40, () -> { fixture.expect(10, 0); fixture.unchanged(); physical[0] = false; });
        fixture.at(50, () -> { fixture.expect(0, -10); fixture.unchanged(); preferred[0] = 1; });
        fixture.at(60, () -> { fixture.expect(10, 0); fixture.unchanged(); fixture.finish(); });
    }

    @GameTest(template = "empty", timeoutTicks = 1100)
    public static void groundRodChunkUnloadReloadUpdatesExistingCircuit(GameTestHelper h) {
        var source = new BlockPos(2562, 64, 2562);
        var load = source.offset(3, 0, 0);
        var returnRod = source.offset(0, 0, 4);
        // Beyond the source's generation halo, with two ordinary wires below 128 m.
        var connector = source.offset(112, 0, 0);
        var remoteRod = source.offset(224, 0, 0);
        var sourceChunk = new ChunkPos(source);
        var remoteChunk = new ChunkPos(remoteRod);
        var level = h.getLevel();
        var unloads = new AtomicInteger();
        Consumer<ChunkEvent.Unload> listener = event -> {
            if (event.getLevel() == level && event.getChunk().getPos().equals(remoteChunk)) unloads.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, listener);
        level.setChunkForced(sourceChunk.x, sourceChunk.z, true);
        level.setChunkForced(remoteChunk.x, remoteChunk.z, true);
        level.getChunk(sourceChunk.x, sourceChunk.z);
        level.getChunk(remoteChunk.x, remoteChunk.z);
        level.getChunk(connector.getX() >> 4, connector.getZ() >> 4);
        Runnable cleanup = () -> {
            NeoForge.EVENT_BUS.unregister(listener);
            level.setChunkForced(remoteChunk.x, remoteChunk.z, false);
            level.setChunkForced(sourceChunk.x, sourceChunk.z, false);
        };
        int[] stage = {0}, cycles = {0};
        long[] since = {0};
        double[] grounded = new double[2];
        OwnedFloatingNode[] endpoints = new OwnedFloatingNode[2];
        Object[] stamps = new Object[3];
        ElectricalNetwork[] network = new ElectricalNetwork[1];
        h.onEachTick(() -> {
            try {
                long tick = h.getTick(), elapsed = tick - since[0];
                switch (stage[0]) {
                    case 0 -> {
                        h.assertTrue(tick < 80, "Grounding fixture chunks did not become entity-ready");
                        if (!level.areEntitiesLoaded(sourceChunk.toLong()) || !level.areEntitiesLoaded(remoteChunk.toLong())) return;
                        for (var pos : new BlockPos[]{source, load, returnRod, connector, remoteRod}) {
                            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                        }
                        level.setBlockAndUpdate(source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
                        level.setBlockAndUpdate(load, CEEBlocks.CREATIVE_RESISTOR.getDefaultState());
                        level.setBlockAndUpdate(returnRod, CEEBlocks.GROUND_ROD.getDefaultState());
                        level.setBlockAndUpdate(connector, CEEBlocks.CONNECTOR.getDefaultState());
                        level.setBlockAndUpdate(remoteRod, CEEBlocks.GROUND_ROD.getDefaultState());
                        stage[0] = 1; since[0] = tick;
                    }
                    case 1 -> {
                        if (elapsed < 5) return;
                        ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
                        DevicesSavedData.load(level).getDevice(load, ResistorDevice.class).properties = ElectricalProperties.resistor(100);
                        WiringGameTests.connect(h, source, 0, load, 0, true);
                        WiringGameTests.connect(h, source, 1, load, 1, false);
                        WiringGameTests.connect(h, source, 0, connector, 0, false);
                        WiringGameTests.connect(h, connector, 0, remoteRod, 0, false);
                        WiringGameTests.connect(h, source, 1, returnRod, 0, false);
                        stage[0] = 2; since[0] = tick;
                    }
                    case 2 -> {
                        if (elapsed < 15) return;
                        endpoints[0] = endpoint(level, source, 0); endpoints[1] = endpoint(level, source, 1);
                        network[0] = endpoints[0].getNetwork();
                        grounded[0] = endpoints[0].getVoltage(); grounded[1] = endpoints[1].getVoltage();
                        near(h, grounded[0] - grounded[1], 10, "Two-ground source voltage");
                        double positiveResistance = 1 + wireResistance(level, source, 0, connector, 0)
                                + wireResistance(level, connector, 0, remoteRod, 0);
                        double negativeResistance = 1 + wireResistance(level, source, 1, returnRod, 0);
                        double voltage = grounded[0] - grounded[1];
                        near(h, grounded[0], voltage * positiveResistance / (positiveResistance + negativeResistance), "Initial positive earth return including wire resistance");
                        near(h, grounded[1], -voltage * negativeResistance / (positiveResistance + negativeResistance), "Initial negative earth return including wire resistance");
                        stamps[0] = branch(level, source, 0, connector, 0);
                        stamps[1] = branch(level, load, 0, load, 1);
                        stamps[2] = branch(level, connector, 0, remoteRod, 0);
                        h.assertTrue(stamps[0] != null && stamps[1] != null && stamps[2] != null, "Grounding fixture branches missing");
                        level.getDataStorage().save(); level.getChunkSource().save(true);
                        level.setChunkForced(remoteChunk.x, remoteChunk.z, false);
                        stage[0] = 3; since[0] = tick;
                    }
                    case 3 -> {
                        h.assertTrue(elapsed < 420, "Ground rod never unloaded: " + level.getChunkSource().getChunkDebugData(remoteChunk));
                        if (elapsed % 5 == 0) level.getChunkSource().tick(() -> true, true);
                        if (level.getChunkSource().getChunkNow(remoteChunk.x, remoteChunk.z) != null || unloads.get() <= cycles[0]) return;
                        WorldWidePowerGrid.LOGGER.info("Grounding regression: actual rod unload {} after {} ticks", cycles[0] + 1, elapsed);
                        stage[0] = 4; since[0] = tick;
                    }
                    case 4 -> {
                        if (elapsed < 5) return;
                        near(h, endpoint(level, source, 0).getVoltage(), 10, "Source after ground chunk unload");
                        near(h, endpoint(level, source, 1).getVoltage(), 0, "Earth return after ground chunk unload");
                        unchanged(h, level, source, load, connector, remoteRod, endpoints, stamps, network[0]);
                        level.setChunkForced(remoteChunk.x, remoteChunk.z, true); level.getChunk(remoteChunk.x, remoteChunk.z);
                        stage[0] = 5; since[0] = tick;
                    }
                    case 5 -> {
                        h.assertTrue(elapsed < 80, "Reloaded ground rod never became entity-ready");
                        if (!level.areEntitiesLoaded(remoteChunk.toLong())) return;
                        stage[0] = 6; since[0] = tick;
                    }
                    case 6 -> {
                        if (elapsed < 5) return;
                        h.assertTrue(level.getBlockState(remoteRod).is(CEEBlocks.GROUND_ROD.get()), "Reload lost the ground rod");
                        near(h, endpoint(level, source, 0).getVoltage(), grounded[0], "Restored positive grounding");
                        near(h, endpoint(level, source, 1).getVoltage(), grounded[1], "Restored negative grounding");
                        unchanged(h, level, source, load, connector, remoteRod, endpoints, stamps, network[0]);
                        if (++cycles[0] == 2) {
                            DynamicGameTests.audit(h); cleanup.run(); h.succeed();
                        } else {
                            level.setChunkForced(remoteChunk.x, remoteChunk.z, false);
                            stage[0] = 3; since[0] = tick;
                        }
                    }
                }
            } catch (RuntimeException failure) { cleanup.run(); throw failure; }
        });
    }

    private static void unchanged(GameTestHelper h, ServerLevel level, BlockPos source, BlockPos load,
                                  BlockPos connector, BlockPos remoteRod, OwnedFloatingNode[] endpoints, Object[] stamps, ElectricalNetwork network) {
        h.assertTrue(endpoint(level, source, 0) == endpoints[0] && endpoint(level, source, 1) == endpoints[1], "Ground-only lifecycle replaced source nodes");
        h.assertTrue(endpoints[0].getNetwork() == network, "Ground-only lifecycle replaced the source network");
        h.assertTrue(branch(level, source, 0, connector, 0) == stamps[0]
                && branch(level, load, 0, load, 1) == stamps[1]
                && branch(level, connector, 0, remoteRod, 0) == stamps[2], "Ground-only lifecycle replaced persistent branches");
    }

    private static OwnedFloatingNode endpoint(ServerLevel level, BlockPos pos, int terminal) {
        return new BlockWireEndpoint(pos, terminal).getNode(level);
    }
    private static Object branch(ServerLevel level, BlockPos a, int pa, BlockPos b, int pb) {
        return Bridges.get(level).branchIdentity(new InWorldNode(pa, a), new InWorldNode(pb, b));
    }
    private static double wireResistance(ServerLevel level, BlockPos a, int pa, BlockPos b, int pb) {
        return InfrastructureSavedData.load(level).getConnectionData(
                new InWorldNodeConnection(new InWorldNode(pa, a), new InWorldNode(pb, b))).getResistance();
    }
    private static void near(GameTestHelper h, double actual, double expected, String message) {
        BoardComponentGameTests.near(h, actual, expected, .03, message);
    }

    private static final class GroundFixture {
        final GameTestHelper h;
        final BlockPos source, load;
        Consumer<AddToElectricGraphEvent> listener;
        OwnedFloatingNode positive, negative;
        ElectricalNetwork network;
        Object loadBranch;

        GroundFixture(GameTestHelper h) {
            this.h = h;
            var a = new BlockPos(1, 2, 1); var b = new BlockPos(4, 2, 1);
            h.setBlock(a.below(), Blocks.STONE); h.setBlock(b.below(), Blocks.STONE);
            h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get()); h.setBlock(b, CEEBlocks.CREATIVE_RESISTOR.get());
            source = h.absolutePos(a); load = h.absolutePos(b);
        }
        void listen(Consumer<AddToElectricGraphEvent> listener) {
            this.listener = listener;
            NeoForge.EVENT_BUS.addListener(AddToElectricGraphEvent.class, listener);
        }
        void wire() {
            ((CreativeSourceBlockEntity) h.getLevel().getBlockEntity(source)).setValue(10);
            DevicesSavedData.load(h.getLevel()).getDevice(load, ResistorDevice.class).properties = ElectricalProperties.resistor(100);
            WiringGameTests.connect(h, source, 0, load, 0, true);
            WiringGameTests.connect(h, source, 1, load, 1, false);
        }
        void at(long tick, Runnable action) {
            h.runAtTickTime(tick, () -> {
                try { action.run(); }
                catch (RuntimeException failure) { close(); throw failure; }
            });
        }
        void expect(double a, double b) {
            near(h, endpoint(h.getLevel(), source, 0).getVoltage(), a, "Positive ground potential");
            near(h, endpoint(h.getLevel(), source, 1).getVoltage(), b, "Negative ground potential");
        }
        void capture() {
            positive = endpoint(h.getLevel(), source, 0); negative = endpoint(h.getLevel(), source, 1);
            network = positive.getNetwork(); loadBranch = branch(h.getLevel(), load, 0, load, 1);
            h.assertTrue(loadBranch != null, "Missing CEE load branch");
        }
        void unchanged() {
            h.assertTrue(endpoint(h.getLevel(), source, 0) == positive && endpoint(h.getLevel(), source, 1) == negative, "Ground change replaced existing nodes");
            h.assertTrue(positive.getNetwork() == network && branch(h.getLevel(), load, 0, load, 1) == loadBranch, "Ground change replaced the network or load branch");
        }
        void close() { if (listener != null) { NeoForge.EVENT_BUS.unregister(listener); listener = null; } }
        void finish() { DynamicGameTests.audit(h); close(); h.succeed(); }
    }
}
