package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class FactoryTransportGameTests {
    @GameTest(template = "empty", timeoutTicks = 280) public static void pgPowerPumpsWaterThroughCee(GameTestHelper h) { pump(h, true); }
    @GameTest(template = "empty", timeoutTicks = 280) public static void ceePowerPumpsWaterThroughPg(GameTestHelper h) { pump(h, false); }
    private static void pump(GameTestHelper h, boolean ceePump) {
        var a = new BlockPos(1, 2, 1); var b = new BlockPos(3, 2, 3); var input = new BlockPos(1, 2, 3); var output = new BlockPos(5, 2, 3);
        for (var p : new BlockPos[] {a, b, input, output}) h.setBlock(p.below(), Blocks.STONE);
        h.setBlock(a, ceePump ? ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get() : CEEBlocks.CREATIVE_BATTERY.get());
        h.setBlock(b, (ceePump ? CEEBlocks.ELECTRIC_PUMP.getDefaultState() : ModdedBlocks.ELECTRIC_PUMP.getDefaultState()).setValue(PumpBlock.FACING, Direction.EAST));
        h.setBlock(input, AllBlocks.FLUID_TANK.get()); h.setBlock(output, AllBlocks.FLUID_TANK.get());
        for (var p : new BlockPos[] {input.east(), output.west()})
            h.setBlock(p, AllBlocks.FLUID_PIPE.getDefaultState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.DOWN, false)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.UP, false)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.NORTH, false)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SOUTH, false)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST, true)
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST, true));
        var source = h.absolutePos(a); var pump = h.absolutePos(b);
        var in = h.absolutePos(input); var out = h.absolutePos(output); int[] amount = new int[1];
        var connector = h.absolutePos(b.above());
        if (!ceePump) h.setBlock(b.above(), ModdedBlocks.DEVICE_CONNECTOR.getDefaultState()
                .setValue(org.patryk3211.powergrid.electricity.deviceconnector.DeviceConnectorBlock.FACING, Direction.DOWN));
        h.runAtTickTime(5, () -> {
            var handler = tank(h, in); h.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE) == 8000, "Input tank did not accept water");
            if (ceePump) ((CreativeSourceBlockEntity) h.getLevel().getBlockEntity(source)).setValue(300);
            else DevicesSavedData.load(h.getLevel()).getDevice(source, CreativeBatteryDevice.class).voltage = 24;
            if (ceePump) for (int i = 0; i < 2; ++i) WiringGameTests.connect(h, source, i, pump, i, false);
            else for (int i = 0; i < 2; ++i) WiringGameTests.connect(h, source, 1 - i, connector, i, false);
        });
        h.runAtTickTime(150, () -> {
            amount[0] = tank(h, out).getFluidInTank(0).getAmount();
            h.assertTrue(amount[0] > 0 && tank(h, in).getFluidInTank(0).getAmount() < 8000, "Mixed electrical power produced no real fluid transport");
            h.assertTrue(amount[0] + tank(h, in).getFluidInTank(0).getAmount() == 8000,
                    "Pump water total: input=" + tank(h, in).getFluidInTank(0).getAmount() + ", output=" + amount[0]
                            + ", input pipe=" + h.getLevel().getBlockState(in.east())
                            + ", output pipe=" + h.getLevel().getBlockState(out.west()));
            if (ceePump) ((CreativeSourceBlockEntity) h.getLevel().getBlockEntity(source)).setValue(0);
            else DevicesSavedData.load(h.getLevel()).getDevice(source, CreativeBatteryDevice.class).voltage = 0;
        });
        h.runAtTickTime(190, () -> amount[0] = tank(h, out).getFluidInTank(0).getAmount());
        h.runAtTickTime(220, () -> {
            h.assertTrue(tank(h, out).getFluidInTank(0).getAmount() == amount[0], "Pump continued fluid transport after power removal");
            DynamicGameTests.audit(h); h.succeed();
        });
    }
    private static IFluidHandler tank(GameTestHelper h, BlockPos pos) {
        var handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, Direction.EAST);
        h.assertTrue(handler != null, "Tank has no native fluid capability"); return handler;
    }
}
