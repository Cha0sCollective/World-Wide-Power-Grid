package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.transmission_distribution.voltage_regulator.VoltageRegulatorBlock;
import com.george_vi.electroenergetics.content.transmission_distribution.voltage_regulator.VoltageRegulatorDevice;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerCoreBlock;
import com.george_vi.electroenergetics.content.transmission_distribution.transformer.TransformerCoreDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;
import org.patryk3211.powergrid.collections.ModdedBlockEntities;
import org.patryk3211.powergrid.electricity.transformer.NetherTransformerBlock;
import java.util.UUID;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

@GameTestHolder("wwpg_distribution")
@PrefixGameTestTemplate(false)
public final class StationaryDistributionGameTests {
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void nativeCeeCoreAssemblyTransformsMixedPowerAndUsesRadiators(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1);
        var primary = new BlockPos(4, 2, 1);
        var secondary = primary.east();
        var load = new BlockPos(5, 2, 4);
        place(h, source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        place(h, load, ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(primary.below(), Blocks.STONE);
        var item = CEEBlocks.TRANSFORMER_CORE.asStack();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(-90);
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var below = h.absolutePos(primary.below());
        var placed = item.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(below.getCenter().add(0, .5, 0), Direction.UP, below, false)));
        h.assertTrue(placed.consumesAction() && item.isEmpty() && CEEBlocks.TRANSFORMER_CORE.has(h.getBlockState(secondary)),
                "Native core item did not create both winding halves");
        h.assertTrue(h.getBlockState(primary).getValue(TransformerCoreBlock.FACING) == Direction.EAST,
                "Fixture did not place the positive-facing native controller");
        double[] cooling = {0};
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(100);
            ((ResistorBlockEntity) h.getBlockEntity(load)).setValue(1000);
            ((SmartBlockEntity) h.getBlockEntity(primary)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(20);
            wire(h, source, 0, primary, 0, true);
            wire(h, source, 1, primary, 1, false);
            wire(h, secondary, 0, load, 0, false);
            wire(h, secondary, 1, load, 1, true);
        });
        h.runAtTickTime(30, () -> {
            near(h, output(h, load), 49.99375, .025, "Native core 20:10 turns with leakage resistance");
            cooling[0] = core(h, primary).heatDissipation;
            h.assertTrue(cooling[0] > 0, "Native core cooling was never calculated");
            ((SmartBlockEntity) h.getBlockEntity(secondary)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(40);
            h.setBlock(primary.above(), CEEBlocks.RADIATOR_PANEL.get());
        });
        h.runAtTickTime(55, () -> {
            near(h, output(h, load), 199.90005, .08, "Native core changed secondary turns");
            h.assertTrue(core(h, primary).heatDissipation > cooling[0], "Native radiator assembly did not increase cooling");
            h.setBlock(primary.above(), Blocks.AIR);
        });
        h.runAtTickTime(80, () -> {
            near(h, core(h, primary).heatDissipation, cooling[0], .01, "Removed radiator cooling");
            h.setBlock(secondary, Blocks.AIR);
        });
        h.runAtTickTime(100, () -> {
            h.assertTrue(h.getBlockState(primary).isAir(), "Removing one native half left a dangling core assembly");
            near(h, output(h, load), 0, .001, "Removed core left ghost mixed power");
            finish(h);
        });
    }

    private static TransformerCoreDevice core(GameTestHelper h, BlockPos pos) {
        return DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(pos), TransformerCoreDevice.class);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void netherTransformerRestoresEverySavedHistorySample(GameTestHelper h) {
        var state = ModdedBlocks.NETHER_TRANSFORMER.getDefaultState().setValue(NetherTransformerBlock.PART, 2);
        var cold = ModdedBlockEntities.NETHER_TRANSFORMER.get().create(h.absolutePos(new BlockPos(1, 2, 1)), state);
        var data = new CompoundTag();
        data.putUUID("Link", UUID.randomUUID());
        data.putBoolean("Secondary", true);
        var samples = new ListTag();
        samples.add(DoubleTag.valueOf(37.25));
        samples.add(DoubleTag.valueOf(-21.5));
        data.put("TrAvgDat", samples);
        data.putInt("AvgHead", 1);
        cold.loadWithComponents(data, h.getLevel().registryAccess());
        var saved = cold.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(samples.equals(saved.getList("TrAvgDat", CompoundTag.TAG_DOUBLE)),
                "Cold loading changed the native Nether transformer's saved voltage history: " + saved);
        h.assertTrue(saved.getInt("AvgHead") == 1 && saved.getUUID("Link").equals(data.getUUID("Link"))
                && saved.getBoolean("Secondary"), "Cold loading changed native link identity or history position");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeRegulatorStartsAtNeutralAndRegulatesMixedLoad(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1);
        var regulator = new BlockPos(4, 2, 1);
        var load = new BlockPos(4, 2, 4);
        place(h, source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        place(h, load, ModdedBlocks.CREATIVE_RESISTOR.get());
        h.setBlock(regulator.below(), Blocks.STONE);
        var item = CEEBlocks.VOLTAGE_REGULATOR.asStack();
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, item);
        var below = h.absolutePos(regulator.below());
        var result = item.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(below.getCenter().add(0, .5, 0), Direction.UP, below, false)));
        h.assertTrue(result.consumesAction() && item.isEmpty(), "Native regulator item placement failed");
        h.assertTrue(h.getBlockState(regulator).getValue(VoltageRegulatorBlock.TOP)
                && h.getBlockState(regulator).getValue(VoltageRegulatorBlock.BOTTOM),
                "Native single regulator assembly did not establish its ends");
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(100);
            ((ResistorBlockEntity) h.getBlockEntity(load)).setValue(1000);
            target(h, regulator, 100);
            h.assertTrue(device(h, regulator).steps == 0, "Fixture did not exercise native neutral ratio");
            wire(h, source, 0, regulator, 0, true);
            wire(h, source, 1, regulator, 2, false);
            wire(h, regulator, 1, load, 0, false);
            wire(h, regulator, 3, load, 1, true);
        });
        h.runAtTickTime(15, () -> {
            near(h, output(h, load), 100, .1, "Native neutral regulator must pass input voltage");
            target(h, regulator, 110);
        });
        h.runAtTickTime(65, () -> {
            near(h, output(h, load), 110, .8, "Native regulator step-up target");
            h.assertTrue(device(h, regulator).steps > 0, "Native step-up control never advanced");
            target(h, regulator, 90);
        });
        h.runAtTickTime(125, () -> {
            near(h, output(h, load), 90, .7, "Native regulator step-down target");
            h.assertTrue(device(h, regulator).steps < 0, "Native step-down control never advanced");
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(80);
        });
        h.runAtTickTime(175, () -> {
            near(h, output(h, load), 90, .7, "Native regulation after source change");
            near(h, device(h, regulator).lastVoltage, output(h, load), .02, "CEE result bridge output");
            finish(h);
        });
    }

    private static VoltageRegulatorDevice device(GameTestHelper h, BlockPos pos) {
        return DevicesSavedData.load(h.getLevel()).getDevice(h.absolutePos(pos), VoltageRegulatorDevice.class);
    }
    private static void target(GameTestHelper h, BlockPos pos, int volts) {
        ((SmartBlockEntity) h.getBlockEntity(pos)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(volts * 1000);
    }
    private static double output(GameTestHelper h, BlockPos pos) {
        var absolute = h.absolutePos(pos);
        return new BlockWireEndpoint(absolute, 0).getNode(h.getLevel()).getVoltage()
                - new BlockWireEndpoint(absolute, 1).getNode(h.getLevel()).getVoltage();
    }
}
