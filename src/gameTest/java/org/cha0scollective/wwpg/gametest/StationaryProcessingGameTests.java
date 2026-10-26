package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.ElectricBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.electromagnet.ElectromagnetBlockEntity;
import org.patryk3211.powergrid.electricity.fan.ElectricFanBlockEntity;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

@GameTestHolder("wwpg_distribution")
@PrefixGameTestTemplate(false)
public final class StationaryProcessingGameTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void mixedSupplyMagnetizesNativeRecipeAndStopsUnpowered(GameTestHelper h) {
        var supply = new BlockPos(1, 2, 1);
        var magnet = new BlockPos(4, 3, 3);
        var input = magnet.below();
        place(h, supply, CEEBlocks.CREATIVE_BATTERY.get());
        h.setBlock(input.below(), Blocks.STONE);
        h.setBlock(magnet, ModdedBlocks.ELECTROMAGNET.get());
        h.setBlock(magnet.above(), ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING, Direction.DOWN));
        var box = new AABB(h.absolutePos(input)).inflate(1);
        h.runAtTickTime(5, () -> {
            source(h, supply, ((ElectricBlockEntity) h.getBlockEntity(magnet)).resistance() * 3);
            wire(h, supply, 1, magnet.above(), 0, true);
            wire(h, supply, 0, magnet.above(), 1, false);
            item(h, input, AllItems.ANDESITE_ALLOY.asStack());
        });
        h.startSequence().thenIdle(20).thenWaitUntil(() -> h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, box)
                .stream().anyMatch(e -> ModdedItems.MAGNET.isIn(e.getItem())), "Waiting for native in-world magnetizing recipe"))
        .thenExecute(() -> {
            h.assertTrue(((ElectromagnetBlockEntity) h.getBlockEntity(magnet)).getFieldStrength() > 0,
                    "Native recipe completed without a solved magnetic field");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                    .noneMatch(e -> AllItems.ANDESITE_ALLOY.isIn(e.getItem())), "Native recipe did not consume the input alloy");
            source(h, supply, 0);
        }).thenIdle(30).thenExecute(() -> item(h, input, AllItems.ANDESITE_ALLOY.asStack()))
        .thenIdle(80).thenExecute(() -> {
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                    .anyMatch(e -> AllItems.ANDESITE_ALLOY.isIn(e.getItem())), "Unpowered magnet continued processing alloy");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 550)
    public static void mixedSupplyFanPerformsNativeWashingRecipe(GameTestHelper h) {
        var supply = new BlockPos(1, 2, 1);
        var fan = new BlockPos(4, 3, 1);
        place(h, supply, CEEBlocks.CREATIVE_BATTERY.get());
        h.setBlock(fan, ModdedBlocks.ELECTRIC_FAN.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        for (int z = 1; z <= 4; z++) {
            h.setBlock(fan.south(z).below(), Blocks.STONE);
            h.setBlock(fan.south(z).east(), Blocks.GLASS);
            h.setBlock(fan.south(z).west(), Blocks.GLASS);
        }
        h.setBlock(fan.south(4), Blocks.GLASS);
        h.setBlock(fan.south(), Blocks.WATER);
        var box = new AABB(h.absolutePos(fan)).inflate(6);
        h.runAtTickTime(5, () -> {
            source(h, supply, ((ElectricBlockEntity) h.getBlockEntity(fan)).resistance() * 4);
            wire(h, supply, 1, fan, 0, true);
            wire(h, supply, 0, fan, 1, false);
            item(h, fan.south(2), AllItems.WHEAT_FLOUR.asStack());
        });
        h.startSequence().thenIdle(20).thenExecute(() -> h.assertTrue(((ElectricFanBlockEntity) h.getBlockEntity(fan)).getSpeed() > 200,
                "Mixed supply did not produce native processing airflow"))
        .thenWaitUntil(() -> h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                .anyMatch(e -> AllItems.DOUGH.isIn(e.getItem())), "Waiting for native fan washing to turn flour into dough"))
        .thenExecute(() -> { source(h, supply, 0); finish(h); });
    }

    private static void item(GameTestHelper h, BlockPos pos, net.minecraft.world.item.ItemStack stack) {
        var absolute = h.absolutePos(pos);
        var entity = new ItemEntity(h.getLevel(), absolute.getX() + .5, absolute.getY() + .1, absolute.getZ() + .5, stack);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setDefaultPickUpDelay();
        h.getLevel().addFreshEntity(entity);
    }
}
