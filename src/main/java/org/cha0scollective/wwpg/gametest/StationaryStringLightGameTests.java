package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedEntities;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.light.string.StringLightCordEntity;
import org.patryk3211.powergrid.electricity.light.string.StringLightCordRecipe;
import java.util.List;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

@GameTestHolder("wwpg_distribution")
@PrefixGameTestTemplate(false)
public final class StationaryStringLightGameTests {
    @GameTest(template = "empty", timeoutTicks = 210)
    public static void nativeColoredStringCordLightsMixedCircuitAndReturnsCuttingCost(GameTestHelper h) {
        var supply = new BlockPos(1, 3, 1);
        var load = new BlockPos(6, 3, 1);
        place(h, supply, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, load, ModdedBlocks.CREATIVE_RESISTOR.get());
        var box = new AABB(h.absolutePos(supply)).inflate(8);
        var recipe = new StringLightCordRecipe(CraftingBookCategory.MISC);
        var input = CraftingInput.of(3, 1, List.of(ModdedItems.STRING_LIGHT_CORD.asStack(),
                Items.RED_DYE.getDefaultInstance(), Items.BLUE_DYE.getDefaultInstance()));
        h.assertTrue(recipe.matches(input, h.getLevel()), "Native string-light dye recipe rejected a cord and dyes");
        var cord = recipe.assemble(input, h.getLevel().registryAccess());
        cord.setCount(64);
        int[] cost = {0};
        h.runAtTickTime(5, () -> {
            source(h, supply, 120);
            ((org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity) h.getBlockEntity(load)).setValue(1000);
            cost[0] = NativeInteractions.connectSplitCord(h, h.absolutePos(supply), 1, 0,
                    h.absolutePos(load), 0, 1, cord);
            h.assertTrue(cost[0] > 0, "Native string cord placement consumed no items");
        });
        h.runAtTickTime(65, () -> {
            var wire = h.getLevel().getEntitiesOfClass(StringLightCordEntity.class, box).getFirst();
            h.assertTrue(wire.getWireCount() == cost[0], "Placed string cord did not retain its native item cost");
            h.assertTrue(wire.measuredCurrent() > 0 && pgVoltage(h, load) > 119,
                    "String cord failed to carry mixed electrical power");
            boolean light = false;
            for (int x = 2; x <= 5; x++) for (int y = 2; y <= 3; y++)
                light |= ModdedBlocks.STRING_LIGHT_BLOCK.has(h.getBlockState(new BlockPos(x, y, 1)));
            h.assertTrue(light, "Solved string filament failed to create native light output");
            var saved = new CompoundTag();
            wire.saveWithoutId(saved);
            h.assertTrue(saved.getFloat("Filament") > 600 && saved.getIntArray("Pattern").length == 2,
                    "Native cord did not save its heated filament and dye pattern");
            var cold = ModdedEntities.STRING_LIGHT_CORD.get().create(h.getLevel());
            cold.load(saved);
            var resaved = new CompoundTag();
            cold.saveWithoutId(resaved);
            near(h, resaved.getFloat("Filament"), saved.getFloat("Filament"), .0001,
                    "Saved string filament before first solve");
            h.assertTrue(java.util.Arrays.equals(saved.getIntArray("Pattern"), resaved.getIntArray("Pattern")),
                    "Saved string cord lost its native dye pattern");
            source(h, supply, 0);
        });
        h.runAtTickTime(155, () -> {
            var wire = h.getLevel().getEntitiesOfClass(StringLightCordEntity.class, box).getFirst();
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, ModdedItems.WIRE_CUTTER.asStack());
            h.assertTrue(wire.interact(player, InteractionHand.MAIN_HAND).consumesAction(),
                    "Native wire cutter rejected the string cord");
        });
        h.runAtTickTime(175, () -> {
            h.assertTrue(h.getLevel().getEntitiesOfClass(StringLightCordEntity.class, box).isEmpty(),
                    "Cut string cord retained its wire entity");
            int returned = h.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                    .filter(e -> ModdedItems.STRING_LIGHT_CORD.isIn(e.getItem())).mapToInt(e -> e.getItem().getCount()).sum();
            h.assertTrue(returned == cost[0], "Native cutting did not return its construction cost: " + returned + "/" + cost[0]);
            for (int x = 2; x <= 5; x++) for (int y = 2; y <= 3; y++)
                h.assertTrue(!ModdedBlocks.STRING_LIGHT_BLOCK.has(h.getBlockState(new BlockPos(x, y, 1))),
                        "Removed string cord left a ghost light block");
            near(h, pgVoltage(h, load), 0, .0001, "Cut cord left ghost power");
            finish(h);
        });
    }
    private static double pgVoltage(GameTestHelper h, BlockPos pos) {
        var p = h.absolutePos(pos);
        return new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(p, 0).getNode(h.getLevel()).getVoltage()
                - new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(p, 1).getNode(h.getLevel()).getVoltage();
    }
}
