package org.cha0scollective.wwpg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.patryk3211.powergrid.collections.ModdedBlockEntities;
import org.patryk3211.powergrid.collections.ModdedBlocks;

/** Loading a schematic must work before Minecraft assigns the new entity its world. */
@GameTestHolder("wwpg_design")
@PrefixGameTestTemplate(false)
public final class DesignTablePersistenceGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void designerInventoryChangesScheduleAWorldSave(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(pos, ModdedBlocks.CIRCUIT_DESIGN_TABLE.get());
        var table = (CircuitDesignTableBlockEntity) h.getBlockEntity(pos);
        var chunk = h.getLevel().getChunkAt(h.absolutePos(pos));
        chunk.setUnsaved(false);
        table.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(Items.PAPER, 2));
        h.assertTrue(chunk.isUnsaved(), "Native inventory edit was never scheduled for a world save");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void savedDesignerLoadsBeforeWorldAssignment(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(pos, ModdedBlocks.CIRCUIT_DESIGN_TABLE.get());
        var original = (CircuitDesignTableBlockEntity) h.getBlockEntity(pos);
        original.setSchematic(BoardWorkflowGameTests.viaLabelBoard());
        original.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(Items.PAPER, 3));
        var registries = h.getLevel().registryAccess();
        var expected = original.getSchematic().serializeNbt(registries);
        var saved = original.saveWithFullMetadata(registries);
        var restored = new CircuitDesignTableBlockEntity(ModdedBlockEntities.CIRCUIT_DESIGN_TABLE.get(),
                h.absolutePos(pos), original.getBlockState());
        h.assertTrue(restored.getLevel() == null, "Cold-load regression accidentally assigned a world");
        restored.loadWithComponents(saved, registries);
        h.assertTrue(expected.equals(restored.getSchematic().serializeNbt(registries)),
                "Cold-load lost component UUIDs, configuration, traces, via or label");
        h.assertTrue(restored.getInventory().getItem(1).getCount() == 3, "Cold-load lost inventory");
        restored.saveWithFullMetadata(registries);
        h.succeed();
    }
}
