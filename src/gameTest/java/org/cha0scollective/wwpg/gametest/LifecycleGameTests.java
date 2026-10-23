package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEWireTypes;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.Bridges;

/** Runs with the actual published jars and the actual server tick hooks. */
@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class LifecycleGameTests {
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void ceeSourceAndLoadUsePg(GameTestHelper helper) {
        var sourceRelative = new BlockPos(1, 2, 1);
        var loadRelative = new BlockPos(4, 2, 1);
        helper.setBlock(sourceRelative.below(), Blocks.STONE);
        helper.setBlock(loadRelative.below(), Blocks.STONE);
        helper.setBlock(sourceRelative, CEEBlocks.CREATIVE_BATTERY.get());
        helper.setBlock(loadRelative, CEEBlocks.CREATIVE_RESISTOR.get());
        var level = helper.getLevel();
        var sourcePos = helper.absolutePos(sourceRelative);
        var loadPos = helper.absolutePos(loadRelative);
        helper.runAtTickTime(5, () -> {
            var devices = DevicesSavedData.load(level);
            devices.getDevice(sourcePos, CreativeBatteryDevice.class).voltage = 10;
            devices.getDevice(loadPos, ResistorDevice.class).properties = ElectricalProperties.resistor(9);
            var wires = InfrastructureSavedData.load(level);
            wires.connect(new InWorldNode(1, sourcePos), new InWorldNode(0, loadPos), CEEWireTypes.STANDARD.get());
            wires.connect(new InWorldNode(0, sourcePos), new InWorldNode(1, loadPos), CEEWireTypes.STANDARD.get());
        });
        final Object[][] stamps = new Object[1][];
        helper.runAtTickTime(15, () -> {
            checkLoad(helper, loadPos, 10);
            stamps[0] = identities(helper, sourcePos, loadPos);
            DevicesSavedData.load(level).getDevice(sourcePos, CreativeBatteryDevice.class).voltage = 20;
        });
        helper.runAtTickTime(25, () -> {
            checkLoad(helper, loadPos, 20);
            var now = identities(helper, sourcePos, loadPos);
            for (int i = 0; i < now.length; ++i)
                helper.assertTrue(stamps[0][i] != null && stamps[0][i] == now[i], "Changing voltage rebuilt PG object " + i);
            helper.setBlock(sourceRelative, Blocks.AIR);
        });
        helper.runAtTickTime(35, () -> {
            var results = InfrastructureSavedData.load(level).ticker.lastResults;
            helper.assertTrue(Math.abs(results.getVoltageAt(loadPos, 0, 1)) < 1e-6, "Source removal left ghost power");
            helper.succeed();
        });
    }

    private static Object[] identities(GameTestHelper h, BlockPos source, BlockPos load) {
        var bridge = Bridges.get(h.getLevel());
        var s0 = new InWorldNode(0, source); var s1 = new InWorldNode(1, source);
        var l0 = new InWorldNode(0, load); var l1 = new InWorldNode(1, load);
        return new Object[] {bridge.branchIdentity(s0, s1), bridge.branchIdentity(l0, l1),
                bridge.branchIdentity(s1, l0), bridge.branchIdentity(s0, l1)};
    }

    private static void checkLoad(GameTestHelper helper, BlockPos pos, double expectedVoltage) {
        var results = InfrastructureSavedData.load(helper.getLevel()).ticker.lastResults;
        helper.assertTrue(results != null, "CEE received no PG results");
        double voltage = results.getVoltageAt(pos, 0, 1);
        helper.assertTrue(Math.abs(voltage - expectedVoltage) < 0.15, "Incorrect load voltage: " + voltage);
        helper.assertTrue(Math.abs(results.getCurrentThrough(pos, 0, 1) - voltage / 9) < 1e-4, "Incorrect bridged current");
    }
}
