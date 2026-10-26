package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.GlobalElectricNetworks;
import org.patryk3211.powergrid.electricity.WorldNetworks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.HashSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("wwpg_design")
@PrefixGameTestTemplate(false)
public final class DesignerChunkCyclesGameTests {
    @GameTest(template = "empty", timeoutTicks = 3000)
    public static void savedDesignerAndCordSurviveFiveActualChunkCycles(GameTestHelper h) throws IOException {
        var level = h.getLevel();
        var source = new BlockPos(6256, 64, 6256);
        var tablePos = source.east(3);
        var chunk = new ChunkPos(source);
        var unloads = new AtomicInteger();
        Consumer<ChunkEvent.Unload> listener = event -> {
            if (event.getLevel() == level && event.getChunk().getPos().equals(chunk)) unloads.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Unload.class, listener);
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunkAt(source);
        boolean verify = System.getProperty("wwpg.test.restartPhase", "SETUP").equals("VERIFY");
        var savedDesign = Path.of("designer-cycles-before-restart.nbt");
        if (!verify) {
            for (var pos : new BlockPos[]{source, tablePos}) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(source, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            level.setBlockAndUpdate(tablePos, ModdedBlocks.CIRCUIT_DESIGN_TABLE.getDefaultState());
            var initial = (CircuitDesignTableBlockEntity) level.getBlockEntity(tablePos);
            initial.setSchematic(BoardWorkflowGameTests.viaLabelBoard());
            initial.getInventory().setItem(1, Items.PAPER.getDefaultInstance().copyWithCount(3));
            NbtIo.writeCompressed(initial.saveWithoutMetadata(level.registryAccess()), savedDesign);
        }
        var table = (CircuitDesignTableBlockEntity) level.getBlockEntity(tablePos);
        h.assertTrue(table != null, "Cold restart lost the chunk-cycle design table");
        if (verify) {
            var stored = NbtIo.readCompressed(savedDesign, NbtAccounter.unlimitedHeap());
            var cold = table.saveWithoutMetadata(level.registryAccess());
            h.assertTrue(stored.getCompound("Schematic").equals(cold.getCompound("Schematic")),
                    "Cold restart lost the chunk-cycle design before its first solve");
            h.assertTrue(stored.getCompound("WWPGInventory").equals(cold.getCompound("WWPGInventory")),
                    "Cold restart lost chunk-cycle inventory before its first solve");
        }
        CompoundTag[] expected = {null};
        Set<WorldNetworks.PartId> ids = new HashSet<>();
        var sequence = h.startSequence().thenIdle(5).thenWaitUntil(() ->
                h.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "Waiting for initial cord entity readiness"))
                .thenExecute(() -> {
            if (verify) return;
            ((SmartBlockEntity) level.getBlockEntity(source)).getBehaviour(ScrollValueBehaviour.TYPE)
                    .setValue((int) Math.ceil(Math.sqrt(40 * org.patryk3211.powergrid.config.ResistanceValues
                            .get(ModdedBlocks.CIRCUIT_DESIGN_TABLE.get())) * 1000));
            NativeInteractions.connectCord(h, source, 1, 0, tablePos);
        }).thenWaitUntil(() -> h.assertTrue(table.isPowered(), "Waiting for native cord power before its first unload"))
                .thenExecute(() -> {
            expected[0] = table.saveWithoutMetadata(level.registryAccess());
            var world = GlobalElectricNetworks.getWorldNetworks(level);
            for (int port = 0; port < 2; port++) for (var part : world.findConnectedWires(new BlockWireEndpoint(source, port)))
                ids.add(part.persistentOwnerId);
            h.assertTrue(ids.size() == 2, "Native cord must contain two stable saved wire identities");
        });
        for (int cycle = 1; cycle <= 5; cycle++) {
            final int expectedUnloads = cycle;
            sequence.thenExecute(() -> {
                level.getDataStorage().save();
                level.getChunkSource().save(true);
                level.setChunkForced(chunk.x, chunk.z, false);
            }).thenIdle(330).thenWaitUntil(() -> {
                level.getChunkSource().tick(() -> true, true);
                h.assertTrue(level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null
                        && unloads.get() >= expectedUnloads, "Waiting for actual chunk unload " + expectedUnloads);
            }).thenExecute(() -> {
                DelayedEntityLoads.hold(level, chunk, 60);
                level.setChunkForced(chunk.x, chunk.z, true);
                level.getChunkAt(source);
                // Schematic and inventory are inspected before wire entities or a resumed solve.
                var loaded = (CircuitDesignTableBlockEntity) level.getBlockEntity(tablePos);
                var cold = loaded.saveWithoutMetadata(level.registryAccess());
                h.assertTrue(expected[0].getCompound("Schematic").equals(cold.getCompound("Schematic")),
                        "Cycle " + expectedUnloads + " changed the saved design");
                h.assertTrue(expected[0].getCompound("WWPGInventory").equals(cold.getCompound("WWPGInventory")),
                        "Cycle " + expectedUnloads + " lost inventory");
            }).thenIdle(20).thenExecute(() -> {
                h.assertTrue(!level.areEntitiesLoaded(chunk.toLong()), "Delayed native cord loading was not exercised");
                var world = GlobalElectricNetworks.getWorldNetworks(level);
                h.assertTrue(ids.stream().allMatch(id -> world.getPart(id) != null),
                        "Saved native cord parts expired before entity readiness");
            }).thenWaitUntil(() -> h.assertTrue(level.areEntitiesLoaded(chunk.toLong()), "Waiting for cord entities"))
                    .thenIdle(5).thenExecute(() -> {
                var loaded = (CircuitDesignTableBlockEntity) level.getBlockEntity(tablePos);
                h.assertTrue(loaded != table && loaded.isPowered(), "Actual chunk cycle failed to restore designer power");
                var world = GlobalElectricNetworks.getWorldNetworks(level);
                var restored = new HashSet<WorldNetworks.PartId>();
                for (int port = 0; port < 2; port++) for (var part : world.findConnectedWires(new BlockWireEndpoint(source, port)))
                    restored.add(part.persistentOwnerId);
                h.assertTrue(ids.equals(restored), "Actual reload duplicated or replaced saved cord identities");
                org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("DESIGNER_CYCLE: cycle={}, actualUnloads={}, power={}",
                        expectedUnloads, unloads.get(), loaded.power());
            });
        }
        sequence.thenExecute(() -> {
            NeoForge.EVENT_BUS.unregister(listener);
            level.setChunkForced(chunk.x, chunk.z, false);
            DynamicGameTests.audit(h);
        }).thenSucceed();
    }
}
