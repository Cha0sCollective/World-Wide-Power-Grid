package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.rotor.AlternatorBrushesBlock;
import com.george_vi.electroenergetics.content.rotor.AlternatorBrushesDevice;
import com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlock;
import com.george_vi.electroenergetics.content.rotor.AlternatorRotorBlockEntity;
import com.george_vi.electroenergetics.content.rotor.StatorBlock;
import com.george_vi.electroenergetics.content.rotor.ThreePhaseAlternatorBrushesDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class GenerationGameTests {
    private static final BlockPos BRUSH = new BlockPos(2, 2, 2);
    private static final BlockPos ROTOR = new BlockPos(3, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(4, 2, 2);

    @GameTest(template = "empty", timeoutTicks = 110) public static void assembledCeeAlternatorPowersPgLoad(GameTestHelper h) {
        assemble(h, false);
        var b = new BlockPos(2, 2, 5); h.setBlock(b.below(), Blocks.STONE); h.setBlock(b, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(BRUSH); var load = h.absolutePos(b); var level = h.getLevel(); double[] firstVoltage = new double[1];
        h.runAtTickTime(5, () -> {
            speed(h, 32); ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(1000);
            WiringGameTests.connect(h, source, 1, load, 0, false);
            WiringGameTests.connect(h, source, 0, load, 1, false);
        });
        h.runAtTickTime(35, () -> {
            var device = DevicesSavedData.load(level).getDevice(source, AlternatorBrushesDevice.class);
            firstVoltage[0] = voltage(h, load);
            h.assertTrue(device.stress > 0 && device.storedEnergy > 0, "Assembled alternator has no generated energy");
            h.assertTrue(Math.abs(((AlternatorRotorBlockEntity) level.getBlockEntity(h.absolutePos(ROTOR))).getSpeed()) == 32, "Create drive failed to rotate the CEE rotor");
            h.assertTrue(firstVoltage[0] > 40 && Math.abs(firstVoltage[0] - device.voltage) < 2, "PG load did not receive assembled alternator voltage: " + firstVoltage[0]);
            speed(h, 64);
        });
        h.runAtTickTime(60, () -> {
            h.assertTrue(voltage(h, load) > firstVoltage[0] * 1.9, "Alternator output did not respond to mechanical speed");
            h.setBlock(DRIVE, Blocks.AIR);
        });
        h.runAtTickTime(85, () -> {
            h.assertTrue(Math.abs(voltage(h, load)) < 0.001, "Stopped alternator left the PG load powered");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 240) public static void assembledThreePhaseGenerationPreservesPhaseAndDirection(GameTestHelper h) {
        assemble(h, true);
        BlockPos[] loads = {new BlockPos(2, 2, 5), new BlockPos(4, 2, 5), new BlockPos(6, 2, 5)};
        for (var pos : loads) { h.setBlock(pos.below(), Blocks.STONE); h.setBlock(pos, ModdedBlocks.CREATIVE_RESISTOR.get()); }
        var source = h.absolutePos(BRUSH); var level = h.getLevel(); double[] ordering = new double[1];
        h.runAtTickTime(5, () -> {
            speed(h, 128);
            for (int i = 0; i < loads.length; ++i) {
                var load = h.absolutePos(loads[i]); ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(1000);
                WiringGameTests.connect(h, source, i + 1, load, 0, true);
                WiringGameTests.connect(h, source, 0, load, 1, true);
            }
        });
        h.runAtTickTime(85, () -> {
            ordering[0] = phaseCheck(h, source);
            speed(h, -128);
        });
        h.runAtTickTime(220, () -> {
            double reverse = phaseCheck(h, source);
            h.assertTrue(reverse * ordering[0] < 0, "Reversing the mechanical drive did not reverse phase sequence");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    private static double phaseCheck(GameTestHelper h, BlockPos source) {
        var device = DevicesSavedData.load(h.getLevel()).getDevice(source, ThreePhaseAlternatorBrushesDevice.class);
        var results = InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
        var neutral = results.getVoltages(new InWorldNode(0, source)); double[][] phases = new double[3][];
        for (int p = 0; p < 3; ++p) {
            phases[p] = results.getVoltages(new InWorldNode(p + 1, source));
            double rms = results.getVoltageAt(source, p + 1, 0);
            h.assertTrue(rms > 100 && Math.abs(rms - device.voltage / Math.sqrt(2)) < 5, "Phase " + p + " RMS was " + rms);
            for (int i = 0; i < neutral.length; ++i) phases[p][i] -= neutral[i];
        }
        double cross = 0;
        for (int i = 0; i < neutral.length; ++i) {
            h.assertTrue(Math.abs(phases[0][i] + phases[1][i] + phases[2][i]) < 2, "Balanced phases lost their 120-degree relationship");
            int next = (i + 1) % neutral.length;
            cross += phases[0][i] * phases[1][next] - phases[1][i] * phases[0][next];
        }
        h.assertTrue(Math.abs(cross) > 1000, "Three-phase sequence could not be measured");
        return cross;
    }

    static void assemble(GameTestHelper h, boolean threePhase) {
        for (var pos : new BlockPos[] {BRUSH, ROTOR, DRIVE}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(BRUSH, (threePhase ? CEEBlocks.THREE_PHASE_ALTERNATOR_BRUSHES.getDefaultState() : CEEBlocks.ALTERNATOR_BRUSHES.getDefaultState())
                .setValue(AlternatorBrushesBlock.FACING, Direction.WEST));
        h.setBlock(ROTOR, CEEBlocks.ALTERNATOR_ROTOR.getDefaultState().setValue(AlternatorRotorBlock.AXIS, Direction.Axis.X));
        h.setBlock(ROTOR.above(), CEEBlocks.STATOR.getDefaultState().setValue(StatorBlock.FACING, Direction.DOWN).setValue(StatorBlock.ROLL, true));
        h.setBlock(DRIVE, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
    }
    static void speed(GameTestHelper h, int rpm) {
        var drive = (SmartBlockEntity) h.getBlockEntity(DRIVE);
        drive.getBehaviour(ScrollValueBehaviour.TYPE).setValue(rpm);
    }
    private static double voltage(GameTestHelper h, BlockPos pos) {
        return new BlockWireEndpoint(pos, 0).getNode(h.getLevel()).getVoltage() - new BlockWireEndpoint(pos, 1).getNode(h.getLevel()).getVoltage();
    }
}
