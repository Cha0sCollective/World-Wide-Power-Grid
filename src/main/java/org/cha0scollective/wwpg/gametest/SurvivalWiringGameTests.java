package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.*;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.foundation.nodes.*;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.resistor.ResistorBlockEntity;
import org.patryk3211.powergrid.electricity.wire.*;
import org.patryk3211.powergrid.electricity.wire.registry.WireRegistry;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class SurvivalWiringGameTests {
    @GameTest(template = "empty", timeoutTicks = 150)
    public static void pgWireTypesRetainCostResistanceAndPlayerCut(GameTestHelper h) {
        var source = h.absolutePos(new BlockPos(1, 2, 1)); var load = h.absolutePos(new BlockPos(5, 2, 1));
        place(h, source, load);
        var items = new Item[] {ModdedItems.WIRE.get(), ModdedItems.IRON_WIRE.get(), ModdedItems.GOLDEN_WIRE.get(), ModdedItems.INSULATED_COPPER_WIRE.get()};
        h.runAtTickTime(5, () -> prepare(h, source, load));
        for (int index = 0; index < items.length; ++index) {
            final int i = index;
            h.runAtTickTime(10 + i * 25, () -> {
                var player = h.makeMockPlayer(GameType.SURVIVAL); var stack = new ItemStack(items[i], 64);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                var a = new BlockWireEndpoint(source, 1); var b = new BlockWireEndpoint(load, 0);
                if (!stack.is(org.patryk3211.powergrid.collections.ModdedTags.Item.LIGHT_WIRES.tag)) {
                    h.assertTrue(!IElectric.getAt(h.getLevel(), source).onWire(h.getLevel().getBlockState(source), context(h, player, a, true)).consumesAction()
                            && stack.getCount() == 64, "Native incompatible wire rejection changed or consumed wire");
                    return;
                }
                for (var endpoint : new BlockWireEndpoint[] {a, b})
                    h.assertTrue(IElectric.getAt(h.getLevel(), endpoint.getPos()).onWire(h.getLevel().getBlockState(endpoint.getPos()), context(h, player, endpoint, true)).consumesAction(), "Survival PG wire placement failed: " + items[i] + " at " + endpoint + ", count=" + stack.getCount());
                var entry = WireRegistry.forItem(h.getLevel(), items[i]);
                int cost = Math.max(1, Math.round((float) a.getExactPosition(h.getLevel()).distanceTo(b.getExactPosition(h.getLevel())) * entry.itemsPerMeter()));
                h.assertTrue(stack.getCount() == 64 - cost, "Mixed terminals changed PG wire item cost");
                var wire = pgWire(h, a, load);
                BoardComponentGameTests.near(h, wire.getResistance(), entry.resistancePerItem() * cost, 0.000001, "PG wire resistance from consumed items");
                for (var endpoint : new BlockWireEndpoint[] {a, b}) IElectric.getAt(h.getLevel(), endpoint.getPos()).onWire(h.getLevel().getBlockState(endpoint.getPos()), context(h, player, endpoint, true));
                h.assertTrue(stack.getCount() == 64 - cost, "Duplicate PG connection consumed wire");
            });
            h.runAtTickTime(20 + i * 25, () -> {
                if (items[i] == ModdedItems.IRON_WIRE.get()) { BoardComponentGameTests.near(h, loadVoltage(h, load), 0, 0.00001, "Power after rejected native wire"); return; }
                h.assertTrue(loadVoltage(h, load) > 4.5, "PG wire type failed to carry mixed power");
                var player = h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND, ModdedItems.WIRE_CUTTER.asStack());
                h.assertTrue(pgWire(h, new BlockWireEndpoint(source, 1), load).interact(player, InteractionHand.MAIN_HAND).consumesAction(), "Native PG cutter failed");
            });
            h.runAtTickTime(27 + i * 25, () -> BoardComponentGameTests.near(h, loadVoltage(h, load), 0, 0.00001, "Power after player wire cut"));
        }
        h.runAtTickTime(115, () -> { DynamicGameTests.audit(h); h.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void ceeSpoolTypesRetainCostAndPlayerRecoveryOnPg(GameTestHelper h) {
        var source = h.absolutePos(new BlockPos(1, 2, 1)); var load = h.absolutePos(new BlockPos(5, 2, 1));
        place(h, source, load);
        var items = new Item[] {CEEItems.WIRE_SPOOL.get(), CEEItems.HEAVILY_INSULATED_WIRE_SPOOL.get(), CEEItems.COPPER_WIRE_SPOOL.get(), CEEItems.ELECTRUM_WIRE_SPOOL.get(), CEEItems.IRON_WIRE_SPOOL.get()};
        h.runAtTickTime(5, () -> prepare(h, source, load));
        var connection = new InWorldNodeConnection(new InWorldNode(1, source), new InWorldNode(0, load));
        for (int index = 0; index < items.length; ++index) {
            final int i = index;
            h.runAtTickTime(10 + i * 25, () -> {
                var player = h.makeMockPlayer(GameType.SURVIVAL); var stack = new ItemStack(items[i], 2);
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                for (var endpoint : new BlockWireEndpoint[] {new BlockWireEndpoint(source, 1), new BlockWireEndpoint(load, 0)})
                    h.assertTrue(stack.getItem().useOn(context(h, player, endpoint, false)).consumesAction(), "Survival CEE spool interaction failed on PG");
                h.assertTrue(InfrastructureSavedData.load(h.getLevel()).isConnected(connection.node1(), connection.node2()) && stack.getCount() == 1,
                        "CEE spool failed to connect or changed its native one-spool cost");
                h.assertTrue(player.getInventory().countItem(CEEItems.EMPTY_SPOOL.get()) == 1, "CEE spool did not return its empty spool");
                for (var endpoint : new BlockWireEndpoint[] {new BlockWireEndpoint(source, 1), new BlockWireEndpoint(load, 0)}) stack.getItem().useOn(context(h, player, endpoint, false));
                h.assertTrue(stack.getCount() == 1, "Duplicate CEE connection consumed a spool");
            });
            h.runAtTickTime(20 + i * 25, () -> {
                h.assertTrue(loadVoltage(h, load) > 4.5, "CEE wire type failed to carry mixed power");
                var original = InfrastructureSavedData.load(h.getLevel()).getConnectionData(connection).wireType().getSpooledItem();
                var player = h.makeMockPlayer(GameType.SURVIVAL); var empty = CEEItems.EMPTY_SPOOL.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, empty);
                for (var endpoint : new BlockWireEndpoint[] {new BlockWireEndpoint(source, 1), new BlockWireEndpoint(load, 0)}) empty.getItem().useOn(context(h, player, endpoint, false));
                h.assertTrue(!InfrastructureSavedData.load(h.getLevel()).isConnected(connection.node1(), connection.node2())
                        && player.getInventory().countItem(original) == 1, "Native CEE recovery lost wire type or connection removal");
            });
            h.runAtTickTime(27 + i * 25, () -> BoardComponentGameTests.near(h, loadVoltage(h, load), 0, 0.00001, "Power after spool recovery"));
        }
        h.runAtTickTime(140, () -> { DynamicGameTests.audit(h); h.succeed(); });
    }
    private static void place(GameTestHelper h, BlockPos source, BlockPos load) {
        for (var pos : new BlockPos[] {source, load}) h.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(source, CEEBlocks.CREATIVE_BATTERY.getDefaultState());
        h.getLevel().setBlockAndUpdate(load, ModdedBlocks.CREATIVE_RESISTOR.getDefaultState());
    }
    private static void prepare(GameTestHelper h, BlockPos source, BlockPos load) {
        DevicesSavedData.load(h.getLevel()).getDevice(source, CreativeBatteryDevice.class).voltage = 5;
        ((ResistorBlockEntity) h.getLevel().getBlockEntity(load)).setValue(9);
        WiringGameTests.connect(h, source, 0, load, 1, false);
    }
    private static UseOnContext context(GameTestHelper h, Player player, BlockWireEndpoint endpoint, boolean pg) {
        var point = pg ? endpoint.getExactPosition(h.getLevel()) : new InWorldNode(endpoint.getTerminal(), endpoint.getPos()).getPosition(h.getLevel());
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(point, Direction.UP, endpoint.getPos(), false));
    }
    private static BaseWireEntity pgWire(GameTestHelper h, BlockWireEndpoint source, BlockPos load) {
        return IElectric.getAt(h.getLevel(), source.getPos()).getBehaviour(h.getLevel(), source.getPos(), h.getLevel().getBlockState(source.getPos())).getConnections().get(source)
                .stream().filter(w -> w.isConnectedTo(load, 0)).findFirst().orElseThrow();
    }
    private static double loadVoltage(GameTestHelper h, BlockPos load) {
        return new BlockWireEndpoint(load, 0).getNode(h.getLevel()).getVoltage() - new BlockWireEndpoint(load, 1).getNode(h.getLevel()).getVoltage();
    }
}
