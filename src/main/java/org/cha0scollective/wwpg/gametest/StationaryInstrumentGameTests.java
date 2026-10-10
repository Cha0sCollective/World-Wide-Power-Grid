package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.config.ResistanceValues;
import org.patryk3211.powergrid.config.ThermalValues;
import org.patryk3211.powergrid.electricity.base.ThermalBehaviour;
import org.patryk3211.powergrid.electricity.carbonpile.CarbonPileCoilBlockEntity;
import org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock;
import org.patryk3211.powergrid.electricity.heater.HeaterBlockEntity;
import org.patryk3211.powergrid.equipment.thermometer.ThermometerBlock;
import org.patryk3211.powergrid.equipment.thermometer.ThermometerBlockEntity;
import org.patryk3211.powergrid.kinetics.plotter.PlotterBlock;
import org.patryk3211.powergrid.kinetics.plotter.PlotterBlockEntity;

import static org.cha0scollective.wwpg.gametest.StationaryEquipmentGameTests.*;

@GameTestHolder("wwpg_stationary")
@PrefixGameTestTemplate(false)
public final class StationaryInstrumentGameTests {
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void pgThermometerReadsCeePoweredPgHeat(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1);
        var heater = new BlockPos(4, 2, 1);
        var terminals = heater.east();
        var meter = heater.south();
        place(h, source, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, heater, ModdedBlocks.HEATING_COIL.get());
        h.setBlock(terminals, ModdedBlocks.DEVICE_CONNECTOR.getDefaultState().setValue(DeviceConnectorBlock.FACING, Direction.WEST));
        h.setBlock(meter, ModdedBlocks.THERMOMETER.getDefaultState().setValue(ThermometerBlock.FACING, Direction.NORTH));
        double[] maximum = {0};
        h.runAtTickTime(5, () -> {
            double resistance = ((HeaterBlockEntity) h.getBlockEntity(heater)).resistance();
            source(h, source, Math.sqrt(resistance * ThermalValues.getPower(ModdedBlocks.HEATING_COIL.get()) * .5));
            wire(h, source, 1, terminals, 0, true);
            wire(h, source, 0, terminals, 1, false);
        });
        h.runAtTickTime(80, () -> {
            var be = (ThermometerBlockEntity) h.getBlockEntity(meter);
            var thermal = ((SmartBlockEntity) h.getBlockEntity(heater)).getBehaviour(ThermalBehaviour.TYPE);
            near(h, be.temperature(), thermal.getTemperature(), .001, "Native Celsius thermometer reading");
            h.assertTrue(be.temperature() > 100 && be.redstoneOutput > 0 && be.dialState > 0,
                    "Mixed power did not produce a visible thermal reading");
            maximum[0] = be.maxTemperature;
            var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
            be.maxTemperature = 0;
            be.loadWithComponents(saved, h.getLevel().registryAccess());
            near(h, be.maxTemperature, maximum[0], .001, "Saved maximum temperature");
            source(h, source, 0);
        });
        h.runAtTickTime(120, () -> {
            var be = (ThermometerBlockEntity) h.getBlockEntity(meter);
            h.assertTrue(be.temperature() < maximum[0] && be.maxTemperature >= maximum[0],
                    "Cooling lost the native maximum-temperature indication");
            be.resetMax();
            near(h, be.maxTemperature, be.temperature(), .001, "Native maximum reset");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void nativePlotterRecordsMixedWaveform(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1);
        var plotter = new BlockPos(4, 2, 1);
        var motor = plotter.east();
        place(h, source, CEEBlocks.CREATIVE_BATTERY.get());
        h.setBlock(plotter.below(), Blocks.STONE);
        h.setBlock(plotter, ModdedBlocks.PLOTTER.getDefaultState().setValue(PlotterBlock.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.WEST));
        h.runAtTickTime(5, () -> {
            ((SmartBlockEntity) h.getBlockEntity(motor)).getBehaviour(ScrollValueBehaviour.TYPE).setValue(128);
            source(h, source, 1);
            wire(h, source, 1, plotter, 0, false);
            wire(h, source, 0, plotter, 1, true);
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(Math.abs(((PlotterBlockEntity) h.getBlockEntity(plotter)).getAnimationSpeed()) == 128,
                    "Plotter lacks native shaft drive");
            assertSample(h, plotter, .5);
            source(h, source, -1);
        });
        h.runAtTickTime(55, () -> {
            assertSample(h, plotter, -.5);
            var be = h.getBlockEntity(plotter);
            var saved = be.saveWithoutMetadata(h.getLevel().registryAccess());
            be.loadWithComponents(saved, h.getLevel().registryAccess());
            h.assertTrue(saved.getList("Samples", Tag.TAG_FLOAT).equals(be.saveWithoutMetadata(h.getLevel().registryAccess())
                    .getList("Samples", Tag.TAG_FLOAT)), "Native plotter lost its saved trace");
            h.setBlock(motor, Blocks.AIR);
        });
        h.runAtTickTime(75, () -> {
            h.assertTrue(((PlotterBlockEntity) h.getBlockEntity(plotter)).getAnimationSpeed() == 0,
                    "Unpowered shaft continued recording");
            finish(h);
        });
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void ceeControlsNativeCarbonPileResistance(GameTestHelper h) {
        var control = new BlockPos(1, 2, 1);
        var supply = new BlockPos(1, 2, 4);
        var coil = new BlockPos(4, 2, 1);
        var load = new BlockPos(6, 2, 4);
        place(h, control, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, supply, CEEBlocks.CREATIVE_BATTERY.get());
        place(h, coil, ModdedBlocks.CARBON_PILE_COIL.get());
        place(h, load, CEEBlocks.CREATIVE_RESISTOR.get());
        // Coal blocks are assembled by native neighbor handling, as during play.
        h.setBlock(coil.above(), Blocks.COAL_BLOCK);
        double[] initial = {0};
        h.runAtTickTime(5, () -> {
            h.assertTrue(h.getBlockState(coil.above()).is(ModdedBlocks.CARBON_PILE.get()),
                    "Native coal-to-carbon-pile assembly failed");
            source(h, control, 0);
            source(h, supply, 10);
            resistor(h, load, 1000);
            wire(h, control, 1, coil, 0, true);
            wire(h, control, 0, coil, 1, false);
            wire(h, supply, 1, coil, 2, false);
            wire(h, coil, 3, load, 0, true);
            wire(h, supply, 0, load, 1, false);
        });
        h.runAtTickTime(25, () -> {
            var be = (CarbonPileCoilBlockEntity) h.getBlockEntity(coil);
            initial[0] = 1 / be.getPileWire().conductance();
            near(h, initial[0], ResistanceValues.get(ModdedBlocks.CARBON_PILE.get()), .001,
                    "Native assembled pile resistance");
            source(h, control, be.resistance() * .4);
        });
        h.runAtTickTime(55, () -> {
            var be = (CarbonPileCoilBlockEntity) h.getBlockEntity(coil);
            h.assertTrue(1 / be.getPileWire().conductance() > initial[0] * 1.5 && ceeVoltage(h, load) > 0,
                    "CEE control current failed to regulate the native pile");
            be.setTrim(.5f);
            var tag = be.saveWithoutMetadata(h.getLevel().registryAccess());
            be.loadWithComponents(tag, h.getLevel().registryAccess());
            near(h, be.getTrim(), .5, .00001, "Saved carbon-pile trim");
            h.setBlock(coil.above(), Blocks.AIR);
        });
        h.runAtTickTime(75, () -> {
            near(h, ceeVoltage(h, load), 0, .001, "Removing assembled carbon pile opens its circuit");
            finish(h);
        });
    }

    private static void assertSample(GameTestHelper h, BlockPos plotter, double expected) {
        var samples = h.getBlockEntity(plotter).saveWithoutMetadata(h.getLevel().registryAccess()).getList("Samples", Tag.TAG_FLOAT);
        boolean found = false;
        for (int i = 0; i < samples.size(); i++) if (Math.abs(samples.getFloat(i) - expected) < .001) found = true;
        h.assertTrue(found, "Native plotter failed to record signed normalized voltage " + expected);
    }
}
