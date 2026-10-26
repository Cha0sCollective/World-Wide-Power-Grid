package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class TopologyGameTests {
    @GameTest(template = "empty", timeoutTicks = 90) public static void mixedBranchesLoopCutReconnectAndRemoval(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var b = new BlockPos(4, 2, 1); var c = new BlockPos(4, 2, 4);
        for (var pos : new BlockPos[] {a, b, c}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(b, CEEBlocks.CREATIVE_RESISTOR.get()); h.setBlock(c, ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var cee = h.absolutePos(b); var pg = h.absolutePos(c); var level = h.getLevel();
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            DevicesSavedData.load(level).getDevice(cee, ResistorDevice.class).properties = ElectricalProperties.resistor(10);
            ((ResistorBlockEntity) level.getBlockEntity(pg)).setValue(10);
            for (int port = 0; port < 2; ++port) {
                WiringGameTests.connect(h, source, port, cee, port, false);
                WiringGameTests.connect(h, source, port, pg, port, false);
                WiringGameTests.connect(h, cee, port, pg, port, true);
            }
        });
        h.runAtTickTime(20, () -> {
            voltage(h, cee, 10);
            InfrastructureSavedData.load(level).removeConnection(new InWorldNodeConnection(new InWorldNode(0, source), new InWorldNode(0, cee)));
        });
        h.runAtTickTime(30, () -> {
            voltage(h, cee, 10); // Alternate path through the PG wires keeps this loop powered.
            WiringGameTests.connect(h, source, 0, cee, 0, false);
            h.setBlock(a, Blocks.AIR);
        });
        h.runAtTickTime(40, () -> {
            voltage(h, cee, 0);
            var infrastructure = InfrastructureSavedData.load(level);
            h.assertTrue(!infrastructure.hasNode(new InWorldNode(0, source)) && !infrastructure.hasNode(new InWorldNode(1, source)),
                    "Removing the PG source left CEE terminal mappings behind");
            h.setBlock(a, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        });
        h.runAtTickTime(45, () -> {
            ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(20);
            WiringGameTests.connect(h, source, 0, cee, 0, false);
            WiringGameTests.connect(h, source, 1, cee, 1, false);
        });
        h.runAtTickTime(60, () -> {
            voltage(h, cee, 20);
            // Replace a fully loaded CEE device within one tick. Its old PG wires must be cut.
            h.setBlock(b, Blocks.AIR); h.setBlock(b, CEEBlocks.CREATIVE_RESISTOR.get());
        });
        h.runAtTickTime(65, () -> {
            voltage(h, cee, 0);
            for (int port = 0; port < 2; ++port) WiringGameTests.connect(h, source, port, cee, port, true);
        });
        h.runAtTickTime(80, () -> { voltage(h, cee, 20); DynamicGameTests.audit(h); h.succeed(); });
    }

    private static void voltage(GameTestHelper h, BlockPos pos, double expected) {
        var results = InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
        double actual = results.getVoltageAt(pos, 0, 1);
        h.assertTrue(Math.abs(actual - expected) < 0.15, "Topology change: expected " + expected + " V, got " + actual);
    }
}
