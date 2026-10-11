package org.cha0scollective.wwpg.examples.upstream;

import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import org.patryk3211.powergrid.circuits.circuitboard.IncompleteCircuitItem;
import org.patryk3211.powergrid.circuits.components.ComponentRegistry;
import org.patryk3211.powergrid.circuits.components.Components;
import org.patryk3211.powergrid.circuits.components.ResistorComponent;
import org.patryk3211.powergrid.circuits.schematic.CircuitSchematic;
import org.patryk3211.powergrid.circuits.schematic.PlacedComponent;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.base.IElectric;
import org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint;

import java.util.UUID;

/** Independent fixtures invoke upstream item interactions, never WWPG internals. */
final class NativeFixtures {
    private NativeFixtures() { }

    static void wire(GameTestHelper helper, BlockPos first, int firstId, BlockPos second, int secondId, boolean pg) {
        ServerLevel level = helper.getLevel();
        var player = new Player(level, first, 0, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "API-wire-fixture")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
        };
        player.setItemInHand(InteractionHand.MAIN_HAND, pg ? ModdedItems.WIRE.asStack(64) : CEEItems.WIRE_SPOOL.asStack());
        for (var endpoint : new BlockWireEndpoint[]{new BlockWireEndpoint(first, firstId), new BlockWireEndpoint(second, secondId)}) {
            var position = endpoint.getPos();
            var point = pg ? endpoint.getExactPosition(level) : new InWorldNode(endpoint.getTerminal(), position).getPosition(level);
            helper.assertTrue(point != null, "Native terminal has no placement geometry: " + endpoint);
            var context = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(point, Direction.UP, position, false));
            var result = pg ? IElectric.getAt(level, position).onWire(level.getBlockState(position), context)
                    : player.getMainHandItem().getItem().useOn(context);
            helper.assertTrue(result.consumesAction(), "Native wire item rejected extension terminal: " + endpoint);
        }
    }

    static CircuitSchematic shuntSchematic() {
        var schematic = new CircuitSchematic();
        schematic.setName("WWPG API native shunt");
        schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), 0, 5, null), 0, 5);
        schematic.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), 13, 5, null), 13, 5);
        var shunt = new PlacedComponent(UpstreamExample.SHUNT.get(), 5, 5, null);
        shunt.set(ResistorComponent.RESISTANCE, 100f);
        schematic.placeComponent(shunt, 5, 5);
        schematic.front().addHorizontalLine(6, 1, 5);
        schematic.front().addHorizontalLine(6, 9, 14);
        return schematic;
    }

    static void assembleAndPlace(GameTestHelper helper, BlockPos boardPosition, CircuitSchematic schematic) {
        var level = helper.getLevel();
        var data = new CompoundTag();
        data.put("Schematic", schematic.serializeNbt(level.registryAccess()));
        ItemStack board = ModdedItems.INCOMPLETE_CIRCUIT.asStack();
        board.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        for (var component : schematic.components()) {
            var item = ComponentRegistry.getItem(level, component.component);
            helper.assertTrue(item != null, "Missing native assembly item: " + ComponentRegistry.getId(component.component));
            board = IncompleteCircuitItem.insert(level, board, new ItemStack(item));
            helper.assertTrue(board != null, "Native component assembly failed: " + ComponentRegistry.getId(component.component));
        }
        helper.assertTrue(board.is(ModdedBlocks.CIRCUIT_BOARD.get().asItem()), "Native component assembly did not finish a board");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, board);
        var result = board.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(boardPosition.below().getCenter().add(0, .5, 0), Direction.UP, boardPosition.below(), false)));
        helper.assertTrue(result.consumesAction() && board.isEmpty(), "Native board item did not place or consume its assembled board");
    }

    static void near(GameTestHelper helper, double actual, double expected, double tolerance, String behavior) {
        helper.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                behavior + ": expected " + expected + " +/- " + tolerance + ", got " + actual);
    }
}
