package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.content.electronic_components.resistor.ResistorDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class WiringGameTests {
    @GameTest(template = "empty", timeoutTicks = 80) public static void ceeToPgWithPgWire(GameTestHelper h) { circuit(h, true, false, true); }
    @GameTest(template = "empty", timeoutTicks = 80) public static void pgToCeeWithPgWire(GameTestHelper h) { circuit(h, false, true, true); }
    @GameTest(template = "empty", timeoutTicks = 80) public static void ceeToPgWithCeeSpool(GameTestHelper h) { circuit(h, true, false, false); }
    @GameTest(template = "empty", timeoutTicks = 80) public static void pgToCeeWithCeeSpool(GameTestHelper h) { circuit(h, false, true, false); }
    @GameTest(template = "empty", timeoutTicks = 80) public static void ceeToCeeWithPgWire(GameTestHelper h) { circuit(h, true, true, true); }
    @GameTest(template = "empty", timeoutTicks = 80) public static void pgToPgWithCeeSpool(GameTestHelper h) { circuit(h, false, false, false); }

    private static void circuit(GameTestHelper h, boolean ceeSource, boolean ceeLoad, boolean pgWire) {
        var a = new BlockPos(1, 2, 1); var b = new BlockPos(4, 2, 1);
        h.setBlock(a.below(), Blocks.STONE); h.setBlock(b.below(), Blocks.STONE);
        h.setBlock(a, ceeSource ? CEEBlocks.CREATIVE_BATTERY.get() : ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        h.setBlock(b, ceeLoad ? CEEBlocks.CREATIVE_RESISTOR.get() : ModdedBlocks.CREATIVE_RESISTOR.get());
        var source = h.absolutePos(a); var load = h.absolutePos(b); var level = h.getLevel();
        h.runAtTickTime(5, () -> {
            if (ceeSource) DevicesSavedData.load(level).getDevice(source, CreativeBatteryDevice.class).voltage = 10;
            else ((CreativeSourceBlockEntity) level.getBlockEntity(source)).setValue(10);
            if (ceeLoad) DevicesSavedData.load(level).getDevice(load, ResistorDevice.class).properties = ElectricalProperties.resistor(9);
            else ((ResistorBlockEntity) level.getBlockEntity(load)).setValue(9);
            connect(h, source, ceeSource ? 1 : 0, load, 0, pgWire);
            connect(h, source, ceeSource ? 0 : 1, load, 1, pgWire);
        });
        h.runAtTickTime(25, () -> {
            double v = ceeLoad ? InfrastructureSavedData.load(level).ticker.lastResults.getVoltageAt(load, 0, 1)
                    : new BlockWireEndpoint(load, 0).getNode(level).getVoltage() - new BlockWireEndpoint(load, 1).getNode(level).getVoltage();
            h.assertTrue(Math.abs(v - 10) < 0.2, "Mixed circuit voltage was " + v);
            h.succeed();
        });
    }

    public static void connect(GameTestHelper h, BlockPos a, int terminalA, BlockPos b, int terminalB, boolean pgWire) {
        connect(h, h.getLevel(), a, terminalA, b, terminalB, pgWire);
    }

    public static void connect(GameTestHelper h, net.minecraft.server.level.ServerLevel level,
                               BlockPos a, int terminalA, BlockPos b, int terminalB, boolean pgWire) {
        var player = new net.minecraft.world.entity.player.Player(level, a, 0,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WWPG-wire-test")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
        };
        player.setItemInHand(InteractionHand.MAIN_HAND, pgWire ? ModdedItems.WIRE.asStack(64) : CEEItems.WIRE_SPOOL.asStack());
        for (var endpoint : new BlockWireEndpoint[] {new BlockWireEndpoint(a, terminalA), new BlockWireEndpoint(b, terminalB)}) {
            var pos = endpoint.getPos();
            var click = pgWire ? endpoint.getExactPosition(level) : new InWorldNode(endpoint.getTerminal(), pos).getPosition(level);
            h.assertTrue(click != null, "Terminal " + endpoint.getTerminal() + " has no position in "
                    + level.dimension().location() + " at " + pos + ": " + level.getBlockState(pos)
                    + " using " + (pgWire ? "PG cord" : "CEE spool"));
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(click, Direction.UP, pos, false));
            var result = pgWire ? IElectric.getAt(level, pos).onWire(level.getBlockState(pos), context)
                    : player.getMainHandItem().getItem().useOn(context);
            h.assertTrue(result.consumesAction(), "Native wire placement failed at " + pos + " terminal " + endpoint.getTerminal());
        }
    }
}
