package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.content.creative_battery.CreativeBatteryDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.patryk3211.powergrid.circuits.circuitboard.CircuitBoardBlockEntity;
import org.patryk3211.powergrid.circuits.circuitboard.IncompleteCircuitItem;
import org.patryk3211.powergrid.circuits.components.*;
import org.patryk3211.powergrid.circuits.editor.CircuitDesignTableBlockEntity;
import org.patryk3211.powergrid.circuits.schematic.*;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;

@GameTestHolder("wwpg")
@PrefixGameTestTemplate(false)
public final class BoardWorkflowGameTests {
    @GameTest(template = "empty", timeoutTicks = 70)
    public static void everyBuiltinBoardComponentAssemblesFromNativeItems(GameTestHelper h) {
        int count = 0;
        for (var type : ComponentRegistry.entries()) {
            var schematic = new CircuitSchematic();
            schematic.placeComponent(new PlacedComponent(type, 6, 6, null), 6, 6);
            var tag = new CompoundTag(); tag.put("Schematic", schematic.serializeNbt(h.getLevel().registryAccess()));
            var unfinished = ModdedItems.INCOMPLETE_CIRCUIT.asStack();
            unfinished.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            var item = ComponentRegistry.getItem(h.getLevel(), type);
            h.assertTrue(item != null, "Missing native assembly item for " + ComponentRegistry.getId(type));
            var assembled = IncompleteCircuitItem.insert(h.getLevel(), unfinished, new ItemStack(item));
            h.assertTrue(assembled != null && assembled.is(ModdedBlocks.CIRCUIT_BOARD.get().asItem()),
                    "Native assembly did not complete " + ComponentRegistry.getId(type));
            var restored = CircuitSchematic.fromStack(h.getLevel().registryAccess(), assembled);
            h.assertTrue(restored.components().size() == 1 && restored.components().getFirst().component == type,
                    "Native assembly lost its component configuration");
            ++count;
        }
        h.assertTrue(count == 28, "Pinned board component inventory changed: " + count);
        DynamicGameTests.audit(h); h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 90)
    public static void ceePoweredDesignerSavesAndPlacesViaLabelBoard(GameTestHelper h) {
        var a = new BlockPos(1, 2, 1); var t = new BlockPos(3, 2, 1); var b = new BlockPos(5, 2, 1);
        for (var pos : new BlockPos[] {a, t, b}) h.setBlock(pos.below(), Blocks.STONE);
        h.setBlock(a, CEEBlocks.CREATIVE_BATTERY.get()); h.setBlock(t, ModdedBlocks.CIRCUIT_DESIGN_TABLE.get());
        var source = h.absolutePos(a); var tablePos = h.absolutePos(t); var boardPos = h.absolutePos(b);
        h.runAtTickTime(5, () -> {
            DevicesSavedData.load(h.getLevel()).getDevice(source, CreativeBatteryDevice.class).voltage =
                    Math.sqrt(40 * org.patryk3211.powergrid.config.ResistanceValues.get(ModdedBlocks.CIRCUIT_DESIGN_TABLE.get()));
            var hit = new BlockHitResult[1];
            var player = new net.minecraft.world.entity.player.Player(h.getLevel(), BlockPos.ZERO, 0,
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WWPG-cord-test")) {
                @Override public boolean isSpectator() { return false; }
                @Override public boolean isCreative() { return true; }
                @Override public net.minecraft.world.phys.HitResult pick(double distance, float partialTick, boolean fluids) { return hit[0]; }
            };
            player.setItemInHand(InteractionHand.MAIN_HAND, ModdedItems.CORD.asStack(64));
            for (int port : new int[] {1, 0}) {
                hit[0] = new BlockHitResult(new org.patryk3211.powergrid.electricity.wire.BlockWireEndpoint(source, port).getExactPosition(h.getLevel()), Direction.UP, source, false);
                h.assertTrue(org.patryk3211.powergrid.electricity.wire.powercord.CordItem.useOn(player, InteractionHand.MAIN_HAND, source, Direction.UP).isTrue(), "Native split cord selection failed on CEE");
            }
            var socket = (org.patryk3211.powergrid.electricity.base.ISocketElectric) h.getLevel().getBlockState(tablePos).getBlock();
            var click = socket.socket(h.getLevel().getBlockState(tablePos)).getOrigin().add(tablePos.getX(), tablePos.getY(), tablePos.getZ());
            hit[0] = new BlockHitResult(click, Direction.SOUTH, tablePos, false);
            h.assertTrue(org.patryk3211.powergrid.electricity.wire.powercord.CordItem.useOn(player, InteractionHand.MAIN_HAND, tablePos, Direction.SOUTH).isTrue(), "Native cord failed to connect CEE supply to PG socket");
            var table = (CircuitDesignTableBlockEntity) h.getLevel().getBlockEntity(tablePos);
            table.setSchematic(viaLabelBoard()); table.getInventory().setItem(1, ModdedItems.CIRCUIT_SCHEMATIC.asStack());
        });
        h.runAtTickTime(25, () -> {
            var table = (CircuitDesignTableBlockEntity) h.getLevel().getBlockEntity(tablePos);
            h.assertTrue(table.isPowered(), "CEE power did not operate the native board designer: " + table.power());
            table.writeToItem(false);
            h.assertTrue(table.getInventory().getItem(1).isEmpty(), "Designer did not consume its blank schematic");
            var saved = CircuitSchematic.fromStack(h.getLevel().registryAccess(), table.getInventory().getItem(2));
            var data = new CompoundTag(); data.put("Schematic", saved.serializeNbt(h.getLevel().registryAccess()));
            var stack = ModdedBlocks.CIRCUIT_BOARD.asStack(); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
            var player = h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var result = stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(boardPos.below().getCenter().add(0, 0.5, 0), Direction.UP, boardPos.below(), false)));
            h.assertTrue(result.consumesAction() && stack.isEmpty(), "Native board item placement failed or consumed no board");
            DevicesSavedData.load(h.getLevel()).getDevice(source, CreativeBatteryDevice.class).voltage = 10;
        });
        h.runAtTickTime(30, () -> {
            WiringGameTests.connect(h, source, 1, boardPos, 0, false);
            WiringGameTests.connect(h, source, 0, boardPos, 1, false);
        });
        h.runAtTickTime(45, () -> {
            var board = (CircuitBoardBlockEntity) h.getLevel().getBlockEntity(boardPos);
            var resistor = board.getComponentsStream().filter(p -> p.component == Components.RESISTOR.get()).findFirst().orElseThrow(() -> new IllegalStateException("Placed board lost resistor: " + board.getSchematic().serializeNbt(h.getLevel().registryAccess())));
            double amps = com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData.load(h.getLevel()).ticker.lastResults.getCurrentThrough(
                    new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(1, source), new com.george_vi.electroenergetics.foundation.nodes.InWorldNode(0, boardPos));
            BoardComponentGameTests.near(h, Math.abs(amps), 0.01, 0.0001, "Via front/back trace current");
            var label = board.getComponentsStream().filter(p -> p.component == Components.LABEL.get()).findFirst().orElseThrow(() -> new IllegalStateException("Placed board lost label: " + board.getSchematic().serializeNbt(h.getLevel().registryAccess())));
            h.assertTrue(label.get(Component.LABEL).equals("WWPG") && label.get(LabelComponent.COLOR) == net.minecraft.world.item.DyeColor.RED,
                    "Saved board lost label text/color");
            DynamicGameTests.audit(h); h.succeed();
        });
    }

    private static CircuitSchematic viaLabelBoard() {
        var s = new CircuitSchematic(); s.setName("WWPG via and label acceptance");
        s.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), 0, 5, null), 0, 5);
        s.placeComponent(new PlacedComponent(Components.CONNECTOR.get(), 13, 5, null), 13, 5);
        var resistor = new PlacedComponent(Components.RESISTOR.get(), 6, 5, null);
        resistor.set(ResistorComponent.RESISTANCE, 1000f); s.placeComponent(resistor, 6, 5);
        s.placeComponent(new PlacedComponent(Components.VIA.get(), 4, 6, null), 4, 6);
        var label = new PlacedComponent(Components.LABEL.get(), 2, 2, null);
        label.set(Component.LABEL, "WWPG"); label.set(LabelComponent.COLOR, net.minecraft.world.item.DyeColor.RED); s.placeComponent(label, 2, 2);
        s.front().addHorizontalLine(6, 1, 4); s.back().addHorizontalLine(6, 4, 6); s.front().addHorizontalLine(6, 10, 14);
        return s;
    }
}
