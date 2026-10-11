package org.cha0scollective.wwpg.examples.upstream;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlock;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelBlockEntity;
import com.george_vi.electroenergetics.content.electrical_panel.ElectricalPanelSlot;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Native upstream add-on identities and state across real server/chunk lifecycles. */
@GameTestHolder("wwpg_api_upstream_restart")
@PrefixGameTestTemplate(false)
public final class UpstreamRestartGameTests {
    private static final BlockPos COLD_SOURCE = new BlockPos(24640, 64, 24640);
    private static final Path EXPECTED = Path.of("upstream-api-before-restart.nbt");

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void customPanelAndNativeBoardSurviveColdServerRestart(GameTestHelper helper) throws IOException {
        var level = helper.getLevel();
        var chunk = new ChunkPos(COLD_SOURCE);
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunkAt(COLD_SOURCE);
        boolean verify = System.getProperty("wwpg.test.restartPhase", "SETUP").equals("VERIFY");
        if (!verify) place(helper, COLD_SOURCE);
        if (verify) {
            var expected = NbtIo.readCompressed(EXPECTED, NbtAccounter.unlimitedHeap());
            compare(helper, expected, snapshot(helper, COLD_SOURCE), "Cold saved state before the first solve");
            helper.assertTrue(expected.getCompound("Panel").getDouble("ApiStoredVoltage") > 60,
                    "Cold restart must start from genuinely charged custom storage");
        }
        var sequence = helper.startSequence().thenIdle(5).thenWaitUntil(() ->
                helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "Waiting for saved native wire entities"));
        if (!verify) sequence.thenExecute(() -> connect(helper, COLD_SOURCE));
        sequence.thenIdle(35).thenExecute(() -> {
            assertOperation(helper, COLD_SOURCE);
            // The final oracle is written after ticking stops. Capturing it when
            // this test succeeds would lag later accepted ticks in the same batch.
            var panel = attachment(helper, COLD_SOURCE.east(3));
            var board = (CircuitBoardBlockEntity) level.getBlockEntity(COLD_SOURCE.south(4).east(3));
            Consumer<ServerStoppingEvent> stopping = event -> {
                if (event.getServer() != level.getServer()) return;
                var finalState = new CompoundTag();
                var electrical = new CompoundTag();
                panel.write(electrical, false, level.registryAccess());
                finalState.put("Panel", electrical);
                finalState.put("Board", board.getSchematic().serializeNbt(level.registryAccess()));
                finalState.putIntArray("Terminals", new int[]{panel.nodes[0].id(), panel.nodes[1].id()});
                try { NbtIo.writeCompressed(finalState, EXPECTED); }
                catch (IOException failure) { throw new IllegalStateException("Cannot save cold restart acceptance oracle", failure); }
                DevicesSavedData.load(level).setDirty();
            };
            NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, stopping);
            level.getDataStorage().save();
            level.getChunkSource().save(true);
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 3000)
    public static void customPanelAndNativeBoardSurviveFiveActualChunkCycles(GameTestHelper helper) {
        var level = helper.getLevel();
        // Keep this unload fixture outside the generation-ticket radius of the
        // concurrently forced cold-restart fixture, so a real unload is possible.
        var source = new BlockPos(25344, 64, 25344);
        var chunk = new ChunkPos(source);
        var unloads = new AtomicInteger();
        var expected = new CompoundTag[1];
        var panelReference = new StorageIndicatorAttachment[1];
        var boardReference = new CircuitBoardBlockEntity[1];
        var panelBlockReference = new ElectricalPanelBlockEntity[1];
        Consumer<ChunkEvent.Unload> listener = event -> {
            if (event.getLevel() != level || !event.getChunk().getPos().equals(chunk)) return;
            if (panelReference[0] != null && boardReference[0] != null) {
                var state = new CompoundTag();
                var data = new CompoundTag();
                panelReference[0].write(data, false, level.registryAccess());
                state.put("Panel", data);
                state.put("Board", boardReference[0].getSchematic().serializeNbt(level.registryAccess()));
                state.putIntArray("Terminals", new int[]{panelReference[0].nodes[0].id(), panelReference[0].nodes[1].id()});
                expected[0] = state;
            }
            unloads.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, listener);
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunkAt(source);
        place(helper, source);
        var ids = new HashSet<String>();
        var sequence = helper.startSequence().thenIdle(5).thenWaitUntil(() ->
                helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "Waiting for initial add-on wire readiness"))
                .thenExecute(() -> connect(helper, source)).thenIdle(35).thenExecute(() -> {
                    assertOperation(helper, source);
                    ids.addAll(pgWireIds(helper, source));
                    helper.assertTrue(ids.size() == 2, "Expected one PG wire in each mixed circuit before unload");
                });
        for (int cycle = 1; cycle <= 5; cycle++) {
            final int expectedUnloads = cycle;
            sequence.thenExecute(() -> {
                panelReference[0] = attachment(helper, source.east(3));
                panelBlockReference[0] = (ElectricalPanelBlockEntity) level.getBlockEntity(source.east(3));
                boardReference[0] = (CircuitBoardBlockEntity) level.getBlockEntity(source.south(4).east(3));
                DevicesSavedData.load(level).setDirty();
                level.getDataStorage().save();
                level.getChunkSource().save(true);
                level.setChunkForced(chunk.x, chunk.z, false);
            }).thenIdle(330).thenWaitUntil(() -> {
                level.getChunkSource().tick(() -> true, true);
                helper.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null
                                && unloads.get() >= expectedUnloads,
                        "Waiting for actual upstream add-on chunk unload " + expectedUnloads);
            }).thenExecute(() -> {
                level.setChunkForced(chunk.x, chunk.z, true);
                level.getChunkAt(source);
                helper.assertTrue(expected[0] != null, "Actual unload did not capture committed attachment state");
                compare(helper, expected[0], snapshot(helper, source), "Actual chunk cycle " + expectedUnloads + " before first solve");
                helper.assertTrue(level.getBlockEntity(source.east(3)) != panelBlockReference[0],
                        "Chunk reload must reconstruct the panel block entity");
            }).thenWaitUntil(() -> helper.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "Waiting for native wire entities"))
                    .thenIdle(8).thenExecute(() -> {
                        assertOperation(helper, source);
                        helper.assertTrue(ids.equals(pgWireIds(helper, source)),
                                "Reload duplicated or replaced native PG wire identities");
                        com.mojang.logging.LogUtils.getLogger().info(
                                "API_UPSTREAM_CHUNK_CYCLE: cycle={}; actualUnloads={}; preSolveStateVerified=true",
                                expectedUnloads, unloads.get());
                    });
        }
        sequence.thenExecute(() -> {
            NeoForge.EVENT_BUS.unregister(listener);
            level.setChunkForced(chunk.x, chunk.z, false);
        }).thenSucceed();
    }

    private static void place(GameTestHelper helper, BlockPos source) {
        var level = helper.getLevel();
        for (var pos : new BlockPos[]{source, source.east(3), source.south(4), source.south(4).east(3)}) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.getDefaultState());
        level.setBlockAndUpdate(source.east(3), CEEBlocks.ELECTRICAL_PANEL.getDefaultState().setValue(ElectricalPanelBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(source.south(4), CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        NativeFixtures.assembleAndPlace(helper, source.south(4).east(3), NativeFixtures.shuntSchematic());
    }

    private static void connect(GameTestHelper helper, BlockPos source) {
        var level = helper.getLevel();
        ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(70);
        var panel = UpstreamApiGameTests.insert(helper, source.east(3));
        NativeFixtures.wire(helper, source, 0, source.east(3), panel.nodes[0].id(), true);
        NativeFixtures.wire(helper, source, 1, source.east(3), panel.nodes[1].id(), false);
        DevicesSavedData.load(level).getDevice(source.south(4), CreativeBatteryDevice.class).voltage = 20;
        DevicesSavedData.load(level).setDirty();
        NativeFixtures.wire(helper, source.south(4), 1, source.south(4).east(3), 0, false);
        NativeFixtures.wire(helper, source.south(4), 0, source.south(4).east(3), 1, true);
    }

    private static StorageIndicatorAttachment attachment(GameTestHelper helper, BlockPos position) {
        var panel = (ElectricalPanelBlockEntity) helper.getLevel().getBlockEntity(position);
        helper.assertTrue(panel != null, "Saved native panel is missing");
        var attachment = panel.getAttachments()[ElectricalPanelSlot.THIRD_LEFT.ordinal()];
        helper.assertTrue(attachment instanceof StorageIndicatorAttachment && attachment.type == UpstreamExample.PANEL.get(),
                "Saved panel lost its custom attachment registry identity");
        return (StorageIndicatorAttachment) attachment;
    }

    private static CompoundTag snapshot(GameTestHelper helper, BlockPos source) {
        var panel = attachment(helper, source.east(3));
        var data = new CompoundTag();
        panel.write(data, false, helper.getLevel().registryAccess());
        var board = (CircuitBoardBlockEntity) helper.getLevel().getBlockEntity(source.south(4).east(3));
        helper.assertTrue(board != null, "Saved native add-on circuit board is missing");
        var result = new CompoundTag();
        result.put("Panel", data);
        result.put("Board", board.getSchematic().serializeNbt(helper.getLevel().registryAccess()));
        result.putIntArray("Terminals", new int[]{panel.nodes[0].id(), panel.nodes[1].id()});
        return result;
    }

    private static void compare(GameTestHelper helper, CompoundTag expected, CompoundTag actual, String label) {
        for (var key : new String[]{"ApiStateVersion", "ApiStoredVoltage", "ApiAcceptedSubsteps"})
            helper.assertTrue(java.util.Objects.equals(expected.getCompound("Panel").get(key), actual.getCompound("Panel").get(key)),
                    label + " changed " + key + ": " + expected.getCompound("Panel").get(key) + " -> " + actual.getCompound("Panel").get(key));
        helper.assertTrue(expected.getCompound("Board").equals(actual.getCompound("Board")),
                label + " changed native component UUIDs, settings, or traces");
        helper.assertTrue(java.util.Arrays.equals(expected.getIntArray("Terminals"), actual.getIntArray("Terminals")),
                label + " changed custom attachment terminal identities");
    }

    private static void assertOperation(GameTestHelper helper, BlockPos source) {
        var panel = attachment(helper, source.east(3));
        NativeFixtures.near(helper, panel.electrical.savedVoltage, 70, .07, "Saved custom panel resumed charging");
        helper.assertTrue(panel.electrical.acceptedSubsteps > 0 && panel.measuredVoltage > 69.9,
                "Saved panel did not regain accepted history and solved voltage");
        var visible = new CompoundTag();
        panel.write(visible, true, helper.getLevel().registryAccess());
        helper.assertTrue(visible.getFloat("Light") > .95, "Saved native panel indicator did not visibly light");
        var board = (CircuitBoardBlockEntity) helper.getLevel().getBlockEntity(source.south(4).east(3));
        var component = board.getComponentsStream().filter(placed -> placed.component == UpstreamExample.SHUNT.get()).findFirst();
        helper.assertTrue(component.isPresent() && !component.orElseThrow().destroyed,
                "Saved native add-on board component is missing or damaged");
        double current = InfrastructureSavedData.load(helper.getLevel()).ticker.lastResults.getCurrentThrough(
                new InWorldNode(1, source.south(4)), new InWorldNode(0, source.south(4).east(3)));
        NativeFixtures.near(helper, Math.abs(current), .2, .0002, "Saved native add-on board resumed mixed power");
    }

    private static Set<String> pgWireIds(GameTestHelper helper, BlockPos source) {
        var ids = new HashSet<String>();
        var world = GlobalElectricNetworks.getWorldNetworks(helper.getLevel());
        for (var endpoint : new BlockWireEndpoint[]{new BlockWireEndpoint(source, 0), new BlockWireEndpoint(source.south(4), 0)})
            for (var wire : world.findConnectedWires(endpoint)) ids.add(wire.persistentOwnerId.toString());
        return ids;
    }
}
