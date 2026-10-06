package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.circuits.components.ResistorComponent;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class BoardGameTests {
    @GameTest(template = "empty", timeoutTicks = 80) public static void ceePowersNonlinearBoardAndReconfiguration(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var b = new BlockPos(4, 2, 1);
        h.setBlock(a.below(), Blocks.STONE); h.setBlock(b.below(), Blocks.STONE);
        h.setBlock(a, CEEBlocks.CREATIVE_BATTERY.get()); h.setBlock(b, ModdedBlocks.CIRCUIT_BOARD.get());
        var source = h.absolutePos(a); var boardPos = h.absolutePos(b); var level = h.getLevel();
        h.runAtTickTime(5, () -> {
            DevicesSavedData.load(level).getDevice(source, CreativeBatteryDevice.class).voltage = 10;
            var board = (CircuitBoardBlockEntity) level.getBlockEntity(boardPos);
            board.setSchematic(diodeBoard(1000));
            h.assertTrue(board.getSchematic().findNodeBundles().size() == 3, "Board traces did not connect the three expected pad pairs");
            WiringGameTests.connect(h, source, 0, boardPos, 0, false);
            WiringGameTests.connect(h, source, 1, boardPos, 1, false);
        });
        h.runAtTickTime(20, () -> {
            double v = new BlockWireEndpoint(boardPos, 1).getNode(level).getVoltage() - new BlockWireEndpoint(boardPos, 0).getNode(level).getVoltage();
            h.assertTrue(Math.abs(v - 10) < 0.1, "Board input voltage was " + v);
            checkCurrent(h, source, 0.009, 0.010);
            ((CircuitBoardBlockEntity) level.getBlockEntity(boardPos)).setSchematic(diodeBoard(2000));
        });
        h.runAtTickTime(30, () -> {
            checkCurrent(h, source, 0.0044, 0.0050);
            DevicesSavedData.load(level).getDevice(source, CreativeBatteryDevice.class).voltage = -10;
        });
        h.runAtTickTime(40, () -> {
            checkCurrent(h, source, 0, 0.000001);
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    static CircuitSchematic diodeBoard(float resistance) {
        var schematic = new CircuitSchematic(); schematic.setName("WWPG diode acceptance");
        schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), 0, 5, null), 0, 5);
        schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), 13, 5, null), 13, 5);
        var resistor = new PlacedComponent(Components.RESISTOR.get(), 3, 5, null);
        resistor.set(ResistorComponent.RESISTANCE, resistance);
        schematic.placeComponent(resistor, 3, 5);
        schematic.placeComponent(new PlacedComponent(Components.DIODE.get(), 8, 5, null), 8, 5);
        schematic.front().addHorizontalLine(6, 1, 3);
        schematic.front().addHorizontalLine(6, 7, 8);
        schematic.front().addHorizontalLine(6, 12, 14);
        return schematic;
    }
    private static void checkCurrent(GameTestHelper h, BlockPos pos, double minimum, double maximum) {
        double amps = Math.abs(InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getCurrentThrough(pos, 0, 1));
        h.assertTrue(Double.isFinite(amps) && amps >= minimum && amps < maximum, "Board current outside [" + minimum + ", " + maximum + "): " + amps);
    }
}
