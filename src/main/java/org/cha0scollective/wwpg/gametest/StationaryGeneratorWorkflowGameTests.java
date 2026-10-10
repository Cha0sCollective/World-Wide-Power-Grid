package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.kinetics.generator.inductionrotor.CommutatorBlockEntity;
import org.patryk3211.powergrid.kinetics.generator.housing.GeneratorHousing;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlock;
import org.patryk3211.powergrid.kinetics.generator.winding.WindingBlockEntity;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

@GameTestHolder("wwpg_distribution")
@PrefixGameTestTemplate(false)
public final class StationaryGeneratorWorkflowGameTests {
    @GameTest(template = "empty", timeoutTicks = 360)
    public static void nativeLargeGeneratorUsesInstalledCoilAndMixedExcitation(GameTestHelper h) { generator(h, false); }

    @GameTest(template = "empty", timeoutTicks = 360)
    public static void nativeVerticalGeneratorUsesInstalledCoilAndMixedExcitation(GameTestHelper h) { generator(h, true); }

    private static void generator(GameTestHelper h, boolean vertical) {
        var brush = new BlockPos(3, 2, 2);
        var axis = vertical ? Direction.Axis.Y : Direction.Axis.Z;
        var along = vertical ? Direction.UP : Direction.SOUTH;
        var rotor = brush.relative(along);
        var clutch = rotor.relative(along);
        var drive = clutch.relative(along);
        var coil = vertical ? brush.east(2) : brush.above(2);
        var connector = vertical ? coil.east() : coil.above();
        var field = new BlockPos(1, 2, 1);
        var load = new BlockPos(6, 2, 6);
        h.setBlock(brush, (vertical ? ModdedBlocks.GENERATOR_VERTICAL_COMMUTATOR : ModdedBlocks.GENERATOR_COMMUTATOR)
                .getDefaultState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(rotor, ModdedBlocks.GENERATOR_LARGE_INDUCTION_ROTOR.getDefaultState().setValue(BlockStateProperties.AXIS, axis));
        h.setBlock(clutch, ModdedBlocks.GENERATOR_CLUTCH.getDefaultState().setValue(BlockStateProperties.FACING, along));
        h.setBlock(drive, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, along.getOpposite()));
        installCoil(h, coil, along, vertical ? Direction.Axis.X : Direction.Axis.Y);
        h.setBlock(connector, ModdedBlocks.DEVICE_CONNECTOR.getDefaultState()
                .setValue(DeviceConnectorBlock.FACING, vertical ? Direction.WEST : Direction.DOWN));
        place(h, field, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, load, CEEBlocks.CREATIVE_RESISTOR.get());
        double[] first = {0};
        h.runAtTickTime(10, () -> {
            source(h, field, 5);
            resistor(h, load, 1000);
            speed(h, drive, 64);
            wire(h, field, 1, connector, 0, true);
            wire(h, field, 0, connector, 1, false);
            wire(h, brush, 0, load, 0, false);
            wire(h, brush, 1, load, 1, true);
        });
        h.runAtTickTime(130, () -> {
            first[0] = ceeVoltage(h, load);
            var generator = (CommutatorBlockEntity) h.getBlockEntity(brush);
            h.assertTrue(first[0] > .1 && generator.getPower() > 0,
                    "Native generator assembly did not power CEE: V=" + first[0] + ", P=" + generator.getPower());
            h.assertTrue(((WindingBlockEntity) h.getBlockEntity(coil)).windingCurrent() > 0,
                    "Installed native winding was not excited by the mixed supply");
            speed(h, drive, 128);
        });
        h.runAtTickTime(240, () -> {
            h.assertTrue(ceeVoltage(h, load) > first[0] * 1.7, "Generator ignored the changed native drive speed");
            source(h, field, 0);
        });
        h.runAtTickTime(330, () -> {
            h.assertTrue(Math.abs(ceeVoltage(h, load)) < first[0] * .05,
                    "Generator retained output after native excitation was removed");
            h.setBlock(drive, Blocks.AIR);
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void nativeHorizontalHousingJoinsAndSeparatesExcitedWindings(GameTestHelper h) { housing(h, false); }

    @GameTest(template = "empty", timeoutTicks = 240)
    public static void nativeVerticalHousingJoinsAndSeparatesExcitedWindings(GameTestHelper h) { housing(h, true); }

    private static void housing(GameTestHelper h, boolean vertical) {
        var first = new BlockPos(4, 2, 2);
        var direction = vertical ? Direction.UP : Direction.SOUTH;
        var housing = vertical ? first.north() : first.west();
        var second = vertical ? housing.west() : housing.above();
        installCoil(h, first, direction, vertical ? Direction.Axis.X : Direction.Axis.Y);
        installCoil(h, second, direction, vertical ? Direction.Axis.Z : Direction.Axis.X);
        h.setBlock(housing, (vertical ? ModdedBlocks.VERTICAL_GENERATOR_HOUSING : ModdedBlocks.GENERATOR_HOUSING)
                .getDefaultState().setValue(GeneratorHousing.HORIZONTAL_FACING, vertical ? Direction.WEST : Direction.EAST));
        if (!vertical) h.setBlock(housing, h.getBlockState(housing).setValue(GeneratorHousing.UP, true));
        var connector = vertical ? first.east() : first.above();
        h.setBlock(connector, ModdedBlocks.DEVICE_CONNECTOR.getDefaultState()
                .setValue(DeviceConnectorBlock.FACING, vertical ? Direction.WEST : Direction.DOWN));
        var supply = new BlockPos(1, 2, 6);
        place(h, supply, CEEBlocks.CREATIVE_BATTERY.get());
        h.runAtTickTime(10, () -> {
            source(h, supply, 5);
            wire(h, supply, 1, connector, 0, true);
            wire(h, supply, 0, connector, 1, false);
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(field(h, first) > .001 && field(h, second) > .001,
                    "Native housing failed to excite both winding assemblies: " + field(h, first) + ", " + field(h, second));
            h.assertTrue(h.getLevel().getBlockEntity(h.absolutePos(housing)) == null,
                    "Structural native housing unexpectedly acquired a compatibility electrical device");
            h.setBlock(housing, Blocks.AIR);
        });
        h.runAtTickTime(220, () -> {
            h.assertTrue(field(h, first) > .001, "Housing removal disconnected the directly supplied winding");
            near(h, ((WindingBlockEntity) h.getBlockEntity(second)).windingCurrent(), 0, .0001,
                    "Removed housing left electrical excitation in the remote winding");
            near(h, field(h, second), .001, .00001, "Native unexcited winding remanent field");
            finish(h);
        });
    }

    private static float field(GameTestHelper h, BlockPos coil) {
        return ((WindingBlockEntity) h.getBlockEntity(coil)).fieldStrengthCalc().get();
    }
    private static void speed(GameTestHelper h, BlockPos drive, int rpm) {
        ((SmartBlockEntity) h.getBlockEntity(drive)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(rpm);
    }
    static void installCoil(GameTestHelper h, BlockPos start, Direction along, Direction.Axis shaftAxis) {
        var end = start.relative(along, 2);
        h.setBlock(start, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, shaftAxis));
        h.setBlock(end, AllBlocks.SHAFT.getDefaultState().setValue(BlockStateProperties.AXIS, shaftAxis));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var stack = ModdedItems.COPPER_COIL.asStack(3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        for (var position : new BlockPos[] {start, end}) {
            var absolute = h.absolutePos(position);
            var result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(absolute.getCenter(), Direction.UP, absolute, false)));
            h.assertTrue(result.consumesAction(), "Native winding item rejected shaft selection at " + position);
        }
        h.assertTrue(stack.isEmpty(), "Native winding installation did not consume three coils");
        for (int part = 0; part < 3; part++) {
            var state = h.getBlockState(start.relative(along, part));
            h.assertTrue(ModdedBlocks.WINDING.has(state) && state.getValue(WindingBlock.PART) == part,
                    "Native winding installation produced an incorrect segment " + part);
        }
    }
}
