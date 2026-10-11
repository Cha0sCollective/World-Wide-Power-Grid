package org.cha0scollective.wwpg.gametest;

import com.george_vi.electroenergetics.CEEBlocks;
import com.george_vi.electroenergetics.CEEItems;
import com.george_vi.electroenergetics.CEEWireInteractionBehaviours;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNode;
import com.george_vi.electroenergetics.foundation.nodes.InWorldNodeConnection;
import com.george_vi.electroenergetics.foundation.nodes.NodeConnectionPoint;
import com.george_vi.electroenergetics.simulation.infrastructure.InfrastructureSavedData;
import com.george_vi.electroenergetics.simulation.infrastructure.WireData;
import com.simibubi.create.AllItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.cha0scollective.wwpg.bridge.Bridges;
import org.patryk3211.powergrid.collections.ModdedBlocks;
import org.patryk3211.powergrid.collections.ModdedItems;
import org.patryk3211.powergrid.electricity.creative.CreativeSourceBlockEntity;

@GameTestHolder("wwpg_distribution")
@PrefixGameTestTemplate(false)
public final class WireAttachmentGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeWireAttachmentsInstallRemoveAndPersist(GameTestHelper h) {
        var source = new BlockPos(1, 3, 2); var load = new BlockPos(9, 3, 2);
        StationaryEquipmentGameTests.place(h, source, ModdedBlocks.CREATIVE_VOLTAGE_SOURCE.get());
        StationaryEquipmentGameTests.place(h, load, CEEBlocks.CREATIVE_RESISTOR.get());
        var connection = new InWorldNodeConnection(new InWorldNode(0, h.absolutePos(source)), new InWorldNode(0, h.absolutePos(load)));
        var bannerPoint = new NodeConnectionPoint(connection.node1(), connection.node2(), .25f);
        var damperPoint = new NodeConnectionPoint(connection.node1(), connection.node2(), .75f);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        Object[] branches = {null, null};
        h.runAtTickTime(5, () -> {
            ((CreativeSourceBlockEntity) h.getBlockEntity(source)).setValue(40);
            StationaryEquipmentGameTests.wire(h, source, 0, load, 0, false);
            StationaryEquipmentGameTests.wire(h, source, 1, load, 1, true);
        });
        h.runAtTickTime(20, () -> {
            var bridge = Bridges.get(h.getLevel());
            branches[0] = bridge.branchIdentity(connection.node1(), connection.node2());
            branches[1] = bridge.branchIdentity(new InWorldNode(0, h.absolutePos(load)), new InWorldNode(1, h.absolutePos(load)));
            h.assertTrue(branches[0] != null && branches[1] != null, "Mixed fixture branches were not mapped");
            var sd = InfrastructureSavedData.load(h.getLevel());
            var before = sd.getConnectionData(connection);
            var banner = new ItemStack(Items.RED_BANNER, 2);
            banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder().add(
                    h.getLevel().registryAccess().registryOrThrow(Registries.BANNER_PATTERN).getHolderOrThrow(BannerPatterns.CROSS),
                    DyeColor.BLACK).build());
            var damper = CEEItems.WIRE_DAMPER.asStack(2);
            h.assertTrue(CEEWireInteractionBehaviours.BANNER.get().isActiveFor(banner, player)
                    && CEEWireInteractionBehaviours.DAMPER.get().isActiveFor(damper, player), "Native installation tools were not recognized");
            CEEWireInteractionBehaviours.BANNER.get().interactWire(bannerPoint, h.getLevel(), player, banner);
            CEEWireInteractionBehaviours.DAMPER.get().interactWire(damperPoint, h.getLevel(), player, damper);
            h.assertTrue(banner.getCount() == 1 && damper.getCount() == 1, "Native wire attachment placement changed item cost");
            CEEWireInteractionBehaviours.BANNER.get().interactWire(bannerPoint, h.getLevel(), player, banner);
            h.assertTrue(banner.getCount() == 1, "Overlapping banner consumed an extra item");
            var data = sd.getConnectionData(connection);
            h.assertTrue(data.attachments().size() == 2 && data.wireType() == before.wireType() && data.length == before.length,
                    "Attachment placement changed native wire type, length or topology");
            var buffer = Unpooled.buffer();
            try {
                WireData.STREAM_CODEC.encode(buffer, data);
                var cold = WireData.STREAM_CODEC.decode(buffer);
                h.assertTrue(cold.wireType() == data.wireType() && cold.length == data.length && cold.temperature() == data.temperature(),
                        "Native attachment serialization changed electrical wire state");
                for (int index = 0; index < 2; index++) h.assertTrue(
                        cold.attachments().get(index).getFirst().equals(data.attachments().get(index).getFirst())
                                && cold.attachments().get(index).getSecond().write().equals(data.attachments().get(index).getSecond().write()),
                        "Native serialization lost attachment position, identity, banner color or pattern");
                sd.setConnectionData(connection, cold);
            } finally { buffer.release(); }
            h.assertTrue(!CEEWireInteractionBehaviours.BUNTING.get().isActiveFor(Items.RED_BANNER.getDefaultInstance(), player),
                    "Optional Supplementaries bunting was incorrectly exposed in the base/Pinout profiles");
        });
        h.runAtTickTime(35, () -> {
            BoardComponentGameTests.near(h, StationaryEquipmentGameTests.ceeVoltage(h, load), 40, .04, "Decorated mixed wire voltage");
            var bridge = Bridges.get(h.getLevel());
            h.assertTrue(bridge.branchIdentity(connection.node1(), connection.node2()) == branches[0]
                    && bridge.branchIdentity(new InWorldNode(0, h.absolutePos(load)), new InWorldNode(1, h.absolutePos(load))) == branches[1],
                    "Decorations replaced electrical objects");
            var wrench = AllItems.WRENCH.asStack();
            h.assertTrue(CEEWireInteractionBehaviours.ATTACHMENT_REMOVAL.get().isActiveFor(wrench, player), "Native removal wrench was not recognized");
            CEEWireInteractionBehaviours.ATTACHMENT_REMOVAL.get().interactWire(bannerPoint, h.getLevel(), player, wrench);
            CEEWireInteractionBehaviours.ATTACHMENT_REMOVAL.get().interactWire(damperPoint, h.getLevel(), player, wrench);
            h.assertTrue(InfrastructureSavedData.load(h.getLevel()).getConnectionData(connection).attachments().isEmpty(), "Wrench left stale attachments");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(2));
            h.assertTrue(drops.stream().map(ItemEntity::getItem).filter(s -> s.is(Items.RED_BANNER)).mapToInt(ItemStack::getCount).sum() == 1
                    && drops.stream().map(ItemEntity::getItem).filter(s -> s.is(CEEItems.WIRE_DAMPER.get())).mapToInt(ItemStack::getCount).sum() == 1,
                    "Native attachment removal lost or duplicated its items");
        });
        h.runAtTickTime(50, () -> {
            var sd = InfrastructureSavedData.load(h.getLevel());
            BoardComponentGameTests.near(h, StationaryEquipmentGameTests.ceeVoltage(h, load), 40, .04, "Power after attachment removal");
            var original = sd.getConnectionData(connection).wireType().getSpooledItem();
            var cutters = ModdedItems.WIRE_CUTTER.asStack();
            player.setItemInHand(InteractionHand.MAIN_HAND, cutters);
            player.getInventory().add(CEEItems.EMPTY_SPOOL.asStack());
            h.assertTrue(player.getInventory().countItem(CEEItems.EMPTY_SPOOL.get()) == 1, "Test player has no recovery spool");
            h.assertTrue(CEEWireInteractionBehaviours.WIRE_CUTTERS.get().isActiveFor(cutters, player), "PG cutters are not recognized by native CEE wire handling");
            CEEWireInteractionBehaviours.WIRE_CUTTERS.get().interactWire(bannerPoint, h.getLevel(), player, cutters);
            h.assertTrue(!sd.isConnected(connection.node1(), connection.node2())
                    && player.getInventory().countItem(original) == 1, "PG cutters lost the native CEE spool or failed to disconnect");
        });
        h.runAtTickTime(70, () -> {
            BoardComponentGameTests.near(h, StationaryEquipmentGameTests.ceeVoltage(h, load), 0, .001, "Cut decorated wire left ghost power");
            DynamicGameTests.audit(h); h.succeed();
        });
    }
}
