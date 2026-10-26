package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.events.AddToElectricGraphEvent;
import com.george_vi.electroenergetics.foundation.nodes.AttachedNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.BridgeSimulationResults;

import java.util.function.Consumer;

/** The healthy shortcut must fall back when any native descriptor lacks a supported identity. */
@GameTestHolder("wwpg_loading")
@PrefixGameTestTemplate(false)
public final class HealthyResultGuardGameTests {
    @BeforeBatch(batch = "wwpg_result_guard")
    public static void configure(ServerLevel level) { DynamicGameTests.substeps(level); }

    @GameTest(template = "empty", batch = "wwpg_result_guard", timeoutTicks = 140)
    public static void unmappedDescriptorsRemainUnavailableBesideHealthyCircuits(GameTestHelper h) {
        var source = new BlockPos(1, 2, 1); var load = new BlockPos(4, 2, 1);
        for (var pos : new BlockPos[]{source, load}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(source, CEEBlocks.CREATIVE_BATTERY.get());
        h.setBlock(load, CEEBlocks.CREATIVE_RESISTOR.get());
        var a = h.absolutePos(source); var b = h.absolutePos(load);
        String owner = "WWPG-unmapped:" + a;
        var unsupported = new UnmappedNode(1, owner); var peer = new UnmappedNode(2, owner);
        boolean[] active = {false};
        Consumer<AddToElectricGraphEvent> listener = event -> {
            if (active[0] && event.level == h.getLevel())
                event.builder.connect(unsupported, peer, ElectricalProperties.resistor(10));
        };
        Runnable close = () -> { active[0] = false; NeoForge.EVENT_BUS.unregister(listener); };
        h.runAtTickTime(130, close);
        h.startSequence().thenIdle(5).thenExecute(() -> {
            DevicesSavedData.load(h.getLevel()).getDevice(a, CreativeBatteryDevice.class).voltage = 20;
            DevicesSavedData.load(h.getLevel()).getDevice(b, ResistorDevice.class).properties = ElectricalProperties.resistor(100);
            WiringGameTests.connect(h, a, 1, b, 0, true);
            WiringGameTests.connect(h, a, 0, b, 1, true);
        }).thenIdle(25).thenExecute(() -> {
            var results = results(h);
            h.assertTrue(results.allAvailable(), "Baseline was not a fully mapped healthy frame");
            BoardComponentGameTests.near(h, results.getVoltageAt(b, 0, 1), 20, .1, "Real CEE load before unsupported descriptor");
            NeoForge.EVENT_BUS.addListener(AddToElectricGraphEvent.class, listener); active[0] = true;
        }).thenIdle(3).thenExecute(() -> {
            try {
                var results = results(h);
                h.assertTrue(!results.allAvailable() && !results.available(unsupported)
                                && Double.isNaN(results.getVoltageAt(unsupported)),
                        "Healthy shortcut exposed an unmapped native descriptor");
                BoardComponentGameTests.near(h, results.getVoltageAt(b, 0, 1), 20, .1, "Unrelated CEE load was lost");
            } finally { close.run(); }
        }).thenIdle(3).thenExecute(() -> {
            h.assertTrue(results(h).allAvailable(), "Next frame retained an obsolete unavailable descriptor");
            BoardComponentGameTests.near(h, results(h).getVoltageAt(b, 0, 1), 20, .1, "Healthy CEE load after recovery");
            DynamicGameTests.audit(h);
        }).thenSucceed();
    }

    private static BridgeSimulationResults results(GameTestHelper h) {
        var value = InfrastructureSavedData.load(h.getLevel()).ticker.lastResults;
        h.assertTrue(value instanceof BridgeSimulationResults, "No current native-compatible result");
        return (BridgeSimulationResults) value;
    }

    private static final class UnmappedNode extends AttachedNode {
        UnmappedNode(int id, String owner) { super(id, owner); }
    }
}
