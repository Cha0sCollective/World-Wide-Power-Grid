package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.circuitboard.IncompleteCircuitItem;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;

import java.io.IOException;
import java.nio.file.Path;

/** Fixed assembly lives in the saved world across separate server JVMs. */
@GameTestHolder("wwpg_design")
@PrefixGameTestTemplate(false)
public final class DesignerRestartGameTests {
    private static final BlockPos SOURCE = new BlockPos(6000, 64, 6000), TABLE = SOURCE.east(3), BOARD = SOURCE.east(6);
    private static final Path EXPECTED = Path.of("designer-before-restart.nbt");

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void savedDesignReopensCopiesAssemblesAndPowersAfterRestart(GameTestHelper h) throws IOException {
        var level = h.getLevel();
        level.setChunkForced(SOURCE.getX() >> 4, SOURCE.getZ() >> 4, true);
        level.getChunkAt(SOURCE);
        boolean verify = System.getProperty("wwpg.test.restartPhase", "SETUP").equals("VERIFY");
        if (!verify) {
            for (var pos : new BlockPos[]{SOURCE, TABLE, BOARD}) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(SOURCE, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
            level.setBlockAndUpdate(TABLE, ModdedBlocks.CIRCUIT_DESIGN_TABLE.getDefaultState());
            var table = (CircuitDesignTableBlockEntity) level.getBlockEntity(TABLE);
            table.setSchematic(BoardWorkflowGameTests.viaLabelBoard());
            table.getInventory().setItem(0, Items.REDSTONE.getDefaultInstance());
            table.getInventory().setItem(1, new ItemStack(Items.PAPER, 3));
            table.getInventory().setItem(2, Items.DIAMOND.getDefaultInstance());
            NbtIo.writeCompressed(table.saveWithoutMetadata(level.registryAccess()), EXPECTED);
        } else {
            // Inspect the stored design/items immediately after loading, before a solve.
            var expected = NbtIo.readCompressed(EXPECTED, NbtAccounter.unlimitedHeap());
            var table = (CircuitDesignTableBlockEntity) level.getBlockEntity(TABLE);
            h.assertTrue(table != null, "Restart lost the design table");
            var actual = table.saveWithoutMetadata(level.registryAccess());
            h.assertTrue(expected.getCompound("Schematic").equals(actual.getCompound("Schematic")),
                    "Restart lost schematic UUIDs, settings, traces, vias or labels");
            h.assertTrue(expected.getCompound("WWPGInventory").equals(actual.getCompound("WWPGInventory")),
                    "Restart lost the design-table inventory before its first solve");
        }
        h.runAtTickTime(5, () -> {
            DevicesSavedData.load(level).getDevice(SOURCE, CreativeBatteryDevice.class).voltage = Math.sqrt(40
                    * org.patryk3211.powergrid.config.ResistanceValues.get(ModdedBlocks.CIRCUIT_DESIGN_TABLE.get()));
            if (!verify) {
                NativeInteractions.connectCord(h, SOURCE, 1, 0, TABLE);
            }
        });
        h.runAtTickTime(25, () -> org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info(
                "DESIGNER_RESTART: verify={}, entitiesReady={}, power={}, source={}, connections={}", verify,
                level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(SOURCE).toLong()),
                ((CircuitDesignTableBlockEntity) level.getBlockEntity(TABLE)).power(),
                DevicesSavedData.load(level).getDevice(SOURCE, CreativeBatteryDevice.class).voltage,
                org.patryk3211.powergrid.electricity.base.IElectric.getAt(level, TABLE).getBehaviour(level, TABLE,
                        level.getBlockState(TABLE)).getConnections()));
        h.startSequence().thenIdle(25).thenWaitUntil(() -> {
            h.assertTrue(level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(SOURCE).toLong()),
                    "Waiting for the saved designer's cord entities");
            var table = (CircuitDesignTableBlockEntity) level.getBlockEntity(TABLE);
            h.assertTrue(table.isPowered(), "Saved design table did not recover mixed power; power=" + table.power());
        }).thenExecute(() -> {
            var table = (CircuitDesignTableBlockEntity) level.getBlockEntity(TABLE);
            org.cha0scollective.wwpg.WorldWidePowerGrid.LOGGER.info("DESIGNER_READY: verify={}, entitiesReady=true, power={}",
                    verify, table.power());
            var restore = table.saveWithoutMetadata(level.registryAccess());
            // PG intentionally creates new UUIDs in copied items, while keeping
            // identities in world saves. Compare the native safe format for copies.
            var design = table.getSchematic().serializeSafeNbt(level.registryAccess());
            table.writeToItem(false);
            h.assertTrue(table.getInventory().getItem(1).getCount() == 2,
                    "Native design save did not consume exactly one paper");
            var copy = table.getInventory().getItem(2).copy();
            h.assertTrue(copy.is(ModdedItems.CIRCUIT_SCHEMATIC.get()), "Native designer did not produce a schematic");
            h.assertTrue(table.getSchematic().components().isEmpty(), "Native designer failed to clear after writing");
            table.getInventory().setItem(0, copy);
            table.readFromItem();
            h.assertTrue(design.equals(table.getSchematic().serializeSafeNbt(level.registryAccess())),
                    "Reopening the saved item changed the circuit design");
            var data = new CompoundTag();
            data.put("Schematic", design);
            var assembled = ModdedItems.INCOMPLETE_CIRCUIT.asStack();
            assembled.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            for (var component : table.getSchematic().components()) {
                assembled = IncompleteCircuitItem.insert(level, assembled,
                        new ItemStack(ComponentRegistry.getItem(level, component.component)));
                h.assertTrue(assembled != null, "Native board assembly rejected " + ComponentRegistry.getId(component.component));
            }
            h.assertTrue(assembled.is(ModdedBlocks.CIRCUIT_BOARD.get().asItem()), "Native assembly did not finish the saved circuit");
            level.setBlockAndUpdate(BOARD, Blocks.AIR.defaultBlockState());
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, assembled);
            var placed = assembled.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(BOARD.below().getCenter().add(0, .5, 0), Direction.UP, BOARD.below(), false)));
            h.assertTrue(placed.consumesAction() && assembled.isEmpty(), "Native saved board item failed to place");
            h.assertTrue(design.equals(((CircuitBoardBlockEntity) level.getBlockEntity(BOARD))
                    .getSchematic().serializeSafeNbt(level.registryAccess())), "Placed board changed saved design");
            WiringGameTests.connect(h, SOURCE, 1, BOARD, 0, false);
            WiringGameTests.connect(h, SOURCE, 0, BOARD, 1, true);
            // Restore the original editable table so consecutive VERIFY runs exercise the same saved state.
            table.loadWithComponents(restore, level.registryAccess());
            table.setChanged();
            DevicesSavedData.load(level).getDevice(SOURCE, CreativeBatteryDevice.class).voltage = 100;
        }).thenIdle(25).thenExecute(() -> {
            var results = InfrastructureSavedData.load(level).ticker.lastResults;
            double current = results.getCurrentThrough(new InWorldNode(1, SOURCE), new InWorldNode(0, BOARD));
            StationaryEquipmentGameTests.near(h, Math.abs(current), .1, .0001, "Saved via/label board's mixed current");
            level.setBlockAndUpdate(BOARD, Blocks.AIR.defaultBlockState());
            level.getDataStorage().save();
            level.getChunkSource().save(true);
            DynamicGameTests.audit(h);
        }).thenSucceed();
    }
}
